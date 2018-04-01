package org.whitesource.agent.dependency.resolver.paket;

import org.springframework.util.StringUtils;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.hash.ChecksumUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * @author raz.nitzan
 */
abstract class AbstractPaketDependencyCollector extends DependencyCollector {

    /* --- Statics Members --- */

    private final Logger logger = LoggerFactory.getLogger(AbstractPaketDependencyCollector.class);
    private static final String SPACE = " ";
    private static final String FOUR_SPACES = "    ";
    private static final String SIX_SPACES = "      ";
    private static final String EMPTY_STRING = "";
    private static final String DASH = "-";
    private static final String DOT = ".";
    private static final String NUPKG = "nupkg";
    private static final String FORWARD_SLASH = "/";
    private static final String PAKET_LOCK = "paket.lock";
    private static final String NUGET = "NUGET";
    private static final String PACKAGES = "packages";
    private static final String RIGHT_BRACKET = ")";

    /* --- Members --- */

    private List<String> linesOfDirectDependencies;
    private String[] paketIgnoredScopes;
    private String rootDirectory;

    /* --- Constructors --- */

    public AbstractPaketDependencyCollector(List<String> linesOfDirectDependencies, String[] paketIgnoredScopes) {
        this.linesOfDirectDependencies = linesOfDirectDependencies;
        this.paketIgnoredScopes = paketIgnoredScopes;
    }

    /* --- Public methods --- */

    @Override
    public Collection<AgentProjectInfo> collectDependencies(String rootDirectory) {
        this.rootDirectory = rootDirectory;
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        if (paketIgnoredScopes == null || !Arrays.asList(paketIgnoredScopes).contains(getGroupName())) {
            collectDirectDependencies(dependencies);
            if (!dependencies.isEmpty()) {
                List<String> groupLines = getGroupLinesFromPaketLock();
                for (DependencyInfo dependency : dependencies) {
                    dependency.setChildren(collectChildrenDependencies(dependency, groupLines));
                }
            }
        }
        return getSingleProjectList(dependencies);
    }

    public String getPackagesFolder() {
        return this.rootDirectory + FORWARD_SLASH + PACKAGES;
    }

    /* --- private methods --- */

    private Collection<DependencyInfo> collectChildrenDependencies(DependencyInfo dependency, List<String> groupLines) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        boolean getToDependencyLine = false;
        for (String line : groupLines) {
            if (!getToDependencyLine && line.startsWith(FOUR_SPACES + dependency.getGroupId() + SPACE)) {
                getToDependencyLine = true;
                // this is not direct dependency, we have to find version and sha1
                if (StringUtils.isEmpty(dependency.getVersion())) {
                    getTransitiveDependencyFromLine(dependency, line);
                }
                continue;
            } else if (getToDependencyLine) {
                if (line.startsWith(SIX_SPACES)) {
                    DependencyInfo childDependency = new DependencyInfo();
                    String lineWithoutSpaces = line.substring(SIX_SPACES.length());
                    childDependency.setGroupId(lineWithoutSpaces.substring(0, lineWithoutSpaces.indexOf(SPACE)));
                    childDependency.setChildren(collectChildrenDependencies(childDependency, groupLines));
                    dependencies.add(childDependency);
                } else {
                    break;
                }
            }
        }
        return dependencies;
    }

    private void collectDirectDependencies(Collection<DependencyInfo> dependencies) {
        for (String line : this.linesOfDirectDependencies) {
            if (line.startsWith(getGroupName())) {
                dependencies.add(getDirectDependencyFromLine(line));
            }
        }
    }

    private DependencyInfo getDirectDependencyFromLine(String line) {
        String nameAndVersion = line.substring(getGroupName().length() + 1);
        String name = nameAndVersion.substring(0, nameAndVersion.indexOf(SPACE));
        String version = nameAndVersion.substring(nameAndVersion.indexOf(DASH) + 2);
        File fileForSha1 = findFileFromPath(name, version);
        String sha1 = getSha1(fileForSha1, name, version);
        String systemPath = EMPTY_STRING;
        if (fileForSha1 != null) {
            systemPath = fileForSha1.getAbsolutePath();
        }
        DependencyInfo result = new DependencyInfo();
        enrichDependency(result, name, version, sha1, systemPath);
        return result;
    }

    private void getTransitiveDependencyFromLine(DependencyInfo dependency, String line) {
        // example line: "    System.Buffers (4.4) - restriction: >= netstandard2.0"
        String beginOfVersion = line.substring(line.indexOf(dependency.getGroupId()) + dependency.getGroupId().length() + 2);
        String version = beginOfVersion.substring(0, beginOfVersion.indexOf(RIGHT_BRACKET));
        File fileForSha1 = findFileFromPath(dependency.getGroupId(), version);
        String sha1 = getSha1(fileForSha1, dependency.getGroupId(), version);
        String systemPath = EMPTY_STRING;
        if (fileForSha1 != null) {
            systemPath = fileForSha1.getAbsolutePath();
        }
        enrichDependency(dependency, dependency.getGroupId(), version, sha1, systemPath);
    }

    private File findFileFromPath(String dependencyName, String dependencyVersion) {
        String path = getFolderPathOfDependency(dependencyName);
        File folder = new File(path);
        File dependencyFile = null;
        String dependencyNameLowerCase = dependencyName.toLowerCase();
        String dependencyVersionLowerCase = dependencyVersion.toLowerCase();
        if (folder.exists()) {
            for (File file : folder.listFiles()) {
                if (file.getName().startsWith(dependencyNameLowerCase + DOT + dependencyVersionLowerCase) && file.getName().endsWith(DOT + NUPKG)) {
                    dependencyFile = file;
                    break;
                }
            }
        }
        return dependencyFile;
    }

    private void enrichDependency(DependencyInfo dependency, String name, String version, String sha1, String systemPath) {
        if (!StringUtils.isEmpty(systemPath)) {
            dependency.setSystemPath(systemPath);
        }
        if (StringUtils.isEmpty(sha1)) {
            dependency.setGroupId(name);
            dependency.setArtifactId(name);
            dependency.setVersion(version);
            dependency.setDependencyType(DependencyType.NUGET);
        } else {
            dependency.setGroupId(name);
//            dependency.setArtifactId(name + DOT + version + DOT + NUPKG);
            dependency.setArtifactId(new File(systemPath).getName());
            dependency.setSha1(sha1);
        }
    }

    private List<String> getGroupLinesFromPaketLock() {
        List<String> result = new LinkedList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(this.rootDirectory + FORWARD_SLASH + PAKET_LOCK))) {
            String line;
            boolean getToRightGroup = false;
            boolean getToNugetSection = false;
            while ((line = bufferedReader.readLine()) != null) {
                if (!getToRightGroup && line.startsWith(beginGroupLine())) {
                    getToRightGroup = true;
                }
                if (getToRightGroup) {
                    if (!getToNugetSection && line.startsWith(NUGET)) {
                        getToNugetSection = true;
                        continue;
                    }
                    if (getToNugetSection) {
                        if (line.startsWith(SPACE)) {
                            result.add(line);
                        } else {
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Could not find paket.lock file in {}. Please execute 'paket install' first.", rootDirectory);
        }
        return result;
    }

    /* --- protected methods --- */

    protected abstract String getGroupName();

    protected abstract String beginGroupLine();

    protected abstract String getFolderPathOfDependency(String dependencyName);

    protected String getSha1(File calcSha1File, String dependencyName, String dependencyVersion) {
        if (calcSha1File == null) {
            logger.warn("{} folder is not exist. Could not calculate sha1 for {} - {}.", getFolderPathOfDependency(dependencyName), dependencyName, dependencyVersion);
            return EMPTY_STRING;
        }
        try {
            return ChecksumUtils.calculateSHA1(calcSha1File);
        } catch (IOException e) {
            logger.info("Failed getting " + calcSha1File.getAbsolutePath() + ". Could not calculate sha1 for this file.");
            return EMPTY_STRING;
        }
    }
}
