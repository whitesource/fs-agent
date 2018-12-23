package org.whitesource.agent.dependency.resolver.php;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.whitesource.agent.hash.HashCalculator;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.php.phpModel.PhpModel;
import org.whitesource.agent.dependency.resolver.php.phpModel.PhpPackage;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.*;
import java.util.*;

import static org.whitesource.agent.Constants.FORWARD_SLASH;
import static org.whitesource.agent.Constants.INSTALL;
import static org.whitesource.agent.Constants.PATTERN;

/**
 * @author chen.luigi
 */
public class PhpDependencyResolver extends AbstractDependencyResolver {

    /* --- Static members --- */

    private static final String COMPOSER_LOCK = "composer.lock";
    private static final String COMPOSER_JSON = "composer.json";
    private static final String PHP_EXTENSION = ".php";
    private static final String COMPOSER_BAT = "composer.bat";
    private static final String COMPOSER = "composer";
    private static final String PHP_INCLUDE_NO_DEV = "--no-dev";
    private static final String REQUIRE = "require";
    private static final String REQUIRE_DEV = "require-dev";
    private static final String PHP = "php";
    private static final List<String> PHP_PATTERN_EXTENSION = Arrays.asList(PATTERN + ".php");

    /* --- Private Members --- */

    private final Logger logger = LoggerFactory.getLogger(PhpDependencyResolver.class);

    private boolean phpPreStep;
    private boolean includeDevDependencies;
    private HashCalculator hashCalculator = new HashCalculator();
    private boolean addSha1;

    /* --- Constructors --- */

    public PhpDependencyResolver(boolean phpPreStep, boolean includeDevDependencies, boolean addSha1) {
        super();
        this.phpPreStep = phpPreStep;
        this.includeDevDependencies = includeDevDependencies;
        this.addSha1 = addSha1;
    }

    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        boolean installSuccess = true;
        Collection<DependencyInfo> dependencyInfos = new LinkedList<>();
        Collection<String> directDependencies = new LinkedList<>();
        File composerLock = new File(topLevelFolder + FORWARD_SLASH + COMPOSER_LOCK);

        // run pre step according to phpPreStep flag
        if (phpPreStep) {
            installSuccess = !executePreStepCommand(topLevelFolder);
        } else {
            if (!composerLock.exists()) {
                logger.warn("Could not find {} file in {}. Please execute {} {} first.", COMPOSER_LOCK, topLevelFolder, COMPOSER, INSTALL);
            }
        }
        if (installSuccess && composerLock.exists()) {
            try {
                Map<String, Object> requireMap = new HashMap<>();
                InputStream is = new FileInputStream(topLevelFolder + FORWARD_SLASH + COMPOSER_JSON);
                String jsonText = IOUtils.toString(is);
                JSONObject json = new JSONObject(jsonText);
                if (json.has(REQUIRE)) {
                    JSONObject require = json.getJSONObject(REQUIRE);
                    requireMap = require.toMap();
                }
                if (includeDevDependencies) {
                    if (json.has(REQUIRE_DEV)) {
                        JSONObject requireDev = json.getJSONObject(REQUIRE_DEV);
                        Map<String, Object> requireDevMap = requireDev.toMap();
                        requireMap.putAll(requireDevMap);
                    }
                }
                if (!requireMap.isEmpty()) {
                    if (requireMap.containsKey(PHP)) {
                        requireMap.remove(PHP);
                    }
                    directDependencies.addAll(requireMap.keySet());
                }

            } catch (IOException e) {
                logger.error("Didn't succeed to read {} - {} ", COMPOSER_JSON, e.getMessage());
            }
        }

        if (!directDependencies.isEmpty()) {
            try {
                JsonReader jsonReader = new JsonReader(new FileReader(topLevelFolder + FORWARD_SLASH + COMPOSER_LOCK));
                PhpModel phpModel = new Gson().fromJson(jsonReader, PhpModel.class);
                Collection<PhpPackage> phpPackages = phpModel.getPhpPackages();
                if (includeDevDependencies) {
                    phpPackages.addAll(phpModel.getPhpPackagesDev());
                }
                if (!phpPackages.isEmpty()) {
                    dependencyInfos = createDependencyInfos(phpPackages, dependencyInfos, directDependencies);
                } else {
                    logger.debug("The file {} is empty", COMPOSER_LOCK);
                }
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
        return new ResolutionResult(dependencyInfos, getExcludes(), getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        return PHP_PATTERN_EXTENSION;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return new ArrayList<>(Arrays.asList(PHP_EXTENSION, COMPOSER_JSON));
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.PHP;
    }

    @Override
    protected String getDependencyTypeName() {
        return DependencyType.PHP.name();
    }

    @Override
    public String[] getBomPattern() {
        return new String[]{Constants.PATTERN + COMPOSER_JSON};
    }

    @Override
    public Collection<String> getManifestFiles(){
        return Arrays.asList(COMPOSER_JSON);
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new LinkedList<>();
    }

    /* --- Private methods --- */

    // create dependencyInfo objects from each direct dependency
    private Collection<DependencyInfo> createDependencyInfos(Collection<PhpPackage> phpPackages, Collection<DependencyInfo> dependencyInfos, Collection<String> directDependencies) {
        HashMap<DependencyInfo, Collection<String>> parentToChildMap = new HashMap<>();
        HashMap<String, DependencyInfo> packageDependencyMap = new HashMap<>();
        // collect packages data and create its dependencyInfo
        for (PhpPackage phpPackage : phpPackages) {
            DependencyInfo dependencyInfo = createDependencyInfo(phpPackage);
            if (dependencyInfo != null) {
                parentToChildMap.put(dependencyInfo, phpPackage.getPackageRequire().keySet());
                packageDependencyMap.put(phpPackage.getName(), dependencyInfo);
            } else {
                logger.debug("Didn't succeed to create dependencyInfo for {}", phpPackage.getName());
            }
        }
        if (!packageDependencyMap.isEmpty()) {
            for (String directDependency : directDependencies) {
                // create hierarchy tree
                DependencyInfo dependencyInfo = packageDependencyMap.get(directDependency);
                if (dependencyInfo != null) {
                    collectChildren(dependencyInfo, packageDependencyMap, parentToChildMap);
                    dependencyInfos.add(dependencyInfo);
                } else {
                    logger.debug("Didn't found {} in map {}", directDependency, packageDependencyMap.getClass().getName());
                }
            }
        } else {
            logger.debug("The map {} is empty ", packageDependencyMap.getClass().getName());
        }
        return dependencyInfos;
    }

    // convert phpPackage to dependencyInfo object
    private DependencyInfo createDependencyInfo(PhpPackage phpPackage) {
        String groupId = getGroupIdFromName(phpPackage);
        String artifactId = phpPackage.getName();
        String version = phpPackage.getVersion();
        String commit = phpPackage.getPackageSource().getReference();
        if (StringUtils.isNotBlank(version) || StringUtils.isNotBlank(commit)) {
            DependencyInfo dependencyInfo = new DependencyInfo(groupId, artifactId, version);
            dependencyInfo.setCommit(commit);
            dependencyInfo.setDependencyType(getDependencyType());
            if (this.addSha1) {
                String sha1 = null;
                String sha1Source = StringUtils.isNotBlank(version) ? version : commit;
                try {
                    sha1 = this.hashCalculator.calculateSha1ByNameVersionAndType(artifactId, sha1Source, DependencyType.PHP);
                } catch (IOException e) {
                    logger.debug("Failed to calculate sha1 of: {}", artifactId);
                }
                if (sha1 != null) {
                    dependencyInfo.setSha1(sha1);
                }
            }
            return dependencyInfo;
        } else {
            logger.debug("The parameters version and commit of {} are null", phpPackage.getName());
            return null;
        }
    }

    // collect children's recursively for each dependencyInfo object
    private void collectChildren(DependencyInfo dependencyInfo, HashMap<String, DependencyInfo> packageDependencyMap,
                                 HashMap<DependencyInfo, Collection<String>> requireDependenciesMap) {
        Collection<String> requires = requireDependenciesMap.get(dependencyInfo);
        // check if dependencyInfo object already have children's
        if (dependencyInfo.getChildren().isEmpty()) {
            for (String require : requires) {
                DependencyInfo dependencyChild = packageDependencyMap.get(require);
                if (dependencyChild != null) {
                    dependencyInfo.getChildren().add(dependencyChild);
                    collectChildren(dependencyChild, packageDependencyMap, requireDependenciesMap);
                }
            }
        }
    }

    // get the groupId from the name of package
    private String getGroupIdFromName(PhpPackage phpPackage) {
        String groupId = null;
        if (StringUtils.isNotBlank(phpPackage.getName())) {
            String packageName = phpPackage.getName();
            String[] gavCoordinates = packageName.split(FORWARD_SLASH);
            groupId = gavCoordinates[0];
        }
        return groupId;
    }

    // get the artifactId from the name of package
    private String getArtifactIdFromName(PhpPackage phpPackage) {
        String artifactId = null;
        if (StringUtils.isNotBlank(phpPackage.getName())) {
            String packageName = phpPackage.getName();
            String[] gavCoordinates = packageName.split(FORWARD_SLASH);
            artifactId = gavCoordinates[1];
        }
        return artifactId;
    }

    // execute pre step command (composer install)
    private boolean executePreStepCommand(String topLevelFolder) {
        String[] command;
        if (DependencyCollector.isWindows()) {
            command = getCommand(COMPOSER_BAT);
        } else {
            command = getCommand(COMPOSER);
        }
        String commandString = String.join(Constants.WHITESPACE, command);
        File file = new File(topLevelFolder + FORWARD_SLASH + COMPOSER_JSON);
        CommandLineProcess composerInstall = null;
        if (file.exists()) {
            logger.info("Running install command : {}", commandString);
            composerInstall = new CommandLineProcess(topLevelFolder, command);
        }
        try {
            composerInstall.executeProcessWithoutOutput();
        } catch (IOException e) {
            logger.warn("Could not run {} in folder {} : {}", commandString, topLevelFolder, e.getMessage());
            return true;
        }
        return composerInstall.isErrorInProcess();
    }

    private String[] getCommand(String firstCommand) {
        String[] command;
        if (includeDevDependencies) {
            command = new String[]{firstCommand, INSTALL};
        } else {
            command = new String[]{firstCommand, INSTALL, PHP_INCLUDE_NO_DEV};
        }
        return command;
    }
}
