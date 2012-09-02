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

import java.nio.ByteBuffer
import java.nio.ByteOrder

import javax.microedition.khronos.opengles.GL10

// D: create the vertices/indices as specified (for 2d/3d)
// A: gl - the gl instance to use
//    maxVertices - maximum vertices allowed in buffer
//    maxIndices - maximum indices allowed in buffer
//    hasColor - use color values in vertices
//    hasTexCoords - use texture coordinates in vertices
//    hasNormals - use normals in vertices
//    use3D - (false, default) use 2d positions (ie. x/y only)
//            (true) use 3d positions (ie. x/y/z)
class Vertices(gl: GL10, maxVertices: Int, maxIndices: Int, hasColor: Boolean, hasTexCoords: Boolean, hasNormals: Boolean, use3D: Boolean) {
  /*
     * Constants
     */
  /** Number of Components in Vertex Position for 2D */
  val POSITION_CNT_2D = 2
  /** Number of Components in Vertex Position for 3D */
  val POSITION_CNT_3D = 3
  /** Number of Components in Vertex Color */
  val COLOR_CNT = 4
  /** Number of Components in Vertex Texture Coords */
  val TEXCOORD_CNT = 2
  /** Number of Components in Vertex Normal */
  val NORMAL_CNT = 3
  /** Index Byte Size (Short.SIZE = bits) */
  val INDEX_SIZE = java.lang.Short.SIZE / 8
  /*
    * Members
    */
  /** Number of Position Components (2=2D, 3=3D) */
  val positionCnt = if (use3D) POSITION_CNT_3D else POSITION_CNT_2D
  /** Vertex Stride (Element Size of a Single Vertex) */
  val vertexStride = positionCnt + (if (hasColor) COLOR_CNT else 0) +
    (if (hasTexCoords) TEXCOORD_CNT else 0) + (if (hasNormals) NORMAL_CNT else 0)
  /** Bytesize of a Single Vertex */
  val vertexSize = vertexStride * 4
  /** Vertex Buffer */
  val vertices = {
    // Allocate Buffer for Vertices (Max)
    val verticesBuffer = ByteBuffer.allocateDirect(maxVertices * vertexSize)
    // Set Native Byte Order
    verticesBuffer.order(ByteOrder.nativeOrder())
    verticesBuffer.asIntBuffer()
  }
  /** Index Buffer */
  val indices = if (maxIndices > 0) { // IF Indices Required
    val indicesBuffer = ByteBuffer.allocateDirect(maxIndices * INDEX_SIZE) // Allocate Buffer for Indices (MAX)
    indicesBuffer.order(ByteOrder.nativeOrder()) // Set Native Byte Order
    indicesBuffer.asShortBuffer()
  } else // ELSE Indices Not Required
    null // No Index Buffer
  /** Number of Vertices in Buffer */
  var numVertices = 0
  /** Number of Indices in Buffer */
  var numIndices = 0
  /** Temp Buffer for Vertex Conversion */
  val tmpBuffer = new Array[Int](maxVertices * vertexSize / 4)

  def this(gl: GL10, maxVertices: Int, maxIndices: Int, hasColor: Boolean, hasTexCoords: Boolean, hasNormals: Boolean) =
    this(gl, maxVertices, maxIndices, hasColor, hasTexCoords, hasNormals, false)

  /**
   * Set Vertices
   * D: set the specified vertices in the vertex buffer
   * NOTE: optimized to use integer buffer!
   * A: vertices - array of vertices (floats) to set
   * offset - offset to first vertex in array
   * length - number of floats in the vertex array (total)
   * for easy setting use: vtx_cnt * (this.vertexSize / 4)
   * R: [none]
   */
  def setVertices(vertices: Array[Float], offset: Int, length: Int) {
    this.vertices.clear() // Remove Existing Vertices
    val last = offset + length // Calculate Last Element
    for (i <- offset until last; j = i - offset) // FOR Each Specified Vertex
      tmpBuffer(j) = java.lang.Float.floatToRawIntBits(vertices(i)) // Set Vertex as Raw Integer Bits in Buffer
    this.vertices.put(tmpBuffer, 0, length) // Set New Vertices
    this.vertices.flip() // Flip Vertex Buffer
    numVertices = length / vertexStride // Save Number of Vertices
  }
  /**
   * Set Indices
   * D: set the specified indices in the index buffer
   * A: indices - array of indices (shorts) to set
   * offset - offset to first index in array
   * length - number of indices in array (from offset)
   */
  def setIndices(indices: Array[scala.Short], offset: Int, length: Int) {
    this.indices.clear() // Clear Existing Indices
    this.indices.put(indices, offset, length) // Set New Indices
    this.indices.flip() // Flip Index Buffer
    numIndices = length // Save Number of Indices
  }
  /**
   * Bind
   * D: perform all required binding/state changes before rendering batches.
   * USAGE: call once before calling draw() multiple times for this buffer.
   */
  def bind() {
    gl.glEnableClientState(GL10.GL_VERTEX_ARRAY) // Enable Position in Vertices
    vertices.position(0) // Set Vertex Buffer to Position
    gl.glVertexPointer(positionCnt, GL10.GL_FLOAT, vertexSize, vertices) // Set Vertex Pointer
    if (hasColor) { // IF Vertices Have Color
      gl.glEnableClientState(GL10.GL_COLOR_ARRAY) // Enable Color in Vertices
      vertices.position(positionCnt) // Set Vertex Buffer to Color
      gl.glColorPointer(COLOR_CNT, GL10.GL_FLOAT, vertexSize, vertices) // Set Color Pointer
    }
    if (hasTexCoords) { // IF Vertices Have Texture Coords
      gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY) // Enable Texture Coords in Vertices
      vertices.position(positionCnt + (if (hasColor) COLOR_CNT else 0)) // Set Vertex Buffer to Texture Coords (NOTE: position based on whether color is also specified)
      gl.glTexCoordPointer(TEXCOORD_CNT, GL10.GL_FLOAT, vertexSize, vertices) // Set Texture Coords Pointer
    }
    if (hasNormals) {
      gl.glEnableClientState(GL10.GL_NORMAL_ARRAY) // Enable Normals in Vertices
      vertices.position(positionCnt + (if (hasColor) COLOR_CNT else 0) + (if (hasTexCoords) TEXCOORD_CNT else 0)) // Set Vertex Buffer to Normals (NOTE: position based on whether color/texcoords is also specified)
      gl.glNormalPointer(GL10.GL_FLOAT, vertexSize, vertices) // Set Normals Pointer
    }
  }
  /**
   * Draw
   * D: draw the currently bound vertices in the vertex/index buffers
   * USAGE: can only be called after calling bind() for this buffer.
   * A: primitiveType - the type of primitive to draw
   * offset - the offset in the vertex/index buffer to start at
   * numVertices - the number of vertices (indices) to draw
   */
  def draw(primitiveType: Int, offset: Int, numVertices: Int) {
    if (indices != null) { // IF Indices Exist
      indices.position(offset) // Set Index Buffer to Specified Offset
      gl.glDrawElements(primitiveType, numVertices, GL10.GL_UNSIGNED_SHORT, indices) // Draw Indexed
    } else { // ELSE No Indices Exist
      gl.glDrawArrays(primitiveType, offset, numVertices) // Draw Direct (Array)
    }
  }
  /**
   * Unbind
   * D: clear binding states when done rendering batches.
   * USAGE: call once before calling draw() multiple times for this buffer.
   */
  def unbind() {
    if (hasColor) // IF Vertices Have Color
      gl.glDisableClientState(GL10.GL_COLOR_ARRAY) // Clear Color State
    if (hasTexCoords) // IF Vertices Have Texture Coords
      gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY) // Clear Texture Coords State
    if (hasNormals) // IF Vertices Have Normals
      gl.glDisableClientState(GL10.GL_NORMAL_ARRAY) // Clear Normals State
  }
  /**
   * Draw Full
   * D: draw the vertices in the vertex/index buffers
   * NOTE: unoptimized version! use bind()/draw()/unbind() for batches
   * A: primitiveType - the type of primitive to draw
   * offset - the offset in the vertex/index buffer to start at
   * numVertices - the number of vertices (indices) to draw
   */
  def drawFull(primitiveType: Int, offset: Int, numVertices: Int) {
    gl.glEnableClientState(GL10.GL_VERTEX_ARRAY) // Enable Position in Vertices
    vertices.position(0) // Set Vertex Buffer to Position
    gl.glVertexPointer(positionCnt, GL10.GL_FLOAT, vertexSize, vertices) // Set Vertex Pointer
    if (hasColor) { // IF Vertices Have Color
      gl.glEnableClientState(GL10.GL_COLOR_ARRAY) // Enable Color in Vertices
      vertices.position(positionCnt) // Set Vertex Buffer to Color
      gl.glColorPointer(COLOR_CNT, GL10.GL_FLOAT, vertexSize, vertices) // Set Color Pointer
    }
    if (hasTexCoords) { // IF Vertices Have Texture Coords
      gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY) // Enable Texture Coords in Vertices
      vertices.position(positionCnt + (if (hasColor) COLOR_CNT else 0)) // Set Vertex Buffer to Texture Coords (NOTE: position based on whether color is also specified)
      gl.glTexCoordPointer(TEXCOORD_CNT, GL10.GL_FLOAT, vertexSize, vertices) // Set Texture Coords Pointer
    }
    if (indices != null) { // IF Indices Exist
      indices.position(offset) // Set Index Buffer to Specified Offset
      gl.glDrawElements(primitiveType, numVertices, GL10.GL_UNSIGNED_SHORT, indices) // Draw Indexed
    } else { // ELSE No Indices Exist
      gl.glDrawArrays(primitiveType, offset, numVertices) // Draw Direct (Array)
    }
    if (hasTexCoords) // IF Vertices Have Texture Coords
      gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY) // Clear Texture Coords State
    if (hasColor) // IF Vertices Have Color
      gl.glDisableClientState(GL10.GL_COLOR_ARRAY) // Clear Color State
  }
  /**
   * Set Vertex Elements
   * D: use these methods to alter the values (position, color, textcoords, normals) for vertices
   * WARNING: these do NOT validate any values, ensure that the index AND specified
   * elements EXIST before using!!
   * A: x, y, z - the x,y,z position to set in buffer
   * r, g, b, a - the r,g,b,a color to set in buffer
   * u, v - the u,v texture coords to set in buffer
   * nx, ny, nz - the x,y,z normal to set in buffer
   */
  def setVtxPosition(vtxIdx: Int, x: Float, y: Float) {
    val index = vtxIdx * vertexStride // Calculate Actual Index
    vertices.put(index + 0, java.lang.Float.floatToRawIntBits(x)) // Set X
    vertices.put(index + 1, java.lang.Float.floatToRawIntBits(y)) // Set Y
  }
  def setVtxPosition(vtxIdx: Int, x: Float, y: Float, z: Float) {
    val index = vtxIdx * vertexStride // Calculate Actual Index
    vertices.put(index + 0, java.lang.Float.floatToRawIntBits(x)) // Set X
    vertices.put(index + 1, java.lang.Float.floatToRawIntBits(y)) // Set Y
    vertices.put(index + 2, java.lang.Float.floatToRawIntBits(z)) // Set Z
  }
  def setVtxColor(vtxIdx: Int, r: Float, g: Float, b: Float, a: Float) {
    val index = (vtxIdx * vertexStride) + positionCnt // Calculate Actual Index
    vertices.put(index + 0, java.lang.Float.floatToRawIntBits(r)) // Set Red
    vertices.put(index + 1, java.lang.Float.floatToRawIntBits(g)) // Set Green
    vertices.put(index + 2, java.lang.Float.floatToRawIntBits(b)) // Set Blue
    vertices.put(index + 3, java.lang.Float.floatToRawIntBits(a)) // Set Alpha
  }
  def setVtxColor(vtxIdx: Int, r: Float, g: Float, b: Float) {
    val index = (vtxIdx * vertexStride) + positionCnt // Calculate Actual Index
    vertices.put(index + 0, java.lang.Float.floatToRawIntBits(r)) // Set Red
    vertices.put(index + 1, java.lang.Float.floatToRawIntBits(g)) // Set Green
    vertices.put(index + 2, java.lang.Float.floatToRawIntBits(b)) // Set Blue
  }
  def setVtxColor(vtxIdx: Int, a: Float) {
    val index = (vtxIdx * vertexStride) + positionCnt // Calculate Actual Index
    vertices.put(index + 3, java.lang.Float.floatToRawIntBits(a)) // Set Alpha
  }
  def setVtxTexCoords(vtxIdx: Int, u: Float, v: Float) {
    val index = (vtxIdx * vertexStride) + positionCnt + (if (hasColor) COLOR_CNT else 0) // Calculate Actual Index
    vertices.put(index + 0, java.lang.Float.floatToRawIntBits(u)) // Set U
    vertices.put(index + 1, java.lang.Float.floatToRawIntBits(v)) // Set V
  }
  def setVtxNormal(vtxIdx: Int, x: Float, y: Float, z: Float) {
    val index = (vtxIdx * vertexStride) + positionCnt +
      (if (hasColor) COLOR_CNT else 0) + (if (hasTexCoords) TEXCOORD_CNT else 0) // Calculate Actual Index
    vertices.put(index + 0, java.lang.Float.floatToRawIntBits(x)) // Set X
    vertices.put(index + 1, java.lang.Float.floatToRawIntBits(y)) // Set Y
    vertices.put(index + 2, java.lang.Float.floatToRawIntBits(z)) // Set Z
  }
}
