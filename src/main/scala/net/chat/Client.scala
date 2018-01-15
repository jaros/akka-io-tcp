package net.chat

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.io.Tcp.Connected
import akka.io.{IO, Tcp}
import akka.util.ByteString

import scala.annotation.tailrec

object ClientRunner extends App {
  val system: ActorSystem = ActorSystem("chatClient")

  val listener: ActorRef = system.actorOf(Props[Listener], "listenerActor")

  val client: ActorRef = system.actorOf(Props(new Client(new InetSocketAddress("localhost", 9999), listener)), "clientActor")
}

class Listener extends Actor {

  val log = Logging(context.system, this)

  override def receive: Receive = {
    case msg: String ⇒
      println(msg)
    case Connected(remote, _) ⇒
      log.info(s"successfully connected to $remote")
      sender() ! ByteString("start")
      context become {
        case data: ByteString ⇒
          if (data.utf8String == "all-sent") {
            context.unbecome()
          } else {
            // buffer and save
          }
          log.debug("got some data")
//          sender() ! "close"
      }
    case x@_ ⇒
      log.info(s"unknown message: $x")
  }

}

object Client {
  def props(remote: InetSocketAddress, replies: ActorRef) =
    Props(classOf[Client], remote, replies)
}

class Client(remote: InetSocketAddress, listener: ActorRef) extends Actor {

  import Tcp._
  import context.system

  IO(Tcp) ! Connect(remote)

  @tailrec
  private def unpackAndSend(data: ByteString): Unit = {
    val msgLength = data.take(4).asByteBuffer.getInt
    val msgBody = data.drop(4)
    val message = msgBody.take(msgLength)
    listener ! message
    if (msgBody.length > msgLength) {
      unpackAndSend(msgBody.drop(msgLength))
    }
  }

  def receive: Receive = {
    case CommandFailed(_: Connect) ⇒
      listener ! "connect failed"
      context stop self

    case c@Connected(_, _) ⇒
      listener ! c
      val connection = sender()
      connection ! Register(self, keepOpenOnPeerClosed = false)
      context become {
        case data: ByteString ⇒
          connection ! Write(data)
        case CommandFailed(w: Write) ⇒
          // O/S buffer was full
          listener ! "write failed"
        case Received(data) ⇒
          unpackAndSend(data)
        case "close" ⇒
          connection ! Close
        case _: ConnectionClosed ⇒
          listener ! "connection closed"
          context stop self
      }
  }
}
