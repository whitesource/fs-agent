package org.whitesource.agent.dependency.resolver.hex;

import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.utils.Cli;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HexDependencyResolver extends AbstractDependencyResolver {

    private static final List<String> HEX_SCRIPT_EXTENSION = Arrays.asList(".ex", ".exs");
    private static final String MIX_LOCK_FILE = "mix.lock";
    private static final String MIX = "mix";
    private static final String DEPS_GET = "deps.get";
    private static final String DEPS_TREE = "deps.tree";
    private static final String ACCENT = "`";
    private static final String HEX_REGEX = "\"(\\w+)\": \\{:hex, :\\w+, \"(\\d+\\.\\d+\\.\\d+(?:-\\w+(?:\\.\\w+)*)?(?:\\+\\w+)?)\", \"(\\w+)\"";
    private static final String GIT_REGEX = "\"(\\w+)\": \\{:git, \"(https|http|):/\\/github.com\\/\\w+\\/\\w+.git\", \"(\\w+)\"";
    private static final String TREE_REGEX = "--\\s(\\w+)\\s(~>\\s(\\d+\\.\\d+(\\.\\d+)?(?:-\\w+(?:\\.\\w+)*)?(?:\\+\\w+)?))?";
    public static final String TAR_EXTENSION = ".tar";

    private final Logger logger = LoggerFactory.getLogger(HexDependencyResolver.class);
    private boolean ignoreSourceFiles;
    private boolean runPreStep;
    private Cli cli;
    private String dotHexCachePath;

    public HexDependencyResolver(boolean ignoreSourceFiles, boolean runPreStep){
        this.ignoreSourceFiles = ignoreSourceFiles;
        this.runPreStep = runPreStep;
        cli = new Cli();
        String currentUsersHomeDir = System.getProperty(Constants.USER_HOME);
        File dotHexCache = Paths.get(currentUsersHomeDir, ".hex", "packages","hexpm").toFile();
        if (dotHexCache.exists()){
            dotHexCachePath = dotHexCache.getAbsolutePath();
        }
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) throws FileNotFoundException {
        if (this.runPreStep){
            runPreStep(topLevelFolder);
        }
        List<DependencyInfo> dependencies = new ArrayList<>();
        File mixLock = new File (topLevelFolder + fileSeparator + MIX_LOCK_FILE);
        if (mixLock.exists()){
            HashMap<String, DependencyInfo> dependencyInfoMap = parseMixLoc(mixLock);
            dependencies = parseMixTree(topLevelFolder, dependencyInfoMap);
        } else {
            logger.warn("Can't find {}", mixLock.getPath());
        }
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    private void runPreStep(String folderPath){
        List<String> compileOutput = cli.runCmd(folderPath, cli.getCommandParams(MIX, DEPS_GET));
        if (compileOutput.isEmpty()) {
            logger.warn("Can't run '{} {}'", MIX, DEPS_GET);
        }
    }

    public List<DependencyInfo> parseMixTree(String folderPath, HashMap<String, DependencyInfo> dependencyInfoMap){
        List<String> lines = cli.runCmd(folderPath, cli.getCommandParams(MIX, DEPS_TREE));
        int currentLevel;
        int prevLevel = 0;
        List<DependencyInfo> dependenciesList = new ArrayList<>();
        Stack<DependencyInfo> parentDependencies = new Stack<>();
        Pattern treePattern = Pattern.compile(TREE_REGEX);
        Matcher matcher;
        if (lines != null){
            for (String line : lines){
                if (line.startsWith(Constants.PIPE) || line.startsWith(ACCENT) || line.startsWith(Constants.WHITESPACE)){
                    currentLevel = (line.indexOf(Constants.DASH) - 1)/4;
                    matcher = treePattern.matcher(line);
                    if (matcher.find()) {
                        String name = matcher.group(1);
                        String version = matcher.group(3);
                        DependencyInfo dependencyInfo = dependencyInfoMap.get(name);
                        if (dependencyInfo != null){
                            if (dependencyInfo.getSha1() == null){
                                String sha1;
                                if (version != null) {
                                    sha1 = getSha1(name, version);
                                } else {
                                    sha1 = getSha1(name);
                                }
                                if (sha1 != null){
                                    dependencyInfo.setSha1(sha1);
                                }
                            }
                            if (version != null) {
                                dependencyInfo.setFilename(dotHexCachePath + fileSeparator + name + Constants.DASH + version + TAR_EXTENSION);
                                if (dependencyInfo.getVersion() == null) {
                                    dependencyInfo.setVersion(version);
                                }
                            }
                            if (currentLevel == prevLevel){
                                if (!parentDependencies.isEmpty()) {
                                    parentDependencies.pop();
                                    if (!parentDependencies.isEmpty()) {
                                        parentDependencies.peek().getChildren().add(dependencyInfo);
                                    }
                                }
                                if (parentDependencies.isEmpty()) {
                                    dependenciesList.add(dependencyInfo);
                                }
                                parentDependencies.push(dependencyInfo);
                            } else if (currentLevel > prevLevel){ // transitive dependency
                                if (!parentDependencies.isEmpty()){
                                    parentDependencies.peek().getChildren().add(dependencyInfo);
                                }
                                parentDependencies.push(dependencyInfo);
                            } else { // dependency with higher hierarchy level than previous one
                                while (prevLevel > currentLevel - 1 && !parentDependencies.isEmpty()){
                                    parentDependencies.pop();
                                    prevLevel--;
                                }
                                if (!parentDependencies.isEmpty()){
                                    parentDependencies.peek().getChildren().add(dependencyInfo); // transitive dependency - adding to its parent
                                } else {
                                    dependenciesList.add(dependencyInfo); // root dependency
                                }
                                parentDependencies.push(dependencyInfo);
                            }
                        }
                        prevLevel = currentLevel;
                    }
                }
            }
        }
        return dependenciesList;
    }

    public HashMap<String, DependencyInfo> parseMixLoc(File mixLock){
        HashMap<String, DependencyInfo> dependencyInfoHashMap = new HashMap<>();
        FileReader fileReader;
        BufferedReader bufferedReader;
        try {
            Pattern hexPattern = Pattern.compile(HEX_REGEX);
            Pattern gitPattern = Pattern.compile(GIT_REGEX);
            Matcher matcher;
            fileReader = new FileReader(mixLock);
            bufferedReader = new BufferedReader(fileReader);
            String currLine;
            while ((currLine = bufferedReader.readLine()) != null) {
                if (currLine.startsWith(Constants.WHITESPACE)) {
                    DependencyInfo dependencyInfo = null;
                    String name = null;
                    if (currLine.contains("git")) {
                        matcher = gitPattern.matcher(currLine);
                        if (matcher.find()){
                            name = matcher.group(1);
                            String commitId = matcher.group(3);
                            dependencyInfo = new DependencyInfo();
                            dependencyInfo.setArtifactId(name);
                            dependencyInfo.setCommit(commitId);
                        }
                    } else {
                        matcher = hexPattern.matcher(currLine);
                        if (matcher.find()) {
                            name = matcher.group(1);
                            String version = matcher.group(2);
                            String sha1 = getSha1(name, version);
                            if (sha1 == null){
                                dependencyInfo = new DependencyInfo();
                            } else {
                                dependencyInfo = new DependencyInfo(sha1);
                            }
                            dependencyInfo.setArtifactId(name);
                            dependencyInfo.setVersion(version);
                            dependencyInfo.setFilename(dotHexCachePath + fileSeparator + name + Constants.DASH + version + TAR_EXTENSION);
                        }
                    }
                    if (dependencyInfo != null) {
                        dependencyInfo.setSystemPath(mixLock.getPath());
                        dependencyInfo.setDependencyType(DependencyType.HEX);
                        dependencyInfoHashMap.put(name, dependencyInfo);
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
    private String getSha1(String name) {
        try {
            File hexCache = new File(dotHexCachePath);
            File[] files = hexCache.listFiles(new HexFileNameFilter(name));
            if (files != null && files.length > 0) {
                Arrays.sort(files, Collections.reverseOrder());
                return ChecksumUtils.calculateSHA1(files[0]);
            }
        } catch (IOException e) {
            logger.warn("Error calculating sha1 {}, error:  {}", name, e.getMessage());
            logger.debug("Error: {}", e.getStackTrace());
        }
        logger.warn("Couldn't find tar file of {}", name);
        return null;
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
        return new String[]{Constants.PATTERN + MIX_LOCK_FILE};
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