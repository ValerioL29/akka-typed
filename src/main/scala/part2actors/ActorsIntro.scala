package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorsIntro extends App {

  // part1 - actor systems
  val actorSystem = ActorSystem("firstActorSystem") // Name can't have spaces
  println(actorSystem.name)

  // part2 - create actors
  /**
   * Actor principles -
   *
   * 1. Actors are uniquely identifies
   * 2. Messages are asynchronous
   * 3. Each actor may respond differently
   * 4. Actors are REALLY encapsulated
   */
  // word count actor
  class WordCountActor extends Actor {
    // internal data
    var totalWords = 0

    // behavior
    override def receive: Receive = {
      case message: String =>
        println(s"[word counter] I have received: ${message}")
        totalWords = message.split(" ").length
      case msg => println(s"[word counter] I cannot understand ${msg.toString}")
    }
  }

  // part3 - instantiate our actor
  val wordCounter: ActorRef = actorSystem.actorOf(Props[WordCountActor], "wordCounter") // Actor name has to be unique!
  val anotherWordCounter: ActorRef = actorSystem.actorOf(Props[WordCountActor], "anotherWordCounter")

  // part4 - communicate!
  wordCounter ! "I am learning Akka and it's pretty damn cool!" // explanation, that is, the "tell"
  anotherWordCounter ! "A different message"
  // asynchronous!

  object Person { // Best practice to form an Actor with constructor!
    def props(name: String) = Props(new Person(name))
  }
  class Person(name: String) extends Actor {
    override def receive: Receive = {
      case "hi" => println(s"[person] Hi, my name is ${name}")
      case _ =>
    }
  }

  // Using constructor: actorSystem.actorOf(Props(new Person("Bob")))
  val person = actorSystem.actorOf(Person.props("Bob"))
  person ! "hi"


}
