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

package org.digimead.digi.ctrl.lib

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import scala.actors.Actor
import scala.collection.JavaConversions._
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap
import scala.ref.WeakReference
import scala.xml._
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.util.Version
import org.digimead.digi.ctrl.ICtrlComponent
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import scala.concurrent.SyncVar

protected class AppActivity private ( final val root: WeakReference[Context]) extends Actor with Logging {
  val state = new SyncVar[AppActivity.State]() {
    private var busyCounter: Tuple2[Int, AppActivity.State] = (0, AppActivity.State(Common.State.Unknown))
    override def set(x: AppActivity.State) = synchronized {
      if (x.code == Common.State.Busy) {
        busyCounter = (busyCounter._1 + 1, busyCounter._2)
        super.set(x)
      } else if (busyCounter._1 != 0) {
        busyCounter = (busyCounter._1, x)
      } else
        super.set(x)
      log.debug("set status to \"" + x + "\"")
      AppActivity.Context.foreach(_.sendBroadcast(new Intent(Common.Intent.Update)))
    }
    def freeBusy() {
      if (busyCounter._1 > 0)
        busyCounter = (busyCounter._1 - 1, busyCounter._2)
      if (busyCounter._1 == 0)
        set(busyCounter._2)
    }
  }
  lazy val appNativePath = root.get.map(ctx => new File(ctx.getFilesDir() + "/" + Common.Constant.apkNativePath + "/"))
  lazy val appNativeManifest = appNativePath.map(appNativePath => new File(appNativePath, "NativeManifest.xml"))
  lazy val nativeManifest = try {
    root.get.map(ctx => XML.load(ctx.getAssets().open(Common.Constant.apkNativePath + "/NativeManifest.xml")))
  } catch {
    case e => log.error(e.getMessage, e); None
  }
  lazy val applicationManifest = try {
    root.get.map(ctx => XML.load(ctx.getAssets().open(Common.Constant.apkNativePath + "/ApplicationManifest.xml")))
  } catch {
    case e => log.error(e.getMessage, e); None
  }
  private[lib] val bindedICtrlPool = new HashMap[String, (ServiceConnection, ICtrlComponent)] with SynchronizedMap[String, (ServiceConnection, ICtrlComponent)]
  //appNativePath.map(appNativePath => new File(appNativePath, "NativeManifest.xml"))
  //lazy val nativeManifest = appNativePath.map(appNativePath => new File(appNativePath, "NativeManifest.xml"))
  def act = {
    loop {
      react {
        case AppActivity.Message.PrepareEnvironment(activity, keep, public, callback) =>
          if (callback == null)
            prepareEnvironment(activity, keep, public)
          else
            callback(prepareEnvironment(activity, keep, public))
        case message: AnyRef =>
          log.error("skip unknown message " + message.getClass.getName + ": " + message)
        case message =>
          log.error("skip unknown message " + message)
      }
    }
  }
  def get(): Option[Context] = get(true)
  def get(throwError: Boolean): Option[Context] = synchronized {
    if (!throwError)
      return root.get
    root.get match {
      case Some(root) =>
        Some(root)
      case None =>
        val t = new Throwable("Intospecting stack frame")
        t.fillInStackTrace()
        log.error("uninitialized Context at AppActivity: " + t.getStackTraceString)
        None
    }
  }
  def filters(): Seq[String] = get() match {
    case Some(root) =>
      root.getSharedPreferences(Common.Preference.Filter, Context.MODE_PRIVATE).getAll().toSeq.map(t => t._1)
    case None =>
      Seq()
  }
  @Loggable
  def sendPrivateBroadcast(intent: Intent, flags: Seq[Int] = Seq(Intent.FLAG_RECEIVER_REGISTERED_ONLY)) = root.get.foreach(context => {
    intent.putExtra("__private__", true)
    flags.foreach(intent.setFlags)
    context.sendBroadcast(intent, Common.Permission.Base)
  })
  @Loggable
  def sendPrivateOrderedBroadcast(intent: Intent, flags: Seq[Int] = Seq()) = root.get.foreach(context => {
    intent.putExtra("__private__", true)
    flags.foreach(intent.setFlags)
    context.sendOrderedBroadcast(intent, Common.Permission.Base)
  })
  @Loggable
  protected def prepareEnvironment(caller: Activity, keep: Boolean, makePublic: Boolean): Boolean = {
    for {
      ctx <- root.get
      appNativePath <- appNativePath
    } yield {
      // Copy armeabi from assests to files folder:
      // /data/data/PKG_NAME/files
      if (!prepareNativePath(caller))
        return false
      val am = ctx.getAssets()
      val files = am.list(Common.Constant.apkNativePath)
      val from = files.map(name => Common.Constant.apkNativePath + "/" + name)
      val to = files.map(name => new File(appNativePath, name))
      val nativeManifestInstalled: Option[Node] = try {
        to.find(_.getName() == "NativeManifest.xml") match {
          case Some(description) => Some(scala.xml.XML.loadFile(description))
          case None => None
        }
      } catch {
        case e =>
          log.error(e.getMessage, e)
          None
      }
      if (nativeManifest == None) {
        log.error("couldn't install native armeabi files without proper manifest")
        state.set(AppActivity.State(Common.State.Broken, Android.getString(ctx, "error_prepare_manifest").
          getOrElse("Error prepare environment, manifest for native files not found")))
        return false
      }
      if (keep && to.forall(_.exists) && nativeManifestInstalled != None &&
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
            val permission = if (makePublic) "a+rx" else "u+x"
            val result = Common.execChmod(permission, entityTo)
            if (!result)
              state.set(AppActivity.State(Common.State.Broken, Android.getString(ctx, "error_prepare_chmod").
                getOrElse("Error prepare environment, chmod failed"),
                () => caller.showDialog(dialog.InstallControl.getId(ctx))))
            result
          } catch {
            case e =>
              state.set(AppActivity.State(Common.State.Broken, Android.getString(ctx, "error_prepare_unknown").
                getOrElse("Error prepare environment, unknown error"),
                () => caller.showDialog(dialog.InstallControl.getId(ctx))))
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
      ctx <- root.get
      appNativePath <- appNativePath
    } yield {
      try {
        if (!appNativePath.exists()) {
          log.debug("prepare native path: " + appNativePath.getAbsolutePath())
          appNativePath.mkdirs()
          val result = Common.execChmod("a+x", appNativePath, true)
          if (!result)
            state.set(AppActivity.State(Common.State.Broken, Android.getString(ctx, "error_prepare_chmod").
              getOrElse("Error prepare native path, chmod failed"),
              () => caller.showDialog(dialog.InstallControl.getId(ctx))))
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
  private def checkEnvironmentVersion(xmlOriginal: Option[Node], xmlInstalled: Option[Node]): Boolean = {
    if (xmlOriginal == None)
      return true
    else if (xmlInstalled == None)
      return false
    try {
      val versionOriginal = new Version((xmlOriginal.get \ "info" \ "version").text)
      val versionInstalled = new Version((xmlInstalled.get \ "info" \ "version").text)
      log.debug("compare versions original: " + versionOriginal + ", installed: " + versionInstalled)
      versionOriginal.compareTo(versionInstalled) <= 0 // original version (from apk) <= installed version
    } catch {
      case e =>
        log.error(e.getMessage(), e)
        false
    }
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
}

object AppActivity extends Logging {
  private var inner: AppActivity = null
  @Loggable
  private[lib] def init(root: Context, _inner: AppActivity = null) = synchronized {
    AppCache.init(root)
    if (inner != null) {
      log.info("reinitialize AppActivity core subsystem for " + root.getPackageName())
      // unbind services from bindedICtrlPool
      inner.root.get.map {
        context =>
          inner.bindedICtrlPool.keys.foreach(key => {
            inner.bindedICtrlPool.remove(key).map(record => {
              log.debug("remove service connection to " + key + " from bindedICtrlPool")
              context.unbindService(record._1)
            })
          })
      }
    } else
      log.info("initialize AppActivity for " + root.getPackageName())
    if (_inner != null)
      inner = _inner
    else
      inner = new AppActivity(new WeakReference(root))
    inner.state.set(State(Common.State.Initializing))
  }
  private[lib] def safe(root: Context) {
    if (inner == null)
      init(root)
  }
  private[lib] def deinit(): Unit = synchronized {
    val context = inner.root.get
    log.info("deinitialize AppActivity for " + context.map(_.getPackageName()).getOrElse("UNKNOWN"))
    assert(inner != null)
    val _inner = inner
    inner = null
    if (AppActivity.initialized)
      for {
        rootApp <- _inner.root.get;
        innerSrv <- AppService.Inner;
        rootSrv <- innerSrv.root.get
      } if (rootApp == rootSrv) {
        log.info("AppActivity and AppService share the same context. Clear.")
        AppService.deinit()
      }
    // unbind services from bindedICtrlPool
    _inner.root.get.map {
      context =>
        _inner.bindedICtrlPool.keys.foreach(key => {
          _inner.bindedICtrlPool.remove(key).map(record => {
            log.debug("remove service connection to " + key + " from bindedICtrlPool")
            context.unbindService(record._1)
          })
        })
    }
    AppCache.deinit()
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
  def Context = Inner.flatMap(_.get())
  def initialized = synchronized { inner != null }
  object Message {
    sealed trait Abstract
    case class PrepareEnvironment(activity: Activity, keep: Boolean, makePublic: Boolean, callback: (Boolean) => Any) extends Abstract
  }
  case class State(val code: Common.State.Value, val data: String = null, val onClickCallback: () => Any = () => {
    log.g_a_s_e("default onClick callback for " + getClass().getName())
  })
}