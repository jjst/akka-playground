import java.nio.charset.StandardCharsets
import java.util.Base64

/**
  * Created by jjst on 17/03/17.
  */
case class ApiKey(key: String, secret: String) {
  def encode(): String = {
    Base64.getEncoder.encodeToString((key + ":" + secret).getBytes(StandardCharsets.UTF_8))
  }
}
