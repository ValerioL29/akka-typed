package part1recap

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MultithreadingRecap extends App {

  // Creating threads on the JVM
  val aThread: Thread = new Thread(() => {
    println("I'm running in parallel")
  })
  aThread.start()
  aThread.join()

  // Thread is unpredictable :(
  val threadHello = new Thread(() => (1 to 1000).foreach((_: Int) => println("hello")))
  val threadGoodbye = new Thread(() => (1 to 1000).foreach((_: Int) => println("goodbye")))

  threadHello.start()
  threadGoodbye.start()
  // Different runs produce different results!
  // @volatile is used for locking, but it only works for primitive type
  // It actually prevents execution rearrangements, that is, queuing again in another order
  class BankAccount(@volatile private var amount: Int) {
    override def toString: String = "" + amount

    def withDraw(money: Int): Unit = this.amount -= money

    /*
      BA(10000)

      T1 -> withdraw 1000
      T2 -> withdraw 2000

      T1 -> this.amount = this.amount - ... // Preempted by the OS
      T2 -> this.amount = this.amount - 2000 = 8000
      T1 -> -1000 = 9000

      => 9000

      this.amount = this.amount - 1000 is NOT ATOMIC
    */
    def safeWithDraw(money: Int): Unit = this.synchronized{ // synchronized means parallel computing
      this.amount -= money
    }
  }

  // Inter-thread communication on the JVM
  // Wait - notify mechanism

  // Scala Futures
  import scala.concurrent.ExecutionContext.Implicits.global
  val future: Future[Int] = Future {
    // long computation - on a different thread
    42
  }

  // Callbacks
  future.onComplete {
    case Success(42) =>
      println("I found the meaning of life")
    case Success(otherResult) =>
      println(s"Unexpected results: $otherResult")
    case Failure(exception) =>
      println(s"something happened with the meaning of life with reason: ${exception.getMessage}!")
  }

  val aProcessedFuture = future.map((_: Int) + 1) // Future with 43
  val aFlatFuture = future.flatMap{ value: Int =>
    Future(value + 1)
  } // Future with 44

  val filteredFuture = future.filter((_: Int) % 2 == 0) // NoSuchElementException

  // For comprehensions
  val aNonsenseFuture = for {
    meaningOfLife <- future
    filteredMeaning <- filteredFuture
  } yield meaningOfLife + filteredMeaning

  // andThen, recover / recoverWith

  // Promises

}
