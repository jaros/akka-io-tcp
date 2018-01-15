package net.chat

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.file.{Files, Path, Paths}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.io.{IO, Tcp}
import akka.util.ByteString
import net.chat.ServerRunner.Ack


object ServerRunner extends App {

  val system: ActorSystem = ActorSystem("chatServer")

  val server: ActorRef = system.actorOf(Props[Server], "serverActor")

  case class Ack(msg: String) extends Tcp.Event

}

class Server extends Actor {

  import akka.io.Tcp._
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 9999))

  def receive: Receive = {
    case b@Bound(_) ⇒
      context.parent ! b

    case CommandFailed(_: Bind) ⇒
      context stop self

    case Connected(_, _) ⇒
      val handler = context.actorOf(Props[SimplisticHandler])
      val connection = sender()
      connection ! Register(handler)
  }

}

class SimplisticHandler extends Actor {

  import Tcp._

  val log = Logging(context.system, this)

  def receive: Receive = {
    case Received(data) =>
      if (data.utf8String == "start") {
        val files = Files.newDirectoryStream(Paths.get("/Users/jaros/Pictures/kiev-dnepr-2016")).iterator()

        val p = files.next()
        sender() ! pack(p)

        context become {
          case ServerRunner.Ack(path) ⇒
            log.info(s"successfully transferred file $path")
            if (!files.hasNext) {
              sender() ! pack("all-sent")
              context unbecome()
            } else {
              sender() ! pack(files.next())
            }
        }
      } else {
        sender() ! Write(data)
      }
    case ServerRunner.Ack(msg) ⇒
      log.info(s"ack: $msg")
    case PeerClosed ⇒
      context stop self
  }

  def pack(msg: String): Write = pack(msg, ByteString(msg))

  def pack(path: Path): Write = pack(path.toString, ByteString(Files.readAllBytes(path)))

  def pack(msg: String, data: ByteString): Write =
    Write(ByteString(ByteBuffer.allocate(4).putInt(data.length).array()) ++ data, Ack(msg))
}