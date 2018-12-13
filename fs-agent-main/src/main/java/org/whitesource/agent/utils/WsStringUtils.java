package org.whitesource.agent.utils;

import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.fs.FSAConfigProperty;
import org.whitesource.fs.WsSecret;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;

public class WsStringUtils {

    private static final Logger logger = LoggerFactory.getLogger(WsStringUtils.class);

    public static String toString(Object obj) {
        StringBuilder result = new StringBuilder();

        Field[] fields = obj.getClass().getDeclaredFields();

        for (Field field : fields) {
            if (field.isAnnotationPresent(FSAConfigProperty.class)) {
                field.setAccessible(true);
                try {
                    Object value = field.get(obj);

                    Class fieldType = field.getType();

                    if (value == null) {
                        result.append(field.getName() + Constants.EQUALS + Constants.EMPTY_STRING + Constants.NEW_LINE);
                    } else {
                        if(field.isAnnotationPresent(WsSecret.class)){
                          result.append(field.getName() + Constants.EQUALS + field.getAnnotation(WsSecret.class).value() + Constants.NEW_LINE);
                        } else if (fieldType.isArray()){
                            result.append(field.getName() + Constants.EQUALS + Arrays.toString((Object[])value) + Constants.NEW_LINE);
                        } else if (fieldType.isPrimitive() || fieldType.isAssignableFrom(String.class)
                                || fieldType.isAssignableFrom(Boolean.class) || Collection.class.isAssignableFrom(fieldType)){
                            result.append(field.getName() + Constants.EQUALS + value + Constants.NEW_LINE);
                        } else {
                            result.append(value.toString());
                        }
                    }
                } catch (IllegalAccessException e) {
                    logger.debug("Failed in WsStringUtils toString - {}. Exception: {}", e.getMessage(), e.getStackTrace());
                }
            }
        }
        return result.toString();
    }
}