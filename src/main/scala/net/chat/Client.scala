package net.chat

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.Tcp.Connected
import akka.io.{IO, Tcp}
import akka.util.ByteString

object ClientRunner extends App {
  val system: ActorSystem = ActorSystem("chatClient")

  val listener: ActorRef = system.actorOf(Props[Listener], "listenerActor")

  val client: ActorRef = system.actorOf(Props(new Client(new InetSocketAddress("localhost", 9999), listener)), "clientActor")
}

class Listener extends Actor {
  override def receive: Receive = {
    case msg: String =>
      println(msg)
    case Connected(remote, _) =>
      println(s"successfully connected to $remote")
      sender() ! ByteString("hello")
      context become {
        case data: ByteString =>
          println(data.utf8String)
          sender() ! "close"
          context.unbecome()
      }
    case x@_ =>
      println(s"unknown message: $x")
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

  def receive = {
    case CommandFailed(_: Connect) =>
      listener ! "connect failed"
      context stop self

    case c@Connected(remote, local) =>
      listener ! c
      val connection = sender()
      connection ! Register(self)
      context become {
        case data: ByteString =>
          connection ! Write(data)
        case CommandFailed(w: Write) =>
          // O/S buffer was full
          listener ! "write failed"
        case Received(data) =>
          listener ! data
        case "close" =>
          connection ! Close
        case _: ConnectionClosed =>
          listener ! "connection closed"
          context stop self
      }
  }
}
