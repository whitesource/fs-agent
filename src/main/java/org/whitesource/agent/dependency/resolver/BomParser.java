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
package org.whitesource.agent.dependency.resolver;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author eugen.horovitz
 */
public abstract class BomParser implements IBomParser{

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(BomParser.class);

    /* --- Public methods --- */

    public BomFile parseBomFile(String bomPath) {
        BomFile bomFile = null;
        String json = null;
        try (InputStream is = new FileInputStream(bomPath)) {
            json = IOUtils.toString(is);
        } catch (FileNotFoundException e) {
            logger.error("file Not Found: {}", bomPath);
        } catch (IOException e) {
            logger.error("error getting file : {}", e.getMessage());
        }

        if (json != null) {
            try {
                bomFile = parseBomFile(json, bomPath);
            } catch (Exception e) {
                logger.debug("Invalid file {}", bomPath);
            }
        }
        return bomFile;
    }

    /* --- Abstract methods --- */

    protected abstract BomFile parseBomFile(String jsonText, String localFileName);

    protected abstract String getFilename(String name, String version);
}