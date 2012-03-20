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
import org.digimead.digi.ctrl.lib.base.Report
import org.digimead.digi.ctrl.lib.util.ExceptionHandler
import scala.annotation.implicitNotFound
import org.slf4j.helpers.MarkerIgnoringBase

@implicitNotFound(msg = "please define implicit RichLogger")
class RichLogger(val loggerName: String) extends MarkerIgnoringBase {
  // fast look while development, highlight it in your IDE
  def g_a_s_e(msg: String) {
    fatal("GASE: " + msg)
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
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, loggerName, msg))

  def trace(format: String, arg: Object) {
    val msg = format.format(arg)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, loggerName, msg))
  }
  def trace(format: String, arg1: Object, arg2: Object) {
    val msg = format.format(arg1, arg2)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, loggerName, msg))
  }
  def trace(format: String, argArray: Array[AnyRef]) {
    val msg = format.format(argArray: _*)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, loggerName, msg))
  }
  def trace(msg: String, t: Throwable) =
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Trace, loggerName, msg, Some(t)))

  // debug
  /* @see org.slf4j.Logger#isDebugEnabled() */
  def isDebugEnabled(): Boolean = true
  def debug(msg: String) =
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, loggerName, msg))
  def debug(format: String, arg: Object) {
    val msg = format.format(arg)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, loggerName, msg))
  }
  def debug(format: String, arg1: Object, arg2: Object) {
    val msg = format.format(arg1, arg2)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, loggerName, msg))
  }
  def debug(format: String, argArray: Array[AnyRef]) {
    val msg = format.format(argArray: _*)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, loggerName, msg))
  }
  def debug(msg: String, t: Throwable) =
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Debug, loggerName, msg, Some(t)))
  def debugWhere(msg: String)(stackLine: Int = -1) {
    val t = new Throwable(msg)
    t.fillInStackTrace()
    if (stackLine == -1)
      debug(msg, t)
    else
      debug(msg + " at " + t.getStackTrace.take(stackLine + 1).last)
  }

  // info
  /* @see org.slf4j.Logger#isInfoEnabled() */
  def isInfoEnabled: Boolean = true
  def info(msg: String) {
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, loggerName, msg))
  }
  def info(format: String, arg: Object) {
    val msg = format.format(arg)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, loggerName, msg))
  }
  def info(format: String, arg1: Object, arg2: Object) {
    val msg = format.format(arg1, arg2)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, loggerName, msg))
  }
  def info(format: String, argArray: Array[AnyRef]) {
    val msg = format.format(argArray: _*)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, loggerName, msg))
  }
  def info(msg: String, t: Throwable) =
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Info, loggerName, msg, Some(t)))

  // warn
  /* @see org.slf4j.Logger#isWarnEnabled() */
  def isWarnEnabled: Boolean = true
  def warn(msg: String) {
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, loggerName, msg))
  }
  def warn(format: String, arg: Object) {
    val msg = format.format(arg)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, loggerName, msg))
  }
  def warn(format: String, arg1: Object, arg2: Object) {
    val msg = format.format(arg1, arg2)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, loggerName, msg))
  }
  def warn(format: String, argArray: Array[AnyRef]) {
    val msg = format.format(argArray: _*)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, loggerName, msg))
  }
  def warn(msg: String, t: Throwable) =
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Warn, loggerName, msg, Some(t)))

  // error
  /* @see org.slf4j.Logger#isErrorEnabled() */
  def isErrorEnabled: Boolean = true
  def error(msg: String) =
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, loggerName, msg))
  def error(format: String, arg: Object) {
    val msg = format.format(arg)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, loggerName, msg))
  }
  def error(format: String, arg1: Object, arg2: Object) {
    val msg = format.format(arg1, arg2)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, loggerName, msg))
  }
  def error(format: String, argArray: Array[AnyRef]) {
    val msg = format.format(argArray: _*)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, loggerName, msg))
  }
  def error(msg: String, t: Throwable) = {
    if (ExceptionHandler.allowGenerateStackTrace)
      ExceptionHandler.generateStackTrace(Thread.currentThread, t)
    Logging.queue.offer(new Logging.Record(new Date(), Thread.currentThread.getId, Logging.Level.Error, loggerName, msg, Some(t)))
  }
}
