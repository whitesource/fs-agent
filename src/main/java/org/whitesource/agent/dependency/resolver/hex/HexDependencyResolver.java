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
    private static final String GIT_REGEX = "\"(\\w+)\": \\{:git, \"(https|http|):\\/\\/github.com\\/\\w+\\/\\w+.git\", \"(\\w+)\"";

    private final Logger logger = LoggerFactory.getLogger(HexDependencyResolver.class);
    private boolean ignoreSourceFiles;
    private boolean runPreStep;
    private Cli cli;

    public HexDependencyResolver(boolean ignoreSourceFiles, boolean runPreStep){
        this.ignoreSourceFiles = ignoreSourceFiles;
        this.runPreStep = runPreStep;
        cli = new Cli();
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) throws FileNotFoundException {
        if (this.runPreStep){
            runPreStep(topLevelFolder);
        }
        File mixLock = new File (topLevelFolder + fileSeparator + MIX_LOCK_FILE);
        if (mixLock.exists()){
            HashMap<String, DependencyInfo> dependencyInfoMap = parseMixLoc(mixLock);
            parseTree(topLevelFolder, dependencyInfoMap);
        }
        return null;
    }

    private void runPreStep(String folderPath){
        List<String> compileOutput = cli.runCmd(folderPath, cli.getCommandParams(MIX, DEPS_GET));
        if (compileOutput.isEmpty()) {
            logger.warn("Can't run '{} {}'", MIX, DEPS_GET);
        }
    }

    private void parseTree(String folderPath, HashMap<String, DependencyInfo> dependencyInfoMap){
        List<String> lines = cli.runCmd(folderPath, cli.getCommandParams(MIX, DEPS_TREE));
        int currentLevel = 0;
        int prevLevel = 0;
        List<DependencyInfo> dependenciesList = new ArrayList<>();
        Stack<DependencyInfo> parentDependencies = new Stack<>();
        if (lines != null){
            for (String line : lines){
                if (line.startsWith(Constants.PIPE) || line.startsWith(ACCENT) || line.startsWith(Constants.WHITESPACE)){
                    currentLevel = (line.indexOf(Constants.DASH) - 1)/4;
                    int nameStart = line.indexOf(Constants.WHITESPACE, line.indexOf(Constants.DASH));
                    int nameEnd = line.indexOf(Constants.WHITESPACE, nameStart + 1);
                    String name = line.substring(nameStart, nameEnd);
                }
            }
        }
    }

    public HashMap<String, DependencyInfo> parseMixLoc(File mixLock){
        HashMap<String, DependencyInfo> dependencyInfoHashMap = new HashMap<>();
        FileReader fileReader = null;
        BufferedReader bufferedReader = null;
        try {
            Pattern hexPattern = Pattern.compile(HEX_REGEX);
            Pattern gitPattern = Pattern.compile(GIT_REGEX);
            Matcher matcher;
            fileReader = new FileReader(mixLock);
            bufferedReader = new BufferedReader(fileReader);
            String currLine;
            String currentUsersHomeDir = System.getProperty(Constants.USER_HOME);
            File dotHexCache = Paths.get(currentUsersHomeDir, ".hex", "packages","hexpm").toFile();
            while ((currLine = bufferedReader.readLine()) != null) {
                if (currLine.startsWith(Constants.WHITESPACE)) {
                    if (currLine.contains("git")) {
                        matcher = gitPattern.matcher(currLine);
                        if (matcher.find()){
                            String name = matcher.group(1);
                            String commitId = matcher.group(3);
                            DependencyInfo dependencyInfo = new DependencyInfo();
                            dependencyInfo.setArtifactId(name);
                            dependencyInfo.setCommit(commitId);
                            dependencyInfoHashMap.put(name,dependencyInfo);
                        }
                    } else {
                        matcher = hexPattern.matcher(currLine);
                        if (matcher.find()) {
                            String name = matcher.group(1);
                            String version = matcher.group(2);
                            String sha1 = null;
                            if (dotHexCache.exists() && name != null && version != null) {
                                File tarFile = new File(dotHexCache.getAbsolutePath() + fileSeparator + name + Constants.DASH + version + ".tar");
                                sha1 = getSha1(tarFile.getAbsolutePath());
                            }
                            DependencyInfo dependencyInfo;
                            if (sha1 == null){
                                dependencyInfo = new DependencyInfo();
                            } else {
                                dependencyInfo = new DependencyInfo(sha1);
                            }
                            dependencyInfo.setArtifactId(name);
                            dependencyInfo.setVersion(version);
                            dependencyInfoHashMap.put(name,dependencyInfo);
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dependencyInfoHashMap;
    }

    protected String getSha1(String filePath) {
        try {
            return ChecksumUtils.calculateSHA1(new File(filePath));
        } catch (IOException e) {
            logger.warn("Failed calculating SHA1 of {}.  Make sure HEX is installed", filePath);
            return Constants.EMPTY_STRING;
        }
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
