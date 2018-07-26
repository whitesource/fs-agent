package org.whitesource.fs;

import ch.qos.logback.core.PropertyDefinerBase;
import org.whitesource.agent.Constants;

import java.util.HashMap;
import java.util.Map;

public class LogMapDefiner extends PropertyDefinerBase {

    /* --- Static members --- */
    private static Map<String, String> properties = new HashMap<>();
    protected static final String APPENDER_NAME = "appenderName";
    protected static final String LOGGER_NAME = "loggerName";

    static {
        properties.put(APPENDER_NAME, Constants.MAP_APPENDER_NAME);
        properties.put(LOGGER_NAME, Constants.MAP_LOG_NAME);
    }

    private String propertyLookupKey;

    public void setPropertyLookupKey(String propertyLookupKey) {
        this.propertyLookupKey = propertyLookupKey;
    }

    @Override
    public String getPropertyValue() {
        return properties.get(propertyLookupKey);
    }
}
