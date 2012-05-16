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
import java.io.FilenameFilter
import java.util.Date
import java.util.UUID

import scala.Option.option2Iterable
import scala.collection.JavaConversions._
import scala.util.control.ControlThrowable

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.storage.GoogleCloud
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.DActivity
import org.digimead.digi.ctrl.lib.AnyBase

import android.app.Activity
import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent

object Report extends Logging {
  val keepLogFiles = 4
  val keepTrcFiles = 8
  def reportPrefix = "U" + android.os.Process.myUid + "-" + Common.dateFile(new Date()) + "-P" + android.os.Process.myPid
  private[lib] def init(context: Context): Unit = synchronized {
    try {
      // create report directory in Common.getDirectory and add LazyInit
      for {
        info <- AnyBase.info.get
        internal <- Common.getDirectory(context, info.reportPath.getName, true, 755)
      } AppComponent.LazyInit("move report to SD, clean outdated") {
        // move reports from internal to external storage
        if (info.reportPath.getAbsolutePath != internal.getAbsolutePath) {
          log.debug("move reports from " + internal + " to " + info.reportPath)
          internal.listFiles.foreach {
            fileFrom =>
              log.debug("move " + fileFrom.getName)
              val fileTo = new File(info.reportPath, fileFrom.getName)
              Common.copyFile(fileFrom, fileTo)
              fileFrom.delete
          }
        }
        clean()
      }
    } catch {
      case e => log.error(e.getMessage, e)
    }
  }
  @Loggable
  def submit(context: Context, force: Boolean, uploadCallback: Option[(File, Int) => Any] = None): Boolean = synchronized {
    val a = for {
      info <- AnyBase.info.get
      context <- AppComponent.Context
    } yield {
      log.debug("looking for error reports in: " + info.reportPath)
      val dir = new File(info.reportPath + "/")
      val reports = Option(dir.list()).getOrElse(Array[String]())
      if (reports.isEmpty)
        return true
      context match {
        case activity: Activity with DActivity =>
          activity.sendBroadcast(new Intent(DIntent.FlushReport))
          Thread.sleep(500) // waiting for no reason ;-)
          val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
          val processList = activityManager.getRunningAppProcesses().toSeq
          try {
            if (force || reports.exists(_.endsWith(".trc"))) {
              val sessionId = UUID.randomUUID.toString + "-"
              val futures = reports.map(name => {
                val report = new File(info.reportPath, name)
                val active = try {
                  val name = report.getName
                  val pid = Integer.parseInt(name.split("""[-\.]""")(4).drop(1))
                  processList.exists(_.pid == pid)
                } catch {
                  case _ =>
                    false
                }
                Logging.flush
                if (active) {
                  log.debug("there is an active report " + report.getName)
                  uploadCallback.foreach(_(report, reports.size))
                  GoogleCloud.upload(report, sessionId)
                } else {
                  log.debug("there is a passive report " + report.getName)
                  uploadCallback.foreach(_(report, reports.size))
                  GoogleCloud.upload(report, sessionId)
                }
              })
              // IMHO there is cloud rate limit. futures are useless
              // awaitAll(DTimeout.long, futures.flatten.toSeq: _*)
              futures.forall(_ == true)
            } else {
              true
            }
          } catch {
            case ce: ControlThrowable => throw ce // propagate
            case e =>
              log.error(e.getMessage, e)
              false
          }
        case service: Service =>
          log.info("unable to send application report from service context")
          false
        case context =>
          log.info("unable to send application report from unknown context " + context)
          false
      }
    }
    a
  } getOrElse false
  @Loggable
  def clean(): Unit = synchronized {
    for {
      info <- AnyBase.info.get
      context <- AppComponent.Context
    } {
      val dir = new File(info.reportPath + "/")
      try {
        // delete description files
        Option(dir.list(new FilenameFilter {
          def accept(dir: File, name: String) =
            name.toLowerCase.endsWith(".description")
        })).getOrElse(Array[String]()).foreach(name => {
          val report = new File(info.reportPath, name)
          log.info("delete outdated description file " + report.getName)
          report.delete
        })
        // delete png files
        Option(dir.list(new FilenameFilter {
          def accept(dir: File, name: String) =
            name.toLowerCase.endsWith(".png")
        })).getOrElse(Array[String]()).foreach(name => {
          val report = new File(info.reportPath, name)
          log.info("delete outdated png file " + report.getName)
          report.delete
        })
        // delete log files
        Option(dir.list(new FilenameFilter {
          def accept(dir: File, name: String) =
            name.toLowerCase.endsWith(".log")
        })).getOrElse(Array[String]()).sorted.reverse.drop(keepLogFiles).foreach(name => {
          val report = new File(info.reportPath, name)
          log.info("delete outdated log file " + report.getName)
          report.delete
        })
        // delete trc files
        Option(dir.list(new FilenameFilter {
          def accept(dir: File, name: String) =
            name.toLowerCase.endsWith(".trc")
        })).getOrElse(Array[String]()).toSeq.sorted.reverse.drop(keepTrcFiles).foreach(name => {
          val trace = new File(info.reportPath, name)
          log.info("delete outdated stacktrace file " + trace.getName)
          trace.delete
        })
      } catch {
        case e =>
          log.error(e.getMessage, e)
      }
    }
  }
  @Loggable
  def cleanAfterReview() {
    for {
      info <- AnyBase.info.get
      context <- AppComponent.Context
    } {
      val dir = new File(info.reportPath + "/")
      val reports = Option(dir.list()).getOrElse(Array[String]())
      if (reports.isEmpty)
        return
      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
      val processList = activityManager.getRunningAppProcesses().toSeq
      try {
        reports.foreach(name => {
          val report = new File(info.reportPath, name)
          val active = try {
            val name = report.getName
            val pid = Integer.parseInt(name.split("""[-\.]""")(4).drop(1))
            processList.exists(_.pid == pid)
          } catch {
            case _ =>
              false
          }
          if (!active || !report.getName.endsWith(".log")) {
            log.info("delete " + report.getName)
            report.delete
          }
        })
      } catch {
        case e =>
          log.error(e.getMessage, e)
      }
    }
  }
}
