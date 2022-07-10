package part5patterns

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout
import org.slf4j.Logger
import utils._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

object AskPattern {

  trait WorkProtocol
  case class ComputationalTask(payload: String, replyTo: ActorRef[WorkProtocol]) extends WorkProtocol
  case class ComputationalResult(result: Int) extends WorkProtocol
  // define extra messages that I should handle as results of ask
  case class ExtendedComputationalResult(count: Int, description: String) extends WorkProtocol

  object Worker {
    def apply(): Behavior[WorkProtocol] = Behaviors.receive { (context: ActorContext[WorkProtocol], message: WorkProtocol) =>
      val logger: Logger = context.log

      message match {
        case ComputationalTask(text, destination) =>
          logger.info(s"[worker] Crunching data for: $text")
          destination ! ComputationalResult(text.split(" ").length)
          Behaviors.same
        case _ => Behaviors.same
      }
    }
  }

  // easy-demo
  def askSimple(): Unit = {
    // 1 - import the right package
    // that is, the AskPattern

    // 2 - set up some implicits
    val system: ActorSystem[WorkProtocol] = ActorSystem(Worker(), "DemoAskSimple").withFiniteLifespan(5 seconds)
    implicit val timeout: Timeout = Timeout(3 seconds)
    implicit val scheduler: Scheduler = system.scheduler

    // 3 - call the ask method
    val reply: Future[WorkProtocol] =
      system.ask((ref: ActorRef[WorkProtocol]) => ComputationalTask("Trying the ask pattern, seems convoluted", ref))
    // It first creates a TEMPORARY actor and messages will be sent to the worker == the user guardian

    // 4 - process the Future
    implicit val ec: ExecutionContext = system.executionContext
    reply.foreach(println)
  }

  // a more complex one
  def askFromWithinAnotherActor(): Unit = {
    val userGuardian: Behavior[WorkProtocol] = Behaviors.setup[WorkProtocol] { context: ActorContext[WorkProtocol] =>
      val worker: ActorRef[WorkProtocol] = context.spawn(Worker(), "worker")

      // 1 - set up implicits
      implicit val timeout: Timeout = Timeout(3 seconds)

      // 2 - ask
      context.ask(worker, (ref: ActorRef[WorkProtocol]) => ComputationalTask(
        "This ask pattern seems quite complicated", ref
      )) {
            // Try[WorkProtocol] => WorkProtocol message that will be sent to ME LATER
            case Success(ComputationalResult(count)) =>
              ExtendedComputationalResult(count, "This is pretty damn hard")
            case Failure(ex) =>
              ExtendedComputationalResult(-1, s"Computation failed: ${ex.getMessage}")
          }

      // 3 - handle the results (messages) from the ask pattern
      Behaviors.receiveMessage[WorkProtocol] {
        case ExtendedComputationalResult(count, description) =>
          context.log.info(s"Ask and you shall received: $description - $count")
          Behaviors.same
        case _ => Behaviors.same
      }
    }

    ActorSystem(userGuardian, "DemoAskConvoluted").withFiniteLifespan(5 seconds)
  }

  def main(args: Array[String]): Unit = {
    askFromWithinAnotherActor()
  }
}
