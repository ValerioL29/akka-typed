package part2actors

import akka.NotUsed
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

  object Parent {
    trait Command
    case class CreateChild(name: String) extends Command
    case class TellChild(message: String) extends Command
    case object StopChild extends Command

    def apply(): Behavior[Command] = idle()

    def idle(): Behavior[Command] = Behaviors.receive { (context: ActorContext[Command], message: Command) =>
      message match {
        case CreateChild(name) =>
          context.log.info(s"[Parent] Creating child with name $name")
          //  creating a child actor REFERENCE (used to send messages to this child)
          val childRef: ActorRef[String]  = context.spawn(Child(), name)
          active(childRef)
      }
    }

    def active(childRef: ActorRef[String]): Behavior[Command] = Behaviors.receive { (context: ActorContext[Command], message: Command) =>
      message match {
        case TellChild(message) =>
          context.log.info(s"[Parent] Sending message $message to child")
          childRef ! message // <- send a message to another actor
          Behaviors.same
        case StopChild =>
          context.log.info("[Parent] stopping child")
          context.stop(childRef) // only works with CHILD actors
          idle()
        case _ =>
          context.log.info(s"[Parent] command not supported")
          Behaviors.same
      }
    }
  }

  object Child {
    def apply(): Behavior[String] = Behaviors.receive { (context: ActorContext[String], message: String) =>
      context.log.info(s"[Child ${context.self.path}] Received $message")
      Behaviors.same
    }
  }

  def demoParentChild(): Unit = {
    import Parent._

    val userGuardianBehavior: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      // set up all the important actors in your application
      // set up the initial interaction between the actors
      val parent: ActorRef[Command] = context.spawn(Parent(), "parent")

      parent ! CreateChild("child")
      parent ! TellChild("hey kid, you there?")
      parent ! StopChild
      parent ! CreateChild("child2")
      parent ! TellChild("yo new kid, how are you?")
      // user guardian usually has no behavior of its own
      Behaviors.empty
    }

    val system: ActorSystem[NotUsed] = ActorSystem(userGuardianBehavior, "DemoParentChild")

    Thread.sleep(1000)

    system.terminate()
  }

  object Parent_V2 {
    trait Command
    case class CreateChild(name: String) extends Command
    case class TellChild(name: String, message: String) extends Command
    case class StopChild(name: String) extends Command

    def apply(): Behavior[Command] = active(Map())

    def active(children: Map[String, ActorRef[String]]): Behavior[Command] = Behaviors.receive { (context: ActorContext[Command], message: Command) =>
      message match {
        case CreateChild(name) =>
          context.log.info(s"[Parent] Creating child: $name")
          val childRef: ActorRef[String] = context.spawn(Child(), name)
          active(children + (name -> childRef))
        case TellChild(name, message) =>
          val childOption: Option[ActorRef[String]] = children.get(name)
          childOption.fold(context.log.info(s"[Parent] Child '$name' could not be found"))((child: ActorRef[String]) => child ! message)
          Behaviors.same
        case StopChild(name) =>
          context.log.info(s"[Parent] attempting to stop child with name $name")
          val childOption: Option[ActorRef[String]] = children.get(name)
          childOption.fold(
            context.log.info(s"[Parent] Child $name could not be stopped: Name doesn't exit")
          )(context.stop)
          active(children - name)
      }
    }
  }

  def demoParentChild_v2(): Unit = {
    import Parent_V2._
    val userGuardianBehavior: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val parent: ActorRef[Command] = context.spawn(Parent_V2(), "parent")
      parent ! CreateChild("alice")
      parent ! CreateChild("bob")
      parent ! TellChild("alice", "living next door to you")
      parent ! TellChild("ken", "I hope your Akka skills are good")
      parent ! StopChild("alice")
      parent ! TellChild("alice", "hey Alice, you still there?")

      Behaviors.empty
    }

    val system: ActorSystem[NotUsed] = ActorSystem(userGuardianBehavior, "DemoParentChildV2")
    Thread.sleep(1000)
    system.terminate()
  }

  def main(args: Array[String]): Unit = {
    demoParentChild_v2()
  }
}
