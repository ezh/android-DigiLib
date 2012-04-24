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
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import scala.actors.Futures.future
import scala.actors.Actor
import scala.collection.JavaConversions._
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.InstallControl
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
import scala.actors.Future

protected class AppControl private () extends Logging {
  protected lazy val ready = new SyncVar[ICtrlHost]()
  private val ctrlBindTimeout = DTimeout.long
  protected[lib] lazy val ctrlBindContext = new AtomicReference[Context](null)
  protected lazy val ctrlBindCounter = new AtomicInteger()
  protected lazy val ctrlConnection = new ServiceConnection() with Logging {
    @Loggable
    def onServiceConnected(className: ComponentName, iservice: IBinder) {
      log.info("connected to DigiControl service")
      val service = ICtrlHost.Stub.asInterface(iservice)
      if (service != null)
        ready.set(service)
    }
    @Loggable
    def onServiceDisconnected(className: ComponentName) {
      log.warn("unexpected disconnect from DigiControl service")
      ready.unset()
    }
  }
  private val rebindInProgressLock = new AtomicBoolean(false)
  private val actorSecondLevelLock: AnyRef = new Object
  log.debug("alive")

  def get(): Option[ICtrlHost] =
    get(true)
  def get(timeout: Long): Option[ICtrlHost] =
    get(timeout, true)
  def get(throwError: Boolean): Option[ICtrlHost] =
    get(0, true)
  def get(timeout: Long, throwError: Boolean): Option[ICtrlHost] =
    AppControl.get(timeout, throwError, ready)
  def getWait(): ICtrlHost =
    getWait(true)
  @Loggable
  def getWait(throwError: Boolean): ICtrlHost =
    AppControl.get(-1, throwError, ready).get
  @Loggable
  def bind(caller: Context): Unit =
    AppControl.bind(caller, ctrlBindContext, ctrlBindCounter, ready, ctrlConnection)
  @Loggable
  def unbind(): Unit =
    AppControl.unbind(ctrlBindContext, ctrlBindCounter, ready, ctrlConnection)
  @Loggable
  protected def rebind(timeout: Long): Option[ICtrlHost] = {
    if (rebindInProgressLock.compareAndSet(false, true)) {
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
        result = get(timeout)
      }
      log.warn("rebind ICtrlHost service finished, result: " + result)
      rebindInProgressLock.set(false)
      result
    } else
      get(0)
  }
  @Loggable
  def callListDirectories(componentPackage: String): Future[Option[(String, String)]] = future {
    get(ctrlBindTimeout) match {
      case Some(service) =>
        service.directories(componentPackage) match {
          case null =>
            None
          case list =>
            Some(list.head, list.last)
        }
      case None =>
        future { rebind(ctrlBindTimeout) }
        None
    }
  }
  @Loggable
  def callStart(componentPackage: String): Future[Boolean] = future {
    get(ctrlBindTimeout) match {
      case Some(service) =>
        service.start(componentPackage)
      case None =>
        future { rebind(ctrlBindTimeout) }
        false
    }
  }
  @Loggable
  def callStatus(componentPackage: String): Future[Either[String, ComponentState]] = future {
    get(ctrlBindTimeout) match {
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
        future { rebind(ctrlBindTimeout) }
        Left(componentPackage + " service unreachable")
    }
  }
  @Loggable
  def callStop(componentPackage: String): Future[Boolean] = future {
    get(ctrlBindTimeout) match {
      case Some(service) =>
        service.stop(componentPackage)
      case None =>
        future { rebind(ctrlBindTimeout) }
        false
    }
  }
  @Loggable
  def callDisconnect(componentPackage: String, processID: Int, connectionID: Int): Future[Boolean] = future {
    get(ctrlBindTimeout) match {
      case Some(service) =>
        service.disconnect(componentPackage, processID, connectionID)
      case None =>
        future { rebind(ctrlBindTimeout) }
        false
    }
  }
  @Loggable
  def callListActiveInterfaces(componentPackage: String): Future[Option[Seq[String]]] = future {
    get(ctrlBindTimeout) match {
      case Some(service) =>
        service.interfaces(componentPackage) match {
          case null =>
            None
          case list =>
            Some(list)
        }
      case None =>
        future { rebind(ctrlBindTimeout) }
        None
    }
  }
  @Loggable
  def callListPendingConnections(componentPackage: String): Future[Option[Seq[Intent]]] = future {
    get(ctrlBindTimeout) match {
      case Some(service) =>
        service.pending_connections(componentPackage) match {
          case null =>
            None
          case list =>
            Some(list)
        }
      case None =>
        future { rebind(ctrlBindTimeout) }
        None
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
    } else {
      log.info("initialize AppControl for " + root.getPackageName())
      resetNatives()
    }
    if (_inner != null)
      inner = _inner
    else
      inner = new AppControl()
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
    resetNatives
  }
  def Inner = inner
  def ICtrlHost = inner.get()
  @Loggable
  private def resetNatives() = future {
    val myUID = android.os.Process.myUid
    Android.withProcess({
      case (name, uid, gid, pid, ppid, path) => try {
        if (uid == myUID && name == "BRIDGE")
          if (ppid <= 1) {
            log.warn("kill bridge with PID " + pid + " and PPID " + ppid)
            android.os.Process.sendSignal(pid, android.os.Process.SIGNAL_KILL)
          } else
            log.debug("detect bridge with PID " + pid + " and PPID " + ppid)
      } catch {
        case e =>
          log.error(e.getMessage, e)
      }
    })
  }
  private def get(timeout: Long, throwError: Boolean, serviceInstance: SyncVar[ICtrlHost]): Option[ICtrlHost] = {
    if (timeout == -1) {
      if (!throwError)
        Some(serviceInstance.get)
      else
        serviceInstance.get match {
          case service: ICtrlHost => Some(service)
          case null =>
            val t = new Throwable("Intospecting stack frame")
            t.fillInStackTrace()
            log.error("uninitialized ICtrlHost at AppControl: " + t.getStackTraceString)
            Some(null)
        }
    } else {
      assert(timeout >= 0)
      if (!throwError)
        serviceInstance.get(timeout)
      else
        serviceInstance.get(timeout) match {
          case result @ Some(service) => result
          case None =>
            val t = new Throwable("Intospecting stack frame")
            t.fillInStackTrace()
            log.error("uninitialized ICtrlHost at AppControl: " + t.getStackTraceString)
            None
        }
    }
  }
  private def bind(caller: Context, ctrlBindContext: AtomicReference[Context], ctrlBindCounter: AtomicInteger,
    serviceInstance: SyncVar[ICtrlHost], ctrlConnection: ServiceConnection) = synchronized {
    if (ctrlBindCounter.incrementAndGet() == 1)
      if (!serviceInstance.isSet && ctrlBindContext.compareAndSet(null, caller)) {
        val intent = new Intent(DIntent.HostService)
        intent.putExtra("packageName", caller.getPackageName())
        val successful = new SyncVar[Boolean]
        if (isICtrlHostInstalled(caller)) {
          log.info("bind to service " + DIntent.HostService)
          successful.set(caller.bindService(intent, ctrlConnection, Context.BIND_AUTO_CREATE))
        } else {
          log.warn(DIntent.HostService + " not installed")
          successful.set(false)
        }
        if (!successful.get)
          AppComponent.Inner.state.set(AppComponent.State(DState.Broken, Android.getString(caller, "error_control_notfound").
            getOrElse("Bind failed, DigiControl application not found")))
        // () => caller.showDialog(InstallControl.getId(caller))
      } else
        log.fatal("service " + DIntent.HostService + " already binding/binded")
    else
      log.debug("service " + DIntent.HostService + " already binding/binded")
  }
  private def unbind(ctrlBindContext: AtomicReference[Context], ctrlBindCounter: AtomicInteger,
    serviceInstance: SyncVar[ICtrlHost], ctrlConnection: ServiceConnection) = synchronized {
    if (ctrlBindCounter.decrementAndGet() == 0)
      if (serviceInstance.isSet) {
        if (serviceInstance.get != null && ctrlBindContext.get != null) {
          log.info("unbind from service " + DIntent.HostService)
          val caller = ctrlBindContext.getAndSet(null)
          caller.unbindService(ctrlConnection)
          serviceInstance.unset()
        } else
          log.warn("service already unbinded")
      } else {
        ctrlBindContext.set(null)
        serviceInstance.unset()
        log.warn("service lost")
      }
  }
  @Loggable
  def isICtrlHostInstalled(ctx: Context): Boolean = {
    val pm = ctx.getPackageManager()
    val intent = new Intent(DIntent.HostService)
    val info = pm.resolveService(intent, 0);
    info != null
  }
}
