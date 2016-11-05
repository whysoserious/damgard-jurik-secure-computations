package org.jz.iohk

import java.security.{KeyPair, SecureRandom}

import akka.actor.{Actor, ActorRef}
import edu.biu.scapi.interactiveMidProtocols.sigmaProtocol.damgardJurikProduct.{SigmaDJProductProverComputation, SigmaDJProductProverInput}
import edu.biu.scapi.interactiveMidProtocols.sigmaProtocol.utility.SigmaProtocolMsg
import edu.biu.scapi.midLayer.asymmetricCrypto.keys.{DamgardJurikPrivateKey, DamgardJurikPublicKey}
import edu.biu.scapi.midLayer.ciphertext.BigIntegerCiphertext
import edu.biu.scapi.midLayer.plaintext.BigIntegerPlainText

object Prover {

  import Env.ActorMessage
  // messages sent by prover
  sealed trait ProverMessage extends ActorMessage
  case class Message1(msg: SigmaProtocolMsg) extends ProverMessage
  case class Message2(msg: SigmaProtocolMsg) extends ProverMessage

}

// Prover usually spawned by a broker. Responsible for providing a valid zero-knowledge proof of a computation
class Prover(verifier: ActorRef, keyPair: KeyPair,
             cA: BigIntegerCiphertext, cB: BigIntegerCiphertext, cC: BigIntegerCiphertext,
             n1: BigIntegerPlainText, n2: BigIntegerPlainText,
             secureRandom: SecureRandom = new SecureRandom(),
             _loggingActor: Option[ActorRef] = None) extends Actor with LoggingInterceptor {

  import Prover._
  import Verifier._

  override def loggingActor: Option[ActorRef] = _loggingActor
  val publicKey: DamgardJurikPublicKey = keyPair.getPublic.asInstanceOf[DamgardJurikPublicKey]
  val privateKey: DamgardJurikPrivateKey = keyPair.getPrivate.asInstanceOf[DamgardJurikPrivateKey]
  val proverComputation: SigmaDJProductProverComputation = new SigmaDJProductProverComputation()

  override def preStart: Unit = {
    val proverInput: SigmaDJProductProverInput = new SigmaDJProductProverInput(publicKey, cA, cB, cC, privateKey, n1, n2)
    val msg1: SigmaProtocolMsg = proverComputation.computeFirstMsg(proverInput)
    verifier ! Message1(msg1)
  }

  override def receive = {

    case Challenge(challenge) =>
      val msg2 = proverComputation.computeSecondMsg(challenge)
      verifier ! Message2(msg2)
      context.stop(self)

    case x =>
      unhandled(x)

  }
}
