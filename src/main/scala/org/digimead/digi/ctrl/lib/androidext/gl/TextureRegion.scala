/**
 * Copyright (c) 2012 Alexey Aksenov ezh@ezh.msk.ru
 * based on fractious games CC0 1.0 public domain license example
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

package org.digimead.digi.ctrl.lib.androidext.gl

// D: calculate U,V coordinates from specified texture coordinates
// A: texWidth, texHeight - the width and height of the texture the region is for
//    x, y - the top/left (x,y) of the region on the texture (in pixels)
//    width, height - the width and height of the region on the texture (in pixels)
class TextureRegion(texWidth: Float, texHeight: Float, x: Float, y: Float, width: Float, height: Float) {
  /** Top/Left U,V Coordinates */
  val u1 = x / texWidth
  /** Top/Left U,V Coordinates */
  val v1 = y / texHeight
  /** Bottom/Right U,V Coordinates */
  val u2 = u1 + (width / texWidth)
  /** Bottom/Right U,V Coordinates */
  val v2 = v1 + (height / texHeight)
}
