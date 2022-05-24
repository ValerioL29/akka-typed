package part2actors

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import scala.collection.mutable.{Map => MutableMap}

/**
 * BE CAREFUL!!!
 * - NEVER PASS MUTABLE STATE TO OTHER ACTORS!
 * - NEVER PASS THE CONTEXT REFERENCE TO OTHER ACTORS!
 * - SAME FOR Futures!
 */

object BreakingActorEncapsulation {

  // naive bank account
  trait AccountCommand
  case class Deposit(cardId: String, amount: Double) extends AccountCommand
  case class Withdraw(cardId: String, amount: Double) extends AccountCommand
  case class CreateCreditCard(cardId: String) extends AccountCommand
  case object CheckCardStatuses extends AccountCommand

  trait CreditCardCommand
  case class AttachToAccount(
    balances: MutableMap[String, Double],
    cards: MutableMap[String, ActorRef[CreditCardCommand]]) extends CreditCardCommand
  case object CheckStatus extends CreditCardCommand

  object NaiveBankAccount {
    def apply(): Behavior[AccountCommand] = Behaviors.setup { context: ActorContext[AccountCommand] =>
      val accountBalances: MutableMap[String, Double] = MutableMap()
      val cardMap: MutableMap[String, ActorRef[CreditCardCommand]] = MutableMap()
      Behaviors.receiveMessage {
        case CreateCreditCard(cardId) =>
          context.log.info(s"Creating card $cardId")
          // create a CreditCard child
          val creditCardRef: ActorRef[CreditCardCommand] = context.spawn(CreditCard(cardId), cardId)
          // BUG ISSUE: give a referral bonus
          // accountBalances += cardId -> 10 - ILLEGAL
          // send an AttachToAccount message to the child
          creditCardRef ! AttachToAccount(accountBalances, cardMap)
          // change behavior
          Behaviors.same
        case Deposit(cardId, amount) =>
          val oldBalance: Double = accountBalances.getOrElse(cardId, 0)
          context.log.info(s"Depositing $amount via card $cardId, balance on card: ${oldBalance + amount}")
          accountBalances += cardId -> (oldBalance + amount)
          Behaviors.same
        case Withdraw(cardId, amount) =>
          val oldBalance: Double = accountBalances.getOrElse(cardId, 0)
          if(oldBalance < amount) {
            context.log.info(s"Attempted withdrawal of $amount via card $cardId: insufficient funds!")
            Behaviors.same
          }else{
            context.log.info(s"Withdrawing $amount via card $cardId, balance on card: ${oldBalance - amount}")
            accountBalances += cardId -> (oldBalance - amount)
            Behaviors.same
          }
        case CheckCardStatuses =>
          context.log.info("Checking all card statuses.")
          cardMap.values.foreach((cardRef: ActorRef[CreditCardCommand]) => cardRef ! CheckStatus)
          Behaviors.same
      }
    }
  }

  object CreditCard {
    def apply(cardId: String): Behavior[CreditCardCommand] = Behaviors.receive { (context: ActorContext[CreditCardCommand], message: CreditCardCommand) =>
      message match {
        case AttachToAccount(balances, cards) =>
          context.log.info(s"[$cardId] Attaching to bank account")
          balances += cardId -> 0
          cards += cardId -> context.self
          Behaviors.same
        case CheckStatus =>
          context.log.info(s"[$cardId] All things green.")
          Behaviors.same
      }
    }
  }

  def demoNaiveBankAccount(): Unit = {
    val userGuardian: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
      val bankAccount: ActorRef[AccountCommand] = context.spawn(NaiveBankAccount(), "bankAccount")

      bankAccount ! CreateCreditCard("gold")
      bankAccount ! CreateCreditCard("premium")
      bankAccount ! Deposit("gold", 1000)
      bankAccount ! CheckCardStatuses

      Behaviors.empty
    }

    val system: ActorSystem[NotUsed] = ActorSystem(userGuardian, "DemoNaiveBankAccount")

    Thread.sleep(1000)
    system.terminate()
  }

  def main(args: Array[String]): Unit = {
    demoNaiveBankAccount()
  }
}
