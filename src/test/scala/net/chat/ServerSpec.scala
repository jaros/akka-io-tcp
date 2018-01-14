package net.chat

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import akka.io.Tcp.Connected
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class ServerSpec() extends TestKit(ActorSystem("ServerSpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A Server actor" must {

    val server = system.actorOf(Props[Server])

    "send files to client" in {
      val client = system.actorOf(Props(new Client(new InetSocketAddress("localhost", 9999), testActor)))
      expectMsgType[Connected]
      client ! ByteString("start")

      fishForMessage(10.seconds) {
        case data: ByteString ⇒
          data.utf8String == "all-sent"
      }

    }

    "send back messages unchanged" in {
      val echo = system.actorOf(TestActors.echoActorProps)
      echo ! "hello world"
      expectMsg("hello world")
    }

  }
}
