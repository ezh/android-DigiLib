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

package org.digimead.digi.ctrl.lib.info

import scala.annotation.elidable
import org.digimead.digi.ctrl.lib.declaration.DState
import annotation.elidable.ASSERTION
import android.os.Parcelable
import android.os.Parcel

class ExecutableInfo(val id: Int //  val commandLine: Seq[String],
//  val port: Int,
//  val env: Seq[String],
//  val state: Int,
//  val name: String,
//  val version: String,
//  val description: String,
//  val origin: String,
//  val license: String,
//  val project: String
) extends Parcelable {
  def this(in: Parcel) = this(0)
  def commandLine: Seq[String] = Seq()
  def port = -1
  def env: Seq[String] = Seq()
  def state: Int = 1
  def name: String = ""
  def version: String = ""
  def description: String = ""
  def origin: String = ""
  def license: String = ""
  def project: String = ""
  //  assert(id >= 0 && id <= 0xFFFF)
  //  assert(port >= -1 && port <= 0xFFFF)
  def writeToParcel(out: Parcel, flags: Int) {

  }
  def describeContents() = 0
}
object ExecutableInfo {
  final val CREATOR: Parcelable.Creator[ExecutableInfo] = new Parcelable.Creator[ExecutableInfo]() {
    def createFromParcel(in: Parcel): ExecutableInfo = new ExecutableInfo(in)
    def newArray(size: Int): Array[ExecutableInfo] = new Array[ExecutableInfo](size)
  }
}