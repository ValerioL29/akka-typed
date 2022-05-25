package part2actors

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy, Terminated}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

object Supervision {

  object FussyWordCounter {
    def apply(): Behavior[String] = active(0)

    def active(total: Int): Behavior[String] = Behaviors.receive { (context: ActorContext[String], message: String) =>
      val wordCount: Int = message.split(" ").length
      context.log.info(s"Received piece of text: '$message', counted $wordCount words, total: ${total + wordCount}")
      // throw some exceptions (maybe unintentionally)
      if (message.startsWith("Q")) throw new RuntimeException("I HATE queues!")
      if (message.startsWith("W")) throw new NullPointerException
      active(total + wordCount)
    }
  }

  // actor throwing exception gets killed
  def demoCrash(): Unit = {
    val guardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val fussyCounter: ActorRef[String] = context.spawn(FussyWordCounter(), "fussyCounter")

      fussyCounter ! "Starting to understand this Akka business..."
      fussyCounter ! "Quick! Hide!"
      fussyCounter ! "Are you there?"

      Behaviors.empty
    }

    val system: ActorSystem[NotUsed] = ActorSystem(guardian, "DemoCrash")

    Thread.sleep(1000)
    system.terminate()
  }

  def demoWithParent(): Unit = {
    val parentBehavior: Behavior[String] = Behaviors.setup { context: ActorContext[String] =>
      val child: ActorRef[String] = context.spawn(FussyWordCounter(), "fussyChild")
      context.watch(child)

      Behaviors.receiveMessage[String] { message: String =>
        child ! message
        Behaviors.same
      }.receiveSignal {
        case (context, Terminated(childRef)) =>
          context.log.warn(s"Child failed: ${childRef.path.name}")
          Behaviors.same
      }
    }

    val guardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val fussyCounter: ActorRef[String] = context.spawn(parentBehavior, "fussyCounter")

      fussyCounter ! "Starting to understand this Akka business..."
      fussyCounter ! "Quick! Hide!"
      fussyCounter ! "Are you there?"

      Behaviors.empty
    }

    val system: ActorSystem[NotUsed] = ActorSystem(guardian, "DemoCrashWithParent")

    Thread.sleep(1000)
    system.terminate()
  }

  def demoSupervisionWithRestart(): Unit = {
    val parentBehavior: Behavior[String] = Behaviors.setup { context: ActorContext[String] =>
      // supervise the child with a restart "strategy"
      // - stop: default
      // - resume: failure ignored, actor state preserved
      // - restart: actor state reset
      val childBehavior: Behavior[String] = Behaviors.supervise(
        Behaviors.supervise(FussyWordCounter())
          .onFailure[RuntimeException](SupervisorStrategy.restart)
          // SupervisorStrategy.restartWithBackoff(1.second, 1.minute, 0.2)) | min time, max time, random factor of all restart actors
      ).onFailure[NullPointerException](SupervisorStrategy.resume)

      val child: ActorRef[String] = context.spawn(childBehavior, "fussyChild")
      context.watch(child)

      Behaviors.receiveMessage[String] { message: String =>
        child ! message
        Behaviors.same
      }.receiveSignal {
        case (context, Terminated(childRef)) =>
          context.log.warn(s"Child failed: ${childRef.path.name}")
          Behaviors.same
      }
    }

    val guardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val fussyCounter: ActorRef[String] = context.spawn(parentBehavior, "fussyCounter")

      fussyCounter ! "Starting to understand this Akka business..."
      fussyCounter ! "Quick! Hide!"
      fussyCounter ! "Are you there?"
      fussyCounter ! "What are you doing?"
      fussyCounter ! "Are you still there?"

      Behaviors.empty
    }

    val system: ActorSystem[NotUsed] = ActorSystem(guardian, "DemoCrashWithParent")

    Thread.sleep(1000)
    system.terminate()
  }

  /**
   * Exercise: how do we specify different supervisor strategies for different kinds of exceptions?
   */
  val differentStrategies: Behavior[String] = Behaviors.supervise(
    Behaviors.supervise(FussyWordCounter())
      .onFailure[NullPointerException](SupervisorStrategy.resume)
  )
    .onFailure[IndexOutOfBoundsException](SupervisorStrategy.restart)
  // Put the most specific exception INSIDE while the most general exception OUTSIDE

  def main(args: Array[String]): Unit = {
    demoSupervisionWithRestart()
  }
}
