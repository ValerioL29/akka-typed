package part3testing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpecLike

class EssentialTestingSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike{

}

object EssentialTestingSpec {
  // code under test
  trait SimpleProtocol
  case class SimpleMessage(message: String, sender: ActorRef[SimpleProtocol]) extends SimpleProtocol
  case class SimpleReply(content: String) extends SimpleProtocol
}