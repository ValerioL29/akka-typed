package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ChangingActorBehaviour.Mom.MomStart

/**
 * How to change an Actor's behavior with time
 * @author Jiacheng Li - April 4th
 * @version 0.1
 */
object ChangingActorBehaviour extends App {

  object FussyKid {
    case object KidAccept
    case object KidReject
    val HAPPY = "happy"
    val SAD = "sad"
  }

  class FussyKid extends Actor {
    import FussyKid._
    import Mom._

    // internal state of the kid
    var state: String = HAPPY // It actually can be even less mutable! => stateless actor!
    override def receive: Receive = {
      case Food(VEGETABLE) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) =>
        if (state == HAPPY) sender() ! KidAccept
        else sender() ! KidReject
    }
  }

  class StatelessFussyKid extends Actor {
    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceive

    def happyReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, discardOld = false) // change my receive handler to sadReceive
      case Food(CHOCOLATE) => context.unbecome() // stay happy
      case Ask(_) => sender() ! KidAccept
    }
    def sadReceive: Receive = {
      case Food(VEGETABLE) => context.become(happyReceive, discardOld = false)// stay sad
      case Food(CHOCOLATE) => context.unbecome() // change my receive handler to happyReceive
      case Ask(_) => sender() ! KidReject
    }
  }

  object Mom {
    case class MomStart(kidRef: ActorRef)
    case class Food(food: String)
    case class Ask(message: String) // do you want to play?
    val VEGETABLE: String = "veggies"
    val CHOCOLATE: String = "chocolate"
  }

  class Mom extends Actor {
    import Mom._
    import FussyKid._
    override def receive: Receive = {
      case MomStart(kidRef) =>
        // test our interaction
        kidRef ! Food(VEGETABLE)
        kidRef ! Food(VEGETABLE)
        kidRef ! Food(CHOCOLATE)
        kidRef ! Ask("[mom actor] do you want to play?")
      case KidAccept => println("[mom actor] Yay, my kid is happy!")
      case KidReject => println("[mom actor] My kid is sad, but as he's healthy!")
    }
  }

  val system = ActorSystem("changingActorBehaviorDemo")
  val fussyKid = system.actorOf(Props[FussyKid], "fussyKid")
  val statelessFussyKid = system.actorOf(Props[StatelessFussyKid], "statelessFussyKid")
  val mom = system.actorOf(Props[Mom], "mom")

  /**
   * mom receives MomStart
   * - kid receives Food(veg)  -> kid will change the handler to sadReceive
   * - kid receives Ask(play?) -> kid replies with the sadReceive handler
   * mom receives KidReject
   */
  mom ! MomStart(fussyKid)
  mom ! MomStart(statelessFussyKid)

  /**
   * New messages
   * Originally:
   * - Food(veg) -> message handler turns to sadReceive
   * - Food(chocolate) -> become happyReceive
   *
   * After adding discardOld = false:
   * Food(veg) -> stack.push(sadReceive)
   * Food(chocolate) -> stack.push(happyReceive)
   *
   * Stack:
   * 1. happyReceive
   * 2. sadReceive
   * 3. happyReceive
   */
}
