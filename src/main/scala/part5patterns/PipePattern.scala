package part5patterns

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import utils._

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

object PrivateExecutionContext {
  val executor: ExecutorService = Executors.newFixedThreadPool(4)
  implicit val externalEC: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(executor)
}

object PipePattern {

  // interaction with an external service that returns Futures
  val db: Map[String, Int] = Map(
    "Ken" -> 123,
    "Norman" -> 456,
    "Yao" -> 789
  )
  import PrivateExecutionContext._

  trait PhoneCallProtocol
  case class FindAndCallPhoneNumber(name: String) extends PhoneCallProtocol
  case class InitiatePhoneCall(number: Int) extends PhoneCallProtocol
  case class LogPhoneCallFailure(reason: Throwable) extends PhoneCallProtocol

  def callExternalService(name: String): Future[Int] = {
    // select phoneNo from people where ... => DB query
    Future(db(name))
  }

  object PhoneCallActor {
    def apply(): Behavior[PhoneCallProtocol] = Behaviors.receive { (context: ActorContext[PhoneCallProtocol], message: PhoneCallProtocol) =>
      message match {
        case FindAndCallPhoneNumber(name) =>
          // pipe pattern
          // 1 - have the future ready
          val phoneNumberFuture: Future[Int] = callExternalService(name)
          // 2 - pipe the Future result back to me as a message
          context.pipeToSelf(phoneNumberFuture) {
            case Success(number) => InitiatePhoneCall(number)
            case Failure(exception) => LogPhoneCallFailure(exception)
          }
          Behaviors.same
        case InitiatePhoneCall(number) =>
          // perform the phone call
          context.log.info(s"Initiating phone call to $number")
          Behaviors.same
        case LogPhoneCallFailure(reason) =>
          context.log.warn(s"Initiating phone call failed: $reason")
          Behaviors.same
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val phoneCallActor: ActorRef[PhoneCallProtocol] = context.spawn(PhoneCallActor(), "phoneCallActor")

      phoneCallActor ! FindAndCallPhoneNumber("Ken")
      phoneCallActor ! FindAndCallPhoneNumber("Superman")

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoPipePattern").withFiniteLifespan(2 seconds)
  }
}
