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

import org.whitesource.agent.api.model.DependencyType;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by eugen.horovitz on 6/26/2017.
 */
public abstract class AbstractDependencyResolver {

   /* --- Static members --- */

   private static String BACK_SLASH = "\\";
   private static String FORWARD_SLASH = "/";

   /* --- Abstract methods --- */

   protected abstract ResolutionResult resolveDependencies(String parentScanFolder, String suspectedFolder, List<String> fullPathIndicators);

   protected abstract String getBomPattern();

   protected abstract DependencyType getDependencyType();

   /* --- Protected methods --- */

   protected List<String> normalizeLocalPath(String parentFolder, String topFolderFound, Collection<String> excludes) {
      String normalizedRoot = new File(parentFolder).getPath();
      if (normalizedRoot.equals(topFolderFound)) {
         topFolderFound = topFolderFound
                 .replace(normalizedRoot, "")
                 .replace(BACK_SLASH, FORWARD_SLASH);
      } else {
         topFolderFound = topFolderFound
                 .replace(parentFolder, "")
                 .replace(BACK_SLASH, FORWARD_SLASH);
      }

      if (topFolderFound.length() > 0)
         topFolderFound = topFolderFound.substring(1, topFolderFound.length()) + FORWARD_SLASH;

      String finalRes = topFolderFound;
      return excludes.stream().map(exclude -> finalRes + exclude).collect(Collectors.toList());
   }
}