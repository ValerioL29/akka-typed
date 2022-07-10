package part4infra

import akka.NotUsed
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, PoolRouter, Routers}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object AkkaRouters {

  import utils._

  def demoPoolRouter(): Unit = {
    val workerBehavior: Behavior[String] = LoggerActor[String]()
    // order offering follows round robin
    val poolRouter: PoolRouter[String] = Routers.pool(5)(workerBehavior) // a "master"

    val userGuardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>

      val poolActor: ActorRef[String] = context.spawn(poolRouter, "pool")

      (1 to 10).foreach((num: Int) => poolActor ! s"work task $num")

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoPoolRouter").withFiniteLifespan(2 seconds)
  }

  def demoGroupRouter(): Unit = {
    // take the type that we want to find
    val serviceKey: ServiceKey[String] = ServiceKey[String]("logWorker") // id is the name of the key
    // service keys are used by a core akka module for DISCOVERING actors and fetching their refs

    val userGuardian: Behavior[NotUsed] = Behaviors.setup[NotUsed] { context: ActorContext[NotUsed] =>
      // in real life the workers may be created elsewhere in your code
      val workers: IndexedSeq[ActorRef[String]] = (1 to 5).map((i: Int) =>
        context.spawn(LoggerActor[String](), s"worker$i"))
      // register the workers with the service key
      workers.foreach((worker: ActorRef[String]) =>
        context.system.receptionist ! Receptionist.Register(serviceKey, worker))

      val groupBehavior: Behavior[String] =
        Routers.group(serviceKey).withRoundRobinRouting() // random by default
      val groupRouter: ActorRef[String] =
        context.spawn(groupBehavior, "workerGroup")

      (1 to 10).foreach((i: Int) => groupRouter ! s"work task $i")

      // add new workers later
      Thread.sleep(1000)
      val extraWorker: ActorRef[String] = context.spawn(LoggerActor[String](), "extraWorker")
      context.system.receptionist ! Receptionist.Register(serviceKey, extraWorker)
      (1 to 12).foreach((i: Int) => groupRouter ! s"work task $i")

      /**
       * Removing workers:
       * - send the receptionist a Receptionist.Deregister(serviceKey, worker, someActorToReceiveConfirmation)
       * - receive Receptionist.Deregister in someActorToReceiveConfirmation
       *   best practice => someActorToReceiveConfirmation == worker
       *   although in this time, there's a risk that the router might still use the worker as the router
       * - safe to stop the worker
       */

      Behaviors.empty
    }

    ActorSystem(userGuardian, "demoGroupRouter").withFiniteLifespan(2 second)
  }

  def main(args: Array[String]): Unit = {
    demoGroupRouter()
  }
}
