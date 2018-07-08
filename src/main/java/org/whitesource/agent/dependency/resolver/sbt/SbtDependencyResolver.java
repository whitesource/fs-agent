package org.whitesource.agent.dependency.resolver.sbt;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.utils.Cli;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.utils.FilesUtils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class SbtDependencyResolver extends AbstractDependencyResolver {

    private static final String BUILD_SBT = "build.sbt";
    private static final String SCALA = "scala";
    private static final String SBT = "sbt";
    private static final String SCALA_EXTENSION = Constants.DOT + SCALA;
    private static final List<String> SCALA_SCRIPT_EXTENSION = Arrays.asList(SCALA_EXTENSION, Constants.DOT + SBT);
    private static final String COMPILE = "compile";
    protected static final String TARGET = "target";
    protected static final String RESOLUTION_CACHE = "resolution-cache";
    protected static final String REPORTS = "reports";
    protected static final String SUCCESS = "success";

    private boolean sbtAggregateModules;
    private boolean dependenciesOnly;

    private final Logger logger = LoggerFactory.getLogger(SbtDependencyResolver.class);

    public SbtDependencyResolver(boolean sbtAggregateModules, boolean dependenciesOnly){
        this.sbtAggregateModules = sbtAggregateModules;
        this.dependenciesOnly = dependenciesOnly;
        this.bomParser = new SbtBomParser();
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        Collection<AgentProjectInfo> projects = new ArrayList<>();
        List<File> xmlFiles = findXmlReport(topLevelFolder);
        if (xmlFiles.size()== 0){
            xmlFiles = loadXmlReport(topLevelFolder);
        }
        if (xmlFiles != null){
            for (File xmlFile :xmlFiles){
                projects.add(parseXmlReport(xmlFile));
            }
        }
        Set<String> excludes = new HashSet<>();
        Map<AgentProjectInfo, Path> projectInfoPathMap = projects.stream().collect(Collectors.toMap(projectInfo -> projectInfo, projectInfo -> {
            if (dependenciesOnly) {
                excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder.toString(), SCALA_SCRIPT_EXTENSION, null));
            }
            return Paths.get(topLevelFolder);
        }));

        ResolutionResult resolutionResult;
        if (!sbtAggregateModules) {
            resolutionResult = new ResolutionResult(projectInfoPathMap, excludes, getDependencyType(), topLevelFolder);
        } else {
            resolutionResult = new ResolutionResult(projectInfoPathMap.keySet().stream()
                    .flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList()), excludes, getDependencyType(), topLevelFolder);
        }
        return resolutionResult;
    }

    /* looking for an xml file ending with '-compile.xml', which should be either in 'target/scala-{version number}/resolution-cache/reports'
     or 'target/scala-{version number}/sbt-{version-number}/resolution-cache/reports'.
     There are some cases where 2 files inside that folder end with '-compile.xml'.  in such case, the way to find the relevant is if its name
     contains the scala-version (which is part of the scala folder name), and that's relevant only if the xml file isn't inside 'sbt-{}' folder.
     */
    private List<File> findXmlReport(String folderPath){
        FilesScanner filesScanner = new FilesScanner();
        String[] includes = {"**" + fileSeparator + "target" + fileSeparator + "**" + fileSeparator + "" + RESOLUTION_CACHE + fileSeparator + REPORTS + fileSeparator + "*-compile.xml"};
        String[] excludes = {"**" + fileSeparator + "project" + fileSeparator + "**"};
        String[] directoryContent = filesScanner.getDirectoryContent(folderPath, includes, excludes, false, false);
        List<File> files = new LinkedList<>();
        for (String filePath : directoryContent){
            boolean add = true;
            File reportFile = new File(folderPath + fileSeparator + filePath);
            for (File file : files){
                if (file.getParent().equals(reportFile.getParent())){
                    add = false;
                    if (reportFile.getName().length() < file.getName().length()){
                        files.remove(file);
                        files.add(reportFile);
                        break;
                    }
                }
            }
            if (add){
                files.add(reportFile);
            }
        }
        return files;
    }

    // creating the xml report using 'sbt "compile"' command
    private List<File> loadXmlReport(String folderPath){
        Cli cli = new Cli();
        List<String> compileOutput = cli.runCmd(folderPath, cli.getCommandParams(SBT, COMPILE));
        if (compileOutput != null){
            if (compileOutput.get(compileOutput.size()-1).contains(SUCCESS)){
                return findXmlReport(folderPath);
            }
        }
        logger.warn("Can't run '{} {}'", SBT, COMPILE);
        return new LinkedList<>();
    }

    private AgentProjectInfo parseXmlReport(File xmlReportFile){
        AgentProjectInfo agentProjectInfo = new AgentProjectInfo();
        Serializer serializer = new Persister();
        Map<String, DependencyInfo> parentsMap = new HashMap<>();
        Map<String, List<String>> childrenMap = new HashMap<>();
        try {
            IvyReport ivyReport = serializer.read(IvyReport.class, xmlReportFile);
            // using these properties to identify root dependencies (having the project's root as their parent)
            String projectGroupId = ivyReport.getInfo().getGroupId();
            String projectArtifactId = ivyReport.getInfo().getArtifactId();
            String projectVersion = ivyReport.getInfo().getVersion();
            agentProjectInfo.setCoordinates(new Coordinates(projectGroupId, projectArtifactId, projectVersion));
            for (Module dependency : ivyReport.getDependencies()) {
                String groupId = dependency.getGroupId();
                String artifactId = dependency.getArtifactId();
                for (Revision revision : dependency.getRevisions()) {
                    // making sure this dependency's version is used (and not over-written by a newer version)
                    if (!revision.isIgnored()) {
                        String version = revision.getVersion();
                        //Artifact artifact = revision.getArtifacts().get(0); // resolving path to jar file
                        if (revision.getArtifacts().size() > 0 && revision.getArtifacts().get(0) != null) {
                            File jarFile = new File(revision.getArtifacts().get(0).getPathToJar());
                            if (jarFile.isFile()) {
                                String sha1 = ChecksumUtils.calculateSHA1(jarFile);
                                if (sha1 != null) {
                                    DependencyInfo dependencyInfo = new DependencyInfo(groupId, artifactId, version);
                                    dependencyInfo.setSha1(sha1);
                                    dependencyInfo.setDependencyType(DependencyType.MAVEN);
                                    dependencyInfo.setFilename(jarFile.getName());
                                    dependencyInfo.setSystemPath(jarFile.getPath());

                                    String extension = FilesUtils.getFileExtension(jarFile.getName());
                                    dependencyInfo.setType(extension);

                                    String dependencyName = groupId + Constants.COLON + artifactId + Constants.COLON + version;
                                    parentsMap.put(dependencyName, dependencyInfo);
                                    for (Caller parent : revision.getParentsList()) {
                                        String parentGroupId = parent.getGroupId();
                                        String parentArtifactId = parent.getArtifactId();
                                        // if this dependency's parent is the root - no need to add is as a child...
                                        if (parentGroupId.equals(projectGroupId) == false && parentArtifactId.equals(projectArtifactId) == false) {
                                            String parentVersion = parent.getVersion();
                                            String parentName = parentGroupId + Constants.COLON + parentArtifactId + Constants.COLON + parentVersion;
                                            DependencyInfo parentDependencyInfo = parentsMap.get(parentName);
                                            if (parentDependencyInfo != null) { // the parent was already created - add it as a child
                                                parentDependencyInfo.getChildren().add(dependencyInfo);
                                            } else { // add this dependency to the children map
                                                if (childrenMap.get(dependencyName) == null) {
                                                    childrenMap.put(dependencyName, new ArrayList<>());
                                                }
                                                childrenMap.get(dependencyName).add(parentName);
                                            }
                                        } else { //... add it directly to the dependency info list
                                            agentProjectInfo.getDependencies().add(dependencyInfo);
                                        }
                                    }
                                } else {
                                    logger.warn("Could not find SHA1 for {}-{}-{}", groupId, revision.getArtifacts().get(0), version);
                                }
                            } else {
                                logger.warn("Could not find jar file {}", jarFile.getPath());
                            }
                        } else {
                            logger.warn("Could not find artifact ID for {}-{}", groupId, version);
                        }
                    }
                }
            }
            // building dependencies tree
            for (String child : childrenMap.keySet()) {
                List<String> parents = childrenMap.get(child);
                for (String parent : parents) {
                    if (!isDescendant(parentsMap.get(child), parentsMap.get(parent))) {
                        parentsMap.get(parent).getChildren().add(parentsMap.get(child));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not read {}: {}", xmlReportFile.getPath(), e.getMessage());
            logger.debug("stacktrace {}", e.getStackTrace());
        }

        return agentProjectInfo;
    }

    // preventing circular dependencies by making sure the dependency is not a descendant of its own
    private boolean isDescendant(DependencyInfo ancestor, DependencyInfo descendant){
        for (DependencyInfo child : ancestor.getChildren()){
            if (child.equals(descendant)){
                return true;
            }
            return isDescendant(child, descendant);
        }
        return false;
    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        excludes.add(Constants.PATTERN + SCALA_EXTENSION);
        excludes.add("**" + fileSeparator + "project" + fileSeparator + "**");
        return excludes;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return SCALA_SCRIPT_EXTENSION;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.MAVEN; // TEMP - we should add SBT
    }

    @Override
    protected String getDependencyTypeName() {
        return SBT.toUpperCase();
    }

    @Override
    protected String[] getBomPattern() {
        return new String[]{"**" + fileSeparator + BUILD_SBT};
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }
}