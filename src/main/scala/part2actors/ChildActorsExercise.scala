package part2actors

import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.slf4j.Logger

/**
 * Exercise: Distributed Word Counting
 *
 * Scenario -
 *     requester ----- (computational task) ----> WCM ----- (computational task) ----> one child of type WCW
 *     requester ----- (computational task) <---- WCM ----- (computational task) <----
 *
 *     Scheme for scheduling tasks to children: round robin
 *     [1-10]
 *     task 1 - child 1
 *     task 2 - child 2
 *     .
 *     .
 *     .
 *     task 10 - child 10
 *     task 11 - child 1
 *     task 12 - child 2
 *     .
 *     .
 *     .
 */
object ChildActorsExercise {
  /**
   * Messages supported by master
   */
  trait MasterProtocol
  case class Initialize(nChildren: Int) extends MasterProtocol
  case class WordCountTask(text: String, replyTo: ActorRef[UserProtocol]) extends MasterProtocol
  case class WordCountReply(id: Int, count: Int) extends MasterProtocol
  /**
   * Messages supported by worker
   */
  trait WorkerProtocol
  case class WorkerTask(id: Int, text: String) extends WorkerProtocol
  /**
   * Messages supported by user
   */
  trait UserProtocol
  case class Reply(count: Int) extends UserProtocol

  object WordCounterMaster {
    def apply(): Behavior[MasterProtocol] =
      Behaviors.receive { (context: ActorContext[MasterProtocol], message: MasterProtocol) =>
        val logger: Logger = context.log

        message match {
          case Initialize(n) =>
            logger.info(s"[master] Start initializing with $n worker actors.")
            val childRefs: IndexedSeq[ActorRef[WorkerProtocol]] =
              for {
                i <- 1 to n
              } yield context.spawn(WordCounterWorker(context.self), s"worker$i")

            active(childRefs, 0, 0, Map())
        }
      }

    def active(childRefs: Seq[ActorRef[WorkerProtocol]],
               currentChildIndex: Int,
               currentTaskId: Int,
               requestMap: Map[Int, ActorRef[UserProtocol]]): Behavior[MasterProtocol] =
      Behaviors.receive { (context: ActorContext[MasterProtocol], message: MasterProtocol) =>
        val logger: Logger = context.log

        message match {
          case WordCountTask(text, replyTo) =>
            logger.info(s"[master] I have received: $text")
            logger.info(s"[master] I will send it to the child $currentChildIndex")
            // preparation
            val task: WorkerTask = WorkerTask(currentTaskId, text)
            val childRef: ActorRef[WorkerProtocol] = childRefs(currentChildIndex)
            // send task to child
            childRef ! task
            // update my data
            val nextChildIndex: Int = (currentChildIndex + 1) % childRefs.length // round robin scheme!
            val nextTaskId: Int = currentTaskId + 1
            val newRequestMap: Map[Int, ActorRef[UserProtocol]] = requestMap + (currentTaskId -> replyTo)
            // change behavior
            active(childRefs, nextChildIndex, nextTaskId, newRequestMap)
          case WordCountReply(id, count) =>
            logger.info(s"[master] I have received a reply for task id: $id with count: $count")
            // preparation
            val originalSender: ActorRef[UserProtocol] = requestMap(id)
            // send back the result to the original requester
            originalSender ! Reply(count)
            // change behavior: removing the task id from the map because it is done
            active(childRefs, currentChildIndex, currentTaskId, requestMap - id)
          case _ =>
            logger.info(s"[master] command not supported while idle")
            Behaviors.same
        }
      }
  }

  object WordCounterWorker {
    def apply(masterRef: ActorRef[MasterProtocol]): Behavior[WorkerProtocol] =
      Behaviors.receive { (context: ActorContext[WorkerProtocol], message: WorkerProtocol) =>
        val logger: Logger = context.log

        message match {
          case WorkerTask(id, text) =>
            logger.info(s"[${context.self.path.name}] I have received task: $id with: $text")
            // do the work
            val result: Int = text.split(" ").length
            // send back the result to the master
            masterRef ! WordCountReply(id, result)
            Behaviors.same
          case _ =>
            logger.info(s"[${context.self.path.name}] Command unsupported.")
            Behaviors.same
        }
      }
  }

  object Aggregator {
    def apply(): Behavior[UserProtocol] = active(0)

    def active(totalWords: Int): Behavior[UserProtocol] =
      Behaviors.receive { (context: ActorContext[UserProtocol], message: UserProtocol) =>
        val logger: Logger = context.log

        message match {
          case Reply(count) =>
            val newTotalWords: Int = totalWords + count
            logger.info(s"[aggregator] I have received: $count, total is: $newTotalWords")
            active(newTotalWords)
        }

      }
  }

  def testWordCounter(): Unit = {
    val userGuardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val aggregator: ActorRef[UserProtocol] = context.spawn(Aggregator(), "aggregator")
      val wcm: ActorRef[MasterProtocol] = context.spawn(WordCounterMaster(), "master")

      wcm ! Initialize(3)
      wcm ! WordCountTask("I love Akka", aggregator)
      wcm ! WordCountTask("Scala is super dope", aggregator)
      wcm ! WordCountTask("yes it is", aggregator)
      wcm ! WordCountTask("Testing round robin scheduling", aggregator)

      Behaviors.empty
    }

    val system: ActorSystem[NotUsed] = ActorSystem(userGuardian, "WordCounting")
    Thread.sleep(1000)
    system.terminate()
  }

  def main(args: Array[String]): Unit = {
    testWordCounter()
  }
}
