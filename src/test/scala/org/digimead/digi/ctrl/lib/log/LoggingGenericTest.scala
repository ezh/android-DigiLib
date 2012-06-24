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

import org.digimead.digi.ctrl.lib.DigiLibTestActivity
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit

import com.jayway.android.robotium.solo.Solo

import android.app.Application
import android.test.ActivityInstrumentationTestCase2

class LoggingGenericTest
  extends ActivityInstrumentationTestCase2[DigiLibTestActivity]("org.digimead.digi.ctrl.lib", classOf[DigiLibTestActivity])
  with JUnitSuite with ShouldMatchersForJUnit {
  @volatile private var solo: Solo = null
  @volatile private var activity: DigiLibTestActivity = null

  override def setUp() {
    super.setUp()
    Logging.reset()
    Logging.resume()
    activity = getActivity
    solo = new Solo(getInstrumentation(), activity)
    activity.log.info("setUp")
    Logging.addLogger(AndroidLogger)
  }
  override def tearDown() = {
    Logging.removeSubscriptions()
    Logging.resume()
    activity.log.info("tearDown")
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
  def testInitDeinit() {
    /*
     * deinit
     */
    Logging.deinit
    Logging.queue should have size (0)

    Logging.logger should not be (null)
    Logging.logger should have size (0)

    Logging.richLogger should not be (null)

    Logging.loggingThread should not be (null)
    Logging.loggingThread.isAlive should be(true)

    Logging.initializationContext should not be (null)
    Logging.initializationContext.get.map(_.getClass) should be === Some(classOf[android.app.Application])

    Logging.shutdownHook should be(null)

    /*
     * init
     */
    Logging.init(Logging.initializationContext.get.get)

    Logging.queue should have size (1)

    Logging.logger should not be (null)
    Logging.logger should have size (0)

    Logging.richLogger should not be (null)

    Logging.loggingThread should not be (null)
    Logging.loggingThread.isAlive should be(true)

    Logging.initializationContext should not be (null)
    Logging.initializationContext.get.map(_.getClass) should be === Some(classOf[android.app.Application])

    Logging.shutdownHook should not be (null)

    /*
     * add record
     */
    activity.log.info("message to void")

    Logging.queue should have size (2)

    Logging.flush should be(-1)

    Logging.queue should have size (2)
    
    Logging.reset

    /*
     * add record
     */
    activity.log.info("message to void")

    Logging.queue should have size (2)

    Logging.reset()

    Logging.queue should have size (1)
  }
  def testAddRemoveLogger() {
    Logging.suspend
    Logging.delLogger(AndroidLogger)
    Logging.reset
    Logging.addLogger(AndroidLogger)

    Logging.logger should have size (1)

    activity.log.info("message to AndroidLogger")

    Logging.queue should have size (3)

    Logging.flush()

    Logging.queue should have size (0)
  }
  def testPublishSubscribe() {
    Logging.removeSubscriptions
    val eventResult = new SyncVar[Boolean]()
    val subscriber = new Logging.Sub {
      def notify(pub: Logging.type#Pub, event: LoggingEvent) = event match {
        case event: Logging.Record =>
          if (event.message == "hello 123")
            eventResult.put(true, 0)
        case _ =>
          eventResult.put(false, 0)
      }
    }
    Logging.subscribe(subscriber)

    activity.log.info("hello 123")

    eventResult.get(0) should equal(None)

    Logging.flush()

    eventResult.get(0) should equal(Some(true))
  }
  def testFlush() {
    Logging.suspend()

    Logging.loggingThread.isAlive should be(false)

    activity.log.debug("1111")

    Logging.flush should be(1)

    for (i <- 0 until Logging.flushLimit + 1)
      activity.log.debug("" + i)

    Logging.flush should be(Logging.flushLimit + 1)

    Logging.queue should have size (0)

    activity.log.debug("1")

    activity.log.debug("2")

    activity.log.debug("3")

    Logging.queue should have size (3)

    Logging.flushQueue(1) should be(1)

    Logging.queue should have size (2)

    Logging.queue.poll.message should be("2")

    Logging.queue.poll.message should be("3")
  }
}
