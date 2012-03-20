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

import scala.actors.scheduler.DaemonScheduler
import scala.actors.scheduler.ResizableThreadPoolScheduler
import scala.concurrent.SyncVar
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.base.AppService
import org.digimead.digi.ctrl.lib.base.Report
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.util.ExceptionHandler

import android.content.Context

private[lib] trait AnyBase extends Logging {
  protected def onCreateBase(context: Context, callSuper: => Any) = {
    log.trace("AnyBase::onCreateBase")
    callSuper
    AnyBase.init(context)
  }
}

object AnyBase extends Logging {
  private lazy val uncaughtExceptionHandler = new ExceptionHandler()
  val info = new SyncVar[Option[Info]]
  info.set(None)
  System.setProperty("actors.enableForkJoin", "false")
  System.setProperty("actors.corePoolSize", "32")
  private val weakScheduler = new WeakReference(DaemonScheduler.impl.asInstanceOf[ResizableThreadPoolScheduler])
  log.debug("set default scala actors scheduler to " + weakScheduler.get.get.getClass.getName() + " "
    + weakScheduler.get.get.toString + "[name,priority,group]")
  log.debug("scheduler corePoolSize = " + scala.actors.HackDoggyCode.getResizableThreadPoolSchedulerCoreSize(weakScheduler.get.get) +
    ", maxPoolSize = " + scala.actors.HackDoggyCode.getResizableThreadPoolSchedulerMaxSize(weakScheduler.get.get))
  def init(context: Context, stackTraceOnUnknownContext: Boolean = true) = synchronized {
    context match {
      case activity: Activity =>
        if (AppActivity.context.get != context)
          AppActivity.init(context)
      case service: Service =>
        if (AppService.context.get != context)
          AppService.init(context)
      case context =>
        if (stackTraceOnUnknownContext)
          log.fatal("init from unknown context " + context)
    }
    if (AppActivity.Inner == null)
      AppActivity.init(context)
    if (AppService.Inner == null)
      AppService.init(context)
    if (AnyBase.info.get == None) {
      Info.init(context)
      Logging.init(context)
      Report.init(context)
      uncaughtExceptionHandler.register(context)
      log.debug("start AppActivity singleton actor")
      // start activity singleton actor
      AppActivity.Inner.start
    }
  }
  case class Info(val reportPath: File,
    val appVersion: String,
    val appBuild: String,
    val appPackage: String,
    val phoneModel: String,
    val androidVersion: String,
    val write: Boolean) {
    log.debug("reportPath: " + reportPath)
    log.debug("appVersion: " + appVersion)
    log.debug("appBuild: " + appBuild)
    log.debug("appPackage: " + appPackage)
    log.debug("phoneModel: " + phoneModel)
    log.debug("androidVersion: " + androidVersion)
    log.debug("write to storage: " + write)
    override def toString = "reportPath: " + reportPath +
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
        val info = new AnyBase.Info(reportPath = new File(context.getFilesDir(), "report"),
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
