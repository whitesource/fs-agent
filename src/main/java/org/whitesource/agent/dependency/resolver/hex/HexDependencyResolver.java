package org.whitesource.agent.dependency.resolver.hex;

import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.utils.Cli;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HexDependencyResolver extends AbstractDependencyResolver {

    private static final List<String> HEX_SCRIPT_EXTENSION = Arrays.asList(".ex");
    private static final String MIX_EXS = "mix.exs";
    private static final String MIX_LOCK = "mix.lock";
    private static final String MIX = "mix";
    private static final String DEPS_GET = "deps.get";
    private static final String DEPS_TREE = "deps.tree";
    private static final String ACCENT = "`";
    private static final String HEX_REGEX = "\"(\\w+)\": \\{:hex, :\\w+, \"(\\d+\\.\\d+\\.\\d+(?:-\\w+(?:\\.\\w+)*)?(?:\\+\\w+)?)\", \"(\\w+)\"";
    private static final String GIT_REGEX = "\"(\\w+)\": \\{:git, \"(https|http|):/\\/github.com\\/\\w+\\/\\w+.git\", \"(\\w+)\"";
    private static final String TREE_REGEX = "\\s(\\w+)\\s(~>\\s(\\d+\\.\\d+(\\.\\d+)?(?:-\\w+(?:\\.\\w+)*)?(?:\\+\\w+)?))?";
    private static final String VERSION_REGEX = "(\\d+\\.\\d+(\\.\\d+)?(?:-\\w+(?:\\.\\w+)*)?(?:\\+\\w+)?)";
    public static final String TAR_EXTENSION = ".tar";
    private static final String GIT = ":git,";
    private static final String MODULE_START = "==>";
    private static final String APPS = "apps";
    private static final String LINUX_PIPE = "│";
    private static final String LINUX_CHAR_1 = "├";
    private static final String LINUX_CHAR_2 = "└";

    private final Logger logger = LoggerFactory.getLogger(HexDependencyResolver.class);
    private boolean ignoreSourceFiles;
    private boolean runPreStep;
    private boolean aggregateModules;
    private Cli cli;
    private String dotHexCachePath;

    public HexDependencyResolver(boolean ignoreSourceFiles, boolean runPreStep, boolean aggregateModules){
        this.ignoreSourceFiles = ignoreSourceFiles;
        this.runPreStep = runPreStep;
        this.aggregateModules = aggregateModules;
        cli = new Cli();
        String currentUsersHomeDir = System.getProperty(Constants.USER_HOME);
        File dotHexCache = Paths.get(currentUsersHomeDir, ".hex", "packages","hexpm").toFile();
        if (dotHexCache.exists()){
            dotHexCachePath = dotHexCache.getAbsolutePath();
        }
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        if (this.runPreStep){
            runPreStep(topLevelFolder);
        }
        Map<AgentProjectInfo, Path> projectInfoPathMap = new HashMap<>();
        File mixLock = new File (topLevelFolder + fileSeparator + MIX_LOCK);
        if (mixLock.exists()){
            Collection<String> excludes = new HashSet<>();
            HashMap<String, DependencyInfo> dependencyInfoMap = parseMixLoc(mixLock);
            HashMap<String, List<DependencyInfo>> modulesMap = parseMixTree(topLevelFolder, dependencyInfoMap);
            if (!modulesMap.isEmpty()){
                for (String moduleName : modulesMap.keySet()){
                    if (modulesMap.get(moduleName).size() > 0) {
                        AgentProjectInfo agentProjectInfo = new AgentProjectInfo();
                        agentProjectInfo.getDependencies().addAll(modulesMap.get(moduleName));
                        if (!aggregateModules && (modulesMap.size() > 1 || !moduleName.equals(topLevelFolder))) {
                            Coordinates coordinates = new Coordinates();
                            coordinates.setArtifactId(moduleName);
                            agentProjectInfo.setCoordinates(coordinates);
                        }
                        File bomFolder = new File(moduleName.equals(topLevelFolder) ? moduleName : topLevelFolder +
                                fileSeparator + APPS + fileSeparator + moduleName);
                        projectInfoPathMap.put(agentProjectInfo, bomFolder.toPath());
                    }
                }
                if (ignoreSourceFiles) {
                    excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder, extensionPattern(HEX_SCRIPT_EXTENSION), null));
                }
            }
        } else {
            logger.warn("Can't find {}", mixLock.getPath());
        }
        Collection<String> excludes = new HashSet<>();
        excludes.addAll(getExcludes());
        ResolutionResult resolutionResult;
        if (!aggregateModules) {
            resolutionResult = new ResolutionResult(projectInfoPathMap, excludes, getDependencyType(), topLevelFolder);
        } else {
            resolutionResult = new ResolutionResult(projectInfoPathMap.keySet().stream()
                    .flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList()), excludes, getDependencyType(), topLevelFolder);
        }
        return resolutionResult;
    }

    private void runPreStep(String folderPath){
        logger.info("running hex pre-step");
        List<String> compileOutput = cli.runCmd(folderPath, cli.getCommandParams(MIX, DEPS_GET));
        if (compileOutput.isEmpty()) {
            logger.warn("Can't run '{} {}'", MIX, DEPS_GET);
        }
    }

    // this method is public only for testing purposes
    public HashMap<String, List<DependencyInfo>> parseMixTree(String folderPath, HashMap<String, DependencyInfo> dependencyInfoMap){
        logger.info("Hex - parsing mix tree");
        List<String> lines = cli.runCmd(folderPath, cli.getCommandParams(MIX, DEPS_TREE));
        int currentLevel;
        int prevLevel = 0;
        boolean insideModule = false;
        HashMap<String, List<DependencyInfo>> modulesMap = new HashMap<>();
        List<DependencyInfo> dependenciesList = new ArrayList<>();
        Stack<DependencyInfo> parentDependencies = new Stack<>();
        Pattern treePattern = Pattern.compile(TREE_REGEX);

        Matcher matcher;
        String moduleName = null;
        if (lines != null){
            for (String line : lines){
                try {
                    if (line.startsWith(MODULE_START)) {
                        moduleName = line.split(Constants.WHITESPACE)[1];
                        modulesMap.put(moduleName, new ArrayList<>());
                        parentDependencies.clear();
                    } else {
                        if (line.startsWith(Constants.PIPE) || line.startsWith(ACCENT) || line.startsWith(Constants.WHITESPACE)
                                || line.startsWith(LINUX_CHAR_1) || line.startsWith(LINUX_CHAR_2) || line.startsWith(LINUX_PIPE)) {
                        /**
                         - dependency's line starts with either |, ` or white-space in windows,
                           and ├ (LINUX_CHAR_1), │ (LINUX_PIPE) or └  (LINUX_CHAR_2) in linux
                         - each level is has 4 more spaces than its parent level, therefore by dividing the index of dash by 4
                           to find the line's level
                         code example:

                        WINDOWS
                        telemetry
                        |-- erlang_pmp ~> 0.1 (Hex package)
                        |-- dialyxir ~> 1.0.0-rc.1 (Hex package)
                        `-- ex_doc ~> 0.19 (Hex package)
                            |-- earmark ~> 1.1 (Hex package)
                            `-- makeup_elixir ~> 0.7 (Hex package)
                                |-- makeup ~> 0.5.0 (Hex package)
                                |   `-- nimble_parsec ~> 0.2.2 (Hex package)
                                `-- nimble_parsec ~> 0.2.2 (Hex package)
                        LINUX
                        telemetry
                        ├── erlang_pmp ~> 0.1 (Hex package)
                        ├── dialyxir ~> 1.0.0-rc.1 (Hex package)
                        └── ex_doc ~> 0.19 (Hex package)
                            ├── earmark ~> 1.1 (Hex package)
                            └── makeup_elixir ~> 0.7 (Hex package)
                                ├── makeup ~> 0.5.0 (Hex package)
                                │   └── nimble_parsec ~> 0.2.2 (Hex package)
                                └── nimble_parsec ~> 0.2.2 (Hex package)

                        **/
                            if (DependencyCollector.isWindows()){
                                currentLevel = (line.indexOf(Constants.DASH) - 1) / 4;
                            } else {
                                currentLevel = Math.max(line.indexOf(LINUX_CHAR_1), line.indexOf(LINUX_CHAR_2)) / 4;
                            }
                            matcher = treePattern.matcher(line);
                            if (matcher.find()) {
                                if (insideModule && currentLevel > 0) {
                                    continue;
                                }
                                insideModule = false;
                                String name = matcher.group(1);
                                String version = matcher.group(3);
                                DependencyInfo dependencyInfo = dependencyInfoMap.get(name);
                                if (dependencyInfo != null) {
                                    getSha1AndVersion(dependencyInfo, version);
                                    if (currentLevel == prevLevel) { // siblings dependencies
                                        if (!parentDependencies.isEmpty()) {
                                            parentDependencies.pop();
                                            if (!parentDependencies.isEmpty()) {
                                                addTransitiveDependency(parentDependencies.peek(), dependencyInfo);
                                            }
                                        }
                                        if (parentDependencies.isEmpty()) {
                                            (moduleName == null ? dependenciesList : modulesMap.get(moduleName)).add(dependencyInfo);
                                        }
                                        parentDependencies.push(dependencyInfo);
                                    } else if (currentLevel > prevLevel) { // transitive dependency
                                        if (!parentDependencies.isEmpty()) {
                                            addTransitiveDependency(parentDependencies.peek(), dependencyInfo);
                                        }
                                        parentDependencies.push(dependencyInfo);
                                    } else { // dependency with higher hierarchy level than previous one
                                        while (prevLevel > currentLevel - 1 && !parentDependencies.isEmpty()) {
                                            parentDependencies.pop();
                                            prevLevel--;
                                        }
                                        if (!parentDependencies.isEmpty()) {
                                            addTransitiveDependency(parentDependencies.peek(), dependencyInfo);
                                        } else {
                                            (moduleName == null ? dependenciesList : modulesMap.get(moduleName)).add(dependencyInfo); // root dependency
                                        }
                                        parentDependencies.push(dependencyInfo);
                                    }
                                } else if (modulesMap.keySet().contains(name)) {
                                    insideModule = true;
                                    parentDependencies.clear();
                                }
                                prevLevel = currentLevel;
                            }
                        }
                    }
                } catch (Exception e){
                    logger.warn("Failed parsing line '{}', error: {}", line, e.getMessage());
                    logger.debug("Exception: {}", e.getStackTrace());
                }
            }
        }
        if (modulesMap.isEmpty()){
            modulesMap.put(folderPath,dependenciesList);
        }
        return modulesMap;
    }

    // this method is public only for testing purposes
    public HashMap<String, DependencyInfo> parseMixLoc(File mixLock){
        logger.info("Hex - parsing " + mixLock.getPath());
        HashMap<String, DependencyInfo> dependencyInfoHashMap = new HashMap<>();
        FileReader fileReader;
        BufferedReader bufferedReader;
        /*
        lines of the mix.lock file can be of 2 types - hex of git
        "artificery": {:hex, :artificery, "0.2.6", "f602909757263f7897130cbd006b0e40514a541b148d366ad65b89236b93497a", [:mix], [], "hexpm"},
        "aruspex": {:git, "https://github.com/oyeb/aruspex.git", "5ca5ca6057b61b2bc19a58abd3a5a656c39d0249", [branch: "tweaks"]},

        using regex to identify the name, version in case of hex, and commit id in case git
        * */
        try {
            Pattern hexPattern = Pattern.compile(HEX_REGEX);
            Pattern gitPattern = Pattern.compile(GIT_REGEX);
            Matcher matcher;
            fileReader = new FileReader(mixLock);
            bufferedReader = new BufferedReader(fileReader);
            String currLine;
            while ((currLine = bufferedReader.readLine()) != null) {
                if (currLine.startsWith(Constants.WHITESPACE)) {
                    logger.debug("parsing line {}", currLine);
                    try {
                        DependencyInfo dependencyInfo = null;
                        String name = null;
                        if (currLine.contains(GIT)) {
                            matcher = gitPattern.matcher(currLine);
                            if (matcher.find()) {
                                name = matcher.group(1);
                                String commitId = matcher.group(3);
                                dependencyInfo = new DependencyInfo();
                                dependencyInfo.setArtifactId(name);
                                dependencyInfo.setCommit(commitId);
                            }else {
                                logger.debug("Failed matching GIT pattern on this line");
                            }
                        } else {
                            matcher = hexPattern.matcher(currLine);
                            if (matcher.find()) {
                                name = matcher.group(1);
                                String version = matcher.group(2);
                                String sha1 = getSha1(name, version);
                                if (sha1 == null) {
                                    dependencyInfo = new DependencyInfo();
                                } else {
                                    dependencyInfo = new DependencyInfo(sha1);
                                }
                                dependencyInfo.setArtifactId(name);
                                dependencyInfo.setVersion(version);
                                dependencyInfo.setSystemPath(dotHexCachePath + fileSeparator + name + Constants.DASH + version + TAR_EXTENSION);
                                dependencyInfo.setFilename(name + Constants.DASH + version + TAR_EXTENSION);
                            } else {
                                logger.debug("Failed matching HEX pattern on this line");
                            }
                        }
                        if (dependencyInfo != null) {
                            dependencyInfo.setDependencyFile(mixLock.getParent() + fileSeparator + MIX_EXS);
                            dependencyInfo.setDependencyType(DependencyType.HEX);
                            dependencyInfoHashMap.put(name, dependencyInfo);
                        }
                    } catch (Exception e){
                        logger.warn("Failed parsing this line, error: {}", e.getMessage());
                        logger.debug("Exception: {}", e.getStackTrace());
                    }
                }
            }
        } catch (FileNotFoundException e) {
            logger.warn("Can't find {}, error: {}", mixLock.getPath(), e.getMessage());
            logger.debug("Error: {}", e.getStackTrace());
        } catch (IOException e) {
            logger.warn("Can't parse {}, error: {}", mixLock.getPath(), e.getMessage());
            logger.debug("Error: {}", e.getStackTrace());
        }
        return dependencyInfoHashMap;
    }

    private void getSha1AndVersion(DependencyInfo dependencyInfo, String version){
        String name = dependencyInfo.getArtifactId();
        if (dependencyInfo.getSha1() == null){
            String sha1 = null;
            if (version != null) {
                sha1 = getSha1(name, version);
            } else {
                File tarFile = getTarFile(name);
                if (tarFile != null){
                    try {
                        sha1 = ChecksumUtils.calculateSHA1(tarFile);
                        // extracting the version from the TAR file's name
                        Pattern versionPattern = Pattern.compile(VERSION_REGEX);
                        Matcher matcher = versionPattern.matcher(tarFile.getName());
                        if (matcher.find()) {
                            version = matcher.group(1);
                        }
                    } catch (IOException e){
                        logger.warn("Failed calculating SHA1 of {}", tarFile.getPath());
                        logger.debug("Error: {}", e.getStackTrace());
                    }
                }
            }
            if (sha1 != null){
                dependencyInfo.setSha1(sha1);
            }
        }
        if (version != null) {
            dependencyInfo.setSystemPath(dotHexCachePath + fileSeparator + name + Constants.DASH + version + TAR_EXTENSION);
            dependencyInfo.setFilename(name + Constants.DASH + version + TAR_EXTENSION);
            if (dependencyInfo.getVersion() == null) {
                dependencyInfo.setVersion(version);
            }
        }
    }

    // this method is used when there's a known version
    private String getSha1(String name, String version) {
        if (dotHexCachePath == null || name == null || version == null){
            logger.warn("Can't calculate SHA1, missing information: .hex-cache = {}, name = {}, version = {}", dotHexCachePath, name, version);
            return null;
        }
        File tarFile = new File(dotHexCachePath + fileSeparator + name + Constants.DASH + version + TAR_EXTENSION);
        try {
            return ChecksumUtils.calculateSHA1(tarFile);
        } catch (IOException e) {
            logger.warn("Failed calculating SHA1 of {}.  Make sure HEX is installed", tarFile.getPath());
            logger.debug("Error: {}", e.getStackTrace());
            return null;
        }
    }

    // this method is used when there's no known version. in such case finding in the cache all the tar files with the relevant name
    // and then finding the most recent one (according to the version)
    private File getTarFile(String name) {
        File hexCache = new File(dotHexCachePath);
        File[] files = hexCache.listFiles(new HexFileNameFilter(name));
        if (files != null && files.length > 0) {
            Arrays.sort(files, Collections.reverseOrder());
            return files[0];
        }
        logger.warn("Couldn't find tar file of {}", name);
        return null;
    }

    private void addTransitiveDependency(DependencyInfo parentDependency, DependencyInfo childDependency){
        // adding transitive dependency after making sure the child is not the parent, the parent dependecy doesn't contain
        // this child already, the child dependency doesn't contain the parent, and that the parent is not a descendant of the child
        if (parentDependency != childDependency && !parentDependency.getChildren().contains(childDependency) &&
                !childDependency.getChildren().contains(parentDependency) && !isDescendant(parentDependency, childDependency)) {
            parentDependency.getChildren().add(childDependency);
        }
    }

    // avoiding circular-dependencies
    private boolean isDescendant(DependencyInfo parentDependency, DependencyInfo childDependency){
        for (DependencyInfo dependencyInfo : childDependency.getChildren()){
            if (dependencyInfo.equals(parentDependency))
                return true;
            return isDescendant(dependencyInfo, childDependency);
        }
        return false;
    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        if(ignoreSourceFiles) {
            for (String hexExtension : HEX_SCRIPT_EXTENSION) {
                excludes.add(Constants.PATTERN + hexExtension);
            }
        }
        return excludes;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.HEX;
    }

    @Override
    protected String getDependencyTypeName() {
        return DependencyType.HEX.name();
    }

    @Override
    protected String[] getBomPattern() {
        return new String[]{Constants.PATTERN + MIX_EXS};
    }

    @Override
    public Collection<String> getManifestFiles(){
        return Arrays.asList(MIX_EXS, MIX_LOCK);
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return HEX_SCRIPT_EXTENSION;
    }
}

class HexFileNameFilter implements FilenameFilter {
    private String fileName;

    HexFileNameFilter(String name){
        fileName = name;
    }
    @Override
    public boolean accept(File dir, String name) {
        return name.toLowerCase().startsWith(fileName) && name.endsWith(HexDependencyResolver.TAR_EXTENSION);
    }
}