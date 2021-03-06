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
  implicit lazy val logger = activity.log

  def testShutdownTimeout() {
    activity.log.warn("testShutdownTimeout BEGIN")

    PublicPreferences(activity).contains(DOption.ShutdownTimeout.tag) should be(true)

    PublicPreferences(activity).getString(DOption.ShutdownTimeout.tag, Preferences.ShutdownTimeout.default).toInt should equal(300)

    Preferences.ShutdownTimeout.get(activity) should equal(300)

    Preferences.ShutdownTimeout.set("600", activity, false)

    Preferences.ShutdownTimeout.get(activity) should equal(600)

    Preferences.ShutdownTimeout.set("300", activity, false)

    Preferences.ShutdownTimeout.get(activity) should equal(300)

    solo.searchText(Android.getString(activity, "preference_shutdown_timeout").get) should be(true)

    activity.log.warn("click begin")
    solo.clickOnText(Android.getString(activity, "preference_shutdown_timeout").get)

    Thread.sleep(1000)

    solo.searchText("1 minute") should be(true)

    solo.clickOnText("1 minute")
    activity.log.warn("click end")

    Thread.sleep(1000)

    Preferences.ShutdownTimeout.get(activity) should equal(60)

    activity.log.warn("testShutdownTimeout END")
  }
  def testPreferredLayoutOrientation() {
    activity.log.warn("testPreferredLayoutOrientation BEGIN")

    PublicPreferences(activity).contains(DOption.PreferredLayoutOrientation.tag) should be(true)

    PublicPreferences(activity).getString(DOption.PreferredLayoutOrientation.tag, Preferences.PreferredLayoutOrientation.default).toInt should equal(4)

    Preferences.PreferredLayoutOrientation.get(activity) should equal(4)

    Preferences.PreferredLayoutOrientation.set("8", activity, false)

    Preferences.PreferredLayoutOrientation.get(activity) should equal(8)

    Preferences.PreferredLayoutOrientation.set("4", activity, false)

    Preferences.PreferredLayoutOrientation.get(activity) should equal(4)

    solo.searchText(Android.getString(activity, "preference_layout").get) should be(true)

    activity.log.warn("click begin")
    solo.clickOnText(Android.getString(activity, "preference_layout").get)

    Thread.sleep(1000)

    solo.searchText("Landscape") should be(true)

    solo.clickOnText("Landscape")
    activity.log.warn("click end")

    Thread.sleep(1000)

    Preferences.PreferredLayoutOrientation.get(activity) should equal(0)

    activity.log.warn("testPreferredLayoutOrientation END")
  }
  def testDebugAndroidLogger() {
    activity.log.warn("testDebugAndroidLogger BEGIN")

    PublicPreferences(activity).contains(DOption.DebugAndroidLogger.tag) should be(true)

    PublicPreferences(activity).getBoolean(DOption.DebugAndroidLogger.tag, Preferences.DebugAndroidLogger.default) should equal(false)

    Preferences.DebugAndroidLogger.get(activity) should equal(false)

    Preferences.DebugAndroidLogger.set(true, activity, false)

    Preferences.DebugAndroidLogger.get(activity) should equal(true)

    Preferences.DebugAndroidLogger.set(false, activity, false)

    Preferences.DebugAndroidLogger.get(activity) should equal(false)

    solo.searchText(Android.getString(activity, "preference_debug_android").get) should be(true)

    activity.log.warn("click begin")
    solo.clickOnText(Android.getString(activity, "preference_debug_android").get)
    activity.log.warn("click end")

    Thread.sleep(1000)

    Preferences.DebugAndroidLogger.get(activity) should equal(true)

    activity.log.warn("testDebugAndroidLogger END")
  }
  def testDebugLogLevel() {
    activity.log.warn("testDebugLogLevel BEGIN")

    PublicPreferences(activity).contains(DOption.DebugLogLevel.tag) should be(true)

    PublicPreferences(activity).getString(DOption.DebugLogLevel.tag, Preferences.DebugLogLevel.default).toInt should equal(5)

    Preferences.DebugLogLevel.get(activity) should equal(5)

    Preferences.DebugLogLevel.set("4", activity, false)

    Preferences.DebugLogLevel.get(activity) should equal(4)

    Preferences.DebugLogLevel.set("5", activity, false)

    Preferences.DebugLogLevel.get(activity) should equal(5)

    solo.searchText(Android.getString(activity, "preference_debug_level").get) should be(true)

    activity.log.warn("click begin")
    solo.clickOnText(Android.getString(activity, "preference_debug_level").get)

    Thread.sleep(1000)

    solo.searchText("The WARN level designates potentially harmful situations") should be(true)

    solo.clickOnText("The WARN level designates potentially harmful situations")
    activity.log.warn("click end")

    Thread.sleep(1000)

    Preferences.DebugLogLevel.get(activity) should equal(2)

    activity.log.warn("testDebugLogLevel END")
  }
  override def setUp() {
    super.setUp()
    Logging.reset()
    Logging.resume()
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
}
