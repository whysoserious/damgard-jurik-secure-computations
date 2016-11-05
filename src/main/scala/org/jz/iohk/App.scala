package org.jz.iohk

import akka.actor.Terminated
import java.math.BigInteger

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

// Example program showing how to deploy clients, broker and logger on 3 ActorSystems
object HelloWorld extends App {

  // configuration is read from src/main/resources/application.conf
  val config = ConfigFactory.load()
  val commonConfig = config.getConfig("common")

  // initializing  logger on system1
  val system1 = ActorSystem("caroll-system", config.getConfig("caroll").withFallback(commonConfig))
  val env = system1.actorOf(Props[Env], "logging-actor")

  // we need to mix in this trait to actors so they can obtain a reference to a logger actor
  trait RemoteLoggingInterceptor {
    this: LoggingInterceptor =>

    override def loggingActor: Option[ActorRef] = Some(env)

  }

  // initializing broker on system1
  val caroll = system1.actorOf(Props(new Broker() with RemoteLoggingInterceptor), "caroll")

  // initializing first client on system2
  val system2 = ActorSystem("alice-system", config.getConfig("alice").withFallback(commonConfig))
  val alice = system2.actorOf(Props(new Client(BigInteger.valueOf(7), caroll) with RemoteLoggingInterceptor), "alice")

  // initializing second client on system3
  val system3 = ActorSystem("bob-system", config.getConfig("bob").withFallback(commonConfig))
  val bob = system3.actorOf(Props(new Client(BigInteger.valueOf(8), caroll) with RemoteLoggingInterceptor), "bob")

  // graceful shutdown!
  val timeout: FiniteDuration = 5.seconds
  implicit val askTimeout: Timeout = timeout
  import Client.GetProofResult
  import scala.concurrent.ExecutionContext.Implicits.global
  val terminated: Future[Seq[Terminated]] = for {
    _ <- (alice ? GetProofResult)
    _ <- (bob ? GetProofResult)
    _ <- Future.sequence(Seq(alice, bob, caroll, env).map { gracefulStop(_, timeout) })
    _ <- system1.terminate()
    _ <- system2.terminate()
    _ <- system3.terminate()
    terminated1 <- system1.whenTerminated
    terminated2 <- system2.whenTerminated
    terminated3 <- system3.whenTerminated
  } yield {
    Seq(terminated1, terminated2, terminated3)
  }
  Await.result(terminated, timeout)
}
