package controllers;

import helpers.accesslog.BasicAuthHelper;
import play.Play;
import play.mvc.Controller;
import play.mvc.Util;
import plugins.accesslog.AccessLogPlugin;

/**
 * Access logs controller.
 *
 * @author jtremeaux
 */
public class AccessLogs extends Controller {
    public static void index() {
        AccessLogPlugin accessLogPlugin = getPluginInstance();
        if (!accessLogPlugin.consoleEnabled) {
            notFound();
        }
        checkBasicAuth();
        render(accessLogPlugin);
    }

    public static void updateAccessLog(boolean enabled, boolean logRequestHeaders, boolean logPost, boolean logResponse) {
        AccessLogPlugin accessLogPlugin = getPluginInstance();
        if (!accessLogPlugin.consoleEnabled) {
            notFound();
        }
        checkBasicAuth();
        accessLogPlugin.enabled = enabled;
        accessLogPlugin.logRequestHeaders = logRequestHeaders;
        accessLogPlugin.logPost = logPost;
        accessLogPlugin.logResponse = logResponse;
        flash.put("success", true);
        index();
    }

    @Util
    public static AccessLogPlugin getPluginInstance() {
        return (AccessLogPlugin) Play.pluginCollection.getPluginInstance(AccessLogPlugin.class);
    }

    @Util
    private static void checkBasicAuth() {
        String username = Play.configuration.getProperty("accesslog.console.username");
        String password = Play.configuration.getProperty("accesslog.console.password");
        if (!BasicAuthHelper.checkAuthenticationHeaders(request, username, password)) {
            unauthorized("Access log console");
        }
    }
}
