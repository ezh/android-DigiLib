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

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import scala.actors.Future
import scala.actors.Futures
import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.ICtrlHost
import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.DActivity
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.InstallControl
import org.digimead.digi.ctrl.lib.info.ComponentState
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.SyncVar

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper

protected class AppControl private (packageName: String) extends Logging {
  private val ctrlBindTimeout = DTimeout.short
  protected lazy val ready = new SyncVar[Option[ICtrlHost]]()
  protected[lib] lazy val ctrlBindContext = new AtomicReference[Context](null)
  protected lazy val ctrlConnection = new ServiceConnection() with Logging {
    @Loggable
    def onServiceConnected(className: ComponentName, iservice: IBinder) {
      log.info("connected to DigiControl service")
      val service = ICtrlHost.Stub.asInterface(iservice)
      if (service != null) {
        service.update_shutdown_timer(packageName, -1)
        ready.set(Some(service))
      }
    }
    @Loggable
    def onServiceDisconnected(className: ComponentName) {
      log.warn("unexpected disconnect from DigiControl service")
      ready.unset()
    }
  }
  private val rebindInProgressLock = new AtomicBoolean(false)
  private val uiThreadID = Looper.getMainLooper.getThread.getId
  private val internalDirectory = new AtomicReference[File](null)
  private val externalDirectory = new AtomicReference[File](null) // sdcard
  log.debug("alive")

  /*
   * None - unknown
   * Some(true) - yes
   * Some(false) - no / DigiControl unavailable
   */
  def isAvailable(): Option[Boolean] =
    if (ready.isSet) {
      if (ready.get != None)
        Some(true)
      else
        Some(false)
    } else
      None
  def get(): Option[ICtrlHost] =
    get(true)
  def get(timeout: Long): Option[ICtrlHost] =
    get(timeout, true)
  def get(throwError: Boolean): Option[ICtrlHost] =
    get(0, true)
  def get(timeout: Long, throwError: Boolean, stackTrace: Throwable = null): Option[ICtrlHost] =
    AppControl.get(timeout, throwError, ready, stackTrace)
  def getWait(): ICtrlHost =
    getWait(true)
  @Loggable
  def getWait(throwError: Boolean): ICtrlHost =
    AppControl.get(-1, throwError, ready).get
  @Loggable
  def bindStub(error: String*): Unit =
    AppControl.bindStub(ctrlBindContext, ready, ctrlConnection, error)
  @Loggable
  def bind(caller: Context, allowCallFromUI: Boolean = false): Unit = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("call AppControl function from UI thread")
    AppControl.bind(caller, ctrlBindContext, ready, ctrlConnection)
  }
  @Loggable
  def unbind(): Unit = {
    AppControl.unbind(ctrlBindContext, ready, ctrlConnection)
  }
  @Loggable
  protected def rebind(timeout: Long, allowCallFromUI: Boolean = false): Option[ICtrlHost] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("call AppControl function from UI thread")
    // rebind only in we already have connection to ICtrlHost
    if (ctrlBindContext.get != null && AppControl.isICtrlHostInstalled(ctrlBindContext.get)) {
      Futures.future {
        if (rebindInProgressLock.compareAndSet(false, true)) {
          unbind()
          var result: Option[ICtrlHost] = None
          while (result == None && AppControl.Inner != null) {
            AppComponent.Context.foreach(_ match {
              case activity: Activity with DActivity =>
                log.warn("rebind ICtrlHost service with timeout " + timeout + " and context " + activity)
                bind(activity)
              case _ =>
                log.warn("rebind ICtrlHost service failed")
                None
            })
            result = get(DTimeout.long)
          }
          log.warn("rebind ICtrlHost service finished, result: " + result)
          rebindInProgressLock.set(false)
        } else {
          log.warn("rebind ICtrlHost service already in progress, skip")
          None
        }
      }
      get(timeout)
    } else
      None
  }
  @Loggable(result = false)
  def getInternalDirectory(timeout: Int = DTimeout.long): Option[File] =
    Option(internalDirectory.get()) orElse {
      Futures.future { initializeDirectories(timeout) }()
      Option(internalDirectory.get())
    }
  @Loggable(result = false)
  def getExternalDirectory(timeout: Int = DTimeout.long): Option[File] =
    Option(externalDirectory.get()) orElse {
      Futures.future { initializeDirectories(timeout) }()
      Option(externalDirectory.get())
    } orElse Option(internalDirectory.get())
  @Loggable(result = false)
  def callListDirectories(componentPackage: String, allowCallFromUI: Boolean = false): Future[Option[(String, String)]] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callListDirectories AppControl function from UI thread")
    val t = new Throwable("Intospecting callListDirectories")
    t.fillInStackTrace()
    Futures.future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
        case Some(service) =>
          service.directories(componentPackage) match {
            case null =>
              None
            case list =>
              Some(list.head, list.last)
          }
        case None =>
          None
      }
    }
  }
  @Loggable(result = false)
  def callEnable(componentPackage: String, flag: Boolean, allowCallFromUI: Boolean = false): Future[_] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callStart AppControl function from UI thread")
    val t = new Throwable("Intospecting callEnable")
    t.fillInStackTrace()
    Futures.future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
        case Some(service) =>
          service.enable(componentPackage, flag)
        case None =>
          false
      }
    }
  }
  @Loggable(result = false)
  def callReset(componentPackage: String, allowCallFromUI: Boolean = false): Future[Boolean] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callReset AppControl function from UI thread")
    val t = new Throwable("Intospecting callReset")
    t.fillInStackTrace()
    Futures.future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
        case Some(service) =>
          service.reset(componentPackage)
        case None =>
          false
      }
    }
  }
  @Loggable(result = false)
  def callStart(componentPackage: String, allowCallFromUI: Boolean = false): Future[Boolean] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callStart AppControl function from UI thread")
    val t = new Throwable("Intospecting callStart")
    t.fillInStackTrace()
    Futures.future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
        case Some(service) =>
          service.start(componentPackage)
        case None =>
          false
      }
    }
  }
  @Loggable(result = false)
  def callStatus(componentPackage: String, allowCallFromUI: Boolean = false): Future[Either[String, ComponentState]] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callStatus AppControl function from UI thread")
    val t = new Throwable("Intospecting callStatus")
    t.fillInStackTrace()
    Futures.future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
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
          val text = if (ready.isSet && ready.get == None) {
            AppComponent.Context match {
              case Some(context) =>
                Android.getString(context, "error_component_status_unavailable").
                  getOrElse("%1$s component status unavailable").format(componentPackage)
              case None =>
                componentPackage + "component status unavailable"
            }
          } else
            "error_digicontrol_not_found"
          Left(text)
      }
    }
  }
  @Loggable(result = false)
  def callStop(componentPackage: String, allowCallFromUI: Boolean = false): Future[Boolean] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callStop AppControl function from UI thread")
    val t = new Throwable("Intospecting callStop")
    t.fillInStackTrace()
    Futures.future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
        case Some(service) =>
          service.stop(componentPackage)
        case None =>
          false
      }
    }
  }
  @Loggable(result = false)
  def callDisconnect(componentPackage: String, processID: Int, connectionID: Int, allowCallFromUI: Boolean = false): Future[Boolean] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callDisconnect AppControl function from UI thread")
    val t = new Throwable("Intospecting callDisconnect")
    t.fillInStackTrace()
    Futures.future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
        case Some(service) =>
          service.disconnect(componentPackage, processID, connectionID)
        case None =>
          false
      }
    }
  }
  @Loggable(result = false)
  def callListActiveInterfaces(componentPackage: String, allowCallFromUI: Boolean = false): Future[Option[Seq[String]]] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callListActiveInterfaces AppControl function from UI thread")
    val t = new Throwable("Intospecting callListActiveInterfaces")
    t.fillInStackTrace()
    Futures.future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
        case Some(service) =>
          service.interfaces(componentPackage) match {
            case null =>
              None
            case list =>
              Some(list)
          }
        case None =>
          None
      }
    }
  }
  @Loggable(result = false)
  def callListPendingConnections(componentPackage: String, allowCallFromUI: Boolean = false): Future[Option[Seq[Intent]]] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callListPendingConnections AppControl function from UI thread")
    val t = new Throwable("Intospecting callListPendingConnections")
    t.fillInStackTrace()
    Futures.future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
        case Some(service) =>
          service.pending_connections(componentPackage) match {
            case null =>
              None
            case list =>
              Some(list)
          }
        case None =>
          None
      }
    }
  }
  @Loggable(result = false)
  def callUpdateShutdownTimer(componentPackage: String, remain_mseconds: Long, allowCallFromUI: Boolean = false): Future[Unit] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callUpdateShutdownTimer AppControl function from UI thread")
    val t = new Throwable("Intospecting callUpdateShutdownTimer")
    t.fillInStackTrace()
    Futures.future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
        case Some(service) =>
          service.update_shutdown_timer(componentPackage, remain_mseconds)
        case None =>
          None
      }
    }
  }
  @Loggable
  private def initializeDirectories(timeout: Int) {
    val internalPath = new SyncVar[File]()
    val externalPath = new SyncVar[File]()
    AppControl.Inner.callListDirectories(packageName)() match {
      case Some((internal, external)) =>
        internalPath.set(new File(internal))
        externalPath.set(new File(external))
      case r =>
        log.warn("unable to get component directories, result " + r)
        internalPath.set(null)
        externalPath.set(null)
    }
    for {
      internalPath <- internalPath.get(timeout) if internalPath != null
      externalPath <- externalPath.get(timeout) if externalPath != null
    } {
      internalDirectory.set(internalPath)
      externalDirectory.set(externalPath)
    }
  }
}

object AppControl extends Logging {
  @volatile private var inner: AppControl = null
  private[base] val deinitializationLock = new SyncVar[Boolean]()
  private val deinitializationInProgressLock = new AtomicBoolean(false)
  deinitializationLock.set(false)
  log.debug("alive")

  @Loggable
  private[lib] def init(root: Context, _inner: AppControl = null) = {
    deinitializationLock.set(false) // cancel deinitialization sequence if any
    initRoutine(root, _inner)
  }
  private[lib] def initRoutine(root: Context, _inner: AppControl) = synchronized {
    if (inner != null) {
      log.info("reinitialize AppControl core subsystem for " + root.getPackageName())
      inner = _inner
    } else {
      log.info("initialize AppControl for " + root.getPackageName())
      inner = new AppControl(root.getPackageName())
    }
  }
  private[lib] def resurrect(supressEvent: Boolean = false): Unit = deinitializationLock.synchronized {
    if (deinitializationLock.get(0) != Some(false)) {
      log.info("resurrect AppControl core subsystem")
      deinitializationLock.set(false) // try to cancel
      // if _AppComponent_ active
      if (AppComponent.deinitializationLock.get(0) == Some(false))
        if (!supressEvent)
          try { AppComponent.publish(AppComponent.Event.Resume) } catch { case e => log.error(e.getMessage, e) }
    }
    if (deinitializationInProgressLock.get) {
      Thread.sleep(100) // unoffending delay
      deinitializationInProgressLock.synchronized {
        while (deinitializationInProgressLock.get) {
          log.debug("deinitialization in progress, waiting...")
          deinitializationInProgressLock.wait
        }
      }
    }
  }
  private[lib] def deinit(): Unit = if (deinitializationInProgressLock.compareAndSet(false, true)) {
    AnyBase.getContext match {
      case Some(context) =>
        val packageName = context.getPackageName()
        if (deinitializationLock.isSet) {
          deinitializationLock.unset()
          Futures.future {
            log.info("deinitializing AppControl for " + packageName)
            try {
              val timeout = AppComponent.deinitializationTimeout(context)
              if (AppComponent.deinitializationLock.get(0) == Some(false)) // AppComponent active
                try { AppComponent.publish(AppComponent.Event.Suspend(timeout)) } catch { case e => log.error(e.getMessage, e) }
              deinitializationLock.get(timeout) match {
                case Some(false) =>
                  log.info("deinitialization AppControl for " + packageName + " canceled")
                case _ =>
                  deinitRoutine(packageName)
              }
            } finally {
              deinitializationInProgressLock.synchronized {
                deinitializationInProgressLock.set(false)
                deinitializationInProgressLock.notifyAll
              }
            }
          }
        }
      case None =>
        log.fatal("unable to find deinitialization context")
        deinitializationInProgressLock.synchronized {
          deinitializationInProgressLock.set(false)
          deinitializationInProgressLock.notifyAll
        }
    }
  }
  private[lib] def deinitRoutine(packageName: String): Unit = synchronized {
    log.info("deinitialize AppControl for " + packageName)
    assert(inner != null)
    inner.unbind
    inner = null
    if (AnyBase.isLastContext && AppComponent.Inner != null) {
      log.info("AppControl hold last context. Clear.")
      AppComponent.deinitRoutine(packageName)
    }
  }
  def Inner = inner
  def ICtrlHost = inner.get()
  def isSuspend = deinitializationLock.get(0) == None
  @Loggable
  def isICtrlHostInstalled(ctx: Context): Boolean = {
    val pm = ctx.getPackageManager()
    val intent = new Intent(DIntent.HostService)
    val info = pm.resolveService(intent, 0);
    info != null
  }
  private def get(timeout: Long, throwError: Boolean, serviceInstance: SyncVar[Option[ICtrlHost]], stackTrace: Throwable = null): Option[ICtrlHost] = {
    if (timeout == -1) {
      if (!throwError)
        Option(serviceInstance.get)
      else
        serviceInstance.get match {
          case Some(service) => Some(Some(service))
          case None => None
          case null =>
            val stack = if (stackTrace == null) {
              val t = new Throwable("Intospecting stack frame")
              t.fillInStackTrace()
            } else
              stackTrace
            log.warn("uninitialized ICtrlHost at AppControl", stack)
            None
        }
    } else {
      assert(timeout >= 0)
      if (!throwError)
        serviceInstance.get(timeout)
      else
        serviceInstance.get(timeout) match {
          case Some(service) => Some(service)
          case None =>
            val stack = if (stackTrace == null) {
              val t = new Throwable("Intospecting stack frame")
              t.fillInStackTrace()
            } else
              stackTrace
            log.warn("uninitialized ICtrlHost at AppControl", stack)
            None
        }
    }
  } getOrElse None
  private def bind(caller: Context, ctrlBindContext: AtomicReference[Context], serviceInstance: SyncVar[Option[ICtrlHost]],
    ctrlConnection: ServiceConnection) = synchronized {
    if (!serviceInstance.isSet || (serviceInstance.isSet && serviceInstance.get == None)) {
      serviceInstance.unset()
      val intent = new Intent(DIntent.HostService)
      intent.putExtra("packageName", caller.getPackageName())
      log.info("bind to service " + DIntent.HostService)
      if (!isICtrlHostInstalled(caller)) {
        AppComponent.Inner.state.set(AppComponent.State(DState.Broken, Seq("error_digicontrol_not_found"), (a) =>
          AppComponent.Inner.showDialogSafe(a, InstallControl.getClass.getName, InstallControl.getId(a))))
        serviceInstance.set(None)
      } else if (!caller.bindService(intent, ctrlConnection, Context.BIND_AUTO_CREATE)) {
        AppComponent.Inner.state.set(AppComponent.State(DState.Broken, Seq("error_digicontrol_bind_failed")))
        serviceInstance.set(None)
      }
      ctrlBindContext.set(caller)
    } else
      log.debug("service " + DIntent.HostService + " already binding/binded")
  }
  private def bindStub(ctrlBindContext: AtomicReference[Context], serviceInstance: SyncVar[Option[ICtrlHost]],
    ctrlConnection: ServiceConnection, error: Seq[String]) = synchronized {
    AppComponent.Inner.state.set(AppComponent.State(DState.Broken, error))
    serviceInstance.set(None)
    ctrlBindContext.set(null)
  }
  private def unbind(ctrlBindContext: AtomicReference[Context], serviceInstance: SyncVar[Option[ICtrlHost]],
    ctrlConnection: ServiceConnection) = synchronized {
    if (serviceInstance.isSet && ctrlBindContext.get != null) {
      val caller = ctrlBindContext.getAndSet(null)
      serviceInstance.get match {
        case Some(service) =>
          log.info("unbind from service " + DIntent.HostService)
          service.update_shutdown_timer(caller.getPackageName(), 0)
          caller.unbindService(ctrlConnection)
        case None =>
          log.info("unbind from unexists service " + DIntent.HostService)
      }
      serviceInstance.unset()
    } else
      log.warn("service " + DIntent.HostService + " already unbinded")
  }
}
