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

import java.io.File
import java.util.Date

import scala.actors.Futures.future
import scala.actors.scheduler.DaemonScheduler
import scala.actors.scheduler.ResizableThreadPoolScheduler
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.base.Report
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.util.ExceptionHandler
import org.digimead.digi.ctrl.lib.util.SyncVar

import android.app.Activity
import android.app.Service
import android.content.Context

private[lib] trait AnyBase extends Logging {
  protected def onCreateBase(context: Context, callSuper: => Any = {}) = AnyBase.synchronized {
    log.trace("AnyBase::onCreateBase")
    callSuper
    AnyBase.init(context)
  }
  protected def onStartBase(context: Context) = AnyBase.synchronized {
    log.trace("AnyBase::onStartBase")
    AnyBase.reset(context, "onStart")
    if (AppComponent.Inner == null)
      AnyBase.init(context)
  }
  protected def onResumeBase(context: Context) = AnyBase.synchronized {
    log.trace("AnyBase::onResumeBase")
    AnyBase.stopOnShutdownTimer(context, "onResume")
  }
  protected def onPauseBase(context: Context) = AnyBase.synchronized {
    log.trace("AnyBase::onPauseBase")
    AnyBase.stopOnShutdownTimer(context, "onPause")
  }
  protected def onStopBase(context: Context, shutdownIfActive: Boolean) = AnyBase.synchronized {
    log.trace("AnyBase::onStopBase")
    AnyBase.startOnShutdownTimer(context, shutdownIfActive)
  }
  protected def onDestroyBase(context: Context, callSuper: => Any = {}) = AnyBase.synchronized {
    log.trace("AnyBase::onDestroyBase")
    callSuper
    AnyBase.deinit(context)
  }
}

object AnyBase extends Logging {
  private lazy val uncaughtExceptionHandler = new ExceptionHandler()
  @volatile private var contextPool = Seq[WeakReference[Context]]()
  @volatile private var currentContext: WeakReference[Context] = new WeakReference(null)
  @volatile private var reportDirectory = "report"
  private val onShutdownTimer = new SyncVar[String]() // string - cancel reason
  val info = new SyncVar[Option[Info]]
  info.set(None)
  System.setProperty("actors.enableForkJoin", "false")
  System.setProperty("actors.corePoolSize", "16")
  private val weakScheduler = new WeakReference(DaemonScheduler.impl.asInstanceOf[ResizableThreadPoolScheduler])
  log.debug("set default scala actors scheduler to " + weakScheduler.get.get.getClass.getName() + " "
    + weakScheduler.get.get.toString + "[name,priority,group]")
  log.debug("scheduler corePoolSize = " + scala.actors.HackDoggyCode.getResizableThreadPoolSchedulerCoreSize(weakScheduler.get.get) +
    ", maxPoolSize = " + scala.actors.HackDoggyCode.getResizableThreadPoolSchedulerMaxSize(weakScheduler.get.get))
  def init(context: Context, stackTraceOnUnknownContext: Boolean = true): Unit = synchronized {
    log.debug("initialize AnyBase context " + context.getClass.getName)
    reset(context, "init")
    if (!AppComponent.resurrect(context) || !AppControl.resurrect())
      return // shutdown in progress
    context match {
      case activity: DActivity =>
        if (!contextPool.exists(_.get == Some(context))) {
          contextPool = contextPool :+ new WeakReference(context)
          updateContext()
          if (contextPool.isEmpty)
            AppComponent.init(context)
        }
      case service: DService =>
        if (!contextPool.exists(_.get == Some(context))) {
          contextPool = contextPool :+ new WeakReference(context)
          updateContext()
          if (contextPool.isEmpty)
            AppControl.init(context)
        }
      case context =>
        // all other contexts are temporary, look at isLastContext
        if (!contextPool.exists(_.get == Some(context)))
          contextPool = contextPool :+ new WeakReference(context)
        if (stackTraceOnUnknownContext)
          log.fatal("init from unknown context " + context)
    }
    if (AppComponent.Inner == null)
      AppComponent.init(context)
    if (AppControl.Inner == null)
      AppControl.init(context)
    if (AnyBase.info.get == None) {
      Info.init(context)
      Logging.init(context)
      Report.init(context)
      uncaughtExceptionHandler.register(context)
      log.debug("start AppComponent singleton actor")
      AppComponent.Inner.start
    }
  }
  def deinit(context: Context): Unit = synchronized {
    log.debug("deinitialize AnyBase context " + context.getClass.getName)
    // reset
    if (reset(context, "deinit"))
      return // shutdown in progress
    // start deinit sequence
    if (AnyBase.isLastContext)
      AppControl.deinit()
    else
      log.debug("skip onDestroyExt deinitialization, because there is another context coexists")
    // unbind bindedICtrlPool entities for context
    if (AppComponent.Inner != null)
      AppComponent.Inner.bindedICtrlPool.foreach(t => {
        val key = t._1
        val (bindContext, connection, component) = t._2
        if (bindContext == context)
          AppComponent.Inner.bindedICtrlPool.remove(key).map(record => {
            log.debug("remove service connection to " + key + " from bindedICtrlPool")
            record._1.unbindService(record._2)
          })
      })
    // unbind bindedICtrlPool entities for context
    if (AppControl.Inner != null && AppControl.Inner.ctrlBindContext.get == context)
      AppControl.Inner.unbind
    // update contextPool
    contextPool = contextPool.filter(_.get != Some(context))
    updateContext()
  }
  // count only Activity and Service contexts
  def isLastContext() =
    contextPool.filter(_.get.map(c => c.isInstanceOf[Activity] || c.isInstanceOf[Service]) == Some(true)).size <= 1
  def getContext(): Option[Context] = currentContext.get match {
    case None => updateContext()
    case result: Some[_] => result
  }
  /**
   * Shutdown the app either safely or quickly. The app is killed safely by
   * killing the virtual machine that the app runs in after finalizing all
   * {@link Object}s created by the app. The app is killed quickly by abruptly
   * killing the process that the virtual machine that runs the app runs in
   * without finalizing all {@link Object}s created by the app. Whether the
   * app is killed safely or quickly the app will be completely created as a
   * new app in a new virtual machine running in a new process if the user
   * starts the app again.
   *
   * <P>
   * <B>NOTE:</B> The app will not be killed until all of its threads have
   * closed if it is killed safely.
   * </P>
   *
   * <P>
   * <B>NOTE:</B> All threads running under the process will be abruptly
   * killed when the app is killed quickly. This can lead to various issues
   * related to threading. For example, if one of those threads was making
   * multiple related changes to the database, then it may have committed some
   * of those changes but not all of those changes when it was abruptly
   * killed.
   * </P>
   *
   * @param safely
   *            Primitive boolean which indicates whether the app should be
   *            exited safely or quickly killed. If true then the app will be exited
   *            safely. Otherwise it will be killed quickly.
   */
  @Loggable
  def shutdownApp(packageName: String, safely: Boolean) = synchronized {
    if (safely) {
      log.info("shutdown safely " + packageName)
      Logging.flush
      Thread.sleep(1000)
      Logging.deinit
      /*
       * Force the system to close the app down completely instead of
       * retaining it in the background. The virtual machine that runs the
       * app will be killed. The app will be completely created as a new
       * app in a new virtual machine running in a new process if the user
       * starts the app again.
       */
      System.exit(0)
    } else {
      log.info("shutdown quick " + packageName)
      Logging.flush
      Thread.sleep(1000)
      Logging.deinit
      /*
       * Alternatively the process that runs the virtual machine could be
       * abruptly killed. This is the quickest way to remove the app from
       * the device but it could cause problems since resources will not
       * be finalized first. For example, all threads running under the
       * process will be abruptly killed when the process is abruptly
       * killed. If one of those threads was making multiple related
       * changes to the database, then it may have committed some of those
       * changes but not all of those changes when it was abruptly killed.
       */
      android.os.Process.sendSignal(android.os.Process.myPid(), 1)
    }
  }
  private def updateContext(): Option[Context] = synchronized {
    contextPool = contextPool.filter(_.get != None).sortBy(n => n.get match {
      case Some(activity) if activity.isInstanceOf[Activity] => 1
      case Some(service) if service.isInstanceOf[Service] => 2
      case _ => 3
    })
    contextPool.headOption.foreach(currentContext = _)
    log.debug("update primary context to " + currentContext)
    currentContext.get
  }
  @Loggable
  private def startOnShutdownTimer(context: Context, shutdownIfActive: Boolean) = synchronized {
    log.debug("start startOnShutdownTimer")
    if (onShutdownTimer.get(0) != Some(null))
      onShutdownTimer.set(null)
    future {
      onShutdownTimer.get(5000, _ != null) match {
        case Some(null) if isLastContext || shutdownIfActive =>
          log.info("start onShutdown sequence")
          AppComponent.deinit()
        case Some(null) =>
          log.info("cancel onShutdown sequence, component in use")
          onShutdownTimer.unset()
        case Some(reason) =>
          log.info("cancel onShutdown sequence, reason: " + reason)
          onShutdownTimer.unset()
        case None =>
          log.fatal("cancel onShutdown sequence, reason unknown")
          onShutdownTimer.unset()
      }
    }
  }
  // true - ok, false - shutdown in progress
  @Loggable
  private def stopOnShutdownTimer(context: Context, reason: String): Boolean = {
    onShutdownTimer.set(reason)
    AppComponent.resurrect(context)
  }
  // true - ok,  false - shutdown in progress
  @Loggable
  private def reset(context: Context, reason: String): Boolean = {
    if (AnyBase.stopOnShutdownTimer(context, reason))
      return false
    if (AppComponent.Inner != null) {
      AppComponent.Inner.state.resetBusyCounter
      context match {
        case component: DActivity =>
          if (AppComponent.Inner.activitySafeDialog.isSet)
            AppComponent.Inner.activitySafeDialog.unset()
        case _ =>
      }
    }
    true
  }
  case class Info(val reportPathInternal: File,
    val reportPathExternal: File,
    val appVersion: String,
    val appBuild: String,
    val appPackage: String,
    val phoneModel: String,
    val androidVersion: String,
    val write: Boolean) {
    log.debug("reportPathInternal: " + reportPathInternal)
    log.debug("reportPathExternal: " + reportPathExternal)
    log.debug("appVersion: " + appVersion)
    log.debug("appBuild: " + appBuild)
    log.debug("appPackage: " + appPackage)
    log.debug("phoneModel: " + phoneModel)
    log.debug("androidVersion: " + androidVersion)
    log.debug("write to storage: " + write)
    override def toString = "reportPathInternal: " + reportPathInternal +
      ", reportPathExternal: " + reportPathExternal +
      ", appVersion: " + appVersion + ", appBuild: " + appBuild +
      ", appPackage: " + appPackage + ", phoneModel: " + phoneModel +
      ", androidVersion: " + androidVersion
  }
  object Info {
    def init(context: Context) = synchronized {
      if (AnyBase.info.get == None) {
        // Get information about the Package
        val pm = context.getPackageManager()
        val pi = pm.getPackageInfo(context.getPackageName(), 0)
        val pref = context.getSharedPreferences(DPreference.Log, Context.MODE_PRIVATE)
        val writeReport = pref.getBoolean(pi.packageName, true)
        // 755
        val info = new AnyBase.Info(reportPathInternal = Common.getDirectory(context, reportDirectory, true,
          Some(true), Some(false), Some(true)).get,
          // 755
          reportPathExternal = Common.getDirectory(context, reportDirectory, false,
            Some(true), Some(false), Some(true)).get,
          appVersion = pi.versionName,
          appBuild = Common.dateString(new Date(pi.versionCode.toLong * 1000)),
          appPackage = pi.packageName,
          phoneModel = android.os.Build.MODEL,
          androidVersion = android.os.Build.VERSION.RELEASE,
          write = writeReport)
        AnyBase.info.set(Some(info))
      }
    }
  }
}
