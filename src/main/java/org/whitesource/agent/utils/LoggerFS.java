package org.whitesource.agent.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

/**
 * @author raz.nitzan
 */
public class LoggerFS implements Logger {

    /* --- Static Members --- */

    private static final String OPENING_BRACKET = "[";
    private static final String CLOSING_BRACKET = "] ";
    private static final String CTX = "[CTX=";

    /* --- Members --- */

    private final org.slf4j.Logger logger;
    private final String contextId;

    /* --- Constructors --- */

    public LoggerFS(Class clazz){
        this.logger = LoggerFactory.getLogger(clazz);
        this.contextId = null;
    }

    public LoggerFS(Class clazz, String contextId){
        this.logger = LoggerFactory.getLogger(clazz);
        this.contextId = contextId;
    }

    public LoggerFS(String name) {
        this.logger = LoggerFactory.getLogger(name);
        this.contextId = null;
    }

    public LoggerFS(String name, String contextId) {
        this.logger = LoggerFactory.getLogger(name);
        this.contextId = contextId;
    }

    @Override
    public void info(String msg) {
        this.logger.info(msgWithContextId(msg));
    }

    @Override
    public void info(String msg, Object arg) {
        this.logger.info(msgWithContextId(msg), arg);
    }

    @Override
    public void info(String msg, Object o, Object o1) {
        this.logger.info(msgWithContextId(msg), o, o1);
    }

    @Override
    public void info(String format, Object... args) {
        this.logger.info(msgWithContextId(format), args);
    }

    @Override
    public void info(String msg, Throwable t) {
        this.logger.info(msgWithContextId(msg), t);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return this.isInfoEnabled(marker);
    }

    @Override
    public void info(Marker marker, String s) {
        this.logger.info(marker, msgWithContextId(s));
    }

    @Override
    public void info(Marker marker, String s, Object o) {
        this.logger.info(marker, msgWithContextId(s), o);
    }

    @Override
    public void info(Marker marker, String s, Object o, Object o1) {
        this.logger.info(marker, msgWithContextId(s), o, o1);
    }

    @Override
    public void info(Marker marker, String s, Object... objects) {
        this.logger.info(marker, msgWithContextId(s), objects);
    }

    @Override
    public void info(Marker marker, String s, Throwable throwable) {
        this.logger.info(marker, msgWithContextId(s), throwable);
    }

    @Override
    public void debug(String msg) {
        this.logger.debug(msgWithContextId(msg));
    }

    @Override
    public void debug(String msg, Object arg) {
        this.logger.debug(msgWithContextId(msg), arg);
    }

    @Override
    public void debug(String s, Object o, Object o1) {
        this.logger.debug(msgWithContextId(s), o, o1);
    }

    @Override
    public void debug(String format, Object... args) {
        this.logger.debug(msgWithContextId(format), args);
    }

    @Override
    public void debug(String msg, Throwable t) {
        this.logger.debug(msgWithContextId(msg), t);
    }

    @Override
    public void warn(String msg) {
        this.logger.warn(msgWithContextId(msg));
    }

    @Override
    public void warn(String msg, Object arg) {
        this.logger.warn(msgWithContextId(msg), arg);
    }

    @Override
    public void warn(String format, Object... args) {
        this.logger.warn(msgWithContextId(format), args);
    }

    @Override
    public void warn(String s, Object o, Object o1) {
        this.logger.warn(msgWithContextId(s), o, o1);
    }

    public void warn(String msg, Throwable t) {
        this.logger.warn(msgWithContextId(msg), t);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return this.logger.isWarnEnabled(marker);
    }

    @Override
    public void warn(Marker marker, String s) {
        this.logger.warn(marker, msgWithContextId(s));
    }

    @Override
    public void warn(Marker marker, String s, Object o) {
        this.logger.warn(marker, msgWithContextId(s), o);
    }

    @Override
    public void warn(Marker marker, String s, Object o, Object o1) {
        this.logger.warn(marker, msgWithContextId(s), o, o1);
    }

    @Override
    public void warn(Marker marker, String s, Object... objects) {
        this.logger.warn(marker, msgWithContextId(s), objects);
    }

    @Override
    public void warn(Marker marker, String s, Throwable throwable) {
        this.logger.warn(marker, msgWithContextId(s), throwable);
    }

    @Override
    public void error(String msg) {
        this.logger.error(msgWithContextId(msg));
    }

    @Override
    public void error(String msg, Object arg) {
        this.logger.error(msgWithContextId(msg), arg);
    }

    @Override
    public void error(String s, Object o, Object o1) {
        this.logger.error(msgWithContextId(s), o, o1);
    }

    @Override
    public void error(String format, Object... args) {
        this.logger.error(msgWithContextId(format), args);
    }

    @Override
    public void error(String msg, Throwable t) {
        this.logger.error(msgWithContextId(msg), t);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return this.logger.isErrorEnabled(marker);
    }

    @Override
    public void error(Marker marker, String s) {
        this.logger.error(marker, msgWithContextId(s));
    }

    @Override
    public void error(Marker marker, String s, Object o) {
        this.logger.error(marker, msgWithContextId(s), o);
    }

    @Override
    public void error(Marker marker, String msg, Object arg1, Object arg2) {
        this.logger.error(marker, msgWithContextId(msg), arg1, arg2);
    }

    @Override
    public void error(Marker marker, String s, Object... objects) {
        this.logger.error(marker, msgWithContextId(s), objects);
    }

    @Override
    public void error(Marker marker, String s, Throwable throwable) {
        this.logger.error(marker, msgWithContextId(s), throwable);
    }

    @Override
    public void trace(String msg) {
        this.logger.trace(msgWithContextId(msg));
    }

    @Override
    public void trace(String msg, Object arg) {
        this.logger.trace(msgWithContextId(msg), arg);
    }

    @Override
    public void trace(String s, Object o, Object o1) {
        this.logger.trace(msgWithContextId(s), o, o1);
    }

    @Override
    public void trace(String format, Object... args) {
        this.logger.trace(msgWithContextId(format), args);
    }

    @Override
    public void trace(String msg, Throwable t) {
        this.logger.trace(msgWithContextId(msg), t);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return this.logger.isTraceEnabled(marker);
    }

    @Override
    public void trace(Marker marker, String s) {
        this.logger.trace(marker, msgWithContextId(s));
    }

    @Override
    public void trace(Marker marker, String s, Object o) {
        this.logger.trace(marker, msgWithContextId(s), o);
    }

    @Override
    public void trace(Marker marker, String s, Object o, Object o1) {
        this.logger.trace(marker, msgWithContextId(s), o, o1);
    }

    @Override
    public void trace(Marker marker, String s, Object... objects) {
        this.logger.trace(marker, msgWithContextId(s), objects);
    }

    @Override
    public void trace(Marker marker, String s, Throwable throwable) {
        this.logger.trace(marker, msgWithContextId(s), throwable);
    }

    @Override
    public boolean isInfoEnabled() {
        return this.logger.isInfoEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return this.logger.isDebugEnabled();
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return this.logger.isDebugEnabled(marker);
    }

    @Override
    public void debug(Marker marker, String s) {
        this.logger.debug(marker, msgWithContextId(s));
    }

    @Override
    public void debug(Marker marker, String s, Object o) {
        this.logger.debug(marker, msgWithContextId(s), o);
    }

    @Override
    public void debug(Marker marker, String s, Object o, Object o1) {
        this.logger.debug(marker, msgWithContextId(s), o, o1);
    }

    @Override
    public void debug(Marker marker, String s, Object... objects) {
        this.logger.debug(marker, msgWithContextId(s), objects);
    }

    @Override
    public void debug(Marker marker, String s, Throwable throwable) {
        this.logger.debug(marker, msgWithContextId(s), throwable);
    }

    @Override
    public boolean isWarnEnabled() {
        return this.logger.isWarnEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return this.logger.isErrorEnabled();
    }

    @Override
    public String getName() {
        return this.logger.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return this.logger.isTraceEnabled();
    }

    public String getContextId() {
        return this.contextId;
    }

    private String msgWithContextId(String msg) {
        if (this.contextId == null) {
            return msg;
        } else {
            return CTX + this.contextId  + CLOSING_BRACKET + "\t" + msg;
        }
    }
}
