import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import pureconfig.loadConfig
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

final case class Author(handle: String)

final case class Hashtag(name: String)

final case class Tweet(author: Author, timestamp: Long, body: String) {
  def hashtags: Set[Hashtag] =
    body.split(" ").collect { case t if t.startsWith("#") => Hashtag(t) }.toSet
}

final case class AuthorizationToken(token_type: String, access_token: String)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val authTokenFormat = jsonFormat2(AuthorizationToken)
}

object ReactiveTweets extends App with JsonSupport {
  implicit val system = ActorSystem("reactive-tweets")
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val http: HttpExt = Http()

  try {
    loadConfig[TwitterSettings](ConfigFactory.load(), "reactivetweets.twitter") match {
      case Left(failures) => {
        println(failures)
      }
      case Right(twitterSettings) => {
        val r = Await.result(authenticate(twitterSettings), 5.seconds)
        println(r)
        //printTweets(twitterSettings)
      }
    }

  }
  finally {
    system.terminate()
  }

  def printTweets(twitterSettings: TwitterSettings) = {


    val akkaTag = Hashtag("#akka")

    val tweets: Source[Tweet, NotUsed] = ???

    val authors: Source[Author, NotUsed] =
      tweets
        .filter(_.hashtags.contains(akkaTag))
        .map(_.author)

    authors.runForeach(println)
  }


  def authenticate(twitterSettings: TwitterSettings)(implicit http: HttpExt, materializer: ActorMaterializer, ec: ExecutionContext): Future[AuthorizationToken] = {
    import HttpMethods._

    val apiKey = ApiKey(twitterSettings.apiKey, twitterSettings.apiSecret)
    val authHeader: HttpHeader = headers.Authorization(BasicHttpCredentials(twitterSettings.apiKey, twitterSettings.apiSecret))
    val request =
      HttpRequest(
        method = POST,
        uri = "https://api.twitter.com/oauth2/token",
        headers = scala.collection.immutable.Seq(authHeader),
        entity = HttpEntity(data = ByteString("grant_type=client_credentials"), contentType = ContentType(MediaTypes.`application/x-www-form-urlencoded`, HttpCharsets.`UTF-8`))
      )
    for {
      response <- http.singleRequest(request)
      token <- Unmarshal(response.entity).to[AuthorizationToken]
    } yield token
  }
}
