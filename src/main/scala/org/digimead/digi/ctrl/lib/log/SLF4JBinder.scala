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

package org.slf4j.impl

import org.digimead.digi.ctrl.lib.log.Logging
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.spi.LoggerFactoryBinder
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.MarkerFactoryBinder
import org.slf4j.ILoggerFactory
import org.slf4j.Logger

/*
 * StaticMDCBinder
 */

class SLF4JMDCAdapter extends MDCAdapter {
  def clear() {}
  def get(key: String): String = null
  def put(key: String, value: String) {}
  def remove(key: String) {}
  def getCopyOfContextMap(): java.util.Map[_, _] = null
  def setContextMap(contextMap: java.util.Map[_, _]) {}
}

class StaticMDCBinder {
  def getMDCA(): MDCAdapter = new SLF4JMDCAdapter()
  def getMDCAdapterClassStr(): String = classOf[SLF4JMDCAdapter].getName()
}

object StaticMDCBinder {
  val SINGLETON = new StaticMDCBinder()
}

/*
 * StaticMarkerBinder
 */

class StaticMarkerBinder extends MarkerFactoryBinder {
  def getMarkerFactory() = StaticMarkerBinder.markerFactory
  def getMarkerFactoryClassStr() = classOf[BasicMarkerFactory].getName()
}

object StaticMarkerBinder {
  val SINGLETON = new StaticMarkerBinder()
  val markerFactory = new BasicMarkerFactory()
}

/*
 * StaticLoggerBinder
 */

class SLF4JLoggerFactory extends ILoggerFactory {
  def getLogger(name: String): Logger = Logging.getLogger(name)
}

class StaticLoggerBinder extends LoggerFactoryBinder {
  def getLoggerFactory() = StaticLoggerBinder.loggerFactory
  def getLoggerFactoryClassStr() = StaticLoggerBinder.loggerFactoryClassStr
}

object StaticLoggerBinder {
  val loggerFactory: ILoggerFactory = new SLF4JLoggerFactory()
  val loggerFactoryClassStr = classOf[SLF4JLoggerFactory].getName
  val SINGLETON = new StaticLoggerBinder()
  def getSingleton() = SINGLETON
}
