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

package org.digimead.digi.ctrl.lib.block

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.SynchronizedBuffer
import scala.ref.WeakReference
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import org.digimead.digi.ctrl.lib.AnyBase

object Level extends Logging {
  private val noviceControlViews = new ArrayBuffer[WeakReference[View]]() with SynchronizedBuffer[WeakReference[View]]
  private val intermediateControlViews = new ArrayBuffer[WeakReference[View]]() with SynchronizedBuffer[WeakReference[View]]
  private val professionalControlViews = new ArrayBuffer[WeakReference[View]]() with SynchronizedBuffer[WeakReference[View]]
  @volatile private var highlight = true
  log.debug("alive")

  def novice(view: View) {
    noviceControlViews.append(new WeakReference(view))
    if (isEnable)
      view.setBackgroundDrawable(Resources.noviceDrawable.mutate)
    else
      view.setBackgroundDrawable(null)
  }
  def intermediate(view: View) {
    intermediateControlViews.append(new WeakReference(view))
    if (isEnable)
      view.setBackgroundDrawable(Resources.intermediateDrawable.mutate)
    else
      view.setBackgroundDrawable(null)
  }
  def professional(view: View) {
    professionalControlViews.append(new WeakReference(view))
    if (isEnable)
      view.setBackgroundDrawable(Resources.professionalDrawable.mutate)
    else
      view.setBackgroundDrawable(null)
  }
  def isEnable = highlight
  def setEnable(switch: Boolean) = highlight = switch
  def hlOn(context: Context) = AnyBase.handler.post(new Runnable {
    def run {
      noviceControlViews.foreach(_.get.foreach(view => {
        view.setBackgroundDrawable(Resources.noviceDrawable.mutate)
        view.invalidate
      }))
      intermediateControlViews.foreach(_.get.foreach(view => {
        view.setBackgroundDrawable(Resources.intermediateDrawable.mutate)
        view.invalidate
      }))
      professionalControlViews.foreach(_.get.foreach(view => {
        view.setBackgroundDrawable(Resources.professionalDrawable.mutate)
        view.invalidate
      }))
    }
  })
  def hlOff(context: Context) = AnyBase.handler.post(new Runnable {
    def run {
      noviceControlViews.foreach(_.get.foreach(view => {
        view.setBackgroundDrawable(null)
        view.invalidate
      }))
      intermediateControlViews.foreach(_.get.foreach(view => {
        view.setBackgroundDrawable(null)
        view.invalidate
      }))
      professionalControlViews.foreach(_.get.foreach(view => {
        view.setBackgroundDrawable(null)
        view.invalidate
      }))
    }
  })
  object Resources {
    lazy val inflater = AppComponent.Context.map {
      context => context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    } getOrElse { log.fatal("unable to get infater"); null }
    def noviceDrawable = AppComponent.Context.flatMap {
      context => Option(context.getApplicationContext.getResources.getDrawable(Android.getId(context, "novice_mark", "drawable")))
    } match {
      case Some(drawable) =>
        drawable.setAlpha(50)
        drawable
      case None =>
        log.fatal("unable to get drawable novice_mark")
        new ColorDrawable()
    }
    def intermediateDrawable = AppComponent.Context.flatMap {
      context => Option(context.getApplicationContext.getResources.getDrawable(Android.getId(context, "intermediate_mark", "drawable")))
    } match {
      case Some(drawable) =>
        drawable.setAlpha(50)
        drawable
      case None =>
        log.fatal("unable to get drawable intermediate_mark")
        new ColorDrawable()
    }
    def professionalDrawable = AppComponent.Context.flatMap {
      context => Option(context.getApplicationContext.getResources.getDrawable(Android.getId(context, "professional_mark", "drawable")))
    } match {
      case Some(drawable) =>
        drawable.setAlpha(70)
        drawable
      case None =>
        log.fatal("unable to get drawable professional_mark")
        new ColorDrawable()
    }
  }
}
