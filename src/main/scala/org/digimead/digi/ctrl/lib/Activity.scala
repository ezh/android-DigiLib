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

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import scala.Array.canBuildFrom
import scala.actors.Futures.future
import scala.collection.JavaConversions._
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap
import scala.concurrent.SyncVar

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.dialog.Report

import android.accounts.AccountManager
import android.app.{ Activity => AActivity }
import android.app.{ Dialog => ADialog }
import android.content.BroadcastReceiver
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import declaration.DTimeout

/*
 * trait hasn't ability to use @Loggable
 */
trait Activity extends AActivity with AnyBase with Logging {
  protected[lib] val activityDialog = new Activity.Dialog
  lazy val uiThreadID: Long = {
    val id = new SyncVar[Long]()
    runOnUiThread(new Runnable {
      def run = id.set(Thread.currentThread.getId)
    })
    id.get
  }
  val onPrepareDialogStash = new HashMap[Int, Any]() with SynchronizedMap[Int, Any]
  /*
   * sometimes in life cycle onCreate stage invoked without onDestroy stage
   */
  override def onCreate(savedInstanceState: Bundle): Unit = {
    log.trace("Activity::onCreate")
    onCreateBase(this, { Activity.super.onCreate(savedInstanceState) })
    activityDialog.set(new ADialog(this)) // lock
  }
  override def onResume() = {
    log.trace("Activity::onResume")
    Activity.registeredReveivers.foreach(t => super.registerReceiver(t._1, t._2._1, t._2._2, t._2._3))
    activityDialog.set(null) // unlock
    super.onResume()
  }
  override def onPause() {
    log.trace("Activity::onPause")
    activityDialog.set(null) // unlock
    Activity.registeredReveivers.keys.foreach(super.unregisterReceiver(_))
    super.onPause()
  }
  /*
   * sometimes in life cycle onCreate stage invoked without onDestroy stage
   * in fact AppActivity.deinit is sporadic event
   */
  override def onDestroy() = {
    log.trace("Activity::onDestroy")
    AppActivity.deinit()
    super.onDestroy()
  }
  def showDialogSafe(id: Int) = future {
    try {
      log.trace("Activity::showDialogSafe")
      activityDialog.synchronized {
        while (activityDialog.get != null)
          activityDialog.wait
        activityDialog.unset
        log.debug("show dialog " + id)
        runOnUiThread(new Runnable { def run = showDialog(id) })
      }
    } catch {
      case e =>
        log.error(e.getMessage, e)
        None
    }
  }
  def showDialogSafe[T <% ADialog](dialog: () => T): Option[T] = activityDialog.synchronized {
    try {
      log.trace("Activity::showDialogSafe")
      assert(uiThreadID != Thread.currentThread.getId)
      while (activityDialog.get != null)
        activityDialog.wait
      activityDialog.unset
      this.runOnUiThread(new Runnable { def run = activityDialog.set(dialog()) })
      activityDialog.get.asInstanceOf[T] match {
        case null =>
          None
        case d =>
          Some(d)
      }
    } catch {
      case e =>
        log.error(e.getMessage, e)
        None
    }
  }
  override def onCreateDialog(id: Int, data: Bundle): ADialog = {
    log.trace("Activity::onCreateDialog")
    id match {
      case id if id == Report.getId(this) =>
        Report.createDialog(this)
      case id =>
        log.error("unknown dialog id " + id)
        super.onCreateDialog(id, data)
    }
  }
  override def onPrepareDialog(id: Int, dialog: ADialog): Unit = activityDialog.synchronized {
    log.trace("Activity::onPrepareDialog")
    super.onPrepareDialog(id, dialog)
    activityDialog.set(dialog)
    id match {
      case id if id == Report.getId(this) =>
        log.debug("prepare Report dialog")
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
    log.trace("Activity::registerReceiver")
    assert(!Activity.registeredReveivers.isDefinedAt(receiver))
    Activity.registeredReveivers(receiver) = (filter, null, null)
    super.registerReceiver(receiver, filter)
  } catch {
    case e =>
      log.error(e.getMessage, e)
      null
  }
  override def registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter, broadcastPermission: String, scheduler: Handler): Intent = try {
    log.trace("Activity::registerReceiver")
    assert(!Activity.registeredReveivers.isDefinedAt(receiver))
    Activity.registeredReveivers(receiver) = (filter, broadcastPermission, scheduler)
    super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
  } catch {
    case e =>
      log.error(e.getMessage, e)
      null
  }
  override def unregisterReceiver(receiver: BroadcastReceiver) {
    log.trace("Activity::unregisterReceiver")
    Activity.registeredReveivers.remove(receiver)
    super.unregisterReceiver(receiver)
  }
}

object Activity {
  /** BroadcastReceiver that recorded at registerReceiver/unregisterReceiver */
  private val registeredReveivers = new HashMap[BroadcastReceiver, (IntentFilter, String, Handler)] with SynchronizedMap[BroadcastReceiver, (IntentFilter, String, Handler)]
  class Dialog extends SyncVar[ADialog]() with Logging {
    private var activityDialogGuard: ScheduledExecutorService = null
    super.set(null)
    @Loggable
    override def set(d: ADialog) = synchronized {
      if (activityDialogGuard != null) {
        activityDialogGuard.shutdownNow
        activityDialogGuard = null
      }
      if (d != null) {
        log.info("show safe dialog " + d.getClass.getName)
        activityDialogGuard = Executors.newSingleThreadScheduledExecutor()
        activityDialogGuard.schedule(new Runnable {
          def run() = {
            log.fatal("dismiss stalled dialog " + d.getClass.getName)
            if (activityDialogGuard != null) {
              activityDialogGuard.shutdownNow
              activityDialogGuard = null
            }
            if (d.isShowing)
              d.dismiss
          }
        }, DTimeout.longest, TimeUnit.MILLISECONDS)
        d.setOnDismissListener(new DialogInterface.OnDismissListener with Logging {
          @Loggable
          override def onDismiss(dialog: DialogInterface) = Dialog.this.synchronized {
            log.info("dismiss safe dialog " + d.getClass.getName)
            if (activityDialogGuard != null) {
              activityDialogGuard.shutdownNow
              activityDialogGuard = null
            }
            Dialog.super.set(null)
            Dialog.this.notify
          }
        })
      } else {
        if (isSet)
          get match {
            case d: Dialog => d.dismiss
            case _ =>
          }
        log.info("reset safe dialog")
      }
      super.set(d)
      notify
    }
  }
  object Dialog {
    implicit def d2ad(d: Dialog): Option[ADialog] = Option(d.get)
  }
}