package part2actors

import ChangingActorBehaviour._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChangingActorBehaviourExercises extends App {
  /**
   * Exercises
   * 1 - Recreate the Counter Actor with context.become and NO MUTABLE STATE
   */
  // DOMAIN of the counter, in practice
  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }
  class Counter extends Actor {
    import Counter._

    override def receive: Receive = ???
  }

  import Counter._

  val system = ActorSystem("changingActorBehaviourExercise")
  val counter = system.actorOf(Props[Counter], "myCounter")
  (1 to 5).foreach(_ => counter ! Increment)
  (1 to 3).foreach(_ => counter ! Decrement)
  counter ! Print

  /**
   * Exercise 2 - a simplified voting system
   *
   */
  case class Vote(candidate: String)
  case class VoteStatusRequest()
  case class VoteStatusReply(candidate: Option[String])
  class Citizen extends Actor {
    override def receive: Receive = ??? // TODO
  }

  case class AggregateVotes(citizens: Set[ActorRef])
  class VoteAggregator extends Actor {
    override def receive: Receive = ??? // TODO
  }

  val alice = system.actorOf(Props[Citizen], "alice")
  val bob = system.actorOf(Props[Citizen], "bob")
  val charlie = system.actorOf(Props[Citizen], "charlie")
  val ken = system.actorOf(Props[Citizen], "ken")

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  charlie ! Vote("Roland")
  ken ! Vote("Roland")

  val voteAggregator = system.actorOf(Props[VoteAggregator])
  voteAggregator ! AggregateVotes(Set(alice, bob, charlie, ken))

  /**
   * Print the status of the votes
   * Martin -> 1
   * Jonas -> 1
   * Roland -> 2
   */
}
