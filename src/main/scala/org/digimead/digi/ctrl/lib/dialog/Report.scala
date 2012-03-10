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
import java.util.concurrent.atomic.AtomicInteger
import java.util.Date

import scala.actors.Futures.future

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.Activity
import org.digimead.digi.ctrl.lib.AnyBase

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

/*
 * report life cycle available only within main activity
 */
object Report extends Logging {
  def getId(context: Context) = Android.getId(context, "report")
  @Loggable
  def createDialog(activity: Activity): Dialog = {
    val inflater = LayoutInflater.from(activity)
    val view = inflater.inflate(Android.getId(activity, "report", "layout"), null)
    new AlertDialog.Builder(activity).
      setTitle(Android.getString(activity, "send_report").
        getOrElse("Submit report")).
      setView(view).
      setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() with Logging {
        @Loggable
        def onClick(dialog: DialogInterface, which: Int) = activity.activityDialog.synchronized {
          AnyBase.info.get.foreach {
            info =>
              activity.activityDialog.dismiss
              future {
                activity.showDialogSafe[ProgressDialog](() => ProgressDialog.show(activity, "Please wait...", Html.fromHtml("uploading..."), true))
                var writer: PrintWriter = null
                try {
                  val file = new File(info.reportPath, org.digimead.digi.ctrl.lib.base.Report.reportPrefix + ".description")
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
                } catch {
                  case e =>
                    log.error(e.getMessage, e)
                } finally {
                  if (writer != null)
                    writer.close()
                }
                val i = new AtomicInteger()
                org.digimead.digi.ctrl.lib.base.Report.submit(activity, true, Some((f, n) => {
                  activity.activityDialog.get match {
                    case dialog: ProgressDialog =>
                      activity.runOnUiThread(new Runnable {
                        def run = dialog.setMessage("uploading " + i.incrementAndGet + "/" + n)
                      })
                    case null =>
                  }
                }))
                activity.activityDialog.dismiss
                org.digimead.digi.ctrl.lib.base.Report.clean()
              }
          }
        }
      }).
      setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() with Logging {
        @Loggable
        def onClick(dialog: DialogInterface, which: Int) = {
          activity.activityDialog.dismiss
          org.digimead.digi.ctrl.lib.base.Report.clean()
        }
      }).
      create()
  }
  def submit(description: String): Unit = submit(null, Some(description))
  @Loggable
  def submit(userActivity: android.app.Activity = null, description: Option[String] = None): Unit = AppActivity.Context.foreach {
    case activity: Activity =>
      if (userActivity != null)
        takeScreenshot(userActivity)
      description.foreach(description => activity.onPrepareDialogStash(Android.getId(activity, "report")) = description)
      activity.showDialogSafe(Android.getId(activity, "report"))
    case context =>
      log.fatal("unable to launch report dialog from illegal context")
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
        val file = new File(info.reportPath, org.digimead.digi.ctrl.lib.base.Report.reportPrefix + ".png")
        file.createNewFile()
        ostream = new FileOutputStream(file)
        bitmap.compress(CompressFormat.PNG, 100, ostream)
      } catch {
        case e =>
          log.error(e.getMessage, e)
      } finally {
        if (ostream != null)
          ostream.close()
      }
  }
}
