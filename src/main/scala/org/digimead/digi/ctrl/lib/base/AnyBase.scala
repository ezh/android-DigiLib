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

package org.digimead.digi.ctrl.lib.base

import org.slf4j.LoggerFactory

import android.content.Context
import org.digimead.digi.ctrl.lib.aop.Loggable

private[base] trait AnyBase {
  private val log = LoggerFactory.getLogger(getClass.getName().replaceFirst("org.digimead.digi.ctrl", "o.d.d.c"))
  System.setProperty("actors.enableForkJoin", "false")
  @Loggable
  protected def onCreateBase(ctx: Context, callSuper: => Any): Boolean = {
    callSuper
    org.digimead.digi.ctrl.lib.AppActivity.init(ctx)
    org.digimead.digi.ctrl.lib.AppService.init(ctx)
    log.debug("start AppActivity singleton actor")
    org.digimead.digi.ctrl.lib.AppActivity.Inner match {
      case Some(inner) =>
        // start activity singleton actor
        inner.start
        true
      case None =>
        false
    } 
  }
}