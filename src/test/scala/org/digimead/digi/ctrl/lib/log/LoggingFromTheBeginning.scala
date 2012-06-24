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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import org.digimead.digi.ctrl.lib.DigiLibTestActivity
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit

import com.jayway.android.robotium.solo.Solo

import android.test.ActivityInstrumentationTestCase2

class LoggingFromTheBeginning
  extends ActivityInstrumentationTestCase2[DigiLibTestActivity]("org.digimead.digi.ctrl.lib", classOf[DigiLibTestActivity])
  with JUnitSuite with ShouldMatchersForJUnit with Logging {
  @volatile private var solo: Solo = null
  @volatile private var activity: DigiLibTestActivity = null
  val logResult = new SyncVar[Logging.Record]()
  val logSubscriber = new LogSubscriber

  def testLogging() {
    log.warn("testLogging BEGIN")

    Logging.subscribe(logSubscriber)
    activity = getActivity
    solo = new Solo(getInstrumentation(), activity)
    Logging.addLogger(AndroidLogger)

    assertLog("Activity::onCreateExt", _ == _, 60000)

    Thread.sleep(1000)

    log.warn("testReport END")
  }

  def assertLog(s: String, f: (String, String) => Boolean, timeout: Long): Logging.Record = synchronized {
    logSubscriber.want.set(s, f)
    Logging.resume
    logResult.unset()
    val result = logResult.get(timeout)
    assert(result != None, "log record \"" + s + "\" not found")
    result.get
  }
  override def setUp() {
    super.setUp()
    log.info("setUp")
    logResult.unset()
  }
  override def tearDown() = {
    Logging.resume()
    logResult.unset()
    log.info("tearDown")
    Logging.removeSubscriptions
    try {
      if (activity != null) {
        activity.finish()
        activity = null
      }
      if (solo != null) {
        solo.finalize()
        solo = null
      }
    } catch {
      case e =>
        e.printStackTrace()
    }
    super.tearDown()
    Thread.sleep(1000)
  }
  class LogSubscriber extends Logging.Sub {
    val lockAfterMatch = new AtomicBoolean(false)
    val want = new AtomicReference[(String, (String, String) => Boolean)](null)
    def notify(pub: Logging.type#Pub, event: LoggingEvent) = {
      event match {
        case event: Logging.Record =>
          want.get match {
            case null =>
            case (message, f) if event.message == null =>
            case (message, f) if f != null && message != null && event.message != null =>
              if (f(event.message.trim, message.trim)) {
                logSubscriber.want.set(null)
                logResult.put(event, 60000)
                if (lockAfterMatch.get)
                  if (logSubscriber.want.get == null)
                    logResult.waitUnset(60000)
              }
          }
        case _ =>
      }
    }
  }
}