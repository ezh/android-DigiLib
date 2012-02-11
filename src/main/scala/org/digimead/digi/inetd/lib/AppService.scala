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

package org.digimead.digi.inetd.lib

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

import scala.actors.Futures.future
import scala.actors.Actor
import scala.ref.WeakReference

import org.digimead.digi.inetd.lib.aop.Loggable
import org.digimead.digi.inetd.IINETDHost
import org.slf4j.LoggerFactory

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException

protected class AppService private (var root: WeakReference[Context]) extends Actor {
  private val log = LoggerFactory.getLogger(getClass.getName().replaceFirst("org.digimead.digi.inetd", "o.d.d.i"))
  protected val serviceInstance: AtomicReference[IINETDHost] = new AtomicReference(null)
  protected val inetdBindCounter = new AtomicInteger()
  protected val inetdConnection = new ServiceConnection() {
    @Loggable
    def onServiceConnected(className: ComponentName, iservice: IBinder) {
      log.debug("connected to INETD service")
      serviceInstance.set(IINETDHost.Stub.asInterface(iservice))
      if (getState == scala.actors.Actor.State.New) {
        log.debug("start AppService singleton actor")
        start() // start service singleton actor
      }
    }
    @Loggable
    def onServiceDisconnected(className: ComponentName) {
      log.debug("disconnected from INETD service")
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
        case AppService.Message.Start(serviceActivity, onComplete) =>
          if (onComplete != null) onComplete(serviceStart(serviceActivity)) else serviceStart(serviceActivity)
        case AppService.Message.StartAll(onComplete) =>
          if (onComplete != null) onComplete(serviceStartAll()) else serviceStartAll()
        case AppService.Message.Status(serviceActivity, onComplete) =>
          serviceStatus(serviceActivity)
        case AppService.Message.Stop(serviceActivity, onComplete) =>
          if (onComplete != null) onComplete(serviceStop(serviceActivity)) else serviceStop(serviceActivity)
        case AppService.Message.StopAll(onComplete) =>
          if (onComplete != null) onComplete(serviceStopAll()) else serviceStopAll()
        case unknown =>
          log.error("unknown message " + unknown)
      }
    }
  }
  def get(): Option[IINETDHost] = get(true)
  def get(throwError: Boolean): Option[IINETDHost] = synchronized {
    if (!throwError)
      return serviceInstance.get() match {
        case service: IINETDHost => Some(service)
        case null => None
      }
    serviceInstance.get match {
      case serviceInstance: IINETDHost =>
        Some(serviceInstance)
      case _ =>
        val t = new Throwable("Intospecting stack frame")
        t.fillInStackTrace()
        log.error("uninitialized IINETDHost at AppService: " + t.getStackTraceString)
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
    root.get.foreach(ctx => {
      if (inetdBindCounter.incrementAndGet() == 1)
        if (serviceInstance.get == null)
          future {
            val intent = new Intent(Common.Intent.hostService)
            intent.putExtra("packageName", ctx.getPackageName())
            val successful = if (isInstalled(ctx)) {
              log.info("bind to " + Common.Intent.hostService)
              ctx.bindService(intent, inetdConnection, Context.BIND_AUTO_CREATE)
            } else {
              log.warn(Common.Intent.hostService + " not installed")
              false
            }
            if (!successful)
              AppActivity.Status(Common.State.error, Android.getString(ctx, "error_inetd_notfound"),
                () => caller.showDialog(dialog.InstallINETD.getId(ctx)))
          }
        else
          log.error("service " + Common.Intent.hostService + " already binded")
    })
  }
  @Loggable
  def unbind() = synchronized {
    root.get.foreach(ctx => {
      if (inetdBindCounter.decrementAndGet() == 0)
        if (serviceInstance.get != null) {
          log.debug("unbind service")
          ctx.unbindService(inetdConnection)
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
  protected def serviceStart(serviceActivity: String): Boolean =
    get match {
      case Some(service) => service.start(serviceActivity)
      case None => false
    }
  @Loggable
  protected def serviceStartAll(): Boolean =
    get match {
      case Some(service) => service.startAll()
      case None => false
    }
  @Loggable
  protected def serviceStatus(serviceActivity: String): Either[String, java.util.Map[String, Any]] =
    AppService.IINETDHost match {
      case Some(service) =>
        try {
          Right(service.status().asInstanceOf[java.util.Map[String, Any]])
        } catch {
          case e: RemoteException =>
            Left(e.getMessage)
        }
      case None =>
        Left("service unreachable")
    }
  @Loggable
  protected def serviceStop(serviceActivity: String): Boolean =
    get match {
      case Some(service) => service.stop(serviceActivity)
      case None => false
    }
  @Loggable
  protected def serviceStopAll(): Boolean =
    get match {
      case Some(service) => service.stopAll()
      case None => false
    }
}

object AppService {
  private val log = LoggerFactory.getLogger(getClass.getName().replaceFirst("org.digimead.digi.inetd", "o.d.d.i"))
  private var inner: AppService = null
  log.debug("alive")
  @Loggable
  def init(root: Context, _inner: AppService = null) = synchronized {
    log.info("initialize AppService for " + root.getPackageName())
    assert(inner == null)
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
  def IINETDHost = Inner.flatMap(_.get())
  def initialized = synchronized { inner != null }
  object Message {
    sealed abstract class Abstract
    object Ping extends Abstract
    case class Start(serviceActivity: String, onComplete: (Boolean) => Unit = null) extends Abstract
    case class StartAll(onComplete: (Boolean) => Unit = null) extends Abstract
    case class Status(serviceActivity: String, onComplete: (Boolean) => Unit = null) extends Abstract
    case class Stop(serviceActivity: String, onComplete: (Boolean) => Unit = null) extends Abstract
    case class StopAll(onComplete: (Boolean) => Unit = null) extends Abstract
    object ListInterfaces extends Abstract
    object Disconnect extends Abstract
  }
  object State extends Enumeration {
    val Initializing, Ready, Active, Error = Value
  }
}
