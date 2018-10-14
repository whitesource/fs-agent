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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.whitesource.agent.TempFolders.BUILD_GRADLE_DIRECTORY;

public class GradleDependencyResolver extends AbstractDependencyResolver {

    /* --- Static members --- */

    private static final List<String> GRADLE_SCRIPT_EXTENSION = Arrays.asList(".gradle", ".groovy", ".java", ".jar", ".war", ".ear", ".car", ".class");

    private static final String JAR_EXTENSION = ".jar";
    private static final String PROJECT = "--- Project";
    public static final String COPY_DEPENDENCIES_TASK_TXT = "copyDependenciesTask.txt";
    private static final String DEPENDENCIES = "dependencies";
    private static final String CURLY_BRACKETS = "{";

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
            downloadMissingDependencies(projectFolder);
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
        String directoryName = "";
        if (!isParent) {
            // TODO - test on linux
            String[] directoryPath = directory.split(Pattern.quote(fileSeparator));
            directoryName = directoryPath[directoryPath.length - 1];
            int lastParamIndex = gradleCommandParams.length - 1;
            gradleCommandParams[lastParamIndex] = directoryName + Constants.COLON + gradleCommandParams[lastParamIndex];
            directory = String.join(fileSeparator, Arrays.copyOfRange(directoryPath, 0, directoryPath.length - 1));
        }
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
    private void downloadMissingDependencies(String projectFolder) {
        logger.debug("running pre-steps on folder {}", projectFolder);
        File buildGradleTempDirectory = new File(BUILD_GRADLE_DIRECTORY);
        buildGradleTempDirectory.mkdir();
        if (copyProjectFolder(projectFolder, buildGradleTempDirectory)) {
            try {
                Stream<Path> pathStream = Files.walk(Paths.get(buildGradleTempDirectory.getPath()), Integer.MAX_VALUE).filter(file -> file.getFileName().toString().equals(Constants.BUILD_GRADLE));
                pathStream.forEach(path -> {
                    File buildGradleTmp = new File(path.toString());
                    if (buildGradleTmp.exists()) {
                        if (appendTaskToBomFile(buildGradleTmp))
                            runPreStepCommand(buildGradleTmp);
                    } else {
                        logger.warn("Could not find the path {}", buildGradleTmp.getPath());
                    }
                });
            } catch (IOException e) {
                logger.warn("Couldn't list all 'build.gradle' files, error: {}", e.getMessage());
                logger.debug("Error: {}", e.getStackTrace());
            }
            FileUtils.deleteQuietly(buildGradleTempDirectory);
        }
    }

    // copy project to local temp directory
    private boolean copyProjectFolder(String projectFolder, File buildGradleTempDirectory) {
        try {
            FileUtils.copyDirectory(new File(projectFolder), buildGradleTempDirectory);
        } catch (IOException e) {
            logger.error("Could not copy the folder {} to {} , the cause {}", projectFolder, buildGradleTempDirectory.getPath(), e.getMessage());
            return false;
        }
        logger.debug("copied folder {} to temp folder successfully", projectFolder);
        return true;
    }

    // append new task to bom file
    private boolean appendTaskToBomFile(File buildGradleTmp)  {
        FileReader fileReader = null;
        InputStream inputStream = null;
        boolean hasDependencies = false;
        try {
            // appending the task only if the build.gradle file has 'dependencies {' node (only at the beginning of the line)
            // otherwise, later when the task is ran it'll fail
            fileReader = new FileReader(buildGradleTmp);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String currLine;
            while ((currLine = bufferedReader.readLine()) != null) {
                if (currLine.indexOf(DEPENDENCIES + Constants.WHITESPACE + CURLY_BRACKETS) == 0 || currLine.indexOf(DEPENDENCIES + CURLY_BRACKETS) == 0){
                    hasDependencies = true;
                    break;
                }
            }
            if (hasDependencies) {
                ClassLoader classLoader = Main.class.getClassLoader();
                inputStream = classLoader.getResourceAsStream(COPY_DEPENDENCIES_TASK_TXT);
                byte[] bytes = IOUtils.toByteArray(inputStream);
                if (bytes.length > 0) {
                    Files.write(Paths.get(buildGradleTmp.getPath()), bytes, StandardOpenOption.APPEND);
                } else {
                    logger.warn("Could not read {}", COPY_DEPENDENCIES_TASK_TXT);
                }
            }
        } catch (IOException e) {
            logger.error("Could not write into the file {}, the cause {}", buildGradleTmp.getPath(), e.getMessage());
            hasDependencies = false;
        }
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            logger.error("Could close the file, cause", e.getMessage());
        }
        return hasDependencies;
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