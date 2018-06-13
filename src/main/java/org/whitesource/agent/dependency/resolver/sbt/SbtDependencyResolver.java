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

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

public class SbtDependencyResolver extends AbstractDependencyResolver {

    private static final String BUILD_SBT = "build.sbt";
    private static final String SCALA_EXTENSION = ".scala";
    private static final List<String> SCALA_SCRIPT_EXTENSION = Arrays.asList(SCALA_EXTENSION, ".sbt");

    private final Logger logger = LoggerFactory.getLogger(SbtDependencyResolver.class);

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        List<DependencyInfo> dependencies = collectDependencies(topLevelFolder);
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    private List<DependencyInfo> collectDependencies(String folderPath){
        File xmlFile = findXmlReport(folderPath);
        return parseXmlReport(xmlFile);
    }

    private File findXmlReport(String folderPath){
        File targetFolder = new File(folderPath + fileSeparator + "project" + fileSeparator + "target");
        if (targetFolder.isDirectory()){
            File scalaFolder = findFolder(targetFolder, "scala");
            File sbtFolder = findFolder(scalaFolder, "sbt");
            if (sbtFolder != null) {
                File reportsFolder = new File(sbtFolder.getAbsolutePath() + fileSeparator + "resolution-cache" + fileSeparator + "reports");
                if (reportsFolder.isDirectory()) {
                    // TODO - which report to take?
                    File[] xmlFiles = reportsFolder.listFiles(new XmlFileNameFilter());
                    if (xmlFiles.length > 0) {
                        Arrays.sort(xmlFiles, SizeFileComparator.SIZE_REVERSE);
                        return xmlFiles[0];
                    }
                }
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

    private List<DependencyInfo> parseXmlReport(File xmlReportFile){
        List<DependencyInfo> dependencyInfoList = new ArrayList<>();
        Serializer serializer = new Persister();
        try {
            IvyReport ivyReport = serializer.read(IvyReport.class, xmlReportFile);
            for (Module dependency : ivyReport.getDependencies()){
                String groupId = dependency.getGroupId();
                String artifactId = dependency.getArtifactId();
                logger.info(groupId + " " + artifactId);
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
                                    dependencyInfo.setDependencyType(DependencyType.SBT);
                                    dependencyInfo.setFilename(jarFile.getName());
                                    dependencyInfo.setSystemPath(xmlReportFile.getPath()); // TODO - not sure about this
                                    dependencyInfoList.add(dependencyInfo);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dependencyInfoList;
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
        return DependencyType.SBT;
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
        return name.toLowerCase().endsWith(".xml");
    }
}
