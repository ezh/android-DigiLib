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
import java.util.Date
import java.util.UUID

import scala.Option.option2Iterable
import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.storage.GoogleCloud
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.Activity
import org.digimead.digi.ctrl.lib.AnyBase

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent

object Report extends Logging {
  def reportPrefix = "U" + android.os.Process.myUid + "-P" + android.os.Process.myPid + "-" + Common.dateFile(new Date())
  private[lib] def init(context: Context): Unit = synchronized {
    try {
      AnyBase.info.get foreach {
        info =>
          AppActivity.LazyInit("try to submit reports if there any stack traces, clean outdated") {
            // move reports from internal to external storage
            Common.getDirectory(context, AnyBase.reportDirectory, true).foreach {
              internal =>
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
            }
          }
      }
    } catch {
      case e => log.error(e.getMessage, e)
    }
  }
  @Loggable
  def submit(context: Context, force: Boolean, uploadCallback: Option[(File, Int) => Any] = None): Unit = synchronized {
    for {
      info <- AnyBase.info.get
      context <- AppActivity.Context
    } {
      log.debug("looking for error reports in: " + info.reportPath)
      val dir = new File(info.reportPath + "/")
      val reports = Option(dir.list()).flatten
      if (reports.isEmpty)
        return
      context match {
        case activity: Activity =>
          activity.sendBroadcast(new Intent(DIntent.FlushReport))
          val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
          val processList = activityManager.getRunningAppProcesses().toSeq
          try {
            if (force || reports.exists(_.endsWith(".trc"))) {
              val sessionId = UUID.randomUUID.toString + "-"
              val futures = reports.map(name => {
                val report = new File(info.reportPath, name)
                val active = try {
                  val name = report.getName
                  val pid = Integer.parseInt(name.split('-')(1).drop(1))
                  processList.exists(_.pid == pid)
                } catch {
                  case _ =>
                    false
                }
                Logging.flush
                if (active) {
                  log.debug("there is active report " + report.getName)
                  if (force) {
                    uploadCallback.foreach(_(report, reports.size))
                    GoogleCloud.upload(report, sessionId)
                  } else {
                    uploadCallback.foreach(_(report, reports.size))
                    None
                  }
                } else {
                  log.debug("there is passive report " + report.getName)
                  uploadCallback.foreach(_(report, reports.size))
                  GoogleCloud.upload(report, sessionId)
                }
              })
              // IMHO there is cloud rate limit. futures are useless
              // awaitAll(DTimeout.long, futures.flatten.toSeq: _*)
            }
          } catch {
            case e =>
              log.error(e.getMessage, e)
          }
        case service: Service =>
          log.info("unable to send application report from service context")
        case context =>
          log.info("unable to send application report from unknown context " + context)
      }
    }
  }
  @Loggable
  def clean(): Unit = synchronized {
    for {
      info <- AnyBase.info.get
      context <- AppActivity.Context
    } {
      val dir = new File(info.reportPath + "/")
      val reports = Option(dir.list()).flatten
      if (reports.isEmpty)
        return
      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
      val processList = activityManager.getRunningAppProcesses().toSeq
      try {
        reports.foreach(name => {
          val report = new File(info.reportPath, name)
          val active = try {
            val name = report.getName
            val pid = Integer.parseInt(name.split('-')(1).drop(1))
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
