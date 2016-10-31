package org.jz.iohk

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, PoisonPill}
import edu.biu.scapi.interactiveMidProtocols.sigmaProtocol.damgardJurikProduct.{SigmaDJProductCommonInput, SigmaDJProductVerifierComputation}
import edu.biu.scapi.interactiveMidProtocols.sigmaProtocol.utility.SigmaProtocolMsg
import edu.biu.scapi.midLayer.asymmetricCrypto.keys.DamgardJurikPublicKey
import edu.biu.scapi.midLayer.ciphertext.BigIntegerCiphertext

object Verifier {

  import Env.ActorMessage

  sealed trait VerifierMessage extends ActorMessage
  case object Prove extends VerifierMessage
  case class Challenge(challenge: Array[Byte]) extends VerifierMessage
  case class ProofResult(success: Boolean) extends VerifierMessage

}
class Verifier(broker: ActorSelection,
               publicKey: DamgardJurikPublicKey,
               cA: BigIntegerCiphertext, cB: BigIntegerCiphertext, cC: BigIntegerCiphertext) extends Actor with LoggingInterceptor {

  import Prover._
  import Verifier._

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
      val commonInput: SigmaDJProductCommonInput = new SigmaDJProductCommonInput(publicKey,
                                                                                 cA.asInstanceOf[BigIntegerCiphertext],
                                                                                 cB.asInstanceOf[BigIntegerCiphertext],
                                                                                 cC.asInstanceOf[BigIntegerCiphertext])
      val result: Boolean = verifierComputation.verify(commonInput, proverMsg1.get, msg2)
      context.parent ! ProofResult(result)
      self ! PoisonPill

    case x =>
      unhandled(x)
  }

}
