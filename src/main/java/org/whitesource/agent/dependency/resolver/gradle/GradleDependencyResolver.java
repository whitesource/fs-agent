package org.whitesource.agent.dependency.resolver.gradle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GradleDependencyResolver extends AbstractDependencyResolver {

    private static final List<String> GRADLE_SCRIPT_EXTENSION = Arrays.asList(".gradle",".groovy", ".java", ".jar", ".war", ".ear", ".car", ".class");
    private static final String JAR_EXTENSION = ".jar";
    public static final String PROJECT = "--- Project";

    private GradleLinesParser gradleLinesParser;
    private GradleCli gradleCli;
    private ArrayList<String> topLevelFoldersNames;
    private boolean dependenciesOnly;
    private boolean gradleAggregateModules;

    private final Logger logger = LoggerFactory.getLogger(GradleDependencyResolver.class);

    public GradleDependencyResolver(boolean runAssembleCommand, boolean dependenciesOnly, boolean gradleAggregateModules, String gradleDefaultEnvironment) {
        super();
        gradleLinesParser = new GradleLinesParser(runAssembleCommand, gradleDefaultEnvironment);
        gradleCli = new GradleCli(gradleDefaultEnvironment);
        topLevelFoldersNames = new ArrayList<>();
        this.dependenciesOnly = dependenciesOnly;
        this.gradleAggregateModules = gradleAggregateModules;
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        // each bom-file ( = build.gradle) represents a module - identify its folder and scan it using 'gradle dependencies'
        Map<AgentProjectInfo, Path> projectInfoPathMap = new HashMap<>();
        Collection<String> excludes = new HashSet<>();

        // Get the list of projects as paths
        List<String> projectsList = null;
        if (bomFiles.size() > 1 ) {
            projectsList = collectProjects(topLevelFolder);
        }
        if (projectsList == null) {
            logger.warn("Command \"gradle projects\" did not return a list of projects");
        }

        for (String bomFile : bomFiles) {
            String bomFileFolder = new File(bomFile).getParent();
            File bomFolder = new File(new File(bomFile).getParent());
            String moduleName = bomFolder.getName();
            String moduleRelativeName = Constants.EMPTY_STRING;
            try {
                String canonicalPath = bomFolder.getCanonicalPath();
                // Relative name by replacing the root folder with "." - will look something like .\abc\def
                moduleRelativeName = Constants.DOT + canonicalPath.replaceFirst(Pattern.quote(topLevelFolder),Constants.EMPTY_STRING);
            } catch (Exception e) {
                logger.debug("Error getting path - {} ", e.getMessage());
            }
            // making sure the module's folder was listed by "gradle projects" command
            if (!moduleRelativeName.isEmpty() && projectsList != null && !projectsList.contains(moduleRelativeName)) {
                logger.debug("Ignoring project at {} - because it was not listed by \"gradle projects\" command", moduleRelativeName);
                continue;
            }

            List<DependencyInfo> dependencies = collectDependencies(bomFileFolder);
            if (dependencies.size() > 0) {
                AgentProjectInfo agentProjectInfo = new AgentProjectInfo();
                agentProjectInfo.getDependencies().addAll(dependencies);
                if (!gradleAggregateModules) {
                    Coordinates coordinates = new Coordinates();
                    coordinates.setArtifactId(moduleName);
                    agentProjectInfo.setCoordinates(coordinates);
                }
                projectInfoPathMap.put(agentProjectInfo, bomFolder.toPath());
                if (dependenciesOnly) {
                    excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder, GRADLE_SCRIPT_EXTENSION, null));
                }
            }
        }
        topLevelFoldersNames.add(topLevelFolder.substring(topLevelFolder.lastIndexOf(fileSeparator) + 1));
        excludes.addAll(getExcludes());
        ResolutionResult resolutionResult;
        if (!gradleAggregateModules) {
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
        for (String topLeverFolderName : topLevelFoldersNames) {
            excludes.add(GLOB_PATTERN + topLeverFolderName + JAR_EXTENSION);
        }
        return excludes;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return GRADLE_SCRIPT_EXTENSION;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.GRADLE;
    }

    @Override
    protected String getDependencyTypeName() {
        return DependencyType.GRADLE.name();
    }

    @Override
    protected String[] getBomPattern() {
        return new String[]{Constants.BUILD_GRADLE};
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    private List<DependencyInfo> collectDependencies(String rootDirectory) {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        List<String> lines = gradleCli.runGradleCmd(rootDirectory, gradleCli.getGradleCommandParams(GradleMvnCommand.DEPENDENCIES));
        if (lines != null) {
            dependencyInfos.addAll(gradleLinesParser.parseLines(lines, rootDirectory));
        }
        return dependencyInfos;
    }

    private List<String> collectProjects(String rootDirectory) {
        List<String> projectsList = gradleCli.runGradleCmd(rootDirectory, gradleCli.getGradleCommandParams(GradleMvnCommand.PROJECTS));
        List<String> resultProjectsList = null;
        if (projectsList != null) {
            resultProjectsList = new ArrayList<>();
            for (String line : projectsList) {
                if (line.contains(PROJECT)) {
                    // Relevant lines look like:
                    //  |    +--- Project ':nes:t4' - optional description for project
                    //  |    \--- Project ':nes:t5' - optional description for project
                    //  +--- Project ':template-server3'
                    // Split the line
                    String[] lineParts = line.split(PROJECT);
                    if (lineParts.length == 2) {
                        String partWithNameAndDescription = lineParts[1].trim();
                        String projectName;
                        // No description at the end of line
                        if (partWithNameAndDescription.endsWith(Constants.APOSTROPHE)) {
                            projectName = partWithNameAndDescription.trim().replaceAll(Constants.APOSTROPHE, Constants.EMPTY_STRING);
                        } else {
                            String[] projectAndDescription = partWithNameAndDescription.split(Constants.APOSTROPHE);
                            projectName = projectAndDescription[1];
                        }
                        // Convert the project name to a path name
                        // Example: :abc:def --> .\abc\def
                        String projectNameAsPath = Constants.DOT + projectName;
                        projectNameAsPath = projectNameAsPath.replaceAll(Constants.COLON, Matcher.quoteReplacement(File.separator));
                        resultProjectsList.add(projectNameAsPath);
                    }
                }
            }
        }
        return resultProjectsList;
    }
}