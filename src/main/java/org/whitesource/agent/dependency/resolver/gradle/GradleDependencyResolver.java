package org.whitesource.agent.dependency.resolver.gradle;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.fs.Main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.whitesource.agent.TempFolders.BUILD_GRADLE_DIRECTORY;

public class GradleDependencyResolver extends AbstractDependencyResolver {

    /* --- Static members --- */

    private static final List<String> GRADLE_SCRIPT_EXTENSION = Arrays.asList(".gradle", ".groovy", ".java", ".jar", ".war", ".ear", ".car", ".class");

    private static final String JAR_EXTENSION = ".jar";
    private static final String PROJECT = "--- Project";
    public static final String COPY_DEPENDENCIES_TASK_TXT = "copyDependenciesTask.txt";

    /* --- Private Members --- */

    private String[] ignoredScopes;
    private GradleLinesParser gradleLinesParser;
    private GradleCli gradleCli;
    private ArrayList<String> topLevelFoldersNames;
    private boolean ignoreSourceCode;
    private boolean gradleAggregateModules;
    private boolean gradleRunPreStep;

    private final Logger logger = LoggerFactory.getLogger(GradleDependencyResolver.class);

    /* --- Constructors --- */

    public GradleDependencyResolver(boolean runAssembleCommand, boolean ignoreSourceCode, boolean gradleAggregateModules, String gradlePreferredEnvironment, String[] gradleIgnoredScopes, boolean gradleRunPreStep) {
        super();
        gradleLinesParser = new GradleLinesParser(runAssembleCommand, gradlePreferredEnvironment);
        gradleCli = new GradleCli(gradlePreferredEnvironment);
        this.ignoredScopes = gradleIgnoredScopes;
        topLevelFoldersNames = new ArrayList<>();
        this.ignoreSourceCode = ignoreSourceCode;
        this.gradleAggregateModules = gradleAggregateModules;
        this.gradleRunPreStep = gradleRunPreStep;
    }

    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        // each bom-file ( = build.gradle) represents a module - identify its folder and scan it using 'gradle dependencies'
        Map<AgentProjectInfo, Path> projectInfoPathMap = new HashMap<>();
        Collection<String> excludes = new HashSet<>();

        // Get the list of projects as paths
        List<String> projectsList = null;
        if (bomFiles.size() > 1) {
            projectsList = collectProjects(topLevelFolder);
        }
        if (projectsList == null) {
            logger.warn("Command \"gradle projects\" did not return a list of projects");
        }
        if (gradleRunPreStep) {
            downloadMissingDependencies(bomFiles);
        }

        for (String bomFile : bomFiles) {
            String bomFileFolder = new File(bomFile).getParent();
            File bomFolder = new File(new File(bomFile).getParent());
            String moduleName = bomFolder.getName();
            String moduleRelativeName = Constants.EMPTY_STRING;
            try {
                String canonicalPath = bomFolder.getCanonicalPath();
                // Relative name by replacing the root folder with "." - will look something like .\abc\def
                moduleRelativeName = Constants.DOT + canonicalPath.replaceFirst(Pattern.quote(topLevelFolder), Constants.EMPTY_STRING);
            } catch (Exception e) {
                logger.debug("Error getting path - {} ", e.getMessage());
            }
            // making sure the module's folder was listed by "gradle projects" command
            if (!moduleRelativeName.isEmpty() && projectsList != null && !projectsList.contains(moduleRelativeName)) {
                logger.debug("Ignoring project at {} - because it was not listed by \"gradle projects\" command", moduleRelativeName);
                continue;
            }

            List<DependencyInfo> dependencies = collectDependencies(bomFileFolder, bomFileFolder.equals(topLevelFolder));
            if (dependencies.size() > 0) {
                AgentProjectInfo agentProjectInfo = new AgentProjectInfo();
                agentProjectInfo.getDependencies().addAll(dependencies);
                if (!gradleAggregateModules) {
                    Coordinates coordinates = new Coordinates();
                    coordinates.setArtifactId(moduleName);
                    agentProjectInfo.setCoordinates(coordinates);
                }
                projectInfoPathMap.put(agentProjectInfo, bomFolder.toPath());
                if (ignoreSourceCode) {
                    excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder, extensionPattern(GRADLE_SCRIPT_EXTENSION), null));
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
        return new String[]{Constants.GLOB_PATTERN_PREFIX + Constants.BUILD_GRADLE};
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    /* --- Private methods --- */

    private List<DependencyInfo> collectDependencies(String directory, boolean isParent) {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        // running the gradle/gradlew command from the project's root folder, because when using gradlew the path must be
        // kept (i.e. - the command 'gradlew' should only be called from the root's project).  In case of a multi-module
        // project, adding the module's name before the 'dependencies' command, so it'll know which folder to refer to
        String[] gradleCommandParams = gradleCli.getGradleCommandParams(GradleMvnCommand.DEPENDENCIES);
        String directoryName = Constants.EMPTY_STRING;
        if (!isParent) {
            // get the name of the directory
            String[] directoryPath = directory.split(Pattern.quote(fileSeparator));
            directoryName = directoryPath[directoryPath.length - 1];
            int lastParamIndex = gradleCommandParams.length - 1;
            gradleCommandParams[lastParamIndex] = directoryName + Constants.COLON + gradleCommandParams[lastParamIndex];
            directory = String.join(fileSeparator, Arrays.copyOfRange(directoryPath, 0, directoryPath.length - 1));
        }
        // get gradle dependencies, if the command runs successfully parse the dependencies
        directoryName = fileSeparator.concat(directoryName);
        List<String> lines = gradleCli.runGradleCmd(directory, gradleCommandParams);
        if (lines != null) {
            dependencyInfos.addAll(gradleLinesParser.parseLines(lines, directory, directoryName, ignoredScopes));
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

    // copy all the bom files (build.gradle) to temp folder and run the command "gradle copyDependencies"
    private void downloadMissingDependencies(Set<String> bomFiles) {
        File buildGradleTempDirectory = new File(BUILD_GRADLE_DIRECTORY);
        buildGradleTempDirectory.mkdir();
        for (String bomFile : bomFiles) {
            File buildGradleTmp = copyBomFile(bomFile, buildGradleTempDirectory);
            if (buildGradleTmp.exists()) {
                appendTaskToBomFile(buildGradleTmp);
                runPreStepCommand(buildGradleTmp);
                buildGradleTmp.delete();
            } else {
                logger.warn("Could not find the path {}", buildGradleTmp.getPath());
            }
        }
        FileUtils.deleteQuietly(buildGradleTempDirectory);

    }

    // copy bom file to local temp directory

    private File copyBomFile(String bomFile, File buildGradleTempDirectory) {
        File buildGradle = new File(bomFile);
        logger.debug("Copy bom file from {} to {}", buildGradle.getPath(), buildGradleTempDirectory);
        File buildGradleTmp = new File(buildGradleTempDirectory + fileSeparator + "build.gradle");
        try {
            FileUtils.copyFile(buildGradle, buildGradleTmp);
        } catch (IOException e) {
            logger.error("Could not copy the file {} to {} , the cause {}", buildGradle.getPath(), buildGradleTempDirectory.getPath(), e.getMessage());
        }
        return buildGradleTmp;
    }

    // append new task to bom file
    private void appendTaskToBomFile(File buildGradleTmp) {
        ClassLoader classLoader = Main.class.getClassLoader();
        InputStream inputStream = null;
        try {
            inputStream = classLoader.getResourceAsStream(COPY_DEPENDENCIES_TASK_TXT);
            byte[] bytes = IOUtils.toByteArray(inputStream);
            if (bytes.length > 0) {
                Files.write(Paths.get(buildGradleTmp.getPath()), bytes, StandardOpenOption.APPEND);
            } else {
                logger.warn("Could not read {}", COPY_DEPENDENCIES_TASK_TXT);
            }
        } catch (IOException e) {
            logger.error("Could not write into the file {}, the cause {}", buildGradleTmp.getPath(), e.getMessage());
        }
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            logger.error("Could close the file, cause", e.getMessage());
        }
    }

    // run pre step command gradle copyDependencies
    private void runPreStepCommand(File bomFile) {
        String directory = bomFile.getParent();
        String[] gradleCommandParams = gradleCli.getGradleCommandParams(GradleMvnCommand.COPY_DEPENDENCIES);
        if (StringUtils.isNotEmpty(directory) && gradleCommandParams.length > 0) {
            gradleCli.runGradleCmd(directory, gradleCommandParams);
        } else {
            logger.warn("Could not run gradle command");
        }

    }
}