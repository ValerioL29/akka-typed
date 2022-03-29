# Thread Model Limtations

## 1. `OOP` is not encapsulated
* race conditions

## 2. Locks to the rescue?
* `deadlocks`, `livelocks`, headaches
* a massive pain in distributed environments

## 3. Delegating tasks
* hard, error-prone
* never feels "first-class" although often needed
* should never be done in a blocking fashion

## 4. Dealing with error
* a monumental task in even small systems