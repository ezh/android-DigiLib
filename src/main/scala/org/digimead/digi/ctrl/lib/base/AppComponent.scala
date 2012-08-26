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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import scala.Option.option2Iterable
import scala.actors.Futures
import scala.annotation.implicitNotFound
import scala.collection.immutable.LongMap
import scala.collection.mutable.HashMap
import scala.collection.mutable.Publisher
import scala.collection.mutable.SynchronizedMap
import scala.xml.XML

import org.digimead.digi.ctrl.ICtrlComponent
import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.XAndroid
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DPermission
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.Preferences
import org.digimead.digi.ctrl.lib.info.ComponentInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.message.DMessage
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.digimead.digi.ctrl.lib.util.Version

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle

protected class AppComponent private () extends Logging {
  /** profiling support */
  private val ppLoading = AnyBase.ppGroup.start("AppComponent")
  lazy val state = new AppComponent.StateContainer
  lazy val internalStorage = AppComponent.Context.flatMap(ctx => Option(ctx.getFilesDir()))
  // -rwx--x--x 711
  lazy val externalStorage = AppComponent.Context.flatMap(ctx => Common.getDirectory(ctx, "var", false, Some(false), Some(false), Some(true)))
  // -rwx------ 711
  lazy val enginePath = AppComponent.Context.flatMap(ctx => Common.getDirectory(ctx, DConstant.enginePath, true, Some(false), Some(false), Some(true)))
  lazy val engineManifestPath = enginePath.map(enginePath => new File(enginePath, "EngineManifest.xml"))
  lazy val wrapperManifest: Option[scala.xml.Elem] = AppComponent.Context.map(ctx => XML.load(ctx.getAssets().open("WrapperManifest.xml")))
  lazy val preferredOrientation = new AtomicInteger(ActivityInfo.SCREEN_ORIENTATION_SENSOR)
  private[lib] lazy val bindedICtrlPool = new HashMap[String, (Context, ServiceConnection, ICtrlComponent)] with SynchronizedMap[String, (Context, ServiceConnection, ICtrlComponent)]
  private[lib] lazy val lockRotationCounter = new AtomicInteger(0)
  ppLoading.stop

  @Loggable
  def disableRotation() = AppComponent.Context match {
    case Some(activity) if activity.isInstanceOf[Activity] =>
      if (lockRotationCounter.getAndIncrement == 0)
        XAndroid.disableRotation(activity.asInstanceOf[Activity])
      log.trace("increment rotation lock to " + lockRotationCounter.get)
    case context =>
      log.warn("unable to disable rotation, invalid context " + context)
  }
  @Loggable
  def enableRotation() = AppComponent.Context match {
    case Some(activity) if activity.isInstanceOf[Activity] =>
      lockRotationCounter.compareAndSet(0, 1)
      if (lockRotationCounter.decrementAndGet == 0)
        XAndroid.enableRotation(activity.asInstanceOf[Activity])
      log.trace("decrement rotation lock to " + lockRotationCounter.get)
    case context =>
      log.warn("unable to enable rotation, invalid context " + context)
  }
  @Loggable
  def getCachedComponentInfo(locale: String, localeLanguage: String,
    iconExtractor: (Seq[(ComponentInfo.IconType, String)]) => Seq[Option[Array[Byte]]] = (icons: Seq[(ComponentInfo.IconType, String)]) => {
      icons.map {
        case (iconType, url) =>
          iconType match {
            case icon: ComponentInfo.Thumbnail.type =>
              None
            case icon: ComponentInfo.LDPI.type =>
              None
            case icon: ComponentInfo.MDPI.type =>
              None
            case icon: ComponentInfo.HDPI.type =>
              None
            case icon: ComponentInfo.XHDPI.type =>
              None
          }
      }
    }): Option[ComponentInfo] = AppComponent.synchronized {
    wrapperManifest.flatMap {
      appManifest =>
        AppCache.actor !? AppCache.Message.GetByID(0, appManifest.hashCode.toString + locale + localeLanguage) match {
          case Some(info) =>
            Some(info.asInstanceOf[ComponentInfo])
          case None =>
            val result = getComponentInfo(locale, localeLanguage, iconExtractor)
            result.foreach(r => AppCache.actor ! AppCache.Message.UpdateByID(0, appManifest.hashCode.toString + locale + localeLanguage, r))
            result
        }
    }
  }
  @Loggable
  def getComponentInfo(locale: String, localeLanguage: String,
    iconExtractor: (Seq[(ComponentInfo.IconType, String)]) => Seq[Option[Array[Byte]]] = (icons: Seq[(ComponentInfo.IconType, String)]) => {
      icons.map {
        case (iconType, url) =>
          iconType match {
            case icon: ComponentInfo.Thumbnail.type =>
              None
            case icon: ComponentInfo.LDPI.type =>
              None
            case icon: ComponentInfo.MDPI.type =>
              None
            case icon: ComponentInfo.HDPI.type =>
              None
            case icon: ComponentInfo.XHDPI.type =>
              None
          }
      }
    }): Option[ComponentInfo] = {
    for {
      appManifest <- wrapperManifest
    } yield {
      AppCache.actor !? AppCache.Message.GetByID(0, appManifest.hashCode.toString) match {
        case Some(info) =>
          Some(info.asInstanceOf[ComponentInfo])
        case None =>
          val result = ComponentInfo(appManifest, locale, localeLanguage, iconExtractor)
          result.foreach(r => AppCache.actor ! AppCache.Message.UpdateByID(0, appManifest.hashCode.toString, r))
          result
      }
    }
  } getOrElse None
  @Loggable
  def sendPrivateBroadcast(intent: Intent, flags: Seq[Int] = Seq()) = AppComponent.Context foreach {
    context =>
      intent.putExtra("__private__", true)
      flags.foreach(intent.addFlags)
      context.sendBroadcast(intent, DPermission.Base)
  }
  @Loggable
  def sendPrivateOrderedBroadcast(intent: Intent, flags: Seq[Int] = Seq()) = AppComponent.Context foreach {
    context =>
      intent.putExtra("__private__", true)
      flags.foreach(intent.addFlags)
      context.sendOrderedBroadcast(intent, DPermission.Base)
  }
  @Loggable
  def giveTheSign(key: Uri, data: Bundle): Unit = AppComponent.Context foreach {
    context =>
      log.debug("send the " + key)
      val intent = new Intent(DIntent.SignResponse, key)
      intent.putExtras(data)
      sendPrivateBroadcast(intent)
  }
  // onFinish(component state, service state, service isBusy flag) => Unit
  @Loggable
  def synchronizeStateWithICtrlHost(onFinish: (DState.Value, DState.Value, Boolean) => Unit = null): Unit = AppComponent.Context.foreach {
    activity =>
      if (AppComponent.this.state.get.value == DState.Broken && AppControl.Inner.isBound == Some(false)) {
        log.warn("DigiControl unavailable and state already broken")
        if (onFinish != null) onFinish(DState.Broken, DState.Unknown, false)
        return
      }
      Futures.future {
        AppControl.Inner.callStatus(activity.getPackageName)() match {
          case Right(componentState) =>
            val appState = componentState.state match {
              case DState.Active =>
                AppComponent.State(DState.Active)
              case DState.Broken =>
                if (componentState.reason == None)
                  AppComponent.State(DState.Broken, Seq("error_digicontrol_service_failed"))
                else
                  AppComponent.State(DState.Broken, componentState.reason.toSeq)
              case _ =>
                AppComponent.State(DState.Passive)
            }
            AppComponent.this.state.set(appState)
            if (onFinish != null) onFinish(DState.Passive, componentState.serviceState, componentState.serviceBusy)
          case Left(error) =>
            val appState = if (error == "error_digicontrol_not_found")
              AppComponent.State(DState.Broken, Seq(error), (a) => {
                log.___gaze("SHOW")
                //SafeDialog.show(a, InstallControl.getClass.getName, InstallControl.getId(a))
              })
            else
              AppComponent.State(DState.Broken, Seq(error))
            AppComponent.this.state.set(appState)
            if (onFinish != null) onFinish(DState.Broken, DState.Unknown, false)
        }
      }
  }
  @Loggable
  def minVersionRequired(componentPackage: String): Option[Version] = try {
    wrapperManifest.flatMap {
      xml =>
        val node = xml \\ "required" find { _.text == componentPackage }
        node.flatMap(_.attribute("version"))
        node.flatMap(_.attribute("version")).map(n => new Version(n.toString))
    }
  } catch {
    case e =>
      log.error(e.getMessage, e)
      None
  }
}

sealed trait AppComponentEvent

object AppComponent extends Logging with Publisher[AppComponentEvent] {
  /** profiling support */
  private val ppLoading = AnyBase.ppGroup.start("AppComponent$")
  /** AppComponent internal singleton */
  @volatile private var inner: AppComponent = null
  /** shutdown flag that enable shutdown application after deinitialization */
  @volatile private[base] var shutdown = true
  /** deinitialization flag that lock on deinit routine */
  private[base] val deinitializationLock = new SyncVar[Boolean]()
  deinitializationLock.set(false)
  ppLoading.stop

  @Loggable
  def deinitializationTimeout(context: Context): Int = {
    implicit val dispatcher = new Dispatcher() { def process(message: DMessage): Unit = {} }
    // dispatch messages of Preferences.ShutdownTimeout to the void, noWhere is the destiny 
    val result = Preferences.ShutdownTimeout.get(context)
    log.debug("retrieve idle shutdown timeout value (" + result + " seconds)")
    if (result > 0)
      result * 1000
    else
      Integer.MAX_VALUE
  }
  @Loggable
  private[lib] def init(root: Context, _inner: AppComponent = null) = deinitializationLock.synchronized {
    AnyBase.ppGroup("AppComponent.init") {
      deinitializationLock.set(false)
      initRoutine(root, _inner)
    }
  }
  private[lib] def initRoutine(root: Context, _inner: AppComponent) = {
    if (inner != null)
      log.info("reinitialize AppComponent core subsystem for " + root.getPackageName())
    else
      log.info("initialize AppComponent for " + root.getPackageName())
    if (_inner != null)
      inner = _inner
    else
      inner = new AppComponent()
    LazyInit("initialize AppCache") { AppCache.init(root) }
    inner.state.set(State(DState.Initializing))
  }
  private[lib] def resurrect(supressEvent: Boolean = false): Unit = deinitializationLock.synchronized {
    if (deinitializationLock.get(0) != Some(false)) {
      log.info("resurrect AppComponent core subsystem")
      deinitializationLock.set(false) // try to cancel
      // if _AppControl_ active
      if (AppControl.deinitializationLock.get(0) == Some(false))
        if (!supressEvent)
          try { publish(Event.Resume) } catch { case e => log.error(e.getMessage, e) }
    }
  }
  private[lib] def deinit(): Unit = deinitializationLock.synchronized {
    AnyBase.getContext match {
      case Some(context) if deinitializationLock.isSet =>
        deinitializationLock.unset()
        val packageName = context.getPackageName()
        log.info("deinitializing AppComponent for " + packageName)
        val timeout = deinitializationTimeout(context)
        if (AppControl.deinitializationLock.get(0) == Some(false)) // AppControl active
          try { publish(Event.Suspend(timeout)) } catch { case e => log.error(e.getMessage, e) }
        Futures.future {
          if (log.isTraceEnabled)
            AnyBase.dumpStopWatchStatistics
          deinitializationLock.get(timeout) match {
            case Some(false) =>
              log.info("deinitialization AppComponent for " + packageName + " canceled")
            case _ =>
              deinitRoutine(packageName)
          }
        }
      case Some(context) =>
        log.debug("skip deinitialization, already in progress")
      case None =>
        log.fatal("unable to find deinitialization context")
    }
  }
  private[lib] def deinitRoutine(packageName: String): Unit = synchronized {
    log.info("deinitialize AppComponent for " + packageName)
    try { publish(Event.Shutdown) } catch { case e => log.error(e.getMessage, e) }
    assert(inner != null, { "unexpected inner value " + inner })
    val savedInner = inner
    inner = null
    if (AnyBase.isLastContext && AppControl.Inner != null) {
      log.info("AppComponent hold last context. Clear.")
      AppControl.deinitRoutine(packageName)
    }
    // unbind services from bindedICtrlPool and finish stopped activities
    AnyBase.getContext.foreach {
      context =>
        savedInner.bindedICtrlPool.keys.foreach(key => {
          savedInner.bindedICtrlPool.remove(key).map(record => {
            log.debug("remove service connection to " + key + " from bindedICtrlPool")
            record._1.unbindService(record._2)
          })
        })
        if (context.isInstanceOf[Activity])
          context.asInstanceOf[Activity].finish
    }
    AppCache.deinit()
    log.info("shutdown (" + shutdown + ")")
    if (shutdown)
      AnyBase.shutdownApp(packageName, true)
  }
  def isSuspend = deinitializationLock.get(0) == None
  def Inner = inner
  def Context = AnyBase.getContext
  def AppContext = Context.map(_.getApplicationContext)
  override protected[base] def publish(event: AppComponentEvent) =
    super.publish(event)
  object LazyInit {
    // priority -> Seq((timeout, function))
    @volatile private[base] var pool: LongMap[Seq[(Int, () => String)]] = LongMap()
    private val defaultTimeout = DTimeout.long
    def apply(description: String, priority: Long = 0, timeout: Int = defaultTimeout)(f: => Any)(implicit log: RichLogger) = synchronized {
      val storedFunc = if (pool.isDefinedAt(priority)) pool(priority) else Seq[(Int, () => String)]()
      pool = pool + (priority -> (storedFunc :+ (timeout, () => {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler.schedule(new Runnable { def run = log.warn("LazyInit block \"" + description + "\" hang") }, timeout, TimeUnit.MILLISECONDS)
        val tsBegin = System.currentTimeMillis
        log.debug("begin LazyInit block \"" + description + "\"")
        f
        val message = "\"" + description + "\" " + (System.currentTimeMillis - tsBegin) + "ms"
        log.debug("end LazyInit block " + message)
        scheduler.shutdownNow
        message
      })))
    }
    def init() = {
      val initpool = LazyInit.synchronized {
        val saved = pool
        pool = LongMap()
        saved
      }
      val begin = System.currentTimeMillis
      log.debug("running " + (initpool.keys.foldLeft(0) { (total, key) => initpool(key).size + total }) + " LazyInit routine(s)")
      for (prio <- initpool.keys.toSeq.sorted) {
        val levelBegin = System.currentTimeMillis
        log.debug("running " + initpool(prio).size + " LazyInit routine(s) at priority level " + prio)
        var timeout = 0
        val futures = initpool(prio).map(t => {
          val fTimeout = t._1
          val f = t._2
          timeout = scala.math.max(timeout, fTimeout)
          Futures.future {
            try {
              f()
            } catch {
              case e =>
                log.error(e.getMessage, e)
            }
          }
        })
        val results = Futures.awaitAll(timeout, futures: _*).asInstanceOf[List[Option[String]]].flatten
        log.debug((("complete " + initpool(prio).size + " LazyInit routine(s) at priority level " + prio +
          " within " + (System.currentTimeMillis - levelBegin) + "ms") +: results).mkString("\n"))
      }
      log.debug("complete LazyInit, " + (initpool.keys.foldLeft(0) { (total, key) => initpool(key).size + total }) +
        "f " + (System.currentTimeMillis - begin) + "ms")
    }
    def isEmpty = synchronized { pool.isEmpty }
    def nonEmpty = synchronized { pool.nonEmpty }
  }
  case class State(val value: DState.Value, val rawMessage: Seq[String] = Seq(), val onClickCallback: (Activity) => Any = (a) => {
    log.warn("onClick callback unimplemented for " + this)
  }) extends Logging {
    log.debugWhere("create new state " + value, 3)
  }
  class StateContainer extends SyncVar[AppComponent.State] with Publisher[AppComponent.State] with Logging {
    @volatile private var lastNonBusyState: AppComponent.State = null
    private var busyCounter = 0
    set(AppComponent.State(DState.Unknown))
    override def set(newState: AppComponent.State, signalAll: Boolean = true): Unit = synchronized {
      if (newState.value == DState.Busy) {
        busyCounter += 1
        log.debug("increase status busy counter to " + busyCounter)
        if (isSet && get.value != DState.Busy)
          lastNonBusyState = get
        super.set(newState, signalAll)
      } else if (busyCounter != 0) {
        lastNonBusyState = newState
      } else {
        lastNonBusyState = newState
        super.set(newState, signalAll)
      }
      log.debugWhere("set status to " + newState, Logging.Where.BEFORE)
      try { publish(newState) } catch { case e => log.error(e.getMessage, e) }
      AppComponent.Context.foreach(_.sendBroadcast(new Intent(DIntent.Update)))
    }
    override def get() = super.get match {
      case State(DState.Busy, message, callback) =>
        lastNonBusyState
      case state =>
        state
    }
    @Loggable
    def freeBusy() = synchronized {
      if (busyCounter > 1) {
        busyCounter -= 1
        log.debug("decrease status busy counter to " + busyCounter)
      } else {
        if (busyCounter == 1) {
          busyCounter -= 1
        } else {
          log.warn("busyCounter " + busyCounter + " out of range")
          busyCounter = 0
        }
        log.debug("reset status busy counter")
        lastNonBusyState match {
          case state: AppComponent.State =>
            lastNonBusyState = null
            set(state)
          case state =>
            log.warn("unknown lastNonBusyState condition " + state)
        }
      }
    }
    def isBusy(): Boolean =
      synchronized { busyCounter != 0 }
    def resetBusyCounter() = { busyCounter = 0 }
  }
  object Event {
    case class Suspend(timeout: Long) extends AppComponentEvent
    object Resume extends AppComponentEvent
    object Shutdown extends AppComponentEvent
  }
}
