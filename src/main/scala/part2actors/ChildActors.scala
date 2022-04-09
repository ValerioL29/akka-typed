package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChildActors extends App {

  // Actors can create other actors
  object Parent {
    case class CreateChild(name: String)
    case class TellChild(message: String)
  }
  class Parent extends Actor {
    import Parent._

    override def receive: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} creating child with name: $name")
        // create a new actor right HERE
        val childRef = context.actorOf(Props[Child], name)
        context.become(withChild(childRef))
    }

    def withChild(ref: ActorRef): Receive = {
      case TellChild(message) =>
        if (ref != null) ref forward message
    }
  }

  class Child extends Actor {
    override def receive: Receive = {
      case message => println(s"${self.path} I got: $message")
    }
  }

  import Parent._

  val system = ActorSystem("ParentChildDemo")
  val parent = system.actorOf(Props[Parent], "parent")

  parent ! CreateChild("ken")
  parent ! TellChild("It's lunch time!")

  /**
   * actor hierarchies
   * parent -> child -> grandChild
   *        -> child2 ->
   *
   * Guardian actors (top-level)
   * - /system = system guardian
   * - /user = user-level guardian
   * - / = the root guardian
   */

  /**
   * Actor selection
   * - By path
   * - By String
   */
  val childSelection = system.actorSelection("/user/parent/child")
  childSelection ! "I found you!"
  //  childSelection = system.actorSelection("/user/parent/child2") -> Dead letter as child2 doesn't exist
  //  childSelection ! "I found you!"

  /**
   * Danger!
   *
   * NEVER PASS MUTABLE ACTOR STATE, OR THE 'THIS' REFERENCE, TO CHILD ACTORS
   *
   * NEVER IN YOUR LIFE!
   */
  object NaiveBankAccount {
    case class Deposit(amount: Int)
    case class WithDraw(amount: Int)
    case object InitializeAccount
  }
  class NaiveBankAccount extends Actor {
    import NaiveBankAccount._
    import CreditCard._

    var amount = 0

    override def receive: Receive = {
      case InitializeAccount =>
        val creditCardRef = context.actorOf(Props[CreditCard], "card")
        creditCardRef ! AttachToAccount(this) // !!
      case Deposit(funds) => deposit(funds)
      case WithDraw(funds) => withDraw(funds)
    }

    def deposit(funds: Int): Unit = {
      println(s"${self.path} depositing $funds on top of $amount")
      amount += funds
    }
    def withDraw(funds: Int): Unit = {
      println(s"${self.path} withdrawing $funds from $amount")
      amount -= funds
    }
  }
  object CreditCard {
    case class AttachToAccount(bankAccount: NaiveBankAccount) // !!
    // This is called a 'closing over' problem which breaks all the Actor model encapsulation
    case object CheckStatus
  }
  class CreditCard extends Actor {
    import CreditCard._

    override def receive: Receive = {
      case AttachToAccount(account) => context.become(attachTo(account))
    }

    def attachTo(account: NaiveBankAccount): Receive = {
      case CheckStatus =>
        println(s"${self.path} Your message has been processed.")
        // benign
        account.withDraw(1) // because I can
        // WRONG! This is calling a method directly from an actor
        // which will put us in the concurrency issues
    }
  }

  import NaiveBankAccount._
  import CreditCard._

  val bankAccountRef = system.actorOf(Props[NaiveBankAccount],"account")
  bankAccountRef ! InitializeAccount
  bankAccountRef ! Deposit(100)

  Thread.sleep(500)
  val creditCardSelection = system.actorSelection("/user/account/card")
  creditCardSelection ! CheckStatus
}
