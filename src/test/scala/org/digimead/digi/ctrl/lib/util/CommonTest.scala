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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import org.digimead.digi.ctrl.lib.DigiLibTestActivity
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.LoggingEvent
import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit

import com.jayway.android.robotium.solo.Solo

import android.test.ActivityInstrumentationTestCase2

class CommonTest
  extends ActivityInstrumentationTestCase2[DigiLibTestActivity]("org.digimead.digi.ctrl.lib", classOf[DigiLibTestActivity])
  with JUnitSuite with ShouldMatchersForJUnit {
  @volatile private var solo: Solo = null
  @volatile private var activity: DigiLibTestActivity = null
  val logResult = new SyncVar[Boolean]()
  val logSubscriber = new LogSubscriber

  def assertLog(s: String, f: (String, String) => Boolean, timeout: Long) = synchronized {
    logSubscriber.want.set(s, f)
    Logging.resume
    logResult.unset()
    assert(logResult.get(timeout) == Some(true), "log record \"" + s + "\" not found")
  }
  override def setUp() {
    super.setUp()
    Logging.reset()
    Logging.resume()
    activity = getActivity
    solo = new Solo(getInstrumentation(), activity)
    Logging.subscribe(logSubscriber)
    Logging.addLogger(AndroidLogger)
    activity.log.info("setUp")
    logResult.unset()
  }
  override def tearDown() = {
    Logging.resume()
    logResult.unset()
    activity.log.info("tearDown")
    Logging.removeSubscriptions
    try {
      solo.finalize()
    } catch {
      case e =>
        e.printStackTrace()
    }
    activity.finish()
    super.tearDown()
    Thread.sleep(1000)
  }
  def testGetDirectory() {
    activity.log.warn("testGetDirectory BEGIN")

    Common.externalStorageDisabled = None

    logSubscriber.lockAfterMatch.set(true)
    Logging.suspend
    Common.getDirectory(activity, "test", false, Some(true), Some(true), Some(true))
    assertLog("get working directory, mode 'force internal': false", _ == _, 10000)
    assertLog("try SD storage directory", _.startsWith(_), 10000)
    assertLog("test external storage", _ == _, 10000)
    logResult.unset()

    Common.externalStorageDisabled should be(Some(false))

    activity.log.warn("testGetDirectory END")
  }

  class LogSubscriber extends Logging.Sub {
    val lockAfterMatch = new AtomicBoolean(false)
    val want = new AtomicReference[(String, (String, String) => Boolean)](null)
    def notify(pub: Logging.type#Pub, event: LoggingEvent) = event match {
      case event: Logging.Record =>
        want.get match {
          case (message, f) =>
            if (f(event.message, message)) {
              logSubscriber.want.set(null)
              logResult.put(true, 60000)
              if (lockAfterMatch.get)
                if (logSubscriber.want.get == null)
                  logResult.waitUnset(60000)
            }
          case null =>
        }
      case _ =>
    }
  }
}