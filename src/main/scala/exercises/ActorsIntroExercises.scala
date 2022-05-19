package exercises

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import javax.xml.ws.handler.MessageContext

object ActorsIntroExercises {
  /**
   * Exercise Introduction
   * 1. Define two "person" actor behaviors, which receive Strings:
   * - "happy", which logs your message, e.g. "I've received ____. That's great!"
   * - "sad", ... "That sucks."
   * Test both
   *
   * 2. Change the actor behavior:
   * - the happy behavior will turn to sad() if it receives "Akka is bad."
   * - the sad behavior will turn to happy if it receives "Akka is awesome!"
   */
  object Person{
    def apply(): Behavior[String] = happy()

    def happy(): Behavior[String] = Behaviors.receive { (context: ActorContext[String], message: String) =>
      message match {
          case "Akka is bad." =>
            context.log.info(s"Don't you say anything bad about Akka!!!")
            sad()
          case _ =>
            context.log.info(s"I've received $message - That's great!")
            Behaviors.same
        }
    }

    def sad(): Behavior[String] = Behaviors.receive { (context: ActorContext[String], message: String) =>
      message match {
        case "Akka is awesome!" =>
          context.log.info(s"Happy now!")
          happy()
        case _ =>
          context.log.info(s"I've received $message - That sucks!")
          Behaviors.same
      }
    }
  }

  def personActorSystem(): Unit = {
    val actorSystem: ActorSystem[String] = ActorSystem(Person(), "PersonActorSystem")

    actorSystem ! "Akka is awesome!"
    actorSystem ! "Akka is bad."
    actorSystem ! "Akka is bad."
    actorSystem ! "Akka is awesome!"
    actorSystem ! "Akka is awesome!"

    Thread.sleep(1000)
    actorSystem.terminate()
  }

  object WeirdActor {
    // wants to receive messages of type Int AND String
    // Any type will make us lose the TYPE SAFETY which we are looking for in the Akka Typed APIs
    def apply(): Behavior[Any] = Behaviors.receive { (context, message) =>
      message match {
        case number: Int =>
          context.log.info(s"I've received an int: $number")
          Behaviors.same
        case string: String =>
          context.log.info(s"I've received a string: $string")
          Behaviors.same
      }
    }
  }

  // solution: add Wrapper types & Type hierarchy (case classes / objects)
  object BetterActor {
    trait Message
    case class IntMessage(number: Int) extends Message
    case class StringMessage(string: String) extends Message

    def apply(): Behavior[Message] = Behaviors.receive { (context, message) =>
      message match {
        case IntMessage(number: Int) =>
          context.log.info(s"I've received an int: $number")
          Behaviors.same
        case StringMessage(string: String) =>
          context.log.info(s"I've received a string: $string")
          Behaviors.same
      }
    }
  }

  def weirdActorSystem(): Unit = {
    import BetterActor._

    val actorSystem: ActorSystem[Message] = ActorSystem(BetterActor(), "BetterWeirdActorSystem")

    actorSystem ! StringMessage("Akka")
    actorSystem ! IntMessage(43)

    Thread.sleep(1000)
    actorSystem.terminate()
  }

  def main(args: Array[String]): Unit = {
    weirdActorSystem()
  }
}
