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

import java.util.Date

import org.digimead.RobotEsTrick
import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.DActivity
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers._

import android.os.Bundle

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
    //assert(simple.getLog.loggerName === "@log.LoggingTest")
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
    val activity = new android.app.Activity with DActivity { val dispatcher = null }
    Logging.init(activity)
    assert(!Logging.queue.isEmpty)
    Logging.queue.peek.isInstanceOf[Logging.Record] should be(true)
    Logging.logger.foreach(println(_))
    Logging.logger.isEmpty should be(true)
  }
}

class LoggingTestConsoleLogger_j1 extends FunSuite with BeforeAndAfter with RobotEsTrick {
  lazy val roboClassHandler = RobotEsTrick.classHandler
  lazy val roboClassLoader = RobotEsTrick.classLoader
  lazy val roboDelegateLoadingClasses = RobotEsTrick.delegateLoadingClasses
  lazy val roboConfig = RobotEsTrick.config

  before {
    roboSetup
  }

  test("logging console") {
    val activity = new android.app.Activity with DActivity {
      override def onCreate(b: Bundle) =
        super.onCreate(b: Bundle)
      val dispatcher = null
    }
    //activity.onCreate(null)
    AnyBase.info.get should not be (None)
    Thread.sleep(100)
    Logging.loggingThread.isAlive should be(false)
    Logging.logger.isEmpty should be(true)
    Logging.init(activity)
    var writeToLog = false
    ConsoleLogger.setF((r) => { writeToLog = true })
    Logging.addLogger(ConsoleLogger)
    Logging.logger.isEmpty should be(false)
    Logging.logger.head should equal(ConsoleLogger)
    Thread.sleep(100)
    Logging.loggingThread.isAlive should be(true)
    writeToLog should be(true)
  }
}

class LoggingTestAndroidLogger_j1 extends FunSuite with BeforeAndAfter with RobotEsTrick {
  lazy val roboClassHandler = RobotEsTrick.classHandler
  lazy val roboClassLoader = RobotEsTrick.classLoader
  lazy val roboDelegateLoadingClasses = RobotEsTrick.delegateLoadingClasses
  lazy val roboConfig = RobotEsTrick.config

  before {
    roboSetup
  }

  test("logging android") {
    val activity = new android.app.Activity with DActivity {
      override def onCreate(b: Bundle) =
        super.onCreate(b: Bundle)
      val dispatcher = null
    }
    //activity.onCreate(null)
    AnyBase.info.get should not be (None)
    Thread.sleep(100)
    Logging.loggingThread.isAlive should be(false)
    Logging.logger.isEmpty should be(true)
    Logging.init(activity)
    var writeToLog = false
    val f = AndroidLogger.getF
    AndroidLogger.setF((r) => { writeToLog = true })
    Logging.addLogger(AndroidLogger)
    Logging.logger.isEmpty should be(false)
    Logging.logger.head should equal(AndroidLogger)
    Thread.sleep(100)
    Logging.loggingThread.isAlive should be(true)
    writeToLog should be(true)
    AndroidLogger.validName.isEmpty should be(true)
    f(Seq(Logging.Record(new Date, 0, Logging.Level.Debug, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "+")))
    AndroidLogger.validName.nonEmpty should be(true)
    AndroidLogger.validName.head should be(("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAA*"))
  }
}

class LoggingTestFileLogger_j1 extends FunSuite with BeforeAndAfter with RobotEsTrick {
  lazy val roboClassHandler = RobotEsTrick.classHandler
  lazy val roboClassLoader = RobotEsTrick.classLoader
  lazy val roboDelegateLoadingClasses = RobotEsTrick.delegateLoadingClasses
  lazy val roboConfig = RobotEsTrick.config

  before {
    roboSetup
  }

  test("logging file") {
    val activity = new android.app.Activity with DActivity {
      override def onCreate(b: Bundle) =
        super.onCreate(b: Bundle)
      val dispatcher = null
    }
    //activity.onCreate(null)
    AnyBase.info.get should not be (None)
    Thread.sleep(100)
    Logging.loggingThread.isAlive should be(false)
    Logging.logger.isEmpty should be(true)
    Logging.init(activity)
    var writeToLog = false
    val f = FileLogger.getF
    FileLogger.setF((r) => { writeToLog = true })
    Logging.addLogger(FileLogger)
    Logging.logger.isEmpty should be(false)
    Logging.logger.head should equal(FileLogger)
    Thread.sleep(100)
    Logging.loggingThread.isAlive should be(true)
    writeToLog should be(true)
  }

  test("file location") {
    AnyBase.info.get should not be (None)
    AppComponent.Context should not be (None)
    AppComponent.Inner.internalStorage should not be (None)
    AppComponent.Inner.externalStorage should be(None)
    Logging.logger.head should equal(FileLogger)
    FileLogger.file should not be (None)
    FileLogger.output should not be (None)
  }
}
