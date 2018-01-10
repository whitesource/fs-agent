package org.whitesource.fs.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.whitesource.fs.FSAConfiguration;

import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.OFFLINE_PRETTY_JSON_KEY;
import static org.whitesource.agent.ConfigPropertyKeys.OFFLINE_PROPERTY_KEY;
import static org.whitesource.agent.ConfigPropertyKeys.OFFLINE_ZIP_PROPERTY_KEY;

public class OfflineConfiguration {
    private final boolean enabled;
    private final boolean zip;
    private final boolean prettyJson;

    @JsonCreator
    public OfflineConfiguration(
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("zip") boolean zip,
            @JsonProperty("prettyJson") boolean prettyJson) {
        this.enabled = enabled;
        this.zip = zip;
        this.prettyJson = prettyJson;
    }

    public OfflineConfiguration(Properties config) {
        enabled = FSAConfiguration.getBooleanProperty(config, OFFLINE_PROPERTY_KEY, false);
        zip = FSAConfiguration.getBooleanProperty(config, OFFLINE_ZIP_PROPERTY_KEY, false);
        prettyJson = FSAConfiguration.getBooleanProperty(config, OFFLINE_PRETTY_JSON_KEY, false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPrettyJson() {
        return prettyJson;
    }

    public boolean isZip() {
        return zip;
    }
}
