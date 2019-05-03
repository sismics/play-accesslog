package controllers;

import play.Play;
import play.mvc.Util;
import play.mvc.WebSocketController;
import plugins.accesslog.AccessLogPlugin;

/**
 * Access logs Websocket controller.
 *
 * @author jtremeaux
 */
public class AccessLogWebsockets extends WebSocketController {
    public static void logs() {
        while (inbound.isOpen()) {
            outbound.send(await(getPluginInstance().events.nextEvent()));
        }
    }

    @Util
    public static AccessLogPlugin getPluginInstance() {
        return Play.pluginCollection.getPluginInstance(AccessLogPlugin.class);
    }
}
