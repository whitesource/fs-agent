package org.whitesource.agent.utils;

import org.whitesource.agent.Constants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * @author raz.nitzan
 */
public class UniqueNamesGenerator {

    /* --- public static methods --- */

    public static String createUniqueName(String name, String extension) {
        if (name == null) {
            name = Constants.EMPTY_STRING;
        }
        if (extension == null) {
            extension = Constants.EMPTY_STRING;
        }
        String creationDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String uuid = UUID.randomUUID().toString();
        return name + Constants.UNDERSCORE + creationDate + Constants.UNDERSCORE + uuid + extension;
    }
}
