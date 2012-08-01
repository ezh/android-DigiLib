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

import scala.Option.option2Iterable
import scala.actors.Actor
import scala.actors.Futures
import scala.actors.OutputChannel
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.SyncVar

import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction

trait SafeDialog extends Logging {
  this: DialogFragment =>
  private var beforeListener: Option[SafeDialog => Any] = None
  private var beforeMark: Int = 0 // hashCode
  private var afterListener: Option[SafeDialog => Any] = None
  private var afterMark: Int = 0 // hashCode
  private var onDismissListener: Option[DialogInterface.OnDismissListener] = None

  /**
   * Sets a listener to be invoked before the DialogFragment created, before/at onCreateView/onCreateDialog.
   *
   * @param listener The callback to use.
   */
  private[SafeDialog] def setBeforeListener(listener: SafeDialog => Any) = {
    assert(Thread.currentThread.getId == AnyBase.uiThreadID, { "set beforeListener out of UI thread" })
    beforeListener = Some(listener)
  }
  protected def notifyBefore() = {
    assert(Thread.currentThread.getId == AnyBase.uiThreadID, { "call beforeListener out of UI thread" })
    afterMark = 0 // clear
    beforeListener.foreach {
      l =>
        if (beforeMark == this.hashCode) {
          log.debug("'before' already fired")
        } else {
          val hash = hashCode
          log.debug("fire 'before' event for " + this.tag + " with hash " + hash)
          beforeMark = hashCode
          l(this)
        }
    }
  }
  /**
   * Sets a listener to be invoked after the DialogFragment destroyed, after/at onDestroyView.
   *
   * @param listener The callback to use.
   */
  private[SafeDialog] def setAfterListener(listener: SafeDialog => Any) = {
    assert(Thread.currentThread.getId == AnyBase.uiThreadID, { "set afterListener out of UI thread" })
    afterListener = Some(listener)
  }
  protected def notifyAfter() = {
    assert(Thread.currentThread.getId == AnyBase.uiThreadID, { "call afterListener out of UI thread" })
    beforeMark = 0 // clear
    afterListener.foreach {
      l =>
        if (beforeMark == this.hashCode) {
          log.debug("'after' already fired")
        } else {
          val hash = hashCode
          log.debug("fire 'after' event for " + this.tag + " with hash " + hash)
          beforeMark = hashCode
          l(this)
        }
    }
  }
  private[SafeDialog] def setOnDismissListener(listener: DialogInterface.OnDismissListener) =
    onDismissListener = Some(listener)
  protected def notifySafeDialogDismissed(dialog: DialogInterface) =
    onDismissListener.foreach(_.onDismiss(dialog))
  def dismiss()
  def show(transaction: FragmentTransaction, tag: String): Int
  def show(manager: FragmentManager, tag: String)
  def setArguments(args: Bundle)
  def getWindow(): android.view.Window = getActivity.getWindow
  def isShowing(): Boolean = isVisible
  def tag(): String
}

object SafeDialog extends Logging {
  private val enabled = new AtomicReference[Option[Boolean]](None)
  private[lib] lazy val container = new Container
  private lazy val actor = {
    val actor = new Actor {
      def act = {
        loop {
          react {
            case Message.ShowDialog(builder) =>
              val s = sender
              log.info("receive message ShowDialog " + builder.tag)
              // wait for previous dialog
              log.debug("wait container lock")
              container.put(Entry(Some(builder.tag), None, None)) // wait
              // wait enabled
              log.debug("wait enabled lock")
              while (enabled.get match {
                case Some(true) => show(builder, s); false // show dialog and exit from loop
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
      private def show[T <: SafeDialog](builder: Builder[T], sender: OutputChannel[Any])(implicit m: scala.reflect.Manifest[T]) {
        val result = builder.onMessageShowDialog()
        log.debug("return from message ShowDialog with result " + result)
        if (sender.receiver.getState == Actor.State.Blocked)
          sender ! result // only for ShowDialogSafeWait
      }
    }
    actor.start
    actor
  }
  log.debug("disable safe dialogs")

  def apply[T <: SafeDialog](activity: FragmentActivity, tag: String, dialog: () => T)(implicit m: scala.reflect.Manifest[T]): Builder[T] =
    new Builder[T](new WeakReference(activity), tag, dialog)
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
  def disable() = {
    log.debug("disable safe dialogs")
    enabled.set(None)
    container.set(Entry(None, None, None)) // throw signal for unlock onMessageShowDialog, ..., dismiss safe dialog if any
    Futures.future { enabled.synchronized { enabled.notifyAll } }
    Thread.sleep(10) // gap for onMessageShowDialog
    container.unset()
  }
  @Loggable
  def isEnabled() = enabled.get == Some(true)

  class Builder[T <: SafeDialog] private[SafeDialog] (val activity: WeakReference[FragmentActivity], val tag: String,
    val dialog: () => T)(implicit m: scala.reflect.Manifest[T]) extends Logging {
    type Transaction = (FragmentTransaction, Fragment, Option[Int]) => Any
    var defaultTransaction: Option[Transaction] = Some((ft, fragment, target) => if (target.nonEmpty)
      ft.replace(target.get, fragment, fragment.toString))
    var transactions = Seq[Transaction]()
    var target: Option[Int] = None
    var before: Option[(T) => Any] = None
    var after: Option[(T) => Any] = None

    def customTrasaction(): Builder[T] = { defaultTransaction = None; this }
    def transaction(arg: Transaction): Builder[T] = { transactions = arg +: transactions; this }
    def target(arg: Int): Builder[T] = { target = Some(arg); this }
    def before(f: (T) => Any): Builder[T] = { before = Some(f); this }
    def after(f: (T) => Any): Builder[T] = { after = Some(f); this }
    @Loggable
    def show(): Unit = {
      for {
        target <- target
        activity <- activity.get
        view <- Option(activity.findViewById(target)) if view.isShown
      } yield {
        val dialogInstance: T = if (Thread.currentThread.getId == AnyBase.uiThreadID) {
          try { dialog() } catch { case e => log.error(e.getMessage, e); null.asInstanceOf[T] }
        } else {
          val result = SyncVar[T]()
          AnyBase.runOnUiThread {
            try {
              Option(dialog()) match {
                case Some(dialog) =>
                  before.foreach(d => dialog.setBeforeListener(d.asInstanceOf[SafeDialog => Any]))
                  after.foreach(d => dialog.setAfterListener(d.asInstanceOf[SafeDialog => Any]))
                  result.set(dialog)
                case None =>
                  result.set(null.asInstanceOf[T])
              }
            } catch {
              case e =>
                log.error(e.getMessage, e)
                result.set(null.asInstanceOf[T])
            }
          }
          result.get
        }
        dialogInstance match {
          case dialog: SafeDialog => try {
            log.trace("SafeDialog::show inline %s, tag[%s]".format(m.erasure.getName, tag))
            assert(dialog.tag == tag, { "dialog tag [%s] is unequal to builder tag [%s]".format(dialog.tag, tag) })
            val ft = activity.getSupportFragmentManager.beginTransaction
            try {
              transactions.foreach(_(ft, dialog.asInstanceOf[Fragment], Some(target)))
              defaultTransaction.foreach(_(ft, dialog.asInstanceOf[Fragment], Some(target)))
            } catch {
              case e =>
                log.error(e.getMessage, e)
            }
            ft.commit()
          } catch {
            case e =>
              log.error(e.getMessage, e)
          }
          case dialog =>
            log.trace("SafeDialog::show skip %s, tag[%s], dialog is %s".format(m.erasure.getName, tag, dialog))
        }
      }
    } getOrElse {
      log.trace("SafeDialog::show modal %s, tag[%s]".format(m.erasure.getName, tag))
      actor ! Message.ShowDialog(this)
    }
    @Loggable
    def showWait(): Option[T] = try {
      assert(AnyBase.uiThreadID != Thread.currentThread.getId, { "unexpected thread == UI, " + Thread.currentThread.getId })
      val result = for {
        target <- target
        activity <- activity.get
        view <- Option(activity.findViewById(target)) if view.isShown
      } yield {
        val dialogInstance = { // always outside of the UI thread in showWait
          val result = SyncVar[T]()
          AnyBase.runOnUiThread {
            try {
              Option(dialog()) match {
                case Some(dialog) =>
                  before.foreach(d => dialog.setBeforeListener(d.asInstanceOf[SafeDialog => Any]))
                  after.foreach(d => dialog.setAfterListener(d.asInstanceOf[SafeDialog => Any]))
                  result.set(dialog)
                case None =>
                  result.set(null.asInstanceOf[T])
              }
            } catch {
              case e =>
                log.error(e.getMessage, e)
                result.set(null.asInstanceOf[T])
            }
          }
          result.get
        }
        dialogInstance match {
          case dialog: DialogFragment =>
            log.trace("SafeDialog::showWait inline %s, tag[%s], thread %d".format(m.erasure.getName, tag, Thread.currentThread.getId))
            assert(dialog.tag == tag, { "dialog tag [%s] is unequal to builder tag [%s]".format(dialog.tag, tag) })
            val ft = activity.getSupportFragmentManager.beginTransaction
            try {
              transactions.foreach(_(ft, dialog.asInstanceOf[Fragment], Some(target)))
              defaultTransaction.foreach(_(ft, dialog.asInstanceOf[Fragment], Some(target)))
            } catch {
              case e =>
                log.error(e.getMessage, e)
            }
            ft.commit()
            Some(dialog.asInstanceOf[T])
          case dialog =>
            log.trace("SafeDialog::show skip %s, tag[%s], thread %d, dialog is %s".format(m.erasure.getName, tag, Thread.currentThread.getId, dialog))
            None
        }
      }
      result.getOrElse {
        (actor !? Message.ShowDialog(this)).asInstanceOf[Option[T]]
      }
    } catch {
      case e =>
        log.error(e.getMessage, e)
        None
    }
    @Loggable
    private[SafeDialog] def onMessageShowDialog(): Option[T] = try {
      activity.get.flatMap {
        activity =>
          // for example: pause activity in the middle of the process
          if (!container.isSet) {
            log.warn("skip onMessageShowDialog for " + tag + ", " + m.erasure.getName + ", reason: dialog gone")
            return None
          }
          val expected = Entry(Some(tag), None, None)
          assert(container.isSet && container.get == expected,
            { "container expected " + expected + ", found " + container.get })
          if (!isActivityValid(activity) || enabled == None) {
            reset
            return None
          }
          AnyBase.runOnUiThread {
            try {
              val instance = Option(dialog())
              instance.foreach {
                dialog =>
                  before.foreach(d => dialog.setBeforeListener(d.asInstanceOf[SafeDialog => Any]))
                  after.foreach(d => dialog.setAfterListener(d.asInstanceOf[SafeDialog => Any]))
              }
              container.set(Entry(Some(tag), instance, Some(() => {
                log.trace("safe dialog dismiss callback")
                AppComponent.Inner.enableRotation()
              })))
            } catch {
              case e =>
                log.error(e.getMessage, e)
                container.set(Entry(Some(tag), None, Some(() => {
                  log.trace("safe dialog dismiss callback")
                  AppComponent.Inner.enableRotation()
                })))
            }
          }
          (container.get(DTimeout.longest, _ != Entry(Some(tag), None, None)) match {
            case Some(entry @ Entry(Some(tag), result @ Some(dialog), dismissCb)) =>
              log.debug("show new safe dialog " + entry + " for " + m.erasure.getName)
              assert(dialog.tag == tag, { "dialog tag [%s] is unequal to builder tag [%s]".format(dialog.tag, tag) })
              AppComponent.Inner.disableRotation()
              val ft = activity.getSupportFragmentManager.beginTransaction
              try {
                transactions.foreach(_(ft, dialog.asInstanceOf[Fragment], None))
              } catch {
                case e =>
                  log.error(e.getMessage, e)
              }
              dialog.show(ft, tag)
              result.asInstanceOf[Some[T]]
            case Some(Entry(None, None, None)) =>
              log.error("unable to show safe dialog '" + tag + "' for " + m.erasure.getName + ", reset detected")
              None
            case result =>
              log.error("unable to show safe dialog '" + tag + "' for " + m.erasure.getName + " result:" + result)
              container.unset()
              None
          })
      }
    } catch {
      case e =>
        log.error(e.getMessage, e)
        None
    }
    private def isActivityValid(activity: FragmentActivity): Boolean =
      !activity.isFinishing && activity.getWindow != null
  }
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
    case class ShowDialog[T <: SafeDialog](builder: Builder[T])
  }
}
