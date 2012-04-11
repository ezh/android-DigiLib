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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import scala.Array.canBuildFrom
import scala.actors.Futures.future
import scala.collection.JavaConversions._
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.dialog.Report
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.util.SyncVar

import android.accounts.AccountManager
import android.app.{ Activity => AActivity }
import android.app.{ Dialog => ADialog }
import android.content.BroadcastReceiver
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import declaration.DTimeout

/*
 * trait hasn't ability to use @Loggable
 */
trait Activity extends AActivity with AnyBase with Logging {
  protected[lib] val activityDialog = new Activity.Dialog
  val uiThreadID: Long = Looper.getMainLooper.getThread.getId
  val onPrepareDialogStash = new HashMap[Int, Any]() with SynchronizedMap[Int, Any]
  /*
   * sometimes in life cycle onCreate stage invoked without onDestroy stage
   */
  override def onCreate(savedInstanceState: Bundle): Unit = {
    log.trace("Activity::onCreate")
    onCreateBase(this, { Activity.super.onCreate(savedInstanceState) })
    Activity.registeredReceiver.clear // sometimes onDestroy skipped, there is no harm to drop garbage
    activityDialog.set(null) // lock
  }
  override def onResume() = {
    log.trace("Activity::onResume")
    Activity.registeredReceiver.foreach(t => super.registerReceiver(t._1, t._2._1, t._2._2, t._2._3))
    activityDialog.unset()
    super.onResume()
  }
  override def onPause() {
    log.trace("Activity::onPause")
    activityDialog.set(null) // lock
    Activity.registeredReceiver.keys.foreach(super.unregisterReceiver(_))
    super.onPause()
  }
  /*
   * sometimes in life cycle onCreate stage invoked without onDestroy stage
   * in fact AppActivity.deinit is sporadic event
   */
  override def onDestroy() = {
    log.trace("Activity::onDestroy")
    Activity.registeredReceiver.clear
    super.onDestroy()
    onDestroyBase(this, {
      if (AnyBase.isLastContext)
        AppActivity.deinit()
      else
        log.debug("skip onDestroy deinitialization, because there is another context coexists")
      Activity.super.onDestroy()
    })
  }
  def showDialogSafe(id: Int, args: Bundle = null) = future {
    try {
      log.trace("Activity::showDialogSafe id " + id + " at thread " + currentThread.getId + " and ui " + uiThreadID)
      assert(uiThreadID != Thread.currentThread.getId)
      activityDialog.synchronized {
        while (activityDialog.get(0) match {
          case Some(d) => log.debug("wait previous safe dialog" + d); true
          case None => false
        }) {
          activityDialog.wait
          Thread.sleep(500) // short gap between dialogs
        }
        activityDialog.unset()
        runOnUiThread(new Runnable { def run = if (args == null) showDialog(id) else showDialog(id, args) })
        log.debug("show new safe dialog " + id)
      }
    } catch {
      case e =>
        log.error(e.getMessage, e)
        None
    }
  }
  def showDialogSafe[T <: ADialog](dialog: () => T)(implicit m: scala.reflect.Manifest[T]): Option[T] = {
    try {
      log.trace("Activity::showDialogSafe " + m.erasure.getName + "at thread " + currentThread.getId + " and ui " + uiThreadID)
      assert(uiThreadID != Thread.currentThread.getId)
      activityDialog.synchronized {
        while (activityDialog.get(0) match {
          case Some(d) => log.debug("wait previous safe dialog" + d); true
          case None => false
        }) {
          activityDialog.wait
          Thread.sleep(500) // short gap between dialogs
        }
        activityDialog.unset()
        this.runOnUiThread(new Runnable { def run = activityDialog.set(dialog()) })
        log.debug("show new safe dialog " + activityDialog.get)
      }
      Option(activityDialog.get.asInstanceOf[T])
    } catch {
      case e =>
        log.error(e.getMessage, e)
        None
    }
  }
  override def onCreateDialog(id: Int): ADialog = {
    log.trace("Activity::onCreateDialog")
    id match {
      case id if id == Report.getId(this) =>
        log.debug("show Report dialog")
        Report.createDialog(this)
      case id =>
        Option(Common.onCreateDialog(id, this)).foreach(dialog => return dialog)
        super.onCreateDialog(id)
    }
  }
  override def onPrepareDialog(id: Int, dialog: ADialog): Unit = {
    super.onPrepareDialog(id, dialog)
    onPrepareDialogLib(id: Int, dialog: ADialog)
  }
  override def onPrepareDialog(id: Int, dialog: ADialog, args: Bundle): Unit = {
    super.onPrepareDialog(id, dialog, args)
    onPrepareDialogLib(id: Int, dialog: ADialog)
  }
  private def onPrepareDialogLib(id: Int, dialog: ADialog): Unit = {
    if (activityDialog.isSet && dialog == activityDialog.get) // guard
      return
    log.trace("Activity::onPrepareDialog")
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
    log.trace("Activity::registerReceiver " + receiver)
    assert(!Activity.registeredReceiver.isDefinedAt(receiver))
    Activity.registeredReceiver(receiver) = (filter, null, null)
    super.registerReceiver(receiver, filter)
  } catch {
    case e =>
      log.error(e.getMessage, e)
      null
  }
  override def registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter, broadcastPermission: String, scheduler: Handler): Intent = try {
    log.trace("Activity::registerReceiver " + receiver)
    assert(!Activity.registeredReceiver.isDefinedAt(receiver))
    Activity.registeredReceiver(receiver) = (filter, broadcastPermission, scheduler)
    super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
  } catch {
    case e =>
      log.error(e.getMessage, e)
      null
  }
  override def unregisterReceiver(receiver: BroadcastReceiver) {
    log.trace("Activity::unregisterReceiver " + receiver)
    Activity.registeredReceiver.remove(receiver)
    super.unregisterReceiver(receiver)
  }
}

object Activity {
  /** BroadcastReceiver that recorded at registerReceiver/unregisterReceiver */
  private val registeredReceiver = new HashMap[BroadcastReceiver, (IntentFilter, String, Handler)] with SynchronizedMap[BroadcastReceiver, (IntentFilter, String, Handler)]
  class Dialog extends SyncVar[ADialog] with Logging {
    private var activityDialogGuard: ScheduledExecutorService = null
    private val isPrivateReplace = new AtomicBoolean(false)
    private val lock = new Object

    @Loggable
    override def set(d: ADialog, signalAll: Boolean = true): Unit = lock.synchronized {
      log.debug("set safe dialog to '" + d + "'")
      if (activityDialogGuard != null) {
        activityDialogGuard.shutdownNow
        activityDialogGuard = null
      }
      get(0) match {
        case Some(previousDialog) if previousDialog != null =>
          if (d == previousDialog) {
            log.fatal("overwrite the same dialog '" + d + "'")
            return
          }
          log.info("replace safe dialog '" + value + "' with new one '" + d + "'")
          isPrivateReplace.synchronized {
            if (previousDialog.isShowing) {
              // we want replace dialog
              isPrivateReplace.set(true)
              previousDialog.dismiss
              while (isSet && isPrivateReplace.get)
                isPrivateReplace.wait
            } else {
              log.warn("replace hidden safe dialog")
              previousDialog.setOnDismissListener(null)
              previousDialog.dismiss
            }
          }
        case _ =>
      }
      if (d != null) {
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
            if (isPrivateReplace.getAndSet(false)) {
              // there is set(N) waiting for us
              // do it silent for external routines
              isPrivateReplace.synchronized {
                value.set(false, null)
                isPrivateReplace.notifyAll
              }
            } else {
              unset(false)
              Dialog.this.notifyAll()
            }
          }
        })
      }
      super.set(d, signalAll)
    }
    override def unset(signalAll: Boolean = true) = {
      log.debug("unset safe dialog " + value.get._2)
      super.unset(signalAll)
    }
  }
  object Dialog {
    implicit def d2ad(d: Dialog): Option[ADialog] = Option(d.get)
  }
}