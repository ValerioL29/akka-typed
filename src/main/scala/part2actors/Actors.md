# Actors

## 1. Difference between `Actors` and `Objects`

### With traditional objects:
* we store their state as data
* we call their methods

### With actors:
* we store their state as data
* we send messages to them, asynchronously

### Actors are objects we can't access directly, but only send messages to.

## 2. Some Natural Principles

### Every interaction happens via sending and receiving messages

### Messages are asynchronous by nature
* it takes time for a message to travel
* sending and receiving may not happen at the same time ...
* ... or even in the same context

### I can never ever poke into my friend's brain!