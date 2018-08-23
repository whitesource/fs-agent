package org.whitesource.agent.dependency.resolver.go;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.gradle.GradleCli;
import org.whitesource.agent.dependency.resolver.gradle.GradleMvnCommand;
import org.whitesource.agent.utils.Cli;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GoDependencyResolver extends AbstractDependencyResolver {

    private final Logger logger = LoggerFactory.getLogger(GoDependencyResolver.class);

    private static final String PROJECTS        = "[[projects]]";
    private static final String DEPS            = "Deps";
    private static final String REV             = "Rev";
    private static final String COMMENT         = "Comment";
    private static final String IMPORT_PATH     = "ImportPath";
    private static final String NAME            = "name";
    private static final String COMMIT          = "commit: ";
    private static final String VERSION         = "version = ";
    private static final String REVISION        = "revision = ";
    private static final String PACKAGES        = "packages = ";
    private static final String BRACKET         = "]";
    private static final String DOT             = ".";
    private static final String ASTERIX         = "(*)";
    private static final String SLASH           = "\\--";
    private static final String GOPKG_LOCK      = "Gopkg.lock";
    private static final String GODEPS_JSON     = "Godeps.json";
    private static final String VNDR_CONF       = "vendor.conf";
    private static final String GOGRADLE_LOCK   = "gogradle.lock";
    private static final String GLIDE_LOCK      = "glide.lock";
    private static final String GO_EXTENSION    = ".go";
    private static final String GO_ENSURE       = "ensure";
    private static final String GO_INIT         = "init";
    private static final String GO_SAVE         = "save";
    private static final List<String> GO_SCRIPT_EXTENSION = Arrays.asList(".lock", ".json", GO_EXTENSION);
    private static final String IMPORTS = "imports";
    private static final String NAME_GLIDE = "- name: ";
    private static final String VERSION_GLIDE = "  version: ";
    private static final String SUBPACKAGES_GLIDE = "  subpackages";
    private static final String PREFIX_SUBPACKAGES_SECTION = "  - ";
    private static final String TEST_IMPORTS = "testImports";
    private static final String GLIDE_YAML = "glide.yaml";
    private static final String GO_UPDATE = "update";

    private Cli cli;
    private GoDependencyManager goDependencyManager;
    private boolean collectDependenciesAtRuntime;
    private boolean isDependenciesOnly;
    private boolean ignoreTestPackages;

    public GoDependencyResolver(GoDependencyManager goDependencyManager, boolean collectDependenciesAtRuntime, boolean isDependenciesOnly, boolean ignoreTestPackages){
        super();
        this.cli = new Cli();
        this.goDependencyManager = goDependencyManager;
        this.collectDependenciesAtRuntime = collectDependenciesAtRuntime;
        this.isDependenciesOnly = isDependenciesOnly;
        this.ignoreTestPackages = ignoreTestPackages;
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        List<DependencyInfo> dependencies = collectDependencies(topLevelFolder);
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        if (!collectDependenciesAtRuntime && goDependencyManager != null && isDependenciesOnly){
            excludes.add(Constants.PATTERN + GO_EXTENSION);
        }
        return excludes;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return GO_SCRIPT_EXTENSION;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.GO;
    }

    @Override
    protected String getDependencyTypeName() {
        return DependencyType.GO.name();
    }

    @Override
    protected String[] getBomPattern() {
        if (collectDependenciesAtRuntime || goDependencyManager == null) {
            return new String[]{Constants.PATTERN + GO_EXTENSION};
        }
        if (goDependencyManager != null) {
            switch (goDependencyManager) {
                case DEP:
                    return new String[]{Constants.PATTERN + GOPKG_LOCK};
                case GO_DEP:
                    return new String[]{Constants.PATTERN + GODEPS_JSON};
                case VNDR:
                    return new String[]{Constants.PATTERN + VNDR_CONF};
                case GO_GRADLE:
                    return new String[]{Constants.BUILD_GRADLE};
                case GLIDE:
                    return new String[]{Constants.PATTERN + GLIDE_LOCK, Constants.PATTERN + GLIDE_YAML};
            }
        }
        return new String[]{Constants.EMPTY_STRING};
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    private List<DependencyInfo> collectDependencies(String rootDirectory) {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        String error = null;
        long creationTime = new Date().getTime(); // will be used later for removing temp files/folders
        if (goDependencyManager != null) {
            try {
                switch (goDependencyManager) {
                    case DEP:
                        collectDepDependencies(rootDirectory, dependencyInfos);
                        break;
                    case GO_DEP:
                        collectGoDepDependencies(rootDirectory, dependencyInfos);
                        break;
                    case VNDR:
                        collectVndrDependencies(rootDirectory, dependencyInfos);
                        break;
                    case GO_GRADLE:
                        collectGoGradleDependencies(rootDirectory, dependencyInfos);
                        break;
                    case GLIDE:
                        collectGlideDependencies(rootDirectory, dependencyInfos);
                        break;
                    default:
                        error = "The selected dependency manager - " + goDependencyManager.getType() + " - is not supported.";
                }
            } catch (Exception e) {
                error = e.getMessage();
            }
        } else {
           error = collectDependenciesWithoutDefinedManager(rootDirectory, dependencyInfos);
        }
        if (error != null){
            logger.error(error);
        }

        if (collectDependenciesAtRuntime)
            removeTempFiles(rootDirectory, creationTime);
        return dependencyInfos;
    }

    // when no dependency manager is defined - trying to run one manager after the other, till one succeeds.  if not - returning an error
    private String collectDependenciesWithoutDefinedManager(String rootDirectory, List<DependencyInfo> dependencyInfos){
        String error = null;
        try {
            collectDepDependencies(rootDirectory, dependencyInfos);
        } catch (Exception e){
            try {
                collectGoDepDependencies(rootDirectory, dependencyInfos);
            } catch (Exception e1){
                try {
                    collectVndrDependencies(rootDirectory, dependencyInfos);
                } catch (Exception e2){
                    try {
                        collectGlideDependencies(rootDirectory, dependencyInfos);
                    } catch (Exception e3) {
                        error = "Couldn't collect dependencies - no dependency manager is installed";
                    }
                }
            }
        }
        return error;
    }

    private void collectDepDependencies(String rootDirectory, List<DependencyInfo> dependencyInfos) throws Exception {
        logger.debug("collecting dependencies using 'dep'");
        File goPkgLock = new File(rootDirectory + fileSeparator + GOPKG_LOCK);
        String error = Constants.EMPTY_STRING;
        if (goPkgLock.isFile()){
            if (runCmd(rootDirectory, cli.getCommandParams(GoDependencyManager.DEP.getType(), GO_ENSURE)) == false) {
                logger.warn("Can't run 'dep ensure' command, output might be outdated.  Run the 'dep ensure' command manually.");
            }
            dependencyInfos.addAll(parseGopckLock(goPkgLock));
        } else if (collectDependenciesAtRuntime) {
            if (runCmd(rootDirectory, cli.getCommandParams(GoDependencyManager.DEP.getType(), GO_INIT))) {
                dependencyInfos.addAll(parseGopckLock(goPkgLock));
            } else {
                error = "Can't run 'dep init' command.  Make sure dep is installed and run the 'dep init' command manually.";
            }
        } else {
            error = "Can't find " + GOPKG_LOCK + " file.  Run the 'dep init' command.";
        }
        if (!error.isEmpty()) {
            throw new Exception(error);
        }
    }

    private List<DependencyInfo> parseGopckLock(File goPckLock){
        logger.debug("parsing {}", goPckLock.getPath());
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(goPckLock);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String currLine;
            boolean insideProject = false;
            boolean resolveRepositoryPackages = false;
            boolean useParent = false;
            DependencyInfo dependencyInfo = null;
            ArrayList<String> repositoryPackages = null;
            while ((currLine = bufferedReader.readLine()) != null){
                if (insideProject) {
                    if (currLine.isEmpty()){
                        insideProject = false;
                        if (dependencyInfo != null) {
                            if (repositoryPackages == null || useParent)
                                dependencyInfos.add(dependencyInfo);
                            if (repositoryPackages != null){
                                for (String name : repositoryPackages){
                                    DependencyInfo packageDependencyInfo = new DependencyInfo(dependencyInfo.getGroupId(),
                                            dependencyInfo.getArtifactId() + Constants.FORWARD_SLASH + name,
                                            dependencyInfo.getVersion());
                                    packageDependencyInfo.setCommit(dependencyInfo.getCommit());
                                    dependencyInfos.add(packageDependencyInfo);
                                }
                                repositoryPackages = null;
                            }
                        }
                    } else {
                        if (resolveRepositoryPackages){
                            if (currLine.contains(BRACKET)){
                                resolveRepositoryPackages = false;
                            } else {
                                String name  = getValue(currLine);
                                if (name.equals(DOT)){
                                    useParent = true;
                                } else {
                                    repositoryPackages.add(getValue(currLine));
                                }
                            }
                        } else if (currLine.contains(NAME + Constants.WHITESPACE + Constants.EQUALS_CHAR + Constants.WHITESPACE)){
                            String name = getValue(currLine);
                            dependencyInfo.setGroupId(getGroupId(name));
                            dependencyInfo.setArtifactId(name);
                        } else if (currLine.contains(VERSION)){
                            dependencyInfo.setVersion(getValue(currLine));
                        } else if (currLine.contains(REVISION)){
                            dependencyInfo.setCommit(getValue(currLine));
                        } else if (currLine.contains(PACKAGES) && !currLine.contains(BRACKET)){
                            resolveRepositoryPackages = true;
                            repositoryPackages = new ArrayList<>();
                        } else if (currLine.contains(PACKAGES) && !currLine.contains(DOT)){
                            // taking care of lines like 'packages = ["spew"]' (and ignoring lines like 'packages = ["."]')
                            String name = getValue(currLine);
                            repositoryPackages = new ArrayList<>(Arrays.asList(name));
                        }
                    }
                } else if (currLine.equals(PROJECTS)){
                    dependencyInfo = new DependencyInfo();
                    insideProject = true;
                    useParent = false;
                }
            }
        } catch (FileNotFoundException e) {
            logger.error("Can't find " + goPckLock.getPath());
        } catch (IOException e) {
            logger.error("Can't read " + goPckLock.getPath());
        } finally {
            if (fileReader != null){
                try {
                    fileReader.close();
                } catch (IOException e) {
                    logger.error("can't close {}: {}", goPckLock.getPath(), e.getMessage());
                }
            }
        }
        dependencyInfos.stream().forEach(dependencyInfo -> {dependencyInfo.setSystemPath(goPckLock.getPath()); dependencyInfo.setDependencyType(DependencyType.GO);});
        return dependencyInfos;
    }

    private String getValue(String line){
        int firstIndex = line.indexOf(Constants.QUOTATION_MARK);
        int lastIndex = line.lastIndexOf(Constants.QUOTATION_MARK);
        String value = line.substring(firstIndex + 1, lastIndex);
        return value;
    }

    private void collectGoDepDependencies(String rootDirectory, List<DependencyInfo> dependencyInfos) throws Exception {
        logger.debug("collecting dependencies using 'godep'");
        File goDepJson = new File(rootDirectory + fileSeparator + "Godeps" + fileSeparator +  GODEPS_JSON);
        if (goDepJson.isFile() || (collectDependenciesAtRuntime && runCmd(rootDirectory, cli.getCommandParams(GoDependencyManager.GO_DEP.getType(), GO_SAVE)))){
            dependencyInfos.addAll(parseGoDeps(goDepJson));
        } else {
            throw new Exception("Can't find " + GODEPS_JSON + " file.  Please make sure 'godep' is installed and run 'godep save' command");
        }
    }

    private List<DependencyInfo> parseGoDeps(File goDeps) throws IOException {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        JsonParser parser = new JsonParser();
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(goDeps.getPath());
            JsonElement element = parser.parse(fileReader);
            if (element.isJsonObject()){
                JsonArray deps = element.getAsJsonObject().getAsJsonArray(DEPS);
                DependencyInfo dependencyInfo;
                for (int i = 0; i < deps.size(); i++){
                    dependencyInfo = new DependencyInfo();
                    JsonObject dep = deps.get(i).getAsJsonObject();
                    String importPath = dep.get(IMPORT_PATH).getAsString();
                    dependencyInfo.setGroupId(getGroupId(importPath));
                    dependencyInfo.setArtifactId(importPath);
                    dependencyInfo.setCommit(dep.get(REV).getAsString());
                    dependencyInfo.setDependencyType(DependencyType.GO);
                    dependencyInfo.setSystemPath(goDeps.getPath());
                    JsonElement commentElement = dep.get(COMMENT);
                    if (commentElement != null){
                        String comment = commentElement.getAsString();
                        if (comment.indexOf(Constants.DASH) > -1) {
                            comment = comment.substring(0, comment.indexOf(Constants.DASH));
                        }
                        dependencyInfo.setVersion(comment);
                    }
                    dependencyInfos.add(dependencyInfo);
                }
            }
        } catch (FileNotFoundException e){
            throw e;
        } finally {
            if (fileReader != null){
                fileReader.close();
            }
        }
        return dependencyInfos;
    }

    private String getGroupId(String name){
        String groupId =  Constants.EMPTY_STRING;
        if (name.contains(Constants.FORWARD_SLASH)) {
            String[] split = name.split( Constants.FORWARD_SLASH);
            groupId = split[1];
        }
        return groupId;
    }

    private void collectVndrDependencies(String rootDirectory, List<DependencyInfo> dependencyInfos) throws Exception {
        logger.debug("collecting dependencies using 'vndr'");
        File vndrConf = new File(rootDirectory + fileSeparator + VNDR_CONF);
        if (vndrConf.isFile() || (collectDependenciesAtRuntime && runCmd(rootDirectory,
                cli.getCommandParams(GoDependencyManager.VNDR.getType(), GO_INIT)))) {
            dependencyInfos.addAll(parseVendorConf(vndrConf));
        } else {
            throw new Exception("Can't find " + VNDR_CONF + " file.  Please make sure 'vndr' is installed and run 'vndr init' command");
        }
    }

    private List<DependencyInfo> parseVendorConf(File vendorConf) throws IOException {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(vendorConf);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String currLine;
            DependencyInfo dependencyInfo;
            while ((currLine = bufferedReader.readLine()) != null){
                dependencyInfo = new DependencyInfo();
                String[] split = currLine.split(Constants.WHITESPACE);
                String name = split[0];
                dependencyInfo.setGroupId(getGroupId(name));
                dependencyInfo.setArtifactId(name);
                dependencyInfo.setCommit(split[1]);
                dependencyInfo.setDependencyType(DependencyType.GO);
                dependencyInfo.setSystemPath(vendorConf.getPath());
                dependencyInfos.add(dependencyInfo);
            }
        } catch (FileNotFoundException e) {
            throw e;
        } finally {
            fileReader.close();
        }
        return dependencyInfos;
    }

    private void collectGoGradleDependencies(String rootDirectory, List<DependencyInfo> dependencyInfos) {
        logger.debug("collecting dependencies using 'GoGradle'");
        GradleCli gradleCli = new GradleCli(Constants.WRAPPER);
        List<String> lines = gradleCli.runGradleCmd(rootDirectory, gradleCli.getGradleCommandParams(GradleMvnCommand.DEPENDENCIES));
        if (lines != null) {
            parseGoGradleDependencies(lines, dependencyInfos, rootDirectory);
            File goGradleLock = new File(rootDirectory + fileSeparator + GOGRADLE_LOCK);
            if (goGradleLock.isFile() || (collectDependenciesAtRuntime && runCmd(rootDirectory, gradleCli.getGradleCommandParams(GradleMvnCommand.LOCK)))){
                HashMap<String, String> gradleLockFile = parseGoGradleLockFile(goGradleLock);
                // for each dependency - matching its full commit id
                dependencyInfos.stream().forEach(dependencyInfo -> dependencyInfo.setCommit(gradleLockFile.get(dependencyInfo.getArtifactId())));
                // removing dependencies without commit-id and version
                dependencyInfos.removeIf(dependencyInfo -> dependencyInfo.getCommit() == null && dependencyInfo.getVersion() == null);
            } else {
                logger.warn("Can't find {} and verify dependencies commit-ids; make sure 'collectDependenciesAtRuntime' is set to true or run 'gradlew lock' manually", goGradleLock.getPath());
            }
        } else {
            logger.warn("running `gradle dependencies` command failed");
        }
    }

    private void parseGoGradleDependencies(List<String> lines, List<DependencyInfo> dependencyInfos, String rootDirectory){
        List<String> filteredLines = lines.stream()
                .filter(line->(line.contains(SLASH) || line.contains(Constants.PIPE)) && !line.contains(ASTERIX))
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
                dependencyInfo.setDependencyType(DependencyType.GO);
                dependencyInfo.setSystemPath(rootDirectory + fileSeparator + "build.gradle");
                dependencyInfos.add(dependencyInfo);
            } catch (Exception e){
                logger.warn("Error parsing line {}, exception: {}", currentLine, e.getMessage());
                logger.debug("Exception: {}", e.getStackTrace());
            }
        }
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
                    if (currLine.contains(NAME + Constants.COLON + Constants.WHITESPACE)) {
                        name = currLine.substring(currLine.indexOf(Constants.QUOTATION_MARK) + 1);
                        name = name.replace(Constants.QUOTATION_MARK, Constants.EMPTY_STRING);
                    } else if (currLine.contains(COMMIT)) {
                        commit = currLine.substring(currLine.indexOf(Constants.QUOTATION_MARK) + 1);
                        commit = commit.replace(Constants.QUOTATION_MARK, Constants.EMPTY_STRING);
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

    private void collectGlideDependencies(String rootDirectory, List<DependencyInfo> dependencyInfos) throws Exception {
        logger.debug("collecting dependencies using 'Glide'");
        File glideLock = new File(rootDirectory + fileSeparator + GLIDE_LOCK);
        if (glideLock.isFile()) {
            dependencyInfos.addAll(parseGlideLock(glideLock));
        } else if (collectDependenciesAtRuntime) {
            File glideYaml = new File(rootDirectory + fileSeparator + GLIDE_YAML);
            if (glideYaml.isFile()) {
                if (runCmd(rootDirectory, cli.getCommandParams(GoDependencyManager.GLIDE.getType(), GO_UPDATE))) {
                    if (glideLock.isFile()) {
                        dependencyInfos.addAll(parseGlideLock(glideLock));
                    }
                } else {
                    throw new Exception("Failed to execute the command 'glide update'");
                }
            } else {
                throw new Exception("Can't find " + GLIDE_YAML + " file. Please make sure 'Glide' is installed and run 'Glide init' command");
            }
        } else {
            throw new Exception("Can't find " + GLIDE_LOCK + " file. Please make sure 'Glide' is installed and run 'Glide update' command");
        }
    }

    private Collection<DependencyInfo> parseGlideLock(File glideLock) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(glideLock));
            String currLine;
            String name = null;
            String commit = null;
            // this flag indicates if we get to imports line
            boolean resolveRepositoryPackages = false;
            boolean resolveSubPackages = false;
            while ((currLine = bufferedReader.readLine()) != null) {
                /* possible lines:
                    imports:
                    - name: github.com/json-iterator/go
                      version: 1624edc4454b8682399def8740d46db5e4362ba4
                    - name: github.com/modern-go/concurrent
                      version: bacd9c7ef1dd9b15be4a9909b8ac7a4e313eec94
                */
                if (!resolveRepositoryPackages && (currLine.startsWith(IMPORTS) || currLine.startsWith(TEST_IMPORTS))) {
                    if (currLine.startsWith(TEST_IMPORTS) && this.ignoreTestPackages) {
                        break;
                    }
                    resolveRepositoryPackages = true;
                } else if (resolveRepositoryPackages) {
                    if (currLine.startsWith(NAME_GLIDE)) {
                        resolveSubPackages = false;
                        name = currLine.substring(NAME_GLIDE.length());
                        currLine = bufferedReader.readLine();
                        if (currLine != null) {
                            commit = currLine.substring(VERSION_GLIDE.length());
                            dependencies.add(createGlideDependency(name, commit, glideLock.getAbsolutePath()));
                        }
                    } else if (currLine.startsWith(SUBPACKAGES_GLIDE)) {
                        resolveSubPackages = true;
                    } else if (resolveSubPackages && currLine.startsWith(PREFIX_SUBPACKAGES_SECTION)) {
                        String subPackageName = currLine.substring(PREFIX_SUBPACKAGES_SECTION.length());
                        dependencies.add(createGlideDependency(name + Constants.FORWARD_SLASH + subPackageName, commit, glideLock.getAbsolutePath()));
                    } else if (currLine.startsWith(TEST_IMPORTS)) {
                        resolveSubPackages = false;
                        if (this.ignoreTestPackages) {
                            break;
                        }
                    } else {
                        resolveSubPackages = false;
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Error parsing {}, exception: {}", glideLock.getName(), e.getMessage());
            logger.debug("Exception: {}", e.getStackTrace());
        } finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            } catch (IOException e) {
                logger.warn("Can't close {}, exception: {}", glideLock.getName(), e.getMessage());
                logger.debug("Exception: {}", e.getStackTrace());
            }
        }
        return dependencies;
    }

    private DependencyInfo createGlideDependency(String name, String commit, String systemPath) {
        DependencyInfo dependency = new DependencyInfo();
        dependency.setArtifactId(name);
        dependency.setCommit(commit);
        dependency.setDependencyType(DependencyType.GO);
        dependency.setSystemPath(systemPath);
        return dependency;
    }

    private boolean runCmd(String rootDirectory, String[] params){
        try {
            CommandLineProcess commandLineProcess = new CommandLineProcess(rootDirectory, params);
            commandLineProcess.executeProcessWithErrorOutput();
            if (!commandLineProcess.isErrorInProcess()) {
                return true;
            }
        } catch (IOException e) {
            logger.warn("Error getting dependencies after running {} on {}, {}" , params , rootDirectory, e.getMessage());
            logger.debug("Error: {}", e.getStackTrace());
        }
        return false;
    }

    // when running the dependency manager at run time different files (and folders) are created.  removing them according to their creatin time
    private void removeTempFiles(String rootDirectory, long creationTime){
        FileTime fileCreationTime = FileTime.fromMillis(creationTime);
        File directory = new File(rootDirectory);
        File[] fList = directory.listFiles();
        if (fList != null) {
            for (File file : fList) {
                try {
                    BasicFileAttributes fileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                    if (fileAttributes.creationTime().compareTo(fileCreationTime) > 0){
                        FileUtils.forceDelete(file);
                    }
                } catch (IOException e) {
                    logger.error("can't remove {}: {}", file.getPath(), e.getMessage());
                }
            }
        }
    }
}
