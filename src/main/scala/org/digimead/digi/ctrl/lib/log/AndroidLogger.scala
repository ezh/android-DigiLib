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

package org.digimead.digi.ctrl.lib.log

import java.util.StringTokenizer

import scala.collection.immutable.HashMap

import android.util.Log

object AndroidLogger extends Logger {
  private[lib] var validName = new HashMap[String, String]()
  final val TAG_MAX_LENGTH = 23; // tag names cannot be longer on Android platform
  // see also android/system/core/include/cutils/property.h
  // and android/frameworks/base/core/jni/android_util_Log.cpp
  protected var f = (records: Seq[Logging.Record]) => records.foreach {
    record =>
      val tag = validName.get(record.tag).getOrElse(this.synchronized {
        val valid = forceValidName(record.tag)
        validName = validName + (record.tag -> valid)
        Log.i(getClass.getSimpleName(),
          "Logger name '" + record.tag + "' exceeds maximum length of " + AndroidLogger.TAG_MAX_LENGTH +
            " characters, using '" + valid + "' instead.")
        valid
      })
      record.level match {
        case Logging.Level.Trace =>
          if (record.throwable.isEmpty)
            Log.v(tag, "[T%05d]%s".format(record.tid, record.message))
          else
            Log.v(tag, "[T%05d]%s".format(record.tid, record.message), record.throwable.get)
        case Logging.Level.Debug =>
          if (record.throwable.isEmpty)
            Log.d(tag, "[T%05d]%s".format(record.tid, record.message))
          else
            Log.d(tag, "[T%05d]%s".format(record.tid, record.message), record.throwable.get)
        case Logging.Level.Info =>
          if (record.throwable.isEmpty)
            Log.i(tag, "[T%05d]%s".format(record.tid, record.message))
          else
            Log.i(tag, "[T%05d]%s".format(record.tid, record.message), record.throwable.get)
        case Logging.Level.Warn =>
          if (record.throwable.isEmpty)
            Log.w(tag, "[T%05d]%s".format(record.tid, record.message))
          else
            Log.w(tag, "[T%05d]%s".format(record.tid, record.message), record.throwable.get)
        case Logging.Level.Error =>
          if (record.throwable.isEmpty)
            Log.e(tag, "[T%05d]%s".format(record.tid, record.message))
          else
            Log.e(tag, "[T%05d]%s".format(record.tid, record.message), record.throwable.get)
      }
  }
  /**
   * Trim name in case it exceeds maximum length of {@value #TAG_MAX_LENGTH} characters.
   */
  private def forceValidName(_name: String): String = {
    var name = _name
    if (name != null && name.length() > AndroidLogger.TAG_MAX_LENGTH) {
      val st = new StringTokenizer(name, ".")
      if (st.hasMoreTokens()) { // note that empty tokens are skipped, i.e., "aa..bb" has tokens "aa", "bb"
        val sb = new StringBuilder()
        var token: String = ""
        do {
          token = st.nextToken();
          if (token.length() == 1) { // token of one character appended as is
            sb.append(token)
            sb.append('.')
          } else if (st.hasMoreTokens()) { // truncate all but the last token
            sb.append(token.charAt(0))
            sb.append("*.")
          } else { // last token (usually class name) appended as is
            sb.append(token)
          }
        } while (st.hasMoreTokens())
        name = sb.toString()
      }
      // Either we had no useful dot location at all or name still too long.
      // Take leading part and append '*' to indicate that it was truncated
      if (name.length() > AndroidLogger.TAG_MAX_LENGTH)
        name = name.substring(0, AndroidLogger.TAG_MAX_LENGTH - 1) + '*'
    }
    name
  }
}
