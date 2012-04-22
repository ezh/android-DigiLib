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

protected class AppService private () extends Actor with Logging {
  private val lock = new Object
  protected lazy val serviceInstance = new SyncVar[ICtrlHost]()
  protected[lib] lazy val ctrlBindContext = new AtomicReference[Activity](null)
  protected lazy val ctrlBindCounter = new AtomicInteger()
  protected lazy val ctrlConnection = new ServiceConnection() with Logging {
    @Loggable
    def onServiceConnected(className: ComponentName, iservice: IBinder) {
      log.info("connected to DigiControl service")
      val service = ICtrlHost.Stub.asInterface(iservice)
      if (service != null)
        serviceInstance.set(service)
      if (getState == scala.actors.Actor.State.New) {
        log.debug("start AppService singleton actor")
        start() // start service singleton actor
      }
    }
    @Loggable
    def onServiceDisconnected(className: ComponentName) {
      log.warn("unexpected disconnect from DigiControl service")
      serviceInstance.unset()
    }
  }
  private val rebindInProgressLock = new AtomicBoolean(false)
  private val actorSecondLevelLock: AnyRef = new Object
  log.debug("alive")

  def act = {
    loop {
      react {
        // TODO suspend actor as scala.actors.Actor.State.Suspended when service lost
        case AppService.Message.Ping =>
          future {
            reply(log.debug("ping"))
          }
        case AppService.Message.ListDirectories(componentPackage, onCompleteCallback) =>
          assert(onCompleteCallback != null, { val e = "onCompleteCallback lost"; log.error(e); e })
          future {
            log.info("receive message ListDirectories for " + componentPackage)
            try {
              onCompleteCallback(componentDirectories(componentPackage))
            } catch {
              case e =>
                log.error(e.getMessage, e)
            }
            log.debug("return from message ListDirectories for " + componentPackage)
          }
        case AppService.Message.Start(componentPackage, onCompleteCallback) =>
          future {
            actorSecondLevelLock.synchronized {
              log.info("receive message Start for " + componentPackage)
              try {
                if (onCompleteCallback != null) onCompleteCallback(componentStart(componentPackage)) else componentStart(componentPackage)
              } catch {
                case e =>
                  log.error(e.getMessage, e)
              }
              log.debug("return from message Start for " + componentPackage)
            }
          }
        case AppService.Message.Status(componentPackage, onCompleteCallback) =>
          assert(onCompleteCallback != null, { val e = "onCompleteCallback lost"; log.error(e); e })
          future {
            actorSecondLevelLock.synchronized {
              log.info("receive message Status for " + componentPackage)
              try {
                onCompleteCallback(componentStatus(componentPackage))
              } catch {
                case e =>
                  log.error(e.getMessage, e)
              }
              log.debug("return from message Status for " + componentPackage)
            }
          }
        case AppService.Message.Stop(componentPackage, onCompleteCallback) =>
          future {
            actorSecondLevelLock.synchronized {
              log.info("receive message Stop for " + componentPackage)
              try {
                if (onCompleteCallback != null) onCompleteCallback(componentStop(componentPackage)) else componentStop(componentPackage)
              } catch {
                case e =>
                  log.error(e.getMessage, e)
              }
              log.debug("return from message Stop for " + componentPackage)
            }
          }
        case AppService.Message.Disconnect(componentPackage, processID, connectionID, onCompleteCallback) =>
          future {
            actorSecondLevelLock.synchronized {
              log.info("receive message Disconnect for " + componentPackage)
              try {
                if (onCompleteCallback != null)
                  onCompleteCallback(componentDisconnect(componentPackage, processID, connectionID))
                else
                  componentDisconnect(componentPackage, processID, connectionID)
              } catch {
                case e =>
                  log.error(e.getMessage, e)
              }
              log.debug("return from message Disconnect for " + componentPackage)
            }
          }
        case AppService.Message.ListInterfaces(componentPackage, onCompleteCallback) =>
          assert(onCompleteCallback != null, { val e = "onCompleteCallback lost"; log.error(e); e })
          future {
            actorSecondLevelLock.synchronized {
              log.info("receive message ListInterfaces for " + componentPackage)
              try {
                onCompleteCallback(componentActiveInterfaces(componentPackage))
              } catch {
                case e =>
                  log.error(e.getMessage, e)
              }
              log.debug("return from message ListInterfaces for " + componentPackage)
            }
          }
        case AppService.Message.ListPendingConnections(componentPackage, onCompleteCallback) =>
          assert(onCompleteCallback != null, { val e = "onCompleteCallback lost"; log.error(e); e })
          future {
            actorSecondLevelLock.synchronized {
              log.info("receive message ListPendingConnections for " + componentPackage)
              try {
                onCompleteCallback(componentPendingConnections(componentPackage))
              } catch {
                case e =>
                  log.error(e.getMessage, e)
              }
              log.debug("return from message ListPendingConnections for " + componentPackage)
            }
          }
        case message: AnyRef =>
          log.errorWhere("skip unknown message " + message.getClass.getName + ": " + message)
        case message =>
          log.errorWhere("skip unknown message " + message)
      }
    }
  }
  def get(): Option[ICtrlHost] =
    get(true)
  def get(timeout: Long): Option[ICtrlHost] =
    get(timeout, true)
  def get(throwError: Boolean): Option[ICtrlHost] =
    get(0, true)
  def get(timeout: Long, throwError: Boolean): Option[ICtrlHost] =
    AppService.get(timeout, throwError, serviceInstance)
  def getWait(): ICtrlHost =
    getWait(true)
  @Loggable
  def getWait(throwError: Boolean): ICtrlHost =
    AppService.get(-1, throwError, serviceInstance).get
  @Loggable
  def bind(caller: Activity): Unit =
    AppService.bind(caller, ctrlBindContext, ctrlBindCounter, serviceInstance, ctrlConnection)
  @Loggable
  def unbind(): Unit =
    AppService.unbind(ctrlBindContext, ctrlBindCounter, serviceInstance, ctrlConnection)
  @Loggable
  protected def rebind(timeout: Long): Option[ICtrlHost] = {
    if (rebindInProgressLock.compareAndSet(false, true)) {
      var result: Option[ICtrlHost] = None
      while (result == None && AppService.Inner != null) {
        AppActivity.Context.foreach(_ match {
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
  def componentDirectories(componentPackage: String): Option[(String, String)] = get(DTimeout.short) match {
    case Some(service) =>
      service.directories(componentPackage) match {
        case null =>
          None
        case list =>
          Some(list.head, list.last)
      }
    case None =>
      future { rebind(DTimeout.normal) }
      None
  }
  @Loggable
  protected def componentStart(componentPackage: String): Boolean = get(DTimeout.short) match {
    case Some(service) =>
      service.start(componentPackage)
    case None =>
      future { rebind(DTimeout.normal) }
      false
  }
  @Loggable
  protected def componentStatus(componentPackage: String): Either[String, ComponentState] = get(DTimeout.short) match {
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
      future { rebind(DTimeout.normal) }
      Left(componentPackage + " service unreachable")
  }
  @Loggable
  protected def componentStop(componentPackage: String): Boolean = get(DTimeout.short) match {
    case Some(service) =>
      service.stop(componentPackage)
    case None =>
      future { rebind(DTimeout.normal) }
      false
  }
  @Loggable
  protected def componentDisconnect(componentPackage: String, processID: Int, connectionID: Int): Boolean = get(DTimeout.short) match {
    case Some(service) =>
      service.disconnect(componentPackage, processID, connectionID)
    case None =>
      future { rebind(DTimeout.normal) }
      false
  }
  @Loggable
  def componentActiveInterfaces(componentPackage: String): Option[Seq[String]] = get(DTimeout.short) match {
    case Some(service) =>
      service.interfaces(componentPackage) match {
        case null =>
          None
        case list =>
          Some(list)
      }
    case None =>
      future { rebind(DTimeout.normal) }
      None
  }
  @Loggable
  def componentPendingConnections(componentPackage: String): Option[Seq[Intent]] = get(DTimeout.short) match {
    case Some(service) =>
      service.pending_connections(componentPackage) match {
        case null =>
          None
        case list =>
          Some(list)
      }
    case None =>
      future { rebind(DTimeout.normal) }
      None
  }
}

object AppService extends Logging {
  @volatile private var inner: AppService = null
  private val deinitializationLock = new SyncVar[Boolean]()
  private val deinitializationInProgressLock = new AtomicBoolean(false)
  private val deinitializationTimeout = DTimeout.longest

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
      resetNatives()
    }
    if (_inner != null)
      inner = _inner
    else
      inner = new AppService()
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
    log.info("resurrect AppService core subsystem")
  }
  private[lib] def deinit(): Unit = future {
    if (deinitializationInProgressLock.compareAndSet(false, true))
      try {
        val packageName = AnyBase.getContext.map(_.getPackageName()).getOrElse("UNKNOWN")
        log.info("deinitializing AppService for " + packageName)
        if (deinitializationLock.isSet)
          deinitializationLock.unset()
        deinitializationLock.get(deinitializationTimeout) match {
          case Some(false) =>
            log.info("deinitialization AppService for " + packageName + " canceled")
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
    log.info("deinitialize AppService for " + packageName)
    assert(inner != null)
    val _inner = inner
    inner = null
    if (AnyBase.isLastContext && AppActivity.Inner != null) {
      log.info("AppService hold last context. Clear.")
      AppActivity.deinitRoutine(packageName)
    }
    resetNatives
  }
  def Inner = inner
  def ICtrlHost = inner.get()
  @Loggable
  private def resetNatives() = future {
    val myUID = android.os.Process.myUid
    Android.withProcess({
      case (uid, gid, pid, path) => try {
        val cmd = new File(path, "cmdline")
        val stat = new File(path, "stat")
        if (uid == myUID && cmd.exists && stat.exists) {
          if (scala.io.Source.fromFile(cmd).getLines.exists(_.contains("armeabi/bridge"))) {
            val statParts = scala.io.Source.fromFile(stat).getLines.next.split("""\s+""")
            val ppid = statParts(3).toInt
            if (ppid <= 1) {
              log.warn("kill bridge with PID " + pid + " and PPID " + ppid)
              android.os.Process.sendSignal(pid, android.os.Process.SIGNAL_KILL)
            } else
              log.debug("detect bridge with PID " + pid + " and PPID " + ppid)
          }
        }
      } catch {
        case e =>
          log.error(e.getMessage, e)
      }
    })
  }
  private def get(timeout: Long, throwError: Boolean, serviceInstance: SyncVar[ICtrlHost]): Option[ICtrlHost] = synchronized {
    if (timeout == -1) {
      if (!throwError)
        return Some(serviceInstance.get)
      serviceInstance.get match {
        case service: ICtrlHost => Some(service)
        case null =>
          val t = new Throwable("Intospecting stack frame")
          t.fillInStackTrace()
          log.error("uninitialized ICtrlHost at AppService: " + t.getStackTraceString)
          Some(null)
      }
    } else {
      assert(timeout >= 0)
      if (!throwError)
        return serviceInstance.get(timeout)
      serviceInstance.get(timeout) match {
        case result @ Some(service) => result
        case None =>
          val t = new Throwable("Intospecting stack frame")
          t.fillInStackTrace()
          log.error("uninitialized ICtrlHost at AppService: " + t.getStackTraceString)
          None
      }
    }
  }
  private def bind(caller: Activity, ctrlBindContext: AtomicReference[Activity], ctrlBindCounter: AtomicInteger,
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
          AppActivity.Inner.state.set(AppActivity.State(DState.Broken, Android.getString(caller, "error_control_notfound").
            getOrElse("Bind failed, DigiControl application not found"),
            () => caller.showDialog(InstallControl.getId(caller))))
      } else
        log.fatal("service " + DIntent.HostService + " already binding/binded")
  }
  private def unbind(ctrlBindContext: AtomicReference[Activity], ctrlBindCounter: AtomicInteger,
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
  object Message {
    object Ping
    case class ListDirectories(componentPackage: String, onCompleteCallback: (Option[(String, String)]) => Unit)
    case class Start(componentPackage: String, onCompleteCallback: (Boolean) => Unit = null)
    case class Status(componentPackage: String, onCompleteCallback: (Either[String, ComponentState]) => Unit)
    case class Stop(componentPackage: String, onCompleteCallback: (Boolean) => Unit = null)
    case class Disconnect(componentPackage: String, processID: Int, connectionID: Int, onCompleteCallback: (Boolean) => Unit = null)
    case class ListInterfaces(componentPackage: String, onCompleteCallback: (Option[Seq[String]]) => Unit)
    case class ListPendingConnections(componentPackage: String, onCompleteCallback: (Option[Seq[Intent]]) => Unit)
  }
}
