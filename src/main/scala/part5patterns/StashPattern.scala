package part5patterns

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import org.slf4j.Logger
import utils.ActorSystemEnhancement

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object StashPattern {

  // an actor with a locked access to a resource
  trait Command
  case object Open extends Command
  case object Close extends Command
  case object Read extends Command
  case class Write(data: String) extends Command

  // [Open] <- mailbox | [Read, Read] <- stash
  // A state machine!
  object ResourceActor {

    // the resource starts as closed with some initial data
    def apply(): Behavior[Command] = closed("42")

    def closed(data: String): Behavior[Command] = Behaviors.withStash(128) { buffer: StashBuffer[Command] =>
      Behaviors.receive { (context: ActorContext[Command], message: Command) =>
        val logger: Logger = context.log

        message match {
          case Open =>
            logger.info("Opening Resource")
            // open(data) is the next behavior, AFTER un-stashing
            buffer.unstashAll(open(data))
          case _ =>
            logger.info(s"Stashing $message because the resource is closed.")
            // buffer is MUTABLE, so keep in mind we can not send it elsewhere
            buffer.stash(message)
            Behaviors.same
        }
      }
    }

    def open(data: String): Behavior[Command] = Behaviors.receive { (context: ActorContext[Command], message: Command) =>
      val logger: Logger = context.log

      message match {
        case Read =>
          logger.info(s"I have read $data") // <- in real life you would fetch some actual data
          Behaviors.same
        case Write(newData) =>
          logger.info(s"I have written $newData")
          open(newData)
        case Close =>
          logger.info("Closing resource")
          closed(data)
        case _ =>
          logger.info("Message not supported while resource is open")
          Behaviors.same
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val resourceActor: ActorRef[Command] = context.spawn(ResourceActor(), "resourceActor")

      resourceActor ! Read // stashed
      resourceActor ! Open // un-stashed the Read message after opening
      resourceActor ! Open // unhandled
      resourceActor ! Write("I love stash") // overwrite
      resourceActor ! Write("This is pretty cool") // overwrite
      resourceActor ! Read
      resourceActor ! Read
      resourceActor ! Close
      resourceActor ! Read // stashed: resource is closed

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoPipePattern").withFiniteLifespan(2 seconds)
  }
}
