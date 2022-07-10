package part4infra

import akka.actor.testkit.typed.scaladsl.{ManualTime, ScalaTestWithActorTestKit}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class TimerSpec extends ScalaTestWithActorTestKit(ManualTime.config) with AnyWordSpecLike {

  import TimerSpec._

  "A reporter" should {
    "trigger report in an hour" in {
      val probe = testKit.createTestProbe[Command]()
      val time = ManualTime()

      testKit.spawn(Reporter(probe.ref))

      probe.expectNoMessage(1 second)
      // accelerate time
      time.timePasses(1 hour)
      // assertions "after" 1 hour
      probe.expectMessage(Report)
    }
  }
}

object TimerSpec {

  trait Command
  case object Timeout extends Command
  case object Report extends Command

  object Reporter {
    def apply(destination: ActorRef[Command]): Behavior[Command] = Behaviors.withTimers { timer =>
      timer.startSingleTimer(Timeout, 1 hour)
      // context.system.scheduler.scheduleOnce(...) - More conky

      Behaviors.receiveMessage {
        case Timeout =>
          destination ! Report
          Behaviors.same
      }
    }
  }
}
