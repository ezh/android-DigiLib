package org.digimead.digi.ctrl.lib.log

object ConsoleLogger extends Logger {
  val LINE_SEPARATOR = System.getProperty("line.separator")
  def apply(record: Logging.Record) {
    val buf = new StringBuffer()
    buf.append(record.date)
    buf.append(" P")
    buf.append(record.pid)
    buf.append(" T")
    buf.append(record.tid)
    buf.append(" ")
    buf.append(record.level)
    buf.append(" ")
    buf.append(record.tag)
    buf.append(" - ")
    buf.append(record.message)
    buf.append(LINE_SEPARATOR)
    System.err.print(buf.toString())
    record.throwable.foreach(_.printStackTrace(System.err))
    System.err.flush()
  }
}
