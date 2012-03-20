package org.digimead.digi.ctrl.lib.log

import android.content.Context

trait Logger {
  def init(context: Context) {}
  def apply(record: Logging.Record)
}
