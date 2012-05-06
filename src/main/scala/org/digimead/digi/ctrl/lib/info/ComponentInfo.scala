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

import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.regex.Pattern

import scala.Option.option2Iterable
import scala.actors.Futures.awaitAll
import scala.actors.Futures.future
import scala.xml.NodeSeq.seqToNodeSeq
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Version

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.BitmapFactory
import android.os.Parcelable
import android.os.Parcel
import android.util.DisplayMetrics

case class ComponentInfo(val id: String, // unique string / primary key
  val name: String,
  val version: String, // Version Not Serializable
  val description: String,
  val project: String, // Uri Not Serializable
  val thumb: Option[Array[Byte]], // Bitmap Not Serializable
  val origin: String,
  val license: String,
  val email: String,
  val iconHDPI: Option[Array[Byte]], // Bitmap Not Serializable
  val iconLDPI: Option[Array[Byte]], // Bitmap Not Serializable
  val iconMDPI: Option[Array[Byte]], // Bitmap Not Serializable
  val iconXHDPI: Option[Array[Byte]], // Bitmap Not Serializable
  val market: String,
  val componentPackage: String) extends Parcelable {
  def this(in: Parcel) = this(id = in.readString,
    name = in.readString,
    version = in.readString,
    description = in.readString,
    project = in.readString,
    thumb = {
      val dataLength = in.readInt
      if (dataLength == -1) {
        None
      } else {
        ComponentInfo.log.trace("set thumb array size to " + dataLength)
        val data = new Array[Byte](dataLength)
        in.readByteArray(data)
        Some(data)
      }
    },
    origin = in.readString,
    license = in.readString,
    email = in.readString,
    iconHDPI = {
      val dataLength = in.readInt
      if (dataLength == -1) {
        None
      } else {
        ComponentInfo.log.trace("set iconHDPI array size to " + dataLength)
        val data = new Array[Byte](dataLength)
        in.readByteArray(data)
        Some(data)
      }
    },
    iconLDPI = {
      val dataLength = in.readInt
      if (dataLength == -1) {
        None
      } else {
        ComponentInfo.log.trace("set iconLDPI array size to " + dataLength)
        val data = new Array[Byte](dataLength)
        in.readByteArray(data)
        Some(data)
      }
    },
    iconMDPI = {
      val dataLength = in.readInt
      if (dataLength == -1) {
        None
      } else {
        ComponentInfo.log.trace("set iconMDPI array size to " + dataLength)
        val data = new Array[Byte](dataLength)
        in.readByteArray(data)
        Some(data)
      }
    },
    iconXHDPI = {
      val dataLength = in.readInt
      if (dataLength == -1) {
        None
      } else {
        ComponentInfo.log.trace("set iconXHDPI array size to " + dataLength)
        val data = new Array[Byte](dataLength)
        in.readByteArray(data)
        Some(data)
      }
    },
    market = in.readString,
    componentPackage = in.readString)
  def writeToParcel(out: Parcel, flags: Int) {
    ComponentInfo.log.debug("writeToParcel ComponentInfo with flags " + flags)
    out.writeString(id)
    out.writeString(name)
    out.writeString(version)
    out.writeString(description)
    out.writeString(project)
    thumb match {
      case Some(thumb) =>
        out.writeInt(thumb.length)
        out.writeByteArray(thumb)
      case _ =>
        out.writeInt(-1)
    }
    out.writeString(origin)
    out.writeString(license)
    out.writeString(email)
    iconHDPI match {
      case Some(icon) =>
        out.writeInt(icon.length)
        out.writeByteArray(icon)
      case _ =>
        out.writeInt(-1)
    }
    iconLDPI match {
      case Some(icon) =>
        out.writeInt(icon.length)
        out.writeByteArray(icon)
      case _ =>
        out.writeInt(-1)
    }
    iconMDPI match {
      case Some(icon) =>
        out.writeInt(icon.length)
        out.writeByteArray(icon)
      case _ =>
        out.writeInt(-1)
    }
    iconXHDPI match {
      case Some(icon) =>
        out.writeInt(icon.length)
        out.writeByteArray(icon)
      case _ =>
        out.writeInt(-1)
    }
    out.writeString(market)
    out.writeString(componentPackage)
  }
  def describeContents() = 0
  def getDescription(): String = {
    Seq("Name: " + name,
      "Version: " + version,
      "Description: " + description,
      "Project: " + project,
      "Market: " + market,
      "License: " + license,
      "E-Mail: " + email,
      "Origin: " + origin).mkString("\n")
  }
  def getIcon(context: Context): Option[Array[Byte]] = context.getResources.getDisplayMetrics.densityDpi match {
    case DisplayMetrics.DENSITY_LOW => iconLDPI
    case DisplayMetrics.DENSITY_MEDIUM => iconMDPI
    case DisplayMetrics.DENSITY_HIGH => iconHDPI
    case low if low < DisplayMetrics.DENSITY_LOW => iconLDPI
    case large if large > DisplayMetrics.DENSITY_HIGH => iconXHDPI
  }
  def getIconBitmap(context: Context) =
    getIcon(context).map(icon => BitmapFactory.decodeByteArray(icon, 0, icon.length))
  def getIconDrawable(context: Context) =
    getIconBitmap(context).map(new BitmapDrawable(_))
}

object ComponentInfo extends Logging {
  override protected[lib] val log = Logging.getLogger(this)
  final val CREATOR: Parcelable.Creator[ComponentInfo] = new Parcelable.Creator[ComponentInfo]() {
    def createFromParcel(in: Parcel): ComponentInfo = try {
      log.debug("createFromParcel new ComponentInfo")
      new ComponentInfo(in)
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    def newArray(size: Int): Array[ComponentInfo] = new Array[ComponentInfo](size)
  }
  final protected val stringLimit = 1024
  val emailPattern = Pattern.compile("""^[a-z0-9._%-]+@(?:[a-z0-9-]+\.)+[a-z]{2,4}$""", Pattern.CASE_INSENSITIVE)
  val packagePattern = Pattern.compile("""^([a-z_]{1}[a-z0-9_]*(\.[a-z_]{1}[a-z0-9_]*)*)$""", Pattern.CASE_INSENSITIVE)
  @Loggable
  def apply(xml: Elem, locale: String, localeLanguage: String, iconExtractor: (Seq[(IconType, String)]) => Seq[Option[Array[Byte]]]): Option[ComponentInfo] = try {
    log.debug("check XML content")
    val id = check(Check.Text("id"), xml \\ "id" headOption)
    val name = check(Check.Text("name"), suitable(xml \\ "name", locale, localeLanguage))
    val version = check(Check.Version("version"), xml \\ "version" headOption)
    val description = check(Check.Text("description"), suitable(xml \\ "description", locale, localeLanguage))
    val project = future { check(Check.URL("project"), xml \\ "project" headOption) }
    val thumb = future { check(Check.URL("thumb"), xml \\ "thumb" headOption) }
    val origin = check(Check.Text("origin"), xml \\ "origin" headOption)
    val license = check(Check.Text("license"), xml \\ "license" headOption)
    val email = check(Check.EMail("email"), xml \\ "email" headOption)
    val icon_hdpi = future { check(Check.URL("icon-hdpi"), xml \\ "icon-hdpi" headOption) }
    val icon_ldpi = future { check(Check.URL("icon-ldpi"), xml \\ "icon-ldpi" headOption) }
    val icon_mdpi = future { check(Check.URL("icon-mdpi"), xml \\ "icon-mdpi" headOption) }
    val icon_xhdpi = future { check(Check.URL("icon-xhdpi"), xml \\ "icon-xhdpi" headOption) }
    val market = future { check(Check.MarketURL("market"), xml \\ "market" headOption) }
    val packageName = check(Check.PackageName("package-name"), xml \\ "package-name" headOption)
    awaitAll(DTimeout.normal, project, thumb, icon_hdpi, icon_ldpi, icon_mdpi, icon_xhdpi, market)
    val artifact: Option[ComponentInfo] = (for {
      id <- id
      name <- name
      version <- version
      description <- description
      project <- project()
      thumb <- thumb()
      origin <- origin
      license <- license
      email <- email
      icon_hdpi <- icon_hdpi()
      icon_ldpi <- icon_ldpi()
      icon_mdpi <- icon_mdpi()
      icon_xhdpi <- icon_xhdpi()
      market <- market()
      packageName <- packageName
    } yield {
      log.debug("create FetchedItem from descriptor")
      val Seq(thumbIcon, iconLDPI, iconMDPI, iconHDPI, iconXHDPI) =
        iconExtractor(Seq((Thumbnail, thumb), (LDPI, icon_ldpi), (MDPI, icon_mdpi), (HDPI, icon_hdpi), (XHDPI, icon_xhdpi)))
      new ComponentInfo(id, name, version, description, project,
        thumbIcon, origin, license, email, iconHDPI, iconLDPI,
        iconMDPI, iconXHDPI, market, packageName)
    })
    if (artifact == None)
      log.warn("broken descriptor > " + Seq("id: ", id, ", name: ", name, ", version: ", version,
        ", description: ", description, ", project: ", project, ", thumb: ", thumb,
        ", origin: ", origin, ", license: ", license, ", email: ", email,
        ", icon-hdpi: ", icon_hdpi, ", icon-ldpi: ", icon_ldpi, ", icon-mdpi: ", icon_mdpi,
        ", icon-xhdpi: ", icon_xhdpi, ", market: ", market, ", package-name: ", packageName).mkString)
    artifact
  } catch {
    case e =>
      log.warn(e.getMessage())
      None
  }
  @Loggable
  private def check(kind: Check.CheckType, in: Option[Node]): Option[String] = in.flatMap {
    in =>
      val string = in.text
      // check size
      if (string.length > stringLimit) {
        log.warn("descriptor field \"" + kind.field + "\" size too large: " + string.length + " vs limit " + stringLimit)
        return None
      }
      val c = string.find(!isPrintableChar(_))
      if (c != None) {
        log.warn("descriptor field \"" + kind.field + "\" contain unprintable characters with code " + c.get.toInt.toString)
        return None
      }
      check(kind, string)
  }
  private def check(kind: Check.CheckType, in: String): Option[String] = kind match {
    case Check.Text(field) =>
      Some(in)
    case Check.EMail(field) =>
      if (!emailPattern.matcher(in).matches()) {
        log.warn("descriptor field \"" + kind.field + "\" contain broken email " + in)
        return None
      }
      Some(in)
    case Check.URL(field) =>
      try {
        val url = new URL(in)
        url.openConnection()
      } catch {
        case e: MalformedURLException =>
          log.warn("descriptor field \"" + kind.field + "\" contain the URL is not in a valid form " + in)
          return None
        case e: IOException =>
          log.trace("descriptor field \"" + kind.field + "\" contain valid URL but the connection to target couldn't be established " + in)
      }
      Some(in)
    case Check.MarketURL(field) =>
      if (!in.startsWith("market://details")) {
        log.warn("descriptor field \"" + kind.field + "\" contain invalid market URL " + in)
        return None
      }
      check(Check.URL(field), in.replaceFirst("market://details", "https://market.android.com/details")).map(replaced => in)
    case Check.Version(field) =>
      val version = new Version(in)
      if (in.length() > 128 || (version.getMajorVersion() == 0 && version.getMinorVersion() == 0 &&
        version.getIncrementalVersion() == 0 && version.getBuildNumber() == 0 && version.getQualifier.isEmpty)) {
        log.warn("descriptor field \"" + kind.field + "\" contain invalid version \"" + in + "\"")
        return None
      }
      Some(in)
    case Check.PackageName(field) =>
      if (!packagePattern.matcher(in).matches()) {
        log.warn("descriptor field \"" + kind.field + "\" contain broken package name")
        return None
      }
      Some(in)
  }
  def isPrintableChar(c: Char): Boolean = {
    val block = Character.UnicodeBlock.of(c)
    (c == '\r' || c == '\n' || c == '\t') || ((!Character.isISOControl(c)) && block != null && block != Character.UnicodeBlock.SPECIALS)
  }
  @Loggable
  private def suitable(seq: NodeSeq, locale: String, localeLanguage: String): Option[Node] = {
    // Some[ Tuple [ lang , string ] ] 
    val variants = for (node <- seq) yield {
      node.attribute("lang") match {
        case None =>
          Some("", node)
        case Some(attr) =>
          attr.text match {
            case l if l == locale =>
              Some(locale, node)
            case l if l == localeLanguage =>
              Some(localeLanguage, node)
            case _ =>
              None
          }
      }
    }
    variants.flatten.sortBy(_._1).map(_._2).lastOption // longest is the language preferred
  }
  sealed trait IconType
  case object Thumbnail extends IconType
  case object LDPI extends IconType
  case object MDPI extends IconType
  case object HDPI extends IconType
  case object XHDPI extends IconType
  object Check {
    sealed trait CheckType {
      val field: String
    }
    case class Text(val field: String) extends CheckType
    case class EMail(val field: String) extends CheckType
    case class URL(val field: String) extends CheckType
    case class MarketURL(val field: String) extends CheckType
    case class Version(val field: String) extends CheckType
    case class PackageName(val field: String) extends CheckType
  }
}
