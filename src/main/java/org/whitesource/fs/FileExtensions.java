package org.whitesource.fs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

/**
 * Created by anna.rozin
 */
public class FileExtensions {


    public static final String FILE_EXTENSION_PREFIX_REGEX = ".*\\.";

    public static final String OR_REGEX = "|";

    public static final String BINARY_FILE_EXTENSION_REGEX;

    public static final Collection<String> GENERIC_RESOURCE_FILE_EXTENSIONS = new ArrayList<String>(
            Arrays.asList("air", "apk", "bz2", "deb", "drpm", "dmg", "egg", "exe",
                    "gem", "gzip", "jar", "msi", "nupkg", "rpm", "swc", "swf",
                    "tar.bz2", "tar.gz", "tgz", "pkg.tar.xz", "udeb", "whl", "zip"));

    public static final Collection<String> JAVA_FILE_EXTENSIONS = new ArrayList<String>(
            Arrays.asList("jar", "war", "aar", "ear", "car"));

    public static final Collection<String> DOT_NET_FILE_EXTENSIONS = new ArrayList<String>(
            Arrays.asList("dll", "exe"));

    static {

        StringBuilder sb = new StringBuilder();
        sb.append(buildRegexString(GENERIC_RESOURCE_FILE_EXTENSIONS));
        sb.append(OR_REGEX);
        sb.append(buildRegexString(JAVA_FILE_EXTENSIONS));
        sb.append(OR_REGEX);
        sb.append(buildRegexString(DOT_NET_FILE_EXTENSIONS));
        BINARY_FILE_EXTENSION_REGEX = sb.toString();
    }

    /* --- Private methods --- */

    private static String buildRegexString(Collection<String> extensions) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> iterator = extensions.iterator();
        while (iterator.hasNext()) {
            String extension = iterator.next();
            sb.append(FILE_EXTENSION_PREFIX_REGEX);
            sb.append(extension);
            if (iterator.hasNext()) {
                sb.append(OR_REGEX);
            }
        }
        return sb.toString();
    }

}
