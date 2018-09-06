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
package org.whitesource.agent.dependency.resolver.bower;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.npm.NpmBomParser;

import java.text.MessageFormat;

/**
 * This class represents an Bower .bower.json file .
 * When missing bower.json is parsed
 *
 * @author eugen.horovitz
 */
public class BowerBomParser extends NpmBomParser {

    /* --- Static members --- */

    public static final String RESOLUTION = "_resolution";
    public static final String BOWER_PACKAGE_FILENAME_FORMAT = "{0}-{1}";
    private final Logger logger = LoggerFactory.getLogger(NpmBomParser.class);

    /* --- Overridden methods --- */

    @Override
    protected String getVersion(JSONObject json, String fileName) {
        String version = Constants.EMPTY_STRING;
        if (json.has(RESOLUTION)) {
            JSONObject jObj = json.getJSONObject(RESOLUTION);
            if (jObj.has(Constants.TAG)) {
                return jObj.getString(Constants.TAG);
            }
            logger.debug("version not found in file {}", fileName);
            return Constants.EMPTY_STRING;
        }
        return version;
    }

    @Override
    protected String getFilename(String name, String version) {
        return MessageFormat.format(BOWER_PACKAGE_FILENAME_FORMAT, name, version);
    }
}