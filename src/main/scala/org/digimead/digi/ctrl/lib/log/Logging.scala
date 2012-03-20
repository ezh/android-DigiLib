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

package org.digimead.digi.ctrl.lib.log

import scala.annotation.implicitNotFound
import scala.collection.immutable.HashMap
import scala.concurrent.SyncVar
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.Date
import org.digimead.digi.ctrl.lib.util.Common
import org.slf4j.LoggerFactory
import android.content.Context
import org.digimead.digi.ctrl.lib.AnyBase
import scala.collection.immutable.HashSet

trait Logging {
  implicit protected[lib] val log: RichLogger = Logging.getLogger(this)
}

object Logging {
  @volatile var logPrefix = "@" // prefix for all adb logcat TAGs, everyone may change (but should not) it on his/her own risk
  private val pid = try { android.os.Process.myPid } catch { case e => 0 }
  private[lib] val queue = new ConcurrentLinkedQueue[Record]
  @volatile private[lib] var logger = new HashSet[Logger]()
  private var richLogger = new HashMap[String, RichLogger]()
  val commonLogger = LoggerFactory.getLogger("@~*~*~*~*")
  private var loggingThread = getWorker
  private[lib] def init(context: Context): Unit = synchronized {
    try {
      AnyBase.info.get foreach {
        info =>
          Runtime.getRuntime().addShutdownHook(new Thread() { override def run() = deinit })
      }
    } catch {
      case e => try {
        System.err.println(e.getMessage + "\n" + e.getStackTraceString)
        android.util.Log.wtf("Logging", e)
      } catch {
        case e =>
        // total destruction
      }
    }
  }
  private[lib] def deinit(): Unit = synchronized {
  }
  private def getWorker() = {
    val thread = new Thread("GenericLogger for " + Logging.getClass.getName) {
      this.setDaemon(true)
      override def run() = AnyBase.info.get.foreach {
        info =>
          while (logger.nonEmpty) {
            if (queue.isEmpty())
              Thread.sleep(500)
            else
              flushQueue(1000)
          }
      }
    }
    thread.start
    thread
  }
  private def flushQueue(): Int = flushQueue(java.lang.Integer.MAX_VALUE)
  private def flushQueue(n: Int): Int = synchronized {
    var count = 0
    while (count < n && !queue.isEmpty()) {
      val record = queue.poll()
      logger.foreach(_(record))
      count += 1;
    }
    count
  }
  def addLogger(l: Logger) = logger = logger + l
  def delLogger(l: Logger) = logger = logger - l
  def getLogger(obj: Logging): RichLogger = synchronized {
    val stackArray = Thread.currentThread.getStackTrace().dropWhile(_.getClassName != getClass.getName)
    val stack = if (stackArray(1).getFileName != stackArray(0).getFileName)
      stackArray(1) else stackArray(2)
    val fileRaw = stack.getFileName.split("""\.""")
    val fileParsed = if (fileRaw.length > 1)
      fileRaw.dropRight(1).mkString(".")
    else
      fileRaw.head
    val loggerName = if (obj.getClass().toString.last == '$') // add object mark to file name
      logPrefix + obj.getClass.getPackage.getName.split("""\.""").last + "." + fileParsed + "$"
    else
      logPrefix + obj.getClass.getPackage.getName.split("""\.""").last + "." + fileParsed
    getLogger(loggerName)
  }
  def getLogger(name: String): RichLogger = {
    if (richLogger.isDefinedAt(name))
      return richLogger(name)
    val newLogger = new RichLogger(name)
    richLogger = richLogger + (name -> newLogger)
    newLogger
  }
  case class Record(val date: Date,
    val tid: Long,
    val level: Level,
    val tag: String,
    val message: String,
    val throwable: Option[Throwable] = None,
    val pid: Int = Logging.pid) {
    override def toString =
      Seq(Common.dateString(date), pid.toString, tid.toString, level.toString, tag.toString).mkString(" ") + ": " + message
  }
  sealed trait Level
  object Level {
    case object Trace extends Level
    case object Debug extends Level
    case object Info extends Level
    case object Warn extends Level
    case object Error extends Level
  }
}
