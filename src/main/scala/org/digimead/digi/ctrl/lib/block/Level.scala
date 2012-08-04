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

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.XAPI
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.Logging

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View

object Level extends Logging {
  private val noviceControlViews = new ArrayBuffer[WeakReference[View]]() with SynchronizedBuffer[WeakReference[View]]
  private val intermediateControlViews = new ArrayBuffer[WeakReference[View]]() with SynchronizedBuffer[WeakReference[View]]
  private val professionalControlViews = new ArrayBuffer[WeakReference[View]]() with SynchronizedBuffer[WeakReference[View]]
  @volatile private var highlight = true
  log.debug("alive")

  def novice(view: View) {
    noviceControlViews.append(new WeakReference(view))
    if (isEnable)
      XAPI.setViewBackground(view, Resources.noviceDrawable.mutate)
    else
      XAPI.setViewBackground(view, null)
  }
  def intermediate(view: View) {
    intermediateControlViews.append(new WeakReference(view))
    if (isEnable)
      XAPI.setViewBackground(view, Resources.intermediateDrawable.mutate)
    else
      XAPI.setViewBackground(view, null)
  }
  def professional(view: View) {
    professionalControlViews.append(new WeakReference(view))
    if (isEnable)
      XAPI.setViewBackground(view, Resources.professionalDrawable.mutate)
    else
      XAPI.setViewBackground(view, null)
  }
  def isEnable = highlight
  def setEnable(switch: Boolean) = highlight = switch
  def hlOn(context: Context) = AnyBase.runOnUiThread {
    noviceControlViews.foreach(_.get.foreach(view => {
      XAPI.setViewBackground(view, Resources.noviceDrawable.mutate)
      view.invalidate
    }))
    intermediateControlViews.foreach(_.get.foreach(view => {
      XAPI.setViewBackground(view, Resources.intermediateDrawable.mutate)
      view.invalidate
    }))
    professionalControlViews.foreach(_.get.foreach(view => {
      XAPI.setViewBackground(view, Resources.professionalDrawable.mutate)
      view.invalidate
    }))
  }
  def hlOff(context: Context) = AnyBase.runOnUiThread {
    noviceControlViews.foreach(_.get.foreach(view => {
      XAPI.setViewBackground(view, null)
      view.invalidate
    }))
    intermediateControlViews.foreach(_.get.foreach(view => {
      XAPI.setViewBackground(view, null)
      view.invalidate
    }))
    professionalControlViews.foreach(_.get.foreach(view => {
      XAPI.setViewBackground(view, null)
      view.invalidate
    }))
  }
  // LayerDrawable is a workaround for 2.x bugs, waste of memory, 'def' instead of 'lazy val' :-/
  object Resources {
    lazy val inflater = AppComponent.Context.map {
      context => context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    } getOrElse { log.fatal("unable to get infater"); null }
    def stripeGreen = AppComponent.Context.flatMap {
      context =>
        Option(new BitmapDrawable(BitmapFactory.decodeResource(context.getApplicationContext.getResources,
          XResource.getId(context, "stripe_green", "drawable")))).map(d => { d.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); d.mutate })
    } getOrElse { log.fatal("unable to get drawable stripe_green"); new ColorDrawable() }
    def gradientGreen = AppComponent.Context.flatMap {
      context =>
        val gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR, Array[Int](Color.parseColor("#55007700"), Color.parseColor("#00001100")))
        gradient.setGradientType(GradientDrawable.RADIAL_GRADIENT)
        gradient.setGradientRadius(500)
        Some(gradient.mutate)
    } getOrElse { log.fatal("unable to get drawable gradient_green"); new ColorDrawable() }
    def noviceDrawable = AppComponent.Context match {
      case Some(context) =>
        new LayerDrawable(Array(stripeGreen.mutate, gradientGreen.mutate)).mutate
      case None =>
        log.fatal("unable to get drawable novice_mark")
        new ColorDrawable()
    }
    def stripeYellow = AppComponent.Context.flatMap {
      context =>
        Option(new BitmapDrawable(BitmapFactory.decodeResource(context.getApplicationContext.getResources,
          XResource.getId(context, "stripe_yellow", "drawable")))).map(d => { d.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); d.mutate })
    } getOrElse { log.fatal("unable to get drawable stripe_yellow"); new ColorDrawable() }
    def gradientYellow = AppComponent.Context.flatMap {
      context =>
        val gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR, Array[Int](Color.parseColor("#55999900"), Color.parseColor("#00111100")))
        gradient.setGradientType(GradientDrawable.RADIAL_GRADIENT)
        gradient.setGradientRadius(500)
        Some(gradient.mutate)
    } getOrElse { log.fatal("unable to get drawable gradient_yellow"); new ColorDrawable() }
    def intermediateDrawable = AppComponent.Context.flatMap {
      context => Option(context.getApplicationContext.getResources.getDrawable(XResource.getId(context, "intermediate_mark", "drawable")))
    } match {
      case Some(drawable) =>
        new LayerDrawable(Array(stripeYellow.mutate, gradientYellow.mutate)).mutate
      case None =>
        log.fatal("unable to get drawable intermediate_mark")
        new ColorDrawable()
    }
    def stripeRed = AppComponent.Context.flatMap {
      context =>
        Option(new BitmapDrawable(BitmapFactory.decodeResource(context.getApplicationContext.getResources,
          XResource.getId(context, "stripe_red", "drawable")))).map(d => { d.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); d.mutate })
    } getOrElse { log.fatal("unable to get drawable stripe_red"); new ColorDrawable() }
    def gradientRed = AppComponent.Context.flatMap {
      context =>
        val gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR, Array[Int](Color.parseColor("#55990000"), Color.parseColor("#00220000")))
        gradient.setGradientType(GradientDrawable.RADIAL_GRADIENT)
        gradient.setGradientRadius(500)
        Some(gradient.mutate)
    } getOrElse { log.fatal("unable to get drawable gradient_red"); new ColorDrawable() }
    def professionalDrawable = AppComponent.Context.flatMap {
      context => Option(context.getApplicationContext.getResources.getDrawable(XResource.getId(context, "professional_mark", "drawable")))
    } match {
      case Some(drawable) =>
        new LayerDrawable(Array(stripeRed.mutate, gradientRed.mutate)).mutate
      case None =>
        log.fatal("unable to get drawable professional_mark")
        new ColorDrawable()
    }
  }
}
