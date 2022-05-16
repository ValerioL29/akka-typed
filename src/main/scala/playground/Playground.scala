package playground

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Playground {
  def main(args: Array[String]): Unit = {
    val root: ActorSystem[String] = ActorSystem(
      Behaviors.receive[String] { (context: ActorContext[String], message: String) =>
        context.log.info(s"Just received: $message")
        Behaviors.same
      },
      "DummySystem"
    )

    root ! "Hey, Akka!"

    implicit val ec: ExecutionContext = root.executionContext
    root.scheduler.scheduleOnce(3.seconds, () => root.terminate())
  }
}
