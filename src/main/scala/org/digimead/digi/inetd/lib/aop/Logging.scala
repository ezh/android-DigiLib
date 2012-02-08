/*
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

import org.aspectj.lang.JoinPoint
import org.slf4j.LoggerFactory

abstract class Logging {
  val logger = LoggerFactory.getLogger("o.d.d.i.a.Logging")

  def enteringMethod(joinPoint: JoinPoint) {
    val tid = Thread.currentThread().getId()
    val source = joinPoint.getSourceLocation()
    val signature = joinPoint.getSignature()
    val className = signature.getDeclaringType().getSimpleName()
    val methodName = signature.getName()
    logger.trace("[T%010d".format(tid) + "] enteringMethod " + className + "::" + methodName + " " + source.getFileName() + ":" + source.getLine())
  }
  def leavingMethod(joinPoint: JoinPoint) {
    val tid = Thread.currentThread().getId()
    val signature = joinPoint.getSignature()
    val className = signature.getDeclaringType().getSimpleName()
    val methodName = signature.getName()
    logger.trace("[T%010d".format(tid) + "] leavingMethod " + className + "::" + methodName)
  }
  def leavingMethod(joinPoint: JoinPoint, returnValue: Object) {
    val tid = Thread.currentThread().getId()
    val signature = joinPoint.getSignature()
    val className = signature.getDeclaringType().getSimpleName()
    val methodName = signature.getName()
    logger.trace("[T%010d".format(tid) + "] leavingMethod " + className + "::" + methodName + " result [" + returnValue + "]")
  }
  def leavingMethodException(joinPoint: JoinPoint, throwable: Exception) {
    val tid = Thread.currentThread().getId()
    val signature = joinPoint.getSignature();
    val className = signature.getDeclaringType().getSimpleName();
    val methodName = signature.getName();
    val exceptionMessage = throwable.getMessage();
    logger.trace("[T%010d".format(tid) + "] leavingMethodException " + className + "::" + methodName + ". Reason: " + exceptionMessage)
  }
}

object Logging {
  final var enabled = true
}

/*

Example:

import org.digimead.digi.inetd.lib.aspect.Loggable;

privileged public aspect INETD extends org.digimead.digi.inetd.lib.aspect.Logging {

	public pointcut loggingNonVoid(Loggable loggable) : execution(!void *(..)) && @annotation(loggable);
	public pointcut loggingVoid(Loggable loggable) : execution(void *(..)) && @annotation(loggable);
	public pointcut logging(Loggable loggable) : loggingVoid(loggable) || loggingNonVoid(loggable);
	
	before(Loggable loggable) : logging(loggable) {
	    enteringMethod(thisJoinPoint, loggable);
	}
	
    after(Loggable loggable) returning(Object result) : loggingNonVoid(loggable) {
        leavingMethod(thisJoinPoint, loggable, result);
    }

    after(Loggable loggable) returning() : loggingVoid(loggable) {
        leavingMethod(thisJoinPoint, loggable);
    }
}

*/
