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

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.util.Android
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.ListPreference
import android.preference.{ Preference => APreference }
import android.preference.PreferenceActivity
import android.preference.PreferenceCategory
import android.preference.PreferenceManager
import android.widget.Toast
import org.digimead.digi.ctrl.lib.base.AppComponent
import android.content.pm.ActivityInfo

abstract class Preference(implicit dispatcher: Dispatcher) extends PreferenceActivity with Logging with OnSharedPreferenceChangeListener {
  implicit protected val logger: RichLogger
  @Loggable
  override def onCreate(savedInstanceState: Bundle) {
    log.trace("Preference::onCreate")
    super.onCreate(savedInstanceState)
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_BEHIND)
    addPreferencesFromResource(Android.getId(this, "options", "xml"))
    for (i <- 0 until getPreferenceScreen.getPreferenceCount())
      initSummary(getPreferenceScreen.getPreference(i))
  }
  @Loggable
  override def onResume() {
    log.trace("Preference::onResume")
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_BEHIND)
    super.onResume()
    // Set up a listener whenever a key changes
    getPreferenceScreen.getSharedPreferences.registerOnSharedPreferenceChangeListener(this)
  }
  @Loggable
  override def onPause() {
    log.trace("Preference::onPause")
    super.onPause()
    // Unregister the listener whenever a key changes 
    getPreferenceScreen.getSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
  }
  def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
    log.trace("Preference::onSharedPreferenceChanged for " + key)
    updatePrefSummary(findPreference(key), key, true)
  }
  protected def initSummary(p: APreference): Unit = {
    log.trace("Preference::initSummary for " + p.getKey)
    p match {
      case p: PreferenceCategory =>
        for (i <- 0 until p.getPreferenceCount)
          initSummary(p.getPreference(i))
      case p =>
        updatePrefSummary(p, p.getKey)
    }
  }
  protected def updatePrefSummary(p: APreference, key: String, notify: Boolean = false) = {
    log.trace("Preference::updatePrefSummary for " + p.getKey)
    p match {
      case p: ListPreference if key == Preference.debugLevelsListKey =>
        Preference.setLogLevel(p.getValue.toString, this, notify)(logger, dispatcher)
      case p: CheckBoxPreference if key == Preference.debugAndroidCheckBoxKey =>
        Preference.setAndroidLogger(PreferenceManager.getDefaultSharedPreferences(this).getBoolean(key, false), this, notify)(logger, dispatcher)
      case p: ListPreference if key == Preference.layoutListKey =>
        Preference.setLayout(p.getValue.toString, this, notify)(logger, dispatcher)
    }
  }
}

object Preference extends Logging {
  val debugLevelsListKey = "debug_level"
  val debugAndroidCheckBoxKey = "debug_android"
  val layoutListKey = "layout"
  @volatile private var lastLogLevelLevel = ""
  @volatile private var lastAndroidLogging = false
  @Loggable
  def setLogLevel(l: String, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = {
    if (lastLogLevelLevel == l) {
      log.info("current log level already set to " + l)
      return
    } else {
      lastLogLevelLevel = l
    }
    l match {
      case "0" =>
        Logging.setErrorEnabled(false)
        Logging.setWarnEnabled(false)
        Logging.setInfoEnabled(false)
        Logging.setDebugEnabled(false)
        Logging.setTraceEnabled(false)
        val message = Android.getString(context, "set_loglevel_none").getOrElse("Set log level to NONE")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "1" =>
        Logging.setErrorEnabled(true)
        Logging.setWarnEnabled(false)
        Logging.setInfoEnabled(false)
        Logging.setDebugEnabled(false)
        Logging.setTraceEnabled(false)
        val message = Android.getString(context, "set_loglevel_error").getOrElse("Set log level to ERROR")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "2" =>
        Logging.setErrorEnabled(true)
        Logging.setWarnEnabled(true)
        Logging.setInfoEnabled(false)
        Logging.setDebugEnabled(false)
        Logging.setTraceEnabled(false)
        val message = Android.getString(context, "set_loglevel_warn").getOrElse("Set log level to WARN")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "3" =>
        Logging.setErrorEnabled(true)
        Logging.setWarnEnabled(true)
        Logging.setInfoEnabled(true)
        Logging.setDebugEnabled(false)
        Logging.setTraceEnabled(false)
        val message = Android.getString(context, "set_loglevel_info").getOrElse("Set log level to INFO")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "4" =>
        Logging.setErrorEnabled(true)
        Logging.setWarnEnabled(true)
        Logging.setInfoEnabled(true)
        Logging.setDebugEnabled(true)
        Logging.setTraceEnabled(false)
        val message = Android.getString(context, "set_loglevel_debug").getOrElse("Set log level to DEBUG")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "5" =>
        Logging.setErrorEnabled(true)
        Logging.setWarnEnabled(true)
        Logging.setInfoEnabled(true)
        Logging.setDebugEnabled(true)
        Logging.setTraceEnabled(true)
        val message = Android.getString(context, "set_loglevel_trace").getOrElse("Set log level to TRACE")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case n =>
        log.error("unknown value " + n + " for preference " + debugLevelsListKey)
    }
  }
  @Loggable
  def setAndroidLogger(f: Boolean, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher) {
    if (lastAndroidLogging == f) {
      log.info("current android logging already set to " + f)
      return
    } else {
      lastAndroidLogging = f
    }
    val message = if (f) {
      Logging.addLogger(AndroidLogger)
      Android.getString(context, "debug_android_on_notify").getOrElse("Android logging facility enabled")
    } else {
      Logging.delLogger(AndroidLogger)
      Android.getString(context, "debug_android_on_notify").getOrElse("Android logging facility disabled")
    }
    if (notify)
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    IAmMumble(message)(logger, dispatcher)
  }
  @Loggable
  def setLayout(l: String, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = {
    l match {
      case "0" =>
        AppComponent.Inner.preferredOrientation.set(0)
        val message = Android.getString(context, "set_layout_landscape").getOrElse("Set preferred orientation to landscape")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "1" =>
        AppComponent.Inner.preferredOrientation.set(1)
        val message = Android.getString(context, "set_layout_portrait").getOrElse("Set preferred orientation to portrait")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "8" =>
        AppComponent.Inner.preferredOrientation.set(8)
        val message = Android.getString(context, "set_layout_portrait").getOrElse("Set preferred orientation to reverse landscape")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "9" =>
        AppComponent.Inner.preferredOrientation.set(9)
        val message = Android.getString(context, "set_layout_portrait").getOrElse("Set preferred orientation to reverse portraint")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "4" =>
        AppComponent.Inner.preferredOrientation.set(4)
        val message = Android.getString(context, "set_layout_portrait").getOrElse("Set preferred orientation to dynamic")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
    }
  }
}