package part3testing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class UsingProbesSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import UsingProbesSpec._

  "A master actor" should {
    val master = testKit.spawn(Master(), "master")
    val workerProbe = testKit.createTestProbe[WorkerTask]()
    val externalProbe = testKit.createTestProbe[ExternalProtocol]()

    "register a worker" in {
      master ! Register(workerProbe.ref, externalProbe.ref)
      externalProbe.expectMessage(RegisterAck)
    }

    "send a task to the worker actor" in {
      master ! Register(workerProbe.ref, externalProbe.ref)
      externalProbe.expectMessage(RegisterAck)

      val taskString = "Get to work!"
      master ! Work(taskString, externalProbe.ref)
      workerProbe.expectMessage(WorkerTask(taskString, master.ref, externalProbe.ref))

      // mocking the interaction with the worker actor
      master ! WorkCompleted(3, externalProbe.ref)
      externalProbe.expectMessage(Report(3))
    }

    "aggregate data correctly" in {
      val mockedWorkerBehavior = Behaviors.receiveMessage[WorkerTask] {
        case WorkerTask(_, masterRef, replyTo) =>
          masterRef ! WorkCompleted(3, replyTo)
          Behaviors.same
      }

      val workerProbe = testKit.createTestProbe[WorkerTask]()
      val mockedWorker = testKit.spawn(Behaviors.monitor(workerProbe.ref, mockedWorkerBehavior))

      master ! Register(mockedWorker, externalProbe.ref)
      externalProbe.expectMessage(RegisterAck)

      val taskString = "Get to work!"
      master ! Work(taskString, externalProbe.ref)
      master ! Work(taskString, externalProbe.ref)

      val replies = externalProbe.receiveMessages(2, 1 second)
        .collect {
          case Report(totalCount) => totalCount
        }

      replies should contain allOf(3, 6)
    }
  }
}

object UsingProbesSpec {
  /**
   * requester -> master -> worker
   *           <-        <-
   */
  trait MasterProtocol
  case class Work(text: String, replyTo: ActorRef[ExternalProtocol]) extends MasterProtocol
  case class WorkCompleted(count: Int, originalDestination: ActorRef[ExternalProtocol]) extends MasterProtocol
  case class Register(workerRef: ActorRef[WorkerTask], replyTo: ActorRef[ExternalProtocol]) extends MasterProtocol

  case class WorkerTask(text: String, master: ActorRef[MasterProtocol], originalDestination: ActorRef[ExternalProtocol])

  trait ExternalProtocol
  case class Report(totalCount: Int) extends ExternalProtocol
  case object RegisterAck extends ExternalProtocol

  object Master {
    def apply(): Behavior[MasterProtocol] = Behaviors.receiveMessage {
      case Register(workerRef, replyTo) =>
        replyTo ! RegisterAck
        active(workerRef)
      case _ =>
        Behaviors.same
    }

    def active(workerRef: ActorRef[WorkerTask], totalCount: Int = 0): Behavior[MasterProtocol] =
      Behaviors.receive { (context, message) =>
        message match {
          case Work(text, replyTo) =>
            workerRef ! WorkerTask(text, context.self, replyTo)
            Behaviors.same
          case WorkCompleted(count, destination) =>
            val newTotalCount = totalCount + count
            destination ! Report(newTotalCount)
            active(workerRef, newTotalCount)
          case Register(workerRef, replyTo) =>
            context.log.info(s"[worker] ${workerRef.path.name} has already been registered.")
            replyTo ! RegisterAck
            Behaviors.same
        }
      }
  }
}
