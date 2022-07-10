import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.concurrent.duration.FiniteDuration

package object utils {

  object LoggerActor {
    def apply[A](): Behavior[A] = Behaviors.receive { (context: ActorContext[A], message: A) =>
      context.log.info(s"[${context.self.path.name}] Received: $message")
      Behaviors.same
    }
  }

  implicit class ActorSystemEnhancement[A](system: ActorSystem[A]) {
    def withFiniteLifespan(duration: FiniteDuration): ActorSystem[A] = {
      import system.executionContext
      system.scheduler.scheduleOnce(duration, () => system.terminate())
      system
    }
  }

}
