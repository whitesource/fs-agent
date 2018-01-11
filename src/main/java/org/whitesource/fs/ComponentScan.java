package org.whitesource.fs;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.fs.configuration.ConfigurationSerializer;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.util.*;


/**
 * Created by anna.rozin
 */
public class ComponentScan {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ComponentScan.class);

    /* --- Members --- */
    private  Properties config;

    /* --- Constructors --- */


    /* --- Methods --- */

    public ComponentScan(Properties config) {
        this.config = config;
    }

    public String scan() {
        String d = config.getProperty("d");
//        List<String>  scannerBaseDirs = (List<String>) config.get("d");
        List<String>  scannerBaseDirs = new ArrayList<>();
        scannerBaseDirs.add(d);
        FSAConfiguration fsaConfiguration = new FSAConfiguration(config);
        config.setProperty(ConfigPropertyKeys.PROJECT_TOKEN_PROPERTY_KEY, "anna");
        ResolverConfiguration resolverConfiguration = new ResolverConfiguration(config);
        String[] includes = config.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY) != null ?
                config.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX) : ExtensionUtils.INCLUDES;
        String[] excludes = config.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY) != null ?
                config.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX) : ExtensionUtils.EXCLUDES;

        boolean globCaseSensitive = config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY) != null ?
                Boolean.valueOf(config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY)) : false;
        boolean followSymlinks = config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY) != null ?
                Boolean.valueOf(config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY)) : false;
        Collection<String> excludedCopyrights = new ArrayList<>(Arrays.asList(fsaConfiguration.getExcludedCopyrightsValue().split(",")));
        excludedCopyrights.remove("");
        //todo hasScmConnectors[0] check it
        Collection<AgentProjectInfo> projects = new FileSystemScanner(true, new DependencyResolutionService(resolverConfiguration)).createProjects(
                scannerBaseDirs, false, includes, excludes, globCaseSensitive, fsaConfiguration.getArchiveExtractionDepth(),
                fsaConfiguration.getArchiveIncludes(), fsaConfiguration.getArchiveExcludes(), fsaConfiguration.isArchiveFastUnpack(), followSymlinks, excludedCopyrights,
                fsaConfiguration.isPartialSha1Match(), fsaConfiguration.isCalculateHints(), fsaConfiguration.isCalculateMd5());

        String jsonString = new ConfigurationSerializer<>().getAsString(projects);
        return jsonString;
    }

}
