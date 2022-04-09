# Child Actors Recap

### 1. Actors can create other actors
```scala
context.actorOf(Props[MyActor], "child")
```

### 2. `Top-level` supervisors(guardians)
#### - System level - `/system`
#### - User level - `/user`
#### - The root guardian, for the whole actor system - `/`

### 3. Actor paths
Example:
```
/user/parent/child
```

### 4. Actor selections
```scala
system.actorSelection("/user/parent/child")
```
which works with `context` as well!

### 5. Actor Encapsulation dangers!
Never pass mutable actor state, or the `this` reference, to child actors!
```scala
object CreditCard {
    case class AttachToAccount(bankAccount: NaiveBankAccount) // !!
    // This is called a 'closing over' problem which breaks all the Actor model encapsulation
    case object CheckStatus
  }
  class CreditCard extends Actor {
    import CreditCard._

    override def receive: Receive = {
      case AttachToAccount(account) => context.become(attachTo(account))
    }

    def attachTo(account: NaiveBankAccount): Receive = {
      case CheckStatus =>
        println(s"${self.path} Your message has been processed.")
        // benign
        account.withDraw(1) // because I can
        // WRONG! This is calling a method directly from an actor
        // which will put us in the concurrency issues
    }
  }
```