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

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class GradleDependencyResolver extends AbstractDependencyResolver {

    private static final String BUILD_GRADLE = "**/*build.gradle";
    private static final List<String> GRADLE_SCRIPT_EXTENSION = Arrays.asList(".gradle",".groovy", ".java", ".jar", ".war", ".ear", ".car", ".class");
    private static final String JAR_EXTENSION = ".jar";
    private static final String SETTINGS_GRADLE = "settings.gradle";

    private GradleLinesParser gradleLinesParser;
    private GradleCli gradleCli;
    private ArrayList<String> topLevelFoldersNames;
    private boolean dependenciesOnly;
    private boolean gradleAggregateModules;

    private final Logger logger = LoggerFactory.getLogger(GradleDependencyResolver.class);

    public GradleDependencyResolver(boolean runAssembleCommand, boolean dependenciesOnly, boolean gradleAggregateModules){
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
        Collection<AgentProjectInfo> projects = new ArrayList<>();
        List<String> modules = new LinkedList<>();
        if (bomFiles.size() > 1){ // reading the 'settings.gradle' to extract a list of modules
            modules = getModules(topLevelFolder);
        }
        for (String bomFile : bomFiles){
            String bomFileFolder = new File(bomFile).getParent();
            File bomFolder = new File(new File(bomFile).getParent());
            String moduleName = bomFolder.getName();
            // making sure the module's folder is found in the modules' list taken from the settings.gradle
            if (modules.size() > 0 && !modules.contains(moduleName)){
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
                projects.add(agentProjectInfo);
            }
        }
        topLevelFoldersNames.add(topLevelFolder.substring(topLevelFolder.lastIndexOf(fileSeparator) + 1));
        Collection<String> excludes = getExcludes();
        Map<AgentProjectInfo, Path> projectInfoPathMap = projects.stream().collect(Collectors.toMap(projectInfo -> projectInfo, projectInfo -> {
            if (dependenciesOnly) {
                excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder, GRADLE_SCRIPT_EXTENSION, null));
            }
            return Paths.get(topLevelFolder);
        }));

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

    private List<String> getModules(String folder){
        List<String> modules = new LinkedList<>();
        File settingsFile = new File(folder + fileSeparator + SETTINGS_GRADLE);
        if (settingsFile.isFile()){
            FileReader fileReader = null;
            try {
                fileReader = new FileReader(settingsFile);
                BufferedReader bufferedReader = new BufferedReader(fileReader);
                String currLine;
                while ((currLine = bufferedReader.readLine()) != null){
                    if (currLine.startsWith("include")){
                        if (currLine.contains(Constants.COMMA)){
                            String[] lineModules = currLine.split(Constants.COMMA);
                            for (String lineModule : lineModules){
                                modules.add(getModuleName(lineModule));
                            }
                        } else {
                            modules.add(getModuleName(currLine));
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                logger.warn("Could not find settings.gradle {}", e.getMessage());
                logger.debug("stacktrace {}", e.getStackTrace());
            } catch (IOException e) {
                logger.warn("Could not parse settings.gradle {}", e.getMessage());
                logger.debug("stacktrace {}", e.getStackTrace());
            } finally {
                try {
                    fileReader.close();
                } catch (Exception e) {
                    logger.warn("Can't close settings.gradle {}", e.getMessage());
                    logger.debug("stacktrace {}", e.getStackTrace());
                }
            }
        }
        return modules;
    }

    private String getModuleName(String module){
        // Module can include single or double quotation, like:
        // include "java-app" or include "libraries:common"
        // include ':app'
        // include 'blue', 'krill'
        // include 'api', 'services:task'
        // include ':app',
        //    ':feature:my-first-feature',
        //    ':feature:my-second-feature',
        // include ':app', ':feature:my-first-feature', ':feature:my-second-feature',

        // This change was only added to prevent exceptions in my Gradle tests, a full solution should be added that
        // fixes the above cases ...

        String separator = null;
        if(module.indexOf(Constants.APOSTROPHE) > 0) {
            separator = Constants.APOSTROPHE;
        } else if (module.indexOf(Constants.QUOTATION_MARK) > 0) {
            separator = Constants.QUOTATION_MARK;
        }
        if (separator != null) {
            module = module.substring(module.indexOf(separator) + 1, module.lastIndexOf(separator));
            if (module.indexOf(Constants.COLON) > -1){
                module = module.substring(module.indexOf(Constants.COLON) + 1);
            }
        }

        return module;
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