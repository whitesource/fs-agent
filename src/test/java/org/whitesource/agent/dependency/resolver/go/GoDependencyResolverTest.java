package org.whitesource.agent.dependency.resolver.go;

import org.apache.maven.model.Build;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.fs.FSAConfigProperties;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GoDependencyResolverTest {

    private final Logger logger = LoggerFactory.getLogger(GoDependencyResolverTest.class);

    //    @Ignore
    @Test
    public void goIgnoreSourceFilesTest() {
        String folderPath = Paths.get(".").toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\go\\");
        List<ResolutionResult> results = getResolutionResults(Arrays.asList(folderPath), "false");
        List<ResolutionResult> resultsISF = getResolutionResults(Arrays.asList(folderPath), "true");
        List<List> bothExcludes = TestHelper.getExcludesFromDependencyResult(results, resultsISF, DependencyType.GO);
        String[] includes = new String[]{"**/*.go"};
        Assert.assertFalse(TestHelper.checkResultOfScanFiles(folderPath, bothExcludes.get(0), bothExcludes.get(1), includes, DependencyType.GO));
    }

    private List<ResolutionResult> getResolutionResults(List<String> pathsToScan,String isIgnoreSourceFiles) {
        FSAConfigProperties props = new FSAConfigProperties();
        props.setProperty(ConfigPropertyKeys.GO_RESOLVE_DEPENDENCIES, "true");
        props.setProperty(ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES, "false");
        props.setProperty(ConfigPropertyKeys.GO_DEPENDENCY_MANAGER, "godep");
        props.setProperty(ConfigPropertyKeys.GO_IGNORE_SOURCE_FILES,isIgnoreSourceFiles );
        ResolverConfiguration resolverConfiguration = new FSAConfiguration(props).getResolver();
        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(resolverConfiguration);
        return dependencyResolutionService.resolveDependencies(pathsToScan, new String[0]);
    }

    @Test
    public void testGoGradle(){
       List<String> lines = new ArrayList<>();
       lines.add("> Configure project :");
       lines.add("BuildPlugin: systemProp.multiProject=false");
       lines.add("BuildPlugin: Current branch is 'whitesource'");
       lines.add("continueWhenFail is deprecated, please use continueOnFailure instead.");
       lines.add("continueWhenFail is deprecated, please use continueOnFailure instead.");
       lines.add("Found go 1.9.4 in /opt/go1.9.4/bin/go, use it.");
       lines.add(" - Finished reading 7 lines");
       lines.add("         > Task :goPrepare");
       lines.add("Found global GOPATH: /opt/kaloom-go.");
       lines.add("- Finished reading 10 lines");
       lines.add("> Task :processGogradleDeps UP-TO-DATE");
       lines.add("- Finished reading 12 lines");
       lines.add("> Task :resolveBuildDependencies");
       lines.add("Resolving flowfabric/fps: commit='LATEST_COMMIT', urls=[git@gitlab.infra.kaloom.io:flowfabric/fps.git]");
       lines.add("Resolving flowfabric/msgbus: commit='LATEST_COMMIT', urls=[git@gitlab.infra.kaloom.io:flowfabric/msgbus.git]");
       lines.add("Resolving github.com/stretchr/testify: commit='be8372ae8ec5c6daaed3cc28ebf73c54b737c240', urls=[https://gitlab.kaloom.io/github.com/stretchr/testify.git]");
       lines.add("Resolving cached github.com/droundy/goopt: commit='0b8effe182da161d81b011aba271507324ecb7ab', urls=[https://gitlab.kaloom.io/github.com/droundy/goopt.git]");
       lines.add("Resolving cached github.com/go-mangos/mangos: commit='41a23037e0d3df33c94236ef5841bf8ea6061267', urls=[https://gitlab.kaloom.io/github.com/nanomsg/mangos.git]");
       lines.add("Resolving cached github.com/google/uuid: commit='064e2069ce9c359c118179501254f67d7d37ba24', urls=[https://gitlab.kaloom.io/github.com/google/uuid.git]");
       lines.add("Resolving cached github.com/gorilla/websocket: commit='66b9c49e59c6c48f0ffce28c2d8b8a5678502c6d', urls=[https://gitlab.kaloom.io/github.com/gorilla/websocket.git]");
       lines.add("Resolving github.com/stretchr/testify: commit='f35b8ab0b5a2cef36673838d662e249dd9c94686', urls=[https://github.com/stretchr/testify.git, git@github.com:stretchr/testify.git]");
       lines.add("Resolving cached github.com/stretchr/objx: commit='477a77ecc69700c7cdeb1fa9e129548e1c1c393c', urls=[https://gitlab.kaloom.io/github.com/stretchr/objx.git]");
       lines.add("Resolving cached github.com/stretchr/testify: commit='f35b8ab0b5a2cef36673838d662e249dd9c94686', urls=[https://gitlab.kaloom.io/github.com/stretchr/testify.git]");
       lines.add("Resolving github.com/ugorji/go: commit='b4c50a2b199d93b13dc15e78929cfb23bfdf21ab', urls=[https://gitlab.kaloom.io/github.com/ugorji/go.git]");
       lines.add("Resolving cached gopkg.in/yaml.v2: commit='5420a8b6744d3b0345ab293f6fcba19c978f1183', urls=[https://gitlab.kaloom.io/gopkg.in/yaml.v2.git]");
       lines.add("- Finished reading 26 lines");
       lines.add("> Task :resolveTestDependencies");
       lines.add("         - Finished reading 28 lines");
       lines.add("> Task :printGogradleCachedDependencies");
       lines.add("Build dependencies found in gogradle cache:");
       lines.add("flowfabric/fps: 9f7d1b02fd0066ba9889955e53d813498db39e32");
       lines.add("flowfabric/msgbus: 033a106a7d633927e59ba85308f74e262c7e11cc");
       lines.add("github.com/davecgh/go-spew: github.com/stretchr/testify#be8372ae8ec5c6daaed3cc28ebf73c54b737c240/vendor/github.com/davecgh/go-spew");
       lines.add("github.com/droundy/goopt: 0b8effe182da161d81b011aba271507324ecb7ab");
       lines.add("github.com/go-mangos/mangos: 41a23037e0d3df33c94236ef5841bf8ea6061267");
       lines.add("github.com/google/uuid: 064e2069ce9c359c118179501254f67d7d37ba24");
       lines.add("github.com/gorilla/websocket: 66b9c49e59c6c48f0ffce28c2d8b8a5678502c6d");
       lines.add("github.com/pmezard/go-difflib: github.com/stretchr/testify#f35b8ab0b5a2cef36673838d662e249dd9c94686/vendor/github.com/pmezard/go-difflib");
       lines.add("github.com/stretchr/objx: 477a77ecc69700c7cdeb1fa9e129548e1c1c393c");
       lines.add("github.com/stretchr/testify: f35b8ab0b5a2cef36673838d662e249dd9c94686");
       lines.add("github.com/ugorji/go: b4c50a2b199d93b13dc15e78929cfb23bfdf21ab");
       lines.add("gopkg.in/yaml.v2: 5420a8b6744d3b0345ab293f6fcba19c978f1183");
       lines.add("Test dependencies found in gogradle cache:");
       lines.add("- Finished reading 44 lines");
       lines.add("         > Task :resolveGogradleDependencies");
       lines.add("- Finished reading 46 lines");
       lines.add("> Task :goDependencies");
       lines.add("build:");
       lines.add("flowfabric/basics");
       lines.add("|-- flowfabric/fps:9f7d1b0");
       lines.add("|-- flowfabric/msgbus:033a106");
       lines.add("|-- github.com/davecgh/go-spew:github.com/stretchr/testify#be8372ae8ec5c6daaed3cc28ebf73c54b737c240/vendor/github.com/davecgh/go-spew");
       lines.add("|-- github.com/droundy/goopt:0b8effe");
       lines.add("|-- github.com/go-mangos/mangos:41a2303");
       lines.add("|-- github.com/google/uuid:064e206");
       lines.add("|-- github.com/gorilla/websocket:66b9c49");
       lines.add("|-- github.com/pmezard/go-difflib:github.com/stretchr/testify#f35b8ab0b5a2cef36673838d662e249dd9c94686/vendor/github.com/pmezard/go-difflib");
       lines.add("|-- github.com/stretchr/objx:477a77e");
       lines.add("|-- github.com/stretchr/testify:f35b8ab");
       lines.add("|-- github.com/ugorji/go:b4c50a2");
       lines.add("\\-- gopkg.in/yaml.v2:5420a8b");
       lines.add("- Finished reading 62 lines");
       lines.add("test:");
       lines.add("flowfabric/basics\\");
       lines.add("Deprecated Gradle features were used in this build, making it incompatible with Gradle 5.0.");
       lines.add("See https://docs.gradle.org/4.8/userguide/command_line_interface.html#sec:command_line_warnings");
       lines.add("- Finished reading 69 lines");
       lines.add("BUILD SUCCESSFUL in 3s");
       lines.add("6 actionable tasks: 6 executed");

       ArrayList<DependencyInfo> dependencyInfos = new ArrayList<>();
       parseGoGradleDependencies(lines, dependencyInfos);

        String filePath = Paths.get(".").toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\go\\gogradle.lock");
        File goLockFile = new File(filePath);
        if (goLockFile.isFile()) {
            HashMap<String, String> gradleLockFile = parseGoGradleLockFile(goLockFile);
            // for each dependency - matching its full commit id
            dependencyInfos.stream().forEach(dependencyInfo -> dependencyInfo.setCommit(gradleLockFile.get(dependencyInfo.getArtifactId())));
            // removing dependencies without commit-id and version
            dependencyInfos.removeIf(dependencyInfo -> dependencyInfo.getCommit() == null && dependencyInfo.getVersion() == null);
        }
    }

    private void parseGoGradleDependencies(List<String> lines, List<DependencyInfo> dependencyInfos){
        List<String> filteredLines = lines.stream()
                .filter(line->(line.contains("\\--") || line.contains(Constants.PIPE)) && !line.contains("(*)"))
                .collect(Collectors.toList());
        DependencyInfo dependencyInfo;
        Pattern shortIdInBracketsPattern = Pattern.compile("\\([a-z0-9]{7}\\)");
        Pattern shortIdPattern = Pattern.compile("[a-z0-9]{7}");
        for (String currentLine : filteredLines){
            /* possible lines:
                |-- github.com/astaxie/beego:053a075
                |-- golang.org/x/crypto:github.com/astaxie/beego#053a075344c118a5cc41981b29ef612bb53d20ca/vendor/golang.org/x/crypto
                |   \-- gopkg.in/yaml.v2:github.com/astaxie/beego#053a075344c118a5cc41981b29ef612bb53d20ca/vendor/gopkg.in/yaml.v2 -> v2.2.1(5420a8b)
                |-- github.com/eRez-ws/go-stringUtil:v1.0.4(99cfd8b)

               splitting them using : - the first part contains the name, the second part might contain the version and short-commit-id
            */
            try {
                dependencyInfo = new DependencyInfo();
                String[] dependencyLineSplit = currentLine.split(Constants.COLON);
                // extracting the group and artifact id from the first part of the line
                String name = dependencyLineSplit[0];
                int lastSpace = name.lastIndexOf(Constants.WHITESPACE);
                name = name.substring(lastSpace + 1);
                dependencyInfo.setGroupId(getGroupId(name));
                if (dependencyLineSplit.length > 1) { // extracting the version from the second part
                    String versionPart = dependencyLineSplit[1];
                    Matcher matcher = shortIdInBracketsPattern.matcher(versionPart);
                    if (matcher.find()) { // extracting version (if found)
                        int index = matcher.start();
                        String version;
                        if (versionPart.contains(Constants.WHITESPACE) && versionPart.lastIndexOf(Constants.WHITESPACE) < index) {
                            version = versionPart.substring(versionPart.lastIndexOf(Constants.WHITESPACE), index);
                        } else {
                            version = versionPart.substring(0, index);
                        }
                        dependencyInfo.setVersion(version);
                    } else {
                        matcher = shortIdPattern.matcher(versionPart);
                        if (matcher.find()){ // extracting short commit id (if found)
                            int index = matcher.start();
                            if (index == 0) {
                                String shortCommit = versionPart.substring(0,7);
                                dependencyInfo.setCommit(shortCommit);
                            }
                        }
                    }
                }
                dependencyInfo.setArtifactId(name);
                dependencyInfos.add(dependencyInfo);
            } catch (Exception e){
                logger.warn("Error parsing line {}, exception: {}", currentLine, e.getMessage());
                logger.debug("Exception: {}", e.getStackTrace());
            }
        }
    }

    private String getGroupId(String name){
        String groupId =  Constants.EMPTY_STRING;
        if (name.contains(Constants.FORWARD_SLASH)) {
            String[] split = name.split( Constants.FORWARD_SLASH);
            groupId = split[1];
        }
        return groupId;
    }

    private HashMap<String, String> parseGoGradleLockFile(File file){
        HashMap<String, String> dependenciesCommits = new HashMap<>();
        if (file.isFile()){
            FileReader fileReader = null;
            try {
                fileReader = new FileReader(file);
                BufferedReader bufferedReader = new BufferedReader(fileReader);
                String currLine;
                String name = null;
                String commit = null;
                while ((currLine = bufferedReader.readLine()) != null) {
                    if (currLine.startsWith(Constants.WHITESPACE + Constants.WHITESPACE) == false)
                        continue;
                    if (currLine.startsWith(Constants.WHITESPACE + Constants.WHITESPACE + Constants.DASH + Constants.WHITESPACE)) {
                        if (name != null && commit != null){
                            dependenciesCommits.put(name, commit);
                        }
                        name = null;
                        commit = null;
                    }
                    if (currLine.contains("name" + Constants.COLON + Constants.WHITESPACE)) {
                        name = currLine.substring(currLine.indexOf(Constants.COLON) + 1).trim();
                        name = name.replace(Constants.QUOTATION_MARK, Constants.EMPTY_STRING);
                        name = name.replace(Constants.APOSTROPHE, Constants.EMPTY_STRING);
                    } else if (currLine.contains("commit: ")) {
                        commit = currLine.substring(currLine.indexOf(Constants.COLON) + 1).trim();
                        commit = commit.replace(Constants.QUOTATION_MARK, Constants.EMPTY_STRING);
                        commit = commit.replace(Constants.APOSTROPHE, Constants.EMPTY_STRING);
                    }
                }
                if (name != null && commit != null){
                    dependenciesCommits.put(name, commit);
                }
            } catch (FileNotFoundException e) {
                logger.warn("Error finding {}, exception: {}", file.getPath(), e.getMessage());
                logger.debug("Error: {}", e.getStackTrace());
            } catch (IOException e) {
                logger.warn("Error parsing {}, exception: {}", file.getName(), e.getMessage());
                logger.debug("Exception: {}", e.getStackTrace());
            } finally {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    logger.warn("Can't close {}, exception: {}", file.getName(), e.getMessage());
                    logger.debug("Exception: {}", e.getStackTrace());
                }
            }
        }
        return dependenciesCommits;
    }
}