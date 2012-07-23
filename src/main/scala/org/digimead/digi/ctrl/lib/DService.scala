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

import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.log.Logging

import android.app.Service

/**
 * DigiLib primary service class trait
 */
trait DService extends AnyBase with Logging {
  /**
   * extend android onCreate method
   *
   * @note sometimes in an application life cycle onCreate method invoked without onDestroy phase
   */
  def onCreateExt(service: Service): Unit = {
    log.trace("Service::onCreateExt")
    onCreateBase(service, {})
  }
  /**
   * extend android onDestroy method
   *
   * @note in fact onDestroy/AppControl.deinit is sporadic event
   */
  def onDestroyExt(service: Service): Unit = {
    log.trace("Service::onDestroyExt")
    onDestroyBase(service)
  }
}

/** DigiLib primary service support singleton */
object DService {
  /** profiling support */
  private val ppLoading = AnyBase.ppGroup.start("DService$")
  ppLoading.stop
}
