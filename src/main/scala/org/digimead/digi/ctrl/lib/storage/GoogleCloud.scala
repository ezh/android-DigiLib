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

package org.digimead.digi.ctrl.lib.storage

import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.ArrayList
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

import scala.actors.Futures
import scala.io.Codec.charset2codec

import org.apache.http.HttpHost
import org.apache.http.HttpVersion
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.conn.params.ConnRoutePNames
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.message.BasicNameValuePair
import org.apache.http.params.CoreProtocolPNames
import org.apache.http.util.EntityUtils
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.json.JSONObject

import android.net.http.AndroidHttpClient
import android.provider.Settings
import android.util.Base64

/*
 * mobile application act as web server ;-) strictly within Google OAuth2 draft10 manual, Ezh
 */
object GoogleCloud extends Logging {
  private lazy val accessToken = new AtomicReference[Option[AccessToken]](None)
  val tokenURL = "https://accounts.google.com/o/oauth2/token"
  val uploadURL = "http://commondatastorage.googleapis.com"
  protected lazy val httpclient = AppComponent.Context.map {
    context =>
      val client = AndroidHttpClient.newInstance("Android")
      client.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1)
      /*
       * attach proxy
       */
      val proxyString = Option(Settings.Secure.getString(context.getContentResolver(), Settings.System.HTTP_PROXY)).
        getOrElse(Settings.Secure.getString(context.getContentResolver(), Settings.Secure.HTTP_PROXY))
      if (proxyString != null) {
        log.info("detect proxy at " + proxyString)
        try {
          val Array(proxyAddress, proxyPort) = proxyString.split(":")
          val proxy = new HttpHost(proxyAddress, Integer.parseInt(proxyPort), "http")
          client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy)
        } catch {
          case e =>
            log.warn(e.getMessage(), e)
        }
      } else
        log.info("proxy not detected")
      client
  }
  val upload: (Seq[File], String, => Any) => Boolean = uploadViaApache
  def uploadViaApache(files: Seq[File], prefix: String = "", callback: => Any): Boolean = AppComponent.Context.flatMap {
    context =>
      log.debug("upload files via Apache client with default credentials")
      for {
        clientID_64 <- Android.getString(context, "APPLICATION_GS_CLIENT_ID")
        clientSecret_64 <- Android.getString(context, "APPLICATION_GS_CLIENT_SECRET")
        refreshToken_64 <- Android.getString(context, "APPLICATION_GS_TOKEN")
        backet_64 <- Android.getString(context, "APPLICATION_GS_BUCKET")
        httpclient <- httpclient
      } yield try {
        val clientID = new String(Base64.decode(clientID_64, Base64.DEFAULT), "UTF-8")
        val clientSecret = new String(Base64.decode(clientSecret_64, Base64.DEFAULT), "UTF-8")
        val refreshToken = new String(Base64.decode(refreshToken_64, Base64.DEFAULT), "UTF-8")
        val bucket = new String(Base64.decode(backet_64, Base64.DEFAULT), "UTF-8")
        getAccessToken(clientID, clientSecret, refreshToken) match {
          case Some(token) =>
            try {
              val uri = new URI(Seq(uploadURL, bucket, URLEncoder.encode(prefix, "utf-8")).mkString("/"))
              // upload byteArray
              val host = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme())
              val futures = files.map(file => Futures.future {
                val uri = new URI(Seq(uploadURL, bucket, URLEncoder.encode(prefix + file.getName, "utf-8")).mkString("/"))
                val httpput = new HttpPut(uri.getPath())
                httpput.setHeader("x-goog-api-version", "2")
                httpput.setHeader("Authorization", "OAuth " + token.access_token)
                val source = scala.io.Source.fromFile(file)(scala.io.Codec.ISO8859)
                val byteArray = source.map(_.toByte).toArray
                source.close()
                httpput.setEntity(new ByteArrayEntity(byteArray))
                val response = httpclient.execute(host, httpput)
                val entity = response.getEntity()
                log.debug(uri + " result: " + response.getStatusLine())
                response.getStatusLine().getStatusCode() match {
                  case 200 =>
                    log.info("upload " + file.getName + " successful")
                    callback
                    true
                  case _ =>
                    log.warn("upload " + file.getName + " failed")
                    callback
                    false
                }
              })
              Futures.awaitAll(DTimeout.longest, futures: _*).forall(_ == Some(true))
            } catch {
              case e =>
                log.warn("unable to upload: ", e.getMessage)
                false
            }
          case None =>
            log.error("access token not available")
            false
        }
      } catch {
        case e =>
          log.error("unable to upload", e)
          false
      }
  } getOrElse false
  def getAccessToken(clientID: String, clientSecret: String, refreshToken: String): Option[AccessToken] = synchronized {
    accessToken.get.foreach(t => if (t.expired > System.currentTimeMillis) {
      log.debug("get cached access token " + t.access_token)
      return Some(t)
    } else {
      log.debug("cached access token expired: " + Common.dateString(new Date(t.expired)))
    })
    log.debug("aquire new access token")
    val result = httpclient.flatMap {
      httpclient =>
        try {
          val uri = new URI(tokenURL)
          val host = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme())
          val httppost = new HttpPost(uri.getPath())
          val nameValuePairs = new ArrayList[NameValuePair](2)
          nameValuePairs.add(new BasicNameValuePair("client_id", clientID))
          nameValuePairs.add(new BasicNameValuePair("client_secret", clientSecret))
          nameValuePairs.add(new BasicNameValuePair("refresh_token", refreshToken))
          nameValuePairs.add(new BasicNameValuePair("grant_type", "refresh_token"))
          httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs))
          val response = httpclient.execute(host, httppost)
          val entity = response.getEntity()
          log.debug(tokenURL + " result: " + response.getStatusLine())
          response.getStatusLine().getStatusCode() match {
            case 200 =>
              val o = new JSONObject(EntityUtils.toString(entity))
              Some(AccessToken(o.getString("access_token"),
                System.currentTimeMillis - 1000 + (o.getInt("expires_in") * 1000),
                o.getString("token_type")))
            case _ =>
              None
          }
        } catch {
          case e =>
            log.warn("unable to get access token: ", e.getMessage)
            None
        }
    }
    if (result != None)
      accessToken.set(result)
    result
  }
  case class AccessToken(
    val access_token: String,
    val expired: Long,
    val token_type: String)
}
