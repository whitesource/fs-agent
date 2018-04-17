package org.whitesource.agent.dependency.resolver.go;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.io.*;
import java.util.*;

public class GoDependencyResolver extends AbstractDependencyResolver {

    private static final String DEPS = "Deps";
    private static final String REV = "Rev";
    private static final String COMMENT = "Comment";
    private static final String IMPORT_PATH = "ImportPath";
    private final Logger logger = LoggerFactory.getLogger(GoDependencyResolver.class);

    private static final String GOPKG_LOCK = "Gopkg.lock";
    private static final String GODEPS_JSON = "Godeps.json";
    private static final String GO_EXTENTION = ".go";
    private static final List<String> GO_SCRIPT_EXTENSION = Arrays.asList(".lock", ".json", GO_EXTENTION);

    private GoCli goCli;
    private boolean ignoreSourceFiles;
    private GoDependencyManager goDependencyManager;

    public GoDependencyResolver(boolean ignoreSourceFiles, GoDependencyManager goDependencyManager){
        super();
        this.goCli = new GoCli();
        this.ignoreSourceFiles = ignoreSourceFiles;
        this.goDependencyManager = goDependencyManager;
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        List<DependencyInfo> dependencies = collectDependencies(topLevelFolder);
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        if (ignoreSourceFiles){
            excludes.add(GLOB_PATTERN + "*" + GO_EXTENTION);
        }
        return excludes;
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return GO_SCRIPT_EXTENSION;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.GO;
    }

    @Override
    protected String getBomPattern() {
        switch (goDependencyManager){
            case DEP:
                return GLOB_PATTERN + "*" + GOPKG_LOCK;
            case GO_DEP:
                return GLOB_PATTERN + "*" + GODEPS_JSON;
        }
        return "";
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    private List<DependencyInfo> collectDependencies(String rootDirectory) {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        String error = null;
        if (goDependencyManager != null) {
            try {
                switch (goDependencyManager) {
                    case DEP:
                        collectDepDependencies(rootDirectory, dependencyInfos);
                        break;
                    case GO_DEP:
                        collectGoDepDependencies(rootDirectory, dependencyInfos);
                        break;
                    default:
                        error = "The selected dependency manager - " + goDependencyManager.getType() + " - is not supported.";
                }
            } catch (Exception e) {
                error = e.getMessage();
            }
        } else {
            error = "No valid dependency manager was defined";
        }
        if (error != null){
            logger.error(error);
        }
        return dependencyInfos;
    }

    private void collectDepDependencies(String rootDirectory, List<DependencyInfo> dependencyInfos) throws Exception {
        File goPkgLock = new File(rootDirectory + fileSeparator + GOPKG_LOCK);
        String error = "";
        if (goPkgLock.isFile()){
            if (goCli.runCmd(rootDirectory,goCli.getGoCommandParams(GoCli.GO_ENSURE))) {
                dependencyInfos.addAll(parseGopckLock(goPkgLock));
                return;
            } else {
               error = "Can't run 'dep ensure' command.  Make sure no files from the 'vendor' folder are in use.";
            }
        } else {
            error = "Can't find Gopkg.lock file.  Please run `dep init` command";
        }
        throw new Exception(error);
    }

    private List<DependencyInfo> parseGopckLock(File goPckLock){
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        try {
            FileReader fileReader = new FileReader(goPckLock);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String currLine;
            boolean insideProject = false;
            DependencyInfo dependencyInfo = null;
            String version;
            String commit;
            DependencyGroupArtifact dependencyGroupArtifact;
            int firstIndex, lastIndex;
            while ((currLine = bufferedReader.readLine()) != null){
                if (insideProject) {
                    if (currLine.isEmpty()){
                        insideProject = false;
                        if (dependencyInfo != null) {
                            dependencyInfos.add(dependencyInfo);
                        }
                    } else {
                        if (currLine.contains("name = ")){
                            firstIndex = currLine.indexOf("\"");
                            lastIndex = currLine.lastIndexOf("\"");
                            String name = currLine.substring(firstIndex + 1, lastIndex);
                            dependencyGroupArtifact = getGroupAndArtifact(name);
                            dependencyInfo.setGroupId(dependencyGroupArtifact.getGroupId());
                            dependencyInfo.setArtifactId(dependencyGroupArtifact.getArtifactId());
                        } else if (currLine.contains("version = ")){
                            firstIndex = currLine.indexOf("\"");
                            lastIndex = currLine.lastIndexOf("\"");
                            version = currLine.substring(firstIndex + 1, lastIndex);
                            dependencyInfo.setVersion(version);
                        } else if (currLine.contains("revision = ")){
                            firstIndex = currLine.indexOf("\"");
                            lastIndex = currLine.lastIndexOf("\"");
                            commit = currLine.substring(firstIndex + 1, lastIndex);
                            dependencyInfo.setCommit(commit);
                        }
                    }
                } else if (currLine.equals("[[projects]]")){
                    dependencyInfo = new DependencyInfo();
                    insideProject = true;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dependencyInfos;
    }

    private void collectGoDepDependencies(String rootDirectory, List<DependencyInfo> dependencyInfos) throws Exception {
        File goDepJson = new File(rootDirectory + fileSeparator + GODEPS_JSON);
        String error;
        if (goDepJson.isFile()){
            dependencyInfos.addAll(parseGoDeps(goDepJson));
            return;
        } else {
            error = "Can't find Godeps.json file.  Please run 'godep save' command";
        }
        throw new Exception(error);
    }

    private List<DependencyInfo> parseGoDeps(File goDeps) throws FileNotFoundException {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        JsonParser parser = new JsonParser();
        JsonElement element = parser.parse(new FileReader(goDeps.getPath()));
        if (element.isJsonObject()){
            JsonArray deps = element.getAsJsonObject().getAsJsonArray(DEPS);
            DependencyInfo dependencyInfo;
            String groupId;
            String artifactId;
            for (int i = 0; i < deps.size(); i++){
                dependencyInfo = new DependencyInfo();
                JsonObject dep = deps.get(i).getAsJsonObject();
                String importPath = dep.get(IMPORT_PATH).getAsString();
                DependencyGroupArtifact dependencyGroupArtifact = getGroupAndArtifact(importPath);
                groupId = dependencyGroupArtifact.getGroupId();
                artifactId = dependencyGroupArtifact.getArtifactId();
                dependencyInfo.setGroupId(groupId);
                dependencyInfo.setArtifactId(artifactId);
                dependencyInfo.setCommit(dep.get(REV).getAsString());
                JsonElement commentElement = dep.get(COMMENT);
                if (commentElement != null){
                    String comment = commentElement.getAsString();
                    if (comment.indexOf("-") > -1) {
                        comment = comment.substring(0, comment.indexOf("-"));
                    }
                    dependencyInfo.setVersion(comment);
                }
                dependencyInfos.add(dependencyInfo);
            }
        }
        return dependencyInfos;
    }

    private DependencyGroupArtifact getGroupAndArtifact(String name){
        String groupId = "";
        String artifactId = "";
        if (name.contains("/")) {
            String[] split = name.split("/");
            groupId = split[1];
            artifactId = name.substring(name.indexOf(split[2]));
        } else {
            artifactId = name;
        }
        return new DependencyGroupArtifact(groupId,artifactId);
    }

    private class DependencyGroupArtifact {
        private String groupId;
        private String artifactId;

        public DependencyGroupArtifact(String groupdId, String artifactId) {
            this.groupId = groupdId;
            this.artifactId = artifactId;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }
    }
}
