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

// Blame it. Look at scala.actors.Debug. Really wise architecture. Scalable. Fuck it :-(
// doInfo, doWarning, doError and hard coded System.out.println... lol
// we haven't console, so provide our scalable way
// One of possibilities why Akka was born
// ... the only benefits are few bright ideas and it is compiled inside and using java.misc.Unsafe in ForkJoin hah :-/
// Poor man that write that scala.actors... But it is better then nothing
// But ;-) anyway, we make it dance
// Alexey Aksenov aka Ezh 02.2012

package scala.actors

import org.slf4j.LoggerFactory
import scala.actors.scheduler.DaemonScheduler
import scala.actors.scheduler.DelegatingScheduler
import scala.actors.scheduler.ThreadPoolConfig
import scala.actors.scheduler.ResizableThreadPoolScheduler

object HackDoggyCode {
  private val log = LoggerFactory.getLogger("o.d.d.c.Actor")
  log.debug("alive")
  def getCurrentThreadPoolCoreSize = ThreadPoolConfig.corePoolSize
  def getCurrentThreadPoolMaxSize = ThreadPoolConfig.maxPoolSize
  def getResizableThreadPoolSchedulerCoreSize(pool: ResizableThreadPoolScheduler): Int =
    Int.unbox(ResizableThreadPoolSchedulerGuts.privateCoreSizeField.invoke(pool, Array[Class[_]](): _*))
  def getResizableThreadPoolSchedulerMaxSize(pool: ResizableThreadPoolScheduler): Int =
    Int.unbox(ResizableThreadPoolSchedulerGuts.privateMaxSizeField.invoke(pool, Array[Class[_]](): _*))
  def getResizableThreadPoolSchedulerNumBlocked(pool: ResizableThreadPoolScheduler): Int =
    Int.unbox(ResizableThreadPoolSchedulerGuts.privateNumBlockedMethod.invoke(pool, Array[Class[_]](): _*))
  object ResizableThreadPoolSchedulerGuts {
    val privateNumBlockedMethod = classOf[ResizableThreadPoolScheduler].getDeclaredMethod("numWorkersBlocked", Array[Class[_]](): _*)
    val privateCoreSizeField = classOf[ResizableThreadPoolScheduler].getDeclaredMethod("coreSize", Array[Class[_]](): _*)
    val privateMaxSizeField = classOf[ResizableThreadPoolScheduler].getDeclaredMethod("maxSize", Array[Class[_]](): _*)
    privateNumBlockedMethod.setAccessible(true)
    privateCoreSizeField.setAccessible(true)
    privateMaxSizeField.setAccessible(true)
  }
}
