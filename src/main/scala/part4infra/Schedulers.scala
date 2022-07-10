package part4infra

import akka.NotUsed
import akka.actor.Cancellable
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.slf4j.Logger

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object Schedulers {

  object LoggerActor {
    def apply(): Behavior[String] = Behaviors.receive { (context: ActorContext[String], message: String) =>
      val logger: Logger = context.log

      logger.info(s"[${context.self.path.name}] Received: $message")
      Behaviors.same
    }
  }

  def demoScheduler(): Unit = {
    val userGuardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val loggerActor: ActorRef[String] = context.spawn(LoggerActor(), "loggerActor")

      context.log.info(s"[system] System starting.")
      // Schedule a message ONCE after a certain delay
      context.scheduleOnce(1 seconds, loggerActor, "reminder")

      Behaviors.empty
    }

    val system: ActorSystem[NotUsed] = ActorSystem(userGuardian, "demoScheduler")

    import system.executionContext
    system.scheduler.scheduleOnce(2 seconds, () => system.terminate())
  }

  // timeout pattern
  def demoActorWithTimeout(): Unit = {
    val timeoutActor: Behavior[String] = Behaviors.receive[String] { (context: ActorContext[String], message: String) =>
      val schedule: Cancellable = context.scheduleOnce(1 second, context.self, "timeout")

      message match {
        case "timeout" =>
          context.log.info("Stopping")
          Behaviors.stopped
        case _ =>
          context.log.info(s"Received: $message")
          Behaviors.same
      }
    }

    val system: ActorSystem[String] = ActorSystem(timeoutActor, "timeoutDemo")

    system ! "trigger"
    Thread.sleep(2000)
    system ! "are you there" // system has been shutdown
  }

  /**
   * Exercise: enhance the timeoutActor to reset its timer with every new message (except the "timeout" message)
   */
  object ResettingTimeoutActor {
    def apply(): Behavior[String] = Behaviors.receive { (context: ActorContext[String], message: String) =>
      context.log.info(s"Received: $message")
      resettingTimeoutActor(context.scheduleOnce(1 second, context.self, "timeout"))
    }

    def resettingTimeoutActor(schedule: Cancellable): Behavior[String] = Behaviors.receive {
      (context: ActorContext[String], message: String) =>
        message match {
          case "timeout" =>
            context.log.info("Stopping")
            Behaviors.stopped
          case _ =>
            context.log.info(s"Received: $message")
            // reset scheduler
            schedule.cancel()
            // start another scheduler
            resettingTimeoutActor(context.scheduleOnce(1 second, context.self, "timeout"))
        }
    }
  }

  def demoActorResettingTimeout(): Unit = {
    val userGuardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val resettingTimeoutActor: ActorRef[String] =
        context.spawn(ResettingTimeoutActor(), "resettingTimeoutActor")

      context.log.info(s"[system] System starting.")
      resettingTimeoutActor ! "start timer"
      Thread.sleep(500)
      resettingTimeoutActor ! "reset"
      Thread.sleep(700)
      resettingTimeoutActor ! "this should still be visible"
      Thread.sleep(1200)
      resettingTimeoutActor ! "this should NOT be visible"

      Behaviors.empty
    }

    import utils._
    val system: ActorSystem[NotUsed] =
      ActorSystem(userGuardian, "demoResettingTimeoutActor").withFiniteLifespan(3 seconds)
  }

  def main(args: Array[String]): Unit = {
    demoActorResettingTimeout()
  }
}
