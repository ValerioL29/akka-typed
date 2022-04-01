package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorCapabilities extends App {

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case "Hi" => context.sender() ! "Hello, there!" // replaying to a message
      case message: String => println(s"[$self] I have received $message")
      case number: Int => println(s"[simple actor] I have received a NUMBER: $number")
      case SpecialMessage(contents) => println(s"[simple actor] I have receive something SPECIAL: $contents")
      case SendMessageToYourself(content) => self ! content
      case SayHiTo(ref) => ref ! "Hi" // this is equal to (ref ! "Hi")(self)
      case WirelessPhoneMessage(content, ref) => ref forward (content + "S") // I keep the original sender of the WPM
    }
  }

  val system = ActorSystem("actorCapabilitiesDemo")
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  simpleActor ! "hello, actor"

  // 1 - messages can be of any type
  /**
   * a) messages must be IMMUTABLE
   * b) messages must be SERIALIZABLE
   * c) in practice use CASE CLASSES and CASE OBJECTS
   *
   */
  simpleActor ! 42 // who is the sender

  case class SpecialMessage(contents: String)
  simpleActor ! SpecialMessage("some special content")

  // 2 - actors have information about their context and about themselves
  // context.self === 'this' in OOP

  case class SendMessageToYourself(content: String)
  simpleActor ! SendMessageToYourself("I am a actor and I am proud of it") // Self-talking!

  // 3 - actors can REPLY to messages
  val alice = system.actorOf(Props[SimpleActor], "alice")
  val bob = system.actorOf(Props[SimpleActor], "bob")

  case class SayHiTo(ref: ActorRef)
  alice ! SayHiTo(bob)

  // 4 - dead letters
  // alice ! "Hi" // replay to "me" but "me" is null
  // Output:
  // [INFO] [akkaDeadLetter][03/31/2022 22:50:54.291] [actorCapabilitiesDemo-akka.actor.default-dispatcher-6]
  // [akka://actorCapabilitiesDemo/deadLetters] Message [java.lang.String]
  // from Actor[akka://actorCapabilitiesDemo/user/alice#92677525]
  // to Actor[akka://actorCapabilitiesDemo/deadLetters]
  // was not delivered. [1] dead letters encountered.
  // If this is not an expected behavior then Actor[akka://actorCapabilitiesDemo/deadLetters] may have terminated unexpectedly.
  // This logging can be turned off or adjusted with configuration settings:
  // 'akka.log-dead-letters' and 'akka.log-dead-letters-during-shutdown'.

  // 5 - forwarding messages
  // K -> A -> B
  // forwarding = sending a message with the ORIGINAL sender

  case class WirelessPhoneMessage(content: String, rec: ActorRef)
  alice ! WirelessPhoneMessage("Hi", bob) // no sender

  /**
   * Exercises
   *
   * 1. a Counter actor
   * - Increment
   * - Decrement
   * - Print internal count
   *
   * 2. a Bank account as an actor
   * receives
   * - Deposit an amount
   * - Withdraw an amount
   * - Statement
   * replies
   * - Success
   * - Failure
   * take cares of edge cases
   *
   * interact with some other kind of actor
   */

  // DOMAIN of the counter, in practice
  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }
  class Counter extends Actor {
    import Counter._

    var count: Int = 0

    override def receive: Receive = {
      case Increment => count += 1
      case Decrement => count -= 1
      case Print => println(s"[counter actor] My current count is $count")
      case _ =>
    }
  }

  import Counter._
  val counter = system.actorOf(Props[Counter], "myCounter")
  (1 to 5).foreach(_ => counter ! Increment)
  (1 to 3).foreach(_ => counter ! Decrement)
  counter ! Print

  // DOMAIN of a bank account
  object BankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object Statement

    case class TransactionSuccess(message: String)
    case class TransactionFailure(reason: String)
  }
  class BankAccount extends Actor {
    import BankAccount._

    var funds = 0

    override def receive: Receive = {
      case Deposit(amount) =>
        if (amount < 0) sender() ! TransactionFailure("[bank account] Invalid deposit amount!")
        else {
          funds += amount
          sender() ! TransactionSuccess(s"[bank account] Successfully deposited $amount")
        }
      case Withdraw(amount) =>
        if (amount < 0) sender() ! TransactionFailure("[bank account] Invalid deposit amount!")
        else if (amount > funds) sender() ! TransactionFailure("[bank account] Insufficient funds!")
        else {
          funds -= amount
          sender() ! TransactionSuccess(s"[bank account] Successfully withdrew $amount")
        }
      case Statement => sender() ! s"Your balance is $funds"
    }
  }

  object Person {
    case class LiveTheLife(account: ActorRef)
  }
  class Person extends Actor {
    import Person._
    import BankAccount._

    override def receive: Receive = {
      case LiveTheLife(account) =>
        account ! Deposit(10000)
        account ! Withdraw(90000)
        account ! Withdraw(500)
        account ! Statement
      case TransactionSuccess(message) => println(s"$message")
      case TransactionFailure(reason) => println(s"$reason")
    }
  }

  import Person._

  val account = system.actorOf(Props[BankAccount], "myAccount")
  val person = system.actorOf(Props[Person], "billionaire")

  person ! LiveTheLife(account)

  /**
   * Valid Questions!
   * 1. Can we assume any ordering of messages?
   * 2. Aren't we causing race conditions?
   *
   * To be figured out later!
   * But now, let's go to sleep.
   * - on April 2nd, 2022
   * zzzzzz~
   */
}
