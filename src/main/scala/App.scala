import akka.actor.{ Actor, ActorLogging, ActorPath, ActorRef, ActorSelection, ActorSystem, PoisonPill, Props }
import akka.actor.ActorSystem
import akka.contrib.pattern.ReceivePipeline.Inner
import com.typesafe.config.ConfigFactory
import java.util.concurrent.TimeUnit.MILLISECONDS
// import com.github.nscala_time.time.Imports._
import scala.annotation.tailrec
import scala.collection.immutable._
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success, Try }
import scala.concurrent.duration._
import akka.event.Logging
import akka.contrib.pattern._

object Data {

  trait Protocol
  case class Multiplicand(n: Int) extends Protocol
  case class LogMessage[Msg <: Protocol](msg: Msg, sndr: ActorPath, rcvr: ActorPath)

  class A(calc: ActorRef, number: Int) extends Actor with LoggingInterceptor {

    val log = Logging(context.system, this)

    calc ! Multiplicand(number)

    def receive = {
      case Multiplicand(x) => log.info(s"received $x")
    }

  }

  class Env extends Actor with ActorLogging with LoggingInterceptor {

    def receive = {
      case LogMessage(msg, sndr, rcvr) => println(s"$sndr -> $rcvr\n\t$msg")
    }

  }

  trait LoggingInterceptor extends ReceivePipeline {

    protected lazy val loggingActor: ActorSelection = context.actorSelection("/user/logging-actor")

    pipelineOuter {
      case msg: Protocol =>
        implicit val ec = context.system.dispatcher
        loggingActor ! LogMessage(msg, sender().path, self.path)
        Inner(msg)
    }

  }

  class Calc extends Actor with LoggingInterceptor {

    var numbers: Map[ActorRef, Int] = Map()

    def receive = {
      case Multiplicand(n) if numbers.isEmpty =>
        numbers = numbers + (sender() -> n)
      case Multiplicand(n) =>
        val result = Multiplicand(numbers.head._2 * n)
        sender() ! result
        numbers.head._1 ! result
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
    val calc = system.actorOf(Props[Calc], "broker")
  val a1 = system.actorOf(Props(new A(calc, 7)), "client-1")
  val a2 = system.actorOf(Props(new A(calc, 8)), "client-2")
  Thread.sleep(2000)
  a1 ! PoisonPill // TODO send PoisonPill only
  a2 ! PoisonPill
  calc ! PoisonPill
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
