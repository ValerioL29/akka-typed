package part4infra

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, DispatcherSelector}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random

object AkkaDispatchers {

  // Dispatchers are in charge of delivering and handling messages within an actor system
  import utils._

  def demoDispatcherConfig(): Unit = {
    val userGuardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      // val childActor: ActorRef[String] =
      //  context.spawn(LoggerActor[String](), "childDefault", DispatcherSelector.default())
      // val childActorBlocking: ActorRef[String] =
      //  context.spawn(LoggerActor[String](), "childBlocking", DispatcherSelector.blocking())
      // val childActorInherit: ActorRef[String] =
      //   context.spawn(LoggerActor[String](), "childInherit", DispatcherSelector.sameAsParent())
      // val childActorConfig: ActorRef[String] =
      //  context.spawn(LoggerActor[String](), "childConfig", DispatcherSelector.fromConfig("my-dispatcher"))

      val actors: IndexedSeq[ActorRef[String]] = (1 to 10).map((i: Int) =>
        context.spawn(LoggerActor[String](), s"child$i", DispatcherSelector.fromConfig("my-dispatcher")))

      val r = new Random()
      (1 to 1000).foreach((i: Int) => actors(r.nextInt(10)) ! s"task$i")

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoDispatchers").withFiniteLifespan(2 seconds)
  }

  object DBActor {
    def apply(): Behavior[String] = Behaviors.receive { (context: ActorContext[String], message: String) =>
      import context.executionContext // same as system.executionContext, SAME AS THE SYSTEM's DISPATCHER
      // If it is configured, this will be "this.actor.dispatcher" which can be changed via "application.conf"
      Future {
        Thread.sleep(1000)
        println(s"Query successful: $message")
      }

      Behaviors.same
    }
  }

  def demoBlockingCalls(): Unit = {
    val userGuardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val loggerActor: ActorRef[String] =
        context.spawn(LoggerActor[String](), "logger")
      val dbActor: ActorRef[String] =
        context.spawn(DBActor(), "db", DispatcherSelector.fromConfig("dedicated-blocking-dispatcher"))

      (1 to 100).foreach { i: Int =>
        val message = s"query $i"
        dbActor ! message
        loggerActor ! message
      }

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoBlockingCalls",
      ConfigFactory.load().getConfig("dispatchers-demo"))
  }

  def main(args: Array[String]): Unit = {
    demoBlockingCalls()
  }
}
