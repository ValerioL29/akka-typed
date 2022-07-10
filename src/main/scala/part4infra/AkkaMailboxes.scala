package part4infra

import akka.NotUsed
import akka.actor.ActorSystem.Settings
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, MailboxSelector}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}
import utils._

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object AkkaMailboxes {

  /**
   * A custom priority mailbox: a support ticketing system.
   * P0, P1, P2, P3, ... => labels for prioritizing
   * - [P1] Bug fix #43: ...
   * - [P0] Urgent fix needed
   */
  trait Command
  case class SupportTicket(contents: String) extends Command
  case class Log(contents: String) extends Command

  class SupportTicketPriorityMailbox(settings: Settings, config: Config)
    extends UnboundedPriorityMailbox(
      PriorityGenerator {
        case SupportTicket(contents) if contents.startsWith("[P0]") => 0
        case SupportTicket(contents) if contents.startsWith("[P1]") => 1
        case SupportTicket(contents) if contents.startsWith("[P2]") => 2
        case SupportTicket(contents) if contents.startsWith("[P3]") => 3
        case _ => 4
      }
    )

  def demoSupportTicketMailbox(): Unit = {
    val userGuardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val actor: ActorRef[Command] =
        context.spawn(LoggerActor[Command](), "ticketLogger", MailboxSelector.fromConfig("support-ticket-mailbox"))

      actor ! Log("This is a log that is received first but processed last.")
      actor ! SupportTicket("[P1] this thing is broken")
      actor ! SupportTicket("[P0] FIX THIS NOW!")
      actor ! SupportTicket("[P3] something nice to have")

      /**
       * When a thread is allocated, whatever is in the mailbox (already ordered) will get handled.
       */

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoMailbox", ConfigFactory.load().getConfig("mailboxes-demo"))
      .withFiniteLifespan(2 seconds)
  }

  // Prioritize at all above
  case object ManagementTicket extends ControlMessage with Command

  def demoControlAwareMailbox(): Unit = {
    val userGuardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val actor: ActorRef[Command] =
        context.spawn(LoggerActor[Command](), "controlAwareLogger",
          MailboxSelector.fromConfig("control-mailbox"))

      actor ! SupportTicket("[P1] this thing is broken")
      actor ! SupportTicket("[P0] FIX THIS NOW!")
      actor ! ManagementTicket

      /**
       * When a thread is allocated, whatever is in the mailbox (already ordered) will get handled.
       */

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoControlAwareMailbox", ConfigFactory.load().getConfig("mailboxes-demo"))
      .withFiniteLifespan(2 seconds)
  }

  def main(args: Array[String]): Unit = {
    // demoSupportTicketMailbox()
    // Output:
    //    20:56:35.787 [DemoMailbox-akka.actor.default-dispatcher-3] INFO akka.event.slf4j.Slf4jLogger - Slf4jLogger started 20:56:36.080 [DemoMailbox-akka.actor.default-dispatcher-3] INFO utils.package$LoggerActor$ - [ticketLogger] Received: SupportTicket([P0] FIX THIS NOW!)
    //    20:56:36.081 [DemoMailbox-akka.actor.default-dispatcher-3] INFO utils.package$LoggerActor$ - [ticketLogger] Received: SupportTicket([P1] this thing is broken)
    //    20:56:36.081 [DemoMailbox-akka.actor.default-dispatcher-3] INFO utils.package$LoggerActor$ - [ticketLogger] Received: SupportTicket([P3] something nice to have)
    //    20:56:36.082 [DemoMailbox-akka.actor.default-dispatcher-3] INFO utils.package$LoggerActor$ - [ticketLogger] Received: Log(This is a log that is received first but processed last.)
    //    20:56:38.255 [DemoMailbox-akka.actor.default-dispatcher-3] INFO akka.actor.CoordinatedShutdown - Running CoordinatedShutdown with reason [ActorSystemTerminateReason]
    demoControlAwareMailbox()
    // Output:
    // 21:02:37.344 [DemoControlAwareMailbox-akka.actor.default-dispatcher-3] INFO akka.event.slf4j.Slf4jLogger - Slf4jLogger started 21:02:37.612 [DemoControlAwareMailbox-akka.actor.default-dispatcher-3] INFO utils.package$LoggerActor$ - [controlAwareLogger] Received: ManagementTicket
    // 21:02:37.614 [DemoControlAwareMailbox-akka.actor.default-dispatcher-3] INFO utils.package$LoggerActor$ - [controlAwareLogger] Received: SupportTicket([P1] this thing is broken)
    // 21:02:37.614 [DemoControlAwareMailbox-akka.actor.default-dispatcher-3] INFO utils.package$LoggerActor$ - [controlAwareLogger] Received: SupportTicket([P0] FIX THIS NOW!)
    // 21:02:39.747 [DemoControlAwareMailbox-akka.actor.default-dispatcher-3] INFO akka.actor.CoordinatedShutdown - Running CoordinatedShutdown with reason [ActorSystemTerminateReason]
  }
}
