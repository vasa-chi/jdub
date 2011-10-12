package com.codahale.jdub

import java.sql.{Connection, PreparedStatement}

/**
 * Created by IntelliJ IDEA.
 * User: vasa
 * Date: 12.10.11
 * Time: 0:07
 */

trait PreparedStatementMaker[T] {
  def toPreparedStatement(statement: T, conn: Connection): PreparedStatement
}