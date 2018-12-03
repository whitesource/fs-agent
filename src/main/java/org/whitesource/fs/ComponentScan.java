package org.whitesource.fs;

import org.slf4j.Logger;
import org.whitesource.agent.ProjectConfiguration;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.Constants;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.docker.DockerResolver;
import org.whitesource.agent.dependency.resolver.packageManger.PackageManagerExtractor;
import org.whitesource.fs.configuration.ConfigurationSerializer;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.util.*;


/**
 * Created by anna.rozin
 */
public class ComponentScan {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(ComponentScan.class);

    /* --- Members --- */

    private FSAConfigProperties config;

    /* --- Constructors --- */

    public ComponentScan(FSAConfigProperties config) {
        this.config = config;
    }

    /* --- Methods --- */

    public String scan() {
        logger.info("Starting analysis - component scan has started");
        String directory = config.getProperty(Constants.DIRECTORY);
        String[] directories = directory.split(Constants.COMMA);
        List<String> scannerBaseDirs = new ArrayList<>(Arrays.asList(directories));
        if (!scannerBaseDirs.isEmpty()) {
            logger.info("Getting properties");
            // configure properties
            FSAConfiguration fsaConfiguration = new FSAConfiguration(config);
            // set default values in case of missing parameters
            ResolverConfiguration resolverConfiguration = fsaConfiguration.getResolver();
            String[] includes = config.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY) != null ?
                    config.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX) : ExtensionUtils.INCLUDES;
            String[] excludes = config.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY) != null ?
                    config.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX) : ExtensionUtils.EXCLUDES;
            String[] acceptExtensionsList = (String[]) config.get(ConfigPropertyKeys.ACCEPT_EXTENSIONS_LIST);
            boolean globCaseSensitive = config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY) != null ?
                    Boolean.valueOf(config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY)) : false;
            boolean followSymlinks = config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY) != null ?
                    Boolean.valueOf(config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY)) : false;
            Collection<String> excludedCopyrights = fsaConfiguration.getAgent().getExcludedCopyrights();
            excludedCopyrights.remove(Constants.EMPTY_STRING);
            //todo hasScmConnectors[0] in future - no need for cx
            // Resolving dependencies
            logger.info("Resolving dependencies");
            // via should not run for componentScan
            Set<String> setDirs = new HashSet<>();
            setDirs.addAll(scannerBaseDirs);
            Map<String, Set<String>> appPathsToDependencyDirs = new HashMap<>();
            appPathsToDependencyDirs.put(FSAConfiguration.DEFAULT_KEY, setDirs);
            Collection<AgentProjectInfo> projects;
            // scan packageManager||Docker||Regular Scan
            if (Boolean.valueOf(config.getProperty(ConfigPropertyKeys.SCAN_PACKAGE_MANAGER))) {
                projects = new PackageManagerExtractor().createProjects();
            } else if (Boolean.valueOf(config.getProperty(ConfigPropertyKeys.SCAN_DOCKER_IMAGES))) {
                projects = new DockerResolver(fsaConfiguration).resolveDockerImages();
            } else {
                ProjectConfiguration projectConfiguration = new ProjectConfiguration(fsaConfiguration.getAgent(), scannerBaseDirs, appPathsToDependencyDirs, false);
                projects = new FileSystemScanner(resolverConfiguration, fsaConfiguration.getAgent(), false).createProjects(projectConfiguration).keySet();
            }

            logger.info("Finished dependency resolution");
            for (AgentProjectInfo project : projects) {
                project.setProjectToken(Constants.WHITESPACE);
                if (acceptExtensionsList != null && acceptExtensionsList.length > 0) {
                    project.setDependencies(getDependenciesFromExtensionsListOnly(project.getDependencies(), acceptExtensionsList));
                }
            }
            //             Return dependencies
            String jsonString = new ConfigurationSerializer().getAsString(projects, true);
            return jsonString;
        } else {
            return Constants.EMPTY_STRING;// new ConfigurationSerializer<>().getAsString(new Collection<AgentProjectInfo>);
        }
    }

    private List<DependencyInfo> getDependenciesFromExtensionsListOnly(Collection<DependencyInfo> dependencies, String[] acceptExtensionsList) {
        LinkedList<DependencyInfo> filteredDependencies = new LinkedList<>();
        for (DependencyInfo dependency : dependencies) {
            for (String extension : acceptExtensionsList) {
                if (dependency.getDependencyType() != null || dependency.getArtifactId().endsWith(Constants.DOT + extension) || checkFileName(dependency, extension)) {
                    filteredDependencies.add(dependency);
                    dependency.setChildren(getDependenciesFromExtensionsListOnly(dependency.getChildren(), acceptExtensionsList));
                    break;
                }
            }
        }
        return filteredDependencies;
    }

    private boolean checkFileName(DependencyInfo dependency, String extension) {
        boolean fileNameEndsWithExtension = false;
        if (dependency.getFilename() != null) {
            fileNameEndsWithExtension = dependency.getFilename().endsWith(Constants.DOT + extension);
        }
        return fileNameEndsWithExtension;
    }
}

