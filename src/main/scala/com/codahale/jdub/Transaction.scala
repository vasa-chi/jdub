package com.codahale.jdub

import java.sql.Connection
import com.codahale.logula.Logging


class Transaction(connection: Connection) extends Logging {
  import Utils._

  /**
   * Performs a query and returns the results.
   */
  def apply[A](query: RawQuery[A]): A = {
    query.timer.time {
      if (log.isDebugEnabled) {
        log.debug("%s with %s", query.sql, query.values.mkString("(", ", ", ")"))
      }
      val stmt = connection.prepareStatement(prependComment(query, query.sql))
      try {
        prepare(stmt, query.values)
        val results = stmt.executeQuery()
        try {
          query.handle(results)
        } finally {
          results.close()
        }
      } finally {
        stmt.close()
      }
    }
  }

  /**
   * Performs a query and returns the results.
   */
  def query[A](query: RawQuery[A]): A = apply(query)

  /**
   * Executes an update, insert, delete, or DDL statement.
   */
  def execute(statement: BasicStatement) = {
    statement.timer.time {
      if (log.isDebugEnabled) {
        log.debug("%s with %s", statement.sql, statement.values.mkString("(", ", ", ")"))
      }
      val stmt = connection.prepareStatement(prependComment(statement, statement.sql))
      try {
        prepare(stmt, statement.values)
        stmt.executeUpdate()
      } finally {
        stmt.close()
      }
    }
  }

  /**
   * Executes an update statement.
   */
  def update(statement: BasicStatement) = execute(statement)

  /**
   * Executes an insert statement.
   */
  def insert(statement: BasicStatement) = execute(statement)

  /**
   * Executes a delete statement.
   */
  def delete(statement: BasicStatement) = execute(statement)

  /**
   * Roll back the transaction.
   */
  def rollback() {
    connection.rollback()
  }
}
