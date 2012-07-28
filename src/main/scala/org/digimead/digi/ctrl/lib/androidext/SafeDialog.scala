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

package org.digimead.digi.ctrl.lib.androidext

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import scala.actors.Actor
import scala.actors.Futures
import scala.actors.OutputChannel
import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.SyncVar
import android.app.Activity
import android.content.DialogInterface
import android.support.v4.app.DialogFragment
import android.view.View
import android.support.v4.app.FragmentActivity

trait SafeDialog {
  this: DialogFragment =>
  @volatile private var onDismissListener: Option[DialogInterface.OnDismissListener] = None

  /**
   * Sets a listener to be invoked when the dialog is shown.
   *
   * @param listener The DialogInterface.OnDismissListener to use.
   */
  def setOnDismissListener(listener: DialogInterface.OnDismissListener) =
    onDismissListener = Some(listener)
  def onDismissSafeDialog(dialog: DialogInterface) =
    onDismissListener.foreach(_.onDismiss(dialog))
  def dismiss()
  def getWindow(): android.view.Window = getActivity.getWindow
  def isShowing(): Boolean = isVisible
}

object SafeDialog extends Logging {
  private val enabled = new AtomicReference[Option[Boolean]](None)
  private[lib] lazy val container = new Container
  private lazy val actor = {
    val actor = new Actor {
      def act = {
        loop {
          react {
            case Message.ShowDialog(activity, tag, dialog, onDismiss) =>
              val s = sender
              log.info("receive message ShowDialog " + dialog)
              // wait for previous dialog
              log.debug("wait container lock")
              container.put(Entry(Some(tag), None, None)) // wait
              // wait enabled
              log.debug("wait enabled lock")
              while (enabled.get match {
                case Some(true) => show(activity, tag, dialog, onDismiss, s); false // show dialog and exit from loop
                case Some(false) => true // wait
                case None => reset; false // skip
              }) enabled.synchronized { enabled.wait }
              log.debug("wait enabled lock complete")
            case message: AnyRef =>
              log.errorWhere("skip unknown message " + message.getClass.getName + ": " + message)
            case message =>
              log.errorWhere("skip unknown message " + message)
          }
        }
      }
      private def show(activity: Activity, tag: String, dialog: () => SafeDialog, onDismiss: Option[() => Any], sender: OutputChannel[Any]) {
        val result = onMessageShowDialog(activity, tag, dialog, onDismiss)
        log.debug("return from message ShowDialog with result " + result)
        if (sender.receiver.getState == Actor.State.Blocked)
          sender ! result // only for ShowDialogSafeWait
      }
    }
    actor.start
    actor
  }
  log.debug("disable safe dialogs")

  def show[T <: SafeDialog](activity: FragmentActivity, target: Option[Int], tag: String, dialog: () => T)(implicit m: scala.reflect.Manifest[T]): Unit =
    show[T](activity, target, tag, dialog, null)
  @Loggable
  def show[T <: SafeDialog](activity: FragmentActivity, target: Option[Int], tag: String, dialog: () => T, onDismiss: () => Any)(implicit m: scala.reflect.Manifest[T]): Unit = {
    for {
      target <- target
      view <- Option(activity.findViewById(target)) if view.getVisibility == View.VISIBLE
    } yield {
      dialog() match {
        case dialog: DialogFragment =>
          log.trace("SafeDialog::show inline %s, tag[%s]".format(m.erasure.getName, tag))
          val ft = activity.getSupportFragmentManager.beginTransaction
          ft.replace(target, dialog.asInstanceOf[DialogFragment])
          ft.commit()
        case dialog =>
          log.trace("SafeDialog::show skip %s, tag[%s], dialog is %s".format(m.erasure.getName, tag, dialog))
      }
    }
  } getOrElse {
    log.trace("SafeDialog::show modal %s, tag[%s]".format(m.erasure.getName, tag))
    actor ! Message.ShowDialog(activity, tag, dialog, Option(onDismiss))
  }
  def showWait[T <: SafeDialog](activity: FragmentActivity, target: Option[Int], tag: String, dialog: () => T)(implicit m: scala.reflect.Manifest[T]): Option[T] =
    showWait[T](activity, target, tag, dialog, null)
  @Loggable
  def showWait[T <: SafeDialog](activity: FragmentActivity, target: Option[Int], tag: String, dialog: () => T, onDismiss: () => Any)(implicit m: scala.reflect.Manifest[T]): Option[T] = try {
    assert(AnyBase.uiThreadID != Thread.currentThread.getId, { "unexpected thread == UI, " + Thread.currentThread.getId })
    (for {
      target <- target
      view <- Option(activity.findViewById(target)) if view.getVisibility == View.VISIBLE
    } yield {
      dialog() match {
        case dialog: DialogFragment =>
          log.trace("SafeDialog::showWait inline %s, tag[%s], thread %d".format(m.erasure.getName, tag, Thread.currentThread.getId))
          val ft = activity.getSupportFragmentManager.beginTransaction
          ft.replace(target, dialog.asInstanceOf[DialogFragment])
          ft.commit()
          Some(dialog.asInstanceOf[T])
        case dialog =>
          log.trace("SafeDialog::show skip %s, tag[%s], thread %d, dialog is %s".format(m.erasure.getName, tag, Thread.currentThread.getId, dialog))
          None
      }
    }) getOrElse {
      (actor !? Message.ShowDialog(activity, tag, dialog, Option(onDismiss))).asInstanceOf[Option[T]]
    }
  } catch {
    case e =>
      log.error(e.getMessage, e)
      None
  }
  @Loggable
  def replace[T <: SafeDialog](tag: String, dialog: () => T) {
    container.get(0) match {
      case Some(entry @ Entry(Some(tag), Some(previousDialog), previousOnDismiss)) =>
        AnyBase.runOnUiThread {
          log.debug("replace previous dialog " + entry)
          previousDialog.setOnDismissListener(new DialogInterface.OnDismissListener {
            override def onDismiss(d: DialogInterface) =
              container.set(Entry(Some(tag), Option(dialog()), previousOnDismiss))
          })
          previousDialog.dismiss
        }
      case entry =>
        log.warn("unable to replace previous dialog " + entry)
    }
  }
  @Loggable
  def set(tag: Option[String], dialog: Option[SafeDialog]) {
    if (container.isSet && container.get(0) != Some(Entry(None, None, None))) {
      val expected = Entry(tag, None, None)
      assert(container.get(0) == Some(expected),
        { "container expected " + expected + ", found " + container.get })
      container.set(Entry(tag, dialog, None))
    } else {
      log.warn("reset dialog " + Entry(tag, dialog, None) + ", previously was " + container.get(0))
      container.set(Entry(None, None, None))
      Thread.sleep(10) // gap for onMessageShowDialog
      container.unset()
    }
  }
  @Loggable
  def reset() = container.unset()
  @Loggable
  def get(timeout: Long): Option[SafeDialog] = container.get(timeout).flatMap(_.dialog)
  @Loggable
  def enable() {
    log.debug("enable safe dialogs")
    enabled.set(Some(true))
    enabled.synchronized { enabled.notifyAll }
  }
  @Loggable
  def suspend() {
    log.debug("suspend safe dialogs")
    enabled.set(Some(false))
    enabled.synchronized { enabled.notifyAll }
  }
  @Loggable
  def disable() = Futures.future {
    log.debug("disable safe dialogs")
    enabled.set(None)
    enabled.synchronized { enabled.notifyAll }
    container.set(Entry(None, None, None)) // throw signal for unlock onMessageShowDialog, ..., dismiss safe dialog if any
    Thread.sleep(10) // gap for onMessageShowDialog
    container.unset()
  }
  @Loggable
  private def onMessageShowDialog[T <: SafeDialog](activity: Activity, tag: String, dialog: () => T, onDismiss: Option[() => Any])(implicit m: scala.reflect.Manifest[T]): Option[T] = try {
    // for example: pause activity in the middle of the process
    if (!container.isSet) {
      log.warn("skip onMessageShowDialog for " + tag + ", " + dialog + ", reason: dialog gone")
      return None
    }
    val expected = Entry(Some(tag), None, None)
    assert(container.isSet && container.get == expected,
      { "container expected " + expected + ", found " + container.get })
    if (!isActivityValid(activity) || enabled == None) {
      reset
      return None
    }
    activity.runOnUiThread(new Runnable {
      def run =
        container.set(Entry(Some(tag), Option(dialog()), Some(() => {
          log.trace("safe dialog dismiss callback")
          AppComponent.Inner.enableRotation()
          onDismiss.foreach(_())
        })))
    })
    (container.get(DTimeout.longest, _ != Entry(Some(tag), None, None)) match {
      case Some(entry @ Entry(tag, result @ Some(dialog), dismissCb)) =>
        log.debug("show new safe dialog " + entry + " for " + m.erasure.getName)
        AppComponent.Inner.disableRotation()
        result.asInstanceOf[Some[T]]
      case Some(Entry(None, None, None)) =>
        log.error("unable to show safe dialog '" + tag + "' for " + m.erasure.getName + ", reset detected")
        None
      case result =>
        log.error("unable to show safe dialog '" + tag + "' for " + m.erasure.getName + " result:" + result)
        container.unset()
        None
    })
  } catch {
    case e =>
      log.error(e.getMessage, e)
      None
  }
  private def isActivityValid(activity: Activity): Boolean =
    !activity.isFinishing && activity.getWindow != null

  class Container private[SafeDialog] () extends SyncVar[Entry] with Logging {
    @volatile private var activityDialogGuard: ScheduledExecutorService = null
    private val replaceFlag = new AtomicBoolean(false)
    private val lock = new Object

    @Loggable
    override def set(entry: Entry, signalAll: Boolean = true): Unit = lock.synchronized {
      replaceFlag.set(false)
      log.debugWhere("set safe dialog to " + entry, Logging.Where.BEFORE)
      if (activityDialogGuard != null) {
        activityDialogGuard.shutdownNow
        activityDialogGuard = null
      }
      // clear previous dialog if any
      get(0) match {
        case Some(previous @ Entry(tag, Some(dialog), dismissCb)) if entry.dialog != None =>
          // replace with new dialog
          if (entry.dialog == dialog) {
            log.fatal("overwrite the same dialog " + entry)
            return
          }
          log.info("replace safe dialog " + previous + " with new one " + entry)
          if (dialog.isShowing) {
            replaceFlag.set(true)
            try {
              dialog.dismiss
            } catch {
              case e =>
                log.warn(previous + " dialog: " + e.getMessage)
                dialog.getWindow.closeAllPanels
            }
            while (replaceFlag.get)
              replaceFlag.synchronized { replaceFlag.wait }
          } else {
            dialog.setOnDismissListener(null)
            try {
              dialog.dismiss
            } catch {
              case e =>
                log.warn(previous + " dialog: " + e.getMessage)
                dialog.getWindow.closeAllPanels
            }
          }
        case Some(previous @ Entry(tag, Some(dialog), dismissCb)) =>
          // replace with stub (state == None)
          if (!dialog.isShowing)
            try {
              dialog.dismiss
            } catch {
              case e =>
                log.warn(previous + " dialog: " + e.getMessage)
                dialog.getWindow.closeAllPanels
            }
        case _ =>
      }
      // set new
      for { dialog <- entry.dialog } {
        // add guard timer
        activityDialogGuard = Executors.newSingleThreadScheduledExecutor()
        activityDialogGuard.schedule(new Runnable {
          def run() = {
            log.fatal("dismiss stalled dialog " + entry)
            if (activityDialogGuard != null) {
              activityDialogGuard.shutdownNow
              activityDialogGuard = null
            }
            try {
              dialog.dismiss
            } catch {
              case e =>
                log.warn(entry + " dialog: " + e.getMessage)
                dialog.getWindow.closeAllPanels
            }
          }
        }, DTimeout.longest, TimeUnit.MILLISECONDS)
        // add on dismiss listener
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener with Logging {
          @Loggable
          override def onDismiss(dialog: DialogInterface) = SafeDialog.this.synchronized {
            log.info("dismiss safe dialog " + entry)
            if (activityDialogGuard != null) {
              activityDialogGuard.shutdownNow
              activityDialogGuard = null
            }
            if (replaceFlag.getAndSet(false)) {
              log.debug("replaced dialog " + entry + " dismissed")
              replaceFlag.synchronized { replaceFlag.notifyAll }
            } else {
              unset(false)
              SafeDialog.this.notifyAll()
            }
          }
        })
      }
      super.set(entry, signalAll)
    }
    def updateDismissCallback(f: Option[() => Any]) =
      super.set(super.get.copy(dismissCb = f))
    override def unset(signalAll: Boolean = true) = {
      log.debugWhere("unset safe dialog '" + value + "'", Logging.Where.BEFORE)
      if (activityDialogGuard != null) {
        activityDialogGuard.shutdownNow
        activityDialogGuard = null
      }
      super.get(0) match {
        case Some(previous @ Entry(tag, Some(dialog), dismissCb)) =>
          if (dialog.isShowing) {
            dialog.setOnDismissListener(null)
            try {
              dialog.dismiss
            } catch {
              case e =>
                log.warn(previous + " dialog: " + e.getMessage)
                dialog.getWindow.closeAllPanels
            }
          }
          dismissCb.foreach(_())
        case _ =>
      }
      super.unset(signalAll)
    }
  }
  private[SafeDialog] case class Entry(val tag: Option[String],
    val dialog: Option[SafeDialog],
    val dismissCb: Option[() => Any])
  object Message {
    sealed trait Abstract
    case class ShowDialog[T <: SafeDialog](activity: Activity, tag: String, dialog: () => T, onDismiss: Option[() => Any]) extends Abstract
  }
}