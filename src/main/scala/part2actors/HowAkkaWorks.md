# How `Akka` Works

> So what is the magic ***underlying mechanism*** of `Akka actors`?

## 1. Overview

`Akka` has a ***thread pool*** that it shares with `actors`.

![Thread Pool And Actors](https://alipicbed.oss-cn-beijing.aliyuncs.com/img/ThreadPoolAndActors.png)

It creates a number of threads which can handle massive actors!

![Threads And Actors](https://alipicbed.oss-cn-beijing.aliyuncs.com/img/ThreadsAndActors.png)

`Akka` schedules actors for executions.

## 2. Communication

#### Sending a message

* Message is ***enqueued*** in the actor's "mailbox"
* It is completely ***thread-safe***!

#### Processing a message

* A thread is scheduled to run this actor
* Messages are extracted from the "mailbox", ***in order***
* The thread invokes the ***handler*** on each message
* At some point the actor is ***unscheduled*** and so something else

![Communication](https://alipicbed.oss-cn-beijing.aliyuncs.com/img/Communication.png)

## 3. Guarantees

#### Only one thread operates on an actor at any time

* Actors are effectively ***single-threaded***
* ***No locks needed!***

#### Message delivery guarantees

* ***At most once delivery***
* For any sender-receiver pair, the message order is ***maintained***

#### If Alice sends Bob message A followed by B

* Bob will ***never*** receive duplicates of A or B
* Bob will ***always*** receive A before B (possibly with some others in between)