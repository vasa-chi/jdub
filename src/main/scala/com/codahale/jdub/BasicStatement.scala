package com.codahale.jdub

import com.yammer.metrics.Instrumented

trait BasicStatement extends SqlBase with Instrumented {
  private[jdub] val timer = metrics.timer("execute")

  def sql: String

  def values: Seq[Any]
}
