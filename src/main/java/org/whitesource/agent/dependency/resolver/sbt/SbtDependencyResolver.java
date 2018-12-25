package org.whitesource.agent.dependency.resolver.sbt;

import org.apache.commons.lang.StringUtils;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.utils.Cli;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.utils.FilesUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SbtDependencyResolver extends AbstractDependencyResolver {

    /* --- Static members --- */

    private static final String BUILD_SBT = "build.sbt";
    private static final String SCALA = "scala";
    private static final String SBT = "sbt";
    private static final String SCALA_EXTENSION = Constants.DOT + SCALA;
    private static final List<String> SCALA_SCRIPT_EXTENSION = Arrays.asList(SCALA_EXTENSION,Constants.DOT + SBT);
    private static final String COMPILE = "compile";
    private static final String TARGET = "target";
    private static final String RESOLUTION_CACHE = "resolution-cache";
    private static final String REPORTS = "reports";
    private static final String SUCCESS = "success";
    private static final String PROJECT = "project";
    private static final String COMPILE_XML = "-compile.xml";
    private static final String SBT_TARGET_FOLDER = "sbt.targetFolder";
    private static final Pattern linuxPattern = Pattern.compile("\\/.*\\/target");
    private static final String windowsPattern = ".*\\s";

    /* --- Private Members --- */

    private boolean sbtAggregateModules;
    private boolean ignoreSourceFiles;
    private boolean sbtRunPreStep;
    private String sbtTargetFolder;
    private String[] includes = {"**" + fileSeparator + TARGET + fileSeparator + "**" + fileSeparator + Constants.EMPTY_STRING + RESOLUTION_CACHE + fileSeparator + REPORTS + fileSeparator + "*" + COMPILE_XML};
    private String[] excludes = {"**" + fileSeparator + PROJECT + fileSeparator + "**"};
    private final Logger logger = LoggerFactory.getLogger(SbtDependencyResolver.class);

    /* --- Constructors --- */

    public SbtDependencyResolver(boolean sbtAggregateModules, boolean ignoreSourceFiles, boolean sbtRunPreStep, String sbtTargetFolder) {
        this.sbtAggregateModules = sbtAggregateModules;
        this.ignoreSourceFiles = ignoreSourceFiles;
        this.bomParser = new SbtBomParser();
        this.sbtRunPreStep = sbtRunPreStep;
        this.sbtTargetFolder = sbtTargetFolder;
    }

    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        Collection<AgentProjectInfo> projects = new ArrayList<>();
        List<File> xmlFiles = new LinkedList<>();

        // run sbt compile if the user turn on the sbt.runPreStep flag
        if (sbtRunPreStep) {
            runPreStep(topLevelFolder);
        }

        // check if sbt.targetFolder is not blank.
        // if not the system trying to search for compile.xml files under the the specific location
        // if yes the system trying to search for compile.xml files under the root of the project
        if (StringUtils.isNotBlank(sbtTargetFolder)) {
            Path path = Paths.get(sbtTargetFolder);
            if (Files.exists(path)) {
                xmlFiles = findXmlReport(sbtTargetFolder, xmlFiles, new String[]{Constants.PATTERN + COMPILE_XML}, excludes);
            } else {
                logger.warn("The target folder path {} doesn't exist", sbtTargetFolder);
            }
        } else {
            Collection<String> targetFolders = findTargetFolders(topLevelFolder);
            if (!targetFolders.isEmpty()) {
                for (String targetPath : targetFolders) {
                    xmlFiles = findXmlReport(targetPath, xmlFiles, new String[]{Constants.PATTERN + COMPILE_XML}, excludes);
                }
            } else {
                logger.debug("Didn't find any target folder in {}", topLevelFolder);
            }
        }

        // if the system didn't find compile.xml files and the user didn't turn on the sbt.runPreStepFlag
        // the system print warning message to the user and ask him to turn on the flag.
        if (xmlFiles.isEmpty() && !sbtRunPreStep) {
            logger.warn("Didn't find compile.xml please try to turn on the flag {}", SBT_TARGET_FOLDER);
        }
        for (File xmlFile : xmlFiles) {
            projects.add(parseXmlReport(xmlFile));
        }
        Set<String> excludes = new HashSet<>();
        Map<AgentProjectInfo, Path> projectInfoPathMap = projects.stream().collect(Collectors.toMap(projectInfo -> projectInfo, projectInfo -> {
            if (ignoreSourceFiles) {
                excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder,  extensionPattern(SCALA_SCRIPT_EXTENSION), null));
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

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        excludes.add(Constants.PATTERN + SCALA_EXTENSION);
        excludes.add("**" + fileSeparator + PROJECT + fileSeparator + "**");
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
    public String[] getBomPattern() {
        return new String[]{Constants.PATTERN + BUILD_SBT};
    }

    @Override
    public Collection<String> getManifestFiles(){
        return Arrays.asList(BUILD_SBT);
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    /* --- Private methods --- */

    /* looking for an xml file ending with '-compile.xml', which should be either in 'target/scala-{version number}/resolution-cache/reports'
     or 'target/scala-{version number}/sbt-{version-number}/resolution-cache/reports'.
     There are some cases where 2 files inside that folder end with '-compile.xml'.  in such case, the way to find the relevant is if its name
     contains the scala-version (which is part of the scala folder name), and that's relevant only if the xml file isn't inside 'sbt-{}' folder.
     */
    private List<File> findXmlReport(String folderPath, List<File> files, String[] includes, String[] excludes) {
        FilesScanner filesScanner = new FilesScanner();
        logger.debug("Trying to find *" + COMPILE_XML + " file under target folder in {}", folderPath);
        String[] directoryContent = filesScanner.getDirectoryContent(folderPath, includes, excludes, false, false);
        for (String filePath : directoryContent) {
            boolean add = true;
            File reportFile = new File(folderPath + fileSeparator + filePath);
            for (File file : files) {
                if (file.getParent().equals(reportFile.getParent())) {
                    add = false;
                    if (reportFile.getName().length() < file.getName().length()) {
                        files.remove(file);
                        files.add(reportFile);
                        break;
                    }
                }
            }
            if (add) {
                files.add(reportFile);
            }
        }
        return files;
    }

    // creating the xml report using 'sbt "compile"' command
    private void runPreStep(String folderPath) {
        Cli cli = new Cli();
        boolean success = false;
        List<String> compileOutput = cli.runCmd(folderPath, cli.getCommandParams(SBT, COMPILE));
        if (!compileOutput.isEmpty()) {
            if (compileOutput.get(compileOutput.size() - 1).contains(SUCCESS)) {
                success = true;
            }
        }
        if (!success) {
            logger.warn("Can't run '{} {}'", SBT, COMPILE);
        }
    }

    // Trying to get all the paths of target folders
    private Collection<String> findTargetFolders(String folderPath) {
        logger.debug("Scanning target folder {}", folderPath);
        Cli cli = new Cli();
        List<String> lines;
        List<String> targetFolders = new LinkedList<>();
        lines = cli.runCmd(folderPath, cli.getCommandParams(SBT, TARGET));
        if (lines != null && !lines.isEmpty()) {
            for (String line : lines) {
                if (DependencyCollector.isWindows()) {
                    if (line.endsWith(TARGET) && line.contains(fileSeparator)) {
                        String[] split = line.split(windowsPattern);
                        targetFolders.add(split[1]);
                    }
                } else {
                    if (line.contains(TARGET) && line.contains(fileSeparator)) {
                        Matcher matcher = linuxPattern.matcher(line);
                        if (matcher.find()) {
                            targetFolders.add(matcher.group(0));
                        }
                    }
                }
            }
            for (int i = 0; i < targetFolders.size(); i++) {
                String targetFolder = targetFolders.get(i);
                Path path = Paths.get(targetFolder);
                if (!Files.exists(path)) {
                    targetFolders.remove(targetFolder);
                    logger.warn("The target folder {} path doesn't exist", sbtTargetFolder);
                }
            }
        }
        return targetFolders;
    }

    private AgentProjectInfo parseXmlReport(File xmlReportFile) {
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
    private boolean isDescendant(DependencyInfo ancestor, DependencyInfo descendant) {
        for (DependencyInfo child : ancestor.getChildren()) {
            if (child.equals(descendant)) {
                return true;
            }
            return isDescendant(child, descendant);
        }
        return false;
    }

}