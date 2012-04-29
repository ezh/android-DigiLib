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

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

import scala.Array.canBuildFrom
import scala.ref.WeakReference
import scala.util.control.ControlThrowable

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.Context
import android.os.Build
import android.view.Surface
import android.view.Display
import android.view.WindowManager

object Android extends Logging {
  @volatile private var busybox: Option[File] = null
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
  def getScreenOrientation(display: Display): Int = if (display.getWidth() == display.getHeight()) {
    log.debug("get screen ORIENTATION_SQUARE/" + Configuration.ORIENTATION_SQUARE)
    Configuration.ORIENTATION_SQUARE
  } else { //if width is less than height than it is portrait
    if (display.getWidth() < display.getHeight()) {
      log.debug("get screen ORIENTATION_PORTRAIT/" + Configuration.ORIENTATION_PORTRAIT)
      Configuration.ORIENTATION_PORTRAIT
    } else { // if it is not any of the above it will definitely be landscape
      log.debug("get screen ORIENTATION_LANDSCAPE/" + Configuration.ORIENTATION_LANDSCAPE)
      Configuration.ORIENTATION_LANDSCAPE
    }
  }
  @Loggable
  def disableRotation(activity: Activity) {
    val display = activity.getWindowManager.getDefaultDisplay()
    val orientation = getScreenOrientation(display)
    val rotation = display.getRotation();

    // Copied from Android docs, since we don't have these values in Froyo 2.2
    var SCREEN_ORIENTATION_REVERSE_LANDSCAPE = 8
    var SCREEN_ORIENTATION_REVERSE_PORTRAIT = 9
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.FROYO) {
      SCREEN_ORIENTATION_REVERSE_LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
      SCREEN_ORIENTATION_REVERSE_PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    orientation match {
      case Configuration.ORIENTATION_PORTRAIT =>
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.FROYO) {
          activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        } else {
          if (rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_180) {
            log.debug("lock orientation REVERSE_PORTRAIT with r:" + rotation + " and ORIENTATION_REVERSE_PORTRAIT")
            activity.setRequestedOrientation(SCREEN_ORIENTATION_REVERSE_PORTRAIT)
          } else {
            log.debug("lock orientation REVERSE_PORTRAIT with r:" + rotation + " and ORIENTATION_PORTRAIT")
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
          }
        }
      case Configuration.ORIENTATION_LANDSCAPE =>
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.FROYO) {
          activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        } else {
          if (rotation == android.view.Surface.ROTATION_0 || rotation == android.view.Surface.ROTATION_90) {
            log.debug("lock orientation LANDSCAPE with r:" + rotation + " and ORIENTATION_LANDSCAPE")
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
          } else {
            log.debug("lock orientation LANDSCAPE with r:" + rotation + " and ORIENTATION_REVERSE_LANDSCAPE")
            activity.setRequestedOrientation(SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
          }
        }
      case n =>
        log.warn("don't know what to do with orientation " + n)
    }
  }
  @Loggable
  def enableRotation(activity: Activity) =
    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR)
  @Loggable
  def findBusyBox(): Option[File] = {
    val names = Seq("busybox", "toolbox")
    if (busybox != null)
      busybox
    for (name <- names) {
      var f: File = null
      f = new File("/sbin/ext/" + name)
      if (f.exists) {
        busybox = Some(f)
        return busybox
      }
      f = new File("/system/bin/" + name)
      if (f.exists) {
        busybox = Some(f)
        return busybox
      }
      f = new File("/system/xbin/" + name)
      if (f.exists) {
        busybox = Some(f)
        return busybox
      }
      f = new File("/bin/" + name)
      if (f.exists) {
        busybox = Some(f)
        return busybox
      }
      f = new File("/sbin/" + name)
      if (f.exists) {
        busybox = Some(f)
        return busybox
      }
      f = new File("/xbin/" + name)
      if (f.exists) {
        busybox = Some(f)
        return busybox
      }
    }
    busybox = None
    busybox
  }
  @Loggable
  def execChmod(permission: Int, file: File, recursive: Boolean = false): Boolean = {
    val busybox = findBusyBox
    if (busybox == None)
      return false
    val args = if (recursive)
      Array(busybox.get.getAbsolutePath, "chmod", "-R", permission.toString, file.getAbsolutePath)
    else
      Array(busybox.get.getAbsolutePath, "chmod", permission.toString, file.getAbsolutePath)
    log.debug(args.tail.mkString(" "))
    val p = Runtime.getRuntime().exec(args)
    val err = new BufferedReader(new InputStreamReader(p.getErrorStream))
    p.waitFor()
    val retcode = p.exitValue()
    if (retcode != 0) {
      var error = err.readLine()
      while (error != null) {
        throw new IOException("chmod '" + args.mkString(" ") + "' error: " + error)
        error = err.readLine()
      }
      false
    } else
      true
  }
  // _NAME IN UPPER CASE_, UID, GID, PID, PPID, Path
  @Loggable
  def withProcess(func: (String, Int, Int, Int, Int, File) => Unit): Boolean = try {
    new File("/proc").listFiles.foreach(file => {
      val status = new File(file, "status")
      if (file.isDirectory && status.exists && status.canRead) {
        var name: Option[String] = None
        var uid: Option[Int] = None
        var gid: Option[Int] = None
        var pid: Option[Int] = None
        var ppid: Option[Int] = None
        try {
          scala.io.Source.fromFile(status).getLines.foreach(l => l.trim.toUpperCase.split("""[:\s]+""") match {
            case Array("NAME", n) =>
              name = Some(l.trim.split("""[:\s]+""").last)
            case Array("PID", n) =>
              pid = Some(n.toInt)
            case Array("PPID", n) =>
              ppid = Some(n.toInt)
            case Array("UID", n1, n2, n3, n4) =>
              uid = Some(n1.toInt)
            case Array("GID", n1, n2, n3, n4) =>
              gid = Some(n1.toInt)
            case _ =>
          })
        } catch {
          case e =>
            log.warn(e.getMessage)
        }
        for {
          name <- name
          uid <- uid
          gid <- gid
          pid <- pid
          ppid <- ppid
        } func(name, uid, gid, pid, ppid, file)
      }
    })
    true
  } catch {
    case ce: ControlThrowable => throw ce // propagate
    case e =>
      log.error(e.getMessage, e)
      false
  }
  @Loggable(result = false)
  def collectCommandOutput(commandArgs: String*): Option[String] = try {
    var buffer: Seq[String] = Seq()
    val args = commandArgs.toArray
    log.debug("process arguments: " + args.mkString(" "))
    val p = Runtime.getRuntime().exec(args)
    val out = new BufferedReader(new InputStreamReader(p.getInputStream))
    val err = new BufferedReader(new InputStreamReader(p.getErrorStream))
    // read output
    var output = out.readLine()
    while (output != null) {
      buffer = buffer :+ output
      output = out.readLine()
    }
    p.waitFor()
    val retcode = p.exitValue()
    if (retcode != 0) {
      var error = err.readLine()
      while (error != null) {
        throw new IOException("ls '" + args.mkString(" ") + "' error: " + error)
        error = err.readLine()
      }
      return None
    }
    Some(buffer.mkString("\n"))
  } catch {
    case ce: ControlThrowable => throw ce // propagate
    case e =>
      log.error(e.getMessage, e)
      None
  }
  @Loggable(result = false)
  def collectCommandOutputWithBusyBox(commandArgs: String*): Option[String] = {
    var buffer: Seq[String] = Seq()
    val busybox = findBusyBox
    if (busybox == None)
      return None
    val args = Array(busybox.get.getAbsolutePath) ++ commandArgs
    collectCommandOutput(args: _*)
  }
}
