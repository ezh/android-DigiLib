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

package org.digimead.digi.ctrl.lib.message

import org.digimead.RobotEsTrick
import org.digimead.digi.ctrl.lib.DActivity
import org.digimead.digi.ctrl.lib.log.ConsoleLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite

import android.os.Bundle
import android.os.Parcel

class DMessageTest_IAmMumble_j1 extends FunSuite with BeforeAndAfter with RobotEsTrick {
  lazy val roboClassHandler = RobotEsTrick.classHandler
  lazy val roboClassLoader = RobotEsTrick.classLoader
  lazy val roboDelegateLoadingClasses = RobotEsTrick.delegateLoadingClasses
  lazy val roboConfig = RobotEsTrick.config

  before {
    roboSetup
  }

  // :-(
  test("test parcelable") {
    val activity = new android.app.Activity with DActivity {
      override def onCreate(b: Bundle) =
        super.onCreate(b: Bundle)
      val dispatcher = null
    }
    //activity.onCreate(null)
    Logging.init(activity)
    Logging.addLogger(ConsoleLogger)
    val logger = Logging.getRichLogger("TESTL")
    val dispatcher = new Dispatcher { def process(message: DMessage) {} }
    val a = IAmMumble("Hello")(logger, dispatcher)
    val parcel = Parcel.obtain
    a.writeToParcel(parcel, 0)
    parcel.writeInt(1)
  }
}
