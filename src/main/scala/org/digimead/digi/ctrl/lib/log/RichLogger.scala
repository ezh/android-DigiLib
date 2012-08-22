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

import scala.annotation.implicitNotFound

import org.digimead.digi.ctrl.lib.util.ExceptionHandler
import org.slf4j.Marker

@implicitNotFound(msg = "please define implicit RichLogger")
class RichLogger(val name: String) extends org.slf4j.Logger {
  override def getName(): String = name
  // fast look while development, highlight it in your IDE
  def g_a_s_e(msg: String) {
    val t = new Throwable(msg)
    t.fillInStackTrace()
    error("GASE: " + msg + "\n" + t.getStackTraceString)
  }
  // error with stack trace
  def fatal(msg: String) {
    val t = new Throwable(msg)
    t.fillInStackTrace()
    error(msg, t)
  }
  def isTraceExtraEnabled(): Boolean = Logging.isTraceExtraEnabled
  // trace
  def isTraceEnabled(marker: Marker) = isTraceEnabled
  def trace(marker: Marker, msg: String) = trace(msg)
  def trace(marker: Marker, format: String, arg: Object) = trace(format, arg)
  def trace(marker: Marker, format: String, arg1: Object, arg2: Object) = trace(format, arg1, arg2)
  def trace(marker: Marker, format: String, argArray: Array[AnyRef]) = trace(format, argArray)
  def trace(marker: Marker, msg: String, t: Throwable) = trace(msg, t)
  /* @see org.slf4j.Logger#isTraceEnabled() */
  def isTraceEnabled(): Boolean = Logging.isTraceEnabled
  def trace(msg: String) = if (Logging.isTraceEnabled)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, name, msg))

  def trace(format: String, arg: Object) = if (Logging.isTraceEnabled) {
    val msg = format.format(arg)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, name, msg))
  }
  def trace(format: String, arg1: Object, arg2: Object) = if (Logging.isTraceEnabled) {
    val msg = format.format(arg1, arg2)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, name, msg))
  }
  def trace(format: String, argArray: Array[AnyRef]) = if (Logging.isTraceEnabled) {
    val msg = format.format(argArray: _*)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, name, msg))
  }
  def trace(msg: String, t: Throwable) = if (Logging.isTraceEnabled)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, name, msg, Some(t)))
  def traceWhere(msg: String): Unit = if (Logging.isTraceEnabled)
    if (Logging.isTraceExtraEnabled)
      logWhere(msg, trace, trace)(-2)
    else
      trace(msg)
  def traceWhere(msg: String, stackLine: Int): Unit = if (Logging.isTraceEnabled)
    if (Logging.isTraceExtraEnabled)
      logWhere(msg, trace, trace)(stackLine)
    else
      trace(msg)

  // debug
  def isDebugEnabled(marker: Marker) = isDebugEnabled
  def debug(marker: Marker, msg: String) = debug(msg)
  def debug(marker: Marker, format: String, arg: Object) = debug(format, arg)
  def debug(marker: Marker, format: String, arg1: Object, arg2: Object) = debug(format, arg1, arg2)
  def debug(marker: Marker, format: String, argArray: Array[AnyRef]) = debug(format, argArray)
  def debug(marker: Marker, msg: String, t: Throwable) = debug(msg, t)
  /* @see org.slf4j.Logger#isDebugEnabled() */
  def isDebugEnabled(): Boolean = Logging.isDebugEnabled
  def debug(msg: String) = if (Logging.isDebugEnabled)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, name, msg))
  def debug(format: String, arg: Object) = if (Logging.isDebugEnabled) {
    val msg = format.format(arg)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, name, msg))
  }
  def debug(format: String, arg1: Object, arg2: Object) = if (Logging.isDebugEnabled) {
    val msg = format.format(arg1, arg2)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, name, msg))
  }
  def debug(format: String, argArray: Array[AnyRef]) = if (Logging.isDebugEnabled) {
    val msg = format.format(argArray: _*)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, name, msg))
  }
  def debug(msg: String, t: Throwable) = if (Logging.isDebugEnabled)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, name, msg, Some(t)))
  def debugWhere(msg: String): Unit = if (Logging.isDebugEnabled)
    if (Logging.isTraceExtraEnabled)
      logWhere(msg, debug, debug)(-2)
    else
      debug(msg)
  def debugWhere(msg: String, stackLine: Int): Unit = if (Logging.isDebugEnabled)
    if (Logging.isTraceExtraEnabled)
      logWhere(msg, debug, debug)(stackLine)
    else
      debug(msg)

  // info
  def isInfoEnabled(marker: Marker) = isInfoEnabled
  def info(marker: Marker, msg: String) = info(msg)
  def info(marker: Marker, format: String, arg: Object) = info(format, arg)
  def info(marker: Marker, format: String, arg1: Object, arg2: Object) = info(format, arg1, arg2)
  def info(marker: Marker, format: String, argArray: Array[AnyRef]) = info(format, argArray)
  def info(marker: Marker, msg: String, t: Throwable) = info(msg, t)
  /* @see org.slf4j.Logger#isInfoEnabled() */
  def isInfoEnabled: Boolean = Logging.isInfoEnabled
  def info(msg: String) = if (Logging.isInfoEnabled)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, name, msg))
  def info(format: String, arg: Object) = if (Logging.isInfoEnabled) {
    val msg = format.format(arg)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, name, msg))
  }
  def info(format: String, arg1: Object, arg2: Object) = if (Logging.isInfoEnabled) {
    val msg = format.format(arg1, arg2)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, name, msg))
  }
  def info(format: String, argArray: Array[AnyRef]) = if (Logging.isInfoEnabled) {
    val msg = format.format(argArray: _*)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, name, msg))
  }
  def info(msg: String, t: Throwable) = if (Logging.isInfoEnabled)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, name, msg, Some(t)))
  def infoWhere(msg: String): Unit = if (Logging.isInfoEnabled)
    if (Logging.isTraceExtraEnabled)
      logWhere(msg, info, info)(-2)
    else
      info(msg)
  def infoWhere(msg: String, stackLine: Int = 4): Unit = if (Logging.isInfoEnabled)
    if (Logging.isTraceExtraEnabled)
      logWhere(msg, info, info)(stackLine)
    else
      info(msg)

  // warn
  def isWarnEnabled(marker: Marker) = isWarnEnabled
  def warn(marker: Marker, msg: String) = warn(msg)
  def warn(marker: Marker, format: String, arg: Object) = warn(format, arg)
  def warn(marker: Marker, format: String, arg1: Object, arg2: Object) = warn(format, arg1, arg2)
  def warn(marker: Marker, format: String, argArray: Array[AnyRef]) = warn(format, argArray)
  def warn(marker: Marker, msg: String, t: Throwable) = warn(msg, t)
  /* @see org.slf4j.Logger#isWarnEnabled() */
  def isWarnEnabled: Boolean = Logging.isWarnEnabled
  def warn(msg: String) = if (Logging.isWarnEnabled)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, name, msg))
  def warn(format: String, arg: Object) = if (Logging.isWarnEnabled) {
    val msg = format.format(arg)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, name, msg))
  }
  def warn(format: String, arg1: Object, arg2: Object) = if (Logging.isWarnEnabled) {
    val msg = format.format(arg1, arg2)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, name, msg))
  }
  def warn(format: String, argArray: Array[AnyRef]) = if (Logging.isWarnEnabled) {
    val msg = format.format(argArray: _*)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, name, msg))
  }
  def warn(msg: String, t: Throwable) = if (Logging.isWarnEnabled)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, name, msg, Some(t)))
  def warnWhere(msg: String): Unit = if (Logging.isWarnEnabled)
    if (Logging.isTraceExtraEnabled)
      logWhere(msg, warn, warn)(-2)
    else
      warn(msg)
  def warnWhere(msg: String, stackLine: Int = 4): Unit = if (Logging.isWarnEnabled)
    if (Logging.isTraceExtraEnabled)
      logWhere(msg, warn, warn)(stackLine)
    else
      warn(msg)

  // error
  def isErrorEnabled(marker: Marker) = isErrorEnabled
  def error(marker: Marker, msg: String) = error(msg)
  def error(marker: Marker, format: String, arg: Object) = error(format, arg)
  def error(marker: Marker, format: String, arg1: Object, arg2: Object) = error(format, arg1, arg2)
  def error(marker: Marker, format: String, argArray: Array[AnyRef]) = error(format, argArray)
  def error(marker: Marker, msg: String, t: Throwable) = error(msg, t)
  /* @see org.slf4j.Logger#isErrorEnabled() */
  def isErrorEnabled: Boolean = Logging.isErrorEnabled
  def error(msg: String) = if (Logging.isErrorEnabled)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, name, msg))
  def error(format: String, arg: Object) = if (Logging.isErrorEnabled) {
    val msg = format.format(arg)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, name, msg))
  }
  def error(format: String, arg1: Object, arg2: Object) = if (Logging.isErrorEnabled) {
    val msg = format.format(arg1, arg2)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, name, msg))
  }
  def error(format: String, argArray: Array[AnyRef]) = if (Logging.isErrorEnabled) {
    val msg = format.format(argArray: _*)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, name, msg))
  }
  // always enabled
  def error(msg: String, t: Throwable) {
    if (ExceptionHandler.allowGenerateStackTrace)
      ExceptionHandler.generateStackTrace(Thread.currentThread, t, System.currentTimeMillis)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, name, msg, Some(t)))
  }
  def errorWhere(msg: String): Unit = if (Logging.isErrorEnabled)
    if (Logging.isTraceExtraEnabled)
      logWhere(msg, error, error)(-2)
    else
      error(msg)
  def errorWhere(msg: String, stackLine: Int): Unit = if (Logging.isErrorEnabled)
    if (Logging.isTraceExtraEnabled)
      logWhere(msg, error, error)(stackLine)
    else
      error(msg)

  private def logWhere(msg: String, f1: (String, Throwable) => Unit, f2: (String => Unit))(stackLine: Int) {
    val t = new Throwable(msg)
    t.fillInStackTrace()
    if (stackLine == -1) { // ALL
      if (isTraceEnabled) {
        f1(msg, t)
      } else { // HERE
        val trace = t.getStackTrace
        val skip = trace.takeWhile(_.getFileName == "RichLogger.scala").size
        f2(msg + " at " + trace.take(skip + (-2 * -1) - 1).last)
      }
    } else if (stackLine <= -2) {
      val trace = t.getStackTrace
      val skip = trace.takeWhile(_.getFileName == "RichLogger.scala").size
      f2(msg + " at " + trace.take(skip + (stackLine * -1) - 1).last)
    } else
      f2(msg + " at " + t.getStackTrace.take(stackLine + 1).last)
  }
}
