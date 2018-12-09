package org.whitesource.fs;

import org.junit.Before;
import org.whitesource.agent.*;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author chen.luigi
 */
public class InitializeConfiguration {

    /* --- Static members --- */

    private static final String DIRECTORY_PATH = "\\src\\test\\resources\\via\\maven\\ksa\\ksa-core";
    private static final String CONFIG_PATH = "\\src\\test\\resources\\via\\whitesource-fs-agent-ksa.config";
    private static final String CONFIG = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath(CONFIG_PATH);
    private static final String DIRECTORY = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath(DIRECTORY_PATH);

    /* --- Private Members --- */

    private FSAConfigProperties config;
    private FSAConfiguration fsaConfiguration;
    private ProjectsSender projectsSender;
    private Map<AgentProjectInfo, LinkedList<ViaComponents>> projectToViaComponents;
    private String[] args = new String[]{};

    @Before
    public void setUp() throws IOException {
        config = new FSAConfigProperties();
        config.load(new FileInputStream(CONFIG));
        config.setProperty(Constants.DIRECTORY, DIRECTORY);
        String directory = config.getProperty(Constants.DIRECTORY);
        String[] directories = directory.split(Constants.COMMA);
        List<String> scannerBaseDirs = new ArrayList<>(Arrays.asList(directories));
        if (!scannerBaseDirs.isEmpty()) {
            // configure properties
            fsaConfiguration = new FSAConfiguration(config, args);
            projectsSender = new ProjectsSender(fsaConfiguration.getSender(), fsaConfiguration.getOffline(), fsaConfiguration.getRequest(), new FileSystemAgentInfo());
            // set default values in case of missing parameters
            ResolverConfiguration resolverConfiguration = fsaConfiguration.getResolver();
            String[] includes = config.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY) != null ?
                    config.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX) : ExtensionUtils.INCLUDES;
            String[] excludes = config.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY) != null ?
                    config.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX) : ExtensionUtils.EXCLUDES;
            boolean globCaseSensitive = config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY) != null ?
                    Boolean.valueOf(config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY)) : false;
            boolean followSymlinks = config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY) != null ?
                    Boolean.valueOf(config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY)) : false;
            Collection<String> excludedCopyrights = fsaConfiguration.getAgent().getExcludedCopyrights();
            excludedCopyrights.remove(Constants.EMPTY_STRING);
            // Resolving dependencies
            // via should not run for componentScan
            Set<String> setDirs = new HashSet<>();
            setDirs.addAll(scannerBaseDirs);
            Map<String, Set<String>> appPathsToDependencyDirs = new HashMap<>();
            appPathsToDependencyDirs.put(FSAConfiguration.DEFAULT_KEY, setDirs);
            ProjectConfiguration projectConfiguration = new ProjectConfiguration(fsaConfiguration.getAgent(), scannerBaseDirs, appPathsToDependencyDirs, false);
            projectToViaComponents = new FileSystemScanner(resolverConfiguration, fsaConfiguration.getAgent(), false).createProjects(projectConfiguration);
        }
    }

    /* --- Public methods --- */

    public Pair<String, StatusCode> sendProjects(ProjectsSender projectsSender, ProjectsDetails projectsDetails) {
        Collection<AgentProjectInfo> projects = projectsDetails.getProjects();
        Iterator<AgentProjectInfo> iterator = projects.iterator();
        while (iterator.hasNext()) {
            AgentProjectInfo project = iterator.next();
            if (project.getDependencies().isEmpty()) {
                iterator.remove();
                // if coordinates are null, then use token
                String projectIdentifier;
                Coordinates coordinates = project.getCoordinates();
                if (coordinates == null) {
                    projectIdentifier = project.getProjectToken();
                } else {
                    projectIdentifier = coordinates.getArtifactId();
                }
            }
        }

        if (projects.isEmpty()) {
            return new Pair<>("Exiting, nothing to update", StatusCode.SUCCESS);
        } else {
            return projectsSender.sendRequest(projectsDetails);//todo
        }
    }



    /* --- Getters / Setters --- */

    public Properties getConfig() {
        return config;
    }

    public void setConfig(FSAConfigProperties config) {
        this.config = config;
    }

    public FSAConfiguration getFsaConfiguration() {
        return fsaConfiguration;
    }

    public void setFsaConfiguration(FSAConfiguration fsaConfiguration) {
        this.fsaConfiguration = fsaConfiguration;
    }

    public Map<AgentProjectInfo, LinkedList<ViaComponents>> getProjectToViaComponents() {
        return projectToViaComponents;
    }

    public void setProjectToViaComponents(Map<AgentProjectInfo, LinkedList<ViaComponents>> projectToViaComponents) {
        this.projectToViaComponents = projectToViaComponents;
    }

    public ProjectsSender getProjectsSender() {
        return projectsSender;
    }

    public void setProjectsSender(ProjectsSender projectsSender) {
        this.projectsSender = projectsSender;
    }

    public String[] getArgs() {
        return args;
    }

    public void setArgs(String[] args) {
        this.args = args;
    }
}