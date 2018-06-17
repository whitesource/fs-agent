package org.whitesource.agent.dependency.resolver.sbt;

import org.apache.commons.io.comparator.SizeFileComparator;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.ruby.RubyDependencyResolver;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.utils.Cli;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

public class SbtDependencyResolver extends AbstractDependencyResolver {

    private static final String BUILD_SBT = "build.sbt";
    private static final String SCALA_EXTENSION = ".scala";
    private static final List<String> SCALA_SCRIPT_EXTENSION = Arrays.asList(SCALA_EXTENSION, ".sbt");
    private static final String SBT = "sbt";
    private static final String COMPILE = "\"compile\"";


    private final Logger logger = LoggerFactory.getLogger(SbtDependencyResolver.class);

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        List<DependencyInfo> dependencies = collectDependencies(topLevelFolder);
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    private List<DependencyInfo> collectDependencies(String folderPath){
        File xmlFile = findXmlReport(folderPath);
        if (xmlFile == null){
            xmlFile = loadXmlReport(folderPath);
        }
        if (xmlFile != null){
            return parseXmlReport(xmlFile);
        }
        return new ArrayList<>();
    }

    private File findXmlReport(String folderPath){
        File targetFolder = new File(folderPath + fileSeparator + "target");
        if (targetFolder.isDirectory()){
            File scalaFolder = findFolder(targetFolder, "scala");
            File reportsFolder = new File(scalaFolder.getAbsolutePath() + fileSeparator + "resolution-cache" + fileSeparator + "reports");
            if (!reportsFolder.isDirectory()) {
                File sbtFolder = findFolder(scalaFolder, "sbt");
                if (sbtFolder != null) {
                    reportsFolder = new File(sbtFolder.getAbsolutePath() + fileSeparator + "resolution-cache" + fileSeparator + "reports");
                    if (!reportsFolder.isDirectory()) {
                        return null;
                    }
                }
            }
            File[] xmlFiles = reportsFolder.listFiles(new XmlFileNameFilter());
            if (xmlFiles.length > 0) {
                return xmlFiles[0];
            }
        }
        return null;
    }

    private File findFolder(File parentFolder, String folderName){
        if (parentFolder != null) {
            File[] files = parentFolder.listFiles(new ScalaFileNameFilter(folderName));
            if (files.length > 0) {
                Arrays.sort(files, Collections.reverseOrder());
                File folder = files[0];
                if (folder.isDirectory()) {
                    return folder;
                }
            }
        }
        return null;
    }

    private File loadXmlReport(String folderPath){
        Cli cli = new Cli();
        List<String> compileOutput = cli.runCmd(folderPath, cli.getCommandParams(SBT, COMPILE));
        if (compileOutput != null){
            if (compileOutput.get(compileOutput.size()-1).startsWith("[success]")){
                return findXmlReport(folderPath);
            }
        }
        return null;
    }

    private List<DependencyInfo> parseXmlReport(File xmlReportFile){
        List<DependencyInfo> dependencyInfoList = new ArrayList<>();
        HashMap<String, DependencyInfo> parentsMap = new HashMap<>();
        HashMap<String, List<String>> childrenMap = new HashMap<>();
        Serializer serializer = new Persister();
        try {
            IvyReport ivyReport = serializer.read(IvyReport.class, xmlReportFile);
            String projectGroupId = ivyReport.getInfo().getGroupId();
            String projectArtifactId = ivyReport.getInfo().getArtifactId();
            for (Module dependency : ivyReport.getDependencies()){
                String groupId = dependency.getGroupId();
                String artifactId = dependency.getArtifactId();
                for (Revision revision : dependency.getRevisions()){
                    if (!revision.getEvicted()){
                        String version = revision.getVersion();
                        Artifact artifact = revision.getArtifacts().get(0);
                        if (artifact != null) {
                            File jarFile = new File(revision.getArtifacts().get(0).getPathToJar());
                            if (jarFile.isFile()){
                                String sha1 = ChecksumUtils.calculateSHA1(jarFile);
                                if (sha1 != null){
                                    DependencyInfo dependencyInfo = new DependencyInfo(groupId,artifactId,version);
                                    dependencyInfo.setSha1(sha1);
                                    dependencyInfo.setDependencyType(DependencyType.GRADLE);
                                    dependencyInfo.setFilename(jarFile.getName());
                                    dependencyInfo.setSystemPath(xmlReportFile.getPath()); // TODO - not sure about this
                                    String dependencyName = groupId + Constants.COLON + artifactId + Constants.COLON + version;
                                    parentsMap.put(dependencyName, dependencyInfo);
                                    for (Caller parent : revision.getParentsList()){
                                        String parentGroupId = parent.getGroupId();
                                        String parentArtifactId = parent.getArtifactId();
                                        if (parentGroupId.equals(projectGroupId) == false && parentArtifactId.equals(projectArtifactId) == false) {
                                            String parentVersion = parent.getVersion();
                                            String parentName = parentGroupId + Constants.COLON + parentArtifactId + Constants.COLON + parentVersion;
                                            DependencyInfo parentDependencyInfo = parentsMap.get(parentName);
                                            if (parentDependencyInfo != null) {
                                                parentDependencyInfo.getChildren().add(dependencyInfo);
                                            } else {
                                                if (childrenMap.get(dependencyName) == null){
                                                    childrenMap.put(dependencyName, new ArrayList<>());
                                                }
                                                childrenMap.get(dependencyName).add(parentName);
                                            }
                                        } else {
                                            dependencyInfoList.add(dependencyInfo);
                                        }
                                    }
                                } else {
                                    logger.warn("Could not find SHA1 for {}-{}-{}", groupId, artifact, version);
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
            for (String child : childrenMap.keySet()){
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
        return dependencyInfoList;
    }

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
        return excludes;
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return SCALA_SCRIPT_EXTENSION;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.GRADLE;//DependencyType.SBT;
    }

    @Override
    protected String getBomPattern() {
        return BUILD_SBT;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }
}

class ScalaFileNameFilter implements FilenameFilter {
    private String folderName;

    public ScalaFileNameFilter(String name){
        folderName = name;
    }

    @Override
    public boolean accept(File dir, String name) {
        return name.toLowerCase().startsWith(folderName);
    }
}

class XmlFileNameFilter implements FilenameFilter{
    @Override
    public boolean accept(File file, String name){
        return name.toLowerCase().endsWith("-compile.xml");
    }
}
