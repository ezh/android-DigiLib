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

package org.digimead.digi.ctrl.lib.dialog

import java.io.File
import java.io.FileFilter
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Comparator

import scala.collection.mutable.HashSet
import scala.collection.mutable.SynchronizedSet
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.R
import org.digimead.digi.ctrl.lib.androidext.XAlertDialog
import org.digimead.digi.ctrl.lib.androidext.XDialog
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.dialog.filechooser.FCCancel
import org.digimead.digi.ctrl.lib.dialog.filechooser.FCClear
import org.digimead.digi.ctrl.lib.dialog.filechooser.FCCopy
import org.digimead.digi.ctrl.lib.dialog.filechooser.FCCut
import org.digimead.digi.ctrl.lib.dialog.filechooser.FCDelete
import org.digimead.digi.ctrl.lib.dialog.filechooser.FCFilter
import org.digimead.digi.ctrl.lib.dialog.filechooser.FCHome
import org.digimead.digi.ctrl.lib.dialog.filechooser.FCMultiple
import org.digimead.digi.ctrl.lib.dialog.filechooser.FCOrder
import org.digimead.digi.ctrl.lib.dialog.filechooser.FCPaste
import org.digimead.digi.ctrl.lib.dialog.filechooser.FCUp
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.SyncVar

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

trait FileChooser extends XAlertDialog with FCHome with FCUp with FCFilter with FCOrder with FCPaste with FCClear
  with FCCopy with FCCut with FCDelete with FCCancel with FCMultiple {
  override lazy val extContent = AppComponent.Context.flatMap {
    case activity: Activity if FileChooser.maximize =>
      log.debug("maximize FileChooser")
      val inflater = activity.getApplicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
      val view = Option(inflater.inflate(R.layout.dialog_filechooser, null))
      view.foreach {
        view =>
          val displayRectangle = new Rect()
          val window = activity.getWindow()
          window.getDecorView().getWindowVisibleDisplayFrame(displayRectangle)
          view.setMinimumHeight(((displayRectangle.height() * 0.9f).toInt))
      }
      view
    case context =>
      val inflater = context.getApplicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
      Option(inflater.inflate(R.layout.dialog_filechooser, null))
  }
  override protected lazy val positive = Some((android.R.string.ok,
    new XDialog.ButtonListener(new WeakReference(FileChooser.this),
      Some((dialog: FileChooser) => {
        defaultButtonCallback(dialog)
        fileChooserResult.set((activeDirectory, Seq()))
        callbackOnResult(activeDirectory, Seq())
      }))))
  override protected lazy val negative = Some((android.R.string.cancel,
    new XDialog.ButtonListener(new WeakReference(FileChooser.this),
      Some(defaultButtonCallback))))
  protected lazy val lv = new WeakReference(extContent.map(_.findViewById(android.R.id.list).asInstanceOf[ListView]).getOrElse(null))
  private lazy val path = new WeakReference(extContent.map(l => l.findViewById(XResource.getId(l.getContext,
    "filechooser_path")).asInstanceOf[TextView]).getOrElse(null))
  /* file chooser data */
  protected val fileList = new ArrayList[File]()
  protected lazy val df = new SimpleDateFormat("yyyy-MM-dd HH:mm")
  @volatile private var fileFilter = new FileFilter { override def accept(file: File) = true }
  private val copiedFiles = new HashSet[File]() with SynchronizedSet[File]
  private val cutFiles = new HashSet[File]() with SynchronizedSet[File]
  private val selectionFiles = new HashSet[File]() with SynchronizedSet[File]
  // active directory, selected files
  @volatile protected var activeDirectory: File = null
  @volatile var callbackDismissOnItemClick = (a: Context, f: File) => false
  @volatile var callbackOnResult: (File, Seq[File]) => Any = (dir, selected) => {}
  protected val fileChooserResult = new SyncVar[(File, Seq[File])]()

  def initialPath(): Option[File]
  override def onResume() {
    super.onResume
    initialize
  }
  override def onDestroyView() {
    super.onDestroyView
    activeDirectory = null
    copiedFiles.clear
    cutFiles.clear
    selectionFiles.clear
    fileList.clear
    fileChooserResult.unset()
  }
  def initialize(): Unit = try {
    initializeHome
    initializeUp
    initializeFilter
    initializeOrder
    initializePaste
    initializeClear
    initializeCopy
    initializeCut
    initializeDelete
    initializeCancel
    initializeMultiple
    initializeListView
    /*
     * initialize sorting
     */
    //sorting.add(R.string.filechooser_action_sorting_name)
    //sorting.add(R.string.filechooser_action_sorting_size)
    //sorting.add(R.string.filechooser_action_sorting_date)
    //sortingValueLabel.add(this.getText(R.string.filechooser_action_sorting_name).toString())
    //sortingValueLabel.add(this.getText(R.string.filechooser_action_sorting_size).toString())
    //sortingValueLabel.add(this.getText(R.string.filechooser_action_sorting_date).toString())
    //
    // clear data variables
    copiedFiles.clear
    cutFiles.clear
    selectionFiles.clear
    fileList.clear
    // show content
    (for {
      path <- path.get
      initialPath <- initialPath
    } yield {
      path.setText(initialPath.getAbsolutePath())
      showDirectory(initialPath)
    }).getOrElse({ throw new RuntimeException("unable to show FileChooser with pathElement:" + path + " and initial path:" + initialPath) })
  } catch {
    case e =>
      log.error(e.getMessage, e)
      dismiss
  }
  def initializeListView() = lv.get.foreach {
    lv =>
      log.debug("FileChooser::initializeListView")
      lv.setAdapter(new ArrayAdapter[File](getDialogActivity, android.R.layout.simple_list_item_2,
        android.R.id.text1, fileList) {
        override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
          val view = super.getView(position, convertView, parent)
          val text1 = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
          val text2 = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
          val item = getItem(position)
          text1.setText(item.getName)
          if (item.isDirectory)
            text1.setTextColor(text1.getContext.getResources.getColor(android.R.color.primary_text_dark))
          else
            text1.setTextColor(text1.getContext.getResources.getColor(android.R.color.secondary_text_dark))
          val d = if (item.isDirectory) "d" else "-"
          val r = if (item.canRead()) "r" else "-"
          val w = if (item.canWrite()) "w" else "-"
          val x = if (item.canExecute()) "x" else "-"
          text2.setText("p:%s%s%s%s m:%s s:%skb".format(d, r, w, x, item.lastModified match {
            case m if m > 0 => df.format(m)
            case unknown => "*Unknown*"
          }, item.length / 1024))
          view
        }
      })
      lv.setOnItemClickListener(new AdapterView.OnItemClickListener {
        def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) =
          FileChooser.this.onItemClick(parent, view, position, id)
      })
  }
  def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) {
    val file = {
      val want = parent.getAdapter.asInstanceOf[ArrayAdapter[File]].getItem(position)
      if (want.getName == "..")
        want.getParentFile.getParentFile.getAbsoluteFile
      else
        want
    }
    log.debug("item click " + file)
    if (file.isDirectory) {
      if (file.canExecute && file.canRead) {
        //        Toast.makeText(view.getContext, XResource.getString(view.getContext, "filechooser_change_directory_to").
        //          getOrElse("change directory to %s").format(file), Toast.LENGTH_SHORT).show()
        showDirectory(file)
      } else {
        Toast.makeText(view.getContext, XResource.getString(view.getContext, "filechooser_change_directory_to_failed").
          getOrElse("unable to change directory to %s").format(file), Toast.LENGTH_SHORT).show()
      }
    } else {
      if (callbackDismissOnItemClick(view.getContext, file)) {
        fileChooserResult.set((activeDirectory, Seq(file)))
        dismiss
        callbackOnResult(activeDirectory, Seq(file))
      }
    }
  }
  protected def showDirectory(file: File) = for {
    lv <- lv.get
    path <- path.get
  } {
    log.debug("show directory " + file)
    path.setText(file.getAbsolutePath())
    activeDirectory = file
    val adapter = lv.getAdapter().asInstanceOf[ArrayAdapter[File]]
    adapter.setNotifyOnChange(false)
    adapter.clear
    val (dirs, files) = file.listFiles(fileFilter).partition(_.isDirectory)
    if (activeDirectory.getParentFile != null)
      adapter.add(new File(activeDirectory, ".."))
    dirs.sortBy(_.getName).foreach(adapter.add)
    files.sortBy(_.getName).foreach(adapter.add)
    adapter.setNotifyOnChange(true)
    adapter.notifyDataSetChanged()
  }
}

object FileChooser extends Logging {
  @volatile var maximize = true
  class DirAlphaComparator extends Comparator[File] {
    def compare(filea: File, fileb: File): Int = {
      if (filea.isDirectory() && !fileb.isDirectory()) -1
      else if (!filea.isDirectory() && fileb.isDirectory()) 1
      else filea.getName().compareToIgnoreCase(fileb.getName())
    }
  }
  class DirSizeComparator extends Comparator[File] {
    def compare(filea: File, fileb: File): Int = {
      if (filea.isDirectory() && !fileb.isDirectory()) -1
      else if (!filea.isDirectory() && fileb.isDirectory()) 1
      else {
        if (filea.length() > fileb.length()) 1
        else if (filea.length() < fileb.length()) -1
        else 0
      }
    }
  }
  class DirDateComparator extends Comparator[File] {
    def compare(filea: File, fileb: File): Int = {
      if (filea.lastModified() > fileb.lastModified()) 1
      else if (filea.lastModified() < fileb.lastModified()) -1
      else 0
    }
  }
}
