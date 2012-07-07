/**
 * Copyright (c) 2010-2012 Alexey Aksenov ezh@ezh.msk.ru
 * This file is part of the Documentum Elasticus project.
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

package org.digimead.digi.ctrl.lib.util

trait Passwords {
  val randomIntN = new scala.util.Random(System.currentTimeMillis())
  val numbers = ('0' to '9').toList
  val symbols = """!@#$%^&*()_+-=[]{};':",./?""".toList
  val alphabet = ('a' to 'z').toList
  val upperAlphabet = ('A' to 'Z').toList
  val defaultPasswordCharacters = numbers ++ symbols ++ alphabet ++ upperAlphabet
  private val random = new java.security.SecureRandom
  def generate(length: Int): String =
    (for (i <- 1 to length) yield defaultPasswordCharacters(random.nextInt(defaultPasswordCharacters.size))).mkString
  def generate(min: Int, max: Int): String = generate(randomInt(min, max))
  def generate(): String = generate(6, 12)
  def randomInt(minValue: Int, maxValue: Int): Int = randomIntN.nextInt(maxValue - minValue + 1) + minValue
}
