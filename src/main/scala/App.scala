import akka.actor.{ Actor, ActorLogging, ActorPath, ActorRef, ActorSelection, ActorSystem, PoisonPill, Props }
import akka.actor.ActorSystem
import akka.contrib.pattern.ReceivePipeline.Inner
import com.typesafe.config.ConfigFactory
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

object Data {

  trait Protocol

  case class LogMessage[Msg <: Protocol](msg: Msg, sndr: ActorPath, rcvr: ActorPath)

  class Env extends Actor with ActorLogging with LoggingInterceptor {

    def receive = {
      case LogMessage(msg, sndr, rcvr) => println(s"$sndr -> $rcvr\n\t$msg")
    }

  }

  trait LoggingInterceptor extends ReceivePipeline {

    protected lazy val loggingActor: ActorSelection = context.actorSelection("/user/logging-actor")

    pipelineOuter {
      case msg: Protocol =>
        loggingActor ! LogMessage(msg, sender().path, self.path)
        Inner(msg)
    }

  }

  case object Abort extends Protocol
  case object Invite extends Protocol
  case class Announce(numbers: Seq[EncryptedNumber]) extends Protocol
  case class Result(number: EncryptedNumber) extends Protocol

  class Client(number: () => Int) extends Actor with LoggingInterceptor { // TODO () => Int?

    def receive = {
      case Invite => sender() ! EncryptedNumber(number())
      case Abort =>
      case Announce(numbers) =>
      case Result(number) =>
    }

  }

  case class Initialize(clients1: ActorRef, client2: ActorRef) extends Protocol // TODO seq or pair?
  case class EncryptedNumber(n: Int) extends Protocol

  class Broker extends Actor with LoggingInterceptor {

    var clients: Map[ActorRef, Option[EncryptedNumber]] = Map()

    def receive = {
      case Initialize(client1, client2) =>
        clients = Map(client1 -> None, client2 -> None)
        clients.keys.foreach(_ ! Invite)
      case en: EncryptedNumber if clients.contains(sender()) =>
        clients = clients.updated(sender(), Some(en))
        tryAnnounceNumbers(Seq(clients.values.toSeq: _*)) map { numbers =>
          clients.keys.foreach(_ ! Result(EncryptedNumber(666)))
        }
    }

    @tailrec
    private def tryAnnounceNumbers(numbers: Seq[Option[EncryptedNumber]], acc: Seq[EncryptedNumber] = Seq()): Option[Seq[EncryptedNumber]] = {
      numbers match {
        case Nil if acc.size == clients.size =>
          val msg = Announce(acc)
          clients.keys.foreach(_ ! msg)
          Some(acc)
        case Some(en) :: tail =>
          tryAnnounceNumbers(tail, acc :+ en)
        case _ =>
          None
      }
    }

  }

  val config = ConfigFactory.parseString(
    """|iohk.logging.actor-name = "logging-actor"
       |iohk.logging.max-resolve-time = 1 second""".stripMargin)
}

object HelloWorld extends App {

  import Data._

  println("START")
  val system = ActorSystem("dj-actor-system", ConfigFactory.load(config))
  val env = system.actorOf(Props[Env], "logging-actor")
  val broker = system.actorOf(Props[Broker], "broker")
  val a1 = system.actorOf(Props(new Client(() => 7)), "client-1")
  val a2 = system.actorOf(Props(new Client(() => 8)), "client-2")
  broker ! Initialize(a1, a2)
  Thread.sleep(2000)
  a1 ! PoisonPill // TODO send PoisonPill only
  a2 ! PoisonPill
  broker ! PoisonPill
  env ! PoisonPill
  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)

}


// protected def resolveLoggingActor(): Future[ActorRef] = {
//   val config = ConfigFactory.load()
//   val actorPath: ActorPath = ActorPath.fromString(config.getString("iohk.logging.actor-path"))
//   val maxResolveTime: FiniteDuration = FiniteDuration(config.getDuration("iohk.logging.max-resolve-time", MILLISECONDS), MILLISECONDS)
//   context.actorSelection(actorPath).resolveOne(maxResolveTime)
// }

// protected lazy val loggingActor: Future[ActorRef] = resolveLoggingActor()
