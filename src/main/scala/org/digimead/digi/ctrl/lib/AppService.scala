/**
 * Copyright (c) 2012 Alexey Aksenov ezh@ezh.msk.ru
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.digimead.digi.ctrl.lib

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

import scala.actors.Futures.future
import scala.actors.Actor
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.ICtrlHost

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process

protected class AppService private ( final val root: WeakReference[Context]) extends Actor with Logging {
  protected val serviceInstance: AtomicReference[ICtrlHost] = new AtomicReference(null)
  protected val ctrlBindCounter = new AtomicInteger()
  protected val ctrlConnection = new ServiceConnection() {
    @Loggable
    def onServiceConnected(className: ComponentName, iservice: IBinder) {
      log.debug("connected to Control service")
      serviceInstance.set(ICtrlHost.Stub.asInterface(iservice))
      if (getState == scala.actors.Actor.State.New) {
        log.debug("start AppService singleton actor")
        start() // start service singleton actor
      }
    }
    @Loggable
    def onServiceDisconnected(className: ComponentName) {
      log.debug("unexpected disconnect from Control service")
      serviceInstance.set(null)
    }
  }
  log.debug("alive")
  def act = {
    loop {
      react {
        // TODO suspend actor as scala.actors.Actor.State.Suspended when service lost
        case AppService.Message.Ping =>
          reply()
        case AppService.Message.Start(componentPackage, onCompleteCallback) =>
          if (onCompleteCallback != null) onCompleteCallback(componentStart(componentPackage)) else componentStart(componentPackage)
        case AppService.Message.Status(componentPackage, onCompleteCallback) =>
          assert(onCompleteCallback != null, { val e = "onCompleteCallback lost"; log.error(e); e })
          onCompleteCallback(componentStatus(componentPackage))
        case AppService.Message.Stop(componentPackage, onCompleteCallback) =>
          if (onCompleteCallback != null) onCompleteCallback(componentStop(componentPackage)) else componentStop(componentPackage)
        case message: AnyRef =>
          log.error("skip unknown message " + message.getClass.getName + ": " + message)
        case message =>
          log.error("skip unknown message " + message)
      }
    }
  }
  def get(): Option[ICtrlHost] = get(true)
  def get(throwError: Boolean): Option[ICtrlHost] = synchronized {
    if (!throwError)
      return serviceInstance.get() match {
        case service: ICtrlHost => Some(service)
        case null => None
      }
    serviceInstance.get match {
      case serviceInstance: ICtrlHost =>
        Some(serviceInstance)
      case null =>
        val t = new Throwable("Intospecting stack frame")
        t.fillInStackTrace()
        log.error("uninitialized ICtrlHost at AppService: " + t.getStackTraceString)
        None
    }
  }
  @Loggable
  def getWait() = {
    this !? AppService.Message.Ping // actor start ONLY after successful binding
    get
  }
  @Loggable
  def bind(caller: Activity) = synchronized {
    for {
      ctx <- root.get
      innerApp <- AppActivity.Inner
    } {
      if (ctrlBindCounter.incrementAndGet() == 1)
        if (serviceInstance.get == null)
          future {
            val intent = new Intent(Common.Intent.hostService)
            intent.putExtra("packageName", ctx.getPackageName())
            val successful = if (isInstalled(ctx)) {
              log.info("bind to " + Common.Intent.hostService)
              ctx.bindService(intent, ctrlConnection, Context.BIND_AUTO_CREATE)
            } else {
              log.warn(Common.Intent.hostService + " not installed")
              false
            }
            if (!successful)
              innerApp.state.set(AppActivity.State(Common.State.Broken, Android.getString(ctx, "error_control_notfound").
                getOrElse("Bind failed, DigiControl application not found"),
                () => caller.showDialog(dialog.InstallControl.getId(ctx))))
          }
        else
          log.error("service " + Common.Intent.hostService + " already binded")
    }
  }
  @Loggable
  def unbind() = synchronized {
    root.get.foreach(ctx => {
      if (ctrlBindCounter.decrementAndGet() == 0)
        if (serviceInstance.get != null) {
          log.debug("unbind service")
          ctx.unbindService(ctrlConnection)
          serviceInstance.set(null)
        } else
          log.warn("service already unbinded")
    })
  }
  @Loggable
  def isInstalled(ctx: Context): Boolean = {
    val pm = ctx.getPackageManager()
    val intent = new Intent(Common.Intent.hostService)
    val info = pm.resolveService(intent, 0);
    info != null
  }
  /*
   * active
   * passive
   * unavailable
   */
  @Loggable
  def getInterfaceStatus(interface: String, filters: Seq[String]): Option[Boolean] = {
    val isListen = false
    val isAvailable = try {
      filters.exists(f => interface.matches(f.replaceAll("""\*""", """\\w+""").replaceAll("""\.""", """\\.""")))
    } catch {
      case e =>
        log.error(e.getMessage, e)
        false
    }
    (isListen, isAvailable) match {
      case (true, _) => Some(true)
      case (false, true) => Some(false)
      case (false, false) => None
    }
  }
  @Loggable
  protected def componentStart(componentPackage: String): Boolean = get match {
    case Some(service) => service.start(componentPackage)
    case None => false
  }
  @Loggable
  protected def componentStatus(componentPackage: String): Either[String, Common.ComponentStatus] = get match {
    case Some(service) =>
      try {
        service.status(componentPackage) match {
          case list: java.util.List[_] =>
            Common.deserializeFromList(list.asInstanceOf[java.util.List[Byte]]) match {
              case Some(obj) =>
                Right(obj.asInstanceOf[Common.ComponentStatus])
              case None =>
                Left("status failed")
            }
          case null =>
            log.debug("service return null instread of Common.ComponentStatus")
            Left("status failed")
        }
      } catch {
        case e =>
          Left(e.getMessage)
      }
    case None =>
      Left("service unreachable")
  }
  @Loggable
  protected def componentStop(componentPackage: String): Boolean = get match {
    case Some(service) => service.stop(componentPackage)
    case None => false
  }
}

object AppService extends Logging {
  private var inner: AppService = null
  log.debug("alive")
  @Loggable
  def init(root: Context, _inner: AppService = null) = synchronized {
    if (inner != null) {
      log.info("reinitialize AppService core subsystem for " + root.getPackageName())
    } else {
      log.info("initialize AppService for " + root.getPackageName())
      resetNatives(root)
    }
    if (_inner != null) {
      inner = _inner
    } else {
      inner = new AppService(new WeakReference(root))
    }
  }
  def deinit(): Unit = synchronized {
    log.info("deinitialize AppService for " + inner.root.get.map(_.getPackageName()).getOrElse("UNKNOWN"))
    assert(inner != null)
    val _inner = inner
    inner = null
    if (AppActivity.initialized)
      for {
        rootSrv <- _inner.root.get;
        innerApp <- AppActivity.Inner;
        rootApp <- innerApp.root.get
      } if (rootApp == rootSrv) {
        log.info("AppActivity and AppService share the same context. Clear.")
        AppActivity.deinit()
      }
    _inner.root.get.foreach(resetNatives)
  }
  def Inner = synchronized {
    if (inner != null) {
      Some(inner)
    } else {
      val t = new Throwable("Intospecting stack frame")
      t.fillInStackTrace()
      log.error(getClass().getName() + " singleton uninitialized: " + t.getStackTraceString)
      None
    }
  }
  def ICtrlHost = Inner.flatMap(_.get())
  def initialized() = synchronized { inner != null }
  @Loggable
  private def resetNatives(context: Context) = future {
/*    val myUID = Process.myUid
    val actvityManager = context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
    val processes = actvityManager.getRunningAppProcesses()
    for (i <- 0 until processes.size() if processes.get(i).uid == myUID) {
      val processInfo = processes.get(i)
    }*/
    val args = Array("pkill", "-P", "1") // children of init
    log.debug("exec " + args.mkString(" "))
    val p = Runtime.getRuntime().exec(args)
    p.waitFor()
  }
  object Message {
    sealed trait Abstract
    object Ping extends Abstract
    case class Start(componentPackage: String, onCompleteCallback: (Boolean) => Unit = null) extends Abstract
    case class Status(componentPackage: String, onCompleteCallback: (Either[String, Common.ComponentStatus]) => Unit) extends Abstract
    case class Stop(componentPackage: String, onCompleteCallback: (Boolean) => Unit = null) extends Abstract
    //    object ListInterfaces extends Abstract
    object Disconnect extends Abstract
  }
}
