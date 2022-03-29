package part1recap

import com.sun.org.apache.xpath.internal.functions.FuncTrue

import scala.concurrent.Future

/**
 * Ken's Rants
 *
 * @author Ken
 * @version 1.0
 */
object ThreadModelLimitations extends App {
  /**
   * KR # 1: OOP encapsulation is only valid in the SINGLE THREADED MODEL.
   */
  class BankAccount(@volatile private var amount: Int) {
    override def toString: String = "" + amount

    def withdraw(money: Int): Unit = this.synchronized{
      this.amount -= money
    }
    def deposit(money: Int): Unit = this.synchronized{
      this.amount += money
    }

    def getAmount: Int = amount
  }

  //  val account = new BankAccount(2000)
  //  for(_ <- 1 to 1000) {
  //    new Thread(() => account.withdraw(1)).start()
  //  }
  //  for(_ <- 1 to 1000) {
  //    new Thread(() => account.deposit(1)).start()
  //  }
  //
  //  println(account.getAmount) // not-sync: 1634 / sync: 1998
  // OOP encapsulation is broken in a multi-threaded env
  // synchronizations ! Locks to the rescue
  // deadlocks, live locks can occur

  /*
    We need a data structure
    - fully encapsulated
    - with no locks
   */

  /**
   * KR #2: delegating something to a thread is a PAIN.
   */

  // you have a running thread and you want to pass a runnable to that thread.
  var task: Runnable = null
  val runningThread: Thread = new Thread(() => {
    while (true){
      while (task == null){
        runningThread.synchronized{
          println("[background] waiting for a task...")
          runningThread.wait()
        }
      }

      task.synchronized{
        println("[background] I have a task!")
        task.run()
        task = null
      }
    }
  })

  def delegateToBackgroundThread(r: Runnable): Unit = {
    if (task == null) task = r

    runningThread.synchronized{
      runningThread.notify()
    }
  }

  runningThread.start()
  Thread.sleep(500)
  delegateToBackgroundThread(() => println(42))
  Thread.sleep(1000)
  delegateToBackgroundThread(() => println("This should run in the background"))

  // background threads will become a BIG PAIN in complex scenario
  // What if -
  // Other signals ?
  // Multiple background tasks and threads ?
  // Who gave the signal ?
  // What if I crash ?

  /**
   * Thus we need a data structure which
   * - can safely receive messages
   * - can identify the sender
   * - is easily identifiable
   * - can guard against failure
   */

  /**
   * KR #3: tracing and dealing with errors in a multithreaded env is a PITN.
   */
  // 1M numbers in between 10 threads
  // Assume that we are smart enough to use Future
  import scala.concurrent.ExecutionContext.Implicits.global
  val futures = (1 to 10)
    .map(i => 100000 * i until 100000 * (i + 1))
    .map(range => Future {
      if(range.contains(546735)) throw new RuntimeException("invalid number")
      range.sum
    })

  val sumFuture = Future.reduceLeft(futures)(_ + _)
  sumFuture.onComplete(println)
}
