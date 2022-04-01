# Actor Basics

### Every actor type derives from:
```scala
trait Actor {
  def receive: Receive
}
```

### The receive type is an alias:
```scala
type Receive = PartialFunction[Any, Unit]
```

### Actors need infrastructure:
```scala
import akka.actor.ActorSystem
val system = ActorSystem("myActorSystem")
```

### Creating actors is not done in the traditional way:
```scala
import akka.actor.{ActorSystem,Props}
val actor = ActorSystem("systemName").actorOf(Props[actorType], "actorUniqueName")
```