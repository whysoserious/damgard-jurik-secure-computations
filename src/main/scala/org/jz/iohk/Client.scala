package org.jz.iohk

import java.math.BigInteger

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import edu.biu.scapi.midLayer.asymmetricCrypto.encryption.{DamgardJurikEnc, ScDamgardJurikEnc}
import edu.biu.scapi.midLayer.asymmetricCrypto.keys.DamgardJurikPublicKey
import edu.biu.scapi.midLayer.ciphertext.BigIntegerCiphertext
import edu.biu.scapi.midLayer.plaintext.BigIntegerPlainText

object Client {

  import Env.ActorMessage

  sealed trait ClientMessage extends ActorMessage
  case object Register extends ClientMessage
  case class EncryptedNumber(n: BigIntegerCiphertext) extends ClientMessage
  case object GetProofResult extends ClientMessage

}

class Client(number: BigInteger, broker: ActorRef) extends Actor with ActorLogging with LoggingInterceptor {

  import Broker._
  import Client._
  import Verifier._

  var cA: Option[BigIntegerCiphertext] = None
  var cB: Option[BigIntegerCiphertext] = None
  var cC: Option[BigIntegerCiphertext] = None
  var publicKey: Option[DamgardJurikPublicKey] = None
  var proofResult: Option[ProofResult] = None
  var waitingForProofResult: Seq[ActorRef] = Seq()

  implicit val ec = context.dispatcher

  override def preStart: Unit = {
    broker ! Register
  }

  override def receive = {

    case Invite(pk: DamgardJurikPublicKey) if cA.isEmpty =>
      publicKey = Some(pk)
      val cipherText: BigIntegerCiphertext = encryptNumber(number, pk)
      broker ! EncryptedNumber(cipherText)

    case EncryptedNumbers(n1: BigIntegerCiphertext, n2: BigIntegerCiphertext) if cA.isEmpty && cB.isEmpty =>
      cA= Some(n1)
      cB = Some(n2)

    case EncryptedResult(n: BigIntegerCiphertext) if cC.isEmpty =>
      cC = Some(n)
      for {
        a <- cA
        b <- cB
        c <- cC
        pk <- publicKey
      } {
        context.actorOf(Props(new Verifier(broker, pk, a, b, c)), "verifier")
      }

    case pr @ ProofResult(success) =>
      log.info(s"Proof result: $success")
      waitingForProofResult.foreach(_ ! pr)
      proofResult = Some(pr)

    case GetProofResult =>
      proofResult match {
        case None => waitingForProofResult = waitingForProofResult :+ sender()
        case Some(pr) => sender ! pr
      }

    case Abort =>
      context.stop(self)

    case x =>
      unhandled(x)
  }

  def encryptNumber(n: BigInteger, publicKey: DamgardJurikPublicKey): BigIntegerCiphertext = { // TODO use
    val encryptor: DamgardJurikEnc = new ScDamgardJurikEnc()
    encryptor.setKey(publicKey)
    encryptor.encrypt(new BigIntegerPlainText(n)).asInstanceOf[BigIntegerCiphertext]
  }

}
