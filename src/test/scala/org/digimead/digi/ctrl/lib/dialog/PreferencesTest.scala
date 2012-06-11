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

package org.digimead.digi.ctrl.lib.dialog

import org.digimead.digi.ctrl.lib.DigiLibTestDispatcher.dispatcher
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.PublicPreferences
import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit

import com.jayway.android.robotium.solo.Solo

import android.preference.PreferenceManager
import android.test.ActivityInstrumentationTestCase2

class PreferencesTest
  extends ActivityInstrumentationTestCase2[PreferencesTestActivity]("org.digimead.digi.ctrl.lib.dialog", classOf[PreferencesTestActivity])
  with JUnitSuite with ShouldMatchersForJUnit {
  @volatile private var solo: Solo = null
  @volatile private var activity: PreferencesTestActivity = null

  override def setUp() {
    super.setUp()
    Logging.reset(true)
    if (activity == null) {
      PublicPreferences.reset(getInstrumentation.getContext)
      val editor = PreferenceManager.getDefaultSharedPreferences(getInstrumentation.getContext).edit
      editor.clear
      editor.commit
    }
    activity = getActivity
    solo = new Solo(getInstrumentation(), activity)
    activity.log.info("setUp")
    Logging.addLogger(AndroidLogger)
  }
  override def tearDown() = {
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
  def testDialogRate() {
    activity.log.warn("TEST testDialogRate BEGIN")

    implicit val logger = activity.log

    PublicPreferences(activity).contains(DOption.ShowDialogRate.tag) should be(true)

    PublicPreferences(activity).getBoolean(DOption.ShowDialogRate.tag, Preferences.ShowDialogRate.defaultShow) should equal(true)

    PublicPreferences(activity).getInt(DOption.CounterDialogRate.tag, Preferences.ShowDialogRate.defaultCounter) should equal(0)

    Preferences.ShowDialogRate.get(activity) should equal(0)

    Preferences.ShowDialogRate.incAndGet(activity) should equal(1)

    Preferences.ShowDialogRate.get(activity) should equal(1)

    Preferences.ShowDialogRate.set(false, activity, false)

    Preferences.ShowDialogRate.incAndGet(activity) should equal(-1)

    Preferences.ShowDialogRate.get(activity) should equal(-1)

    Preferences.ShowDialogRate.set(true, activity, false)

    Preferences.ShowDialogRate.incAndGet(activity) should equal(2)

    Preferences.ShowDialogRate.get(activity) should equal(2)

    solo.searchText(Android.getString(activity, "preference_show_dialog_rate").get) should be(true)

    activity.log.warn("click begin")
    solo.clickOnText(Android.getString(activity, "preference_show_dialog_rate").get)
    activity.log.warn("click end")

    Thread.sleep(10000)

    Preferences.ShowDialogRate.get(activity) should equal(-1)

    activity.log.warn("TEST testDialogRate END")
  }
}