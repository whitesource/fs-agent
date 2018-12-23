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

import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyType;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author eugen.horovitz
 */
public abstract class AbstractDependencyResolver {

    /* --- Static Members --- */

    protected static final String GLOB_PATTERN = "**/";
    protected static final String fileSeparator = System.getProperty(Constants.FILE_SEPARATOR);
    protected IBomParser bomParser;

    /* --- Abstract methods --- */

    protected abstract ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) throws FileNotFoundException;

    protected abstract Collection<String> getExcludes();

    protected abstract DependencyType getDependencyType();

    protected abstract String getDependencyTypeName();

    protected abstract String[] getBomPattern();

    public abstract Collection<String> getManifestFiles();

    protected abstract Collection<String> getLanguageExcludes();

    protected Collection<String> getRelevantScannedFolders(Collection<String> scannedFolders) {
        if (scannedFolders == null) {
            return new HashSet<>();
        }
        if (scannedFolders.isEmpty()) {
            return scannedFolders;
        }
        Collection<String> foldersToRemove = new HashSet<>();
        for(String folder : scannedFolders) {
            for (String subFolder : scannedFolders) {
                if (subFolder.contains(folder) && !subFolder.equals(folder)) {
                    foldersToRemove.add(subFolder);
                }
            }
        }
        scannedFolders.removeAll(foldersToRemove);
        return scannedFolders;
    }

    protected boolean printResolvedFolder() {
        return true;
    }

    public abstract Collection<String> getSourceFileExtensions();

    /* --- Protected methods --- */
    protected List<String> extensionPattern(List<String> extensions) {
        List<String> extensionsPatternStr = new LinkedList<>();
        for (String extension : extensions) {
            extensionsPatternStr.add(Constants.PATTERN + extension);
        }
        return extensionsPatternStr;
    }
    protected List<String> normalizeLocalPath(String parentFolder, String topFolderFound, Collection<String> excludes, String folderToIgnore) {
        String normalizedRoot = new File(parentFolder).getPath();
        if (normalizedRoot.equals(topFolderFound)) {
            topFolderFound = topFolderFound
                    .replace(normalizedRoot, Constants.EMPTY_STRING)
                    .replace(Constants.BACK_SLASH, Constants.FORWARD_SLASH);
        } else {
            topFolderFound = topFolderFound
                    .replace(parentFolder, Constants.EMPTY_STRING)
                    .replace(Constants.BACK_SLASH, Constants.FORWARD_SLASH);
        }

        if (topFolderFound.length() > 0)
            topFolderFound = topFolderFound.substring(1, topFolderFound.length()) + Constants.FORWARD_SLASH;

        String finalRes = topFolderFound;
        if (StringUtils.isBlank(folderToIgnore)) {
            return excludes.stream().map(exclude -> finalRes + exclude).collect(Collectors.toList());
        } else {
            return excludes.stream().map(exclude -> finalRes + folderToIgnore + Constants.FORWARD_SLASH + exclude).collect(Collectors.toList());
        }
    }
}