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
import java.util.concurrent.atomic.AtomicBoolean
import scala.actors.Futures.future
import scala.actors.Future
import scala.collection.JavaConversions._
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.info.ComponentState
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.digimead.digi.ctrl.lib.Activity
import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.ICtrlHost
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper

protected class AppControl private () extends Logging {
  protected lazy val ready = new SyncVar[Option[ICtrlHost]]()
  private val ctrlBindTimeout = DTimeout.short
  protected[lib] lazy val ctrlBindContext = new AtomicReference[Context](null)
  protected lazy val ctrlConnection = new ServiceConnection() with Logging {
    @Loggable
    def onServiceConnected(className: ComponentName, iservice: IBinder) {
      log.info("connected to DigiControl service")
      val service = ICtrlHost.Stub.asInterface(iservice)
      if (service != null)
        ready.set(Some(service))
    }
    @Loggable
    def onServiceDisconnected(className: ComponentName) {
      log.warn("unexpected disconnect from DigiControl service")
      ready.unset()
    }
  }
  private val rebindInProgressLock = new AtomicBoolean(false)
  private val actorSecondLevelLock: AnyRef = new Object
  private val uiThreadID = Looper.getMainLooper.getThread.getId
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
      future {
        if (rebindInProgressLock.compareAndSet(false, true)) {
          unbind()
          var result: Option[ICtrlHost] = None
          while (result == None && AppControl.Inner != null) {
            AppComponent.Context.foreach(_ match {
              case activity: Activity =>
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
  @Loggable
  def callListDirectories(componentPackage: String, allowCallFromUI: Boolean = false): Future[Option[(String, String)]] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callListDirectories AppControl function from UI thread")
    val t = new Throwable("Intospecting callListDirectories")
    t.fillInStackTrace()
    future {
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
  @Loggable
  def callStart(componentPackage: String, allowCallFromUI: Boolean = false): Future[Boolean] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callStart AppControl function from UI thread")
    val t = new Throwable("Intospecting callStart")
    t.fillInStackTrace()
    future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
        case Some(service) =>
          service.start(componentPackage)
        case None =>
          false
      }
    }
  }
  @Loggable
  def callStatus(componentPackage: String, allowCallFromUI: Boolean = false): Future[Either[String, ComponentState]] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callStatus AppControl function from UI thread")
    val t = new Throwable("Intospecting callStatus")
    t.fillInStackTrace()
    future {
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
          val text = if (ready.isSet && ready == None) {
            AppComponent.Context match {
              case Some(context) =>
                Android.getString(context, "error_component_status_unavailable").
                  getOrElse("%1$s component status unavailable").format(componentPackage)
              case None =>
                componentPackage + "component status unavailable"
            }
          } else {
            AppComponent.Context match {
              case Some(context) =>
                Android.getString(context, "error_digicontrol_not_found").
                  getOrElse("DigiControl not found")
              case None =>
                "DigiControl not found"
            }
          }
          Left(text)
      }
    }
  }
  @Loggable
  def callStop(componentPackage: String, allowCallFromUI: Boolean = false): Future[Boolean] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callStop AppControl function from UI thread")
    val t = new Throwable("Intospecting callStop")
    t.fillInStackTrace()
    future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
        case Some(service) =>
          service.stop(componentPackage)
        case None =>
          false
      }
    }
  }
  @Loggable
  def callDisconnect(componentPackage: String, processID: Int, connectionID: Int, allowCallFromUI: Boolean = false): Future[Boolean] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callDisconnect AppControl function from UI thread")
    val t = new Throwable("Intospecting callDisconnect")
    t.fillInStackTrace()
    future {
      get(ctrlBindTimeout, true, t) orElse rebind(ctrlBindTimeout) match {
        case Some(service) =>
          service.disconnect(componentPackage, processID, connectionID)
        case None =>
          false
      }
    }
  }
  @Loggable
  def callListActiveInterfaces(componentPackage: String, allowCallFromUI: Boolean = false): Future[Option[Seq[String]]] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callListActiveInterfaces AppControl function from UI thread")
    val t = new Throwable("Intospecting callListActiveInterfaces")
    t.fillInStackTrace()
    future {
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
  @Loggable
  def callListPendingConnections(componentPackage: String, allowCallFromUI: Boolean = false): Future[Option[Seq[Intent]]] = {
    if (!allowCallFromUI && Thread.currentThread.getId == uiThreadID)
      log.fatal("callListPendingConnections AppControl function from UI thread")
    val t = new Throwable("Intospecting callListPendingConnections")
    t.fillInStackTrace()
    future {
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
}

object AppControl extends Logging {
  @volatile private var inner: AppControl = null
  private val deinitializationLock = new SyncVar[Boolean]()
  private val deinitializationInProgressLock = new AtomicBoolean(false)
  private val deinitializationTimeout = DTimeout.longest

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
      inner = new AppControl()
    }
  }
  private[lib] def resurrect() = {
    deinitializationLock.set(false)
    if (deinitializationInProgressLock.get) {
      log.debug("deinitialization in progress, waiting...")
      deinitializationInProgressLock.synchronized {
        while (deinitializationInProgressLock.get)
          deinitializationInProgressLock.wait
      }
    }
    log.info("resurrect AppControl core subsystem")
  }
  private[lib] def deinit(): Unit = future {
    if (deinitializationInProgressLock.compareAndSet(false, true))
      try {
        val packageName = AnyBase.getContext.map(_.getPackageName()).getOrElse("UNKNOWN")
        log.info("deinitializing AppControl for " + packageName)
        if (deinitializationLock.isSet)
          deinitializationLock.unset()
        deinitializationLock.get(deinitializationTimeout) match {
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
  private[lib] def deinitRoutine(packageName: String): Unit = synchronized {
    log.info("deinitialize AppControl for " + packageName)
    assert(inner != null)
    val _inner = inner
    inner = null
    if (AnyBase.isLastContext && AppComponent.Inner != null) {
      log.info("AppControl hold last context. Clear.")
      AppComponent.deinitRoutine(packageName)
    }
  }
  def Inner = inner
  def ICtrlHost = inner.get()
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
            log.error("uninitialized ICtrlHost at AppControl: " + stack.getStackTraceString)
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
            log.error("uninitialized ICtrlHost at AppControl: " + stack.getStackTraceString)
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
      if (!caller.bindService(intent, ctrlConnection, Context.BIND_AUTO_CREATE) && !isICtrlHostInstalled(caller)) {
        AppComponent.Inner.state.set(AppComponent.State(DState.Broken, Android.getString(caller, "error_digicontrol_not_found").
          getOrElse("Bind failed, DigiControl application not found")))
        serviceInstance.set(None)
      }
      ctrlBindContext.set(caller)
    } else
      log.debug("service " + DIntent.HostService + " already binding/binded")
  }
  private def unbind(ctrlBindContext: AtomicReference[Context], serviceInstance: SyncVar[Option[ICtrlHost]],
    ctrlConnection: ServiceConnection) = synchronized {
    if (serviceInstance.isSet && ctrlBindContext.get != null) {
      val caller = ctrlBindContext.getAndSet(null)
      serviceInstance.get match {
        case Some(service) =>
          log.info("unbind from service " + DIntent.HostService)
          caller.unbindService(ctrlConnection)
        case None =>
          log.info("unbind from unexists service " + DIntent.HostService)
      }
      serviceInstance.unset()
    } else
      log.warn("service " + DIntent.HostService + " already unbinded")
  }
  @Loggable
  def isICtrlHostInstalled(ctx: Context): Boolean = {
    val pm = ctx.getPackageManager()
    val intent = new Intent(DIntent.HostService)
    val info = pm.resolveService(intent, 0);
    info != null
  }
}
