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
package org.whitesource.agent;

import org.apache.tools.ant.DirectoryScanner;

import java.io.File;

/**
 * A {@link org.apache.tools.ant.DirectoryScanner} for a single file.
 *
 * @author tom.shapira
 */
public class SingleFileScanner extends DirectoryScanner {

    /**
     * Exposes the {@link DirectoryScanner#isIncluded(String)} method to check if a single file should be included
     * in the scan.
     *
     * @param file for scanning
     * @return weather the file should be included or not
     */
    public boolean isIncluded(File file) {
        return isIncluded(file.getAbsolutePath());
    }
}
