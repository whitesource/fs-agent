package org.whitesource.agent.dependency.resolver.gradle;

import org.apache.commons.lang.StringUtils;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class GradleDependencyResolver extends AbstractDependencyResolver {

    private static final String BUILD_GRADLE = "**/*build.gradle";
    private static final List<String> GRADLE_SCRIPT_EXTENSION = Arrays.asList(".gradle", ".groovy", ".java", ".jar", ".war", ".ear", ".car", ".class");
    private static final String JAR_EXTENSION = ".jar";
    private static final String SETTINGS_GRADLE = "settings.gradle";
    protected static final String COMMENT_START = "/*";
    protected static final String COMMENT_END = "*/";

    private GradleLinesParser gradleLinesParser;
    private GradleCli gradleCli;
    private ArrayList<String> topLevelFoldersNames;
    private boolean dependenciesOnly;
    private boolean gradleAggregateModules;

    private final Logger logger = LoggerFactory.getLogger(GradleDependencyResolver.class);

    public GradleDependencyResolver(boolean runAssembleCommand, boolean dependenciesOnly, boolean gradleAggregateModules) {
        super();
        gradleLinesParser = new GradleLinesParser(runAssembleCommand);
        gradleCli = new GradleCli();
        topLevelFoldersNames = new ArrayList<>();
        this.dependenciesOnly = dependenciesOnly;
        this.gradleAggregateModules = gradleAggregateModules;
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        // each bom-file ( = build.gradle) represents a module - identify its folder and scan it using 'gradle dependencies'
        Map<AgentProjectInfo, Path> projectInfoPathMap = new HashMap<>();
        Collection<String> excludes = new HashSet<>();
        String settingsFileContent = null;
        ArrayList<Integer[]> commentBlocks = null;
        if (bomFiles.size() > 1) {
            settingsFileContent = readSettingsFile(topLevelFolder);
            commentBlocks = findCommentBlocksInSettingsFile(settingsFileContent);
        }
        for (String bomFile : bomFiles) {
            String bomFileFolder = new File(bomFile).getParent();
            File bomFolder = new File(new File(bomFile).getParent());
            String moduleName = bomFolder.getName();
            // making sure the module's folder is found inside the settings.gradle file
            if (StringUtils.isNotBlank(settingsFileContent) && !validateModule(moduleName, settingsFileContent, commentBlocks)) {
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
        return new String[]{BUILD_GRADLE};
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    private String readSettingsFile(String folder) {
        String content = "";
        File settingsFile = new File(folder + fileSeparator + SETTINGS_GRADLE);
        if (settingsFile.isFile()) {
            try {
                content = new String(Files.readAllBytes(Paths.get(settingsFile.getPath())));
            } catch (IOException e) {
                logger.warn("could not read settings file {} - {}", settingsFile.getPath(), e.getMessage());
                logger.debug("stacktrace {}", e.getStackTrace());
            }
        }
        return content;
    }

    private ArrayList<Integer[]> findCommentBlocksInSettingsFile(String content) {
        ArrayList<Integer[]> commentBlock = new ArrayList<>();
        int startIndex = content.indexOf(COMMENT_START);
        int endIndex;
        while (startIndex > -1) {
            endIndex = content.indexOf(COMMENT_END, startIndex);
            commentBlock.add(new Integer[]{startIndex, endIndex});
            startIndex = content.indexOf(COMMENT_START, endIndex);
        }
        return commentBlock;
    }

    /* valid modules are proceeded by ' or " or :, and followed by ' or ", and also not proceeded (in the same line) by =
       also - making sure the line isn't commented out

       //include 'echoserver'
        include 'client'
        rootProject.name = 'multi-project-gradle'

        only the second line is valid
        also - making sure the module isn't inside comment block
     */
    private boolean validateModule(String moduleName, String settings, ArrayList<Integer[]> commentBlocks) {
        if (settings != null && settings.contains(moduleName)) {
            int startIndex = settings.indexOf(moduleName);
            char proceedingChar = settings.charAt(startIndex - 1);
            if (proceedingChar == Constants.QUOTATION_MARK.charAt(0) || proceedingChar == Constants.APOSTROPHE.charAt(0) || proceedingChar == Constants.COLON.charAt(0)) {
                int endIndex = startIndex + moduleName.length();
                char followingChar = settings.charAt(endIndex);
                if (followingChar == Constants.QUOTATION_MARK.charAt(0) || followingChar == Constants.APOSTROPHE.charAt(0)) {
                    while (settings.charAt(startIndex) != '\r' && startIndex > 0) {
                        startIndex--;
                        if (settings.charAt(startIndex) == Constants.EQUALS_CHAR) {
                            return false;
                        }
                        // making sure there are no // before the module nams
                        if (settings.charAt(startIndex) == Constants.FORWARD_SLASH.charAt(0) && startIndex > 0 && settings.charAt(startIndex - 1) == Constants.FORWARD_SLASH.charAt(0)) {
                            return false;
                        }
                    }
                    // making sure the module isn't inside comment block
                    if (commentBlocks != null) {
                        for (Integer[] commentBlock : commentBlocks) {
                            if (settings.indexOf(moduleName) > commentBlock[0] && endIndex < commentBlock[1]) {
                                return false;
                            }
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private List<DependencyInfo> collectDependencies(String rootDirectory) {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        List<String> lines = gradleCli.runGradleCmd(rootDirectory, gradleCli.getGradleCommandParams(MvnCommand.DEPENDENCIES));
        if (lines != null) {
            dependencyInfos.addAll(gradleLinesParser.parseLines(lines, rootDirectory));
        }
        return dependencyInfos;
    }
}