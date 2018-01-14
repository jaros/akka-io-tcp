package net.chat

import java.net.InetSocketAddress
import java.nio.file.{Files, Path, Paths}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.io.{IO, Tcp}
import akka.util.ByteString


object ServerRunner extends App {

  val system: ActorSystem = ActorSystem("chatServer")

  val server: ActorRef = system.actorOf(Props[Server], "serverActor")

  case class Ack(file: Path) extends Tcp.Event

}

class Server extends Actor {

  import akka.io.Tcp._
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 9999))

  def receive = {
    case b@Bound(localAddress) =>
      context.parent ! b

    case CommandFailed(_: Bind) =>
      context stop self

    case c@Connected(remote, local) =>
      val handler = context.actorOf(Props[SimplisticHandler])
      val connection = sender()
      connection ! Register(handler)
  }

}

class SimplisticHandler extends Actor {

  import Tcp._

  val log = Logging(context.system, this)

  def receive = {
    case Received(data) =>
      if (data.utf8String == "start") {
        val files = Files.newDirectoryStream(Paths.get("/Users/jaros/Pictures/kiev-dnepr-2016")).iterator()

        val p = files.next()
        sender() ! Write(ByteString(Files.readAllBytes(p)), ServerRunner.Ack(p))

        context become {
          case ServerRunner.Ack(path) =>
            log.info(s"successfully transferred file $path")
            if (!files.hasNext) {
              sender() ! Write(ByteString("all-sent"))
              context unbecome()
            } else {
              val p = files.next()
              sender() ! Write(ByteString(Files.readAllBytes(p)), ServerRunner.Ack(p))
            }
        }
      } else {
        sender() ! Write(data)
      }
    case PeerClosed =>
      context stop self
  }
}