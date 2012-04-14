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

package org.digimead.digi.ctrl.lib.util

import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.Context
import android.os.Build
import android.view.Surface

object Android extends Logging {
  def getString(context: WeakReference[Context], name: String): Option[String] =
    context.get.flatMap(ctx => getString(ctx, name))
  def getString(context: Context, name: String): Option[String] =
    getId(context, name, "string") match {
      case 0 =>
        None
      case id =>
        Option(context.getString(id))
    }
  def getString(context: WeakReference[Context], name: String, formatArgs: AnyRef*): Option[String] =
    context.get.flatMap(ctx => getString(ctx, name, formatArgs: _*))
  def getString(context: Context, name: String, formatArgs: AnyRef*): Option[String] =
    getId(context, name, "string") match {
      case 0 =>
        None
      case id =>
        Option(context.getString(id, formatArgs: _*))
    }
  def getCapitalized(context: WeakReference[Context], name: String): Option[String] =
    context.get.flatMap(ctx => getCapitalized(ctx, name))
  def getCapitalized(context: Context, name: String) =
    getString(context, name).map(s => if (s.length > 1)
      s(0).toUpper + s.substring(1)
    else
      s.toUpperCase)
  def getCapitalized(context: WeakReference[Context], name: String, formatArgs: AnyRef*): Option[String] =
    context.get.flatMap(ctx => getCapitalized(ctx, name, formatArgs: _*))
  def getCapitalized(context: Context, name: String, formatArgs: AnyRef*) =
    getString(context, name, formatArgs: _*).map(s => if (s.length > 1)
      s(0).toUpper + s.substring(1)
    else
      s.toUpperCase)
  def getId(context: WeakReference[Context], name: String): Int =
    getId(context, name, "id")
  def getId(context: WeakReference[Context], name: String, scope: String): Int =
    context.get.map(ctx => getId(ctx, name, scope)).getOrElse(0)
  def getId(context: Context, name: String): Int =
    getId(context, name, "id")
  def getId(context: Context, name: String, scope: String): Int =
    context.getResources().getIdentifier(name, scope, context.getPackageName())
  @Loggable
  def disableRotation(activity: Activity) {
    val orientation = activity.getResources().getConfiguration().orientation
    val rotation = activity.getWindowManager().getDefaultDisplay().getOrientation()
    // Copied from Android docs, since we don't have these values in Froyo 2.2
    var SCREEN_ORIENTATION_REVERSE_LANDSCAPE = 8
    var SCREEN_ORIENTATION_REVERSE_PORTRAIT = 9
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.FROYO) {
      SCREEN_ORIENTATION_REVERSE_LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
      SCREEN_ORIENTATION_REVERSE_PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90) {
      if (orientation == Configuration.ORIENTATION_PORTRAIT)
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
      else if (orientation == Configuration.ORIENTATION_LANDSCAPE)
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    } else if (rotation == Surface.ROTATION_180 || rotation == Surface.ROTATION_270) {
      if (orientation == Configuration.ORIENTATION_PORTRAIT)
        activity.setRequestedOrientation(SCREEN_ORIENTATION_REVERSE_PORTRAIT)
      else if (orientation == Configuration.ORIENTATION_LANDSCAPE)
        activity.setRequestedOrientation(SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
    }
  }
  @Loggable
  def enableRotation(activity: Activity) =
    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
}
