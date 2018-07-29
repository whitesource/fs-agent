package org.whitesource.fs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import java.util.concurrent.ConcurrentSkipListMap;

public class LogMapAppender extends AppenderBase<ILoggingEvent> {

    // using this collection as its thread-safe and easily sortable
    private ConcurrentSkipListMap<Long, ILoggingEvent> logEvents = new ConcurrentSkipListMap<>();
    private Level rootLevel;

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        // it is possible that multiple events have the same time-stamp.  in such case - increment the time-stamp key
        long timeStamp = iLoggingEvent.getTimeStamp();
        while (logEvents.get(timeStamp) != null){
            timeStamp++;
        }
        logEvents.put(timeStamp, iLoggingEvent);
        ch.qos.logback.classic.Logger logsSet = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Constants.MAP_LOG_NAME);
        // by setting the 'additive' property of this logger dynamically, it allows the pass the incoming event to the
        // parent logger depending on the event's level and root's level
        logsSet.setAdditive(iLoggingEvent.getLevel().levelInt >= rootLevel.levelInt);
    }

    public ConcurrentSkipListMap getLogEvents(){
        return logEvents;
    }

    public void setRootLevel(Level rootLevel) {
        this.rootLevel = rootLevel;
    }
}