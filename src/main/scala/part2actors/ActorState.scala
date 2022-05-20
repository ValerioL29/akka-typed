package part2actors

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

object ActorState {
  /**
   * Exercise: use the setup method to create a word counter which
   * - splits each message into words
   * - keeps track of the TOTAL number of words received so far
   * - log the current # of words + TOTAL # of words
   */
  object WordCounter{
    def apply(): Behavior[String] = Behaviors.setup { context: ActorContext[String] =>
      var total: Int = 0

      Behaviors.receiveMessage { message: String =>
        val newCount: Int = message.split(" ").length
        total += newCount
        context.log.info(s"Message word count: $newCount - total count: $total")
        Behaviors.same
      }
    }
  }

  trait SimpleThing
  case object EatChocolate extends SimpleThing
  case object CleanUpTheFloor extends SimpleThing
  case object LearnAkka extends SimpleThing

  /**
   * Message types must be IMMUTABLE and SERIALIZABLE
   * - use case classes / objects
   * - use a lat type hierarchy
   */

  object SimpleHuman {
    def apply(): Behavior[SimpleThing] = Behaviors.setup { context: ActorContext[SimpleThing] =>
      var happiness = 0

      Behaviors.receiveMessage {
        case EatChocolate =>
          context.log.info(s"[Happiness $happiness] Eating chocolate!")
          happiness += 1
          Behaviors.same
        case CleanUpTheFloor =>
          context.log.info(s"[Happiness $happiness] Wiping the floor, ugh...")
          happiness -= 2
          Behaviors.same
        case LearnAkka =>
          context.log.info(s"[Happiness $happiness] Learning Akka, YAY!")
          happiness += 99
          Behaviors.same
      }
    }
  }

  object SimpleHuman_V2 {
    def apply(): Behavior[SimpleThing] = statelessHuman(0)

    def statelessHuman(happiness: Int): Behavior[SimpleThing] = Behaviors.receive{ (context: ActorContext[SimpleThing], message: SimpleThing) =>
      message match {
        case EatChocolate =>
          context.log.info(s"[Happiness $happiness] Eating chocolate!")
          statelessHuman(happiness + 1)
        case CleanUpTheFloor =>
          context.log.info(s"[Happiness $happiness] Wiping the floor, ugh...")
          statelessHuman(happiness - 2)
        case LearnAkka =>
          context.log.info(s"[Happiness $happiness] Learning Akka, YAY!")
          statelessHuman(happiness + 99)
      }
    }
  }

  /**
   * Tips:
   * - 1. Each var/mutable field becomes an immutable METHOD ARGUMENT
   * - 2. Each state change = new behavior obtained by calling the method with a different argument
   */

  /**
   * Exercise: refactor the "stateful" word counter into a "stateless" version.
   */
  object WordCounter_V2{
    def apply(): Behavior[String] = active(0)

    def active(total: Int): Behavior[String] = Behaviors.receive {
      (context: ActorContext[String], message: String) =>
        val newCount: Int = message.split(" ").length
        context.log.info(s"Message word count: $newCount - total count: ${total + newCount}")
        active(total + newCount)
    }
  }

  def demoSimpleHuman(): Unit = {
    val human: ActorSystem[SimpleThing] = ActorSystem(SimpleHuman_V2(), "DemoSimpleHuman")

    human ! LearnAkka
    human ! EatChocolate
    (1 to 30).foreach((_: Int) => human ! CleanUpTheFloor)

    Thread.sleep(1000)
    human.terminate()
  }

  def demoWordCounter(): Unit = {
    val counter: ActorSystem[String] = ActorSystem(WordCounter_V2(), "DemoWordCounter")

    counter ! "I am learning Akka"
    counter ! "I hope you will be stateless one day"
    counter ! "Lets' see the next one"

    Thread.sleep(1000)
    counter.terminate()
  }

  def main(args: Array[String]): Unit = {
    demoWordCounter()
  }

}
