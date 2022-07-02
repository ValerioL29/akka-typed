package part3testing

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.Logger

class InterceptingLogsSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import InterceptingLogsSpec._

  val item: String = "\"Rock the JVM Akka Typed course\""
  val validCreditCard: String = "1234-1234-1234-1234"
  val invalidCreditCard: String = "0000-0000-0000-0000"

  "A checkout flow" should {
    val checkoutActor: ActorRef[PaymentProtocol] =
      testKit.spawn(CheckoutActor(), "checkoutActor")

    "correctly dispatch an order for a valid credit card" in {
      // filter log messages of level INFO which contain the string "Order"
      LoggingTestKit.info("Order")
        // "[0-9]+" is a Regular Expression pattern for string pattern matching
        .withMessageRegex(s"Order [0-9]+ for item $item has been dispatched.")
        .withOccurrences(1)
        .expect { // scenario under test
          checkoutActor ! Checkout(item, validCreditCard)
        }
    }

    "freak out if the payment is declined" in {
      LoggingTestKit.error[RuntimeException]
        .withOccurrences(1)
        .expect {
          checkoutActor ! Checkout(item, invalidCreditCard)
        }
    }
  }
}

object InterceptingLogsSpec {

  // payment system
  // checkout actor -> payment manager
  //                <-
  //                -> fulfillment manager
  //                <- deny / order
  trait PaymentProtocol
  case class Checkout(item: String, creditCard: String) extends PaymentProtocol
  case class AuthorizeCard(creditCard: String, replyTo: ActorRef[PaymentProtocol]) extends PaymentProtocol
  case object PaymentAccepted extends PaymentProtocol
  case object PaymentDeclined extends PaymentProtocol
  case class DispatchOrder(item: String, replyTo: ActorRef[PaymentProtocol]) extends PaymentProtocol
  case object OrderConfirmed extends PaymentProtocol
  case object OrderRejected extends PaymentProtocol

  object CheckoutActor {
    def apply(): Behavior[PaymentProtocol] = Behaviors.setup { context: ActorContext[PaymentProtocol] =>
      val paymentManger: ActorRef[PaymentProtocol] =
        context.spawn(PaymentManger(), "paymentManager")
      val fulfillmentManager: ActorRef[PaymentProtocol] =
        context.spawn(FulfillmentManager(), "fulfillmentManager")
      val logger: Logger = context.log

      def awaitingCheckout(): Behavior[PaymentProtocol] = Behaviors.receiveMessage {
        case Checkout(item, card) =>
          logger.info(s"Received order for item $item")
          paymentManger ! AuthorizeCard(card, context.self)
          pendingPayment(item)
        case _ =>
          Behaviors.same
      }

      def pendingPayment(item: String): Behavior[PaymentProtocol] = Behaviors.receiveMessage {
        case PaymentAccepted =>
          logger.info(s"Checkout completed! Payment was accepted.")
          fulfillmentManager ! DispatchOrder(item, context.self)
          pendingDispatch(item)
        case PaymentDeclined =>
          logger.info(s"Checkout failed! Payment was declined.")
          throw new RuntimeException("Cannot handle invalid payments")
      }

      def pendingDispatch(item: String): Behaviors.Receive[PaymentProtocol] = Behaviors.receiveMessage {
        case OrderConfirmed =>
          logger.info(s"Dispatch for $item confirmed")
          awaitingCheckout()
        case OrderRejected =>
          logger.info(s"Dispatch for $item rejected")
          throw new RuntimeException("Dispatching failed")
      }

      // the first state
      awaitingCheckout()
    }
  }

  object PaymentManger {
    def apply(): Behavior[PaymentProtocol] = Behaviors.receiveMessage {
      case AuthorizeCard(card, replyTo) =>
        if (card.startsWith("0")) // validation process
          replyTo ! PaymentDeclined
        else
          replyTo ! PaymentAccepted
        Behaviors.same
      case _ =>
        Behaviors.same
    }
  }

  object FulfillmentManager {
    def apply(): Behavior[PaymentProtocol] = active(43)

    def active(orderId: Int): Behavior[PaymentProtocol] =
      Behaviors.receive { (context: ActorContext[PaymentProtocol], message: PaymentProtocol) =>
        val logger: Logger = context.log

        message match {
          case DispatchOrder(item, replyTo) =>
            logger.info(s"Order $orderId for item $item has been dispatched.")
            replyTo ! OrderConfirmed
            active(orderId + 1)
          case _ =>
            Behaviors.same
        }
      }
  }
}
