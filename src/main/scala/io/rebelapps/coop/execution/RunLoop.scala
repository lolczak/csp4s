package io.rebelapps.coop.execution

import cats.Monad
import cats.data.State
import io.rebelapps.coop.data._
import io.rebelapps.coop.execution.stack._
import cats.implicits._

object RunLoop {

  private val M = Monad[State[CallStack, ?]]
  import M._

  def createCallStack(coroutine: Coroutine[Any]): CallStack = {
    //todo tail rec version
    def loop(coroutine: Coroutine[Any]): State[CallStack, Unit]  =
      coroutine match {
        case Map(fa: Coroutine[Any], f: (Any => Any)) =>
          push(Continuation(f andThen Pure.apply)) >> loop(fa)

        case FlatMap(fa: Coroutine[Any], f: (Any => Coroutine[Any])) =>
          push(Continuation(f)) >> loop(fa)

        case Pure(value) =>
          push(Val(value))

        case Eval(thunk) =>
          push(Evaluation(thunk))

        case _ => State.set(emptyStack) //todo async, raise error
      }

    loop(coroutine)
      .run(emptyStack)
      .value
      ._1
  }

  def step(): State[CallStack, Either[Alive.type, Result]] =
    for {
      frame  <- pop()
      result <- frame match {
        case Val(value) =>
          def cont(): State[CallStack, Either[Alive.type, Result]] = {
            for {
              next            <- pop()
              Continuation(f)  = next
              _               <- pushStack(createCallStack(f(value)))
            } yield Alive.asLeft
          }
          ifM(isEmpty())(State.pure((Return(value): Result).asRight[Alive.type]), cont())

        case Evaluation(thunk) =>
          val value = thunk()
          ifM(isEmpty())(State.pure((Return(value): Result).asRight[Alive.type]), push(Val(value)) >> State.pure(Alive.asLeft[Result]))

        case Continuation(f) =>
          throw new RuntimeException("Impossible")

      }
    } yield result

  def run[A](coroutine: Coroutine[A]): A = {
    val initialStack = createCallStack(coroutine)

    val op = M.tailRecM(Alive)(_ => step())

    val Return(result) =
      op
        .run(initialStack)
        .value
        ._2

    result.asInstanceOf[A]
  }


}