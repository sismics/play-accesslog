package plugins.accesslog;

import helpers.accesslog.CappedEventStream;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.mvc.Http;
import play.mvc.Router;

import java.util.StringJoiner;
import java.util.regex.Pattern;

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

    public Pattern blacklistPattern;

    /**
     * The last request hash code (used to count duplicates).
     */
    public int lastRequestHashCode;

    /**
     * The number of duplicates from last request.
     */
    public long duplicates = 0;
    /**
     * The previous line to log.
     */
    public String previousLine;


    public CappedEventStream<String> events = new CappedEventStream<>();

    private static final String CONFIG_PREFIX = "accesslog";

    @Override
    public void onConfigurationRead() {
        enabled = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + ".enabled", "false"));
        logRequestHeaders = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + ".logRequestHeaders", "false"));
        logPost = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + ".logPost", "false"));
        logResponse = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + ".logResponse", "false"));
        consoleEnabled = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + ".console.enabled", String.valueOf(Play.mode.isDev())));
        String blacklistRegexp = Play.configuration.getProperty(CONFIG_PREFIX + ".blacklist");
        if (blacklistRegexp != null && !"".equals(blacklistRegexp.trim())) {
            try {
                blacklistPattern = Pattern.compile(blacklistRegexp);
            } catch (Exception e) {
                Logger.error("Error compiling blacklist pattern: " + blacklistRegexp);
            }
        } else {
            blacklistPattern = null;
        }
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

        String line = FORMAT;
        line = StringUtils.replaceOnce(line, "%v", request.host);
        line = StringUtils.replaceOnce(line, "%h", request.remoteAddress);
        line = StringUtils.replaceOnce(line, "%u", getUser(request));
        line = StringUtils.replaceOnce(line, "%t", request.date.toString());
        line = StringUtils.replaceOnce(line, "%m", request.method);
        line = StringUtils.replaceOnce(line, "%r", request.url);
        line = StringUtils.replaceOnce(line, "%s", getStatus(request, response));
        line = StringUtils.replaceOnce(line, "%b", getBytes(request, response));
        line = StringUtils.replaceOnce(line, "%ref", getReferrer(request));
        line = StringUtils.replaceOnce(line, "%ua", getUserAgent(request));
        line = StringUtils.replaceOnce(line, "%rt", String.valueOf(requestProcessingTime));

        line = replaceRequestHeaders(request, line);
        line = replacePost(request, line);
        line = replaceResponse(response, line);

        line = StringUtils.trim(line);

        if (isBlacklisted(line)) {
            return;
        }

        // Check for duplicates
        int requestHashCode = getRequestHashCode();
        if (lastRequestHashCode == requestHashCode) {
            previousLine = line;
            duplicates++;
        } else {
            lastRequestHashCode = requestHashCode;
            if (duplicates > 0) {
                logLine(previousLine);
                logLine(" + " + duplicates + " duplicates");
                duplicates = 0;
            }

            logLine(line);
        }
    }

    private void logLine(String line) {
        if (consoleEnabled) {
            events.publish(line);
        }

        Logger.info(line);
    }

    private boolean isBlacklisted(String line) {
        if (blacklistPattern == null) {
            return false;
        }

        return blacklistPattern.matcher(line).find();
    }

    private String getUser(Http.Request request) {
        return (StringUtils.isEmpty(request.user)) ? "-" : request.user;
    }

    private String getStatus(Http.Request request, Http.Response response) {
      /* It seems as though the Response.current() is only valid when the request is handled by a controller
         Serving static files, static 404's and 500's etc don't populate the same Response.current()
         This prevents us from getting the bytes sent and response status all of the time
       */
        if (request.action != null && response.out.size() > 0) {
            return response.status.toString();
        }
        return "-";
    }

    private String getBytes(Http.Request request, Http.Response response) {
      /* It seems as though the Response.current() is only valid when the request is handled by a controller
         Serving static files, static 404's and 500's etc don't populate the same Response.current()
         This prevents us from getting the bytes sent and response status all of the time
       */
        if (request.action != null && response.out.size() > 0) {
            return String.valueOf(response.out.size());
        }
        return "-";
    }

    private String getReferrer(Http.Request request) {
        Http.Header referrer = request.headers.get(HttpHeaders.Names.REFERER.toLowerCase());
        return (referrer != null) ? referrer.value() : "";
    }

    private String getUserAgent(Http.Request request) {
        Http.Header userAgent = request.headers.get(HttpHeaders.Names.USER_AGENT.toLowerCase());
        return (userAgent != null) ? userAgent.value() : "";
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

    private int getRequestHashCode() {
        Http.Request request = Http.Request.current();
        Http.Response response = Http.Response.current();
        String requestSig = request.host;
        requestSig += request.remoteAddress;
        requestSig += getUser(request);
        requestSig += request.method;
        requestSig += request.url;
        requestSig += getStatus(request, response);
        requestSig += getBytes(request, response);
        requestSig += getReferrer(request);
        requestSig += getUserAgent(request);
        return requestSig.hashCode();
    }

    @Override
    public void onRoutesLoaded() {
        Router.prependRoute("GET", "/@accesslog", "AccessLogs.index");
        Router.prependRoute("WS", "/@accesslog/logs", "AccessLogWebsockets.logs");
    }
}