/**
 * Copyright (C) 2014 WhiteSource Ltd.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.agent;

/**
 * Property keys for the whitesource-docker-agent.configuration file.
 *
 * @author annarozin
 */
public final class Constants {

    public static final String NEW_LINE             = System.lineSeparator();
    public static final String JAVA_NETWORKING      = "java.net";
    public static final String FILE_SEPARATOR       = "file.separator";
    public static final String FALSE                = "false";
    public static final String TRUE                 = "true";
    public static final String TAG                  = "tag";
    public static final String VERSION              = "version";
    public static final String RESOLUTION           = "_resolution";
    public static final String NAME                 = "name";
    public static final String MISSING              = "missing";
    public static final String DEPENDENCIES         = "dependencies";
    public static final String SRC                  = "src";
    public static final String CMD                  = "cmd";
    public static final String OS_NAME              = "os.name";
    public static final String WIN                  = "win";
    public static final String JS_EXTENSION         = ".js";
    public static final String PACKAGES             = "packages";
    public static final String INSTALL              = "install";
    public static final String MAVEN                = "maven";
    public static final String HTML                 = "html";
    public static final String HTM                  = "htm";
    public static final String SHTML                = "shtml";
    public static final String XHTML                = "xhtml";
    public static final String JSP                  = "jsp";
    public static final String ASP                  = "asp";
    public static final String DO                   = "do";
    public static final String ASPX                 = "aspx";
    public static final String WINDOWS              = "Windows";
    public static final String GRADLE_WRAPPER       = "wrapper";
    public static final String GRADLE               = "gradle";
    public static final String POM                  = "pom";
    public static final String JAR                  = "jar";
    public static final String DOT                  = ".";
    public static final String DIRECTORY            = "d";
    public static final String BACK_SLASH           = "\\";
    public static final String FORWARD_SLASH        = "/";
    public static final String WHITESPACE           = " ";
    public static final String EMPTY_STRING         = "";
    public static final String COLON                = ":";
    public static final String AT                   = "@";
    public static final String PLUS                 = "+";
    public static final String DASH                 = "-";
    public static final String PATTERN              = "**/*";
    public static final String COMMA                = ",";
    public static final String PIPE                 = "|";
    public static final String REGEX_PATTERN_PREFIX = ".*\\.";
    public static final String GLOB_PATTERN_PREFIX  = "**/*";
    public static final String GLOB_PATTERN         = ".*.*";
    public static final String EQUALS               = "=";
    public static final String POUND                = "#";
    public static final String QUOTATION_MARK       = "\"";
    public static final String APOSTROPHE           = "'";
    public static final String HTTP                 = "http";
    public static final String HTTPS                = "https";
    public static final String UTF8                 = "UTF-8";
    public static final String DLL                  = ".dll";
    public static final String EXE                  = ".exe";
    public static final String NUPKG                = ".nupkg";
    public static final String CS                   = ".cs";
    public static final String VAR                  = "var";
    public static final String LIB                  = "lib";
    public static final String YUM_DB               = "yumdb";
    public static final String YUM                  = "yum";
    public static final String PYTHON_REQUIREMENTS  = "requirements.txt";
    public static final String PIPFILE              = "Pipfile";
    public static final String TXT_EXTENSION        = ".txt";
    public static final String SETUP_PY             = "setup.py";
    public static final String JAR_EXTENSION        = ".jar";
    public static final int MAX_EXTRACTION_DEPTH    = 7;
    public static final int COMMENT_MAX_LENGTH      = 1000;
    public static final int ZERO                    = 0;
    public static final int ONE                     = 1;
    public static final String BUILD_GRADLE         = "build.gradle";
    public static final String COPY_DEPENDENCIES    = "copyDependencies";
    public static final String UNDERSCORE           = "_";
    public static final char QUESTION_MARK          = '?';
    public static final char WHITESPACE_CHAR        = ' ';
    public static final char OPEN_BRACKET           = '(';
    public static final char CLOSE_BRACKET          = ')';
    public static final char EQUALS_CHAR            = '=';
    public static final char OPEN_SQUARE_BRACKET    = '[';
    public static final char CLOSE_SQUARE_BRACKET   = ']';
    public static final String DOUBLE_EQUALS        = "==";
    public static final char SEMI_COLON             = ';';
    public static final String DOLLAR               = "$";
    public static final String OPEN_CURLY_BRACKET = "{";
    public static final String CLOSE_CURLY_BRACKET = "}";

    public static final int MAX_NUMBER_OF_DEPENDENCIES = 1000000;

    public static final String MAP_LOG_NAME = "org.whitesource";
    public static final String MAP_APPENDER_NAME = "collectToMap";
    public static final String HELP_ARG1 = "-help";
    public static final String HELP_ARG2 = "-h";
    public static final String TARGET = "target";
    public static final String BUILD = "build";
    public static final String NONE = "None";
    public static final String LIBS = "libs";
    public static final String USER_HOME = "user.home";
}