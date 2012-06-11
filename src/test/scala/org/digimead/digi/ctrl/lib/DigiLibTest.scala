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

import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.Logging

import com.jayway.android.robotium.solo.Solo

import android.test.ActivityInstrumentationTestCase2

class DigiLibTest
  extends ActivityInstrumentationTestCase2[DigiLibTestActivity]("org.digimead.digi.ctrl.lib", classOf[DigiLibTestActivity]) {
  @volatile private var solo: Solo = null
  @volatile private var activity: DigiLibTestActivity = null
  def testHelloWorld() {
    android.util.Log.i("DigiLibTest", "TEST testHelloWorld BEGIN")
    activity.log.warn("TEST testHelloWorld BEGIN")
    activity.log.warn("TEST testHelloWorld END")
    android.util.Log.i("DigiLibTest", "TEST testHelloWorld END")
  }
  override def setUp() {
    super.setUp()
    Logging.reset(true)
    activity = getActivity
    solo = new Solo(getInstrumentation(), activity)
    activity.log.info("setUp")
    Logging.addLogger(AndroidLogger)
    android.util.Log.i("DigiLibTest", "setUp")
  }
  def testLogin() {
    android.util.Log.i("DigiLibTest", "TEST testLogin BEGIN")
    activity.log.warn("TEST testLogin BEGIN")
    activity.log.warn("TEST testLogin END")
    android.util.Log.i("DigiLibTest", "TEST testLogin END")
  }
  override def tearDown() = {
    android.util.Log.i("DigiLibTest", "tearDown")
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
}
