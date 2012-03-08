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

package org.digimead.digi.ctrl.lib

import java.util.concurrent.atomic.AtomicReference

import scala.Array.canBuildFrom
import scala.actors.Futures.future
import scala.collection.JavaConversions._
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.dialog.Report

import android.accounts.AccountManager
import android.app.{Activity => AActivity}
import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView

trait Activity extends AActivity with AnyBase with Logging {
  protected[lib] val activityDialog = new AtomicReference[Dialog](null)
  val onPrepareDialogStash = new HashMap[Int, Any]() with SynchronizedMap[Int, Any]
  @Loggable
  override def onCreate(savedInstanceState: Bundle): Unit =
    onCreateBase(this, { Activity.super.onCreate(savedInstanceState) })
  @Loggable
  override def onDestroy() = {
    AppActivity.deinit()
    super.onDestroy()
  }
  @Loggable
  def showDialogSafe(id: Int) = future {
    activityDialog.synchronized {
      while (activityDialog.get != null)
        activityDialog.wait
      runOnUiThread(new Runnable { def run = showDialog(id) })
    }
  }
  @Loggable
  override def onCreateDialog(id: Int, data: Bundle): Dialog = id match {
    case id if id == Report.getId(this) =>
      Report.createDialog(this)
    case id =>
      log.error("unknown dialog id " + id)
      null
  }
  @Loggable
  override def onPrepareDialog(id: Int, dialog: Dialog): Unit = activityDialog.synchronized {
    activityDialog.set(dialog)
    id match {
      case id if id == Report.getId(this) =>
        val summary = dialog.findViewById(android.R.id.text1).asInstanceOf[TextView]
        onPrepareDialogStash.remove(id) match {
          case Some(stash) =>
            summary.getRootView.post(new Runnable { def run = summary.setText(stash.asInstanceOf[String]) })
          case None =>
            summary.getRootView.post(new Runnable { def run = summary.setText("") })
        }
        val spinner = dialog.findViewById(android.R.id.text2).asInstanceOf[Spinner]
        val emails = AccountManager.get(this).getAccounts().map(_.name).filter(_.contains('@')).toList :+ "none"
        val adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, emails)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.setAdapter(adapter)
      case id =>
        log.error("unknown dialog id " + id)
    }
  }
}
