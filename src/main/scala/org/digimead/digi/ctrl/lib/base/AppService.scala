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

package org.digimead.digi.ctrl.lib.base

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

import scala.actors.Futures.future
import scala.actors.Actor
import scala.concurrent.SyncVar
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.InstallControl
import org.digimead.digi.ctrl.lib.info.ComponentState
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.Activity
import org.digimead.digi.ctrl.ICtrlHost

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder

protected class AppService private () extends Actor with Logging {
  protected lazy val serviceInstance: AtomicReference[ICtrlHost] = new AtomicReference(null)
  protected lazy val ctrlBindCounter = new AtomicInteger()
  protected lazy val ctrlConnection = new ServiceConnection() with Logging {
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
        case AppService.Message.Disconnect(componentPackage, processID, connectionID, onCompleteCallback) =>
          if (onCompleteCallback != null)
            onCompleteCallback(componentDisconnect(componentPackage, processID, connectionID))
          else
            componentDisconnect(componentPackage, processID, connectionID)
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
    AppActivity.Context foreach {
      context =>
        if (ctrlBindCounter.incrementAndGet() == 1)
          if (serviceInstance.get == null)
            future {
              val intent = new Intent(DIntent.HostService)
              intent.putExtra("packageName", context.getPackageName())
              val successful = if (isInstalled(context)) {
                log.info("bind to " + DIntent.HostService)
                context.bindService(intent, ctrlConnection, Context.BIND_AUTO_CREATE)
              } else {
                log.warn(DIntent.HostService + " not installed")
                false
              }
              if (!successful)
                AppActivity.Inner.state.set(AppActivity.State(DState.Broken, Android.getString(context, "error_control_notfound").
                  getOrElse("Bind failed, DigiControl application not found"),
                  () => caller.showDialog(InstallControl.getId(context))))
            }
          else
            log.fatal("service " + DIntent.HostService + " already binded")
    }
  }
  @Loggable
  def unbind() = synchronized {
    AppActivity.Context foreach {
      context =>
        if (ctrlBindCounter.decrementAndGet() == 0)
          if (serviceInstance.get != null) {
            log.debug("unbind service")
            context.unbindService(ctrlConnection)
            serviceInstance.set(null)
          } else
            log.warn("service already unbinded")
    }
  }
  @Loggable
  def isInstalled(ctx: Context): Boolean = {
    val pm = ctx.getPackageManager()
    val intent = new Intent(DIntent.HostService)
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
  protected def componentStatus(componentPackage: String): Either[String, ComponentState] = get match {
    case Some(service) =>
      try {
        service.status(componentPackage) match {
          case status: ComponentState =>
            Right(status)
          case null =>
            log.debug("service return null instread of DComponentStatus")
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
  @Loggable
  protected def componentDisconnect(componentPackage: String, processID: Int, connectionID: Int): Boolean = get match {
    case Some(service) => service.disconnect(componentPackage, processID, connectionID)
    case None => false
  }
}

object AppService extends Logging {
  @volatile private var inner: AppService = null
  @volatile private[lib] var context: WeakReference[Context] = new WeakReference(null)
  private val deinitializationLock = new SyncVar[Boolean]()

  log.debug("alive")
  @Loggable
  private[lib] def init(root: Context, _inner: AppService = null) = {
    deinitializationLock.set(false) // cancel deinitialization sequence if any
    initRoutine(root, _inner)
  }
  private[lib] def initRoutine(root: Context, _inner: AppService) = synchronized {
    if (inner != null) {
      log.info("reinitialize AppService core subsystem for " + root.getPackageName())
    } else {
      log.info("initialize AppService for " + root.getPackageName())
      resetNatives(root)
    }
    context = new WeakReference(root)
    if (_inner != null)
      inner = _inner
    else
      inner = new AppService()
  }
  private[lib] def deinit(): Unit = future {
    val packageName = AppService.context.get.map(_.getPackageName()).getOrElse("UNKNOWN")
    log.info("deinitializing AppService for " + packageName)
    deinitializationLock.unset
    deinitializationLock.get(DTimeout.longest) match {
      case Some(false) =>
        log.info("deinitialization AppService for " + packageName + " canceled")
      case _ =>
        deinitRoutine(packageName)
    }
  }
  private[lib] def deinitRoutine(packageName: String): Unit = synchronized {
    log.info("deinitialize AppService for " + packageName)
    assert(inner != null)
    val _inner = inner
    inner = null
    if (AppActivity.initialized)
      for {
        rootSrv <- context.get
        rootApp <- AppActivity.Context
      } if (rootApp == rootSrv) {
        log.info("AppActivity and AppService share the same context. Clear.")
        AppActivity.deinitRoutine(packageName)
      }
    context.get.foreach(resetNatives)
  }
  def Inner = inner
  def ICtrlHost = inner.get()
  def initialized() = synchronized {
    while (inner != null)
      wait()
  }
  @Loggable
  private def resetNatives(context: Context) = future {
    /*    val myUID = Process.myUid
    val actvityManager = context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
    val processes = actvityManager.getRunningAppProcesses()
    for (i <- 0 until processes.size() if processes.get(i).uid == myUID) {
      val processInfo = processes.get(i)
    }*/
    val args = Array("pkill", "-9", "-P", "1") // children of init
    log.debug("exec " + args.mkString(" "))
    val p = Runtime.getRuntime().exec(args)
    val err = new BufferedReader(new InputStreamReader(p.getErrorStream()))
    p.waitFor()
    val retcode = p.exitValue()
    if (retcode != 0) {
      var error = err.readLine()
      while (error != null) {
        log.fatal("pkill error: " + error)
        error = err.readLine()
      }
      false
    } else
      true
  }
  object Message {
    sealed trait Abstract
    object Ping extends Abstract
    case class Start(componentPackage: String, onCompleteCallback: (Boolean) => Unit = null) extends Abstract
    case class Status(componentPackage: String, onCompleteCallback: (Either[String, ComponentState]) => Unit) extends Abstract
    case class Stop(componentPackage: String, onCompleteCallback: (Boolean) => Unit = null) extends Abstract
    //    object ListInterfaces extends Abstract
    case class Disconnect(componentPackage: String, processID: Int, connectionID: Int, onCompleteCallback: (Boolean) => Unit = null) extends Abstract
  }
}
