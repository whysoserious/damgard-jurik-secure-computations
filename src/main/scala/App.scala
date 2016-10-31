package org.jz.iohk

import akka.actor.{ Actor, ActorLogging, ActorPath, ActorRef, ActorSelection, ActorSystem, PoisonPill, Props }
import akka.actor.ActorSystem
import akka.contrib.pattern.ReceivePipeline.Inner
import com.typesafe.config.ConfigFactory
import edu.biu.scapi.interactiveMidProtocols.sigmaProtocol.damgardJurikProduct.{ SigmaDJProductCommonInput, SigmaDJProductProverComputation, SigmaDJProductProverInput, SigmaDJProductVerifierComputation }
import edu.biu.scapi.interactiveMidProtocols.sigmaProtocol.utility.SigmaProtocolMsg
import edu.biu.scapi.midLayer.asymmetricCrypto.encryption.{ DJKeyGenParameterSpec, DamgardJurikEnc, ScDamgardJurikEnc, ScElGamalOnGroupElement }
import edu.biu.scapi.midLayer.asymmetricCrypto.keys.{ DamgardJurikPrivateKey, DamgardJurikPublicKey, ScDamgardJurikPrivateKey, ScDamgardJurikPublicKey }
import edu.biu.scapi.midLayer.ciphertext.{ AsymmetricCiphertext, BigIntegerCiphertext }
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

object HelloWorld extends App {

  println("START")
  val system = ActorSystem("dj-actor-system")
  val env = system.actorOf(Props[Env], "logging-actor")
  val broker = system.actorOf(Props(new Broker()), "caroll")
  val a1 = system.actorOf(Props(new Client(new BigInteger("7"), broker.path, 1.second)), "alice")
  val a2 = system.actorOf(Props(new Client(new BigInteger("8"), broker.path, 1.second)), "bob")
  Thread.sleep(2000)
  a1 ! PoisonPill
  a2 ! PoisonPill
  broker ! PoisonPill
  env ! PoisonPill
  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)

}
