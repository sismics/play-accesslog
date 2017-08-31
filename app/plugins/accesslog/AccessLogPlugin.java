package plugins.accesslog;

import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.libs.F;
import play.mvc.Http;
import play.mvc.Router;

import java.util.StringJoiner;

/**
 * Access logs plugin.
 *
 * @author jtremeaux
 */
public class AccessLogPlugin extends PlayPlugin {
    private static final String FORMAT = "%v %h - %u [%t] %m \"%r\" %s %b \"%ref\" \"%ua\" %rt [%rh] \"%post\" \"%response\"";

    public boolean enabled;

    public boolean logRequestHeaders;

    public boolean logPost;

    public boolean logResponse;

    public boolean consoleEnabled;

    public F.EventStream<String> events = new F.EventStream<>();

    private static final String CONFIG_PREFIX = "accesslog";

    @Override
    public void onConfigurationRead() {
        enabled = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + ".enabled", "false"));
        logRequestHeaders = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + ".logRequestHeaders", "false"));
        logPost = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + ".logPost", "false"));
        logResponse = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + ".logResponse", "false"));
        consoleEnabled = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + ".console.enabled", String.valueOf(Play.mode.isDev())));
    }

    @Override
    public void invocationFinally() {
        log();
    }

    private synchronized void log() {
        if (!enabled) {
            return;
        }
        Http.Request request = Http.Request.current();
        Http.Response response = Http.Response.current();

        if (request == null || response == null) {
            return;
        }

        long requestProcessingTime = System.currentTimeMillis() - request.date.getTime();

        Http.Header referrer = request.headers.get(HttpHeaders.Names.REFERER.toLowerCase());
        Http.Header userAgent = request.headers.get(HttpHeaders.Names.USER_AGENT.toLowerCase());

        String bytes = "-";
        String status = "-";

      /* It seems as though the Response.current() is only valid when the request is handled by a controller
         Serving static files, static 404's and 500's etc don't populate the same Response.current()
         This prevents us from getting the bytes sent and response status all of the time
       */
        if (request.action != null && response.out.size() > 0) {
            bytes = String.valueOf(response.out.size());
            status = response.status.toString();
        }

        String line = FORMAT;
        line = StringUtils.replaceOnce(line, "%v", request.host);
        line = StringUtils.replaceOnce(line, "%h", request.remoteAddress);
        line = StringUtils.replaceOnce(line, "%u", (StringUtils.isEmpty(request.user)) ? "-" : request.user);
        line = StringUtils.replaceOnce(line, "%t", request.date.toString());
        line = StringUtils.replaceOnce(line, "%m", request.method);
        line = StringUtils.replaceOnce(line, "%r", request.url);
        line = StringUtils.replaceOnce(line, "%s", status);
        line = StringUtils.replaceOnce(line, "%b", bytes);
        line = StringUtils.replaceOnce(line, "%ref", (referrer != null) ? referrer.value() : "");
        line = StringUtils.replaceOnce(line, "%ua", (userAgent != null) ? userAgent.value() : "");
        line = StringUtils.replaceOnce(line, "%rt", String.valueOf(requestProcessingTime));

        line = replaceRequestHeaders(request, line);
        line = replacePost(request, line);
        line = replaceResponse(response, line);

        line = StringUtils.trim(line);

        if (consoleEnabled) {
            events.publish(line);
        }

        Logger.info(line);
    }

    private String replaceRequestHeaders(Http.Request request, String line) {
        if (logRequestHeaders) {
            StringJoiner sj = new StringJoiner(", ");
            for (Http.Header header : request.headers.values()) {
                for (String value: header.values) {
                    sj.add("\"" + header.name + ": " + value + "\"");
                }
            }
            line = StringUtils.replaceOnce(line, "%rh", sj.toString());
        } else {
            line = StringUtils.remove(line, "[%rh]");
        }
        return line;
    }

    private String replacePost(Http.Request request, String line) {
        if (logPost && request.method.equals("POST")) {
            String body = request.params.get("body");

            if (StringUtils.isNotEmpty(body)) {
                line = StringUtils.replaceOnce(line, "%post", body);
            } else {
                // leave quotes in the logged string to show it was an empty POST request
                line = StringUtils.remove(line, "%post");
            }
        } else {
            line = StringUtils.remove(line, "\"%post\"");
        }
        return line;
    }

    private String replaceResponse(Http.Response response, String line) {
        if (logResponse && isErrorStatus(response) && response.out != null) {
            String value = response.out.toString();

            if (StringUtils.isNotEmpty(value)) {
                line = StringUtils.replaceOnce(line, "%response", value);
            } else {
                // leave quotes in the logged string to show it was an empty response
                line = StringUtils.remove(line, "%response");
            }
        } else {
            line = StringUtils.remove(line, "\"%response\"");
        }
        return line;
    }

    private boolean isErrorStatus(Http.Response response) {
        int statusCode = response.status / 100;
        return statusCode == 4 || statusCode == 5;
    }

    @Override
    public void onRoutesLoaded() {
        Router.prependRoute("GET", "/@accesslog", "AccessLogs.index");
        Router.prependRoute("WS", "/@accesslog/logs", "AccessLogWebsockets.logs");
    }
}