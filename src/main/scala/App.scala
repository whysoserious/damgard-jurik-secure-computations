package org.jz.iohk

import akka.actor.{ Actor, ActorLogging, ActorPath, ActorRef, ActorSelection, ActorSystem, PoisonPill, Props }
import akka.actor.ActorSystem
import akka.contrib.pattern.ReceivePipeline.Inner
import com.typesafe.config.ConfigFactory
import edu.biu.scapi.midLayer.asymmetricCrypto.encryption.{ DJKeyGenParameterSpec, DamgardJurikEnc, ScDamgardJurikEnc }
import edu.biu.scapi.midLayer.ciphertext.AsymmetricCiphertext
import edu.biu.scapi.midLayer.plaintext.{ BigIntegerPlainText, Plaintext }
import java.security.KeyPair
import java.util.concurrent.TimeUnit.MILLISECONDS
// import com.github.nscala_time.time.Imports._
import scala.annotation.tailrec
// import scala.collection.immutable._
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success, Try }
import scala.concurrent.duration._
import akka.event.Logging
import akka.contrib.pattern._
import org.bouncycastle.util.BigIntegers;

import edu.biu.scapi.primitives.dlog.DlogGroup;
import edu.biu.scapi.primitives.dlog.GroupElement;
import edu.biu.scapi.primitives.dlog.openSSL.OpenSSLDlogECF2m;

import java.math.BigInteger
import java.security.SecureRandom
object Data {

  trait Protocol

  case class LogMessage[Msg <: Protocol](msg: Msg, sndr: ActorPath, rcvr: ActorPath)

  class Env extends Actor with ActorLogging with LoggingInterceptor {

    def receive = {
      case LogMessage(msg, sndr, rcvr) => println(s"$sndr -> $rcvr\n\t$msg")
    }

  }

  trait LoggingInterceptor extends ReceivePipeline {

    protected lazy val loggingActor: ActorSelection = context.actorSelection("/user/logging-actor")

    pipelineOuter {
      case msg: Protocol =>
        loggingActor ! LogMessage(msg, sender().path, self.path)
        Inner(msg)
    }

  }

  case object Abort extends Protocol
  case object Invite extends Protocol
  case class Announce(numbers: Seq[EncryptedNumber]) extends Protocol
  case class Result(number: EncryptedNumber) extends Protocol

  class Client(number: () => Int) extends Actor { // TODO () => Int?

    def receive = {
      case Invite => sender() ! EncryptedNumber(number())
      case Abort =>
      case Announce(numbers) =>
      case Result(number) =>
    }

  }

  case class Initialize(clients1: ActorRef, client2: ActorRef) extends Protocol // TODO seq or pair?
  case class EncryptedNumber(n: Int) extends Protocol

  class Broker extends Actor with LoggingInterceptor {

    var clients: Map[ActorRef, Option[EncryptedNumber]] = Map()

    def receive = {
      case Initialize(client1, client2) =>
        clients = Map(client1 -> None, client2 -> None)
        clients.keys.foreach(_ ! Invite)
      case en: EncryptedNumber if clients.contains(sender()) =>
        clients = clients.updated(sender(), Some(en))
        tryAnnounceNumbers(Seq(clients.values.toSeq: _*)) map { numbers =>
          clients.keys.foreach(_ ! Result(EncryptedNumber(666)))
        }
    }

    @tailrec
    private def tryAnnounceNumbers(numbers: Seq[Option[EncryptedNumber]], acc: Seq[EncryptedNumber] = Seq()): Option[Seq[EncryptedNumber]] = {
      numbers match {
        case Nil if acc.size == clients.size =>
          val msg = Announce(acc)
          clients.keys.foreach(_ ! msg)
          Some(acc)
        case Some(en) :: tail =>
          tryAnnounceNumbers(tail, acc :+ en)
        case _ =>
          None
      }
    }

  }

  val config = ConfigFactory.parseString(
    """|iohk.logging.actor-name = "logging-actor"
       |iohk.logging.max-resolve-time = 1 second""".stripMargin)
}

object HelloWorld extends App {

  val senderPair: KeyPair = (new ScDamgardJurikEnc()).generateKey(new DJKeyGenParameterSpec(128, 40))

  val encryptor: DamgardJurikEnc = new ScDamgardJurikEnc()
  val pair: KeyPair = encryptor.generateKey(new DJKeyGenParameterSpec(128, 40))
  encryptor.setKey(pair.getPublic(), pair.getPrivate())
  val plainText: BigIntegerPlainText = new BigIntegerPlainText(new BigInteger("3"))
  val ciphertext: AsymmetricCiphertext = encryptor.encrypt(plainText)
  val decryptedText: BigIntegerPlainText = encryptor.decrypt(ciphertext).asInstanceOf[BigIntegerPlainText]

  println(s">>> PLAIN:     $plainText")
  println(s">>> CIPHER:    $ciphertext")
  println(s">>> DECRYPTED: $decryptedText")


}

// object HelloWorld extends App {

//   // sender
//   val encryptor: DamgardJurikEnc = new ScDamgardJurikEnc()
//   val senderPair: KeyPair = encryptor.generateKey(new DJKeyGenParameterSpec(128, 40))

//   val receiverPair: KeyPair = encryptor.generateKey(new DJKeyGenParameterSpec(128, 40))
//   encryptor.setKey(receiverPair.getPublic(), senderPair.getPrivate())
//   val plainText: BigIntegerPlainText = new BigIntegerPlainText(new BigInteger("3"))
//   val ciphertext: AsymmetricCiphertext = encryptor.encrypt(plainText)

//   // receiver

//   val encryptor2: DamgardJurikEnc = new ScDamgardJurikEnc()
//   encryptor2.setKey(senderPair.getPublic(), receiverPair.getPrivate())
//   val decryptedText: BigIntegerPlainText = encryptor2.decrypt(ciphertext).asInstanceOf[BigIntegerPlainText]
//   val element: BigInteger = decryptedText.getX()

//   val x = new BigInteger("3")
//   println(s">>> BI: $plainText")
//   println(s">>> DT: $decryptedText")
//   println(s">>> EL: $element")

// }

// object HelloWorld extends App {

//   println(">>>>>>>>" + System.getProperty("java.library.path"))

//   // initiate a discrete log group
//   // (in this case the OpenSSL implementation of the elliptic curve group K-233)
//   val dlog: DlogGroup = new OpenSSLDlogECF2m("K-233")
//   val random: SecureRandom = new SecureRandom()

//   // get the group generator and order
//   val g: GroupElement = dlog.getGenerator()
//   val q: BigInteger = dlog.getOrder()
//   val qMinusOne: BigInteger = q.subtract(BigInteger.ONE)

//   // create a random exponent r
//   val r: BigInteger = BigIntegers.createRandomInRange(BigInteger.ZERO, qMinusOne, random)

//   // exponentiate g in r to receive a new group element
//   val g1: GroupElement = dlog.exponentiate(g, r)
//   // create a random group element
//   val h: GroupElement = dlog.createRandomElement()
//   // multiply elements
//   val gMult: GroupElement = dlog.multiplyGroupElements(g1, h)

//   println(">>>> " + gMult)

// }

// object HelloWorld extends App {

//   import Data._

//   println("START")
//   val system = ActorSystem("dj-actor-system", ConfigFactory.load(config))
//   val env = system.actorOf(Props[Env], "logging-actor")
//   val broker = system.actorOf(Props[Broker with LoggingInterceptor], "broker")
//   val a1 = system.actorOf(Props(new Client(() => 7) with LoggingInterceptor), "client-1")
//   val a2 = system.actorOf(Props(new Client(() => 8) with LoggingInterceptor), "client-2")
//   broker ! Initialize(a1, a2)
//   Thread.sleep(2000)
//   a1 ! PoisonPill
//   a2 ! PoisonPill
//   broker ! PoisonPill
//   env ! PoisonPill
//   system.terminate()
//   Await.result(system.whenTerminated, 5.seconds)

// }
