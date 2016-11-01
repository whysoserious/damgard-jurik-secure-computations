package org.jz.iohk

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, ActorSelection}
import akka.contrib.pattern._
import akka.contrib.pattern.ReceivePipeline.Inner


object Env {

  trait ActorMessage

  case class LogMessage[Msg <: ActorMessage](msg: Msg, sndr: ActorPath, rcvr: ActorPath)

}

class Env extends Actor with ActorLogging {

  import Env._

  def customName(path: ActorPath): String = {
    path.elements.dropWhile(_.equals("user")).mkString("/") match {
      case "" => path.toStringWithoutAddress
      case s => s
    }

  }

  def receive = {
    case LogMessage(msg, sndr, rcvr) =>
      println(s">>> ${customName(sndr)} -> ${customName(rcvr)}\n\t$msg\n") // Use log.info
  }

}

trait LoggingInterceptor extends ReceivePipeline {

  import Env._

  protected lazy val loggingActor: ActorSelection = context.actorSelection("/user/logging-actor")

  pipelineOuter {
    case msg: ActorMessage =>
      loggingActor ! LogMessage(msg, sender().path, self.path)
      Inner(msg)
  }

}
