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

/*
 * function convertToHex copied from Google source file available under Apache 2.0
 */

/*

   crypt adapted from
   
   MD5Crypt.java

   Created: 3 November 1999
   Release: $Name:  $
   Version: $Revision: 7678 $
   Last Mod Date: $Date: 2007-12-28 11:51:49 -0600 (Fri, 28 Dec 2007) $
   Java Port By: Jonathan Abbey, jonabbey@arlut.utexas.edu
   Original C Version:
   ----------------------------------------------------------------------------
   "THE BEER-WARE LICENSE" (Revision 42):
   <phk@login.dknet.dk> wrote this file.  As long as you retain this notice you
   can do whatever you want with this stuff. If we meet some day, and you think
   this stuff is worth it, you can buy me a beer in return.   Poul-Henning Kamp
   ----------------------------------------------------------------------------

   This Java Port is  

     Copyright (c) 1999-2008 The University of Texas at Austin.

     All rights reserved.

     Redistribution and use in source and binary form are permitted
     provided that distributions retain this entire copyright notice
     and comment. Neither the name of the University nor the names of
     its contributors may be used to endorse or promote products
     derived from this software without specific prior written
     permission. THIS SOFTWARE IS PROVIDED "AS IS" AND WITHOUT ANY
     EXPRESS OR IMPLIED WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE
     IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
     PARTICULAR PURPOSE.

*/

package org.digimead.digi.ctrl.lib.util;

import java.security.MessageDigest
import scala.collection.mutable.ArrayBuffer

object Hash {
  private val itoa64 = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private val SALTCHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
  private val chars = Map(0 -> '0', 1 -> '1', 2 -> '2', 3 -> '3', 4 -> '4', 5 -> '5', 6 -> '6', 7 -> '7',
    8 -> '8', 9 -> '9', 10 -> 'a', 11 -> 'b', 12 -> 'c', 13 -> 'd', 14 -> 'e', 15 -> 'f');
  private def convertToHex(data: Array[Byte]): String = {
    val buf = new StringBuilder();
    for (b <- data) {
      buf.append(chars(b >>> 4 & 0x0F));
      buf.append(chars(b & 0x0F));
    }
    buf.toString();
  }

  def apply(text: String, algorithm: String = "SHA-1"): String = {
    val md = MessageDigest.getInstance(algorithm);
    md.update(text.getBytes("UTF-8"), 0, text.length());
    convertToHex(md.digest());
  }
  /**
   * <p>This method actually generates a OpenBSD/FreeBSD/Linux PAM compatible
   * md5-encoded password hash from a plaintext password and a
   * salt.</p>
   *
   * <p>The resulting string will be in the form '$1$&lt;salt&gt;$&lt;hashed mess&gt;</p>
   *
   * @param password Plaintext password
   *
   * @return An OpenBSD/FreeBSD/Linux-compatible md5-hashed password field.
   */
  def crypt(password: String): String = {
    val salt = new StringBuffer();
    val randgen = new java.util.Random();
    while (salt.length() < 8) {
      val index = (randgen.nextFloat() * SALTCHARS.length()).asInstanceOf[Int]
      salt.append(SALTCHARS.substring(index, index + 1))
    }
    crypt(password, salt.toString())
  }
  /**
   * <p>This method actually generates a OpenBSD/FreeBSD/Linux PAM compatible
   * md5-encoded password hash from a plaintext password and a
   * salt.</p>
   *
   * <p>The resulting string will be in the form '$1$&lt;salt&gt;$&lt;hashed mess&gt;</p>
   *
   * @param password Plaintext password
   * @param salt A short string to use to randomize md5.  May start with $1$, which
   *             will be ignored.  It is explicitly permitted to pass a pre-existing
   *             MD5Crypt'ed password entry as the salt.  crypt() will strip the salt
   *             chars out properly.
   *
   * @return An OpenBSD/FreeBSD/Linux-compatible md5-hashed password field.
   */
  def crypt(password: String, salt: String): String =
    crypt(password, salt, "$1$")
  /**
   * <p>This method generates an Apache MD5 compatible
   * md5-encoded password hash from a plaintext password and a
   * salt.</p>
   *
   * <p>The resulting string will be in the form '$apr1$&lt;salt&gt;$&lt;hashed mess&gt;</p>
   *
   * @param password Plaintext password
   *
   * @return An Apache-compatible md5-hashed password string.
   */
  def apacheCrypt(password: String): String = {
    val salt = new StringBuffer();
    val randgen = new java.util.Random();
    while (salt.length() < 8) {
      val index = (randgen.nextFloat() * SALTCHARS.length()).asInstanceOf[Int]
      salt.append(SALTCHARS.substring(index, index + 1))
    }
    apacheCrypt(password, salt.toString())
  }
  /**
   * <p>This method actually generates an Apache MD5 compatible
   * md5-encoded password hash from a plaintext password and a
   * salt.</p>
   *
   * <p>The resulting string will be in the form '$apr1$&lt;salt&gt;$&lt;hashed mess&gt;</p>
   *
   * @param password Plaintext password
   * @param salt A short string to use to randomize md5.  May start with $apr1$, which
   *             will be ignored.  It is explicitly permitted to pass a pre-existing
   *             MD5Crypt'ed password entry as the salt.  crypt() will strip the salt
   *             chars out properly.
   *
   * @return An Apache-compatible md5-hashed password string.
   */
  def apacheCrypt(password: String, salt: String): String =
    crypt(password, salt, "$apr1$")
  /**
   * <p>This method actually generates md5-encoded password hash from
   * a plaintext password, a salt, and a magic string.</p>
   *
   * <p>There are two magic strings that make sense to use here.. '$1$' is the
   * magic string used by the FreeBSD/Linux/OpenBSD MD5Crypt algorithm, and
   * '$apr1$' is the magic string used by the Apache MD5Crypt algorithm.</p>
   *
   * <p>The resulting string will be in the form '&lt;magic&gt;&lt;salt&gt;$&lt;hashed mess&gt;</p>
   *
   * @param password Plaintext password
   * @param salt A short string to use to randomize md5.  May start
   * with the magic string, which will be ignored.  It is explicitly
   * permitted to pass a pre-existing MD5Crypt'ed password entry as
   * the salt.  crypt() will strip the salt chars out properly.
   * @param magic Either "$apr1$" or "$1$", which controls whether we
   * are doing Apache-style or FreeBSD-style md5Crypt.
   *
   * @return An md5-hashed password string.
   */
  def crypt(password: String, _salt: String, magic: String): String = {
    /* This string is magic for this algorithm.  Having it this way,
     * we can get get better later on */
    var salt = _salt
    var finalState: Array[Byte] = null
    var ctx: MessageDigest = null
    var ctx1: MessageDigest = null
    var l: Long = 0;
    /* -- */
    /* Refine the Salt first */
    /* If it starts with the magic string, then skip that */
    if (salt.startsWith(magic))
      salt = salt.substring(magic.length())
    /* It stops at the first '$', max 8 chars */
    if (salt.indexOf('$') != -1)
      salt = salt.substring(0, salt.indexOf('$'))
    if (salt.length() > 8)
      salt = salt.substring(0, 8)
    ctx = MessageDigest.getInstance("MD5")
    ctx.update(password.getBytes()) // The password first, since that is what is most unknown
    ctx.update(magic.getBytes()) // Then our magic string
    ctx.update(salt.getBytes()) // Then the raw salt
    /* Then just as many characters of the MD5(pw,salt,pw) */
    ctx1 = MessageDigest.getInstance("MD5")
    ctx1.update(password.getBytes())
    ctx1.update(salt.getBytes())
    ctx1.update(password.getBytes())
    finalState = ctx1.digest()
    for (pl <- password.length() until (0, -16))
      ctx.update(finalState, 0, if (pl > 16) 16 else pl)
    /* the original code claimed that finalState was being cleared
       to keep dangerous bits out of memory, but doing this is also
       required in order to get the right output. */
    clearbits(finalState)

    /* Then something really weird... */
    var i = password.length()
    /* for (int i = password.length(); i != 0; i >>>=1) */
    while (i != 0) {
      if ((i & 1) != 0)
        ctx.update(finalState, 0, 1)
      else
        ctx.update(password.getBytes(), 0, 1)
      i >>>= 1
    }

    finalState = ctx.digest()
    /*
     * and now, just to make sure things don't run too fast
     * On a 60 Mhz Pentium this takes 34 msec, so you would
     * need 30 seconds to build a 1000 entry dictionary...
     *
     * (The above timings from the C version)
     */
    for (i <- 0 until 1000) {
      ctx1.reset()
      if ((i & 1) != 0)
        ctx1.update(password.getBytes())
      else
        ctx1.update(finalState, 0, 16)
      if ((i % 3) != 0)
        ctx1.update(salt.getBytes())
      if ((i % 7) != 0)
        ctx1.update(password.getBytes())
      if ((i & 1) != 0)
        ctx1.update(finalState, 0, 16)
      else
        ctx1.update(password.getBytes())
      finalState = ctx1.digest()
    }
    /* Now make the output string */
    val result = new StringBuffer()
    result.append(magic)
    result.append(salt)
    result.append("$")

    l = (bytes2u(finalState(0)) << 16) | (bytes2u(finalState(6)) << 8) | bytes2u(finalState(12))
    result.append(to64(l, 4))

    l = (bytes2u(finalState(1)) << 16) | (bytes2u(finalState(7)) << 8) | bytes2u(finalState(13))
    result.append(to64(l, 4))

    l = (bytes2u(finalState(2)) << 16) | (bytes2u(finalState(8)) << 8) | bytes2u(finalState(14))
    result.append(to64(l, 4))

    l = (bytes2u(finalState(3)) << 16) | (bytes2u(finalState(9)) << 8) | bytes2u(finalState(15))
    result.append(to64(l, 4))

    l = (bytes2u(finalState(4)) << 16) | (bytes2u(finalState(10)) << 8) | bytes2u(finalState(5))
    result.append(to64(l, 4))

    l = bytes2u(finalState(11))
    result.append(to64(l, 2))

    /* Don't leave anything around in vm they could use. */
    clearbits(finalState)
    return result.toString()
  }
  def clearbits(bits: Array[Byte]): Unit =
    for (i <- 0 until bits.length)
      bits(i) = 0
  /**
   * This method tests a plaintext password against a md5Crypt'ed hash and returns
   * true if the password matches the hash.
   *
   * This method will work properly whether the hashtext was crypted
   * using the default FreeBSD md5Crypt algorithm or the Apache
   * md5Crypt variant.
   *
   * @param plaintextPass The plaintext password text to test.
   * @param md5CryptText The Apache or FreeBSD-md5Crypted hash used to authenticate the plaintextPass.
   */
  def verifyPassword(plaintextPass: String, md5CryptText: String): Boolean = {
    if (md5CryptText.startsWith("$1$"))
      return md5CryptText.equals(crypt(plaintextPass, md5CryptText))
    else if (md5CryptText.startsWith("$apr1$"))
      return md5CryptText.equals(apacheCrypt(plaintextPass, md5CryptText))
    else
      throw new RuntimeException("Bad md5CryptText")
  }
  /**
   * convert an encoded unsigned byte value into a int
   * with the unsigned value.
   */
  def bytes2u(inp: Byte): Int =
    (inp & 0xff).asInstanceOf[Int]
  def to64(_v: Long, _size: Int): String = {
    val result = new StringBuffer();
    var size = _size
    var v = _v
    while (size > 0) {
      size -= 1
      result.append(itoa64.charAt((v & 0x3f).asInstanceOf[Int]))
      v >>>= 6
    }
    return result.toString()
  }

}
