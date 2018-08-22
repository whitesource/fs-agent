package org.whitesource.agent.utils;

/**
 * @author raz.nitzan
 */
public class LoggerFactory {

    public static String contextId;

    public static LoggerFS getLogger(Class clazz) {
        return new LoggerFS(clazz, contextId);
    }

    public static LoggerFS getLogger(String name) {
        return new LoggerFS(name, contextId);
    }
}
