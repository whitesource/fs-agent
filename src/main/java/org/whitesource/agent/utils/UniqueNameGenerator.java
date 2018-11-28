package org.whitesource.agent.utils;

import org.whitesource.agent.Constants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * @author raz.nitzan
 */
public class UniqueNameGenerator {

    /* --- public static methods --- */

    public static String createUniqueName(String name, String extension) {
        String creationDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String uuid = UUID.randomUUID().toString();
        return name + Constants.UNDERSCORE + creationDate + Constants.UNDERSCORE + uuid + extension;
    }
}
