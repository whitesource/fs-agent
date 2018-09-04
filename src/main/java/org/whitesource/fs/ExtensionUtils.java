/**
 * Copyright (C) 2016 WhiteSource Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.fs;


import org.whitesource.agent.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class contains all supported file extensions and creates regex and GLOB patterns for the directory scanner.
 *
 * @author tom.shapira
 */
public class ExtensionUtils {

    /* --- Static members --- */

    public static final List<String> SOURCE_EXTENSIONS = Arrays.asList(
            "c", "cc", "cp", "cpp", "cxx", "c\\+\\+", "h", "hh", "hpp", "hxx", "h\\+\\+", "m", "mm",  "pch", // C and C++
            "c#", "cs", "csharp",  // C#
            "go", "goc",  // GO
            "js", // JavaScript
            "pl", "plx", "pm", "ph", "cgi", "fcgi", "pod", "psgi", "al", "perl", "t", // PERL
            "pl6", "p6m", "p6l", "pm6", "nqp", "6pl", "6pm", "p6", // PERL6
            "php", // PHP
            "py", // Python
            "rb", // Ruby
            "swift", // Swift
            "java", // Java
            "clj", "cljx", "cljs", "cljc"); // Clojure
    public static final List<String> BINARY_EXTENSIONS = Arrays.asList("jar", "egg", "tar.gz", "tgz", "zip", "whl", "gem",
            "apk", "air", "dmg", "exe", "gem", "gzip", "msi", "nupkg", "swc", "swf", "tar.bz2", "pkg.tar.xz", "(u)?deb", "(a)?rpm");
    public static final List<String> ARCHIVE_EXTENSIONS = Arrays.asList("war", "ear", "zip", "whl", "tar.gz", "tgz", "tar");

    public static final String SOURCE_FILE_PATTERN;
    public static final String BINARY_FILE_PATTERN;
    public static final String ARCHIVE_FILE_PATTERN;
    public static final String[] INCLUDES;
    public static final String[] EXCLUDES = new String[] { "**/*sources.jar", "**/*javadoc.jar", "**/tests/**" };
    public static final String[] ARCHIVE_INCLUDES;
    public static final String[] ARCHIVE_EXCLUDES = new String[] { "**/*sources.jar", "**/*javadoc.jar", "**/tests/**" };

    static {
        SOURCE_FILE_PATTERN = initializeRegexPattern(SOURCE_EXTENSIONS);
        BINARY_FILE_PATTERN = initializeRegexPattern(BINARY_EXTENSIONS);
        ARCHIVE_FILE_PATTERN = initializeRegexPattern(ARCHIVE_EXTENSIONS);
        List<String> allExtensions = new ArrayList<>();
        allExtensions.addAll(SOURCE_EXTENSIONS);
        allExtensions.addAll(BINARY_EXTENSIONS);
        INCLUDES = initializeGlobPattern(allExtensions);
        ARCHIVE_INCLUDES = initializeGlobPattern(ARCHIVE_EXTENSIONS);
    }

    private static String initializeRegexPattern(List<String> extensions) {
        StringBuilder sb = new StringBuilder();
        for (String extension : extensions) {
            sb.append(Constants.REGEX_PATTERN_PREFIX);
            sb.append(extension);
            sb.append(Constants.PIPE);
        }
        return sb.toString().substring(0, sb.toString().lastIndexOf(Constants.PIPE));
    }

    private static String[] initializeGlobPattern(List<String> extensions) {
        String[] globPatterns = new String[extensions.size()];
        for (int i = 0; i < extensions.size(); i++) {
            globPatterns[i] = Constants.GLOB_PATTERN_PREFIX + Constants.DOT +  extensions.get(i);
        }
        return globPatterns;
    }
}
