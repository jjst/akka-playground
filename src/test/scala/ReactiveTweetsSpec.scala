import org.scalatest.FlatSpec

/**
  * Created by jjst on 19/03/17.
  */
class ReactiveTweetsSpec extends FlatSpec {
  "A datetime" should "get parsed" in {
    val str = "Sun Mar 19 11:20:32 +0000 2017"
    ReactiveTweets.DateFormat.parseDateTime(str)
  }
}
