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

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import scala.Array.canBuildFrom
import scala.actors.Futures.future
import scala.actors.Futures.awaitAll
import scala.actors.Actor
import scala.annotation.elidable
import scala.annotation.implicitNotFound
import scala.collection.mutable.Publisher
import scala.collection.JavaConversions._
import scala.collection.immutable.LongMap
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap
import scala.ref.WeakReference
import scala.xml.Node
import scala.xml.XML

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DPermission
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.InstallControl
import org.digimead.digi.ctrl.lib.info.ComponentInfo
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.util.Version
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.digimead.digi.ctrl.ICtrlComponent
import org.digimead.digi.ctrl.lib.declaration.DState

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.DialogInterface
import android.os.Bundle
import android.app.Dialog
import android.os.Looper
import android.net.Uri

import annotation.elidable.ASSERTION

protected class AppComponent private () extends Actor with Logging {
  private lazy val uiThreadID: Long = Looper.getMainLooper.getThread.getId
  lazy val state = new AppComponent.StateContainer
  lazy val internalStorage = AppComponent.Context.flatMap(ctx => Option(ctx.getFilesDir()))
  // -rwx--x--x 711
  lazy val externalStorage = AppComponent.Context.flatMap(ctx => Common.getDirectory(ctx, "var", false, 711))
  lazy val appNativePath = AppComponent.Context.flatMap(ctx => Common.getDirectory(ctx, DConstant.apkNativePath, true, 700))
  lazy val appNativeManifest = appNativePath.map(appNativePath => new File(appNativePath, "NativeManifest.xml"))
  lazy val nativeManifest = try {
    AppComponent.Context.map(ctx => XML.load(ctx.getAssets().open(DConstant.apkNativePath + "/NativeManifest.xml")))
  } catch {
    case e => log.error(e.getMessage, e); None
  }
  lazy val applicationManifest = try {
    AppComponent.Context.map(ctx => XML.load(ctx.getAssets().open(DConstant.apkNativePath + "/ApplicationManifest.xml")))
  } catch {
    case e => log.error(e.getMessage, e); None
  }
  private[lib] val bindedICtrlPool = new HashMap[String, (Context, ServiceConnection, ICtrlComponent)] with SynchronizedMap[String, (Context, ServiceConnection, ICtrlComponent)]
  private[lib] val lockRotationCounter = new AtomicInteger(0)
  private val isSafeDialogEnabled = new AtomicBoolean(false)
  private val activitySafeDialog = new AppComponent.SafeDialog
  private val activitySafeDialogActor = new Actor {
    def act = {
      loop {
        react {
          case AppComponent.Message.ShowDialog(activity, dialog, onDismiss) =>
            val s = sender
            log.info("receive message ShowDialog " + dialog)
            // wait for previous dialog
            log.debug("wait activitySafeDialog lock")
            activitySafeDialog.put(null) // wait
            // wait isSafeDialogEnabled
            isSafeDialogEnabled.synchronized {
              log.debug("wait isSafeDialogEnabled lock")
              while (!isSafeDialogEnabled.get)
                isSafeDialogEnabled.wait
            }
            // show dialog
            val result = onMessageShowDialog(activity, dialog, onDismiss)
            log.debug("return from message ShowDialog with result " + result)
            if (s.receiver.getState == Actor.State.Blocked)
              s ! result // only for ShowDialogSafeWait
          case AppComponent.Message.ShowDialogResource(activity, dialog, args, onDismiss) =>
            val s = sender
            log.info("receive message ShowDialogResource " + dialog)
            // wait for previous dialog
            log.debug("wait activitySafeDialog lock")
            activitySafeDialog.put(null) // wait
            // wait isSafeDialogEnabled
            isSafeDialogEnabled.synchronized {
              log.debug("wait isSafeDialogEnabled lock")
              while (!isSafeDialogEnabled.get)
                isSafeDialogEnabled.wait
            }
            // show dialog
            val result = onMessageShowDialogResource(activity, dialog, args, onDismiss)
            log.debug("return from message ShowDialog with result " + result)
            if (s.receiver.getState == Actor.State.Blocked)
              s ! result // only for ShowDialogSafeWait
          case message: AnyRef =>
            log.errorWhere("skip unknown message " + message.getClass.getName + ": " + message)
          case message =>
            log.errorWhere("skip unknown message " + message)
        }
      }
    }
  }
  log.debug("disable safe dialogs")
  log.debug("alive")

  def act = {
    loop {
      react {
        case message: AnyRef =>
          log.errorWhere("skip unknown message " + message.getClass.getName + ": " + message)
        case message =>
          log.errorWhere("skip unknown message " + message)
      }
    }
  }
  def showDialogSafe(activity: Activity, id: Int): Unit =
    showDialogSafe(activity, id, null)
  def showDialogSafe(activity: Activity, id: Int, args: Bundle): Unit =
    showDialogSafe(activity, id, args, null)
  @Loggable
  def showDialogSafe(activity: Activity, id: Int, args: Bundle, onDismiss: () => Unit) {
    log.trace("Activity::showDialogSafe id " + id)
    assert(id != 0, { "unexpected id value " + id })
    activitySafeDialogActor ! AppComponent.Message.ShowDialogResource(activity, id, Option(args), Option(onDismiss))
  }
  def showDialogSafeWait(activity: Activity, id: Int): Option[Dialog] =
    showDialogSafeWait(activity, id, null)
  def showDialogSafeWait(activity: Activity, id: Int, args: Bundle): Option[Dialog] =
    showDialogSafeWait(activity, id, args, null)
  @Loggable
  def showDialogSafeWait(activity: Activity, id: Int, args: Bundle, onDismiss: () => Unit): Option[Dialog] = try {
    log.trace("Activity::showDialogSafe id " + id + " at thread " + Thread.currentThread.getId + " and ui " + uiThreadID)
    assert(uiThreadID != Thread.currentThread.getId && id != 0, { "unexpected thread == UI, " + Thread.currentThread.getId + " or id " + id })
    (activitySafeDialogActor !? AppComponent.Message.ShowDialogResource(activity, id, Option(args), Option(onDismiss))).asInstanceOf[Option[Dialog]]
  } catch {
    case e =>
      log.error(e.getMessage, e)
      None
  }
  def showDialogSafe[T <: Dialog](activity: Activity, dialog: () => T)(implicit m: scala.reflect.Manifest[T]): Unit =
    showDialogSafe[T](activity, dialog, null)
  @Loggable
  def showDialogSafe[T <: Dialog](activity: Activity, dialog: () => T, onDismiss: () => Unit)(implicit m: scala.reflect.Manifest[T]) {
    log.trace("Activity::showDialogSafe " + m.erasure.getName)
    activitySafeDialogActor ! AppComponent.Message.ShowDialog(activity, dialog, Option(onDismiss))
  }
  def showDialogSafeWait[T <: Dialog](activity: Activity, dialog: () => T)(implicit m: scala.reflect.Manifest[T]): Option[T] =
    showDialogSafeWait[T](activity, dialog, null)
  @Loggable
  def showDialogSafeWait[T <: Dialog](activity: Activity, dialog: () => T, onDismiss: () => Unit)(implicit m: scala.reflect.Manifest[T]): Option[T] = try {
    log.trace("Activity::showDialogSafe " + m.erasure.getName + " at thread " + Thread.currentThread.getId + " and ui " + uiThreadID)
    assert(uiThreadID != Thread.currentThread.getId, { "unexpected thread == UI, " + Thread.currentThread.getId })
    (activitySafeDialogActor !? AppComponent.Message.ShowDialog(activity, dialog, Option(onDismiss))).asInstanceOf[Option[T]]
  } catch {
    case e =>
      log.error(e.getMessage, e)
      None
  }
  @Loggable
  def setDialogSafe(dialog: Dialog) {
    assert(!activitySafeDialog.isSet || activitySafeDialog.get == null,
      { "unexpected dialog value " + activitySafeDialog.get })
    activitySafeDialog.set((dialog, null))
  }
  @Loggable
  def resetDialogSafe() =
    activitySafeDialog.unset()
  @Loggable
  def getDialogSafe(timeout: Long): Option[Dialog] =
    activitySafeDialog.get(timeout).map(_._1)
  @Loggable
  def enableSafeDialogs() {
    log.debug("enable safe dialogs")
    isSafeDialogEnabled.synchronized {
      isSafeDialogEnabled.set(true)
      isSafeDialogEnabled.notifyAll
    }
  }
  @Loggable
  def disableSafeDialogs() {
    log.debug("disable safe dialogs")
    isSafeDialogEnabled.synchronized {
      isSafeDialogEnabled.set(false)
      isSafeDialogEnabled.notifyAll
    }
  }
  @Loggable
  def disableRotation() = AppComponent.Context match {
    case Some(activity) if activity.isInstanceOf[Activity] =>
      if (lockRotationCounter.getAndIncrement == 0)
        Android.disableRotation(activity.asInstanceOf[Activity])
      log.trace("set rotation lock to " + lockRotationCounter.get)
    case context =>
      log.warn("unable to disable rotation, invalid context " + context)
  }
  @Loggable
  def enableRotation() = AppComponent.Context match {
    case Some(activity) if activity.isInstanceOf[Activity] =>
      lockRotationCounter.compareAndSet(0, 1)
      if (lockRotationCounter.decrementAndGet == 0)
        Android.enableRotation(activity.asInstanceOf[Activity])
      log.trace("set rotation lock to " + lockRotationCounter.get)
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
    applicationManifest.flatMap {
      appManifest =>
        AppCache !? AppCache.Message.GetByID(0, appManifest.hashCode.toString + locale + localeLanguage) match {
          case Some(info) =>
            Some(info.asInstanceOf[ComponentInfo])
          case None =>
            val result = getComponentInfo(locale, localeLanguage, iconExtractor)
            result.foreach(r => AppCache ! AppCache.Message.UpdateByID(0, appManifest.hashCode.toString + locale + localeLanguage, r))
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
      appManifest <- applicationManifest
    } yield {
      AppCache !? AppCache.Message.GetByID(0, appManifest.hashCode.toString) match {
        case Some(info) =>
          Some(info.asInstanceOf[ComponentInfo])
        case None =>
          val result = ComponentInfo(appManifest, locale, localeLanguage, iconExtractor)
          result.foreach(r => AppCache ! AppCache.Message.UpdateByID(0, appManifest.hashCode.toString, r))
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
  /*
   *  true - all ok
   *  false - we need some work
   */
  @Loggable
  def synchronizeStateWithICtrlHost(onFinish: (DState.Value) => Unit = null): Unit = AppComponent.Context.foreach {
    activity =>
      if (AppComponent.Inner.state.get.value == DState.Broken && AppControl.Inner.isAvailable == Some(false)) {
        // DigiControl unavailable and state already broken, submit and return
        if (onFinish != null) onFinish(DState.Broken)
        return
      }
      AppControl.Inner.callStatus(activity.getPackageName)() match {
        case Right(componentState) =>
          val appState = componentState.state match {
            case DState.Active =>
              AppComponent.State(DState.Active)
            case DState.Broken =>
              AppComponent.State(DState.Broken, Seq("service_failed"))
            case _ =>
              AppComponent.State(DState.Passive)
          }
          AppComponent.Inner.state.set(appState)
          if (onFinish != null) onFinish(DState.Passive)
        case Left(error) =>
          val appState = if (error == "error_digicontrol_not_found")
            AppComponent.State(DState.Broken, Seq(error), (a) => { AppComponent.Inner.showDialogSafe(a, InstallControl.getId(a)) })
          else
            AppComponent.State(DState.Broken, Seq(error))
          AppComponent.Inner.state.set(appState)
          if (onFinish != null) onFinish(DState.Broken)
      }
  }
  @Loggable
  def minVersionRequired(componentPackage: String): Option[Version] = try {
    applicationManifest.flatMap {
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
  @Loggable
  private def onMessageShowDialog[T <: Dialog](activity: Activity, dialog: () => T, onDismiss: Option[() => Unit])(implicit m: scala.reflect.Manifest[T]): Option[Dialog] = try {
    // for example: pause activity in the middle of the process
    if (!activitySafeDialog.isSet) {
      log.warn("skip onMessageShowDialog for " + dialog + ", reason: dialog gone")
      return None
    }
    assert(activitySafeDialog.isSet && activitySafeDialog.get == null,
      { "unexpected dialog value " + activitySafeDialog.get(0) })
    if (!isActivityValid(activity)) {
      resetDialogSafe
      return None
    }
    activity.runOnUiThread(new Runnable {
      def run = activitySafeDialog.set((dialog(), () => {
        log.trace("safe dialog dismiss callback")
        AppComponent.Inner.enableRotation()
        onDismiss.foreach(_())
      }))
    })
    (activitySafeDialog.get(DTimeout.longest, _ != null) match {
      case Some((d, c)) if d != null =>
        log.debug("show new safe dialog " + d + " for " + m.erasure.getName)
        AppComponent.Inner.disableRotation()
        Option(d)
      case _ =>
        log.error("unable to show safe dialog for " + m.erasure.getName)
        activitySafeDialog.unset()
        None
    })
  } catch {
    case e =>
      log.error(e.getMessage, e)
      None
  }
  @Loggable
  private def onMessageShowDialogResource(activity: Activity, id: Int, args: Option[Bundle], onDismiss: Option[() => Unit]): Option[Dialog] = try {
    // for example: pause activity in the middle of the process
    if (!activitySafeDialog.isSet) {
      log.warn("skip onMessageShowDialogResource for " + id + ", reason: dialog gone")
      return None
    }
    assert(activitySafeDialog.isSet && activitySafeDialog.get == null,
      { "unexpected dialog value " + activitySafeDialog.get(0) })
    if (!isActivityValid(activity)) {
      resetDialogSafe
      return None
    }
    activity.runOnUiThread(new Runnable {
      def run = {
        try {
          args match {
            case Some(bundle) => activity.showDialog(id, bundle)
            case None => activity.showDialog(id)
          }
        } catch {
          case e =>
            log.error(e.getMessage + " on activity " + activity, e)
            activitySafeDialog.unset()
            None
        }
      }
    })
    activitySafeDialog.get(DTimeout.longest, _ != null) match {
      case Some((d, c)) =>
        if (d != null) {
          log.debug("show new safe dialog " + d + " for id " + id)
          activitySafeDialog.updateDismissCallback(() => {
            log.trace("safe dialog dismiss callback")
            AppComponent.Inner.enableRotation()
            onDismiss.foreach(_())
          })
          AppComponent.Inner.disableRotation()
          Option(d)
        } else {
          log.error("unable to show safe dialog for id " + id)
          activitySafeDialog.unset()
          None
        }
      case None =>
        log.error("unable to show safe dialog for id " + id)
        activitySafeDialog.unset()
        None
    }
  } catch {
    case e =>
      log.error(e.getMessage, e)
      None
  }
  private def isActivityValid(activity: Activity): Boolean =
    !activity.isFinishing && activity.getWindow != null
}

object AppComponent extends Logging {
  @volatile private var inner: AppComponent = null
  private val deinitializationLock = new SyncVar[Boolean]()
  private val deinitializationInProgressLock = new AtomicBoolean(false)
  private val deinitializationTimeout = DTimeout.longest

  log.debug("alive")
  @Loggable
  private[lib] def init(root: Context, _inner: AppComponent = null) = {
    deinitializationLock.set(false)
    initRoutine(root, _inner)
  }
  private[lib] def initRoutine(root: Context, _inner: AppComponent) = synchronized {
    // cancel deinitialization sequence if any
    LazyInit("initialize AppCache") { AppCache.init(root) }
    if (inner != null) {
      log.info("reinitialize AppComponent core subsystem for " + root.getPackageName())
      // unbind services from bindedICtrlPool
      AnyBase.getContext.foreach {
        context =>
          inner.bindedICtrlPool.keys.foreach(key => {
            inner.bindedICtrlPool.remove(key).map(record => {
              log.debug("remove service connection to " + key + " from bindedICtrlPool")
              record._1.unbindService(record._2)
            })
          })
      }
    } else
      log.info("initialize AppComponent for " + root.getPackageName())
    if (_inner != null)
      inner = _inner
    else {
      inner = new AppComponent()
      inner.activitySafeDialogActor.start
    }
    inner.state.set(State(DState.Initializing))
  }
  private[lib] def resurrect(caller: Context) = deinitializationInProgressLock.synchronized {
    deinitializationLock.set(false) // try to cancel
    if (deinitializationInProgressLock.get) {
      log.debug("deinitialization in progress, waiting...")
      deinitializationInProgressLock.synchronized {
        while (deinitializationInProgressLock.get)
          deinitializationInProgressLock.wait
      }
    }
    log.info("resurrect AppComponent core subsystem")
    if (caller.isInstanceOf[AppComponent])
      caller.asInstanceOf[AppComponent].activitySafeDialog.unset()
  }
  private[lib] def deinit(): Unit = future {
    if (deinitializationInProgressLock.compareAndSet(false, true))
      try {
        val packageName = Context.map(_.getPackageName()).getOrElse("UNKNOWN")
        log.info("deinitializing AppComponent for " + packageName)
        if (deinitializationLock.isSet)
          deinitializationLock.unset()
        deinitializationLock.get(deinitializationTimeout) match {
          case Some(false) =>
            log.info("deinitialization AppComponent for " + packageName + " canceled")
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
    log.info("deinitialize AppComponent for " + packageName)
    assert(inner != null, { "unexpected inner value " + inner })
    val savedInner = inner
    inner = null
    if (AnyBase.isLastContext && AppControl.Inner != null) {
      log.info("AppComponent hold last context. Clear.")
      AppControl.deinitRoutine(packageName)
    }
    // unbind services from bindedICtrlPool
    AnyBase.getContext.foreach {
      context =>
        savedInner.bindedICtrlPool.keys.foreach(key => {
          savedInner.bindedICtrlPool.remove(key).map(record => {
            log.debug("remove service connection to " + key + " from bindedICtrlPool")
            record._1.unbindService(record._2)
          })
        })
    }
    AppCache.deinit()
  }
  def Inner = inner
  def Context = AnyBase.getContext
  object LazyInit {
    // priority -> Seq(functions)
    @volatile private var pool: LongMap[Seq[() => Any]] = LongMap()
    private val timeout = DTimeout.long
    def apply(description: String, priority: Long = 0)(f: => Any)(implicit log: RichLogger) = synchronized {
      val storedFunc = if (pool.isDefinedAt(priority)) pool(priority) else Seq[() => Any]()
      pool = pool + (priority -> (storedFunc :+ (() => {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler.schedule(new Runnable { def run = log.warn("LazyInit block \"" + description + "\" hang") }, timeout, TimeUnit.MILLISECONDS)
        val tsBegin = System.currentTimeMillis
        log.debug("begin LazyInit block \"" + description + "\"")
        f
        log.debug("end LazyInit block \"" + description + "\" within " + ((System.currentTimeMillis - tsBegin).toFloat / 1000) + "s")
        scheduler.shutdownNow
      })))
    }
    def init() = synchronized {
      for (prio <- pool.keys.toSeq.sorted) {
        log.debug("running " + pool(prio).size + " LazyInit routine(s) at priority level " + prio)
        val futures = pool(prio).map(f => future {
          try {
            f()
          } catch {
            case e =>
              log.error(e.getMessage, e)
          }
        })
        awaitAll(timeout, futures: _*)
      }
      pool = LongMap()
    }
    def isEmpty = synchronized { pool.isEmpty }
    def nonEmpty = synchronized { pool.nonEmpty }
  }
  // Tuple2 [ dialog, onDismiss callback ]
  class SafeDialog extends SyncVar[(Dialog, () => Unit)] with Logging {
    @volatile private var activityDialogGuard: ScheduledExecutorService = null
    private val isPrivateReplace = new AtomicBoolean(false)
    private val lock = new Object

    @Loggable
    override def set(d: (Dialog, () => Unit), signalAll: Boolean = true): Unit = lock.synchronized {
      log.debugWhere("set safe dialog to '" + d + "'", Logging.Where.BEFORE)
      if (activityDialogGuard != null) {
        activityDialogGuard.shutdownNow
        activityDialogGuard = null
      }
      get(0) match {
        case Some((previousDialog, dismissCallback)) if previousDialog != null =>
          if (d._1 == previousDialog) {
            log.fatal("overwrite the same dialog '" + d._1 + "'")
            return
          }
          log.info("replace safe dialog '" + previousDialog + "' with new one '" + d._1 + "'")
          isPrivateReplace.synchronized {
            if (previousDialog.isShowing) {
              // we want replace dialog
              isPrivateReplace.set(true)
              previousDialog.dismiss
              while (isSet && isPrivateReplace.get)
                isPrivateReplace.wait
            } else {
              log.warn("replace hidden safe dialog")
              previousDialog.setOnDismissListener(null)
              previousDialog.dismiss
            }
          }
        case _ =>
      }
      if (d._1 != null) {
        activityDialogGuard = Executors.newSingleThreadScheduledExecutor()
        activityDialogGuard.schedule(new Runnable {
          def run() = {
            log.fatal("dismiss stalled dialog " + d._1)
            if (activityDialogGuard != null) {
              activityDialogGuard.shutdownNow
              activityDialogGuard = null
            }
            if (d._1.isShowing)
              d._1.dismiss
          }
        }, DTimeout.longest, TimeUnit.MILLISECONDS)
        d._1.setOnDismissListener(new DialogInterface.OnDismissListener with Logging {
          @Loggable
          override def onDismiss(dialog: DialogInterface) = SafeDialog.this.synchronized {
            log.info("dismiss safe dialog " + d._1)
            if (activityDialogGuard != null) {
              activityDialogGuard.shutdownNow
              activityDialogGuard = null
            }
            if (isPrivateReplace.getAndSet(false)) {
              log.g_a_s_e("!")
              // there is set(N) waiting for us
              // do it silent for external routines
              isPrivateReplace.synchronized {
                value.set(None)
                isPrivateReplace.notifyAll
              }
            } else {
              unset(false)
              SafeDialog.this.notifyAll()
            }
          }
        })
      }
      super.set(d, signalAll)
    }
    def updateDismissCallback(f: () => Unit) {
      val (d, c) = super.get
      super.set((d, f))
    }
    override def unset(signalAll: Boolean = true) = {
      log.debugWhere("unset safe dialog " + value, Logging.Where.BEFORE)
      if (activityDialogGuard != null) {
        activityDialogGuard.shutdownNow
        activityDialogGuard = null
      }
      super.get(0).foreach {
        case (previousDialog, dismissCallback) if previousDialog != null =>
          if (previousDialog.isShowing) {
            previousDialog.setOnDismissListener(null)
            previousDialog.dismiss
          }
          if (dismissCallback != null)
            dismissCallback()
        case _ =>
      }
      super.unset(signalAll)
    }
  }
  object Message {
    sealed trait Abstract
    case class ShowDialog[T <: Dialog](activity: Activity, dialog: () => T, onDismiss: Option[() => Unit]) extends Abstract
    case class ShowDialogResource(activity: Activity, dialog: Int, args: Option[Bundle], onDismiss: Option[() => Unit]) extends Abstract
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
      } else
        super.set(newState, signalAll)
      log.debugWhere("set status to " + newState, Logging.Where.BEFORE)
      publish(newState)
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
      } else if (busyCounter == 1) {
        busyCounter -= 1
        log.debug("reset status busy counter")
        lastNonBusyState match {
          case state: AppComponent.State =>
            lastNonBusyState = null
            set(state)
          case state =>
            log.warn("unknown lastNonBusyState condition " + state)
        }
      } else {
        log.fatal("illegal busyCounter")
      }
    }
    def isBusy(): Boolean = synchronized {
      busyCounter != 0
    }
  }
}
