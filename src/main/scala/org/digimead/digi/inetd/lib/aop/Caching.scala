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

package org.digimead.digi.inetd.lib.aop

import org.slf4j.LoggerFactory
import org.digimead.digi.inetd.lib.AppCache

abstract class Caching {
  val log = LoggerFactory.getLogger("o.d.d.i.a.Logging")

  protected def execute(invoker: Invoker, annotation: Cacheable, longSignature: String, shortSignature: String, args: Array[AnyRef]): Any = {
    // TODO val logging = log.isTraceEnabled()
    val tid = Thread.currentThread().getId()
    log.trace("[T%010d".format(tid) + "] CACHE: " + shortSignature + " with namespace id " + annotation.namespace)
    val key = longSignature.hashCode() + " " + args.map(_.hashCode()).mkString(" ")
    AppCache !? AppCache.Message.GetByID(annotation.namespace(), key, annotation.period()) match {
      case r @ Some(retVal) =>
        log.trace("[T%010d".format(tid) + "] CACHE: key " + key + " found, returning cached value")
        return r
      case None =>
        log.trace("[T%010d".format(tid) + "] CACHE: key " + key + " not found, invoking original method")
        // all cases except "Option" and "Traversable" must throw scala.MatchError
        // so developer notified about design bug
        invoker.invoke() match {
          case r @ Traversable =>
            // process collection
            AppCache ! AppCache.Message.UpdateByID(annotation.namespace(), key, r)
            log.trace("[T%010d".format(tid) + "] CACHE: key " + key + " updated")
            r
          case Nil =>
            // process Nil
            log.trace("[T%010d".format(tid) + "] CACHE: key " + key + " NOT saved, original method return Nil value")
            Nil
          case r @ Some(retVal) =>
            // process option
            AppCache ! AppCache.Message.UpdateByID(annotation.namespace(), key, retVal)
            log.trace("[T%010d".format(tid) + "] CACHE: key " + key + " updated")
            r
          case None =>
            // process None
            log.trace("[T%010d".format(tid) + "] CACHE: key " + key + " NOT saved, original return None value")
            None
        }
    }
  }
  trait Invoker {
    def invoke(): AnyRef
  }
}

/*

Example:

import org.digimead.digi.inetd.lib.aop.Cacheable;

privileged public final aspect AspectCaching extends
		org.digimead.digi.inetd.lib.aop.Caching {
	public pointcut cachedAccessPoint(Cacheable cachable):
		execution(@Cacheable * *(..)) && @annotation(cachable);

	Object around(final Cacheable cacheable): cachedAccessPoint(cacheable) {
		Invoker aspectJInvoker = new Invoker() {
			public Object invoke() {
				return proceed(cacheable);
			}
		};
		return execute(aspectJInvoker, cacheable,
				thisJoinPointStaticPart.toLongString(), thisJoinPoint.getArgs());
	}
}

*/
