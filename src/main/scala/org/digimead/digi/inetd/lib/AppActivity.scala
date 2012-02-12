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

package org.digimead.digi.inetd.lib

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference

import scala.actors.Actor
import scala.collection.JavaConversions._
import scala.ref.WeakReference
import scala.xml.Node

import org.digimead.digi.inetd.lib.aop.Loggable
import org.digimead.digi.inetd.lib.versioning.DefaultArtifactVersion
import org.slf4j.LoggerFactory

import android.app.Activity
import android.content.Context
import android.content.Intent

protected class AppActivity private (var root: WeakReference[Context]) extends Actor {
  private val log = LoggerFactory.getLogger(getClass.getName().replaceFirst("org.digimead.digi.inetd", "o.d.d.i"))
  private var status: AtomicReference[AppActivity.Status] = new AtomicReference()
  lazy val appNativePath = root.get.map(ctx => new File(ctx.getFilesDir() + "/" + Common.Constant.apkNativePath + "/"))
  lazy val appNativeDescription = appNativePath.map(appNativePath => new File(appNativePath, "description.xml"))
  def act = {
    loop {
      react {
        case AppActivity.Message.PrepareEnvironment(activity, keep, public, callback) =>
          if (callback == null)
            prepareEnvironment(activity, keep, public)
          else
            callback(prepareEnvironment(activity, keep, public))
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
  def getStatus() = status.get
  def filters(): Seq[String] = get() match {
    case Some(root) =>
      root.getSharedPreferences(Common.Preference.filter, Context.MODE_PRIVATE).getAll().toSeq.map(t => t._1)
    case None =>
      Seq()
  }
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
      val xmlOriginal: Option[Node] = try {
        Some(scala.xml.XML.load(am.open(Common.Constant.apkNativePath + "/description.xml")))
      } catch {
        case e =>
          log.error(e.getMessage, e)
          None
      }
      val xmlInstalled: Option[Node] = try {
        to.find(_.getName() == "description.xml") match {
          case Some(description) => Some(scala.xml.XML.loadFile(description))
          case None => None
        }
      } catch {
        case e =>
          log.error(e.getMessage, e)
          None
      }
      if (xmlOriginal == None) {
        log.error("couldn't install native armeabi files without proper description.xml")
        AppActivity.Status(Common.State.error, Android.getString(ctx, "error_prepare_description"))
        return false
      }
      if (keep && to.forall(_.exists) && xmlInstalled != None && checkEnvironmentVersion(xmlOriginal, xmlInstalled)) {
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
              AppActivity.Status(Common.State.error, Android.getString(ctx, "error_prepare_chmod"),
                () => caller.showDialog(dialog.InstallINETD.getId(ctx)))
            result
          } catch {
            case e =>
              AppActivity.Status(Common.State.error, Android.getString(ctx, "error_prepare_unknown"),
                () => caller.showDialog(dialog.InstallINETD.getId(ctx)))
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
            AppActivity.Status(Common.State.error, Android.getString(ctx, "error_prepare_chmod"),
              () => caller.showDialog(dialog.InstallINETD.getId(ctx)))
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
      val versionOriginal = new DefaultArtifactVersion((xmlOriginal.get \ "info" \ "version").text)
      val versionInstalled = new DefaultArtifactVersion((xmlInstalled.get \ "info" \ "version").text)
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

object AppActivity {
  private val log = LoggerFactory.getLogger(getClass.getName().replaceFirst("org.digimead.digi.inetd", "o.d.d.i"))
  private var inner: AppActivity = null
  @Loggable
  def init(root: Context, _inner: AppActivity = null) = synchronized {
    AppCache.init(root)
    log.info("initialize AppActivity for " + root.getPackageName())
    assert(inner == null)
    if (_inner != null)
      inner = _inner
    else
      inner = new AppActivity(new WeakReference(root))
    Status(Common.State.initializing)
  }
  def deinit(): Unit = synchronized {
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
    sealed abstract class Abstract
    case class PrepareEnvironment(activity: Activity, keep: Boolean, makePublic: Boolean, callback: (Boolean) => Any) extends Abstract
  }
  case class Status(val state: Common.State.Value, val data: Any = null, val onClickCallback: () => Any = () => {
    log.debug("default onClick callback for " + getClass().getName())
  }) {
    AppActivity.this.synchronized {
      AppActivity.Inner.foreach(_.status.set(this))
      AppActivity.Context.foreach(_.sendBroadcast(new Intent(Common.Intent.update)))
      log.debug("set status to \"" + state + "\"")
    }
  }
}