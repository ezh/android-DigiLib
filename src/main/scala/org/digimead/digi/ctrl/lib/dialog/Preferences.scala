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

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.util.ExceptionHandler

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

abstract class Preferences(implicit dispatcher: Dispatcher) extends PreferenceActivity with Logging with OnSharedPreferenceChangeListener {
  implicit protected val logger: RichLogger
  protected lazy val shared = PreferenceManager.getDefaultSharedPreferences(this)
  @Loggable
  override def onCreate(savedInstanceState: Bundle) {
    log.trace("Preference::onCreate")
    super.onCreate(savedInstanceState)
    AnyBase.init(this, false)
    AnyBase.preventShutdown(this)
    ExceptionHandler.retry[Unit](1) {
      try {
        addPreferencesFromResource(Android.getId(this, "options", "xml"))
      } catch {
        case e: ClassCastException =>
          IAmWarn("reset broken preferences")
          val editor = shared.edit
          editor.clear
          editor.commit
          throw e
      }
    }
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_BEHIND)
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
  @Loggable
  override def onDestroy() {
    log.trace("Preference::onDestroy")
    AnyBase.deinit(this)
    super.onDestroy()
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
      case p: ListPreference if key == DOption.DebugLogLevel.tag =>
        if (shared.contains(DOption.DebugLogLevel.tag))
          // DOption.DebugLogLevel.tag exists
          Preferences.DebugLogLevel.set(p.getValue.toString, this, notify)(logger, dispatcher)
        else
          // DOption.DebugLogLevel.tag not exists
          Preferences.DebugLogLevel.set(Preferences.DebugLogLevel.get(this).toString, this, notify)(logger, dispatcher)
      case p: CheckBoxPreference if key == DOption.DebugAndroidLogger.tag =>
        if (shared.contains(DOption.DebugAndroidLogger.tag))
          // DOption.DebugAndroidLogger.tag exists
          Preferences.DebugAndroidLogger.set(shared.getBoolean(DOption.DebugAndroidLogger.tag, Preferences.DebugAndroidLogger.default), this, notify)(logger, dispatcher)
        else
          // DOption.DebugAndroidLogger.tag not exists
          Preferences.DebugAndroidLogger.set(Preferences.DebugAndroidLogger.get(this), this, notify)(logger, dispatcher)
      case p: ListPreference if key == DOption.PreferredLayoutOrientation.tag =>
        if (shared.contains(DOption.PreferredLayoutOrientation.tag))
          // DOption.PreferredLayoutOrientation.tag exists
          Preferences.PreferredLayoutOrientation.set(p.getValue.toString, this, notify)(logger, dispatcher)
        else
          // DOption.PreferredLayoutOrientation.tag not exists
          Preferences.PreferredLayoutOrientation.set(Preferences.PreferredLayoutOrientation.get(this).toString, this, notify)(logger, dispatcher)
      case p: ListPreference if key == DOption.ShutdownTimeout.tag =>
        if (shared.contains(DOption.ShutdownTimeout.tag))
          // DOption.ShutdownTimeout.tag exists
          Preferences.ShutdownTimeout.set(p.getValue.toString, this, notify)(logger, dispatcher)
        else
          // DOption.ShutdownTimeout.tag not exists
          Preferences.ShutdownTimeout.set(Preferences.ShutdownTimeout.get(this).toString, this, notify)(logger, dispatcher)
    }
  }
}
// default values are also duplicated at res/xml/options.xml/.../android:defaultValue
object Preferences extends Logging {
  object DebugLogLevel extends Logging {
    @volatile private var lastLogLevelLevel = ""
    def default = DOption.DebugLogLevel.default.asInstanceOf[String]
    @Loggable
    def get(context: Context): Int = synchronized {
      try {
        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        val public = Common.getPublicPreferences(context)
        public.getString(DOption.DebugLogLevel.tag, shared.getString(DOption.DebugLogLevel.tag, default.toString)).toInt
      } catch {
        case e =>
          log.error(e.getMessage, e)
          default.toInt
      }
    }
    @Loggable
    def set(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = synchronized {
      set(get(context).toString, context)(logger, dispatcher)
    }
    @Loggable
    def set(l: String, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = synchronized {
      val shared = PreferenceManager.getDefaultSharedPreferences(context)
      val public = Common.getPublicPreferences(context)
      if (!shared.contains(DOption.DebugLogLevel.tag) || public.getString(DOption.DebugLogLevel.tag, default.toString) != l) {
        log.debug("set DebugLogLevel shared preference to [%s]".format(l))
        val sharedEditor = shared.edit
        sharedEditor.putString(DOption.DebugLogLevel.tag, l)
        sharedEditor.commit
      }
      if (!public.contains(DOption.DebugLogLevel.tag) || public.getString(DOption.DebugLogLevel.tag, default.toString) != l) {
        log.debug("set DebugLogLevel public preference to [%s]".format(l))
        val publicEditor = public.edit()
        publicEditor.putString(DOption.DebugLogLevel.tag, l)
        publicEditor.commit()
      }
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
          val message = Android.getString(context, "set_loglevel_none").getOrElse("set log level to NONE")
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        case "1" =>
          Logging.setErrorEnabled(true)
          Logging.setWarnEnabled(false)
          Logging.setInfoEnabled(false)
          Logging.setDebugEnabled(false)
          Logging.setTraceEnabled(false)
          val message = Android.getString(context, "set_loglevel_error").getOrElse("set log level to ERROR")
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        case "2" =>
          Logging.setErrorEnabled(true)
          Logging.setWarnEnabled(true)
          Logging.setInfoEnabled(false)
          Logging.setDebugEnabled(false)
          Logging.setTraceEnabled(false)
          val message = Android.getString(context, "set_loglevel_warn").getOrElse("set log level to WARN")
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        case "3" =>
          Logging.setErrorEnabled(true)
          Logging.setWarnEnabled(true)
          Logging.setInfoEnabled(true)
          Logging.setDebugEnabled(false)
          Logging.setTraceEnabled(false)
          val message = Android.getString(context, "set_loglevel_info").getOrElse("set log level to INFO")
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        case "4" =>
          Logging.setErrorEnabled(true)
          Logging.setWarnEnabled(true)
          Logging.setInfoEnabled(true)
          Logging.setDebugEnabled(true)
          Logging.setTraceEnabled(false)
          val message = Android.getString(context, "set_loglevel_debug").getOrElse("set log level to DEBUG")
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        case "5" =>
          Logging.setErrorEnabled(true)
          Logging.setWarnEnabled(true)
          Logging.setInfoEnabled(true)
          Logging.setDebugEnabled(true)
          Logging.setTraceEnabled(true)
          val message = Android.getString(context, "set_loglevel_trace").getOrElse("set log level to TRACE")
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        case n =>
          log.error("unknown value " + n + " for preference " + DOption.DebugLogLevel)
      }
    }
  }
  object DebugAndroidLogger extends Logging {
    @volatile private var lastAndroidLogging = false
    def default = DOption.DebugAndroidLogger.default.asInstanceOf[Boolean]
    @Loggable
    def get(context: Context): Boolean = synchronized {
      try {
        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        val public = Common.getPublicPreferences(context)
        public.getBoolean(DOption.DebugAndroidLogger.tag, shared.getBoolean(DOption.DebugAndroidLogger.tag, default))
      } catch {
        case e =>
          log.error(e.getMessage, e)
          default
      }
    }
    @Loggable
    def set(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = synchronized {
      set(get(context), context)(logger, dispatcher)
    }
    @Loggable
    def set(f: Boolean, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = synchronized {
      val shared = PreferenceManager.getDefaultSharedPreferences(context)
      val public = Common.getPublicPreferences(context)
      if (!shared.contains(DOption.DebugAndroidLogger.tag) || public.getBoolean(DOption.DebugAndroidLogger.tag, default) != f) {
        log.debug("set DebugAndroidLogger shared preference to [%s]".format(f))
        val sharedEditor = shared.edit
        sharedEditor.putBoolean(DOption.DebugAndroidLogger.tag, f)
        sharedEditor.commit
      }
      if (!public.contains(DOption.DebugAndroidLogger.tag) || public.getBoolean(DOption.DebugAndroidLogger.tag, default) != f) {
        log.debug("set DebugAndroidLogger public preference to [%s]".format(f))
        val publicEditor = public.edit()
        publicEditor.putBoolean(DOption.DebugAndroidLogger.tag, f)
        publicEditor.commit()
      }
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
  }
  object PreferredLayoutOrientation extends Logging {
    def default = DOption.PreferredLayoutOrientation.default.asInstanceOf[String]
    @Loggable
    def get(context: Context): Int = synchronized {
      try {
        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        val public = Common.getPublicPreferences(context)
        public.getString(DOption.PreferredLayoutOrientation.tag, shared.getString(DOption.PreferredLayoutOrientation.tag, default.toString)).toInt
      } catch {
        case e =>
          log.error(e.getMessage, e)
          default.toInt
      }
    }
    @Loggable
    def set(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = synchronized {
      set(get(context).toString, context)(logger, dispatcher)
    }
    @Loggable
    def set(l: String, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = synchronized {
      val shared = PreferenceManager.getDefaultSharedPreferences(context)
      val public = Common.getPublicPreferences(context)
      if (!shared.contains(DOption.PreferredLayoutOrientation.tag) || public.getString(DOption.PreferredLayoutOrientation.tag, default.toString) != l) {
        log.debug("set PreferredLayoutOrientation shared preference to [%s]".format(l))
        val sharedEditor = shared.edit
        sharedEditor.putString(DOption.PreferredLayoutOrientation.tag, l)
        sharedEditor.commit
      }
      if (!public.contains(DOption.PreferredLayoutOrientation.tag) || public.getString(DOption.PreferredLayoutOrientation.tag, default.toString) != l) {
        log.debug("set PreferredLayoutOrientation public preference to [%s]".format(l))
        val publicEditor = public.edit()
        publicEditor.putString(DOption.PreferredLayoutOrientation.tag, l)
        publicEditor.commit()
      }
      l match {
        case "0" => // SCREEN_ORIENTATION_LANDSCAPE
          AppComponent.Inner.preferredOrientation.set(0)
          val message = Android.getString(context, "set_layout_landscape").getOrElse("set preferred orientation to landscape")
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        case "1" => // SCREEN_ORIENTATION_PORTRAIT
          AppComponent.Inner.preferredOrientation.set(1)
          val message = Android.getString(context, "set_layout_portrait").getOrElse("set preferred orientation to portrait")
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        case "8" => // SCREEN_ORIENTATION_REVERSE_LANDSCAPE
          AppComponent.Inner.preferredOrientation.set(8)
          val message = Android.getString(context, "set_layout_portrait").getOrElse("set preferred orientation to reverse landscape")
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        case "9" => // SCREEN_ORIENTATION_REVERSE_PORTRAIT
          AppComponent.Inner.preferredOrientation.set(9)
          val message = Android.getString(context, "set_layout_portrait").getOrElse("set preferred orientation to reverse portraint")
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        case "4" => // SCREEN_ORIENTATION_SENSOR
          AppComponent.Inner.preferredOrientation.set(4)
          val message = Android.getString(context, "set_layout_portrait").getOrElse("set preferred orientation to dynamic")
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        case n =>
          log.fatal("incorrect orientation value: " + n)
      }
    }
  }
  object ShutdownTimeout extends Logging {
    def default = DOption.ShutdownTimeout.default.asInstanceOf[String]
    @Loggable
    def get(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Int = synchronized {
      try {
        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        val public = Common.getPublicPreferences(context)
        try {
          public.getString(DOption.ShutdownTimeout.tag, shared.getString(DOption.ShutdownTimeout.tag, default.toString)).toInt
        } catch {
          case e: ClassCastException =>
            reset(context)(logger, dispatcher)
            default.toInt
        }
      } catch {
        case e =>
          log.error(e.getMessage, e)
          default.toInt
      }
    }
    @Loggable
    def set(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = synchronized {
      set(get(context)(logger, dispatcher).toString, context)(logger, dispatcher)
    }
    @Loggable
    def set(timeout: String, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = synchronized {
      val shared = PreferenceManager.getDefaultSharedPreferences(context)
      val public = Common.getPublicPreferences(context)
      ExceptionHandler.retry[Unit](1) {
        try {
          if (!shared.contains(DOption.ShutdownTimeout.tag) || public.getString(DOption.ShutdownTimeout.tag, default.toString) != timeout) {
            log.debug("set ShutdownTimeout shared preference to [%s]".format(timeout))
            val sharedEditor = shared.edit
            sharedEditor.putString(DOption.ShutdownTimeout.tag, timeout)
            sharedEditor.commit
          }
          if (!public.contains(DOption.ShutdownTimeout.tag) || public.getString(DOption.ShutdownTimeout.tag, default.toString) != timeout) {
            log.debug("set ShutdownTimeout public preference to [%s]".format(timeout))
            val publicEditor = public.edit()
            publicEditor.putString(DOption.ShutdownTimeout.tag, timeout)
            publicEditor.commit()
          }
          val message = Android.getString(context, "set_shutdown_timeout_notify").getOrElse("set timeout to %s seconds").format(timeout)
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        } catch {
          case e: ClassCastException =>
            reset(context)(logger, dispatcher)
            throw e
        }
      }
    }
    @Loggable
    private def reset(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher) {
      IAmWarn("reset preference ShutdownTimeout")(logger, dispatcher)
      val sharedEditor = PreferenceManager.getDefaultSharedPreferences(context).edit
      sharedEditor.remove(DOption.ShutdownTimeout.tag)
      sharedEditor.putString(DOption.ShutdownTimeout.tag, default.toString)
      sharedEditor.commit()
      val publicEditor = Common.getPublicPreferences(context).edit()
      publicEditor.remove(DOption.ShutdownTimeout.tag)
      publicEditor.putString(DOption.ShutdownTimeout.tag, default.toString)
      publicEditor.commit()
    }
  }
  object CachePeriod extends StringPreference[Int](DOption.CachePeriod, (s) => s.toInt,
    "set_cache_period_notify", "set cache period to %s seconds") {}
  object CacheFolder extends StringPreference[String](DOption.CacheFolder, (s) => s,
    "set_cache_folder_notify", "set cache folder to \"%s\"") {
    override def default = AppComponent.Context.get.getCacheDir.getAbsolutePath + "/"
  }
  object CacheClass extends StringPreference[String](DOption.CacheClass, (s) => s,
    "set_cache_class_notify", "set cache class to \"%s\"") {}
  object DebugFlag extends StringPreference[String](DOption.DebugFlag, (s) => s,
    "set_debug_flag_notify", "set debug flag to \"%s\"") {}
  // X - default, Y - in, Z - out  
  trait Preference[X, Y, Z] {
    val option: DOption#OptVal
    def default = option.default.asInstanceOf[X]
    def get(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Z
    def set(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit
    def set(value: Y, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit
  }
  abstract class StringPreference[T](val option: DOption#OptVal, convert: String => T, messageID: String, messageFallBack: String)
    extends Preference[String, String, T] {
    def get(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): T = synchronized {
      try {
        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        val public = Common.getPublicPreferences(context)
        try {
          convert(public.getString(option.tag, shared.getString(option.tag, default.toString)))
        } catch {
          case e: ClassCastException =>
            reset(context)(logger, dispatcher)
            convert(default)
        }
      } catch {
        case e =>
          log.error(e.getMessage, e)
          convert(default)
      }
    }
    def set(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = synchronized {
      set(get(context)(logger, dispatcher).toString, context)(logger, dispatcher)
    }
    def set(value: String, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = synchronized {
      val shared = PreferenceManager.getDefaultSharedPreferences(context)
      val public = Common.getPublicPreferences(context)
      ExceptionHandler.retry[Unit](1) {
        try {
          if (!shared.contains(option.tag) || public.getString(option.tag, default.toString) != value) {
            log.debug("set %s shared preference to [%s]".format(option.toString, value))
            val sharedEditor = shared.edit
            sharedEditor.putString(option.tag, value)
            sharedEditor.commit
          }
          if (!public.contains(option.tag) || public.getString(option.tag, default.toString) != value) {
            log.debug("set %s public preference to [%s]".format(option.toString, value))
            val publicEditor = public.edit()
            publicEditor.putString(option.tag, value)
            publicEditor.commit()
          }
          val message = Android.getString(context, messageID).getOrElse(messageFallBack).format(value)
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        } catch {
          case e: ClassCastException =>
            reset(context)(logger, dispatcher)
            throw e
        }
      }
    }
    private def reset(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher) {
      IAmWarn("reset preference " + option)(logger, dispatcher)
      val sharedEditor = PreferenceManager.getDefaultSharedPreferences(context).edit
      sharedEditor.remove(option.tag)
      sharedEditor.putString(option.tag, default.toString)
      sharedEditor.commit()
      val publicEditor = Common.getPublicPreferences(context).edit()
      publicEditor.remove(option.tag)
      publicEditor.putString(option.tag, default.toString)
      publicEditor.commit()
    }
  }
  abstract class BooleanPreference(val option: DOption#OptVal, messageID: String, messageFallBack: String)
    extends Preference[Boolean, Boolean, Boolean] {
    def get(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Boolean = synchronized {
      try {
        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        val public = Common.getPublicPreferences(context)
        try {
          public.getBoolean(option.tag, shared.getBoolean(option.tag, default))
        } catch {
          case e: ClassCastException =>
            reset(context)(logger, dispatcher)
            default
        }
      } catch {
        case e =>
          log.error(e.getMessage, e)
          default
      }
    }
    def set(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = synchronized {
      set(get(context)(logger, dispatcher), context)(logger, dispatcher)
    }
    def set(value: Boolean, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = synchronized {
      val shared = PreferenceManager.getDefaultSharedPreferences(context)
      val public = Common.getPublicPreferences(context)
      ExceptionHandler.retry[Unit](1) {
        try {
          if (!shared.contains(option.tag) || public.getString(option.tag, default.toString) != value) {
            log.debug("set %s shared preference to [%s]".format(option.toString, value))
            val sharedEditor = shared.edit
            sharedEditor.putBoolean(option.tag, value)
            sharedEditor.commit
          }
          if (!public.contains(option.tag) || public.getString(option.tag, default.toString) != value) {
            log.debug("set %s public preference to [%s]".format(option.toString, value))
            val publicEditor = public.edit()
            publicEditor.putBoolean(option.tag, value)
            publicEditor.commit()
          }
          val message = Android.getString(context, messageID).getOrElse(messageFallBack).format(value)
          if (notify)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          IAmMumble(message)(logger, dispatcher)
        } catch {
          case e: ClassCastException =>
            reset(context)(logger, dispatcher)
            throw e
        }
      }
    }
    private def reset(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher) {
      IAmWarn("reset preference " + option)(logger, dispatcher)
      val sharedEditor = PreferenceManager.getDefaultSharedPreferences(context).edit
      sharedEditor.remove(option.tag)
      sharedEditor.putBoolean(option.tag, default)
      sharedEditor.commit()
      val publicEditor = Common.getPublicPreferences(context).edit()
      publicEditor.remove(option.tag)
      publicEditor.putBoolean(option.tag, default)
      publicEditor.commit()
    }
  }
}
