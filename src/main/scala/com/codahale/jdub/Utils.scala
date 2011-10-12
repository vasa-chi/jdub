package com.codahale.jdub

import scala.annotation.tailrec
import java.sql.{Types, PreparedStatement}

object Utils {
  private[jdub] def prependComment(obj: Object, sql: String) =
    "/* %s */ %s".format(obj.getClass.getSimpleName.replace("$", ""), sql)

  @tailrec
  private[jdub] def prepare(stmt: PreparedStatement, values: Seq[Any], index: Int = 1) {
    if (!values.isEmpty) {
      val v = values.head
      if (v == null) {
        stmt.setNull(index, Types.NULL)
      } else {
        stmt.setObject(index, v.asInstanceOf[AnyRef])
      }
      prepare(stmt, values.tail, index + 1)
    }
  }

  @tailrec
  private[jdub] def prepareBatch(stmt: PreparedStatement, values: Seq[Seq[Any]]) {
    if (!values.isEmpty) {
      prepare(stmt, values.head)
      stmt.addBatch()
      prepareBatch(stmt, values.tail)
    }
  }
}
