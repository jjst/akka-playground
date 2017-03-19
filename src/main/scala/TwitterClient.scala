import akka.NotUsed
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.util.ByteString
import org.joda.time.format.DateTimeFormat
import spray.json.{DefaultJsonProtocol, JsArray, JsObject, JsString}

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val authTokenFormat = jsonFormat2(AuthorizationToken)
}

object TwitterClient {
  val DateFormat = DateTimeFormat.forPattern("E MMM dd HH:mm:ss Z yyyy")
}

class TwitterClient(http: HttpExt)(implicit mat: ActorMaterializer, ec: ExecutionContext) extends JsonSupport {

  def tweets(authorizationToken: AuthorizationToken, page: Option[Page]): Future[(Seq[Tweet], Option[Page])] = {
    val authHeader: HttpHeader = headers.Authorization(credentials = OAuth2BearerToken(authorizationToken.access_token))
    val searchUri = "https://api.twitter.com/1.1/search/tweets.json"
    val uri = page match {
      case Some(Page(queryParams)) => Uri(searchUri + queryParams)
      case None => Uri(searchUri).withQuery(Query("q" -> "scala"))
    }
    val request =
      HttpRequest(
        method = HttpMethods.GET,
        uri = uri,
        headers = Seq(authHeader)
      )
    for {
      response <- http.singleRequest(request)
      s <- Unmarshal(response.entity).to[JsObject]
    } yield {
      val nextPage = for {
        m <- s.fields.get("search_metadata")
        mo <- Try(m.asJsObject).toOption
        n <- mo.fields.get("next_results")
      } yield Page(n.asInstanceOf[JsString].value)
      val tweets = s.fields.get("statuses") match {
        case Some(JsArray(elements)) => {
          elements.flatMap { e =>
            for {
              tweet <- Try(e.asJsObject).toOption
              user <- tweet.fields.get("user")
              userObj <- Try(user.asJsObject).toOption
              screenName <- userObj.fields.get("screen_name")
              userName = screenName.asInstanceOf[JsString].value
              text <- tweet.fields.get("text")
              textStr = text.asInstanceOf[JsString].value
              createdAt <- tweet.fields.get("created_at")
              timestamp = TwitterClient.DateFormat.parseDateTime(createdAt.asInstanceOf[JsString].value).getMillis
            } yield Tweet(author = Author(userName), body = textStr, timestamp = timestamp)
          }
        }
        case _ => Vector.empty
      }
      (tweets, nextPage)
    }
  }

  def tweets(authorizationToken: AuthorizationToken): Source[Tweet, NotUsed] = {
    // Build initial request
    val f = tweets(authorizationToken, page = None) map { case (tweetsOnPage, nextPage) =>
      val initialSource = Source.single(tweetsOnPage)
      val nextPagesSource = nextPage match {
        case Some(p) => {
          // Use unfoldAsync to search & paginate
          Source.unfoldAsync(p) { nextPage: Page =>
            tweets(authorizationToken, Some(nextPage)) map { case (ts, np) =>
              np.map { p => (p, ts) }
            }
          }
        }
        case None => Source.empty
      }
      initialSource.concat(nextPagesSource)
    }
    Source
      .fromFuture(f)
      .flatMapConcat(identity)
      .mapConcat(identity)
      .throttle(450, 15.minutes, 450, ThrottleMode.Shaping)
  }

  def authenticate(twitterSettings: TwitterSettings): Future[AuthorizationToken] = {
    val apiKey = ApiKey(twitterSettings.apiKey, twitterSettings.apiSecret)
    val authHeader: HttpHeader = headers.Authorization(BasicHttpCredentials(twitterSettings.apiKey, twitterSettings.apiSecret))
    val request =
      HttpRequest(
        method = HttpMethods.POST,
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
