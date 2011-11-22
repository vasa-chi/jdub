package com.codahale.jdub

import javax.sql.DataSource
import com.yammer.metrics.Instrumented
import org.apache.tomcat.dbcp.pool.impl.GenericObjectPool
import org.apache.tomcat.dbcp.dbcp.{PoolingDataSource, PoolableConnectionFactory, DriverManagerConnectionFactory}
import com.codahale.logula.Logging
import java.util.Properties

object Database {
  /**
   * Create a pool of connections to the given database.
   *
   * @param url the JDBC url
   * @param username the database user
   * @param password the database password
   */
  def connect(url: String,
              username: String,
              password: String,
              name: String = null,
              maxWaitForConnectionInMS: Long = 8,
              maxSize: Int = 8,
              minSize: Int = 0,
              checkConnectionWhileIdle: Boolean = true,
              checkConnectionHealthWhenIdleForMS: Long = 10000,
              closeConnectionIfIdleForMS: Long = 1000L * 60L * 30L,
              healthCheckQuery: String = Utils.prependComment(PingQuery, PingQuery.sql),
              jdbcProperties: Map[String, String] = Map.empty) = {
    val properties = new Properties
    for ((k, v) <- jdbcProperties) {
      properties.setProperty(k, v)
    }
    properties.setProperty("user", username)
    properties.setProperty("password", password)

    val factory = new DriverManagerConnectionFactory(url, properties)
    val pool = new GenericObjectPool(null)
    pool.setMaxWait(maxWaitForConnectionInMS)
    pool.setMaxIdle(maxSize)
    pool.setMaxActive(maxSize)
    pool.setMinIdle(minSize)
    pool.setTestWhileIdle(checkConnectionWhileIdle)
    pool.setTimeBetweenEvictionRunsMillis(checkConnectionHealthWhenIdleForMS)
    pool.setMinEvictableIdleTimeMillis(closeConnectionIfIdleForMS)

    // this constructor sets itself as the factory of the pool
    new PoolableConnectionFactory(
      factory, pool, null, healthCheckQuery, false, true
    )
    new Database(new PoolingDataSource(pool), pool, name)
  }

}

/**
 * A set of pooled connections to a database.
 */
class Database protected(source: DataSource, pool: GenericObjectPool, name: String)
    extends Logging with Instrumented {

  metrics.gauge("active-connections", name) {
    pool.getNumActive
  }
  metrics.gauge("idle-connections", name) {
    pool.getNumIdle
  }
  metrics.gauge("total-connections", name) {
    pool.getNumIdle + pool.getNumActive
  }
  private val poolWait = metrics.timer("pool-wait")

  import Utils._

  /**
   * Opens a transaction which is committed after `f` is called. If `f` throws
   * an exception, the transaction is rolled back.
   */
  def transaction[A](f: Transaction => A): A = {
    val connection = poolWait.time {
      source.getConnection
    }
    val txn = new Transaction(connection)
    try {
      log.debug("Starting transaction")
      val result = f(txn)
      log.debug("Committing transaction")
      result
    } catch {
      case e =>
        log.error(e, "Exception thrown in transaction scope; aborting transaction")
        throw e
    } finally {
      txn.close()
    }
  }

  /**
   * Returns {@code true} if we can talk to the database.
   */
  def ping() = query(PingQuery)

  /**
   * Performs a query and returns the results.
   */
  @deprecated(message = "Use query instead", since = "12 October 2011")
  def apply[A](query: RawQuery[A]): A = {
    val connection = poolWait.time {
      source.getConnection
    }
    query.timer.time {
      try {
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
      } finally {
        connection.close()
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
    val connection = poolWait.time {
      source.getConnection
    }
    statement.timer.time {
      try {
        if (log.isDebugEnabled) {
          log.debug("%s with %s", statement.sql, statement.values.mkString("(", ", ", ")"))
        }
        val stmt = connection.prepareStatement(prependComment(statement, statement.sql))
        try {
          if (statement.isInstanceOf[BatchStatement]) {
            prepareBatch(stmt, statement.asInstanceOf[BatchStatement].values)
            try {
              connection.setAutoCommit(false)
              stmt.executeBatch()
              connection.commit()
            } catch {
              case e =>
                connection.rollback()
                throw e
            } finally {
              connection.setAutoCommit(true)
            }
          } else {
            prepare(stmt, statement.values)
            stmt.executeUpdate()
          }
        } finally {
          stmt.close()
        }
      } finally {
        connection.close()
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
   * Closes all connections to the database.
   */
  def close() {
    pool.close()
  }
}
