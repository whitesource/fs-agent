package org.whitesource.agent.dependency.resolver.paket;

import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.springframework.util.StringUtils;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
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
    public final String DEPENDECYERROR = "Dependency {} was not updated, please try change paket.runPreStep to true, or run 'paket install' manually to fix issue";

    /* --- Statics Members --- */

    private final Logger logger = LoggerFactory.getLogger(AbstractPaketDependencyCollector.class);
    private static final String FOUR_SPACES = "    ";
    private static final String SIX_SPACES = "      ";
    private static final String NUPKG = "nupkg";
    private static final String PAKET_LOCK = "paket.lock";
    private static final String NUGET = "NUGET";
    private static final String RIGHT_BRACKET = ")";

    /* --- Members --- */

    private String[] paketIgnoredGroups;
    private String rootDirectory;
    private List<String> directDependenciesNames;

    /* --- Constructors --- */

    public AbstractPaketDependencyCollector(List<String> directDependenciesNames, String[] paketIgnoredGroups) {
        this.directDependenciesNames = directDependenciesNames;
        this.paketIgnoredGroups = paketIgnoredGroups;
    }

    /* --- Public methods --- */

    @Override
    public Collection<AgentProjectInfo> collectDependencies(String rootDirectory) {
        this.rootDirectory = rootDirectory;
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        // check if paket group is not ignored & it has dependencies
        if (paketIgnoredGroups == null || !Arrays.asList(paketIgnoredGroups).contains(getGroupName().toLowerCase())) {
            if (!this.directDependenciesNames.isEmpty()) {
                List<String> groupLines = getGroupDependenciesFromPaketLock();
                for (String dependencyName : this.directDependenciesNames) {
                    DependencyInfo dependency = new DependencyInfo();
                    dependency.setGroupId(dependencyName);
                    dependency.setChildren(collectChildrenDependencies(dependency, groupLines));
                    if(dependency.getSha1() == null && dependency.getArtifactId() == null) {
                        logger.warn(DEPENDECYERROR, dependency.getGroupId());
                    } else {
                        dependencies.add(dependency);
                    }
                }
            }
        }
        return getSingleProjectList(dependencies);
    }

    /* --- private methods --- */

    private Collection<DependencyInfo> collectChildrenDependencies(DependencyInfo dependency, List<String> groupLines) {
        logger.debug("Collect child dependencies of {}, total group-lines = {}", dependency.getGroupId(), groupLines.size());
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        boolean dependencyLine = false;
        for (String line : groupLines) {
            if (!dependencyLine && line.startsWith(FOUR_SPACES + dependency.getGroupId() + Constants.WHITESPACE)) {
                dependencyLine = true;
                getDependencyFromLine(dependency, line);
            } else if (dependencyLine) {
                if (line.startsWith(SIX_SPACES)) {
                    DependencyInfo childDependency = new DependencyInfo();
                    String lineWithoutSpaces = line.substring(SIX_SPACES.length());
                    childDependency.setGroupId(lineWithoutSpaces.substring(0, lineWithoutSpaces.indexOf(Constants.WHITESPACE)));
                    childDependency.setChildren(collectChildrenDependencies(childDependency, groupLines));
                    // prevent adding dependencies without sha1 or artifact id version and dependency type.
                    if(dependency.getSha1() == null && dependency.getArtifactId() == null) {
                        logger.warn(DEPENDECYERROR, dependency.getGroupId());
                    } else {
                        dependencies.add(childDependency);
                    }
                } else {
                    // move to the next dependency parent with its children
                    break;
                }
            }
        }
        return dependencies;
    }

    private void getDependencyFromLine(DependencyInfo dependency, String line) {
        // example line: "    System.Buffers (4.4) - restriction: >= netstandard2.0"
        String beginOfVersion = line.substring(line.indexOf(dependency.getGroupId()) + dependency.getGroupId().length() + 2);
        String version = beginOfVersion.substring(0, beginOfVersion.indexOf(RIGHT_BRACKET));
        File dependencyFile = findFileFromPath(dependency.getGroupId(), version);
        String sha1 = getPackageSha1(dependencyFile, dependency.getGroupId(), version);
        String systemPath = Constants.EMPTY_STRING;
        if (dependencyFile != null) {
            systemPath = dependencyFile.getAbsolutePath();
        }
        updateDependencyInfo(dependency, dependency.getGroupId(), version, sha1, systemPath);
    }

    private File findFileFromPath(String dependencyName, String dependencyVersion) {
        String path = getFolderPathOfDependency(dependencyName);
        File folder = new File(path);
        File dependencyFile = null;
        String dependencyNameLowerCase = dependencyName.toLowerCase();
        String dependencyVersionLowerCase = dependencyVersion.toLowerCase();
        if (folder.exists()) {
            for (File file : folder.listFiles()) {
                // check if the file exists: 'package-name.package-version' && ends with .nupkg
                if (file.getName().toLowerCase().startsWith(dependencyNameLowerCase + Constants.DOT + dependencyVersionLowerCase)
                        && file.getName().endsWith(Constants.DOT + NUPKG)) {
                    dependencyFile = file;
                    break;
                }
            }
        }
        return dependencyFile;
    }

    private void updateDependencyInfo(DependencyInfo dependency, String name, String version, String sha1, String systemPath) {
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
            dependency.setArtifactId(new File(systemPath).getName());
            dependency.setSha1(sha1);
        }
    }

    private List<String> getGroupDependenciesFromPaketLock() {
        List<String> result = new LinkedList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(this.rootDirectory +
                Constants.FORWARD_SLASH + PAKET_LOCK))) {
            String line;
            boolean rightGroup = false;
            boolean nugetSection = false;
            while ((line = bufferedReader.readLine()) != null) {
                // check if we should read group lines, for example: 'GROUP Build'
                if (!rightGroup && line.startsWith(beginGroupLine())) {
                    rightGroup = true;
                }
                // collect group dependencies
                if (rightGroup) {
                    if (!nugetSection && line.startsWith(NUGET)) {
                        nugetSection = true;
                        continue;
                    }
                    if (nugetSection) {
                        if (line.startsWith(Constants.WHITESPACE)) {
                            result.add(line);
                        } else {
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Failed reading paket.lock file in {}. Consider executing 'paket install' first.", this.rootDirectory);
        }
        return result;
    }

    /* --- protected methods --- */

    protected abstract String getGroupName();

    protected abstract String beginGroupLine();

    protected abstract String getFolderPathOfDependency(String dependencyName);

    protected String getPackagesFolder() {
        return this.rootDirectory + Constants.FORWARD_SLASH + Constants.PACKAGES;
    }

    protected String getPackageSha1(File calcSha1File, String dependencyName, String dependencyVersion) {
        String sha1 = Constants.EMPTY_STRING;
        if (calcSha1File != null) {
            try {
                sha1 = ChecksumUtils.calculateSHA1(calcSha1File);
            } catch (IOException e) {
                logger.warn("Failed getting " + calcSha1File.getAbsolutePath() + ". Could not calculate sha1 for this file.");
                logger.debug("Error calculating sha1: {}", e.getMessage());
            }
        } else {
            logger.warn("{} folder is not exist. Could not calculate sha1 for {} - {}.", getFolderPathOfDependency(dependencyName), dependencyName, dependencyVersion);
        }
        return sha1;
    }
}
