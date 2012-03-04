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

package org.digimead.digi.ctrl.lib.aop

import scala.annotation.implicitNotFound
import org.slf4j.Logger
import java.util.Date

@implicitNotFound(msg = "please define implicit RichLogger")
class RichLogger(val logger: Logger) {
  private val pid = android.os.Process.myPid
  // fast look while development, highlight it in your IDE
  def g_a_s_e(msg: String) {
    fatal("GASE: " + msg)
  }
  // error with stack trace
  def fatal(msg: String) {
    val t = new Throwable("Intospecting stack frame")
    t.fillInStackTrace()
    logger.error(msg + "\n" + t.getStackTraceString)
  }
  // trace
  def trace(msg: String) = {
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'V', logger.getName, msg))
    logger.trace(msg)
  }
  def trace(format: String, arg: Object) = {
    val msg = format.format(arg)
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'V', logger.getName, msg))
    logger.trace(msg)
  }
  def trace(format: String, arg1: Object, arg2: Object) = {
    val msg = format.format(arg1, arg2)
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'V', logger.getName, msg))
    logger.trace(msg)
  }
  def trace(format: String, argArray: Array[AnyRef]) = {
    val msg = format.format(argArray: _*)
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'V', logger.getName, msg))
    logger.trace(msg)
  }
  def trace(msg: String, t: Throwable) = {
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'V', logger.getName, msg + "\n" + t.getStackTraceString))
    logger.trace(msg, t)
  }
  // debug
  def debug(msg: String) = {
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'D', logger.getName, msg))
    logger.debug(msg)
  }
  def debug(format: String, arg: Object) = {
    val msg = format.format(arg)
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'D', logger.getName, msg))
    logger.debug(msg)
  }
  def debug(format: String, arg1: Object, arg2: Object) = {
    val msg = format.format(arg1, arg2)
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'D', logger.getName, msg))
    logger.debug(msg)
  }
  def debug(format: String, argArray: Array[AnyRef]) = {
    val msg = format.format(argArray: _*)
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'D', logger.getName, msg))
    logger.debug(msg)
  }
  def debug(msg: String, t: Throwable) = {
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'D', logger.getName, msg + "\n" + t.getStackTraceString))
    logger.debug(msg, t)
  }
  // info
  def info(msg: String) = {
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'I', logger.getName, msg))
    logger.info(msg)
  }
  def info(format: String, arg: Object) = {
    val msg = format.format(arg)
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'I', logger.getName, msg))
    logger.info(msg)
  }
  def info(format: String, arg1: Object, arg2: Object) = {
    val msg = format.format(arg1, arg2)
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'I', logger.getName, msg))
    logger.info(msg)
  }
  def info(format: String, argArray: Array[AnyRef]) = {
    val msg = format.format(argArray: _*)
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'I', logger.getName, msg))
    logger.info(msg)
  }
  def info(msg: String, t: Throwable) = {
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'I', logger.getName, msg + "\n" + t.getStackTraceString))
    logger.info(msg, t)
  }
  // error
  def error(msg: String) = {
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'E', logger.getName, msg))
    logger.error(msg)
  }
  def error(format: String, arg: Object) = {
    val msg = format.format(arg)
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'E', logger.getName, msg))
    logger.error(msg)
  }
  def error(format: String, arg1: Object, arg2: Object) = {
    val msg = format.format(arg1, arg2)
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'E', logger.getName, msg))
    logger.error(msg)
  }
  def error(format: String, argArray: Array[AnyRef]) = {
    val msg = format.format(argArray: _*)
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'E', logger.getName, msg))
    logger.error(msg)
  }
  def error(msg: String, t: Throwable) = {
    Logging.Report.queue.offer(new Logging.Report.Record(new Date(), pid, Thread.currentThread.getId, 'E', logger.getName, msg + "\n" + t.getStackTraceString))
    logger.error(msg, t)
  }
}

object RichLogger {
  implicit def rich2plain(rich: RichLogger): Logger = rich.logger
}