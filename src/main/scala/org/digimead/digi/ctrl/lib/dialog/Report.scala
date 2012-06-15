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

import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.Date

import scala.Option.option2Iterable
import scala.actors.Futures.future

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.DActivity
import org.digimead.digi.ctrl.lib.AnyBase

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap.CompressFormat
import android.graphics.Bitmap.Config
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.Html
import android.view.LayoutInflater
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/*
 * report life cycle available only within main activity
 */
object Report extends Logging {
  val searchAndSubmitLock = new AtomicBoolean(false)
  def getId(context: Context) = Android.getId(context, "report")
  @Loggable
  def createDialog(activity: Activity with DActivity): Dialog = {
    val inflater = LayoutInflater.from(activity)
    val view = inflater.inflate(Android.getId(activity, "report", "layout"), null)
    new AlertDialog.Builder(activity).
      setTitle(Android.getString(activity, "send_report").
        getOrElse("Submit report")).
      setView(view).
      setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() with Logging {
        @Loggable
        def onClick(dialog: DialogInterface, which: Int) = {
          AnyBase.info.get.foreach {
            info =>
              AppComponent.Inner.resetDialogSafe
              future {
                AppComponent.Inner.showDialogSafe[ProgressDialog](activity, getClass.getName, () => ProgressDialog.show(activity, "Please wait...", Html.fromHtml("uploading..."), true))
                var writer: PrintWriter = null
                try {
                  val myUID = android.os.Process.myUid
                  Android.withProcess({
                    case (name, uid, gid, pid, ppid, path) =>
                      val cmdLine = new File(path, "cmdline")
                      if (name == "bridge" && cmdLine.exists && cmdLine.canRead) {
                        try {
                          val cmd = scala.io.Source.fromFile(cmdLine).getLines.mkString.trim
                          if (cmd.startsWith("/data/data/org.digimead.digi.ctrl/files/armeabi/bridge")) {
                            log.debug("found bridge with UID " + uid + " and PID " + pid)
                            if (uid == 0) {
                              log.debug("send INT signal to root bridge with PID " + pid)
                              val p = Runtime.getRuntime.exec(Array("su", "-c", "kill -INT " + pid))
                              p.waitFor
                            } else {
                              log.debug("send INT signal to bridge with PID " + pid)
                              android.os.Process.sendSignal(pid, 2)
                            }
                          }
                        } catch {
                          case e =>
                            log.error(e.getMessage, e)
                        }
                      }
                  })
                  val file = new File(info.reportPathInternal, org.digimead.digi.ctrl.lib.base.Report.reportPrefix + ".description")
                  file.createNewFile()
                  writer = new PrintWriter(file)
                  val time = System.currentTimeMillis
                  val date = Common.dateString(new Date(time))
                  val summary = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
                  val spinner = view.findViewById(android.R.id.text2).asInstanceOf[Spinner]
                  AnyBase.info.get.foreach(writer.println)
                  writer.println("email: " + spinner.getSelectedItem().toString())
                  writer.println("description: " + summary.getText.toString)
                  writer.println("generation time: " + date)
                  writer.println("generation time (long): " + time)
                  writer.println("ps: \n" + (Android.collectCommandOutput("ps") match {
                    case result: Some[_] => result
                    case None => Android.collectCommandOutputWithBusyBox("ps")
                  }))
                  writer.println("\nnetstat: \n" + (Android.collectCommandOutput("netstat") match {
                    case result: Some[_] => result
                    case None => Android.collectCommandOutputWithBusyBox("netstat")
                  }))
                  Thread.sleep(1000)
                } catch {
                  case e =>
                    log.error(e.getMessage, e)
                } finally {
                  if (writer != null)
                    writer.close()
                }
                val i = new AtomicInteger()
                val submitResult = org.digimead.digi.ctrl.lib.base.Report.submit(activity, true, Some((f, n) => {
                  AppComponent.Inner.getDialogSafe(0) match {
                    case Some(dialog) if dialog != null =>
                      activity.runOnUiThread(new Runnable {
                        def run = dialog.asInstanceOf[AlertDialog].setMessage("uploading " + i.incrementAndGet + "/" + n)
                      })
                    case dialog =>
                      log.warn("lost uploading dialog, got " + dialog)
                  }
                }))
                AppComponent.Inner.resetDialogSafe
                searchAndSubmitLock.set(false)
                if (submitResult)
                  org.digimead.digi.ctrl.lib.base.Report.cleanAfterReview()
                else {
                  log.warn("some reports submission failed, cleanAfterReview skipped")
                  activity.runOnUiThread(new Runnable {
                    def run = Toast.makeText(activity, Android.getString(activity, "report_upload_failed").
                      getOrElse("Some of the reports could not be uploaded to the Digimead Error Reporting service. Please try again later."), Toast.LENGTH_LONG).show()
                  })
                }
              }
          }
        }
      }).
      setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() with Logging {
        @Loggable
        def onClick(dialog: DialogInterface, which: Int) = {
          AppComponent.Inner.resetDialogSafe
          searchAndSubmitLock.set(false)
          org.digimead.digi.ctrl.lib.base.Report.cleanAfterReview()
        }
      }).
      create()
  }
  def submit(description: String): Unit = submit(null, Some(description))
  @Loggable
  def submit(activity: Activity with DActivity = null, description: Option[String] = None): Unit = {
    if (activity != null) {
      activity.runOnUiThread(new Runnable { def run = takeScreenshot(activity) })
      description.foreach(description => activity.onPrepareDialogStash(Android.getId(activity, "report")) = description)
      AppComponent.Inner.showDialogSafe(activity, Report.getClass.getName, Android.getId(activity, "report"))
    } else {
      AppComponent.Context.foreach {
        case activity: Activity with DActivity =>
          description.foreach(description => activity.onPrepareDialogStash(Android.getId(activity, "report")) = description)
          AppComponent.Inner.showDialogSafe(activity, Report.getClass.getName, Android.getId(activity, "report"))
        case context =>
          log.fatal("unable to launch report dialog from illegal context")
      }
    }
  }
  @Loggable
  def takeScreenshot(activity: android.app.Activity) = AnyBase.info.get.foreach {
    info =>
      log.debug("taking screenshort of activity")
      var ostream: FileOutputStream = null
      try {
        val content = activity.getWindow.getDecorView.getRootView
        val bitmap = Bitmap.createBitmap(content.getWidth, content.getHeight, Config.ARGB_8888)
        val canvas = new Canvas(bitmap)
        content.draw(canvas)
        val file = new File(info.reportPathInternal, org.digimead.digi.ctrl.lib.base.Report.reportPrefix + ".png")
        file.createNewFile()
        ostream = new FileOutputStream(file)
        bitmap.compress(CompressFormat.PNG, 100, ostream)
        file.setReadable(true, false)
      } catch {
        case e =>
          log.error(e.getMessage, e)
      } finally {
        if (ostream != null)
          ostream.close()
      }
  }
  @Loggable // try to submit reports if there any stack traces
  def searchAndSubmit(activity: Activity with DActivity) = AnyBase.info.get.map {
    info =>
      if (searchAndSubmitLock.compareAndSet(false, true)) {
        Thread.sleep(DTimeout.short) // take it gently ;-)
        log.info("looking for stack trace reports in: " + info.reportPathInternal)
        val reports = Option(info.reportPathInternal.list()).getOrElse(Array[String]())
        if (reports.exists(_.endsWith(org.digimead.digi.ctrl.lib.base.Report.traceFileExtension)))
          submit(activity, Some("stack trace detected"))
      }
  } getOrElse ({ "skip searchAndSubmit - uninitialized AnyBase.info" })
}
