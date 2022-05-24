package part2actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

object StoppingActors {

  object SensitiveActor {
    def apply(): Behavior[String] = Behaviors.receive[String] { (context: ActorContext[String], message: String) =>
      context.log.info(s"Received: $message")
      if(message == "you're ugly"){
        // optionally pass a () => Unit to clear up resources after the actor is stopped
        // 1. zero lambda approach
        //  Behaviors.stopped(() =>
        //    context.log.info("I'm stopped now, not receiving other messages")
        //  )
        Behaviors.stopped
      }
      else Behaviors.same
    }.receiveSignal {
      case (context, PostStop) =>
        // clean up resources that this actor might use
        context.log.info("I'm stopping now.")
        Behaviors.same
    }
  }

  def demoSensitiveActor(): Unit = {
        val userGuardian: Behavior[Unit] = Behaviors.setup[Unit] { context: ActorContext[Unit] =>
          val sensitiveActor: ActorRef[String] = context.spawn(SensitiveActor(), "sensitiveActor")

          sensitiveActor ! "Hi"
          sensitiveActor ! "How are you"
          sensitiveActor ! "you're ugly"
          sensitiveActor ! "sorry about that"

          Behaviors.empty
        }

        val system: ActorSystem[Unit] = ActorSystem(userGuardian, "DemoStoppingActors")
        Thread.sleep(1000)
        system.terminate()
  }

  def main(args: Array[String]): Unit = {
    demoSensitiveActor()
  }
}
