package org.jz.iohk

import akka.actor.{Actor, ActorRef}
import edu.biu.scapi.interactiveMidProtocols.sigmaProtocol.damgardJurikProduct.{SigmaDJProductCommonInput, SigmaDJProductVerifierComputation}
import edu.biu.scapi.interactiveMidProtocols.sigmaProtocol.utility.SigmaProtocolMsg
import edu.biu.scapi.midLayer.asymmetricCrypto.keys.DamgardJurikPublicKey
import edu.biu.scapi.midLayer.ciphertext.BigIntegerCiphertext

object Verifier {

  import Env.ActorMessage

  //messages sent by a verifier
  sealed trait VerifierMessage extends ActorMessage
  case object Prove extends VerifierMessage
  case class Challenge(challenge: Array[Byte]) extends VerifierMessage
  case class ProofResult(success: Boolean) extends VerifierMessage

}

// Verifier usually spawned by a client of a broker. Responsible for validating a zero-knowledge proof of a computation performed by a broker
class Verifier(broker: ActorRef,
               publicKey: DamgardJurikPublicKey,
               cA: BigIntegerCiphertext, cB: BigIntegerCiphertext, cC: BigIntegerCiphertext,
               _loggingActor: Option[ActorRef] = None) extends Actor with LoggingInterceptor {

  import Prover._
  import Verifier._

  override def loggingActor: Option[ActorRef] = _loggingActor
  val verifierComputation: SigmaDJProductVerifierComputation = new SigmaDJProductVerifierComputation()

  var proverMsg1: Option[SigmaProtocolMsg] = None

  override def preStart: Unit = {
    broker ! Prove
  }

  override def receive = {

    case Message1(msg1) if proverMsg1.isEmpty =>
      proverMsg1 = Some(msg1)
      verifierComputation.sampleChallenge()
      val challenge: Array[Byte] = verifierComputation.getChallenge()
      sender() ! Challenge(challenge)

    case Message2(msg2) if proverMsg1.isDefined  =>
      val commonInput: SigmaDJProductCommonInput = new SigmaDJProductCommonInput(publicKey, cA, cB, cC)
      val result: Boolean = verifierComputation.verify(commonInput, proverMsg1.get, msg2)
      context.parent ! ProofResult(result)
      context.stop(self)

    case x =>
      unhandled(x)
  }

}
