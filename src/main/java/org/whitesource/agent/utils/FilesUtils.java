/**
 * Copyright (C) 2017 WhiteSource Ltd.
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
package org.whitesource.agent.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author eugen.horovitz
 */
public class FilesUtils {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(FilesUtils.class);

    /* --- Static methods --- */

    public static List<Path> getSubDirectories(String directory) {
        String[] files;
        try {
            File file = new File(directory);
            files = file.list((current, name) -> new File(current, name).isDirectory());
            if (files == null) {
                logger.info("Error getting sub directories from: " + directory);
                files = new String[0];
            }
        } catch (Exception ex) {
            logger.info("Error getting sub directories from: " + directory, ex);
            files = new String[0];
        }
        return Arrays.stream(files).map(subDir -> Paths.get(directory, subDir)).collect(Collectors.toList());
    }
}
