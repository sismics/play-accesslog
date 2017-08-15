package helpers;

import play.libs.Codec;
import play.mvc.Http;

/**
 * @author jtremeaux
 */
public class BasicAuthHelper {
    public static boolean checkAuthenticationHeaders(Http.Request request, String username, String password) {
        if (username == null || password == null) {
            return true;
        }
        Http.Header authHeader = request.headers.get("authorization");
        if (authHeader != null) {
            String expected = "Basic " + Codec.encodeBASE64(username + ":" + password);
            if (expected.equals(authHeader.value())) {
                return true;
            }
        }
        return false;
    }
}