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


import java.util.Arrays;
import java.util.List;

/**
 * This class contains all supported file extensions and creates regex and GLOB patterns for the directory scanner.
 *
 * @author tom.shapira
 */
public class ImpactAnalysisExtensionUtils {

    /* --- Static members --- */

    public static final String REGEX_PATTERN_PREFIX = ".*\\.";
    public static final String REGEX_OR = "|";
    public static final String GLOB_PATTERN_PREFIX = "**/*.";

    public static final List<String> JAVA_EXTENSIONS = Arrays.asList("jar");
    public static final List<String> JAVA_SCRIPT_EXTENSIONS = Arrays.asList("package.json");

    public static final String JAVA_EXTENSIONS_PATTERN;
    public static final String JAVA_SCRIPT_EXTENSIONS_PATTERN;

    static {
        JAVA_EXTENSIONS_PATTERN = initializeRegexPattern(JAVA_EXTENSIONS);
        JAVA_SCRIPT_EXTENSIONS_PATTERN = initializeRegexPattern(JAVA_SCRIPT_EXTENSIONS);
    }

    private static String initializeRegexPattern(List<String> extensions) {
        StringBuilder sb = new StringBuilder();
        for (String extension : extensions) {
            sb.append(REGEX_PATTERN_PREFIX);
            sb.append(extension);
            sb.append(REGEX_OR);
        }

        return sb.toString().substring(0, sb.toString().lastIndexOf(REGEX_OR));
    }

    private static String[] initializeGlobPattern(List<String> extensions) {
        String[] globPatterns = new String[extensions.size()];
        for (int i = 0; i < extensions.size(); i++) {
            globPatterns[i] = GLOB_PATTERN_PREFIX + extensions.get(i);
        }
        return globPatterns;
    }
}
