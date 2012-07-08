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

package org.digimead.digi.ctrl.lib.base

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import org.digimead.digi.ctrl.lib.DigiLibTestActivity
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.LoggingEvent
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit

import com.jayway.android.robotium.solo.Solo

import android.test.ActivityInstrumentationTestCase2

class ReportTestWithActivity
  extends ActivityInstrumentationTestCase2[DigiLibTestActivity]("org.digimead.digi.ctrl.lib", classOf[DigiLibTestActivity])
  with JUnitSuite with ShouldMatchersForJUnit with Logging {
  @volatile private var solo: Solo = null
  @volatile private var activity: DigiLibTestActivity = null
  @volatile var startupTime = System.currentTimeMillis
  val logResult = new SyncVar[Logging.Record]()
  val logSubscriber = new LogSubscriber

  def testReport() {
    log.warn("testReport BEGIN")

    Logging.suspend
    logSubscriber.lockAfterMatch.set(true)
    startupTime = System.currentTimeMillis
    activity = getActivity
    startupTime = (System.currentTimeMillis - startupTime) / 1000
    solo = new Solo(getInstrumentation(), activity)
    log.warn("current startup time is " + startupTime + "s")

    startupTime.toInt should be < (5)

    AppComponent.LazyInit.pool(0).size should be > (0)

    AppComponent.LazyInit.init

    var eventBegin = assertLog("begin LazyInit block \"move report to SD, clean outdated\"", _ startsWith _, 60000)
    var eventEnd = assertLog("end LazyInit block \"move report to SD, clean outdated\"", _ startsWith _, 60000)
    var eventDuration = eventEnd.date.getTime - eventBegin.date.getTime
    log.warn("TIME LazyInit " + (eventEnd.date.getTime - eventBegin.date.getTime) + "ms")

    eventDuration.toInt should be < (1000)

    //assertLog("new report cleaner thread", _ startsWith _, 60000)

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
    Logging.init(getInstrumentation.getContext)
    Logging.reset()
    Logging.subscribe(logSubscriber)
    Logging.resume()
    Logging.addLogger(AndroidLogger)
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
        case event: Logging.Event.Outgoing =>
          want.get match {
            case null =>
            case (message, f) if event.record.message == null =>
            case (message, f) if f != null && message != null && event.record.message != null =>
              if (f(event.record.message.trim, message.trim)) {
                logSubscriber.want.set(null)
                logResult.put(event.record, 60000)
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