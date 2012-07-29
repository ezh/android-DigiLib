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
import java.util.concurrent.atomic.AtomicReference

import scala.annotation.implicitNotFound
import scala.collection.mutable.HashSet
import scala.collection.mutable.SynchronizedSet
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.util.SyncVar

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

object FileChooser extends Logging {
  @volatile private var dactivity = new WeakReference[Activity](null)
  @volatile private var dialog: Option[AlertDialog] = None
  @volatile private var layout: Option[LinearLayout] = None
  @volatile private var inflater: Option[LayoutInflater] = None
  @volatile private var fileFilter = new FileFilter { override def accept(file: File) = true }
  @volatile private var onFileClickDismissCb = (a: Context, f: File) => false
  @volatile private var onResult: (Context, File, Seq[File], AnyRef) => Any = (context, dir, selected, stash) => {}
  private lazy val lv = new WeakReference(layout.map(_.findViewById(android.R.id.list).asInstanceOf[ListView]).getOrElse(null))
  private lazy val path = new WeakReference(layout.map(l => l.findViewById(XResource.getId(l.getContext, "filechooser_path")).asInstanceOf[TextView]).getOrElse(null))
  private lazy val home = new WeakReference(layout.map(l => l.findViewById(XResource.getId(l.getContext, "filechooser_home")).asInstanceOf[Button]).getOrElse(null))
  private lazy val up = new WeakReference(layout.map(l => l.findViewById(XResource.getId(l.getContext, "filechooser_up")).asInstanceOf[Button]).getOrElse(null))
  private lazy val filter = new WeakReference(layout.map(l => l.findViewById(XResource.getId(l.getContext, "filechooser_preference")).asInstanceOf[Button]).getOrElse(null))
  private lazy val order = new WeakReference(layout.map(l => l.findViewById(XResource.getId(l.getContext, "filechooser_order")).asInstanceOf[Button]).getOrElse(null))
  private lazy val paste = new WeakReference(layout.map(l => l.findViewById(XResource.getId(l.getContext, "filechooser_paste")).asInstanceOf[Button]).getOrElse(null))
  private lazy val clear = new WeakReference(layout.map(l => l.findViewById(XResource.getId(l.getContext, "filechooser_clear")).asInstanceOf[Button]).getOrElse(null))
  private lazy val copy = new WeakReference(layout.map(l => l.findViewById(XResource.getId(l.getContext, "filechooser_copy")).asInstanceOf[Button]).getOrElse(null))
  private lazy val cut = new WeakReference(layout.map(l => l.findViewById(XResource.getId(l.getContext, "filechooser_cut")).asInstanceOf[Button]).getOrElse(null))
  private lazy val delete = new WeakReference(layout.map(l => l.findViewById(XResource.getId(l.getContext, "filechooser_delete")).asInstanceOf[Button]).getOrElse(null))
  private lazy val cancel = new WeakReference(layout.map(l => l.findViewById(XResource.getId(l.getContext, "filechooser_cancel")).asInstanceOf[Button]).getOrElse(null))
  private lazy val multiple = new WeakReference(layout.map(l => l.findViewById(XResource.getId(l.getContext, "filechooser_multiple")).asInstanceOf[Button]).getOrElse(null))
  private val copiedFiles = new HashSet[File]() with SynchronizedSet[File]
  private val cutFiles = new HashSet[File]() with SynchronizedSet[File]
  private val selectionFiles = new HashSet[File]() with SynchronizedSet[File]
  private val fileList = new ArrayList[File]()
  @volatile private var activeDirectory: File = null
  private lazy val df = new SimpleDateFormat("yyyy-MM-dd HH:mm")
  // active directory, selected files
  private val fileChooserResult = new SyncVar[(File, Seq[File])]()
  private val fileChooserStash = new AtomicReference[AnyRef](null)
  log.debug("alive")

  @Loggable
  def createDialog(activity: Activity,
    title: String,
    path: File,
    _onResult: (Context, File, Seq[File], AnyRef) => Any,
    _fileFilter: FileFilter = null,
    _onFileClickDismissCb: (Context, File) => Boolean = null,
    isTherePositiveButton: Boolean = true,
    stash: AnyRef = null)(implicit logger: RichLogger, dispatcher: Dispatcher): Dialog = {
    log.debug("create FileChooser dialog")
    fileChooserResult.unset()
    fileChooserStash.set(stash)
    onResult = _onResult
    if (_fileFilter != null)
      fileFilter = _fileFilter
    if (_onFileClickDismissCb != null)
      onFileClickDismissCb = _onFileClickDismissCb
    if (!dactivity.get.exists(_ == activity)) {
      layout.foreach(l => l.getParent.asInstanceOf[ViewGroup].removeView(l))
      dialog = None
      dactivity = new WeakReference(activity)
    }
    val result = dialog orElse (for {
      inflater <- inflater.orElse({ inflater = Some(activity.getLayoutInflater()); inflater })
      layout <- layout.orElse({ layout = Some(inflater.inflate(XResource.getId(activity, "dialog_filechooser", "layout"), null).asInstanceOf[LinearLayout]); layout })
    } yield {
      log.debug("initialize new FileChooser dialog")
      val builder = new AlertDialog.Builder(activity).
        setView(layout).
        setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
          @Loggable
          def onClick(dialog: DialogInterface, whichButton: Int) {
          }
        })
      if (isTherePositiveButton)
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          @Loggable
          def onClick(dialog: DialogInterface, whichButton: Int) {
            fileChooserResult.set((activeDirectory, Seq()))
            onResult(dialog.asInstanceOf[AlertDialog].getOwnerActivity, activeDirectory, Seq(), fileChooserStash.get)
          }
        })
      val result = builder.create()
      initialize(activity)
      dialog = Some(result)
      result
    })
    setup(title, path)
    result
  } getOrElse (null)
  @Loggable
  def getResult(timeout: Long) = fileChooserResult.get(timeout)
  @Loggable
  def initialize(activity: Activity): Unit = {
    for {
      lv <- lv.get
      home <- home.get
      up <- up.get
      filter <- filter.get
      order <- order.get
      paste <- paste.get
      clear <- clear.get
      copy <- copy.get
      cut <- cut.get
      delete <- delete.get
      cancel <- cancel.get
      multiple <- multiple.get
      layout <- layout
    } yield {
      home.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) {
          activeDirectory = new File("/")
          //          Toast.makeText(v.getContext, XResource.getString(v.getContext, "filechooser_change_directory_to").
          //            getOrElse("change directory to %s").format(activeDirectory), Toast.LENGTH_SHORT).show()
          showDirectory(activeDirectory)
        }
      })
      up.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) = {
          activeDirectory.getParentFile match {
            case parent: File =>
              //              Toast.makeText(v.getContext, XResource.getString(v.getContext, "filechooser_change_directory_to").
              //                getOrElse("change directory to %s").format(parent), Toast.LENGTH_SHORT).show()
              showDirectory(parent)
            case null =>
              Toast.makeText(v.getContext, XResource.getString(v.getContext, "filechooser_change_directory_to_failed").
                getOrElse("unable to change directory to %s").format("outer of " + activeDirectory), Toast.LENGTH_SHORT).show()
          }
        }
      })
      filter.setVisibility(View.GONE)
      order.setOnClickListener(new View.OnClickListener() { override def onClick(v: View) = {} /*showDialog(DIALOG_ORDER)*/ })
      order.setVisibility(View.GONE)
      paste.setVisibility(View.GONE)
      paste.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) {
          /*        for (val fileToMove <- cutFiles)
          FileSystemUtils.rename(mRoot, fileToMove)
        new CopyFilesTask(FileChooserActivity.this, copiedFiles, cutFiles, mRoot).execute()*/
        }
      })
      clear.setVisibility(View.GONE)
      clear.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) {
          /*        copiedFiles.clear()
        cutFiles.clear()
        paste.setVisibility(View.GONE)
        clear.setVisibility(View.GONE)
        ActionUtils.displayMessage(FileChooserActivity.this, R.string.action_clear_success)*/
        }
      })
      copy.setVisibility(View.GONE)
      copy.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) {
          /*        copy.setVisibility(View.GONE)
        cut.setVisibility(View.GONE)
        paste.setVisibility(View.GONE)
        clear.setVisibility(View.GONE)
        delete.setVisibility(View.GONE)
        multipleMode = false
        copiedFiles.addAll(selectionFiles)
        selectionFiles.clear()*/
        }
      })
      cut.setVisibility(View.GONE)
      cut.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) {
          /*        copy.setVisibility(View.GONE)
        cut.setVisibility(View.GONE)
        paste.setVisibility(View.GONE)
        clear.setVisibility(View.GONE)
        delete.setVisibility(View.GONE)
        multipleMode = false
        cutFiles.addAll(selectionFiles)
        selectionFiles.clear()*/
        }
      })
      delete.setVisibility(View.GONE)
      delete.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) {
          /*        copy.setVisibility(View.GONE)
        cut.setVisibility(View.GONE)
        paste.setVisibility(View.GONE)
        clear.setVisibility(View.GONE)
        delete.setVisibility(View.GONE)
        multipleMode = false
        for (file <- selectionFiles)
          FileSystemUtils.delete(file)*/
        }
      })
      cancel.setVisibility(View.GONE)
      multiple.setVisibility(View.GONE)
      multiple.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) {
          /*        if (multipleMode) {
          copy.setVisibility(View.GONE)
          cut.setVisibility(View.GONE)
          paste.setVisibility(View.GONE)
          delete.setVisibility(View.GONE)
          multipleMode = false
          initialize(mRoot.getName(), mRoot)
        } else {
          copy.setVisibility(View.VISIBLE)
          cut.setVisibility(View.VISIBLE)
          paste.setVisibility(View.VISIBLE)
          clear.setVisibility(View.GONE)
          delete.setVisibility(View.VISIBLE)
          multipleMode = true
        }*/
        }
      })
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
      lv.setAdapter(new ArrayAdapter[File](activity, android.R.layout.simple_list_item_2,
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
      lv.setOnItemClickListener(new OnItemClickListener {
        def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) =
          FileChooser.onItemClick(parent, view, position, id)
      })
    }
  } getOrElse { log.fatal("unable to initialize FileChooser dialog") }
  @Loggable
  def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) {
    val file = {
      val want = parent.getAdapter.asInstanceOf[ArrayAdapter[File]].getItem(position)
      if (want.getName == "..") want.getParentFile.getParentFile else want
    }
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
      if (onFileClickDismissCb(view.getContext, file)) {
        fileChooserResult.set((activeDirectory, Seq(file)))
        dialog.foreach(_.dismiss)
        onResult(view.getContext, activeDirectory, Seq(file), fileChooserStash.get)
      }
    }
  }
  @Loggable
  def setup(title: String, file: File): Unit = {
    for {
      dialog <- dialog
      layout <- layout
      path <- path.get
    } {
      dialog.setTitle(title)
      path.setText(file.getAbsolutePath())
      copiedFiles.clear
      cutFiles.clear
      selectionFiles.clear
      fileList.clear
      showDirectory(file)
    }
  }
  @Loggable
  private def showDirectory(file: File) = for {
    lv <- lv.get
    layout <- layout
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
