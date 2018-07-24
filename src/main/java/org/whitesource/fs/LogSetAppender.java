package org.whitesource.fs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.netty.util.internal.ConcurrentSet;
import org.slf4j.LoggerFactory;

public class LogSetAppender extends AppenderBase<ILoggingEvent> {

    private ConcurrentSet<ILoggingEvent> events = new ConcurrentSet<>();
    private Level rootLevel;

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        events.add(iLoggingEvent);
        ch.qos.logback.classic.Logger logsSet = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org");
        logsSet.setAdditive(iLoggingEvent.getLevel().levelInt >= rootLevel.levelInt);
    }

    public ConcurrentSet<ILoggingEvent> getEvents() {
        return events;
    }

    public void setRootLevel(Level rootLevel) {
        this.rootLevel = rootLevel;
    }
}
