package org.jz.iohk

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import akka.testkit.{ TestActors, TestKit, ImplicitSender }
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

class AppSpec() extends TestKit(ActorSystem("app-spec")) with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  import Data._

  // TODO test normal scenario
  // TODO test timeout
  // TODO test > 1 Initialize

  "A Lorem ipsum" should {

    "dolor amet" in {

      val broker = system.actorOf(Props[Broker with LoggingInterceptor], "broker")
      val a1 = system.actorOf(Props(new Client(() => 7) with LoggingInterceptor), "client-1")
      val a2 = system.actorOf(Props(new Client(() => 8) with LoggingInterceptor), "client-2")

    }
  }


}
