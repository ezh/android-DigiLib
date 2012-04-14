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

package org.digimead.digi.ctrl.lib.util

import java.util.concurrent.atomic.AtomicReference

import org.digimead.digi.ctrl.lib.log.Logging

/*
 * variant of scala.concurrent.SyncVar
 * remove few possible deadlocks from isSet, get, get(timeout)
 * set weaker access permissions protected vs private
 * faster ??? return values immediately if any (benchmark needed)
 *  TODO benchmark
 */

class SyncVar[A] extends Logging {
  protected val value = new AtomicReference[(Boolean, A)](false, null.asInstanceOf[A])

  def get(): A = value.get match {
    case (true, result) => result
    case (false, _) =>
      while (value.get match {
        case (true, result) => return result
        case (false, _) => true
      }) value.synchronized {
        log.traceWhere(this + " get() waiting", Logging.Where.BEFORE)
        value.wait
        log.traceWhere(this + " get() running", Logging.Where.BEFORE)
      }
      // unreachable point
      value.get._2
  }

  /**
   * Waits `timeout` millis. If `timeout <= 0` just returns 0. If the system clock
   *  went backward, it will return 0, so it never returns negative results.
   */
  protected def waitMeasuringElapsed(timeout: Long): Long = if (timeout <= 0) 0 else {
    val start = System.currentTimeMillis
    value.synchronized {
      log.traceWhere(this + " get(" + timeout + ") waiting", -4)
      value.wait(timeout)
    }
    val elapsed = System.currentTimeMillis - start
    val result = if (elapsed < 0) 0 else elapsed
    log.traceWhere(this + " get(" + timeout + ") running, reserve " + (timeout - result), -4)
    result
  }

  /**
   * Waits for this SyncVar to become defined at least for
   *  `timeout` milliseconds (possibly more), and gets its
   *  value.
   *
   *  @param timeout     the amount of milliseconds to wait, 0 means forever
   *  @return            `None` if variable is undefined after `timeout`, `Some(value)` otherwise
   */
  def get(timeout: Long): Option[A] = value.get match {
    case (true, result) => Some(result)
    case (false, _) =>
      var rest = timeout
      while ((value.get match {
        case (true, result) => return Some(result)
        case (false, _) => true
      }) && rest > 0) {
        /**
         * Defending against the system clock going backward
         *  by counting time elapsed directly.  Loop required
         *  to deal with spurious wakeups.
         */
        val elapsed = waitMeasuringElapsed(rest)
        rest -= elapsed
      }
      value.get match {
        case (true, result) => Some(result)
        case (false, _) => None
      }
  }

  def take(): A = value.getAndSet(false, null.asInstanceOf[A]) match {
    case (true, result) => result
    case (false, _) =>
      while (value.getAndSet(false, null.asInstanceOf[A]) match {
        case (true, result) => return result
        case (false, _) => true
      }) value.synchronized {
        log.traceWhere(this + " take() waiting", Logging.Where.BEFORE)
        value.wait
        log.traceWhere(this + " take() running", Logging.Where.BEFORE)
      }
      // unreachable point
      value.get._2
  }

  def set(x: A, signalAll: Boolean = true): Unit = {
    value.set(true, x)
    value.synchronized {
      if (signalAll)
        value.notifyAll
      else
        value.notify
    }
  }

  def put(x: A, signalAll: Boolean = true): Unit = {
    while (!value.compareAndSet((false, null.asInstanceOf[A]), (true, x)))
      value.synchronized {
        log.traceWhere(this + " put(...) waiting", Logging.Where.BEFORE)
        value.wait
        log.traceWhere(this + " put(...) running", Logging.Where.BEFORE)
      }
    value.synchronized {
      if (signalAll)
        value.notifyAll
      else
        value.notify
    }
  }

  def isSet: Boolean = value.get._1

  def unset(signalAll: Boolean = true): Unit = {
    value.set(false, null.asInstanceOf[A])
    value.synchronized {
      if (signalAll)
        value.notifyAll
      else
        value.notify
    }
  }
}
