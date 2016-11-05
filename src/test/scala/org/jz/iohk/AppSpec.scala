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
import akka.testkit.{ TestActors, TestKit, ImplicitSender, TestProbe }
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.duration._

class AppSpec() extends TestKit(ActorSystem("dj-test-system-1", ConfigFactory.parseString("{akka.loglevel = ERROR}")))
    with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  import Client._
  import Broker._

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val broker = system.actorOf(Props(new Broker(protocolTimeout = 0.millis)), "broker")
  val client = system.actorOf(Props(new Client(new BigInteger("1"), broker)), "client")

  "A broker" when {

    "a protocol timeout occured" must {

      "send an Abort message to registered clients" in {
        broker ! Register
        expectMsg(Abort)
      }

      "respond with an Abort message to any message it receives" in {
        broker ! ""
        broker ! 42
        expectMsg(Abort)
        expectMsg(Abort)
      }

    }

  }

  "A client" must {

    "stop when it received an Abort message from a broker" in {
      val probe = TestProbe()
      probe watch client
      probe.expectTerminated(client, 50.millis)
    }

  }

}
