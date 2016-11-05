package org.jz.iohk

import java.security.KeyPair

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{Actor, ActorRef, Props}
import edu.biu.scapi.midLayer.asymmetricCrypto.encryption.{DJKeyGenParameterSpec, DamgardJurikEnc, ScDamgardJurikEnc}
import edu.biu.scapi.midLayer.asymmetricCrypto.keys.DamgardJurikPublicKey
import edu.biu.scapi.midLayer.ciphertext.BigIntegerCiphertext
import edu.biu.scapi.midLayer.plaintext.BigIntegerPlainText

object Broker {

  import Env.ActorMessage

  // messages sent by a broker
  sealed trait BrokerMessage extends ActorMessage
  case class Invite(publicKey: DamgardJurikPublicKey) extends BrokerMessage
  case class EncryptedNumbers(n1: BigIntegerCiphertext, n2: BigIntegerCiphertext) extends BrokerMessage
  case class EncryptedResult(n: BigIntegerCiphertext) extends BrokerMessage
  case object Abort extends BrokerMessage
  // messages sent by a broker to itself
  private case object MultiplyNumbers extends BrokerMessage
  private case object CheckProtocolTimeout extends BrokerMessage

}

class Broker(modulusLength: Int = 128, certainty: Int = 40, protocolTimeout: FiniteDuration = 10.seconds) extends Actor with LoggingInterceptor {

  import Broker._
  import Client._
  import Verifier._
  import context.dispatcher

  val encryptor: DamgardJurikEnc = new ScDamgardJurikEnc()
  val keyPair: KeyPair = encryptor.generateKey(new DJKeyGenParameterSpec(modulusLength, certainty))
  val publicKey: DamgardJurikPublicKey = keyPair.getPublic().asInstanceOf[DamgardJurikPublicKey]

  // registered clients
  var client1: Option[ActorRef] = None
  var client2: Option[ActorRef] = None
  // ciphertexts received from clients
  var ciphertext1: Option[BigIntegerCiphertext] = None
  var ciphertext2: Option[BigIntegerCiphertext] = None
  // decrypted ciphertexts received from clients
  var plainText1: Option[BigIntegerPlainText] = None
  var plainText2: Option[BigIntegerPlainText] = None
  // encrypted result of multiplication of numbers received from clients
  var encryptedProduct: Option[BigIntegerCiphertext] = None

  override def preStart(): Unit = {
    // check after a given timeout whether both clients sent their numbers
    context.system.scheduler.scheduleOnce(protocolTimeout, self, CheckProtocolTimeout)
  }

  override def receive = {

    case Register if client1.isEmpty =>
      client1 = Some(sender())
      sender() ! Invite(publicKey)

    case Register if client2.isEmpty =>
      client2 = Some(sender())
      sender() ! Invite(publicKey)

    case EncryptedNumber(ciphertext) if ciphertext1.isEmpty && Some(sender()).equals(client1) =>
      ciphertext1 = Some(ciphertext)
      self ! MultiplyNumbers

    case EncryptedNumber(ciphertext) if ciphertext2.isEmpty && Some(sender()).equals(client2) =>
      ciphertext2 = Some(ciphertext)
      self ! MultiplyNumbers

    case MultiplyNumbers if ciphertext1.isDefined && ciphertext2.isDefined && encryptedProduct.isEmpty =>
      for {
        ct1 <- ciphertext1
        ct2 <- ciphertext2
        sndr1 <- client1
        sndr2 <- client2
      } {
        sndr1 ! EncryptedNumbers(ct1, ct2)
        sndr2 ! EncryptedNumbers(ct1, ct2)
        encryptor.setKey(keyPair.getPublic(), keyPair.getPrivate())
        val number1 = encryptor.decrypt(ct1).asInstanceOf[BigIntegerPlainText]
        val number2 = encryptor.decrypt(ct2).asInstanceOf[BigIntegerPlainText]
        val plaintext: BigIntegerPlainText = new BigIntegerPlainText(number1.getX multiply number2.getX)
        val ciphertext: BigIntegerCiphertext = encryptor.encrypt(plaintext).asInstanceOf[BigIntegerCiphertext]
        plainText1 = Some(number1)
        plainText2 = Some(number2)
        encryptedProduct = Some(ciphertext)
        sndr1 ! EncryptedResult(ciphertext)
        sndr2 ! EncryptedResult(ciphertext)
      }

    case Prove =>
      for {
        cA <- ciphertext1
        cB <- ciphertext2
        cC <- encryptedProduct
        number1 <- plainText1
        number2 <- plainText2
      } {
        // sender().path needs to be saved in val because Props.apply below accepts lazy constructor
        // which can be invoked when sender() would return a different value
        val verifier = sender()
        val actorName = s"prover-${Random.alphanumeric.take(4).mkString}"
        context.actorOf(Props(new Prover(verifier, keyPair, cA, cB, cC, number1, number2)), actorName)
      }

    case CheckProtocolTimeout if ciphertext1.isEmpty || ciphertext2.isEmpty =>
      // send Abort if possible
      client1.map(_ ! Abort)
      client2.map(_ ! Abort)
      // respond with Abort to all actors
      context.become({case _ => sender() ! Abort})

    case x =>
      unhandled(x)
  }

}
