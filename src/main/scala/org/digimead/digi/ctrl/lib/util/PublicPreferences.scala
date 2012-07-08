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
import java.io.FilenameFilter
import java.util.concurrent.atomic.AtomicLong

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

case class PublicPreferences private (val file: File, val bundle: Bundle, baseBlob: Long)
  extends SharedPreferences with Parcelable with Logging {
  val blob = new AtomicLong(baseBlob)
  log.debug("public preferences directory is \"" + file.getAbsolutePath + "\"")
  log.debug("create new public preferences id " + blob.get)
  log.debug("alive")

  def this(in: Parcel) =
    this(file = new File(in.readString), bundle = in.readBundle(), baseBlob = in.readLong)
  def writeToParcel(out: Parcel, flags: Int) {
    if (log.isTraceExtraEnabled)
      log.trace("writeToParcel PublicPreferences with flags " + flags)
    out.writeString(file.getAbsolutePath)
    out.writeBundle(bundle)
    out.writeLong(baseBlob)
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
  @volatile private[util] var preferences: PublicPreferences = null
  @volatile private[util] var onSharedPreferenceChangeListeners: Seq[SharedPreferences.OnSharedPreferenceChangeListener] = Seq()
  override protected[lib] val log = Logging.getRichLogger(this)
  final val CREATOR: Parcelable.Creator[PublicPreferences] = new Parcelable.Creator[PublicPreferences]() {
    def createFromParcel(in: Parcel): PublicPreferences = try {
      if (log.isTraceExtraEnabled)
        log.trace("createFromParcel new PublicPreferences")
      new PublicPreferences(in)
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    def newArray(size: Int): Array[PublicPreferences] = new Array[PublicPreferences](size)
  }
  val preferenceFilter = new FilenameFilter() { def accept(file: File, name: String) = name.endsWith(".blob") }
  // allow interprocess notification
  val preferencesUpdateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      val keys = intent.getStringArrayExtra("keys")
      log.debug("receive PublicPreferences update notification for keys [" + keys.mkString(", ") + "]")
      for {
        context <- AppComponent.Context
        oldPreferences <- Option(preferences)
        newPreferences <- Option(PublicPreferences(context.getApplicationContext, Some(oldPreferences.file)))
      } onSharedPreferenceChangeListeners.foreach(l => keys.foreach(k => l.onSharedPreferenceChanged(newPreferences, k)))
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  // AppComponent global state subscriber
  val globalStateSubscriber = new Subscriber[AppComponentEvent, AppComponent.type#Pub] {
    def notify(pub: AppComponent.type#Pub, event: AppComponentEvent) = event match {
      case AppComponent.Event.Shutdown =>
        log.debug("receive shutdown event, unregister OnSharedPreferenceChangeListeners")
        unregisterOnSharedPreferenceChangeListeners()
      case _ =>
    }
  }
  AppComponent.subscribe(globalStateSubscriber)
  log.debug("alive")

  def apply(context: Context, location: Option[File] = None): PublicPreferences = synchronized {
    if (preferences != null)
      Option(preferences.file.list(preferenceFilter)).foreach(_.sorted.lastOption match {
        case Some(latest) if (latest == preferences.blob.get + ".blob") =>
          return preferences
        case _ =>
          preferences = null
      })
    location orElse getLocation(context) flatMap {
      file =>
        // get blob id
        val preferenceBlob = Option(file.list(preferenceFilter)).flatMap(_.sorted.lastOption) match {
          case Some(latest) if latest.length > 5 =>
            try {
              val blob = latest.takeWhile(_ != '.').toLong
              log.debug("return latest public preferences " + blob)
              blob
            } catch {
              case e =>
                log.error(e.getMessage, e)
                val blob = System.currentTimeMillis
                log.debug("return new public preferences " + blob)
                blob
            }
          case _ =>
            val blob = System.currentTimeMillis
            var shift = 0 // (in future)
            while (new File(file, blob + ".blob").exists)
              shift += 1
            log.debug("return new public preferences " + blob)
            blob + shift
        }
        // open
        val publicPreferenceBlob = new File(file, preferenceBlob + ".blob")
        if (!publicPreferenceBlob.exists || publicPreferenceBlob.length == 0) {
          preferences = new PublicPreferences(file, new Bundle, preferenceBlob)
        } else {
          val biStream = new BufferedInputStream(new FileInputStream(publicPreferenceBlob))
          val baoStream = new ByteArrayOutputStream()
          preferences = try {
            Common.writeToStream(biStream, baoStream)
            val result = Common.unparcelFromArray[PublicPreferences](baoStream.toByteArray).getOrElse({
              log.error("preferences file " + publicPreferenceBlob.getAbsolutePath + " broken")
              publicPreferenceBlob.delete
              new PublicPreferences(file, new Bundle, preferenceBlob)
            }).copy(baseBlob = preferenceBlob)
            result.blob.set(preferenceBlob)
            result
          } catch {
            case e =>
              log.error("preferences file " + publicPreferenceBlob.getAbsolutePath + " broken: " + e.getMessage, e)
              publicPreferenceBlob.delete
              new PublicPreferences(file, new Bundle, preferenceBlob)
          } finally {
            if (biStream != null)
              biStream.close
          }
        }
        // cleanup
        val activeFileName = Option(preferences).map(_.blob.get + ".blob").getOrElse(null)
        for {
          files <- Option(file.list(preferenceFilter)).map(_.sorted)
          preferenceFile <- files
        } try {
          val blob = preferenceFile.takeWhile(_ != '.').toLong
          if (preferenceFile != activeFileName && System.currentTimeMillis - blob > 60 * 60 * 1000) {
            log.debug("delete outdated preference record " + preferenceFile)
            new File(file, preferenceFile).delete
          }
        } catch {
          case e =>
            log.error(e.getMessage, e)
            log.debug("delete broken preference record " + preferenceFile)
            new File(file, preferenceFile).delete
        }
        Option(preferences)
    }
  } getOrElse { throw new RuntimeException("public preferences unavailable") }
  def getLocation(context: Context, forceInternal: Boolean = false): Option[File] =
    Common.getDirectory(context, ".", forceInternal, Some(true), Some(false), Some(true)).flatMap(dir => {
      // package path,that prevent erase
      val file = new File(dir.getParentFile.getParentFile, "preferences")
      if (!file.isDirectory) {
        if (file.exists)
          file.delete
        if (file.mkdirs())
          Some(file)
        else
          None
      } else
        Some(file)
    })
  def reset(context: Context) = synchronized {
    getLocation(context, false).foreach(Common.deleteFile)
    getLocation(context, true).foreach(Common.deleteFile)
    preferences = null
  }
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
    if (onSharedPreferenceChangeListeners.contains(listener))
      log.fatal("onSharedPreferenceChangeListener already exists " + listener)
    else
      onSharedPreferenceChangeListeners = onSharedPreferenceChangeListeners :+ listener
  }
  @Loggable
  def unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = synchronized {
    onSharedPreferenceChangeListeners = onSharedPreferenceChangeListeners.filterNot(_ == listener)
    if (onSharedPreferenceChangeListeners.isEmpty)
      AppComponent.Context foreach {
        ctx =>
          ctx.getApplicationContext.unregisterReceiver(preferencesUpdateReceiver)
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
          val blob = System.currentTimeMillis
          log.debug("commit public preferences to \"" + p.file.getAbsolutePath + " id " + blob + "\"")
          val modifiedKeys = (for (key <- changes.keySet)
            yield if (p.bundle.containsKey(key) && p.bundle.get(key) == changes.get(key))
            None else Some(key)).flatten
          p.bundle.clear
          p.bundle.putAll(changes)
          // prepare
          if (!p.file.isDirectory) {
            if (p.file.exists)
              p.file.delete
            p.file.mkdirs()
          }
          // save
          val publicPreferenceBlob = new File(p.file, blob + ".blob")
          val foStream = new FileOutputStream(publicPreferenceBlob)
          val boStream = new BufferedOutputStream(foStream)
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
            foStream.flush
            foStream.close
          }
          // cleanup
          val oldBlob = new File(p.file, p.blob.get + ".blob")
          log.debug("update preference record from " + p.blob.get + " to " + blob)
          p.blob.set(blob)
          if (oldBlob.exists) {
            log.debug("delete outdated preference record " + oldBlob.getName)
            oldBlob.delete
          }
          // notify
          AppComponent.Context foreach {
            ctx =>
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
  private[util] class Editor extends SharedPreferences.Editor {
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
