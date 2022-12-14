package part2actors

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

object ChildActors {

  /**
   * 1. actors can create other actors (child): parent -> child -> grandChild
   *
   * 2. actor hierarchy = tree-like structure
   *
   * 3. root of the hierarchy = "guardian" actor (created with the ActorSystem)
   *
   * 4. actors can be identified via a path: akka://actorSystem/user/parent/child
   *
   * 5. ActorSystem creates
   * - the top-level (root) guardian
   * - system guardian (for Akka internal messages)
   * - user guardian (for our custom actors)
   * ALL OUR ACTORS are child actors of the user guardian
   */

  object Parent {
    trait Command
    case class CreateChild(name: String) extends Command
    case class TellChild(message: String) extends Command
    case object StopChild extends Command
    case object WatchChild extends Command

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

    def active(childRef: ActorRef[String]): Behavior[Command] = Behaviors.receive[Command]{ (context: ActorContext[Command], message: Command) =>
      message match {
        case TellChild(message) =>
          context.log.info(s"[Parent] Sending message $message to child")
          childRef ! message // <- send a message to another actor
          Behaviors.same
        case StopChild =>
          context.log.info("[Parent] stopping child")
          context.stop(childRef) // only works with CHILD actors
          idle()
        case WatchChild =>
          context.log.info("[Parent] watching child")
          context.watch(childRef) // can use any ActorRef, that is, we can watch actors in other actor system
          Behaviors.same
        case _ =>
          context.log.info(s"[Parent] command not supported")
          Behaviors.same
      }
    }.receiveSignal { // work with Watching mechanism
      case (context, Terminated(childRefWhichDied)) =>
        context.log.info(s"[Parent] Child ${childRefWhichDied.path} has been terminated by something...")
        idle()
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
      parent ! WatchChild
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
    case class WatchChild(name: String) extends Command

    def apply(): Behavior[Command] = active(Map())

    def active(children: Map[String, ActorRef[String]]): Behavior[Command] = Behaviors.receive[Command] { (context: ActorContext[Command], message: Command) =>
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
        case WatchChild(name) =>
          context.log.info(s"[Parent] watching child with name $name")
          val childOption: Option[ActorRef[String]] = children.get(name)
          childOption.fold(
            context.log.info(s"[Parent] Child $name could not be watched: Name doesn't exit")
          )(context.watch)
          Behaviors.same
      }
    }.receiveSignal {
      case (context, Terminated(ref)) =>
        context.log.info(s"[Parent] Child ${ref.path} was killed.")
        val childName: String = ref.path.name
        active(children - childName)
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
      parent ! WatchChild("alice")
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
