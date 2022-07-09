package part4infra

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.Logger

object AkkaConfiguration {

  object SimpleLoggingActor {
    def apply(): Behavior[String] = Behaviors.receive { (context: ActorContext[String], message: String) =>
      val logger: Logger = context.log

      logger.info(message)
      Behaviors.same
    }
  }

  // 1 - inline configuration
  def demoInlineConfig(): Unit = {
    // HOCON((Human-Optimized Object Configuration Notation) - superset of JSON, managed by Lightbend
    val configString: String =
      """
        |akka {
        |  loglevel = "DEBUG"
        |}
        |""".stripMargin

    val config: Config = ConfigFactory.parseString(configString)
    val system: ActorSystem[String] = ActorSystem(SimpleLoggingActor(), "InlineConfigDemo", ConfigFactory.load(config))

    system ! "A message to remember"

    Thread.sleep(1000)
    system.terminate()
  }

  // 2 - config file
  def demoConfigFile(): Unit = {
    val specialConfig: Config = ConfigFactory.load().getConfig("mySpecialConfig1")
    val system: ActorSystem[String] = ActorSystem(SimpleLoggingActor(), "FileConfigDemo", ConfigFactory.load(specialConfig))

    system ! "A message to remember"

    Thread.sleep(1000)
    system.terminate()
  }

  // 3 - a different config in another file
  def demoSeparateConfigFile(): Unit = {
    val separateConfig: Config = ConfigFactory.load("secretDir/secretConfiguration.conf")
    println(separateConfig.getString("akka.loglevel"))
  }

  // 4 - different file formats (JSON, properties)
  def demoOtherFileFormats(): Unit = {
    val jsonConfig: Config = ConfigFactory.load("json/jsonConfiguration.json")
    println(s"json config with custom property: ${jsonConfig.getString("aJsonProperty")}")
    println(s"json config with custom property: ${jsonConfig.getString("akka.loglevel")}")

    // properties format
    val propsConfig: Config = ConfigFactory.load("properties/propsConfiguration.properties")
    println(s"properties config with custom property: ${propsConfig.getString("mySimpleProperty")}")
    println(s"properties config with custom property: ${propsConfig.getString("akka.loglevel")}")
  }

  def main(args: Array[String]): Unit = {
    demoOtherFileFormats()
  }
}
