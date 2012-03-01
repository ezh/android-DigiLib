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

import scala.actors.scheduler.DaemonScheduler
import scala.actors.scheduler.ResizableThreadPoolScheduler
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.RichLogger.rich2plain
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.base.AppService

import android.content.Context

private[lib] trait AnyBase extends Logging {
  @Loggable
  protected def onCreateBase(ctx: Context, callSuper: => Any): Boolean = {
    callSuper
    AnyBase.init(ctx)
  }
}

object AnyBase extends Logging {
  System.setProperty("actors.enableForkJoin", "false")
  System.setProperty("actors.corePoolSize", "128")
  private val weakScheduler = new WeakReference(DaemonScheduler.impl.asInstanceOf[ResizableThreadPoolScheduler])
  log.debug("set default scala actors scheduler to " + weakScheduler.get.get.getClass.getName() + " "
    + weakScheduler.get.get.toString + "[name,priority,group]")
  log.debug("scheduler corePoolSize = " + scala.actors.HackDoggyCode.getResizableThreadPoolSchedulerCoreSize(weakScheduler.get.get) +
    ", maxPoolSize = " + scala.actors.HackDoggyCode.getResizableThreadPoolSchedulerMaxSize(weakScheduler.get.get))
  private def init(ctx: Context) = {
    AppActivity.init(ctx)
    AppService.init(ctx)
    log.debug("start AppActivity singleton actor")
    AppActivity.Inner match {
      case Some(inner) =>
        // start activity singleton actor
        inner.start
        true
      case None =>
        false
    }
  }
  def safeInit(ctx: Context) = {
    AppActivity.safe(ctx)
    AppService.safe(ctx)
    log.debug("start AppActivity singleton actor")
    AppActivity.Inner match {
      case Some(inner) =>
        // start activity singleton actor
        inner.start
        true
      case None =>
        false
    }
  }
}