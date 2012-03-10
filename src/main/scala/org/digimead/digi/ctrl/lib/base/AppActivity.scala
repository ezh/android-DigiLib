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

import scala.Array.canBuildFrom
import scala.actors.Futures._
import scala.actors.Actor
import scala.annotation.elidable
import scala.annotation.implicitNotFound
import scala.collection.JavaConversions._
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap
import scala.concurrent.SyncVar
import scala.ref.WeakReference
import scala.xml.Node
import scala.xml.XML

import org.digimead.digi.ctrl.lib.aop.RichLogger.rich2plain
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.aop.RichLogger
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
import org.digimead.digi.ctrl.ICtrlComponent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import annotation.elidable.ASSERTION

protected class AppActivity private () extends Actor with Logging {
  lazy val state = new SyncVar[AppActivity.State]() with Logging {
    private var lastNonBusyState: AppActivity.State = null
    private var busyCounter = 0
    set(AppActivity.State(DState.Unknown))
    override def set(newState: AppActivity.State) = synchronized {
      if (newState.code == DState.Busy) {
        busyCounter += 1
        log.debug("increase status busy counter to " + busyCounter)
        if (isSet && get.code != DState.Busy)
          lastNonBusyState = get
        super.set(newState)
      } else if (busyCounter != 0) {
        lastNonBusyState = newState
      } else
        super.set(newState)
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
  lazy val appNativePath = AppActivity.Context.map(ctx => new File(ctx.getFilesDir() + "/" + DConstant.apkNativePath + "/"))
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
  def filters(): Seq[String] = AppActivity.Context match {
    case Some(root) =>
      root.getSharedPreferences(DPreference.Filter, Context.MODE_PRIVATE).getAll().toSeq.map(t => t._1)
    case None =>
      Seq()
  }
  @Loggable
  def sendPrivateBroadcast(intent: Intent, flags: Seq[Int] = Seq()) = AppActivity.Context foreach {
    context =>
      intent.putExtra("__private__", true)
      flags.foreach(intent.setFlags)
      context.sendBroadcast(intent, DPermission.Base)
  }
  @Loggable
  def sendPrivateOrderedBroadcast(intent: Intent, flags: Seq[Int] = Seq()) = AppActivity.Context foreach {
    context =>
      intent.putExtra("__private__", true)
      flags.foreach(intent.setFlags)
      context.sendOrderedBroadcast(intent, DPermission.Base)
  }
  @Loggable
  protected def prepareEnvironment(caller: Activity, keep: Boolean, makePublic: Boolean): Boolean = {
    for {
      ctx <- AppActivity.Context
      appNativePath <- appNativePath
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
          case Some(description) => Some(scala.xml.XML.loadFile(description))
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
              state.set(AppActivity.State(DState.Broken, Android.getString(ctx, "error_prepare_chmod").
                getOrElse("Error prepare environment, chmod failed"),
                () => caller.showDialog(InstallControl.getId(ctx))))
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
          val result = Common.execChmod("a+x", appNativePath, true)
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
  @volatile private var inner: AppActivity = null
  @volatile private[lib] var context: WeakReference[Context] = new WeakReference(null)
  private val deinitializationLock = new SyncVar[Boolean]()

  log.debug("alive")
  @Loggable
  private[lib] def init(root: Context, _inner: AppActivity = null) = {
    deinitializationLock.set(false)
    synchronized {
      // cancel deinitialization sequence if any
      LazyInit("initialize AppCache") { AppCache.init(root) }
      if (inner != null) {
        log.info("reinitialize AppActivity core subsystem for " + root.getPackageName())
        // unbind services from bindedICtrlPool
        context.get.foreach {
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
      context = new WeakReference(root)
      if (_inner != null)
        inner = _inner
      else
        inner = new AppActivity()
      inner.state.set(State(DState.Initializing))
    }
  }
  private[lib] def deinit(): Unit = future {
    val name = Context.map(_.getPackageName()).getOrElse("UNKNOWN")
    log.info("deinitializing AppActivity for " + name)
    deinitializationLock.unset
    deinitializationLock.get(DTimeout.longest) match {
      case Some(false) =>
        log.info("deinitialization AppActivity for " + name + " canceled")
      case _ =>
        synchronized {
          log.info("deinitialize AppActivity for " + name)
          assert(inner != null)
          val savedInner = inner
          val savedContext = context.get
          inner = null
          context = new WeakReference(null)
          if (AppActivity.initialized)
            for {
              rootApp <- savedContext
              rootSrv <- AppService.context.get
            } if (rootApp == rootSrv) {
              log.info("AppActivity and AppService share the same context. Clear.")
              AppService.deinit()
            }
          // unbind services from bindedICtrlPool
          savedContext.foreach {
            context =>
              savedInner.bindedICtrlPool.keys.foreach(key => {
                savedInner.bindedICtrlPool.remove(key).map(record => {
                  log.debug("remove service connection to " + key + " from bindedICtrlPool")
                  context.unbindService(record._1)
                })
              })
          }
          AppCache.deinit()
        }
    }
  }
  def Inner = inner
  def Context = context.get
  def initialized = synchronized { inner != null }
  object LazyInit {
    @volatile private var pool: Seq[() => Any] = Seq()
    private val timeout = DTimeout.long
    def apply(description: String)(f: => Any)(implicit log: RichLogger) = synchronized {
      pool = pool :+ (() => {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler.schedule(new Runnable { def run = log.warn("LazyInit block \"" + description + "\" hang") }, timeout, TimeUnit.MILLISECONDS)
        log.debug("begin LazyInit block \"" + description + "\"")
        f
        log.debug("end LazyInit block \"" + description + "\"")
        scheduler.shutdownNow
      })
    }
    def init() = synchronized {
      val futures = pool.map(f => future {
        try {
          f()
        } catch {
          case e =>
            log.error(e.getMessage, e)
        }
      })
      awaitAll(timeout, futures: _*)
      pool = Seq()
    }
    def isEmpty = synchronized { pool.isEmpty }
    def nonEmpty = synchronized { pool.nonEmpty }
  }
  object Message {
    sealed trait Abstract
    case class PrepareEnvironment(activity: Activity, keep: Boolean, makePublic: Boolean, callback: (Boolean) => Any) extends Abstract
  }
  case class State(val code: DState.Value, val data: String = null, val onClickCallback: () => Any = () => {
    log.g_a_s_e("default onClick callback for " + getClass().getName())
  })
}
