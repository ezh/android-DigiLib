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

package org.digimead.digi.ctrl.lib.aop

import scala.Array.canBuildFrom

import org.digimead.digi.ctrl.lib.aop.RichLogger.rich2plain
import org.digimead.digi.ctrl.lib.base.AppCache

abstract class Caching extends Logging {
  protected def execute(invoker: Invoker, annotation: Cacheable, longSignature: String, shortSignature: String, args: Array[AnyRef]): Any = {
    // TODO val logging = log.isTraceEnabled()
    /*
     * last argument is inappropriate, for example scala compiler add Manifest[_] to tail
     * there may be other bytecode generators
     */
    val key = if (annotation.examination() && args.nonEmpty) {
      args.head match {
        case Caching.BoxedTrue =>
          // forced
          val key = longSignature.hashCode() + " " + args.tail.map(_.hashCode()).mkString(" ")
          log.trace("FORCED " + shortSignature + " with namespace id " + annotation.namespace)
          return invokeOriginal(invoker, key, annotation.namespace())
        case Caching.BoxedFalse =>
          longSignature.hashCode() + " " + args.tail.map(_.hashCode()).mkString(" ")
        case _ =>
          // lost in space? lazy code? something broken? null? something modify bytecode or other reasons...
          // nothing critical, but notify someone
          log.warn("UNKNOWN TYPE of cacheable method 1st argument, " + shortSignature + " with namespace id " + annotation.namespace)
          longSignature.hashCode() + " " + args.map(_.hashCode()).mkString(" ")
      }
    } else
      longSignature.hashCode() + " " + args.map(_.hashCode()).mkString(" ")
    log.trace(shortSignature + " with namespace id " + annotation.namespace)
    AppCache !? AppCache.Message.GetByID(annotation.namespace(), key, annotation.period()) match {
      case r @ Some(retVal) =>
        log.trace("HIT, key " + key + " found, returning cached value")
        return r
      case None =>
        log.trace("MISS, key " + key + " not found, invoking original method")
        invokeOriginal(invoker, key, annotation.namespace())
    }
  }
  def invokeOriginal(invoker: Invoker, key: String, namespaceID: Int) =
    // all cases except "Option" and "Traversable" must throw scala.MatchError
    // so developer notified about design bug
    invoker.invoke() match {
      case r @ Traversable =>
        // process collection
        AppCache ! AppCache.Message.UpdateByID(namespaceID, key, r)
        log.trace("key " + key + " updated")
        r
      case Nil =>
        // process Nil
        AppCache ! AppCache.Message.RemoveByID(namespaceID, key)
        log.trace("key " + key + "  removed, original method return Nil value")
        Nil
      case r @ Some(retVal) =>
        // process option
        AppCache ! AppCache.Message.UpdateByID(namespaceID, key, retVal)
        log.trace("key " + key + " updated")
        r
      case None =>
        // process None
        AppCache ! AppCache.Message.RemoveByID(namespaceID, key)
        log.trace("key " + key + " removed, original return None value")
        None
    }
  trait Invoker {
    def invoke(): AnyRef
  }
}

object Caching {
  private final val BoxedTrue = Boolean.box(true)
  private final val BoxedFalse = Boolean.box(false)
}

/*

Example:

import org.digimead.digi.ctrl.lib.aop.Cacheable;

privileged public final aspect AspectCaching extends
		org.digimead.digi.ctrl.lib.aop.Caching {
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
