/**
 * Copyright (c) 2010-2012 Alexey Aksenov ezh@ezh.msk.ru
 * This file is part of the Documentum Elasticus project.
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

package org.digimead.digi.ctrl.lib.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

import scala.Option.option2Iterable
import scala.actors.Futures.future
import scala.annotation.implicitNotFound
import scala.collection.JavaConversions._
import scala.collection.mutable.Subscriber

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppComponentEvent
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DPermission
import org.digimead.digi.ctrl.lib.log.Logging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

class PublicPreferences private (val file: File, val bundle: Bundle)
  extends SharedPreferences with Parcelable with Logging {
  log.debug("public preferences file is \"" + file.getAbsolutePath + "\"")
  log.debug("alive")
  def this(in: Parcel) =
    this(file = new File(in.readString), bundle = in.readBundle())
  def writeToParcel(out: Parcel, flags: Int) {
    log.debug("writeToParcel PublicPreferences with flags " + flags)
    out.writeString(file.getAbsolutePath)
    out.writeBundle(bundle)
  }
  def describeContents() = 0
  def unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
    PublicPreferences.unregisterOnSharedPreferenceChangeListener(listener)
  def registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
    PublicPreferences.registerOnSharedPreferenceChangeListener(listener)
  def edit(): SharedPreferences.Editor = new PublicPreferences.Editor
  def contains(key: String): Boolean = bundle.containsKey(key)
  def getBoolean(key: String, defValue: Boolean): Boolean = bundle.getBoolean(key, defValue)
  def getFloat(key: String, defValue: Float): Float = bundle.getFloat(key, defValue)
  def getLong(key: String, defValue: Long): Long = bundle.getLong(key, defValue)
  def getInt(key: String, defValue: Int): Int = bundle.getInt(key, defValue)
  def getString(key: String, defValue: String): String = bundle.getString(key) match {
    case n: String => n
    case _ => defValue
  }
  def getAll(): java.util.Map[String, Object] = {
    val result = new java.util.HashMap[String, Object]()
    val iterator = bundle.keySet.iterator.foreach(key => result.put(key, bundle.get(key)))
    result
  }
}

object PublicPreferences extends Logging {
  @volatile var preferences: PublicPreferences = null
  @volatile var preferencesModificationTime = 0L
  @volatile var onSharedPreferenceChangeListeners: Seq[SharedPreferences.OnSharedPreferenceChangeListener] = Seq()
  override protected[lib] val log = Logging.getLogger(this)
  final val CREATOR: Parcelable.Creator[PublicPreferences] = new Parcelable.Creator[PublicPreferences]() {
    def createFromParcel(in: Parcel): PublicPreferences = try {
      log.debug("createFromParcel new PublicPreferences")
      new PublicPreferences(in)
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    def newArray(size: Int): Array[PublicPreferences] = new Array[PublicPreferences](size)
  }
  // allow interprocess notification
  private val preferencesUpdateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      log.debug("receive PublicPreferences update notification")
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  // AppComponent state subscriber
  val stateSubscriber = new Subscriber[AppComponentEvent, AppComponent.type#Pub] {
    def notify(pub: AppComponent.type#Pub, event: AppComponentEvent) = event match {
      case AppComponent.Event.Shutdown =>
        log.debug("receive shutdown event, unregister OnSharedPreferenceChangeListeners")
        unregisterOnSharedPreferenceChangeListeners()
      case _ =>
    }
  }
  AppComponent.subscribe(stateSubscriber)
  log.debug("alive")

  def apply(context: Context, location: Option[File] = None): Option[PublicPreferences] = {
    if (preferences != null) {
      if (preferencesModificationTime == preferences.file.lastModified)
        return Some(preferences)
      else
        preferences = null
    }
    location orElse getLocation(context) flatMap {
      file =>
        if (file.length == 0) {
          log.debug("create new public preferences")
          preferences = new PublicPreferences(file, new Bundle)
        } else {
          val biStream = new BufferedInputStream(new FileInputStream(file))
          val baoStream = new ByteArrayOutputStream()
          preferences = try {
            Common.writeToStream(biStream, baoStream)
            Common.unparcelFromArray[PublicPreferences](baoStream.toByteArray).getOrElse({
              log.error("preferences file " + file.getAbsolutePath + " broken")
              log.debug("create new public preferences")
              new PublicPreferences(file, new Bundle)
            })
          } catch {
            case e =>
              log.error(e.getMessage, e)
              log.error("preferences file " + file.getAbsolutePath + " broken")
              log.debug("create new public preferences")
              new PublicPreferences(file, new Bundle)
          } finally {
            if (biStream != null)
              biStream.close
          }
        }
        Option(preferences).map(p => { preferencesModificationTime = p.file.lastModified; p })
    }
  }
  def getLocation(context: Context): Option[File] = Common.getDirectory(context, ".").flatMap(dir => {
    // package path,that prevent erase
    val file = new File(dir.getParentFile.getParentFile, "preferences")
    if (!file.exists) {
      if (file.createNewFile)
        Some(file)
      else
        None
    } else
      Some(file)
  })
  @Loggable
  def registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = synchronized {
    log.debugWhere("add OnSharedPreferenceChangeListener " + listener, Logging.Where.BEFORE)
    if (onSharedPreferenceChangeListeners.isEmpty) {
      AppComponent.Context foreach {
        ctx =>
          log.debug("register OnSharedPreferenceChangeListener entry point")
          val context = ctx.getApplicationContext
          val preferencesUpdateFilter = new IntentFilter(DIntent.Update)
          preferencesUpdateFilter.addDataScheme("code")
          preferencesUpdateFilter.addDataAuthority(DConstant.PublicPreferencesAuthority, null)
          try {
            context.registerReceiver(preferencesUpdateReceiver, preferencesUpdateFilter, DPermission.Base, null)
          } catch {
            case e =>
              log.error(e.getMessage, e)
          }
      }
    }
    onSharedPreferenceChangeListeners = onSharedPreferenceChangeListeners :+ listener
  }
  @Loggable
  def unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = synchronized {
    onSharedPreferenceChangeListeners = onSharedPreferenceChangeListeners.filterNot(_ == listener)
    if (onSharedPreferenceChangeListeners.isEmpty)
      AppComponent.Context foreach {
        ctx =>
          val context = ctx.getApplicationContext
          try {
            context.unregisterReceiver(preferencesUpdateReceiver)
          } catch {
            case e =>
              log.error(e.getMessage, e)
          }
      }
  }
  @Loggable
  def unregisterOnSharedPreferenceChangeListeners() =
    onSharedPreferenceChangeListeners.foreach(unregisterOnSharedPreferenceChangeListener)
  @Loggable
  private def commit(changes: Bundle): Boolean = synchronized {
    try {
      Option(preferences).map {
        p =>
          log.debug("commit public preferences to \"" + p.file.getAbsolutePath + "\"")
          p.bundle.clear
          p.bundle.putAll(changes)
          val boStream = new BufferedOutputStream(new FileOutputStream(p.file))
          val baoStream = new ByteArrayOutputStream()
          try {
            baoStream.write(Common.parcelToArray(p))
            baoStream.writeTo(boStream)
          } catch {
            case e =>
              log.error(e.getMessage, e)
          } finally {
            if (boStream != null)
              boStream.close
          }
          AppComponent.Context foreach {
            ctx =>
              val modifiedKeys = (for (key <- changes.keySet)
                yield if (p.bundle.containsKey(key) && p.bundle.get(key) == changes.get(key))
                None else Some(key)).flatten
              log.debug("modified keys [" + modifiedKeys.mkString(", ") + "]")
              if (modifiedKeys.nonEmpty) {
                log.debug("send notification to OnSharedPreferenceChangeListeners")
                val intent = new Intent(DIntent.Update, Uri.parse("code://" + DConstant.PublicPreferencesAuthority))
                intent.putExtra("keys", modifiedKeys.toArray)
                ctx.sendBroadcast(intent, DPermission.Base)
              }
          }
          true
      } getOrElse {
        log.warn("commit public preferences failed")
        false
      }
    } catch {
      case e =>
        log.error(e.getMessage, e)
        false
    }
  }
  private class Editor extends SharedPreferences.Editor {
    val changes = preferences match {
      case p: PublicPreferences => new Bundle(p.bundle)
      case null => new Bundle()
    }
    def commit(): Boolean =
      PublicPreferences.commit(changes)
    def apply() =
      future { commit() }
    def clear(): SharedPreferences.Editor =
      { changes.clear; this }
    def remove(key: String): SharedPreferences.Editor =
      { changes.remove(key); this }
    def putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
      { changes.putBoolean(key, value); this }
    def putFloat(key: String, value: Float): SharedPreferences.Editor =
      { changes.putFloat(key, value); this }
    def putLong(key: String, value: Long): SharedPreferences.Editor =
      { changes.putLong(key, value); this }
    def putInt(key: String, value: Int): SharedPreferences.Editor =
      { changes.putInt(key, value); this }
    def putString(key: String, value: String): SharedPreferences.Editor =
      { changes.putString(key, value); this }
  }
}
