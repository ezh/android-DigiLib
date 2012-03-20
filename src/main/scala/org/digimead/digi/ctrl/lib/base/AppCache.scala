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

/*
 * very simple cache routine that save record with keys like
 * Int Int Int ... = Any :-)
 * namespace method_signature_hash arg1_hash arg2_hash and so on
 * lack a lot of cool futures, but deadline is the only point of view
 * Ezh
 */

package org.digimead.digi.ctrl.lib.base

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import scala.actors.Actor
import scala.annotation.elidable
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap
import scala.ref.SoftReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DPreference

import android.content.Context
import annotation.elidable.ASSERTION

trait AppCacheT[K, V] {
  def get(namespace: scala.Enumeration#Value, key: K): Option[V]
  def get(namespace: scala.Enumeration#Value, key: K, period: Long): Option[V]
  def get(namespaceID: Int, key: K, period: Long): Option[V]
  def apply(namespace: scala.Enumeration#Value, key: K): V
  def apply(namespace: scala.Enumeration#Value, key: K, period: Long): V
  def apply(namespaceID: Int, key: K, period: Long): V
  def update(namespace: scala.Enumeration#Value, key: K, value: V)
  def update(namespaceID: Int, key: K, value: V)
  def update(namespace: scala.Enumeration#Value, updates: Iterable[(K, V)])
  def remove(namespace: scala.Enumeration#Value, key: K): Option[V]
  def remove(namespaceID: Int, key: K): Option[V]
  def clear(namespace: scala.Enumeration#Value): Unit
}

class NilCache extends AppCacheT[String, Any] {
  def get(namespace: scala.Enumeration#Value, key: String) = None
  def get(namespace: scala.Enumeration#Value, key: String, period: Long) = None
  def get(namespaceID: Int, key: String, period: Long) = None
  def apply(namespace: scala.Enumeration#Value, key: String) = None
  def apply(namespace: scala.Enumeration#Value, key: String, period: Long) = None
  def apply(namespaceID: Int, key: String, period: Long) = None
  def update(namespace: scala.Enumeration#Value, key: String, value: Any): Unit = {}
  def update(namespaceID: Int, key: String, value: Any): Unit = {}
  def update(namespace: scala.Enumeration#Value, updates: Iterable[(String, Any)]): Unit = {}
  def remove(namespace: scala.Enumeration#Value, key: String): Option[Any] = None
  def remove(namespaceID: Int, key: String): Option[Any] = None
  def clear(namespace: scala.Enumeration#Value): Unit = {}
}
class AppCache extends AppCacheT[String, Any] with Logging {
  def get(namespace: scala.Enumeration#Value, key: String) =
    get(namespace, key, AppCache.getDefaultPeriod())
  def get(namespace: scala.Enumeration#Value, key: String, period: Long): Option[Any] =
    get(namespace.id, key, period)
  def get(namespaceID: Int, key: String, period: Long): Option[Any] = {
    log.trace("search cached value for namespace id " + namespaceID + " and key " + key)
    val ref = namespaceID + " " + key
    AppCache.map.get(ref).flatMap(_.get) match {
      case None =>
        val file = new File(AppCache.cacheFolder, ref)
        if (file.exists) {
          if (period > 0) {
            if (System.currentTimeMillis() - file.lastModified() <= period) {
              // period > 0 and file is fresh
              readFile(namespaceID, key, file) match {
                case Some(value @ (time, obj)) =>
                  AppCache.map(ref) = new SoftReference(value)
                  Some(obj)
                case None =>
                  file.delete()
                  None
              }
            } else {
              // period > 0 and file outdated
              file.delete()
              log.trace("MISS, expired")
              None
            }
          } else
            // period <= 0
            readFile(namespaceID, key, file) match {
              case Some(value @ (time, obj)) =>
                AppCache.map(ref) = new SoftReference(value)
                Some(obj)
              case None =>
                file.delete()
                log.trace("MISS, period " + period)
                None
            }
        } else {
          log.trace("MISS")
          None
        }
      case Some((time, obj)) =>
        if (period > 0)
          if (System.currentTimeMillis() - time <= period)
            Some(obj)
          else {
            // remove file
            AppCache.map.remove(ref)
            None
          }
        else
          Some(obj)
    }
  }
  def apply(namespace: scala.Enumeration#Value, key: String) =
    get(namespace, key, AppCache.getDefaultPeriod()) get
  def apply(namespace: scala.Enumeration#Value, key: String, period: Long): Any =
    get(namespace.id, key, period) get
  def apply(namespaceID: Int, key: String, period: Long): Any =
    get(namespaceID, key, period) get
  def update(namespace: scala.Enumeration#Value, key: String, value: Any): Unit =
    update(namespace.id, key, value)
  def update(namespaceID: Int, key: String, value: Any): Unit = {
    log.trace("update cached value for namespace id " + namespaceID + " and key " + key)
    val ref = namespaceID + " " + key
    val file = new File(AppCache.cacheFolder, ref)
    writeFile(namespaceID, key, file, value)
    AppCache.map(ref) = new SoftReference((System.currentTimeMillis(), value))
  }
  def update(namespace: scala.Enumeration#Value, updates: Iterable[(String, Any)]): Unit =
    updates.foreach(t => update(namespace, t._1, t._2))
  def remove(namespace: scala.Enumeration#Value, key: String): Option[Any] =
    remove(namespace.id, key)
  def remove(namespaceID: Int, key: String): Option[Any] = {
    val ref = namespaceID + " " + key
    val file = new File(AppCache.cacheFolder, ref)
    if (file.exists)
      if (!file.delete())
        log.fatal("failed to delete cache file " + file)
    AppCache.map.remove(ref).flatMap(ref => ref.get.map(t => t._2))
  }
  def clear(namespace: scala.Enumeration#Value): Unit = {
    val prefix = namespace.id + " "
    AppCache.map.filter(t => t._1.startsWith(prefix)).foreach(t => {
      log.debug("remove cache ref " + prefix + t._1)
      AppCache.map.remove(t._1)
    })
    AppCache.cacheFolder.listFiles(new FileFilter {
      override def accept(file: File) = file.getName.startsWith(prefix)
    }).foreach(f => {
      log.debug("remove cache file " + f.getName())
      f.delete()
    })
  }
  private def readFile(namespaceID: Int, key: String, file: File): Option[(Long, Any)] = try {
    log.trace("read cached value from file for namespace id " + namespaceID + " and key " + key)
    val ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))
    val o = ois.readObject()
    ois.close()
    Some(file.lastModified(), o)
  } catch {
    case e =>
      log.warn("broken cache file " + file + ", " + e.getMessage, e)
      None
  }
  private def writeFile(namespaceID: Int, key: String, file: File, o: Any) = try {
    log.trace("write cached value to file for namespace id " + namespaceID + " and key " + key)
    val oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))
    oos.writeObject(o)
    oos.close()
  } catch {
    case e =>
      log.warn("failed update cache file " + file + ", " + e.getMessage, e)
  }
}

object AppCache extends Actor with Logging {
  @volatile private var contextPackageName = ""
  private var inner: AppCacheT[String, Any] = null
  private var period: Long = 1000 * 60 * 10 // 10 minutes
  private var cacheClass = "org.digimead.digi.ctrl.lib.base.AppCache"
  private var cachePath = "."
  private lazy val cacheFolder = new File(cachePath)
  // key -> (timestamp, data)
  private val map = new HashMap[String, SoftReference[(Long, Any)]]() with SynchronizedMap[String, SoftReference[(Long, Any)]]
  log.debug("alive")
  def act = {
    loop {
      react {
        case Message.Get(namespace, key, period) =>
          try {
            reply(inner.get(namespace, key, period))
          } catch {
            case e =>
              log.warn(e.getMessage(), e)
              reply(None)
          }
        case Message.GetByID(namespaceID, key, period) =>
          try {
            reply(inner.get(namespaceID, key, period))
          } catch {
            case e =>
              log.warn(e.getMessage(), e)
              reply(None)
          }
        case Message.Update(namespace, key, value) =>
          try {
            inner.update(namespace, key, value)
          } catch {
            case e =>
              log.warn(e.getMessage(), e)
          }
        case Message.UpdateByID(namespaceID, key, value) =>
          try {
            inner.update(namespaceID, key, value)
          } catch {
            case e =>
              log.warn(e.getMessage(), e)
          }
        case Message.UpdateMany(namespace, updates) =>
          try {
            inner.update(namespace, updates)
          } catch {
            case e =>
              log.warn(e.getMessage(), e)
          }
        case Message.Remove(namespace, key) =>
          try {
            reply(inner.remove(namespace, key))
          } catch {
            case e =>
              log.warn(e.getMessage(), e)
          }
        case Message.RemoveByID(namespaceID, key) =>
          try {
            reply(inner.remove(namespaceID, key))
          } catch {
            case e =>
              log.warn(e.getMessage(), e)
          }
        case Message.Clear(namespace) =>
          try {
            inner.clear(namespace)
          } catch {
            case e =>
              log.warn(e.getMessage(), e)
          }
        case Message.Reinitialize(context, innerCache) =>
          inner = null
          init(context, innerCache)
        case message: AnyRef =>
          log.error("skip unknown message " + message.getClass.getName + ": " + message)
        case message =>
          log.error("skip unknown message " + message)
      }
    }
  }
  def getDefaultPeriod(): Long = synchronized {
    period
  }
  @Loggable
  def setDefaultPeriod(_period: Long) = synchronized {
    period = _period
  }
  @Loggable
  def init(context: Context, innerCache: AppCache = null): Unit = synchronized {
    if (inner != null) {
      if (contextPackageName == context.getPackageName())
        return // reinitialize AppCache for the same package is too aggressive
      log.info("reinitialize AppCache core subsystem for " + context.getPackageName())
      /*
       * since actor is a single point of entry
       * process all requests
       * and then reinitialize
       */
      this ! Message.Reinitialize(context, innerCache)
      return
    } else
      log.info("initialize AppCache for " + context.getPackageName())
    contextPackageName = context.getPackageName()
    val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
    period = pref.getLong(DOption.CachePeriod.res, period)
    cachePath = pref.getString(DOption.CacheFolder.res, context.getCacheDir + "/")
    cacheClass = pref.getString(DOption.CacheClass.res, cacheClass)
    if (innerCache != null) {
      inner = innerCache
    } else {
      inner = try {
        log.debug("create cache with implementation \"" + cacheClass + "\"")
        Class.forName(cacheClass).newInstance().asInstanceOf[AppCacheT[String, Any]]
      } catch {
        case e =>
          log.warn(e.getMessage(), e)
          new AppCache()
      }
    }
    if (!cacheFolder.exists)
      if (!cacheFolder.mkdirs) {
        log.fatal("cannot create directory: " + cacheFolder)
        inner = new NilCache()
      }
    log.info("set cache directory to \"" + cachePath + "\"")
    log.info("set cache implementation to \"" + inner.getClass.getName() + "\"")
    start()
  }
  def deinit() = synchronized {
    log.info("deinitialize AppCache for " + contextPackageName)
    assert(inner != null)
    inner = null
  }
  def initialized = synchronized { inner != null }
  object Message {
    case class Get(namespace: scala.Enumeration#Value, key: String, period: Long = getDefaultPeriod())
    case class GetByID(namespaceId: Int, key: String, period: Long = getDefaultPeriod())
    case class Update(namespace: scala.Enumeration#Value, key: String, value: Any)
    case class UpdateByID(namespaceId: Int, key: String, value: Any)
    case class UpdateMany(namespace: scala.Enumeration#Value, updates: Iterable[(String, Any)])
    case class Remove(namespace: scala.Enumeration#Value, key: String)
    case class RemoveByID(namespaceId: Int, key: String)
    case class Clear(namespace: scala.Enumeration#Value)
    case class Reinitialize(context: Context, innerCache: AppCache)
  }
}
