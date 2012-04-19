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

protected class AppActivity private () extends Actor with Logging {
  private lazy val uiThreadID: Long = Looper.getMainLooper.getThread.getId
  lazy val state = new SyncVar[AppActivity.State]() with Logging {
    private var lastNonBusyState: AppActivity.State = null
    private var busyCounter = 0
    set(AppActivity.State(DState.Unknown))
    override def set(newState: AppActivity.State, signalAll: Boolean = true): Unit = synchronized {
      if (newState.code == DState.Busy) {
        busyCounter += 1
        log.debug("increase status busy counter to " + busyCounter)
        if (isSet && get.code != DState.Busy)
          lastNonBusyState = get
        super.set(newState, signalAll)
      } else if (busyCounter != 0) {
        lastNonBusyState = newState
      } else
        super.set(newState, signalAll)
      log.debug("set status to " + newState)
      AppActivity.Context.foreach(_.sendBroadcast(new Intent(DIntent.Update)))
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
          case state: AppActivity.State =>
            lastNonBusyState = null
            set(state)
          case state =>
            log.warn("unknown lastNonBusyState condition " + state)
        }
      } else {
        log.fatal("illegal busyCounter")
      }
    }
  }
  lazy val internalStorage = AppActivity.Context.flatMap(ctx => Option(ctx.getFilesDir()))
  lazy val externalStorage = AppActivity.Context.flatMap(ctx => Option(ctx.getExternalFilesDir(null)))
  lazy val appNativePath = internalStorage.map(is => new File(is, DConstant.apkNativePath))
  lazy val appNativeManifest = appNativePath.map(appNativePath => new File(appNativePath, "NativeManifest.xml"))
  lazy val nativeManifest = try {
    AppActivity.Context.map(ctx => XML.load(ctx.getAssets().open(DConstant.apkNativePath + "/NativeManifest.xml")))
  } catch {
    case e => log.error(e.getMessage, e); None
  }
  lazy val applicationManifest = try {
    AppActivity.Context.map(ctx => XML.load(ctx.getAssets().open(DConstant.apkNativePath + "/ApplicationManifest.xml")))
  } catch {
    case e => log.error(e.getMessage, e); None
  }
  private[lib] val bindedICtrlPool = new HashMap[String, (Context, ServiceConnection, ICtrlComponent)] with SynchronizedMap[String, (Context, ServiceConnection, ICtrlComponent)]
  private[lib] val lockRotationCounter = new AtomicInteger(0)
  private val isSafeDialogEnabled = new AtomicBoolean(false)
  private val activitySafeDialog = new AppActivity.SafeDialog
  private val activitySafeDialogActor = new Actor {
    def act = {
      loop {
        react {
          case AppActivity.Message.ShowDialog(activity, dialog, onDismiss) =>
            val s = sender
            log.info("receive message ShowDialog " + dialog)
            // wait for previous dialog
            log.trace("wait activitySafeDialog lock")
            activitySafeDialog.put((null, null)) // wait
            activitySafeDialog.unset()
            // wait isSafeDialogEnabled
            isSafeDialogEnabled.synchronized {
              log.trace("wait isSafeDialogEnabled lock")
              while (!isSafeDialogEnabled.get)
                isSafeDialogEnabled.wait
            }
            // show dialog
            val result = onMessageShowDialog(activity, dialog, onDismiss)
            log.debug("return from message ShowDialog with result " + result)
            if (s.receiver.getState == Actor.State.Blocked)
              s ! result // only for ShowDialogSafeWait
          case AppActivity.Message.ShowDialogResource(activity, dialog, args, onDismiss) =>
            val s = sender
            log.info("receive message ShowDialogResource " + dialog)
            // wait for previous dialog
            log.trace("wait activitySafeDialog lock")
            activitySafeDialog.put((null, null)) // wait
            activitySafeDialog.unset()
            // wait isSafeDialogEnabled
            isSafeDialogEnabled.synchronized {
              log.trace("wait isSafeDialogEnabled lock")
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
  activitySafeDialogActor.start
  log.debug("disable safe dialogs")

  def act = {
    loop {
      react {
        case AppActivity.Message.PrepareEnvironment(activity, keep, public, callback) =>
          if (callback == null)
            prepareEnvironment(activity, keep, public)
          else
            callback(prepareEnvironment(activity, keep, public))
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
    assert(id != 0)
    activitySafeDialogActor ! AppActivity.Message.ShowDialogResource(activity, id, Option(args), Option(onDismiss))
  }
  def showDialogSafeWait(activity: Activity, id: Int): Option[Dialog] =
    showDialogSafeWait(activity, id, null)
  def showDialogSafeWait(activity: Activity, id: Int, args: Bundle): Option[Dialog] =
    showDialogSafeWait(activity, id, args, null)
  @Loggable
  def showDialogSafeWait(activity: Activity, id: Int, args: Bundle, onDismiss: () => Unit): Option[Dialog] = try {
    log.trace("Activity::showDialogSafe id " + id + " at thread " + currentThread.getId + " and ui " + uiThreadID)
    assert(uiThreadID != Thread.currentThread.getId && id != 0)
    (activitySafeDialogActor !? AppActivity.Message.ShowDialogResource(activity, id, Option(args), Option(onDismiss))).asInstanceOf[Option[Dialog]]
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
    activitySafeDialogActor ! AppActivity.Message.ShowDialog(activity, dialog, Option(onDismiss))
  }
  def showDialogSafeWait[T <: Dialog](activity: Activity, dialog: () => T)(implicit m: scala.reflect.Manifest[T]): Option[T] =
    showDialogSafeWait[T](activity, dialog, null)
  @Loggable
  def showDialogSafeWait[T <: Dialog](activity: Activity, dialog: () => T, onDismiss: () => Unit)(implicit m: scala.reflect.Manifest[T]): Option[T] = try {
    log.trace("Activity::showDialogSafe " + m.erasure.getName + " at thread " + currentThread.getId + " and ui " + uiThreadID)
    assert(uiThreadID != Thread.currentThread.getId)
    (activitySafeDialogActor !? AppActivity.Message.ShowDialog(activity, dialog, Option(onDismiss))).asInstanceOf[Option[T]]
  } catch {
    case e =>
      log.error(e.getMessage, e)
      None
  }
  @Loggable
  def setDialogSafe(dialog: Dialog) {
    assert(!activitySafeDialog.isSet || (activitySafeDialog.isSet && activitySafeDialog.get == null))
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
  def disableRotation() = AppActivity.Context match {
    case Some(activity) if activity.isInstanceOf[Activity] =>
      if (lockRotationCounter.getAndIncrement == 0)
        Android.disableRotation(activity.asInstanceOf[Activity])
      log.trace("set rotation lock to " + lockRotationCounter.get)
    case context =>
      log.warn("unable to disable rotation, invalid context " + context)
  }
  @Loggable
  def enableRotation() = AppActivity.Context match {
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
    }): Option[ComponentInfo] = applicationManifest.flatMap {
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
  def sendPrivateBroadcast(intent: Intent, flags: Seq[Int] = Seq()) = AppActivity.Context foreach {
    context =>
      intent.putExtra("__private__", true)
      flags.foreach(intent.addFlags)
      context.sendBroadcast(intent, DPermission.Base)
  }
  @Loggable
  def sendPrivateOrderedBroadcast(intent: Intent, flags: Seq[Int] = Seq()) = AppActivity.Context foreach {
    context =>
      intent.putExtra("__private__", true)
      flags.foreach(intent.addFlags)
      context.sendOrderedBroadcast(intent, DPermission.Base)
  }
  @Loggable
  def giveTheSign(key: Uri, data: Bundle): Unit = AppActivity.Context foreach {
    context =>
      log.debug("send the " + key)
      val intent = new Intent(DIntent.SignResponse, key)
      intent.putExtras(data)
      sendPrivateBroadcast(intent)
  }
  @Loggable
  protected def prepareEnvironment(caller: Activity, keep: Boolean, makePublic: Boolean): Boolean = {
    for {
      ctx <- AppActivity.Context
      appNativePath <- appNativePath
      info <- AnyBase.info.get
    } yield {
      // Copy armeabi from assests to files folder:
      // /data/data/PKG_NAME/files
      if (!prepareNativePath(caller))
        return false
      val am = ctx.getAssets()
      val files = am.list(DConstant.apkNativePath)
      val from = files.map(name => DConstant.apkNativePath + "/" + name)
      val to = files.map(name => new File(appNativePath, name))
      val nativeManifestInstalled: Option[Node] = try {
        to.find(_.getName() == "NativeManifest.xml") match {
          case Some(description) =>
            if (description.exists)
              Some(scala.xml.XML.loadFile(description))
            else
              None
          case None => None
        }
      } catch {
        case e =>
          log.error(e.getMessage, e)
          None
      }
      if (nativeManifest == None) {
        log.fatal("couldn't install native armeabi files without proper manifest")
        state.set(AppActivity.State(DState.Broken, Android.getString(ctx, "error_prepare_manifest").
          getOrElse("Error prepare environment, manifest for native files not found")))
        return false
      }
      // compare versionCode
      val appBuildFile = new File(appNativePath, ".build")
      val appBuild = try {
        if (appBuildFile.exists) scala.io.Source.fromFile(appBuildFile).getLines.mkString.trim else ""
      } catch {
        case e => ""
      }
      if ((info.appBuild == appBuild) && keep && to.forall(_.exists) && nativeManifestInstalled != None &&
        checkEnvironmentVersion(nativeManifest, nativeManifestInstalled)) {
        log.debug("skip, armeabi files already installed")
        true
      } else {
        log.debug("update native files")
        val results: Seq[Boolean] = for (
          i <- 0 until from.length;
          entityFrom = from(i);
          entityTo = to(i)
        ) yield {
          try {
            val toName = entityTo.getName()
            log.debug("copy armeabi resource from apk:/" + entityFrom + " to " + entityTo)
            if (entityTo.exists())
              entityTo.delete()
            val outStream = new BufferedOutputStream(new FileOutputStream(entityTo, true), 8192)
            Common.writeToStream(am.open(entityFrom), outStream)
            // -rwxr-xr-x 755 vs -rwx------ 700
            val permission = if (makePublic) 755 else 700
            val result = Common.execChmod(permission, entityTo)
            if (!result)
              state.set(AppActivity.State(DState.Broken, Android.getString(ctx, "error_prepare_chmod").
                getOrElse("Error prepare environment, chmod failed"),
                () => caller.showDialog(InstallControl.getId(ctx))))
            // save appBuild
            Common.writeToFile(appBuildFile, info.appBuild)
            result
          } catch {
            case e =>
              state.set(AppActivity.State(DState.Broken, Android.getString(ctx, "error_prepare_unknown").
                getOrElse("Error prepare environment, unknown error"),
                () => caller.showDialog(InstallControl.getId(ctx))))
              false
            // TODO               return "Error process " + outFileName + ": " +!!! e.getMessage()!!!;
          }
        }
        !results.exists(_ == false) // all true
      }
    }
  } getOrElse false
  @Loggable
  private def prepareNativePath(caller: Activity): Boolean = {
    for {
      ctx <- AppActivity.Context
      appNativePath <- appNativePath
    } yield {
      try {
        if (!appNativePath.exists()) {
          log.debug("prepare native path: " + appNativePath.getAbsolutePath())
          appNativePath.mkdirs()
          val result = Common.execChmod(711, appNativePath)
          if (!result)
            state.set(AppActivity.State(DState.Broken, Android.getString(ctx, "error_prepare_chmod").
              getOrElse("Error prepare native path, chmod failed"),
              () => caller.showDialog(InstallControl.getId(ctx))))
          result
        } else
          true
      } catch {
        case e =>
          log.error(e.getMessage(), e)
          false
      }
    }
  } getOrElse false
  /*
   *  true - all ok
   *  false - we need some work
   */
  @Loggable
  private[base] def checkEnvironmentVersion(xmlOriginal: Option[Node], xmlInstalled: Option[Node]): Boolean = {
    if (xmlOriginal == None)
      return true
    else if (xmlInstalled == None)
      return false
    val versionOriginalText = (xmlOriginal.get \\ "manifest" \ "build" \ "version").text
    val versionInstalledText = (xmlInstalled.get \\ "manifest" \ "build" \ "version").text
    if (versionOriginalText.trim.isEmpty) {
      log.warn("build version in original mainfest not found")
      return false
    }
    if (versionInstalledText.trim.isEmpty) {
      log.warn("build version in installed mainfest not found")
      return false
    }
    try {
      val versionOriginal = new Version(versionOriginalText)
      val versionInstalled = new Version(versionInstalledText)
      log.debug("compare versions original: " + versionOriginal + ", installed: " + versionInstalled)
      versionOriginal.compareTo(versionInstalled) <= 0 // original version (from apk) <= installed version
    } catch {
      case e =>
        log.error(e.getMessage(), e)
        false
    }
  }
  @Loggable
  def synchronizeStateWithICtrlHost() = AppActivity.Context.foreach {
    activity =>
      AppService.Inner ! AppService.Message.Status(activity.getPackageName, {
        case Right(componentState) =>
          val appState = componentState.state match {
            case DState.Active =>
              AppActivity.State(DState.Active)
            case DState.Broken =>
              AppActivity.State(DState.Broken, "service failed")
            case _ =>
              AppActivity.State(DState.Passive)
          }
          AppActivity.Inner.state.set(appState)
        case Left(error) =>
          val appState = AppActivity.State(DState.Broken, error)
          AppActivity.Inner.state.set(appState)
      })
  }
  /*protected def listInterfaces(): Either[String, java.util.List[String]] =
    AppService().get() match {
      case Some(service) =>
        try {
          Right(service.listInterfaces())
        } catch {
          case e: RemoteException =>
            Left(e.getMessage)
        }
      case None =>
        Left("service unreachable")
    }*/
  @Loggable
  private def onMessageShowDialog[T <: Dialog](activity: Activity, dialog: () => T, onDismiss: Option[() => Unit])(implicit m: scala.reflect.Manifest[T]): Option[Dialog] = try {
    assert(!activitySafeDialog.isSet)
    if (!isActivityValid(activity)) {
      resetDialogSafe
      return None
    }
    activity.runOnUiThread(new Runnable {
      def run = activitySafeDialog.set((dialog(), () => {
        log.trace("safe dialog dismiss callback")
        AppActivity.Inner.enableRotation()
        onDismiss.foreach(_())
      }))
    })
    log.debug("show new safe dialog " + activitySafeDialog.get)
    (activitySafeDialog.get(DTimeout.longest) match {
      case Some((d, c)) =>
        log.debug("show new safe dialog " + d + " for " + m.erasure.getName)
        AppActivity.Inner.disableRotation()
        Option(d)
      case None =>
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
    assert(!activitySafeDialog.isSet)
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
    activitySafeDialog.get(DTimeout.longest) match {
      case Some((d, c)) =>
        if (d != null) {
          log.debug("show new safe dialog " + d + " for id " + id)
          activitySafeDialog.updateDismissCallback(() => {
            log.trace("safe dialog dismiss callback")
            AppActivity.Inner.enableRotation()
            onDismiss.foreach(_())
          })
          AppActivity.Inner.disableRotation()
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

object AppActivity extends Logging {
  @volatile private var inner: AppActivity = null
  private val deinitializationLock = new SyncVar[Boolean]()
  private val deinitializationInProgressLock = new AtomicBoolean(false)
  private val deinitializationTimeout = DTimeout.longest

  log.debug("alive")
  @Loggable
  private[lib] def init(root: Context, _inner: AppActivity = null) = {
    deinitializationLock.set(false)
    initRoutine(root, _inner)
  }
  private[lib] def initRoutine(root: Context, _inner: AppActivity) = synchronized {
    // cancel deinitialization sequence if any
    LazyInit("initialize AppCache") { AppCache.init(root) }
    if (inner != null) {
      log.info("reinitialize AppActivity core subsystem for " + root.getPackageName())
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
      log.info("initialize AppActivity for " + root.getPackageName())
    if (_inner != null)
      inner = _inner
    else
      inner = new AppActivity()
    inner.state.set(State(DState.Initializing))
  }
  private[lib] def resurrect() = deinitializationInProgressLock.synchronized {
    deinitializationLock.set(false) // try to cancel
    if (deinitializationInProgressLock.get) {
      log.debug("deinitialization in progress, waiting...")
      deinitializationInProgressLock.synchronized {
        while (deinitializationInProgressLock.get)
          deinitializationInProgressLock.wait
      }
    }
    log.info("resurrect AppActivity core subsystem")
    AppActivity.Inner match {
      case activity: AppActivity =>
        activity.activitySafeDialog.unset()
      case null =>
    }
  }
  private[lib] def deinit(): Unit = future {
    if (deinitializationInProgressLock.compareAndSet(false, true))
      try {
        val packageName = Context.map(_.getPackageName()).getOrElse("UNKNOWN")
        log.info("deinitializing AppActivity for " + packageName)
        if (deinitializationLock.isSet)
          deinitializationLock.unset()
        deinitializationLock.get(deinitializationTimeout) match {
          case Some(false) =>
            log.info("deinitialization AppActivity for " + packageName + " canceled")
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
    log.info("deinitialize AppActivity for " + packageName)
    assert(inner != null)
    val savedInner = inner
    inner = null
    if (AnyBase.isLastContext && AppService.Inner != null) {
      log.info("AppActivity hold last context. Clear.")
      AppService.deinitRoutine(packageName)
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
        log.debug("begin LazyInit block \"" + description + "\"")
        f
        log.debug("end LazyInit block \"" + description + "\"")
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
    private var activityDialogGuard: ScheduledExecutorService = null
    private val isPrivateReplace = new AtomicBoolean(false)
    private val lock = new Object

    @Loggable
    override def set(d: (Dialog, () => Unit), signalAll: Boolean = true): Unit = lock.synchronized {
      log.debug("set safe dialog to '" + d + "'")
      if (activityDialogGuard != null) {
        activityDialogGuard.shutdownNow
        activityDialogGuard = null
      }
      get(0) match {
        case Some((previousDialog, dismissCallback)) if previousDialog != null =>
          if (d._1 == previousDialog) {
            log.fatal("overwrite the same dialog '" + d + "'")
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
      if (d != null) {
        activityDialogGuard = Executors.newSingleThreadScheduledExecutor()
        activityDialogGuard.schedule(new Runnable {
          def run() = {
            log.fatal("dismiss stalled dialog " + d._1.getClass.getName)
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
            log.info("dismiss safe dialog " + d._1.getClass.getName)
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
      log.debug("unset safe dialog " + value)
      if (activityDialogGuard != null) {
        activityDialogGuard.shutdownNow
        activityDialogGuard = null
      }
      super.get(0).foreach {
        case (previousDialog, dismissCallback) =>
          if (previousDialog != null) {
            if (previousDialog.isShowing) {
              previousDialog.setOnDismissListener(null)
              previousDialog.dismiss
            }
            if (dismissCallback != null)
              dismissCallback()
          }
      }
      super.unset(signalAll)
    }
  }
  object Message {
    sealed trait Abstract
    case class PrepareEnvironment(activity: Activity, keep: Boolean, makePublic: Boolean, callback: (Boolean) => Any) extends Abstract
    case class ShowDialog[T <: Dialog](activity: Activity, dialog: () => T, onDismiss: Option[() => Unit]) extends Abstract
    case class ShowDialogResource(activity: Activity, dialog: Int, args: Option[Bundle], onDismiss: Option[() => Unit]) extends Abstract
  }
  case class State(val code: DState.Value, val data: String = null, val onClickCallback: () => Any = () => {
    log.g_a_s_e("default onClick callback for " + getClass().getName())
  }) extends Logging {
    log.debugWhere("create new state " + code, 3)
  }
}
