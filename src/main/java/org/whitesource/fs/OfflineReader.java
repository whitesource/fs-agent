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
package org.whitesource.fs;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.dispatch.UpdateInventoryRequest;

import java.io.*;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class OfflineReader {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(OfflineReader.class);
    private static final String UTF_8 = "UTF-8";

    public Collection<UpdateInventoryRequest> getAgentProjectsFromRequests(List<String> offlineRequestFiles){//, FSAConfiguration fsaConfiguration) {
        Collection<UpdateInventoryRequest> projects = new LinkedList<>();

        List<File> requestFiles = new LinkedList<>();
        if (offlineRequestFiles != null) {
            for (String requestFilePath : offlineRequestFiles) {
                if (StringUtils.isNotBlank(requestFilePath)) {
                    requestFiles.add(new File(requestFilePath));
                }
            }
        }
        if (!requestFiles.isEmpty()) {
            for (File requestFile : requestFiles) {
                if (!requestFile.isFile()) {
                    logger.warn("'{}' is a folder. Enter a valid file path, folder is not acceptable.", requestFile.getName());
                    continue;
                }
                Gson gson = new Gson();
                UpdateInventoryRequest updateRequest;
                logger.debug("Converting offline request to JSON");
                try(FileReader fileReader = new FileReader(requestFile);
                    JsonReader jsonReader = new JsonReader(fileReader)){
                    updateRequest = gson.fromJson(jsonReader, new TypeToken<UpdateInventoryRequest>() {
                    }.getType());
                    logger.info("Reading information from request file {}", requestFile);
                    projects.add(updateRequest);
                } catch (JsonSyntaxException e) {
                    // try to decompress file content
                    try {
                        logger.debug("Decompressing zipped offline request");
                        String fileContent = decompress(requestFile);
                        logger.debug("Converting offline request to JSON");
                        updateRequest = gson.fromJson(fileContent, new TypeToken<UpdateInventoryRequest>() {}.getType());
                        logger.info("Reading information from request file {}", requestFile);
                        projects.add(updateRequest);
                    } catch (IOException ioe) {
                        logger.warn("Error parsing request: " + ioe.getMessage());
                    } catch (JsonSyntaxException jse) {
                        logger.warn("Error parsing request: " + jse.getMessage());
                    }
                } catch (FileNotFoundException e) {
                    logger.warn("Error parsing request: " + e.getMessage());
                } catch (IOException e) {
                    logger.warn("Error parsing request: " + e.getMessage());
                }
            }
        }
        return projects;
    }

    /* --- Private methods --- */

    private static String decompress(File file) throws IOException {
        if (file == null || !file.exists()) {
            return Constants.EMPTY_STRING;
        }

        byte[] bytes = Base64.getDecoder().decode(IOUtils.toByteArray(new FileInputStream(file)));
        GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
        BufferedReader bf = new BufferedReader(new InputStreamReader(gzipInputStream, UTF_8));
        StringBuilder outStr = new StringBuilder(Constants.EMPTY_STRING);
        String line;
        while ((line = bf.readLine()) != null) {
            outStr.append(line);
        }
        return outStr.toString();
    }
}
