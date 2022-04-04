package part2actors

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

    override def receive: Receive = countReceive(0)

    def countReceive(currentCount: Int): Receive = {
      case Increment => context.become(countReceive(currentCount + 1))
      case Decrement => context.become(countReceive(currentCount - 1))
      case Print => println(s"[counter] my current count is $currentCount")
    }

  }

  import Counter._

  val system = ActorSystem("changingActorBehaviourExercise")
  val counter = system.actorOf(Props[Counter], "myStatelessCounter")
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
    override def receive: Receive = {
      case Vote(c) => context.become(voted(c))// candidate = Some(c)
      case VoteStatusRequest => sender() ! VoteStatusReply(None)
    }

    def voted(candidate: String): Receive = {
      case VoteStatusRequest => sender() ! VoteStatusReply(Some(candidate))
    }
  }

  case class AggregateVotes(citizens: Set[ActorRef])
  class VoteAggregator extends Actor {
    override def receive: Receive = awaitingCommand

    def awaitingCommand: Receive = {
      case AggregateVotes(citizens) =>
        context.become(awaitingStatuses(citizens, Map()))
        citizens.foreach(citizenRef => citizenRef ! VoteStatusRequest)
    }

    def awaitingStatuses(stillWaiting: Set[ActorRef], currentStats: Map[String, Int]): Receive = {
      case VoteStatusReply(None) =>
        // a citizen hasn't voted yet
        sender() ! VoteStatusRequest // this might be ended up with a infinite loop
        // In this testing scenario, we ensure that all our citizens have voted
      case VoteStatusReply(Some(candidate)) =>
        val newStillWaiting = stillWaiting - sender()
        val currentVotesOfCandidate = currentStats.getOrElse(candidate, 0)
        val newStats = currentStats + (candidate -> (currentVotesOfCandidate + 1))
        if (newStillWaiting.isEmpty) println(s"[aggregator] poll stats: ${newStats.mkString("\n{\n  ", ",\n  ", "\n}")}")
        else context.become(awaitingStatuses(newStillWaiting, newStats))
    }
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
