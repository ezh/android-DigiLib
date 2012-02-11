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
package org.digimead.digi.inetd.lib

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import scala.actors.Actor
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap
import scala.ref.SoftReference

import org.digimead.digi.inetd.lib.aop.Loggable
import org.slf4j.LoggerFactory

import android.content.Context

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
  def clear(namespace: scala.Enumeration#Value): Unit = {}
}
class AppCache extends AppCacheT[String, Any] {
  private val log = LoggerFactory.getLogger(getClass.getName().replaceFirst("org.digimead.digi.inetd", "o.d.d.i"))
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
                None
            }
        } else
          None
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
}

object AppCache extends Actor {
  private val log = LoggerFactory.getLogger(getClass.getName().replaceFirst("org.digimead.digi.inetd", "o.d.d.i"))
  private var contextPackageName = ""
  private var inner: AppCacheT[String, Any] = null
  private var period: Long = 1000 * 60 * 10 // 10 minutes
  private var cacheClass = "org.digimead.digi.inetd.lib.AppCache"
  private var cachePath = "."
  private lazy val cacheFolder = new File(cachePath)
  // key -> (timestamp, data)
  private val map = new HashMap[String, SoftReference[(Long, Any)]]() with SynchronizedMap[String, SoftReference[(Long, Any)]]
  log.debug("alive")
  def act = {
    loop {
      react {
        case Message.Get(namespace, key, period) =>
          reply(inner.get(namespace, key, period))
        case Message.GetByID(namespaceID, key, period) =>
          reply(inner.get(namespaceID, key, period))
        case Message.Update(namespace, key, value) =>
          reply(inner.update(namespace, key, value))
        case Message.UpdateByID(namespaceID, key, value) =>
          reply(inner.update(namespaceID, key, value))
        case Message.UpdateMany(namespace, updates) =>
          reply(inner.update(namespace, updates))
        case Message.Clear(namespace) =>
          reply(inner.clear(namespace))
        case unknown =>
          log.error("unknown message " + unknown)
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
  def init(context: Context, innerCache: AppCache = null) = synchronized {
    contextPackageName = context.getPackageName()
    log.info("initialize AppCache for " + contextPackageName)
    assert(inner == null)
    val pref = context.getSharedPreferences(Common.Preference.main, Context.MODE_PRIVATE)
    period = pref.getLong(Common.Option.cache_period.res, period)
    cachePath = pref.getString(Common.Option.cache_folder.res, context.getCacheDir + "/")
    cacheClass = pref.getString(Common.Option.cache_class.res, "org.digimead.digi.inetd.lib.AppCache")
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
        log.error("cannot create directory: " + cacheFolder)
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
    case class Clear(namespace: scala.Enumeration#Value)
  }
}
