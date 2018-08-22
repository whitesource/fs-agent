/**
 * Copyright (C) 2014 WhiteSource Ltd.
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
package org.whitesource.fs.configuration;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Author: eugen.horovitz
 */
public class ScmRepositoriesParser {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(ScmRepositoriesParser.class);
    public static final String URL = "url";
    public static final String BRANCH = "branch";
    public static final String SCM_REPOSITORIES = "scmRepositories";

    /* --- Static methods --- */

    public Collection<ScmConfiguration> parseRepositoriesFile(String fileName, String scmType, String scmPpk, String scmUser, String scmPassword) {
        try (InputStream is = new FileInputStream(fileName)) {
            String jsonText = IOUtils.toString(is);
            JSONObject json = new JSONObject(jsonText);
            JSONArray arr = json.getJSONArray(SCM_REPOSITORIES);

            List<ScmConfiguration> configurationList = new LinkedList<>();
            arr.forEach(scm -> {
                JSONObject obj = (JSONObject) scm;
                String url = obj.getString(URL);
                String branch = obj.getString(BRANCH);
                String tag = obj.getString(Constants.TAG);
                configurationList.add(new ScmConfiguration(scmType, scmUser, scmPassword, scmPpk, url, branch, tag,
                        null, false ,1));
            });

            return configurationList;
        } catch (FileNotFoundException e) {
            logger.error("file Not Found: {}", fileName);
        } catch (IOException e) {
            logger.error("error getting file : {}", e.getMessage());
        }
        return null;
    }
}
