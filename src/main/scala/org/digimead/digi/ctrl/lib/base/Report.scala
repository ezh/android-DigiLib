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

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileWriter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.Date
import java.util.UUID

import scala.Option.option2Iterable
import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.storage.GoogleCloud
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.Activity
import org.digimead.digi.ctrl.lib.AnyBase

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Base64.DEFAULT
import android.util.Base64.encode

object Report extends Logging {
  private[lib] val queue = new ConcurrentLinkedQueue[Record]
  private val run = new AtomicBoolean(true)
  val reportName = reportPrefix + ".log"
  private val reportThread = new Thread("GenericLogger " + reportName) {
    this.setDaemon(true)
    override def run() = AnyBase.info.get.foreach {
      info =>
        log.info("start report writing to " + reportName)
        val f = new File(info.reportPath, reportName)
        if (!f.getParentFile.exists)
          f.getParentFile().mkdirs()
        logWriter = new FileWriter(f, true)
        logWriter.write(info.toString + "\n")
        while (Report.this.run.get) {
          if (queue.isEmpty())
            Thread.sleep(500)
          else
            flush(1000)
        }
        logWriter.flush()
        logWriter.close()
        logWriter = null
    }
  }
  private val nullThread = new Thread("NullLogger " + reportName) {
    this.setDaemon(true)
    override def run() {
      while (Report.this.run.get) {
        if (queue.isEmpty())
          Thread.sleep(500)
        else
          queue.clear
      }
    }
  }
  private var workerThread: Thread = null
  private var logWriter: FileWriter = null
  def reportPrefix = "U" + android.os.Process.myUid + "-P" + android.os.Process.myPid + "-" + Common.dateString(new Date())
  private[lib] def init(context: Context): Unit = synchronized {
    try {
      AnyBase.info.get foreach {
        info =>
          // Try to create the files folder if it doesn't exist
          if (!info.reportPath.exists)
            info.reportPath.mkdir()
          if (info.write) {
            Runtime.getRuntime().addShutdownHook(new Thread() { override def run() = logWriter.flush })
            write()
          } else {
            drop()
          }
          workerThread.start()
          AppActivity.LazyInit("try to submit reports if there any stack traces, clean outdated") {
            // try to submit reports if there any stack traces
            context match {
              case context: Activity =>
                AnyBase.info.get.foreach {
                  info =>
                    Thread.sleep(DTimeout.normal) // take it gently ;-)
                    log.debug("looking for stack trace reports in: " + info.reportPath)
                    val dir = new File(info.reportPath + "/")
                    val reports = Option(dir.list()).flatten
                    if (reports.exists(_.endsWith(".stacktrace")))
                      org.digimead.digi.ctrl.lib.dialog.Report.submit("stack trace detected")
                    clean()
                }
              case _ =>
            }
          }
      }
    } catch {
      case e => log.error(e.getMessage, e)
    }
  }
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
          flush()
          activity.sendBroadcast(new Intent(DIntent.FlushReport))
          val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
          val processList = activityManager.getRunningAppProcesses().toSeq
          try {
            if (force || reports.exists(_.endsWith(".stacktrace"))) {
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
    clean()
  }
  private def clean(): Unit = synchronized {
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
  private def write() {
    workerThread = reportThread
  }
  private def drop() {
    workerThread = nullThread
  }
  private def close() = logWriter.close()
  private def flush(): Int = flush(java.lang.Integer.MAX_VALUE)
  private def flush(n: Int): Int = synchronized {
    if (logWriter == null)
      return 0
    var count = 0
    while (count < n && !queue.isEmpty()) {
      val record = queue.poll()
      logWriter.write(record + "\n")
      count += 1;
    }
    if (count > 0)
      logWriter.flush()
    count
  }
  class Record(val date: Date,
    val pid: Int,
    val tid: Long,
    val level: Char,
    val tag: String,
    val message: String) {
    override def toString =
      Seq(Common.dateString(date), pid.toString, tid.toString, level.toString, tag.toString).mkString(" ") + ": " + message
  }
}
