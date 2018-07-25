package org.whitesource.fs;

import ch.qos.logback.core.PropertyDefinerBase;
import org.whitesource.agent.Constants;

import java.util.HashMap;
import java.util.Map;

public class LogMapDefiner extends PropertyDefinerBase {

    private static Map<String, String> properties = new HashMap<>();

    static {
        properties.put("appenderName", Constants.MAP_APPENDER_NAME);
        properties.put("loggerName", Constants.MAP_LOG_NAME);
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
