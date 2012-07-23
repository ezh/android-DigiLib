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

import scala.Option.option2Iterable
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
import android.os.Handler
import android.os.Looper
import stopwatch.Stopwatch
import stopwatch.StopwatchGroup

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
    AnyBase.cancelOnShutdownTimer(context, "onResume")
  }
  protected def onPauseBase(context: Context) = AnyBase.synchronized {
    log.trace("AnyBase::onPauseBase")
    AnyBase.cancelOnShutdownTimer(context, "onPause")
  }
  protected def onStopBase(context: Context, shutdownIfActive: Boolean) = AnyBase.synchronized {
    log.trace("AnyBase::onStopBase")
    AnyBase.startOnShutdownTimer(context, "onStop", shutdownIfActive)
  }
  protected def onDestroyBase(context: Context, callSuper: => Any = {}) = AnyBase.synchronized {
    log.trace("AnyBase::onDestroyBase")
    callSuper
    /*
     *  deinitialize only if there was initialization
     *  AnyBase.info initialize only once at the beginning
     *  call onDestroy without onCreate - looks like a dirty trick from Android team
     */
    if (AnyBase.info.get.nonEmpty)
      AnyBase.deinit(context)
  }
}

/**
 * Digi base singleton
 *
 * @note startup duration is around 500ms
 */
object AnyBase extends Logging {
  /** profiling support  */
  @volatile private var stopWatchGroups = Seq[StopwatchGroup](Stopwatch)
  val (ppGroup, ppStartup, ppLoading) = {
    val group = getStopWatchGroup("DigiLib")
    group.enabled = true
    group.enableOnDemand = true
    (group, group.start("startup"), group.start("AnyBase$"))
  }
  System.setProperty("actors.enableForkJoin", "false")
  System.setProperty("actors.corePoolSize", "16")
  System.setProperty("actors.maxPoolSize", "16")
  private lazy val uncaughtExceptionHandler = new ExceptionHandler()
  @volatile private var contextPool = Seq[WeakReference[Context]]()
  @volatile private var currentContext: WeakReference[Context] = new WeakReference(null)
  @volatile private var reportDirectory = "report"
  @volatile private var onShutdownProtect = new WeakReference[Activity](null)
  private val onShutdownState = new SyncVar[ShutdownState]() // string - cancel reason
  private val handler = new Handler() // bound to the current thread (UI)
  // heavy weight: scheduler 200ms, log 70ms
  private val weakScheduler = {
    val previousPriority = Thread.currentThread.getPriority
    Thread.currentThread.setPriority(Thread.MAX_PRIORITY)
    val scheduler = DaemonScheduler.impl.asInstanceOf[ResizableThreadPoolScheduler]
    log.debug("set default scala actors scheduler to " + scheduler.getClass.getName() + " "
      + scheduler.toString + "[name,priority,group]")
    log.debug("scheduler corePoolSize = " + scala.actors.HackDoggyCode.getResizableThreadPoolSchedulerCoreSize(scheduler) +
      ", maxPoolSize = " + scala.actors.HackDoggyCode.getResizableThreadPoolSchedulerMaxSize(scheduler))
    log.debug("adjust UI thread priority %d -> %d".format(previousPriority, Thread.currentThread.getPriority))
  }
  val uiThreadID: Long = Looper.getMainLooper.getThread.getId
  val info = new SyncVar[Option[Info]]
  info.set(None)
  ppLoading.stop

  def getStopWatchGroup(name: String) = synchronized {
    stopWatchGroups.find(_.name == name) match {
      case Some(group) =>
        group
      case None =>
        val group = new StopwatchGroup(name)
        stopWatchGroups = stopWatchGroups :+ group
        group
    }
  }
  def dumpStopWatchStatistics() = stopWatchGroups.sortBy(_.name).foreach {
    group =>
      log.debug("""process "%s" stopwatch statistics""".format(group.name))
      group.names.toSeq.sorted.foreach(name =>
        log.debug(group.snapshot(name).toShortString))
  }
  def init(context: Context, stackTraceOnUnknownContext: Boolean = true): Unit = synchronized {
    if (AnyBase.info.get == None) {
      // dump profiling at startup
      ppStartup.stop
      ppGroup.names.toSeq.sorted.foreach(name => log.debug(ppGroup.snapshot(name).toShortString))
    }
    ppGroup("AnyBase.init") {
      log.debug("initialize AnyBase context " + context.getClass.getName)
      ppGroup("AnyBase.init.A resurrect objects") { reset(context, "init") }
      ppGroup("AnyBase.init.B initialize object from context") {
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
      }
      ppGroup("AnyBase.init.C initialize companion object") {
        if (AppComponent.Inner == null)
          AppComponent.init(context)
        if (AppControl.Inner == null)
          AppControl.init(context)
      }
      if (AnyBase.info.get == None)
        ppGroup("AnyBase.init.D initialize AnyBase") {
          Info.init(context)
          Logging.init(context)
          Report.init(context)
          uncaughtExceptionHandler.register(context)
          log.debug("start AppComponent singleton actor")
        }
    }
  }
  def deinit(context: Context): Unit = synchronized {
    log.debug("context pool are " + contextPool.flatMap(_.get).mkString(", "))
    if (!contextPool.exists(_.get == Some(context)))
      return // shutdown in progress/this context already cleared
    log.debug("deinitialize AnyBase context " + context.getClass.getName)
    // clear state
    abortOnShutdownTimer(context, "deinit")
    reset(context, "deinit")
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
  def runOnUiThread(f: => Any): Unit = getContext.map {
    case activity: Activity => activity.runOnUiThread(new Runnable { def run() = f })
    case _ => AnyBase.handler.post(new Runnable { def run = f })
  } getOrElse {
    AnyBase.handler.post(new Runnable { def run = f })
  }
  // count only Activity and Service contexts
  def isLastContext() =
    contextPool.filter(_.get.map(c => c.isInstanceOf[Activity] || c.isInstanceOf[Service]) == Some(true)).size <= 1
  def getContext(): Option[Context] = currentContext.get match {
    case None => updateContext()
    case result: Some[_] => result
  }
  def preventShutdown(activity: Activity) =
    onShutdownProtect = new WeakReference(activity)
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
  def shutdownApp(packageName: String, safely: Boolean) = onShutdownState.synchronized {
    if (log.isTraceEnabled)
      dumpStopWatchStatistics
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
    if (contextPool.nonEmpty) {
      contextPool.headOption.foreach(currentContext = _)
      log.debug("update primary context to " + currentContext.get)
      currentContext.get
    } else {
      currentContext = new WeakReference(null)
      None
    }
  }
  @Loggable
  private def startOnShutdownTimer(context: Context, reason: String, shutdownIfActive: Boolean): Unit = synchronized {
    log.debug("start startOnShutdownTimer, reason: " + reason)
    if (onShutdownProtect.get match {
      case Some(activity) =>
        if (!activity.isFinishing && activity.getWindow != null) {
          log.debug("prevent start onShutdown timer - protector in use " + activity)
          true
        } else false
      case None =>
        false
    }) return
    if (onShutdownState.get(0) != Some(Shutdown.InProgress))
      onShutdownState.set(Shutdown.InProgress(reason))
    future {
      onShutdownState.get(5000, _ != Shutdown.InProgress) match {
        case Some(Shutdown.InProgress(reason)) if isLastContext || shutdownIfActive =>
          log.info("start onShutdown sequence, reason: " + reason)
          AppComponent.deinit()
        case Some(Shutdown.InProgress(reason)) =>
          log.info("cancel onShutdown sequence, reason: " + reason + " - component is in use")
          onShutdownState.unset()
        case Some(Shutdown.Cancel(reason)) =>
          log.info("cancel onShutdown sequence, reason: " + reason)
          onShutdownState.unset()
        case Some(Shutdown.Abort(reason)) =>
          log.info("abort onShutdown sequence, reason: " + reason)
          onShutdownState.unset()
        case None =>
          log.fatal("cancel onShutdown sequence, reason unknown")
          onShutdownState.unset()
      }
    }
  }
  @Loggable
  private def cancelOnShutdownTimer(context: Context, reason: String): Unit = {
    onShutdownState.set(Shutdown.Cancel(reason))
    AppComponent.resurrect()
    AppControl.resurrect()
  }
  @Loggable
  private def abortOnShutdownTimer(context: Context, reason: String): Unit = {
    onShutdownState.set(Shutdown.Abort(reason))
    AppComponent.resurrect(true)
    AppControl.resurrect(true)
  }
  @Loggable
  private def reset(context: Context, reason: String): Unit = synchronized {
    AnyBase.cancelOnShutdownTimer(context, reason)
    Option(AppComponent.Inner) match {
      case Some(inner) =>
        inner.state.resetBusyCounter
        context match {
          case component: DActivity =>
            if (inner.activitySafeDialog.isSet)
              inner.activitySafeDialog.unset()
            inner.bindedICtrlPool.keys.foreach(key => {
              inner.bindedICtrlPool.remove(key).map(record => {
                log.debug("remove service connection to " + key + " from bindedICtrlPool")
                record._1.unbindService(record._2)
              })
            })
          case _ =>
        }
      case None =>
    }
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
  sealed trait ShutdownState
  object Shutdown {
    case class InProgress(reason: String) extends ShutdownState
    case class Cancel(reason: String) extends ShutdownState
    case class Abort(reason: String) extends ShutdownState
  }
}
