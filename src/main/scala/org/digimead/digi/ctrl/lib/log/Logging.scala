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

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.Date

import scala.Array.canBuildFrom
import scala.collection.immutable.HashSet
import scala.collection.immutable.HashMap
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.AnyBase
import org.slf4j.LoggerFactory

import android.content.Context

trait Logging {
  implicit protected[lib] val log: RichLogger = Logging.getLogger(this)
}

object Logging {
  @volatile var logPrefix = "@" // prefix for all adb logcat TAGs, everyone may change (but should not) it on his/her own risk
  @volatile var isTraceEnabled = true
  @volatile var isDebugEnabled = true
  @volatile var isInfoEnabled = true
  @volatile var isWarnEnabled = true
  @volatile var isErrorEnabled = true
  private[log] val pid = try { android.os.Process.myPid } catch { case e => 0 }
  private[log] val queue = new ConcurrentLinkedQueue[Record]
  private[log] var logger = new HashSet[Logger]()
  private[log] var richLogger = new HashMap[String, RichLogger]()
  private[log] var loggingThread = getWorker
  private[log] var initializationContext: WeakReference[Context] = new WeakReference(null)
  val commonLogger = LoggerFactory.getLogger("@~*~*~*~*")
  def offer(record: Record) = queue.offer(record)
  private[lib] def init(context: Context): Unit = synchronized {
    try {
      AnyBase.info.get foreach {
        info =>
          Runtime.getRuntime().addShutdownHook(new Thread() { override def run() = deinit })
      }
      logger.foreach(_.init(context))
      if (!loggingThread.isAlive)
        loggingThread = getWorker
      initializationContext = new WeakReference(context)
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
  private[lib] def deinit(): Unit = synchronized { delLogger(logger.toSeq) }
  private def getWorker() = {
    val thread = new Thread("GenericLogger for " + Logging.getClass.getName) {
      this.setDaemon(true)
      override def run() = AnyBase.info.get.foreach {
        info =>
          while (logger.nonEmpty) {
            if (queue.isEmpty())
              Thread.sleep(500)
            else {
              flushQueue(1000)
              Thread.sleep(50)
            }
          }
      }
    }
    thread.start
    thread
  }
  def flush() = synchronized {
    while (!queue.isEmpty())
      Thread.sleep(100)
    logger.foreach(_.flush)
  }
  private def flushQueue(): Int = flushQueue(java.lang.Integer.MAX_VALUE)
  private def flushQueue(n: Int): Int = {
    var count = 0
    var records: Seq[Record] = Seq()
    while (count < n && !queue.isEmpty()) {
      queue.poll() match {
        case record: Record =>
          records = records :+ record
        case other =>
          records = records :+ Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, "__LOGGER__", "unknown record '" + other + "'")
      }
      count += 1;
    }
    logger.foreach(_(records))
    count
  }
  def dump() =
    ("queue size: " + queue.size + ", loggers: " + logger.mkString(",") + " thread: " + loggingThread.isAlive) +: queue.toArray.map(_.toString)
  def addLogger(s: Seq[Logger]): Unit =
    synchronized { s.foreach(l => addLogger(l, false)) }
  def addLogger(s: Seq[Logger], force: Boolean): Unit =
    synchronized { s.foreach(l => addLogger(l, force)) }
  def addLogger(l: Logger): Unit =
    synchronized { addLogger(l, false) }
  def addLogger(l: Logger, force: Boolean): Unit = synchronized {
    if (!logger.contains(l) || force) {
      initializationContext.get.foreach(l.init)
      logger = logger + l
    }
    if (!loggingThread.isAlive)
      loggingThread = getWorker
  }
  def delLogger(s: Seq[Logger]): Unit =
    synchronized { s.foreach(l => delLogger(l)) }
  def delLogger(l: Logger) = synchronized {
    logger = logger - l
    l.flush
    l.deinit
  }
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
    override def toString = "%s P%05d T%05d %s %-24s %s".format(Common.dateString(date), pid, tid, level.toString.charAt(0), tag + ":", message)
  }
  sealed trait Level
  object Level {
    case object Trace extends Level
    case object Debug extends Level
    case object Info extends Level
    case object Warn extends Level
    case object Error extends Level
  }
  object Where {
    val ALL = -1
    val HERE = -2
    val BEFORE = -3
  }
}
