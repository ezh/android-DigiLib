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

import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue

import scala.Array.canBuildFrom
import scala.annotation.implicitNotFound
import scala.annotation.tailrec
import scala.collection.immutable.HashSet
import scala.collection.mutable.HashMap
import scala.collection.mutable.Publisher
import scala.collection.mutable.SynchronizedMap
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.util.Common
import org.slf4j.LoggerFactory

import android.content.Context

trait Logging {
  implicit protected[lib] val log: RichLogger = Logging.getRichLogger(this)
}

sealed trait LoggingEvent

object Logging extends Publisher[LoggingEvent] {
  @volatile var logPrefix = "@" // prefix for all adb logcat TAGs, everyone may change (but should not) it on his/her own risk
  @volatile private[log] var isTraceExtraEnabled = false
  @volatile private[log] var isTraceEnabled = true
  @volatile private[log] var isDebugEnabled = true
  @volatile private[log] var isInfoEnabled = true
  @volatile private[log] var isWarnEnabled = true
  @volatile private[log] var isErrorEnabled = true
  @volatile private[log] var loggingThread = new Thread() // stub
  private[log] val flushLimit = 1000
  private[log] val pid = try { android.os.Process.myPid } catch { case e => 0 }
  private[log] val queue = new ConcurrentLinkedQueue[Record]
  private[log] val richLogger = new HashMap[String, RichLogger]() with SynchronizedMap[String, RichLogger]
  private[log] var logger = new HashSet[Logger]()
  private[log] var initializationContext: WeakReference[Context] = new WeakReference(null)
  private[log] var shutdownHook: Thread = null
  private val loggingThreadRecords = new Array[Record](flushLimit)
  val commonLogger = LoggerFactory.getLogger("@~*~*~*~*")
  AnyBase // init AnyBase before Logging

  def setTraceEnabled(t: Boolean) {
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, commonLogger.getName, if (t)
      "enable TRACE log level"
    else
      "disable TRACE log level"))
    isTraceEnabled = t
  }
  def setDebugEnabled(t: Boolean) {
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, commonLogger.getName, if (t)
      "enable DEBUG log level"
    else
      "disable DEBUG log level"))
    isDebugEnabled = t
  }
  def setInfoEnabled(t: Boolean) {
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, commonLogger.getName, if (t)
      "enable INFO log level"
    else
      "disable INFO log level"))
    isInfoEnabled = t
  }
  def setWarnEnabled(t: Boolean) {
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, commonLogger.getName, if (t)
      "enable WARN log level"
    else
      "disable WARN log level"))
    isWarnEnabled = t
  }
  def setErrorEnabled(t: Boolean) {
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, commonLogger.getName, if (t)
      "enable ERROR log level"
    else
      "disable ERROR log level"))
    isErrorEnabled = t
  }
  def offer(record: Record) = queue.synchronized {
    queue.offer(record)
    queue.notifyAll
  }
  def reset() = synchronized {
    deinit()
    queue.clear()
    // if initializationContext.get == None, than there isn't any initialization yet
    initializationContext.get.foreach(context => init(context))
  }
  private[lib] def init(context: Context): Unit = synchronized {
    def initCommon = {
      initializationContext = new WeakReference(context.getApplicationContext)
      shutdownHook = new Thread() { override def run() = deinit }
      Runtime.getRuntime().addShutdownHook(shutdownHook)
      logger.foreach(_.init(context.getApplicationContext))
      offer(Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, commonLogger.getName, "initialize logging"))
    }
    try {
      if (initializationContext.get == None) {
        initCommon
        resume
      } else
        initCommon
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
    offer(Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, commonLogger.getName, "deinitialize logging"))
    flush()
    delLogger(logger.toSeq)
    queue.clear()
    if (shutdownHook != null) {
      Runtime.getRuntime().removeShutdownHook(shutdownHook)
      shutdownHook = null
    }
  }
  def suspend() = {
    // non blocking check
    if (loggingThread.isAlive)
      synchronized {
        if (loggingThread.isAlive)
          loggingThread = new Thread() // stub
      }
  }
  def resume() = {
    // non blocking check
    if (!loggingThread.isAlive)
      synchronized {
        if (!loggingThread.isAlive) {
          loggingThread = new Thread("GenericLogger for " + Logging.getClass.getName) {
            this.setDaemon(true)
            @tailrec
            override def run() = {
              if (logger.nonEmpty && !queue.isEmpty) {
                flushQueue(flushLimit)
                Thread.sleep(50)
              } else
                queue.synchronized { queue.wait }
              if (loggingThread.getId == this.getId)
                run
            }
          }
          loggingThread.start
        }
      }
  }
  def flush(): Int = synchronized {
    if (logger.isEmpty)
      return -1
    val flushed = flushQueue()
    logger.foreach(_.flush)
    flushed
  }
  private def flushQueue(): Int = flushQueue(Int.MaxValue)
  @tailrec
  private[log] def flushQueue(n: Int, accumulator: Int = 0): Int = {
    var records = 0
    loggingThreadRecords.synchronized {
      val limit = if (n <= flushLimit) (n - accumulator) else flushLimit
      while (records < limit && !queue.isEmpty()) {
        loggingThreadRecords(records) = queue.poll().asInstanceOf[Record]
        try {
          publish(loggingThreadRecords(records))
        } catch {
          case e =>
            offer(Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, commonLogger.getName, e.getMessage, Some(e)))
        }
        records += 1
      }
      logger.foreach(_(loggingThreadRecords.take(records)))
    }
    if (records == flushLimit) {
      if (records == n)
        return accumulator + records
    } else
      return accumulator + records
    Thread.sleep(100)
    flushQueue(n, accumulator + records)
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
      offer(Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, commonLogger.getName, "add logger " + l))
    }
  }
  def delLogger(s: Seq[Logger]): Unit =
    synchronized { s.foreach(l => delLogger(l)) }
  def delLogger(l: Logger) = synchronized {
    if (logger.contains(l)) {
      offer(Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, commonLogger.getName, "delete logger " + l))
      logger = logger - l
      flush
      l.flush
      l.deinit
    }
  }
  def getRichLogger(obj: Logging): RichLogger = {
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
    getRichLogger(loggerName)
  }
  def getRichLogger(name: String): RichLogger = richLogger.synchronized {
    if (richLogger.isDefinedAt(name))
      return richLogger(name)
    val newLogger = new RichLogger(name)
    richLogger(name) = newLogger
    newLogger
  }
  def findRichLogger(f: ((String, RichLogger)) => Boolean): Option[(String, RichLogger)] =
    richLogger.find(f)
  case class Record(val date: Date,
    val tid: Long,
    val level: Level,
    val tag: String,
    val message: String,
    val throwable: Option[Throwable] = None,
    val pid: Int = Logging.pid) extends LoggingEvent {
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
