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
import java.util.concurrent.locks.ReentrantLock

import scala.actors.Futures.future
import scala.actors.Actor
import scala.collection.JavaConversions._
import scala.concurrent.SyncVar

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.InstallControl
import org.digimead.digi.ctrl.lib.info.ComponentState
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
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
  protected lazy val serviceInstance = new AtomicReference[ICtrlHost](null)
  protected lazy val ctrlBindContext = new AtomicReference[Activity](null)
  protected lazy val ctrlBindCounter = new AtomicInteger()

  protected lazy val ctrlConnection = new ServiceConnection() with Logging {
    @Loggable
    def onServiceConnected(className: ComponentName, iservice: IBinder) {
      log.info("connected to DigiControl service")
      serviceInstance.set(ICtrlHost.Stub.asInterface(iservice))
      if (getState == scala.actors.Actor.State.New) {
        log.debug("start AppService singleton actor")
        start() // start service singleton actor
      }
    }
    @Loggable
    def onServiceDisconnected(className: ComponentName) {
      log.warn("unexpected disconnect from DigiControl service")
      serviceInstance.set(null)
    }
  }

  log.debug("alive")
  def act = {
    loop {
      react {
        // TODO suspend actor as scala.actors.Actor.State.Suspended when service lost
        case AppService.Message.Ping =>
          reply(log.debug("ping"))
        case AppService.Message.Start(componentPackage, onCompleteCallback) =>
          log.info("receive message Start for " + componentPackage)
          try {
            if (onCompleteCallback != null) onCompleteCallback(componentStart(componentPackage)) else componentStart(componentPackage)
          } catch {
            case e =>
              log.error(e.getMessage, e)
          }
          log.debug("return from message Start for " + componentPackage)
        case AppService.Message.Status(componentPackage, onCompleteCallback) =>
          assert(onCompleteCallback != null, { val e = "onCompleteCallback lost"; log.error(e); e })
          log.info("receive message Status for " + componentPackage)
          try {
            onCompleteCallback(componentStatus(componentPackage))
          } catch {
            case e =>
              log.error(e.getMessage, e)
          }
          log.debug("return from message Status for " + componentPackage)
        case AppService.Message.Stop(componentPackage, onCompleteCallback) =>
          log.info("receive message Stop for " + componentPackage)
          try {
            if (onCompleteCallback != null) onCompleteCallback(componentStop(componentPackage)) else componentStop(componentPackage)
          } catch {
            case e =>
              log.error(e.getMessage, e)
          }
          log.debug("return from message Stop for " + componentPackage)
        case AppService.Message.Disconnect(componentPackage, processID, connectionID, onCompleteCallback) =>
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
        case AppService.Message.ListInterfaces(componentPackage, onCompleteCallback) =>
          assert(onCompleteCallback != null, { val e = "onCompleteCallback lost"; log.error(e); e })
          log.info("receive message ListInterfaces for " + componentPackage)
          try {
            onCompleteCallback(componentActiveInterfaces(componentPackage))
          } catch {
            case e =>
              log.error(e.getMessage, e)
          }
          log.debug("return from message ListInterfaces for " + componentPackage)
        case message: AnyRef =>
          log.error("skip unknown message " + message.getClass.getName + ": " + message)
        case message =>
          log.error("skip unknown message " + message)
      }
    }
  }
  def get(): Option[ICtrlHost] = get(true)
  def get(throwError: Boolean): Option[ICtrlHost] = AppService.get(throwError, serviceInstance)
  @Loggable
  def getWait() = {
    this !? AppService.Message.Ping // actor start ONLY after successful binding
    get
  }
  @Loggable
  def bind(caller: Activity): Unit =
    AppService.bind(caller, ctrlBindContext, ctrlBindCounter, serviceInstance, ctrlConnection)
  @Loggable
  def unbind(): Unit =
    AppService.unbind(ctrlBindContext, ctrlBindCounter, serviceInstance, ctrlConnection)
  @Loggable
  protected def componentStart(componentPackage: String): Boolean = get match {
    case Some(service) =>
      service.start(componentPackage)
    case None =>
      false
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
      Left(componentPackage + " service unreachable")
  }
  @Loggable
  protected def componentStop(componentPackage: String): Boolean = get match {
    case Some(service) =>
      service.stop(componentPackage)
    case None =>
      false
  }
  @Loggable
  protected def componentDisconnect(componentPackage: String, processID: Int, connectionID: Int): Boolean = get match {
    case Some(service) =>
      service.disconnect(componentPackage, processID, connectionID)
    case None =>
      false
  }
  @Loggable
  def componentActiveInterfaces(componentPackage: String): Option[Seq[String]] = get match {
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

object AppService extends Logging {
  @volatile private var inner: AppService = null
  private val deinitializationLock = new SyncVar[Boolean]()
  private val deinitializationProgress = new ReentrantLock
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
      resetNatives(root)
    }
    if (_inner != null)
      inner = _inner
    else
      inner = new AppService()
  }
  private[lib] def resurrect() =
    if (deinitializationProgress.isLocked) {
      log.info("resurrect AppService core subsystem")
      deinitializationLock.set(false)
    }
  private[lib] def deinit(): Unit = future {
    if (deinitializationProgress.tryLock)
      try {
        val packageName = AnyBase.getContext.map(_.getPackageName()).getOrElse("UNKNOWN")
        log.info("deinitializing AppService for " + packageName)
        if (deinitializationLock.isSet)
          deinitializationLock.unset
        deinitializationLock.get(deinitializationTimeout) match {
          case Some(false) =>
            log.info("deinitialization AppService for " + packageName + " canceled")
          case _ =>
            deinitRoutine(packageName)
        }
      } finally {
        deinitializationProgress.unlock
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
    AnyBase.getContext.foreach(resetNatives)
  }
  def Inner = inner
  def ICtrlHost = inner.get()
  //  def initialized() = synchronized {
  //    while (inner != null)
  //      wait()
  //  }
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
  private def get(throwError: Boolean, serviceInstance: AtomicReference[ICtrlHost]): Option[ICtrlHost] = synchronized {
    if (!throwError)
      return serviceInstance.get() match {
        case service: ICtrlHost => Some(service)
        case null => None
      }
    serviceInstance.get match {
      case service: ICtrlHost => Some(service)
      case null =>
        val t = new Throwable("Intospecting stack frame")
        t.fillInStackTrace()
        log.error("uninitialized ICtrlHost at AppService: " + t.getStackTraceString)
        None
    }
  }
  private def bind(caller: Activity, ctrlBindContext: AtomicReference[Activity], ctrlBindCounter: AtomicInteger,
    serviceInstance: AtomicReference[ICtrlHost], ctrlConnection: ServiceConnection) = synchronized {
    if (ctrlBindCounter.incrementAndGet() == 1)
      if (serviceInstance.get == null && ctrlBindContext.compareAndSet(null, caller)) {
        val intent = new Intent(DIntent.HostService)
        intent.putExtra("packageName", caller.getPackageName())
        val successful = new SyncVar[Boolean]
        if (isICtrlHostInstalled(caller)) {
          log.info("bind to service " + DIntent.HostService)
          caller.runOnUiThread(new Runnable { def run = successful.set(caller.bindService(intent, ctrlConnection, Context.BIND_AUTO_CREATE)) })
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
    serviceInstance: AtomicReference[ICtrlHost], ctrlConnection: ServiceConnection) = synchronized {
    if (ctrlBindCounter.decrementAndGet() == 0)
      if (serviceInstance.get != null && ctrlBindContext.get != null) {
        log.info("unbind from service " + DIntent.HostService)
        val caller = ctrlBindContext.getAndSet(null)
        caller.runOnUiThread(new Runnable { def run = caller.unbindService(ctrlConnection) })
        serviceInstance.set(null)
      } else
        log.warn("service already unbinded")
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
    case class Start(componentPackage: String, onCompleteCallback: (Boolean) => Unit = null)
    case class Status(componentPackage: String, onCompleteCallback: (Either[String, ComponentState]) => Unit)
    case class Stop(componentPackage: String, onCompleteCallback: (Boolean) => Unit = null)
    case class Disconnect(componentPackage: String, processID: Int, connectionID: Int, onCompleteCallback: (Boolean) => Unit = null)
    case class ListInterfaces(componentPackage: String, onCompleteCallback: (Option[Seq[String]]) => Unit = null)
  }
}
