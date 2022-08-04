package extensions

import akka.NotUsed
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, DispatcherSelector}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.slf4j.Logger

import java.util.UUID
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random

/**
 * IoT Domain Definition
 */
object IoTDomain {
  case class SensorReading(id: String, value: Double)

  // sensor section
  sealed trait SensorCommand
  final case object SensorHeartbeat extends SensorCommand
  final case class ChangeDataAggregator(agg: Option[ActorRef[SensorReading]]) extends SensorCommand
}

/**
 * Sensor data inspector
 */
object DataAggregator {

  import IoTDomain._

  // Unique identifier at the AKKA level => used for identifying a specific service
  val serviceKey: ServiceKey[SensorReading] = ServiceKey[SensorReading]("dataAggregator")

  def apply(): Behavior[SensorReading] = active(Map.empty[String, Double])

  def active(latestReadings: Map[String, Double]): Behavior[SensorReading] =
    Behaviors.receive[SensorReading] { (context: ActorContext[SensorReading], reading: SensorReading) =>
      val logger: Logger = context.log

      val SensorReading(id, value) = reading
      val updatedReadings: Map[String, Double] = latestReadings + (id -> value)

      // "display" part
      logger.info(s"[${context.self.path.name}] Latest readings: $updatedReadings")
      active(updatedReadings)
    }
}

/**
 * Sensor actor definition
 */
object Sensor {

  import IoTDomain._

  def apply(id: String): Behavior[SensorCommand] = Behaviors.setup { context: ActorContext[SensorCommand] =>
    // use a message adapter to turn a receptionist listing into a SensorCommand
    val receptionistSubscriber: ActorRef[Receptionist.Listing] = context.messageAdapter {
      case DataAggregator.serviceKey.Listing(set: Set[ActorRef[SensorReading]]) => ChangeDataAggregator(set.headOption)
    }

    // first subscribe to the receptionist using the service key
    context.system.receptionist ! Receptionist.Subscribe(DataAggregator.serviceKey, receptionistSubscriber)

    active(id, None)
  }

  def active(id: String, aggregator: Option[ActorRef[SensorReading]]): Behavior[SensorCommand] =
    Behaviors.receiveMessage[SensorCommand] {
      case SensorHeartbeat =>
        aggregator match {
          case Some(agg) => agg ! SensorReading(id, Random.nextDouble() * 40)
          case None => ()
        }
        Behaviors.same
      case ChangeDataAggregator(newAgg) =>
        active(id, newAgg)
    }

  def controller(): Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
    implicit val ec: ExecutionContextExecutor = context.executionContext

    val sensors: IndexedSeq[ActorRef[SensorCommand]] =
      (1 to 10).map((i: Int) => context.spawn(Sensor(UUID.randomUUID().toString), s"Sensor-$i"))

    // send heartbeats every second
    context.system.scheduler.scheduleAtFixedRate(1 second, 1 second) { () =>
      sensors.foreach((_: ActorRef[SensorCommand]) ! SensorHeartbeat)
    }

    Behaviors.empty[NotUsed]
  }
}

/**
 * IOT Scenario:
 *
 * Sensors - actors
 *
 * Sensor controller
 *
 * (disconnect)
 *
 * Data Aggregator
 *
 * Guardian
 *
 * ---- Solution: Akka Actor Discovery, "Receptionist" ----
 */
object ActorDiscovery {

  import IoTDomain._

  val rootBehavior: Behavior[NotUsed] = Behaviors.setup { context: ActorContext[NotUsed] =>
    // controller for the sensors
    context.spawn(Sensor.controller(), "controller")

    val dataAgg1: ActorRef[SensorReading] = context.spawn(DataAggregator(), "dataAgg_1")
    // "publish" - dataAgg1 is available by associating it to a key, that is, the SERVICE KEY
    context.system.receptionist ! Receptionist.register[SensorReading](DataAggregator.serviceKey, dataAgg1)

    // change data aggregator after 10s
    Thread.sleep(10000)
    context.log.info(s"[guardian] Changing data aggregator.")
    context.system.receptionist ! Receptionist.deregister[SensorReading](DataAggregator.serviceKey, dataAgg1)
    val dataAgg2: ActorRef[SensorReading] = context.spawn(DataAggregator(), "dataAgg_2")
    context.system.receptionist ! Receptionist.register[SensorReading](DataAggregator.serviceKey, dataAgg2)

    Behaviors.empty
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[NotUsed] = ActorSystem(rootBehavior, "IoTSystem")
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(DispatcherSelector.default())

    system.scheduler.scheduleOnce(20 seconds, () => system.terminate())
  }
}
