package part2actors

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

object TypedActorsIntro {

  // part 1: behavior
  val simpleActorBehavior: Behavior[String] = Behaviors.receiveMessage { (message: String) => // Behavior[String]
    // do something with the message
    println(s"[simple actor] I have received: $message")

    // new behavior for the NEXT message
    Behaviors.same
  }

  def demoSimpleActor(): Unit = {
    // part 2: instantiate
    val actorSystem: ActorSystem[String] = ActorSystem(SimpleActor_V2(), "FirstActorSystem")

    // part 3: communicate!
    actorSystem ! "I am learning Akka" // asynchronously send a message
    // ! is the "tell" method
    // actorSystem ! 43 This is banned! Because we have specified the Behavior type is STRING!!!

    // part 4: gracefully shut down
    Thread.sleep(1000)
    actorSystem.terminate()
  }

  // "refactor" behavior definition
  // Similar to our OOP pattern
  object SimpleActor {
    def apply(): Behavior[String] = Behaviors.receiveMessage { (message: String) => // Behavior[String]
      // do something with the message
      println(s"[simple actor] I have received: $message")

      // new behavior for the NEXT message
      Behaviors.same
    }
  }

  object SimpleActor_V2 {
    def apply(): Behavior[String] = Behaviors.receive{ (context: ActorContext[String], message: String) =>
      // context is the ActorContext
      // context is a data structure (ActorContext) with access to a variety of APIs
      // simple example: logging
      context.log.info(s"[simple actor] I have received: $message")

      // new behavior for the NEXT message
      Behaviors.same
    }
  }

  object SimpleActor_V3 {
    def apply(): Behavior[String] = Behaviors.setup{ context: ActorContext[String] =>
      // actor "private" data and methods, behaviors etc
      // YOUR CODE HERE

      // Returns the behavior when the actor received the first message
      Behaviors.receiveMessage { message: String =>
        context.log.info(s"[simple actor] I have received: $message")
        Behaviors.same
      }
    }
  }

  def main(args: Array[String]): Unit = {
    demoSimpleActor()
  }
}
