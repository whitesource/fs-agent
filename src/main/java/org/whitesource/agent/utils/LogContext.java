package org.whitesource.agent.utils;

import org.whitesource.agent.Constants;

import java.io.Serializable;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * Created by anna.rozin
 * Don't delete this class - it is being used by outside code
 */
public class LogContext implements Serializable {

    /* --- Static members --- */

    private static final long serialVersionUID = 4342818989304453815L;

    protected static final String CONTEXT_FORMAT = "CTX=%s";

    /* --- Members --- */

    private String contextId;

    /* --- Constructors --- */

    public LogContext(){
        contextId = new Random().nextInt(4) + UUID.randomUUID().toString().replace(Constants.DASH, Constants.EMPTY_STRING);
    }

    /* --- Public methods --- */

    public String getExtraContextString() {
        return Constants.EMPTY_STRING;
    }

    /* --- Overridden methods --- */

    @Override
    public String toString() {
        return String.format(CONTEXT_FORMAT, contextId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogContext)) return false;
        LogContext that = (LogContext) o;
        return Objects.equals(contextId, that.contextId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextId);
    }

    /* --- Getters / Setters --- */

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

}
