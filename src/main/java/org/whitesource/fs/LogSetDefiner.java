package org.whitesource.fs;

import ch.qos.logback.core.PropertyDefinerBase;

public class LogSetDefiner extends PropertyDefinerBase {
    @Override
    public String getPropertyValue() {
        // TODO - move to constants
        return "org";
    }
}
