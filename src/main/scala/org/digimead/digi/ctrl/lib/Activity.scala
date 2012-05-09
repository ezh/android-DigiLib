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

import scala.Array.canBuildFrom
import scala.collection.JavaConversions._
import scala.collection.mutable.HashSet
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.SynchronizedSet
import scala.collection.mutable.HashMap

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.dialog.Report
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common

import android.accounts.AccountManager
import android.app.{ Activity => AActivity }
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView

/*
 * trait hasn't ability to use @Loggable
 */
trait Activity extends AActivity with AnyBase with Logging {
  implicit val dispatcher: Dispatcher
  val onPrepareDialogStash = new HashMap[Int, Any]() with SynchronizedMap[Int, Any]
  /*
   * sometimes in life cycle onCreate stage invoked without onDestroy stage
   */
  override def onCreate(savedInstanceState: Bundle): Unit = {
    log.trace("Activity::onCreate")
    onCreateBase(this, { Activity.super.onCreate(savedInstanceState) })
    // sometimes onDestroy skipped, there is no harm to drop garbage
    Activity.registeredReceivers.clear
    Activity.activeReceivers.clear
  }
  override def onResume() = {
    log.trace("Activity::onResume")
    Report.searchAndSubmitLock.set(false)
    AppComponent.Inner.lockRotationCounter.set(0)
    AppComponent.Inner.resetDialogSafe
    Activity.registeredReceivers.foreach(t => {
      if (Activity.activeReceivers(t._1)) {
        log.trace("onResume skip registerReceiver " + t._1)
      } else {
        log.trace("onResume registerReceiver " + t._1)
        Activity.activeReceivers(t._1) = true
        super.registerReceiver(t._1, t._2._1, t._2._2, t._2._3)
      }
    })
    super.onResume()
  }
  override def onPause() {
    log.trace("Activity::onPause")
    Activity.registeredReceivers.foreach(t => {
      if (Activity.activeReceivers(t._1)) {
        log.trace("onResume unregisterReceiver " + t._1)
        Activity.activeReceivers(t._1) = false
        try {
          super.unregisterReceiver(t._1)
        } catch {
          case e =>
            log.error(e.getMessage)
        }
      } else {
        log.trace("onPause skip unregisterReceiver " + t._1)
      }
    })
    if (AppComponent.Inner != null) {
      AppComponent.Inner.lockRotationCounter.set(0)
      AppComponent.Inner.disableSafeDialogs
      AppComponent.Inner.resetDialogSafe
    }
    Android.enableRotation(this)
    super.onPause()
  }
  /*
   * sometimes in life cycle onCreate stage invoked without onDestroy stage
   * in fact AppComponent.deinit is a sporadic event
   */
  override def onDestroy() = {
    log.trace("Activity::onDestroy")
    Activity.registeredReceivers.clear
    Activity.activeReceivers.clear
    super.onDestroy()
    onDestroyBase(this, {
      if (AnyBase.isLastContext)
        AppComponent.deinit()
      else
        log.debug("skip onDestroy deinitialization, because there is another context coexists")
      Activity.super.onDestroy()
    })
  }
  override def onCreateDialog(id: Int, args: Bundle): Dialog = {
    log.trace("Activity::onCreateDialog")
    id match {
      case id if id == Report.getId(this) =>
        log.debug("show Report dialog")
        Report.createDialog(this)
      case id =>
        Option(Common.onCreateDialog(id, this)(log, dispatcher)).foreach(dialog => return dialog)
        super.onCreateDialog(id)
    }
  }
  override def onPrepareDialog(id: Int, dialog: Dialog, args: Bundle): Unit = {
    super.onPrepareDialog(id, dialog, args)
    log.trace("Activity::onPrepareDialog")
    AppComponent.Inner.setDialogSafe(dialog)
    id match {
      case id if id == Report.getId(this) =>
        log.debug("prepare Report dialog with id " + id)
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
      case _ =>
    }
  }
  override def registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter): Intent = try {
    log.trace("Activity::registerReceiver " + receiver)
    assert(!Activity.registeredReceivers.isDefinedAt(receiver),
      { "receiver " + receiver + " already registered" })
    Activity.registeredReceivers(receiver) = (filter, null, null)
    Activity.activeReceivers(receiver) = true
    super.registerReceiver(receiver, filter)
  } catch {
    case e =>
      log.error(e.getMessage, e)
      null
  }
  override def registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter, broadcastPermission: String, scheduler: Handler): Intent = try {
    log.trace("Activity::registerReceiver " + receiver)
    assert(!Activity.registeredReceivers.isDefinedAt(receiver),
      { "receiver " + receiver + " not found" })
    Activity.registeredReceivers(receiver) = (filter, broadcastPermission, scheduler)
    Activity.activeReceivers(receiver) = true
    super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
  } catch {
    case e =>
      log.error(e.getMessage, e)
      null
  }
  override def unregisterReceiver(receiver: BroadcastReceiver) {
    log.trace("Activity::unregisterReceiver " + receiver)
    Activity.registeredReceivers.remove(receiver)
    if (Activity.activeReceivers.remove(receiver))
      super.unregisterReceiver(receiver)
  }
}

object Activity extends Logging {
  /** BroadcastReceiver that recorded at registerReceiver/unregisterReceiver */
  private val registeredReceivers = new HashMap[BroadcastReceiver, (IntentFilter, String, Handler)] with SynchronizedMap[BroadcastReceiver, (IntentFilter, String, Handler)]
  private val activeReceivers = new HashSet[BroadcastReceiver] with SynchronizedSet[BroadcastReceiver]
  log.debug("alive")
}
