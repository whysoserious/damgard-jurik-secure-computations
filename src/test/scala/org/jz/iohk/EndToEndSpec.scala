package org.jz.iohk

import akka.actor.ActorRef
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import java.math.BigInteger
import org.scalatest.{ AsyncWordSpecLike, PropSpecLike }
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
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec, Assertion}
import org.scalacheck._



// TODO organize traits
class EndToEndSpec() extends TestKit(ActorSystem("dj-test-system-2", ConfigFactory.parseString("{akka.loglevel = ERROR}")))
    with ImplicitSender with WordSpecLike with PropertyChecks with GeneratorDrivenPropertyChecks with Matchers with BeforeAndAfterAll {

  import Client._
  import Broker._
  import Verifier._

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import scala.concurrent.ExecutionContext.Implicits.global
  val timeout: FiniteDuration = 1.second
  implicit val askTimeout: Timeout = timeout

  val bigIntegerGen = Gen.posNum[Long].map(BigInteger.valueOf)

  "A broker" should {

    "generate valid proof for any pair of input numbers" in {
      forAll(bigIntegerGen, bigIntegerGen) {(n1: BigInteger, n2: BigInteger) =>
        val broker: ActorRef = system.actorOf(Props(new Broker()))
        val client1: ActorRef = system.actorOf(Props(new Client(n1, broker)))
        val client2: ActorRef = system.actorOf(Props(new Client(n2, broker)))
        val proofs: Future[(ProofResult, ProofResult)] = for {
          proof1 <- (client1 ? GetProofResult).mapTo[ProofResult]
          proof2 <- (client2 ? GetProofResult).mapTo[ProofResult]
          _ <- Future.sequence(Seq(broker, client1, client2).map { gracefulStop(_, timeout) })
        } yield {
          proof1 -> proof2
        }
        Await.result(proofs, timeout) shouldBe ProofResult(true) -> ProofResult(true)
      }

    }

  }

}
