package org.jz.iohk

import akka.actor.{ Actor, ActorLogging, ActorPath, ActorRef, ActorSelection, ActorSystem, PoisonPill, Props }
import akka.actor.ActorSystem
import akka.contrib.pattern.ReceivePipeline.Inner
import com.typesafe.config.ConfigFactory
import edu.biu.scapi.midLayer.asymmetricCrypto.encryption.{ DJKeyGenParameterSpec, DamgardJurikEnc, ScDamgardJurikEnc, ScElGamalOnGroupElement }
import edu.biu.scapi.midLayer.asymmetricCrypto.keys.ScDamgardJurikPublicKey
import edu.biu.scapi.midLayer.ciphertext.AsymmetricCiphertext
import edu.biu.scapi.midLayer.plaintext.{ BigIntegerPlainText, GroupElementPlaintext, Plaintext }
import edu.biu.scapi.primitives.dlog.miracl.MiraclDlogECFp
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
import java.security.PublicKey
import java.security.Key
object Data {

  trait ActorMessage

  case class LogMessage[Msg <: ActorMessage](msg: Msg, sndr: ActorPath, rcvr: ActorPath)

  class Env extends Actor with ActorLogging with LoggingInterceptor {

    def receive = {
      case LogMessage(msg, sndr, rcvr) => println(s"$sndr -> $rcvr\n\t$msg")
    }

  }

  trait LoggingInterceptor extends ReceivePipeline {

    protected lazy val loggingActor: ActorSelection = context.actorSelection("/user/logging-actor")

    pipelineOuter {
      case msg: ActorMessage =>
        loggingActor ! LogMessage(msg, sender().path, self.path)
        Inner(msg)
    }

  }

  trait UserMessage extends ActorMessage
  case object Register extends UserMessage
  case class EncryptedNumber(n: AsymmetricCiphertext) extends ActorMessage

  trait BrokerMessage extends ActorMessage
  case class Invite(publicKey: ScDamgardJurikPublicKey) extends BrokerMessage
  case class EncryptedNumbers(n1: AsymmetricCiphertext, n2: AsymmetricCiphertext) extends BrokerMessage
  case class EncryptedResult(n: AsymmetricCiphertext) extends BrokerMessage
  case object Abort extends BrokerMessage

  class Client(number: BigInteger, brokerPath: ActorPath, resolveTimeout: FiniteDuration) extends Actor {

    val broker: ActorSelection = context.actorSelection(brokerPath)

    var cA: Option[AsymmetricCiphertext] = None
    var cB: Option[AsymmetricCiphertext] = None
    var cC: Option[AsymmetricCiphertext] = None

    implicit val ec = context.dispatcher

    override def preStart = {
      broker ! Register
    }

    override def receive = {
      case Invite(publicKey: ScDamgardJurikPublicKey) if cA.isEmpty =>
        val cipherText: AsymmetricCiphertext = encryptNumber(number, publicKey)
        cA = Some(cipherText)
        broker ! EncryptedNumber(cipherText)
      case EncryptedNumbers(n1: AsymmetricCiphertext, n2: AsymmetricCiphertext) if cB.isEmpty && Some(n1).equals(cA) =>
        cB = Some(n2)
      case EncryptedNumbers(n1: AsymmetricCiphertext, n2: AsymmetricCiphertext) if cB.isEmpty && Some(n2).equals(cA) =>
        cB = Some(n1)
      case EncryptedResult(n: AsymmetricCiphertext) if cC.isEmpty =>
        cC = Some(n)
      case Abort =>
        self ! PoisonPill
      case x =>
        println(">>> " + x)
        unhandled(x)
    }

    def encryptNumber(n: BigInteger, publicKey: ScDamgardJurikPublicKey): AsymmetricCiphertext = {
      val encryptor: DamgardJurikEnc = new ScDamgardJurikEnc()
      encryptor.setKey(publicKey)
      encryptor.encrypt(new BigIntegerPlainText(n))
    }

  }

  case object Broker {

    case class ClientData(ref: ActorRef, number: Option[AsymmetricCiphertext])

    private case object MultiplyNumbers extends BrokerMessage

  }

  class Broker(modulusLength: Int = 128, certainty: Int = 40) extends Actor with LoggingInterceptor {

    import Broker._

    val encryptor: DamgardJurikEnc = new ScDamgardJurikEnc()
    val keyPair: KeyPair = encryptor.generateKey(new DJKeyGenParameterSpec(modulusLength, certainty))

    var client1: Option[ClientData] = None
    var client2: Option[ClientData] = None
    var encryptedProduct: Option[AsymmetricCiphertext] = None

    override def receive = {
      case Register =>
        val publicKey: ScDamgardJurikPublicKey = keyPair.getPublic().asInstanceOf[ScDamgardJurikPublicKey]
        sender() ! Invite(publicKey)
        val clientData = Some(ClientData(sender(), None))
                             (client1, client2) match { // TODO fix formatting
          case (None, _) => client1 = clientData
          case (Some(_), None) => client2 = clientData
          case _ =>
        }
      case EncryptedNumber(ciphertext: AsymmetricCiphertext) =>
        (client1, client2) match {
          case (Some(clientData @ ClientData(sndr, None)), _) if sndr.equals(sender()) =>
            client1 = Some(clientData.copy(number = Some(ciphertext)))
          case (_, Some(clientData @ ClientData(sndr, None))) if sndr.equals(sender()) =>
            client2 = Some(clientData.copy(number = Some(ciphertext)))
            self ! MultiplyNumbers
        }
      case MultiplyNumbers =>
        (client1, client2) match {
          case (Some(ClientData(sndr1, Some(ciphertext1))), Some(ClientData(sndr2, Some(ciphertext2)))) if encryptedProduct.isEmpty =>
            encryptor.setKey(keyPair.getPublic(), keyPair.getPrivate())
            val n1: BigInteger = encryptor.decrypt(ciphertext1).asInstanceOf[BigIntegerPlainText].getX
            val n2: BigInteger = encryptor.decrypt(ciphertext2).asInstanceOf[BigIntegerPlainText].getX
            // encryptor.setKey(keyPair.getPublic() // TODO do we need that?
            val plaintext: BigIntegerPlainText = new BigIntegerPlainText(n1 multiply n2)
            val ciphertext = encryptor.encrypt(plaintext)
            encryptedProduct = Some(ciphertext)
            sndr1 ! EncryptedResult(ciphertext)
            sndr2 ! EncryptedResult(ciphertext)
          case _ =>
        }

      case x =>
        unhandled(x)
    }

  }

  val config = ConfigFactory.parseString(
    """|iohk.logging.actor-name = "logging-actor"
       |iohk.logging.max-resolve-time = 1 second""".stripMargin)
}

object HelloWorld extends App {

  import Data._

  println("START")
  val system = ActorSystem("dj-actor-system", ConfigFactory.load(config))
  val env = system.actorOf(Props[Env], "logging-actor")
  val broker = system.actorOf(Props(new Broker() with LoggingInterceptor), "caroll")
  val a1 = system.actorOf(Props(new Client(new BigInteger("7"), broker.path, 1.second) with LoggingInterceptor), "alice")
  val a2 = system.actorOf(Props(new Client(new BigInteger("8"), broker.path, 1.second) with LoggingInterceptor), "bob")
  Thread.sleep(2000)
  a1 ! PoisonPill
  a2 ! PoisonPill
  broker ! PoisonPill
  env ! PoisonPill
  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)

}

// OBJECT HelloWorld extends App {
//   //TODO keys in plain text



//   val encryptor: DamgardJurikEnc = new ScDamgardJurikEnc()
//   val senderPair: KeyPair = encryptor.generateKey(new DJKeyGenParameterSpec(128, 40))
//   val receiverPair: KeyPair = encryptor.generateKey(new DJKeyGenParameterSpec(128, 40))

//   //sender
//   println(">>> " + receiverPair.getPublic().asInstanceOf[ScDamgardJurikPublicKey])
//   val n = new BigInteger("3")
//   encryptor.setKey(receiverPair.getPublic())
//   val plainText: BigIntegerPlainText = new BigIntegerPlainText(n)
//   val ciphertext: AsymmetricCiphertext = encryptor.encrypt(plainText)

//   //receiver
//   encryptor.setKey(receiverPair.getPublic(), receiverPair.getPrivate())
//   val decryptedText: BigIntegerPlainText = encryptor.decrypt(ciphertext).asInstanceOf[BigIntegerPlainText]

//   println(s">>> PLAIN:     $plainText")
//   println(s">>> CIPHER:    $ciphertext")
//   println(s">>> DECRYPTED: $decryptedText")


// }


// object HelloWorld extends App {

//   println(System.getProperty("java.library.path"))

//   val dlog: DlogGroup = new MiraclDlogECFp();
//   val elGamal = new ScElGamalOnGroupElement(dlog)

//   val senderPair: KeyPair = elGamal.generateKey()
//   val receiverPair: KeyPair = elGamal.generateKey()

//   //sender
//   val n = new BigInteger("3")
//   elGamal.setKey(receiverPair.getPublic(), senderPair.getPrivate())
//   val plainText: Plaintext = new GroupElementPlaintext(dlog.createRandomElement())
//   val ciphertext: AsymmetricCiphertext = elGamal.encrypt(plainText)

//   //receiver
//   elGamal.setKey(senderPair.getPublic(), receiverPair.getPrivate())
//   val decryptedText: GroupElement = elGamal.decrypt(ciphertext).asInstanceOf[GroupElementPlaintext].getElement

//   println(s">>> PLAIN:     $plainText")
//   println(s">>> CIPHER:    $ciphertext")
//   println(s">>> DECRYPTED: $decryptedText")


// }

// object HelloWorld extends App {
//   //TODO keys in plain text



//   val encryptor: DamgardJurikEnc = new ScDamgardJurikEnc()
//   val senderPair: KeyPair = encryptor.generateKey(new DJKeyGenParameterSpec(128, 40))
//   val receiverPair: KeyPair = encryptor.generateKey(new DJKeyGenParameterSpec(128, 40))

//   //sender
//   val n = new BigInteger("3")
//   encryptor.setKey(receiverPair.getPublic())
//   val plainText: BigIntegerPlainText = new BigIntegerPlainText(n)
//   val ciphertext: AsymmetricCiphertext = encryptor.encrypt(plainText)

//   //receiver
//   encryptor.setKey(receiverPair.getPublic(), receiverPair.getPrivate())
//   val decryptedText: BigIntegerPlainText = encryptor.decrypt(ciphertext).asInstanceOf[BigIntegerPlainText]

//   println(s">>> PLAIN:     $plainText")
//   println(s">>> CIPHER:    $ciphertext")
//   println(s">>> DECRYPTED: $decryptedText")


// }

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
