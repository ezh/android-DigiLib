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
import java.util.concurrent.atomic.AtomicReference

import scala.Array.canBuildFrom
import scala.actors.OutputChannel
import scala.actors.Futures
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
import org.digimead.digi.ctrl.lib.DActivity
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
import org.digimead.digi.ctrl.lib.dialog.Preferences
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.DMessage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.app.Dialog
import android.os.Looper
import android.net.Uri

import annotation.elidable.ASSERTION

protected class AppComponent private () extends Logging {
  private lazy val uiThreadID: Long = Looper.getMainLooper.getThread.getId
  lazy val state = new AppComponent.StateContainer
  lazy val internalStorage = AppComponent.Context.flatMap(ctx => Option(ctx.getFilesDir()))
  // -rwx--x--x 711
  lazy val externalStorage = AppComponent.Context.flatMap(ctx => Common.getDirectory(ctx, "var", false, Some(false), Some(false), Some(true)))
  // -rwx------ 711
  lazy val appNativePath = AppComponent.Context.flatMap(ctx => Common.getDirectory(ctx, DConstant.apkNativePath, true, Some(false), Some(false), Some(true)))
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
  val preferredOrientation = new AtomicInteger(ActivityInfo.SCREEN_ORIENTATION_SENSOR)
  private[lib] val lockRotationCounter = new AtomicInteger(0)
  private val isSafeDialogEnabled = new AtomicReference[Option[Boolean]](None)
  private[lib] val activitySafeDialog = new AppComponent.SafeDialog
  private val activitySafeDialogActor = new Actor {
    def act = {
      loop {
        react {
          case AppComponent.Message.ShowDialog(activity, tag, dialog, onDismiss) =>
            val s = sender
            log.info("receive message ShowDialog " + dialog)
            // wait for previous dialog
            log.debug("wait activitySafeDialog lock")
            activitySafeDialog.put(AppComponent.SafeDialogEntry(Some(tag), None, None)) // wait
            // wait isSafeDialogEnabled
            log.debug("wait isSafeDialogEnabled lock")
            while (isSafeDialogEnabled.get match {
              case Some(true) => show(activity, tag, dialog, onDismiss, s); false // show dialog and exit from loop
              case Some(false) => true // wait
              case None => resetDialogSafe; false // skip
            }) isSafeDialogEnabled.synchronized { isSafeDialogEnabled.wait }
            log.debug("wait isSafeDialogEnabled lock complete")
          case AppComponent.Message.ShowDialogResource(activity, tag, dialog, args, onDismiss) =>
            val s = sender
            log.info("receive message ShowDialogResource " + dialog)
            // wait for previous dialog
            log.debug("wait activitySafeDialog lock")
            activitySafeDialog.put(AppComponent.SafeDialogEntry(Some(tag), None, None)) // wait
            // wait isSafeDialogEnabled
            log.debug("wait isSafeDialogEnabled lock")
            while (isSafeDialogEnabled.get match {
              case Some(true) => show(activity, tag, dialog, args, onDismiss, s); false // show dialog and exit from loop
              case Some(false) => true // wait
              case None => resetDialogSafe; false // skip
            }) isSafeDialogEnabled.synchronized { isSafeDialogEnabled.wait }
            log.debug("wait isSafeDialogEnabled lock complete")
          case message: AnyRef =>
            log.errorWhere("skip unknown message " + message.getClass.getName + ": " + message)
          case message =>
            log.errorWhere("skip unknown message " + message)
        }
      }
    }
    private def show(activity: Activity, tag: String, dialog: () => Dialog, onDismiss: Option[() => Any], sender: OutputChannel[Any]) {
      val result = onMessageShowDialog(activity, tag, dialog, onDismiss)
      log.debug("return from message ShowDialog with result " + result)
      if (sender.receiver.getState == Actor.State.Blocked)
        sender ! result // only for ShowDialogSafeWait
    }
    private def show(activity: Activity, tag: String, dialog: Int, args: Option[Bundle], onDismiss: Option[() => Any], sender: OutputChannel[Any]) {
      val result = onMessageShowDialogResource(activity, tag, dialog, args, onDismiss)
      log.debug("return from message ShowDialog with result " + result)
      if (sender.receiver.getState == Actor.State.Blocked)
        sender ! result // only for ShowDialogSafeWait
    }
  }
  log.debug("disable safe dialogs")
  log.debug("alive")

  def showDialogSafe(activity: Activity, tag: String, id: Int): Unit =
    showDialogSafe(activity, tag, id, null)
  def showDialogSafe(activity: Activity, tag: String, id: Int, args: Bundle): Unit =
    showDialogSafe(activity, tag, id, args, null)
  @Loggable
  def showDialogSafe(activity: Activity, tag: String, id: Int, args: Bundle, onDismiss: () => Any) {
    log.trace("Activity::showDialogSafe tag:%s id:%d".format(tag, id))
    assert(id != 0, { "unexpected id value " + id })
    activitySafeDialogActor ! AppComponent.Message.ShowDialogResource(activity, tag, id, Option(args), Option(onDismiss))
  }
  def showDialogSafeWait(activity: Activity, tag: String, id: Int): Option[Dialog] =
    showDialogSafeWait(activity, tag, id, null)
  def showDialogSafeWait(activity: Activity, tag: String, id: Int, args: Bundle): Option[Dialog] =
    showDialogSafeWait(activity, tag, id, args, null)
  @Loggable
  def showDialogSafeWait(activity: Activity, tag: String, id: Int, args: Bundle, onDismiss: () => Any): Option[Dialog] = try {
    log.trace("Activity::showDialogSafe tag:%s id:%d at thread %l and ui %l ".format(tag, id, Thread.currentThread.getId, uiThreadID))
    assert(uiThreadID != Thread.currentThread.getId && id != 0, { "unexpected thread == UI, " + Thread.currentThread.getId + " or id " + id })
    (activitySafeDialogActor !? AppComponent.Message.ShowDialogResource(activity, tag, id, Option(args), Option(onDismiss))).asInstanceOf[Option[Dialog]]
  } catch {
    case e =>
      log.error(e.getMessage, e)
      None
  }
  def showDialogSafe[T <: Dialog](activity: Activity, tag: String, dialog: () => T)(implicit m: scala.reflect.Manifest[T]): Unit =
    showDialogSafe[T](activity, tag, dialog, null)
  @Loggable
  def showDialogSafe[T <: Dialog](activity: Activity, tag: String, dialog: () => T, onDismiss: () => Any)(implicit m: scala.reflect.Manifest[T]) {
    log.trace("Activity::showDialogSafe " + m.erasure.getName)
    activitySafeDialogActor ! AppComponent.Message.ShowDialog(activity, tag, dialog, Option(onDismiss))
  }
  def showDialogSafeWait[T <: Dialog](activity: Activity, tag: String, dialog: () => T)(implicit m: scala.reflect.Manifest[T]): Option[T] =
    showDialogSafeWait[T](activity, tag, dialog, null)
  @Loggable
  def showDialogSafeWait[T <: Dialog](activity: Activity, tag: String, dialog: () => T, onDismiss: () => Any)(implicit m: scala.reflect.Manifest[T]): Option[T] = try {
    log.trace("Activity::showDialogSafe " + m.erasure.getName + " at thread " + Thread.currentThread.getId + " and ui " + uiThreadID)
    assert(uiThreadID != Thread.currentThread.getId, { "unexpected thread == UI, " + Thread.currentThread.getId })
    (activitySafeDialogActor !? AppComponent.Message.ShowDialog(activity, tag, dialog, Option(onDismiss))).asInstanceOf[Option[T]]
  } catch {
    case e =>
      log.error(e.getMessage, e)
      None
  }
  @Loggable
  def setDialogSafe(tag: Option[String], dialog: Option[Dialog]) {
    if (activitySafeDialog.isSet && activitySafeDialog.get(0) != Some(AppComponent.SafeDialogEntry(None, None, None))) {
      val expected = AppComponent.SafeDialogEntry(tag, None, None)
      assert(activitySafeDialog.get(0) == Some(expected),
        { "activitySafeDialog expected " + expected + ", found " + activitySafeDialog.get })
      activitySafeDialog.set(AppComponent.SafeDialogEntry(tag, dialog, None))
    } else {
      log.warn("reset dialog " + AppComponent.SafeDialogEntry(tag, dialog, None) + ", previously was " + activitySafeDialog.get(0))
      activitySafeDialog.set(AppComponent.SafeDialogEntry(None, None, None))
      Thread.sleep(10) // gap for onMessageShowDialog
      activitySafeDialog.unset()
    }
  }
  @Loggable
  def resetDialogSafe() =
    activitySafeDialog.unset()
  @Loggable
  def getDialogSafe(timeout: Long): Option[Dialog] =
    activitySafeDialog.get(timeout).flatMap(_.dialog)
  @Loggable
  def enableSafeDialogs() {
    log.debug("enable safe dialogs")
    isSafeDialogEnabled.set(Some(true))
    isSafeDialogEnabled.synchronized { isSafeDialogEnabled.notifyAll }
  }
  @Loggable
  def suspendSafeDialogs() {
    log.debug("disable safe dialogs")
    isSafeDialogEnabled.set(Some(false))
    isSafeDialogEnabled.synchronized { isSafeDialogEnabled.notifyAll }
  }
  @Loggable
  def disableSafeDialogs() = Futures.future {
    log.debug("disable safe dialogs")
    isSafeDialogEnabled.set(None)
    isSafeDialogEnabled.synchronized { isSafeDialogEnabled.notifyAll }
    activitySafeDialog.set(AppComponent.SafeDialogEntry(None, None, None)) // throw signal for unlock onMessageShowDialog, ..., dismiss safe dialog if any
    Thread.sleep(10) // gap for onMessageShowDialog
    activitySafeDialog.unset()
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
      appManifest <- applicationManifest
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
  /*
   *  true - all ok
   *  false - we need some work
   */
  @Loggable
  def synchronizeStateWithICtrlHost(onFinish: (DState.Value) => Unit = null): Unit = AppComponent.Context.foreach {
    activity =>
      if (AppComponent.this.state.get.value == DState.Broken && AppControl.Inner.isAvailable == Some(false)) {
        log.warn("DigiControl unavailable and state already broken")
        if (onFinish != null) onFinish(DState.Broken)
        return
      }
      Futures.future {
        AppControl.Inner.callStatus(activity.getPackageName)() match {
          case Right(componentState) =>
            val appState = componentState.state match {
              case DState.Active =>
                AppComponent.State(DState.Active)
              case DState.Broken =>
                AppComponent.State(DState.Broken, Seq("error_digicontrol_service_failed"))
              case _ =>
                AppComponent.State(DState.Passive)
            }
            AppComponent.this.state.set(appState)
            if (onFinish != null) onFinish(DState.Passive)
          case Left(error) =>
            val appState = if (error == "error_digicontrol_not_found")
              AppComponent.State(DState.Broken, Seq(error), (a) => { AppComponent.this.showDialogSafe(a, InstallControl.getClass.getName, InstallControl.getId(a)) })
            else
              AppComponent.State(DState.Broken, Seq(error))
            AppComponent.this.state.set(appState)
            if (onFinish != null) onFinish(DState.Broken)
        }
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
  private def onMessageShowDialog[T <: Dialog](activity: Activity, tag: String, dialog: () => T, onDismiss: Option[() => Any])(implicit m: scala.reflect.Manifest[T]): Option[Dialog] = try {
    // for example: pause activity in the middle of the process
    if (!activitySafeDialog.isSet) {
      log.warn("skip onMessageShowDialog for " + tag + ", " + dialog + ", reason: dialog gone")
      return None
    }
    val expected = AppComponent.SafeDialogEntry(Some(tag), None, None)
    assert(activitySafeDialog.isSet && activitySafeDialog.get == expected,
      { "activitySafeDialog expected " + expected + ", found " + activitySafeDialog.get })
    if (!isActivityValid(activity) || isSafeDialogEnabled == None) {
      resetDialogSafe
      return None
    }
    activity.runOnUiThread(new Runnable {
      def run =
        activitySafeDialog.set(AppComponent.SafeDialogEntry(Some(tag), Option(dialog()), Some(() => {
          log.trace("safe dialog dismiss callback")
          AppComponent.this.enableRotation()
          onDismiss.foreach(_())
        })))
    })
    (activitySafeDialog.get(DTimeout.longest, _ != AppComponent.SafeDialogEntry(Some(tag), None, None)) match {
      case Some(entry @ AppComponent.SafeDialogEntry(tag, result @ Some(dialog), dismissCb)) =>
        log.debug("show new safe dialog " + entry + " for " + m.erasure.getName)
        AppComponent.this.disableRotation()
        result
      case Some(AppComponent.SafeDialogEntry(None, None, None)) =>
        log.error("unable to show safe dialog '" + tag + "' for " + m.erasure.getName + ", reset detected")
        None
      case result =>
        log.error("unable to show safe dialog '" + tag + "' for " + m.erasure.getName + " result:" + result)
        activitySafeDialog.unset()
        None
    })
  } catch {
    case e =>
      log.error(e.getMessage, e)
      None
  }
  @Loggable
  private def onMessageShowDialogResource(activity: Activity, tag: String, id: Int, args: Option[Bundle], onDismiss: Option[() => Any]): Option[Dialog] = try {
    // for example: pause activity in the middle of the process
    if (!activitySafeDialog.isSet) {
      log.warn("skip onMessageShowDialogResource for " + tag + ", " + id + ", reason: dialog gone")
      return None
    }
    val expected = AppComponent.SafeDialogEntry(Some(tag), None, None)
    assert(activitySafeDialog.isSet && activitySafeDialog.get == expected,
      { "activitySafeDialog expected " + expected + ", found " + activitySafeDialog.get })
    if (!isActivityValid(activity) || isSafeDialogEnabled == None) {
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
    activitySafeDialog.get(DTimeout.longest, _ != AppComponent.SafeDialogEntry(Some(tag), None, None)) match {
      case Some(entry @ AppComponent.SafeDialogEntry(tag, result @ Some(dialog), dismissCb)) =>
        log.debug("show new safe dialog " + entry + " for id " + id)
        activitySafeDialog.updateDismissCallback(Some(() => {
          log.trace("safe dialog dismiss callback")
          AppComponent.this.enableRotation()
          onDismiss.foreach(_())
        }))
        AppComponent.this.disableRotation()
        result
      case Some(AppComponent.SafeDialogEntry(None, None, None)) =>
        log.error("unable to show safe dialog '" + tag + "' for id " + id + ", reset detected")
        None
      case result =>
        log.error("unable to show safe dialog '" + tag + "' for id " + id + " result:" + result)
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

sealed trait AppComponentEvent

object AppComponent extends Logging with Publisher[AppComponentEvent] {
  @volatile private var inner: AppComponent = null
  @volatile private[base] var shutdown = true
  private[base] val deinitializationLock = new SyncVar[Boolean]()
  private val deinitializationInProgressLock = new AtomicBoolean(false)
  deinitializationLock.set(false)
  log.debug("alive")

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
  private[lib] def resurrect(): Unit = deinitializationInProgressLock.synchronized {
    if (deinitializationLock.get(0) != Some(false)) {
      log.info("resurrect AppComponent core subsystem")
      deinitializationLock.set(false) // try to cancel
      // deinitialization canceled
      if (AppControl.deinitializationLock.get(0) == Some(false)) // AppControl active
        try { publish(Event.Resume) } catch { case e => log.error(e.getMessage, e) }
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
    AnyBase.getContext map {
      context =>
        val packageName = context.getPackageName()
        log.info("deinitializing AppComponent for " + packageName)
        if (deinitializationLock.isSet)
          deinitializationLock.unset()
        Futures.future {
          try {
            val timeout = deinitializationTimeout(context)
            if (AppControl.deinitializationLock.get(0) == Some(false)) // AppControl active
              try { publish(Event.Suspend(timeout)) } catch { case e => log.error(e.getMessage, e) }
            deinitializationLock.get(timeout) match {
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
    } orElse {
      log.fatal("unable to find deinitialization context")
      None
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
    def init() = synchronized {
      val begin = System.currentTimeMillis
      log.debug("running " + (pool.keys.foldLeft(0) { (total, key) => pool(key).size + total }) + " LazyInit routine(s)")
      for (prio <- pool.keys.toSeq.sorted) {
        val levelBegin = System.currentTimeMillis
        log.debug("running " + pool(prio).size + " LazyInit routine(s) at priority level " + prio)
        var timeout = 0
        val futures = pool(prio).map(t => {
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
        log.debug((("complete " + pool(prio).size + " LazyInit routine(s) at priority level " + prio +
          " within " + (System.currentTimeMillis - levelBegin) + "ms") +: results).mkString("\n"))
      }
      log.debug("complete LazyInit, " + (pool.keys.foldLeft(0) { (total, key) => pool(key).size + total }) +
        "f " + (System.currentTimeMillis - begin) + "ms")
      pool = LongMap()
    }
    def isEmpty = synchronized { pool.isEmpty }
    def nonEmpty = synchronized { pool.nonEmpty }
  }
  class SafeDialog extends SyncVar[SafeDialogEntry] with Logging {
    @volatile private var activityDialogGuard: ScheduledExecutorService = null
    private val replaceFlag = new AtomicBoolean(false)
    private val lock = new Object

    @Loggable
    override def set(entry: SafeDialogEntry, signalAll: Boolean = true): Unit = lock.synchronized {
      replaceFlag.set(false)
      log.debugWhere("set safe dialog to " + entry, Logging.Where.BEFORE)
      if (activityDialogGuard != null) {
        activityDialogGuard.shutdownNow
        activityDialogGuard = null
      }
      // clear previous dialog if any
      get(0) match {
        case Some(previous @ SafeDialogEntry(tag, Some(dialog), dismissCb)) if entry.dialog != None =>
          // replace with new dialog
          if (entry.dialog == dialog) {
            log.fatal("overwrite the same dialog " + entry)
            return
          }
          log.info("replace safe dialog " + previous + " with new one " + entry)
          if (dialog.isShowing) {
            replaceFlag.set(true)
            try {
              dialog.dismiss
            } catch {
              case e =>
                log.warn(previous + " dialog: " + e.getMessage)
                dialog.getWindow.closeAllPanels
            }
            while (replaceFlag.get)
              replaceFlag.synchronized { replaceFlag.wait }
          } else {
            dialog.setOnDismissListener(null)
            try {
              dialog.dismiss
            } catch {
              case e =>
                log.warn(previous + " dialog: " + e.getMessage)
                dialog.getWindow.closeAllPanels
            }
          }
        case Some(previous @ SafeDialogEntry(tag, Some(dialog), dismissCb)) =>
          // replace with stub (state == None)
          if (!dialog.isShowing)
            try {
              dialog.dismiss
            } catch {
              case e =>
                log.warn(previous + " dialog: " + e.getMessage)
                dialog.getWindow.closeAllPanels
            }
        case _ =>
      }
      // set new
      for { dialog <- entry.dialog } {
        // add guard timer
        activityDialogGuard = Executors.newSingleThreadScheduledExecutor()
        activityDialogGuard.schedule(new Runnable {
          def run() = {
            log.fatal("dismiss stalled dialog " + entry)
            if (activityDialogGuard != null) {
              activityDialogGuard.shutdownNow
              activityDialogGuard = null
            }
            try {
              dialog.dismiss
            } catch {
              case e =>
                log.warn(entry + " dialog: " + e.getMessage)
                dialog.getWindow.closeAllPanels
            }
          }
        }, DTimeout.longest, TimeUnit.MILLISECONDS)
        // add on dismiss listener
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener with Logging {
          @Loggable
          override def onDismiss(dialog: DialogInterface) = SafeDialog.this.synchronized {
            log.info("dismiss safe dialog " + entry)
            if (activityDialogGuard != null) {
              activityDialogGuard.shutdownNow
              activityDialogGuard = null
            }
            if (replaceFlag.getAndSet(false)) {
              log.debug("replaced dialog " + entry + " dismissed")
              replaceFlag.synchronized { replaceFlag.notifyAll }
            } else {
              unset(false)
              SafeDialog.this.notifyAll()
            }
          }
        })
      }
      super.set(entry, signalAll)
    }
    def updateDismissCallback(f: Option[() => Any]) =
      super.set(super.get.copy(dismissCb = f))
    override def unset(signalAll: Boolean = true) = {
      log.debugWhere("unset safe dialog '" + value + "'", Logging.Where.BEFORE)
      if (activityDialogGuard != null) {
        activityDialogGuard.shutdownNow
        activityDialogGuard = null
      }
      super.get(0) match {
        case Some(previous @ SafeDialogEntry(tag, Some(dialog), dismissCb)) =>
          if (dialog.isShowing) {
            dialog.setOnDismissListener(null)
            try {
              dialog.dismiss
            } catch {
              case e =>
                log.warn(previous + " dialog: " + e.getMessage)
                dialog.getWindow.closeAllPanels
            }
          }
          dismissCb.foreach(_())
        case _ =>
      }
      super.unset(signalAll)
    }
  }
  case class SafeDialogEntry(val tag: Option[String],
    val dialog: Option[Dialog],
    val dismissCb: Option[() => Any])
  object Message {
    sealed trait Abstract
    case class ShowDialog[T <: Dialog](activity: Activity, tag: String, dialog: () => T, onDismiss: Option[() => Any]) extends Abstract
    case class ShowDialogResource(activity: Activity, tag: String, dialog: Int, args: Option[Bundle], onDismiss: Option[() => Any]) extends Abstract
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
