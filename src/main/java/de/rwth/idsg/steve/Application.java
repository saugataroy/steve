package de.rwth.idsg.steve;

import de.rwth.idsg.steve.ocpp.ws.custom.WsSessionSelectStrategyEnum;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static de.rwth.idsg.steve.SteveConfiguration.Auth;
import static de.rwth.idsg.steve.SteveConfiguration.DB;
import static de.rwth.idsg.steve.SteveConfiguration.Jetty;
import static de.rwth.idsg.steve.SteveConfiguration.Ocpp;
import static de.rwth.idsg.steve.SteveConfiguration.STEVE_VERSION;

/**
 * @author Sevket Goekay <goekay@dbis.rwth-aachen.de>
 * @since 14.01.2015
 */
public class Application {

    public static void main(String[] args) throws IOException {
        loadProperties();

        JettyServer jettyServer = new JettyServer();
        jettyServer.start();
    }

    private static void loadProperties() throws IOException {
        final String fileName = "main.properties";
        Properties prop = new Properties();

        InputStream is = Application.class.getClassLoader().getResourceAsStream(fileName);
        if (is == null) {
            throw new FileNotFoundException("Property file '" + fileName + "' is not found in classpath");
        } else {
            prop.load(is);
        }

        STEVE_VERSION = prop.getProperty("steve.version");

        DB.URL      = prop.getProperty("db.url");
        DB.USERNAME = prop.getProperty("db.user");
        DB.PASSWORD = prop.getProperty("db.password");

        Auth.USERNAME   = prop.getProperty("auth.user");
        Auth.PASSWORD   = prop.getProperty("auth.password");

        Jetty.SERVER_HOST           = prop.getProperty("server.host");
        Jetty.HTTP_ENABLED          = Boolean.parseBoolean(prop.getProperty("http.enabled"));
        Jetty.HTTP_PORT             = Integer.parseInt(prop.getProperty("http.port"));
        Jetty.HTTPS_ENABLED         = Boolean.parseBoolean(prop.getProperty("https.enabled"));
        Jetty.HTTPS_PORT            = Integer.parseInt(prop.getProperty("https.port"));
        Jetty.KEY_STORE_PATH        = prop.getProperty("keystore.path");
        Jetty.KEY_STORE_PASSWORD    = prop.getProperty("keystore.password");

        Ocpp.WS_SESSION_SELECT_STRATEGY = WsSessionSelectStrategyEnum.fromName(prop.getProperty("ws.session.select.strategy"));

        if (!(Jetty.HTTP_ENABLED || Jetty.HTTPS_ENABLED)) {
            throw new IllegalArgumentException(
                    "HTTP and HTTPS are both disabled. Well, how do you want to access the server, then?");
        }
    }
}
