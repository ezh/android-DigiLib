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

package org.digimead.digi.ctrl.lib.log

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.digimead.RobotEsTrick
import org.scalatest.matchers.ShouldMatchers._
import org.digimead.digi.ctrl.lib.Activity

class LoggingTestBeforeInit_j1 extends FunSuite with BeforeAndAfter with RobotEsTrick {
  lazy val roboClassHandler = RobotEsTrick.classHandler
  lazy val roboClassLoader = RobotEsTrick.classLoader
  lazy val roboDelegateLoadingClasses = RobotEsTrick.delegateLoadingClasses
  lazy val roboConfig = RobotEsTrick.config

  before {
    roboSetup
  }

  test("logging before initialization") {
    val simple = new Logging { def getLog = log }
    Thread.sleep(500)
    assert(simple.getLog.isInstanceOf[RichLogger])
    assert(simple.getLog.loggerName === "@log.LoggingTest")
    simple.log.trace("trace")
    assert(!Logging.queue.isEmpty)
    Logging.queue.peek.isInstanceOf[Logging.Record] should be(true)
    val traceRecord = Logging.queue.toArray.last.asInstanceOf[Logging.Record]
    traceRecord should have(
      'level(Logging.Level.Trace),
      'message("trace"))
    simple.log.debug("debug")
    val debugRecord = Logging.queue.toArray.last.asInstanceOf[Logging.Record]
    debugRecord should have(
      'level(Logging.Level.Debug),
      'message("debug"))
  }
}

class LoggingTestAfterInit_j1 extends FunSuite with BeforeAndAfter with RobotEsTrick {
  lazy val roboClassHandler = RobotEsTrick.classHandler
  lazy val roboClassLoader = RobotEsTrick.classLoader
  lazy val roboDelegateLoadingClasses = RobotEsTrick.delegateLoadingClasses
  lazy val roboConfig = RobotEsTrick.config

  before {
    roboSetup
  }

  test("logging after initialization") {
    val activity = new android.app.Activity with Activity
    Logging.init(activity)
    assert(!Logging.queue.isEmpty)
    Logging.queue.peek.isInstanceOf[Logging.Record] should be(true)
    Logging.logger.foreach(println(_))
    Logging.logger.isEmpty should be(true)
  }
}

class LoggingTestLoggers_j1 extends FunSuite with BeforeAndAfter with RobotEsTrick {
  lazy val roboClassHandler = RobotEsTrick.classHandler
  lazy val roboClassLoader = RobotEsTrick.classLoader
  lazy val roboDelegateLoadingClasses = RobotEsTrick.delegateLoadingClasses
  lazy val roboConfig = RobotEsTrick.config

  before {
    roboSetup
  }

  test("logging console") {
    val activity = new android.app.Activity with Activity
    Logging.init(activity)
    Logging.addLogger(ConsoleLogger)
  }
}
