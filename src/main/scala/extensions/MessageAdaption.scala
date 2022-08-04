package extensions

import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, DispatcherSelector}
import org.slf4j.Logger

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object MessageAdaption {

  /**
   * Interaction logic:
   * customer -> check-out -> shopping cart
   *            "frontend" ->   "backend"
   */
  object StoreDomain {
    // never use double for money - for illustration purposes
    case class Product(name: String, price: Double)
  }

  object ShoppingCart {

    import StoreDomain._

    sealed trait Request
    case class GetCurrentCart(cartId: String, replyTo: ActorRef[Response]) extends Request
    // + some others

    sealed trait Response
    case class CurrentCart(cartId: String, items: List[Product]) extends Response
    // + some others

    val db: Map[String, List[Product]] = Map {
      "123-abc-456" -> List(Product("iPhone", 699), Product("selfie stick", 35))
    }

    def dummy(): Behavior[Request] = Behaviors.setup { context: ActorContext[Request] =>
      Behaviors.receiveMessage {
        case GetCurrentCart(cartId, replyTo) =>
          val mayBeCart: Try[List[Product]] = Try(db(cartId))

          mayBeCart match {
            case Success(products) => replyTo ! CurrentCart(cartId, products)
            case Failure(_) =>
              context.log.error(s"[${context.self.path.name}] No such cart stored in db with id: $cartId")
          }

          Behaviors.same
      }
    }
  }

  object Checkout {

    import ShoppingCart._

    // this is what we receive from the customer
    sealed trait Request
    final case class InspectSummary(cartId: String, replyTo: ActorRef[Response]) extends Request
    // + some others
    private final case class WrappedShoppingCartResponse(response: ShoppingCart.Response) extends Request

    // this is what we send to the customer
    sealed trait Response
    final case class Summary(cartId: String, amount: Double) extends Response
    // + some others

    // Each actor needs to support its own "request" type and nothing else
    def apply(shoppingCart: ActorRef[ShoppingCart.Request]): Behavior[Request] =
      Behaviors.setup { context: ActorContext[Request] =>
        // spawn a pseudo-child-actor
        val messageAdapter: ActorRef[ShoppingCart.Response] =
          context.messageAdapter((rsp: ShoppingCart.Response) => WrappedShoppingCartResponse(rsp))

        def handlingCheckouts(checkoutsInProgress: Map[String, ActorRef[Response]]): Behavior[Request] =
          Behaviors.receiveMessage {
            case InspectSummary(cartId, customer) =>
              shoppingCart ! ShoppingCart.GetCurrentCart(cartId, messageAdapter)

              handlingCheckouts(checkoutsInProgress + (cartId -> customer))
            case WrappedShoppingCartResponse(response) =>
              // logic for dealing with response from shopping cart
              response match {
                case  CurrentCart(cartId, items) =>
                  val summary: Summary = Summary(cartId, items.map((_: StoreDomain.Product).price).sum)
                  val mayHaveSuchCart: Try[ActorRef[Response]] = Try(checkoutsInProgress(cartId)) // add checks here

                  mayHaveSuchCart match {
                    case Success(customer) => customer ! summary
                    case Failure(_) =>
                      context.log.error(s"[${context.self.path.name}] No such shopping cart with id: $cartId")
                  }
                  // remove the cart from our map
                  handlingCheckouts(checkoutsInProgress - cartId)
              }
          }

        // return a behavior
        handlingCheckouts(Map())
      }
  }

  def main(args: Array[String]): Unit = {

    import Checkout._

    val rootBehavior: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val shoppingCart: ActorRef[ShoppingCart.Request] = context.spawn(ShoppingCart.dummy(), "dummyShoppingCart")

      val customer: ActorRef[Checkout.Response] = context.spawn(
        Behaviors.receive[Checkout.Response] { (context: ActorContext[Checkout.Response], message: Checkout.Response) =>
          val logger: Logger = context.log

          message match {
            case Summary(_, amount) =>
              logger.info(s"Total to pay: $amount - pay by card below")
              Behaviors.same
          }
        }, "aCustomer")

      val checkout: ActorRef[Checkout.Request] = context.spawn(Checkout(shoppingCart), "checkout")

      // start interaction
      checkout ! InspectSummary("123-abc-456", customer)

      Behaviors.empty
    }

    implicit val system: ActorSystem[NotUsed] = ActorSystem(rootBehavior, "ShoppingPlatform")
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(DispatcherSelector.default())
    system.scheduler.scheduleOnce(1 second, () => system.terminate())
  }
}
