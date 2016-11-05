package org.jz.iohk

import java.math.BigInteger

import scala.concurrent.duration._

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}

// Basic tests for a protocol timeout
class AbortSpec() extends TestKit(ActorSystem("dj-test-system-1", ConfigFactory.parseString("{akka.loglevel = ERROR}")))
    with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  import Broker._
  import Client._

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val broker: ActorRef = system.actorOf(Props(new Broker(protocolTimeout = 0.millis)), "broker")
  val client: ActorRef = system.actorOf(Props(new Client(new BigInteger("1"), broker)), "client")

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

  "A client" when {

    "received an Abort message from a broker" must {

      "stop" in {
        val probe = TestProbe()
        probe watch client
        probe.expectTerminated(client, 50.millis)
      }

    }

  }

}
