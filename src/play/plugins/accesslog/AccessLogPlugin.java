package play.plugins.accesslog;

import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import play.Play;
import play.PlayPlugin;
import play.mvc.Http;

public class AccessLogPlugin extends PlayPlugin {
    private static final String FORMAT = "%v %h - %u [%t] \"%r\" %s %b \"%ref\" \"%ua\" %rt \"%post\"";

    private boolean enable;

    private boolean logPost;

    private static final String CONFIG_PREFIX = "accesslog";

    @Override
    public void onConfigurationRead() {
        enable = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + ".enabled", "false"));
        logPost = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + ".logPost", "false"));
    }

    @Override
    public void onApplicationStart() {
    }

    @Override
    public void onApplicationStop() {
    }

    @Override
    public void invocationFinally() {
        log();
    }

    private synchronized void log() {
        if (!enable) {
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
        line = StringUtils.replaceOnce(line, "%r", request.url);
        line = StringUtils.replaceOnce(line, "%s", status);
        line = StringUtils.replaceOnce(line, "%b", bytes);
        line = StringUtils.replaceOnce(line, "%ref", (referrer != null) ? referrer.value() : "");
        line = StringUtils.replaceOnce(line, "%ua", (userAgent != null) ? userAgent.value() : "");
        line = StringUtils.replaceOnce(line, "%rt", String.valueOf(requestProcessingTime));

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

        line = StringUtils.trim(line);

        play.Logger.info(line);
    }
}