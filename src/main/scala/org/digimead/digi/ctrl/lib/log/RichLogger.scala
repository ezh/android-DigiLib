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
import org.slf4j.helpers.MarkerIgnoringBase

@implicitNotFound(msg = "please define implicit RichLogger")
class RichLogger(private val _name: String) extends MarkerIgnoringBase {
  name = _name // set protected String name in abstract class NamedLoggerBase
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
  // trace
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
    logWhere(msg, trace, trace)(-2)
  def traceWhere(msg: String, stackLine: Int): Unit = if (Logging.isTraceEnabled)
    logWhere(msg, trace, trace)(stackLine)

  // debug
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
    logWhere(msg, debug, debug)(-2)
  def debugWhere(msg: String, stackLine: Int): Unit = if (Logging.isDebugEnabled)
    logWhere(msg, debug, debug)(stackLine)

  // info
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
    logWhere(msg, info, info)(-2)
  def infoWhere(msg: String, stackLine: Int = 4): Unit = if (Logging.isInfoEnabled)
    logWhere(msg, info, info)(stackLine)

  // warn
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
    logWhere(msg, warn, warn)(-2)
  def warnWhere(msg: String, stackLine: Int = 4): Unit = if (Logging.isWarnEnabled)
    logWhere(msg, warn, warn)(stackLine)

  // error
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
      ExceptionHandler.generateStackTrace(Thread.currentThread, t)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, name, msg, Some(t)))
  }
  def errorWhere(msg: String): Unit = if (Logging.isErrorEnabled)
    logWhere(msg, error, error)(-2)
  def errorWhere(msg: String, stackLine: Int): Unit = if (Logging.isErrorEnabled)
    logWhere(msg, error, error)(stackLine)

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
