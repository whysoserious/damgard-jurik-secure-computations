package org.jz.iohk

import java.math.BigInteger
import java.util.concurrent.TimeUnit.MILLISECONDS

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{ActorSystem, PoisonPill, Props}

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
