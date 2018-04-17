package org.whitesource.agent.dependency.resolver.go;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.io.*;
import java.util.*;

public class GoDependencyResolver extends AbstractDependencyResolver {

    private final Logger logger = LoggerFactory.getLogger(GoDependencyResolver.class);

    private static final String GOPKG_LOCK = "Gopkg.lock";
    protected static final String GO_EXTENTION = ".go";
    private static final List<String> GO_SCRIPT_EXTENSION = Arrays.asList(".lock", GO_EXTENTION);

    private GoCli goCli;
    private boolean ignoreScriptFiles;

    public GoDependencyResolver(boolean ignoreScriptFiles){
        super();
        this.goCli = new GoCli();
        this.ignoreScriptFiles = ignoreScriptFiles;
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        List<DependencyInfo> dependencies = collectDependencies(topLevelFolder);
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        if (ignoreScriptFiles){
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
        return GLOB_PATTERN + "*" + GOPKG_LOCK;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    private List<DependencyInfo> collectDependencies(String rootDirectory) {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        File goPkgLock = new File(rootDirectory + fileSeparator + GOPKG_LOCK);
        if (goPkgLock.isFile()){
            if (goCli.runCmd(rootDirectory,goCli.getGoCommandParams(GoCli.GO_ENSURE))) {
                dependencyInfos.addAll(parseGopckLock(goPkgLock));
            } else {
               logger.error("Can't run 'dep ensure' command.  Make sure no files from the 'vendor' folder are in use.");
            }
        } else {
            logger.error("Can't find Gopkg.lock file.  Please run `dep init` command");
        }
        return dependencyInfos;
    }

    private List<DependencyInfo> parseGopckLock(File goPckLock){
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        try {
            FileReader fileReader = new FileReader(goPckLock);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String currLine;
            boolean insideProject = false;
            DependencyInfo dependencyInfo = null;
            String groupdId = "";
            String artifactId = "";
            String version = "";
            String commit = "";
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
                            if (name.contains("/")) {
                                String[] split = name.split("/");
                                groupdId = split[1];
                                artifactId = name.substring(name.indexOf(split[2]));
                            } else {
                                artifactId = name;
                            }
                            dependencyInfo.setGroupId(groupdId);
                            dependencyInfo.setArtifactId(artifactId);
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
}
