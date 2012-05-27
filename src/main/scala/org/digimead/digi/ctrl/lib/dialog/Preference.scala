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
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DOption.OptVal.value2string_id
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.ListPreference
import android.preference.{ Preference => APreference }
import android.preference.PreferenceActivity
import android.preference.PreferenceCategory
import android.preference.PreferenceManager
import android.widget.Toast

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
      case p: ListPreference if key == Preference.debugLevelListKey =>
        Preference.setLogLevel(p.getValue.toString, this, notify)(logger, dispatcher)
      case p: CheckBoxPreference if key == Preference.debugAndroidLoggingCheckBoxKey =>
        Preference.setAndroidLogger(Preference.getAndroidLogger(this), this, notify)(logger, dispatcher)
      case p: ListPreference if key == Preference.layoutListKey =>
        Preference.setPrefferedLayoutOrientation(p.getValue.toString, this, notify)(logger, dispatcher)
      case p: CheckBoxPreference if (key == Preference.showDialogWelcomeKey) =>
        Preference.setShowDialogWelcome(Preference.getShowDialogWelcome(this), this, notify)(logger, dispatcher)
      case p: CheckBoxPreference if (key == Preference.showDialogRateKey) =>
        Preference.setShowDialogRate(Preference.getShowDialogRate(this) > -1, this, notify)(logger, dispatcher)
      case p: ListPreference if key == Preference.shutdownTimeoutKey =>
        Preference.setShutdownTimeout(p.getValue.toString, this, notify)(logger, dispatcher)
    }
  }
}

object Preference extends Logging {
  // log level
  val debugLevelListKey = "debug_level"
  val defaultLogLevelLevel = 5 // see res/xml/options.xml/ListPreference/android:defaultValue
  @volatile private var lastLogLevelLevel = ""
  // android logging
  val debugAndroidLoggingCheckBoxKey = "debug_android"
  val defaultAndroidLogging = false // see res/xml/options.xml/CheckBoxPreference/android:defaultValue
  @volatile private var lastAndroidLogging = false
  // orientation layout
  val layoutListKey = "layout"
  val defaultLayout = 4 // see res/xml/options.xml/ListPreference/android:defaultValue SCREEN_ORIENTATION_SENSOR    
  // show dialog welcome
  val showDialogWelcomeKey = "show_dialog_welcome"
  val defaultShowDialogWelcome = true // see res/xml/options.xml/CheckBoxPreference/android:defaultValue
  // show rate dialog
  val showDialogRateKey = "show_dialog_rate"
  val defaultShowDialogRate = true // see res/xml/options.xml/CheckBoxPreference/android:defaultValue
  // shutdown timeout
  val shutdownTimeoutKey = "shutdown_timeout"
  val defaultShutdownTimeout = 300 // seconds, see res/xml/options.xml/ListPreference/android:defaultValue
  @Loggable
  def initPersistentOptions(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher) {
    setShowDialogWelcome(context)(logger, dispatcher)
    setShowDialogRate(context)(logger, dispatcher)
    setShutdownTimeout(context)(logger, dispatcher)
  }
  @Loggable
  def getLogLevel(context: Context): Int = try {
    PreferenceManager.getDefaultSharedPreferences(context).
      getString(Preference.debugLevelListKey, defaultLogLevelLevel.toString).toInt
  } catch {
    case e =>
      log.error(e.getMessage, e)
      defaultLogLevelLevel
  }
  @Loggable
  def setLogLevel(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit =
    setLogLevel(getLogLevel(context).toString, context)(logger, dispatcher)
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
        log.error("unknown value " + n + " for preference " + debugLevelListKey)
    }
  }
  @Loggable
  def getAndroidLogger(context: Context): Boolean = try {
    PreferenceManager.getDefaultSharedPreferences(context).
      getBoolean(Preference.debugAndroidLoggingCheckBoxKey, defaultAndroidLogging)
  } catch {
    case e =>
      log.error(e.getMessage, e)
      defaultAndroidLogging
  }
  @Loggable
  def setAndroidLogger(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit =
    setAndroidLogger(getAndroidLogger(context), context)(logger, dispatcher)
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
      Android.getString(context, "debug_android_off_notify").getOrElse("Android logging facility disabled")
    }
    if (notify)
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    IAmMumble(message)(logger, dispatcher)
  }
  @Loggable
  def getPrefferedLayoutOrientation(context: Context): Int = try {
    PreferenceManager.getDefaultSharedPreferences(context).
      getString(Preference.layoutListKey, defaultLayout.toString).toInt
  } catch {
    case e =>
      log.error(e.getMessage, e)
      defaultLayout
  }
  @Loggable
  def setPrefferedLayoutOrientation(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit =
    setPrefferedLayoutOrientation(getPrefferedLayoutOrientation(context).toString, context)(logger, dispatcher)
  @Loggable
  def setPrefferedLayoutOrientation(l: String, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = {
    l match {
      case "0" => // SCREEN_ORIENTATION_LANDSCAPE
        AppComponent.Inner.preferredOrientation.set(0)
        val message = Android.getString(context, "set_layout_landscape").getOrElse("Set preferred orientation to landscape")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "1" => // SCREEN_ORIENTATION_PORTRAIT
        AppComponent.Inner.preferredOrientation.set(1)
        val message = Android.getString(context, "set_layout_portrait").getOrElse("Set preferred orientation to portrait")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "8" => // SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        AppComponent.Inner.preferredOrientation.set(8)
        val message = Android.getString(context, "set_layout_portrait").getOrElse("Set preferred orientation to reverse landscape")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "9" => // SCREEN_ORIENTATION_REVERSE_PORTRAIT
        AppComponent.Inner.preferredOrientation.set(9)
        val message = Android.getString(context, "set_layout_portrait").getOrElse("Set preferred orientation to reverse portraint")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
      case "4" => // SCREEN_ORIENTATION_SENSOR
        AppComponent.Inner.preferredOrientation.set(4)
        val message = Android.getString(context, "set_layout_portrait").getOrElse("Set preferred orientation to dynamic")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
    }
  }
  @Loggable
  def getShowDialogWelcome(context: Context): Boolean = try {
    Common.getPublicPreferences(context) map {
      pref =>
        pref.getBoolean(DOption.ShowDialogWelcome, PreferenceManager.getDefaultSharedPreferences(context).
          getBoolean(Preference.showDialogWelcomeKey, defaultShowDialogWelcome))
    } getOrElse {
      PreferenceManager.getDefaultSharedPreferences(context).
        getBoolean(Preference.showDialogWelcomeKey, defaultShowDialogWelcome)
    }
  } catch {
    case e =>
      log.error(e.getMessage, e)
      defaultShowDialogWelcome
  }
  @Loggable
  def setShowDialogWelcome(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit =
    setShowDialogWelcome(getShowDialogWelcome(context), context)(logger, dispatcher)
  @Loggable
  def setShowDialogWelcome(f: Boolean, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = {
    Common.getPublicPreferences(context) map {
      pref =>
        if (pref.getBoolean(DOption.ShowDialogWelcome, defaultShowDialogWelcome) == f) {
          log.info("current 'show dialog welcome' already set to " + f)
          return
        } else {
          val editor = pref.edit()
          editor.putBoolean(DOption.ShowDialogWelcome, f)
          editor.commit()
        }
        val message = if (f)
          Android.getString(context, "show_dialog_welcome_on_notify").getOrElse("Dialog <Welcome> enabled")
        else
          Android.getString(context, "show_dialog_welcome_off_notify").getOrElse("Dialog <Welcome> disabled")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
    } orElse {
      log.fatal("unable to get public preferences with context" + context)
      None
    }
  }
  @Loggable
  def getShowDialogRate(context: Context): Int = try {
    if (PreferenceManager.getDefaultSharedPreferences(context).
      getBoolean(Preference.showDialogRateKey, defaultShowDialogRate)) {
      Common.getPublicPreferences(context) map (_.getInt(DOption.ShowDialogRate, 0)) getOrElse 0
    } else -1
  } catch {
    case e =>
      log.error(e.getMessage, e)
      -1
  }
  @Loggable
  def incAndGetShowDialogRate(context: Context): Int = {
    Common.getPublicPreferences(context) foreach {
      pref =>
        val newVal = pref.getInt(DOption.ShowDialogRate, 0) + 1
        val editor = pref.edit()
        editor.putInt(DOption.ShowDialogRate, newVal)
        editor.commit()
    }
    getShowDialogRate(context)
  }
  @Loggable
  def setShowDialogRate(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit =
    setShowDialogRate(PreferenceManager.getDefaultSharedPreferences(context).
      getBoolean(Preference.showDialogRateKey, defaultShowDialogRate), context)(logger, dispatcher)
  @Loggable
  def setShowDialogRate(f: Boolean, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = {
    Common.getPublicPreferences(context) map {
      pref =>
        if (pref.getBoolean(DOption.ShowDialogRate, defaultShowDialogRate) == f) {
          log.info("current 'show dialog rate' already set to " + f)
          return
        }
        val message = if (f)
          Android.getString(context, "show_dialog_rate_on_notify").getOrElse("Dialog <Rate It> enabled")
        else
          Android.getString(context, "show_dialog_rate_off_notify").getOrElse("Dialog <Rate It> disabled")
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
    } orElse {
      log.fatal("unable to get public preferences with context" + context)
      None
    }
  }
  @Loggable
  def getShutdownTimeout(context: Context): Int = try {
    Common.getPublicPreferences(context) map {
      pref =>
        pref.getInt(DOption.ShutdownTimeout, PreferenceManager.getDefaultSharedPreferences(context).
          getString(Preference.shutdownTimeoutKey, defaultShutdownTimeout.toString).toInt)
    } getOrElse {
      PreferenceManager.getDefaultSharedPreferences(context).
        getString(Preference.shutdownTimeoutKey, defaultShutdownTimeout.toString).toInt
    }
  } catch {
    case e =>
      log.error(e.getMessage, e)
      defaultShutdownTimeout
  }
  @Loggable
  def setShutdownTimeout(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit =
    setShutdownTimeout(getShutdownTimeout(context).toString, context)(logger, dispatcher)
  @Loggable
  def setShutdownTimeout(timeout: String, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = {
    Common.getPublicPreferences(context) map {
      pref =>
        if (pref.getInt(DOption.ShutdownTimeout, defaultShutdownTimeout).toString == timeout) {
          log.info("current 'shutdown timeout' already set to " + timeout)
          return
        } else {
          val editor = pref.edit()
          editor.putInt(DOption.ShutdownTimeout, timeout.toInt)
          editor.commit()
        }
        val message = Android.getString(context, "shutdown_timeout_on_notify").getOrElse("Set timeout to %s seconds").format(timeout)
        if (notify)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        IAmMumble(message)(logger, dispatcher)
    } orElse {
      log.fatal("unable to get public preferences with context" + context)
      None
    }
  }
}