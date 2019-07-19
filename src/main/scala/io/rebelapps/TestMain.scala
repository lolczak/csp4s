package io.rebelapps

import cats.implicits._
import io.rebelapps.coop.data.Coroutine.{eval, pure}
import io.rebelapps.coop.execution.RunLoop

object TestMain extends App {

  val fiber =
    for {
      value  <- pure { 123 }
      next = value + 1
      result <- eval { next + 3 }
    } yield result

  println(fiber map(_ - 5))

  val callStack = RunLoop.createCallStack(fiber)

  callStack foreach println

  val result = RunLoop.run(fiber map(_ - 5))

  println(result)

  //backlog
  //todo 1)async handling
  //todo 2)channels
  //todo 3)bifunctor
  //todo 4)thread pool executor

}