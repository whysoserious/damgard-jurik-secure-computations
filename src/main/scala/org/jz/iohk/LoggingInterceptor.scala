package org.jz.iohk

import akka.actor.{Actor, ActorLogging, ActorRef, ActorPath}
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
      // Yes, println for better readability
      println(s">>> ${customName(sndr)} -> ${customName(rcvr)}\n\t$msg\n")
  }

}

// more info about ReceivePipeline: http://doc.akka.io/docs/akka/2.4.12/contrib/receive-pipeline.html
trait LoggingInterceptor extends ReceivePipeline {

  import Env._

  def loggingActor: Option[ActorRef] = None

  pipelineOuter {
    case msg: ActorMessage =>
      loggingActor map { _! LogMessage(msg, sender().path, self.path) }
      Inner(msg)
  }

}
