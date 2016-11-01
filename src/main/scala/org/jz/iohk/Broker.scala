package org.jz.iohk

import java.math.BigInteger
import java.security.{KeyPair, PublicKey, SecureRandom}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import edu.biu.scapi.midLayer.asymmetricCrypto.encryption.{DJKeyGenParameterSpec, DamgardJurikEnc, ScDamgardJurikEnc}
import edu.biu.scapi.midLayer.asymmetricCrypto.keys.DamgardJurikPublicKey
import edu.biu.scapi.midLayer.ciphertext.{AsymmetricCiphertext, BigIntegerCiphertext}
import edu.biu.scapi.midLayer.plaintext.{BigIntegerPlainText, Plaintext}
import scala.util.Random

object Broker {

  import Env.ActorMessage

  sealed trait BrokerMessage extends ActorMessage
  case class Invite(publicKey: DamgardJurikPublicKey) extends BrokerMessage
  case class EncryptedNumbers(n1: BigIntegerCiphertext, n2: BigIntegerCiphertext) extends BrokerMessage
  case class EncryptedResult(n: BigIntegerCiphertext) extends BrokerMessage
  case object Abort extends BrokerMessage
  private case object MultiplyNumbers extends BrokerMessage

}

class Broker(modulusLength: Int = 128, certainty: Int = 40) extends Actor with LoggingInterceptor { // TODO timeout

  import Broker._
  import Client._
  import Verifier._

  val encryptor: DamgardJurikEnc = new ScDamgardJurikEnc()
  val keyPair: KeyPair = encryptor.generateKey(new DJKeyGenParameterSpec(modulusLength, certainty))
  val publicKey: DamgardJurikPublicKey = keyPair.getPublic().asInstanceOf[DamgardJurikPublicKey]

  var client1: Option[ActorRef] = None
  var client2: Option[ActorRef] = None
  var ciphertext1: Option[BigIntegerCiphertext] = None
  var ciphertext2: Option[BigIntegerCiphertext] = None
  var plainText1: Option[BigIntegerPlainText] = None
  var plainText2: Option[BigIntegerPlainText] = None
  var encryptedProduct: Option[BigIntegerCiphertext] = None

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

    case Prove if encryptedProduct.isDefined =>
      for {
        cA <- ciphertext1
        cB <- ciphertext2
        cC <- encryptedProduct
        number1 <- plainText1
        number2 <- plainText2
      } {
        // sender().path needs to be saved in val because Props.apply below accepts lazy constructor
        // which can be invoked when sender() would return a different value
        val verifierPath = sender().path
        val actorName = s"prover-${Random.alphanumeric.take(4).mkString}"
        context.actorOf(Props(new Prover(verifierPath, keyPair, cA, cB, cC, number1, number2)), actorName)
      }

    case x =>
      unhandled(x)
  }

}
