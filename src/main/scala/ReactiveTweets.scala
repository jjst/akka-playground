import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import pureconfig.loadConfig

import scala.concurrent.duration._

final case class Author(handle: String)

final case class Hashtag(name: String)

final case class Tweet(author: Author, timestamp: Long, body: String) {
  def hashtags: Set[Hashtag] =
    body.split(" ").collect { case t if t.startsWith("#") => Hashtag(t) }.toSet
}

final case class AuthorizationToken(token_type: String, access_token: String)

final case class Page(queryParams: String) extends AnyVal

object ReactiveTweets extends App {

  loadConfig[TwitterSettings](ConfigFactory.load(), "reactivetweets.twitter") match {
    case Left(failures) => {
      println(failures)
    }
    case Right(twitterSettings) => {

      implicit val system = ActorSystem("reactive-tweets")
      implicit val materializer = ActorMaterializer()
      implicit val dispatcher = system.dispatcher
      implicit val http: HttpExt = Http()
      val twitterClient = new TwitterClient(http)

      val f = for {
       token <- twitterClient.authenticate(twitterSettings)
      } yield twitterClient.tweets(token)
      /*
      val source = Source.fromFuture(f)
      source.runForeach(println)
      val r = Await.result(f, 5.seconds)
      println(r)
      */
      val source = Source.fromFuture(f).flatMapConcat(identity)
      val akkaTag = Hashtag("#scala")
      source
        .filter(_.hashtags.contains(akkaTag))
        .takeWithin(1.minute)
        .runForeach(println)
        .onComplete(_ => system.terminate())
    }
  }
}
