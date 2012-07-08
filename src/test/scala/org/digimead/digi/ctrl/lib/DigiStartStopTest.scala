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

package org.digimead.digi.ctrl.lib

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import org.digimead.digi.ctrl.lib.base.AppCache
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.dialog.Preferences
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.LoggingEvent
import org.digimead.digi.ctrl.lib.util.PublicPreferences
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit

import com.jayway.android.robotium.solo.Solo

import android.content.Intent
import android.test.ActivityInstrumentationTestCase2

package base {
  package object Test {
    def CacheInner = AppCache.inner
    def AppComponentShutdownOff = AppComponent.shutdown = false
  }
}

class DigiStartStopTest
  extends ActivityInstrumentationTestCase2[DigiLibTestActivity](classOf[DigiLibTestActivity])
  with JUnitSuite with ShouldMatchersForJUnit with Logging {
  implicit val dispatcher = org.digimead.digi.ctrl.lib.DigiLibTestDispatcher.dispatcher
  @volatile private var solo: Solo = null
  @volatile private var activity: DigiLibTestActivity = null
  @volatile var startupTime = System.currentTimeMillis
  val logResult = new SyncVar[Logging.Record]()
  val logSubscriber = new LogSubscriber

  def testFullStartStop() {
    log.warn("testFullStartStop BEGIN")

    logSubscriber.lockAfterMatch.set(true)
    Logging.suspend

    for (i <- 1 to 1) {
      log.warn("iteration N" + i)
      startupTime = System.currentTimeMillis
      val intent = new Intent(getInstrumentation.getContext, classOf[DigiLibTestActivity])
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      activity = getInstrumentation.startActivitySync(intent).asInstanceOf[DigiLibTestActivity]

      activity should not be (null)

      solo = new Solo(getInstrumentation(), activity)
      startupTime = System.currentTimeMillis - startupTime

      log.warn("current startup time is " + startupTime + "ms")

      startupTime.toInt should be < (5000)

      AppComponent.LazyInit.init()

      AppComponent.Inner should not be (null)
      AppControl.Inner should not be (null)
      base.Test.CacheInner should not be (null)
      AppComponent.Context should not be (None)

      solo.goBack

      assertLog("Activity::onPauseExt", _ == _, 60000)

      val rec = assertLog("retrieve idle shutdown timeout value", _ startsWith _, 60000)
      val sec = rec.message.split(" ")(5).substring(1).toInt + 10

      log.warn("waiting for shutdown " + sec + "s")

      assertLog("AppControl hold last context. Clear.", _ == _, sec * 1000)

      assertLog("shutdown (false)", _ == _, 60000)

      AppComponent.Inner should be(null)
      AppControl.Inner should be(null)
      base.Test.CacheInner should be(null)
      AppComponent.Context should be(None)

      Thread.sleep(1000)

    }

    Logging.resume

    log.warn("testFullStartStop END")
  }

  def testPartialStartStop() {
    log.warn("testPartialStartStop BEGIN")

    logSubscriber.lockAfterMatch.set(true)
    Logging.suspend

    for (i <- 1 to 10) {
      log.warn("iteration N" + i)
      startupTime = System.currentTimeMillis
      val intent = new Intent(getInstrumentation.getContext, classOf[DigiLibTestActivity])
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      activity = getInstrumentation.startActivitySync(intent).asInstanceOf[DigiLibTestActivity]

      activity should not be (null)

      solo = new Solo(getInstrumentation(), activity)
      startupTime = System.currentTimeMillis - startupTime

      log.warn("current startup time is " + startupTime + "ms")

      startupTime.toInt should be < (5000)

      AppComponent.LazyInit.init()

      AppComponent.Inner should not be (null)
      AppControl.Inner should not be (null)
      base.Test.CacheInner should not be (null)
      AppComponent.Context should not be (None)

      solo.goBack

      assertLog("Activity::onPauseExt", _ == _, 60000)

      val rec = assertLog("retrieve idle shutdown timeout value", _ startsWith _, 60000)
      val sec = rec.message.split(" ")(5).substring(1).toInt / 2

      log.warn("waiting for shutdown " + sec + "s")

      Thread.sleep(sec * 1000)

      AppComponent.Inner should not be (null)
      AppControl.Inner should not be (null)
      base.Test.CacheInner should not be (null)
      AppComponent.Context should be(None)

    }

    Logging.resume

    log.warn("testPartialStartStop END")
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
    PublicPreferences.reset(getInstrumentation.getContext)
    Preferences.ShutdownTimeout.set("5", getInstrumentation.getContext)
    Logging.init(getInstrumentation.getContext)
    Logging.reset()
    Logging.subscribe(logSubscriber)
    Logging.resume()
    Logging.addLogger(AndroidLogger)
    log.info("setUp")
    logResult.unset()
    base.Test.AppComponentShutdownOff
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
    Thread.sleep(10000)
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