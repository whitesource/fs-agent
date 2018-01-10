package org.whitesource.fs.configuration;
import org.whitesource.fs.FSAConfiguration;
import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.*;

public class EndPointConfiguration {

    public static final int DEFAULT_PORT = 443;
    public static final boolean DEFAULT_SSL = true;
    public static final String DEFAULT_CERTIFICATE = "test.jks";
    public static final String DEFAULT_PASS = "123456";

    private final int port;
    private final String certificate;
    private final String pass;
    private final boolean enabled;
    private final boolean ssl;

    //@JsonProperty("endpoint.port")
    public int getPort() {
        return port;
    }

    //@JsonProperty("endpoint.certificate")
    public String getCertificate() {
        return certificate;
    }

    //@JsonProperty("endpoint.pass")
    public String getPass() {
        return pass;
    }

    //@JsonProperty("endpoint.enabled")
    public boolean isEnabled() {
        return enabled;
    }

    //@JsonProperty("endpoint.ssl")
    public boolean isSsl() {
        return ssl;
    }

    public EndPointConfiguration(){
        this(new Properties());
    }

    //@JsonCreator
    public EndPointConfiguration(
            int port,
            String certificate,
            String pass,
            boolean enabled,
            boolean ssl) {
        this.port = port;
        this.certificate = certificate;
        this.pass = pass;
        this.enabled = enabled;
        this.ssl = ssl;
    }

    public EndPointConfiguration(Properties config) {
        port = FSAConfiguration.getIntProperty(config, ENDPOINT_PORT, DEFAULT_PORT);
        enabled = FSAConfiguration.getBooleanProperty(config, ENDPOINT_ENABLED, false);
        ssl = FSAConfiguration.getBooleanProperty(config,ENDPOINT_SSL_ENABLED , true);
        if(config.getProperty(ENDPOINT_CERTIFICATE)==null) {
            certificate = DEFAULT_CERTIFICATE;
        }else{
            certificate = config.getProperty(ENDPOINT_CERTIFICATE);
        }

        if(config.getProperty(ENDPOINT_PASS)==null) {
            pass = DEFAULT_PASS;
        }else{
            pass = config.getProperty(ENDPOINT_PASS);
        }
    }
}
