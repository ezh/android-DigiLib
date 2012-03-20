/*
* Copyright (c) 2012 Alexey Aksenov ezh@ezh.msk.ru
* 
* this file based on AndroidLogger.java
* 
* Copyright (c) 2009 SLF4J.ORG
*
* All rights reserved.
*
* Permission is hereby granted, free of charge, to any person obtaining
* a copy of this software and associated documentation files (the
* "Software"), to deal in the Software without restriction, including
* without limitation the rights to use, copy, modify, merge, publish,
* distribute, sublicense, and/or sell copies of the Software, and to
* permit persons to whom the Software is furnished to do so, subject to
* the following conditions:
*
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
* LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
* OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
* WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package org.digimead.digi.ctrl.lib.log

import android.util.Log
import java.util.StringTokenizer
import org.slf4j.helpers.MarkerIgnoringBase

class AndroidLogger(val rawLoggerName: String) extends MarkerIgnoringBase {
  val actualName = forceValidName(rawLoggerName)
  if (!actualName.equals(rawLoggerName))
    Log.i(getClass.getSimpleName(),
      "Logger name '" + rawLoggerName + "' exceeds maximum length of " + AndroidLogger.TAG_MAX_LENGTH +
        " characters, using '" + actualName + "' instead.")

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

  /* @see org.slf4j.Logger#isTraceEnabled() */
  def isTraceEnabled(): Boolean = Log.isLoggable(rawLoggerName, Log.VERBOSE)

  /* @see org.slf4j.Logger#trace(java.lang.String) */
  def trace(msg: String) = Log.v(actualName, msg)

  /* @see org.slf4j.Logger#trace(java.lang.String, java.lang.Object) */
  def trace(format: String, param1: AnyRef) = Log.v(actualName, format.format(param1))

  /* @see org.slf4j.Logger#trace(java.lang.String, java.lang.Object, java.lang.Object) */
  def trace(format: String, param1: AnyRef, param2: AnyRef) = Log.v(actualName, format.format(param1, param2))

  /* @see org.slf4j.Logger#trace(java.lang.String, java.lang.Object[]) */
  def trace(format: String, argArray: Array[AnyRef]) = Log.v(actualName, format.format(argArray: _*))

  /* @see org.slf4j.Logger#trace(java.lang.String, java.lang.Throwable) */
  def trace(msg: String, t: Throwable) = Log.v(actualName, msg, t)

  /* @see org.slf4j.Logger#isDebugEnabled() */
  def isDebugEnabled(): Boolean = Log.isLoggable(rawLoggerName, Log.DEBUG)

  /* @see org.slf4j.Logger#debug(java.lang.String) */
  def debug(msg: String) = Log.d(actualName, msg)

  /* @see org.slf4j.Logger#debug(java.lang.String, java.lang.Object) */
  def debug(format: String, param1: AnyRef) = Log.d(actualName, format.format(param1))

  /* @see org.slf4j.Logger#debug(java.lang.String, java.lang.Object, java.lang.Object) */
  def debug(format: String, param1: AnyRef, param2: AnyRef) = Log.d(actualName, format.format(param1, param2))

  /* @see org.slf4j.Logger#debug(java.lang.String, java.lang.Object[]) */
  def debug(format: String, argArray: Array[AnyRef]) = Log.d(actualName, format.format(argArray: _*))

  /* @see org.slf4j.Logger#debug(java.lang.String, java.lang.Throwable) */
  def debug(msg: String, t: Throwable) = Log.d(actualName, msg, t)

  /* @see org.slf4j.Logger#isInfoEnabled() */
  def isInfoEnabled(): Boolean = Log.isLoggable(rawLoggerName, Log.INFO)

  /* @see org.slf4j.Logger#info(java.lang.String) */
  def info(msg: String) = Log.i(actualName, msg)

  /* @see org.slf4j.Logger#info(java.lang.String, java.lang.Object) */
  def info(format: String, param1: AnyRef) = Log.i(actualName, format.format(param1))

  /* @see org.slf4j.Logger#info(java.lang.String, java.lang.Object, java.lang.Object) */
  def info(format: String, param1: AnyRef, param2: AnyRef) = Log.i(actualName, format.format(param1, param2))

  /* @see org.slf4j.Logger#info(java.lang.String, java.lang.Object[]) */
  def info(format: String, argArray: Array[AnyRef]) = Log.i(actualName, format.format(argArray: _*))

  /* @see org.slf4j.Logger#info(java.lang.String, java.lang.Throwable) */
  def info(msg: String, t: Throwable) = Log.i(actualName, msg, t)

  /* @see org.slf4j.Logger#isWarnEnabled() */
  def isWarnEnabled(): Boolean = Log.isLoggable(rawLoggerName, Log.WARN)

  /* @see org.slf4j.Logger#warn(java.lang.String) */
  def warn(msg: String) = Log.w(actualName, msg)

  /* @see org.slf4j.Logger#warn(java.lang.String, java.lang.Object) */
  def warn(format: String, param1: AnyRef) = Log.w(actualName, format.format(param1))

  /* @see org.slf4j.Logger#warn(java.lang.String, java.lang.Object, java.lang.Object) */
  def warn(format: String, param1: AnyRef, param2: AnyRef) = Log.w(actualName, format.format(param1, param2))

  /* @see org.slf4j.Logger#warn(java.lang.String, java.lang.Object[]) */
  def warn(format: String, argArray: Array[AnyRef]) = Log.w(actualName, format.format(argArray: _*))

  /* @see org.slf4j.Logger#warn(java.lang.String, java.lang.Throwable) */
  def warn(msg: String, t: Throwable) = Log.w(actualName, msg, t)

  /* @see org.slf4j.Logger#isErrorEnabled() */
  def isErrorEnabled(): Boolean = Log.isLoggable(rawLoggerName, Log.ERROR)

  /* @see org.slf4j.Logger#error(java.lang.String) */
  def error(msg: String) = Log.e(actualName, msg)

  /* @see org.slf4j.Logger#error(java.lang.String, java.lang.Object) */
  def error(format: String, param1: AnyRef) = Log.e(actualName, format.format(param1))

  /* @see org.slf4j.Logger#error(java.lang.String, java.lang.Object, java.lang.Object) */
  def error(format: String, param1: AnyRef, param2: AnyRef) = Log.e(actualName, format.format(param1, param2))

  /* @see org.slf4j.Logger#error(java.lang.String, java.lang.Object[]) */
  def error(format: String, argArray: Array[AnyRef]) = Log.e(actualName, format.format(argArray: _*))

  /* @see org.slf4j.Logger#error(java.lang.String, java.lang.Throwable) */
  def error(msg: String, t: Throwable) = Log.e(actualName, msg, t)
}

object AndroidLogger extends Logger {
  final val TAG_MAX_LENGTH = 23; // tag names cannot be longer on Android platform
  // see also android/system/core/include/cutils/property.h
  // and android/frameworks/base/core/jni/android_util_Log.cpp
  def apply(record: Logging.Record) {
    
  }
}
