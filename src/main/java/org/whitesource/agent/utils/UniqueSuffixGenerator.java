package org.whitesource.agent.utils;

import org.whitesource.agent.Constants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * @author raz.nitzan
 */
public class UniqueSuffixGenerator {

    /* --- public static methods --- */

    public static String createUniqueSuffix(String name) {
        String creationDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String uuid = UUID.randomUUID().toString();
        return name + Constants.UNDERSCORE + creationDate + Constants.UNDERSCORE + uuid;
    }
}
