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
  def isTraceEnabled(): Boolean = true
  def trace(msg: String) =
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, name, msg))

  def trace(format: String, arg: Object) {
    val msg = format.format(arg)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, name, msg))
  }
  def trace(format: String, arg1: Object, arg2: Object) {
    val msg = format.format(arg1, arg2)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, name, msg))
  }
  def trace(format: String, argArray: Array[AnyRef]) {
    val msg = format.format(argArray: _*)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, name, msg))
  }
  def trace(msg: String, t: Throwable) =
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, name, msg, Some(t)))
  def traceWhere(msg: String)(stackLine: Int = -1) =
    logWhere(msg, trace, trace)(stackLine)

  // debug
  /* @see org.slf4j.Logger#isDebugEnabled() */
  def isDebugEnabled(): Boolean = true
  def debug(msg: String) =
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, name, msg))
  def debug(format: String, arg: Object) {
    val msg = format.format(arg)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, name, msg))
  }
  def debug(format: String, arg1: Object, arg2: Object) {
    val msg = format.format(arg1, arg2)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, name, msg))
  }
  def debug(format: String, argArray: Array[AnyRef]) {
    val msg = format.format(argArray: _*)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, name, msg))
  }
  def debug(msg: String, t: Throwable) =
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, name, msg, Some(t)))
  def debugWhere(msg: String)(stackLine: Int = -1) =
    logWhere(msg, debug, debug)(stackLine)

  // info
  /* @see org.slf4j.Logger#isInfoEnabled() */
  def isInfoEnabled: Boolean = true
  def info(msg: String) {
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, name, msg))
  }
  def info(format: String, arg: Object) {
    val msg = format.format(arg)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, name, msg))
  }
  def info(format: String, arg1: Object, arg2: Object) {
    val msg = format.format(arg1, arg2)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, name, msg))
  }
  def info(format: String, argArray: Array[AnyRef]) {
    val msg = format.format(argArray: _*)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, name, msg))
  }
  def info(msg: String, t: Throwable) =
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, name, msg, Some(t)))
  def infoWhere(msg: String)(stackLine: Int = -1) =
    logWhere(msg, info, info)(stackLine)

  // warn
  /* @see org.slf4j.Logger#isWarnEnabled() */
  def isWarnEnabled: Boolean = true
  def warn(msg: String) {
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, name, msg))
  }
  def warn(format: String, arg: Object) {
    val msg = format.format(arg)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, name, msg))
  }
  def warn(format: String, arg1: Object, arg2: Object) {
    val msg = format.format(arg1, arg2)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, name, msg))
  }
  def warn(format: String, argArray: Array[AnyRef]) {
    val msg = format.format(argArray: _*)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, name, msg))
  }
  def warn(msg: String, t: Throwable) =
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, name, msg, Some(t)))
  def warnWhere(msg: String)(stackLine: Int = -1) =
    logWhere(msg, warn, warn)(stackLine)

  // error
  /* @see org.slf4j.Logger#isErrorEnabled() */
  def isErrorEnabled: Boolean = true
  def error(msg: String) =
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, name, msg))
  def error(format: String, arg: Object) {
    val msg = format.format(arg)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, name, msg))
  }
  def error(format: String, arg1: Object, arg2: Object) {
    val msg = format.format(arg1, arg2)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, name, msg))
  }
  def error(format: String, argArray: Array[AnyRef]) {
    val msg = format.format(argArray: _*)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, name, msg))
  }
  def error(msg: String, t: Throwable) = {
    if (ExceptionHandler.allowGenerateStackTrace)
      ExceptionHandler.generateStackTrace(Thread.currentThread, t)
    Logging.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, name, msg, Some(t)))
  }
  def errorWhere(msg: String)(stackLine: Int = -1) =
    logWhere(msg, error, error)(stackLine)

  private def logWhere(msg: String, f1: (String, Throwable) => Unit, f2: (String => Unit))(stackLine: Int) {
    val t = new Throwable(msg)
    t.fillInStackTrace()
    if (stackLine == -1)
      f1(msg, t)
    else
      f2(msg + " at " + t.getStackTrace.take(stackLine + 1).last)
  }

}
