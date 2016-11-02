package org.jz.iohk

import akka.actor.ActorRef
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import java.math.BigInteger
import scala.concurrent.{ Await, Future }
import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{ActorSystem, Props}
import akka.pattern._

object HelloWorld extends App {

  val config = ConfigFactory.load()
  val commonConfig = config.getConfig("common")

  val system1 = ActorSystem("caroll-system", config.getConfig("caroll").withFallback(commonConfig))
  val env = system1.actorOf(Props[Env], "logging-actor")

  trait RemoteLoggingInterceptor {
    this: LoggingInterceptor =>

    override def loggingActor: Option[ActorRef] = Some(env)

  }

  val caroll = system1.actorOf(Props(new Broker() with RemoteLoggingInterceptor), "caroll")

  val system2 = ActorSystem("alice-system", config.getConfig("alice").withFallback(commonConfig))
  val alice = system2.actorOf(Props(new Client(new BigInteger("7"), caroll) with RemoteLoggingInterceptor), "alice")

  val system3 = ActorSystem("bob-system", config.getConfig("bob").withFallback(commonConfig))
  val bob = system3.actorOf(Props(new Client(new BigInteger("8"), caroll) with RemoteLoggingInterceptor), "bob")

  // graceful shutdown

  val timeout: FiniteDuration = 5.seconds
  implicit val askTimeout: Timeout = timeout
  import scala.concurrent.ExecutionContext.Implicits.global
  import Client.GetProofResult

  for {
    _ <- (alice ? GetProofResult)
    _ <- (bob ? GetProofResult)
    _ <- Future.sequence(Seq(alice, bob, caroll, env).map { gracefulStop(_, timeout) })
  } {
    system1.terminate()
    system2.terminate()
    system3.terminate()
    Await.result(system1.whenTerminated, timeout)
    Await.result(system2.whenTerminated, timeout)
    Await.result(system3.whenTerminated, timeout)
  }

}
