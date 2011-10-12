package com.codahale.jdub

import com.yammer.metrics.Instrumented

/**
 * Created by IntelliJ IDEA.
 * User: vasa
 * Date: 01.10.11
 * Time: 20:59
 */

trait BatchStatement extends BasicStatement {

  def values: Seq[Seq[Any]]

}