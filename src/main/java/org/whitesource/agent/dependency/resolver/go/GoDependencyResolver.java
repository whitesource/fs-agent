package org.whitesource.agent.dependency.resolver.go;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.gradle.GradleCli;
import org.whitesource.agent.dependency.resolver.gradle.GradleMvnCommand;
import org.whitesource.agent.hash.HashCalculator;
import org.whitesource.agent.utils.Cli;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.whitesource.agent.Constants.BUILD_GRADLE;
import static org.whitesource.agent.Constants.EMPTY_STRING;

public class GoDependencyResolver extends AbstractDependencyResolver {

    public static final String GOPM_GEN_CMD = "gen";
    private static final String GODEPS = "Godeps";
    private static final String VENDOR = "vendor";
    private final Logger logger = LoggerFactory.getLogger(GoDependencyResolver.class);

    private static final String PROJECTS        = "[[projects]]";
    private static final String DEPS            = "Deps";
    private static final String PACKAGE         = "package";
    private static final String REV             = "Rev";
    private static final String COMMENT         = "Comment";
    private static final String IMPORT_PATH     = "ImportPath";
    private static final String PATH            = "path";
    private static final String NAME            = "name";
    private static final String COMMIT          = "commit: ";
    private static final String VERSION         = "version = ";
    private static final String VERSION_GOV     = "version";
    private static final String REVISION        = "revision = ";
    private static final String REVISION_GOV    = "revision";
    private static final String CHECKSUM_SHA1    = "checksumSHA1";
    private static final String PACKAGES        = "packages = ";
    private static final String BRACKET         = "]";
    private static final String DOT             = ".";
    private static final String ASTERIX         = "(*)";
    private static final String SLASH           = "\\--";
    private static final String GOPKG_LOCK      = "Gopkg.lock";
    private static final String GODEPS_JSON     = "Godeps.json";
    private static final String GOVENDOR_JSON   = "vendor.json";
    private static final String VNDR_CONF       = "vendor.conf";
    private static final String GOGRADLE_LOCK   = "gogradle.lock";
    private static final String GLIDE_LOCK      = "glide.lock";
    private static final String GLIDE_YAML      = "glide.yaml";
    private static final String GOPM_FILE       = ".gopmfile";
    private static final String GO_EXTENSION    = ".go";
    private static final String GO_ENSURE       = "ensure";
    private static final String GO_INIT         = "init";
    private static final String GO_SAVE         = "save";
    private static final String GO_ADD_EXTERNAL = "add +external";
    private static final List<String> GO_SCRIPT_EXTENSION = Arrays.asList(GOPKG_LOCK, GODEPS_JSON, VNDR_CONF,
                                                                            BUILD_GRADLE, GLIDE_LOCK, GLIDE_YAML, GOVENDOR_JSON,
                                                                            GOPM_FILE);
    private static final String IMPORTS                     = "imports";
    private static final String NAME_GLIDE                  = "- name: ";
    private static final String VERSION_GLIDE               = "  version: ";
    private static final String SUBPACKAGES_GLIDE           = "  subpackages";
    private static final String PREFIX_SUBPACKAGES_SECTION  = "  - ";
    private static final String TEST_IMPORTS                = "testImports";
    private static final String GO_UPDATE                   = "update";
    private static final String GOPM_DEPS                   = "deps";
    private static final String OPENNING_BRACKET            = "[";
    private static final String EQUAL                       = "=";
    private static final String GOPM_TAG                    = "tag:";
    private static final String GOPM_COMMIT                 = "commit:";
    private static final String GOPM_BRANCH                 = "branch:";
    public static String  GO_DEPENDENCIES                   = "goDependencies";
    public static final String GRADLE_LOCK                  = "lock";
    public static final String GRADLE_GO_LOCK               = "goLock";

    private Cli cli;
    private GoDependencyManager goDependencyManager;
    private boolean collectDependenciesAtRuntime;
    private boolean ignoreSourceFiles;
    private boolean ignoreTestPackages;
    private boolean goGradleEnableTaskAlias;
    private String gradlePreferredEnvironment;
    private HashCalculator hashCalculator = new HashCalculator();
    private boolean addSha1;
    private boolean afterCollect = false;

    public GoDependencyResolver(GoDependencyManager goDependencyManager, boolean collectDependenciesAtRuntime, boolean ignoreSourceFiles, boolean ignoreTestPackages, boolean goGradleEnableTaskAlias, String gradlePreferredEnvironment, boolean addSha1){
        super();
        this.cli = new Cli();
        this.goDependencyManager = goDependencyManager;
        this.collectDependenciesAtRuntime = collectDependenciesAtRuntime;
        this.ignoreSourceFiles = ignoreSourceFiles;
        this.ignoreTestPackages = ignoreTestPackages;
        this.goGradleEnableTaskAlias = goGradleEnableTaskAlias;
        this.gradlePreferredEnvironment = gradlePreferredEnvironment;
        this.addSha1 = addSha1;
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        List<DependencyInfo> dependencies = collectDependencies(topLevelFolder);
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        if (afterCollect && ignoreSourceFiles){
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
    public String[] getBomPattern() {
        // when collectDependenciesAtRuntime=false, the FSA should look for the relevant lock/json file, when its true
        // the FSA should look for a *.go file, unless when the dependency-manager is go-gradle, don't return *.go but build.gradle
        if (goDependencyManager == null || (collectDependenciesAtRuntime && goDependencyManager != GoDependencyManager.GO_GRADLE)) {
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
                    return new String[]{Constants.GLOB_PATTERN_PREFIX + Constants.BUILD_GRADLE};
                case GLIDE:
                    return new String[]{Constants.PATTERN + GLIDE_LOCK, Constants.PATTERN + GLIDE_YAML};
                case GO_VENDOR:
                    return new String[]{Constants.PATTERN + GOVENDOR_JSON};
                case GOPM:
                    return new String[]{Constants.PATTERN + GOPM_FILE};
            }
        }
        return new String[]{EMPTY_STRING};
    }

    @Override
    public Collection<String> getManifestFiles(){
        return Arrays.asList(GOPKG_LOCK, GOVENDOR_JSON, VNDR_CONF, Constants.BUILD_GRADLE, GLIDE_LOCK, GLIDE_YAML, GOVENDOR_JSON, GOPM_FILE, GO_EXTENSION);
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
                    case GO_VENDOR:
                        collectGoVendorDependencies(rootDirectory, dependencyInfos);
                        break;
                    case GOPM:
                        collectGoPMDependencies(rootDirectory, dependencyInfos);
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

        if (collectDependenciesAtRuntime) {
            String errors = FilesUtils.removeTempFiles(rootDirectory, creationTime);
            if (!errors.isEmpty()){
                logger.error(errors);
            }
        }
        afterCollect = true;
        return dependencyInfos;
    }

    // when no dependency manager is defined - trying to run one manager after the other, till one succeeds.  if not - returning an error
    private String collectDependenciesWithoutDefinedManager(String rootDirectory, List<DependencyInfo> dependencyInfos){
        String error = null;
        try {
            collectDepDependencies(rootDirectory, dependencyInfos);
            goDependencyManager = GoDependencyManager.DEP;
        } catch (Exception e){
            try {
                collectGoDepDependencies(rootDirectory, dependencyInfos);
                goDependencyManager = GoDependencyManager.GO_DEP;
            } catch (Exception e1){
                try {
                    collectVndrDependencies(rootDirectory, dependencyInfos);
                    goDependencyManager = GoDependencyManager.VNDR;
                } catch (Exception e2) {
                    try {
                        collectGoGradleDependencies(rootDirectory, dependencyInfos);
                        goDependencyManager = GoDependencyManager.GO_GRADLE;
                    } catch (Exception e3) {
                        try {
                            collectGlideDependencies(rootDirectory, dependencyInfos);
                            goDependencyManager = GoDependencyManager.GLIDE;
                        } catch (Exception e4) {
                            try {
                                collectGoVendorDependencies(rootDirectory, dependencyInfos);
                                goDependencyManager = GoDependencyManager.GO_VENDOR;
                            } catch (Exception e5) {
                                try {
                                    collectGoPMDependencies(rootDirectory, dependencyInfos);
                                    goDependencyManager = GoDependencyManager.GOPM;
                                } catch (Exception e6) {
                                    error = "Couldn't collect dependencies - no dependency manager is installed";
                                }
                            }
                        }
                    }
                }
            }
        }
        return error;
    }

    private void collectDepDependencies(String rootDirectory, List<DependencyInfo> dependencyInfos) throws Exception {
        logger.debug("collecting dependencies using 'dep'");
        File goPkgLock = new File(rootDirectory + fileSeparator + GOPKG_LOCK);
        String error = EMPTY_STRING;
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
                                    packageDependencyInfo.setDependencyType(DependencyType.GO);
                                    if (useParent) {
                                        dependencyInfo.getChildren().add(packageDependencyInfo);
                                    } else {
                                        dependencyInfos.add(packageDependencyInfo);
                                    }
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
        dependencyInfos.stream().forEach(dependencyInfo -> {
            //dependencyInfo.setDependencyFile(goPckLock.getPath());
            dependencyInfo.setDependencyType(DependencyType.GO);
            setSha1(dependencyInfo);

        });
        return dependencyInfos;
    }

    private void collectGoPMDependencies(String rootDirectory, List<DependencyInfo> dependencyInfos) throws Exception {
        logger.debug("collecting dependencies using 'GoPM'");
        File goPMFile = new File(rootDirectory + fileSeparator + GOPM_FILE);
        String error = EMPTY_STRING;
        if (goPMFile.isFile()){
            dependencyInfos.addAll(parseGoPm(goPMFile));
        } else if (collectDependenciesAtRuntime) {
            if (runCmd(rootDirectory, cli.getCommandParams(GoDependencyManager.GOPM.getType(), GOPM_GEN_CMD))) {
                dependencyInfos.addAll(parseGoPm(goPMFile));
            } else {
                error = "Can't run 'gopm gen' command.  Make sure gopm is installed and run the 'gopm gen' command manually.";
                logger.warn("FileNotFoundException: {}", error);
            }
        } else {
            error = "Can't find " + GOPM_FILE + " file.  Run the 'gopm gen' command.";
            logger.warn("FileNotFoundException: {}", error);
        }
        if (!error.isEmpty()) {
            throw new Exception(error);
        }
    }

    private List<DependencyInfo> parseGoPm(File goPmFile){
        logger.debug("parsing {}", goPmFile.getPath());
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(goPmFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String currLine;
            //insideDeps is a boolean indicates if we are inside [deps] section in .gopmfile
            boolean insideDeps = false;
            DependencyInfo dependencyInfo = null;
            ArrayList<String> repositoryPackages = null;
            while ((currLine = bufferedReader.readLine()) != null){
                if (insideDeps) { //if we are inside the needed section
                    dependencyInfo = new DependencyInfo();
                    if (currLine.isEmpty() ) {
                        continue;
                    }
                    //if we are no longer in [deps] section
                    else if (currLine.contains(OPENNING_BRACKET) && currLine.contains(BRACKET) && !currLine.contains(GOPM_DEPS)){
                        insideDeps = false;
                    } else {
                        //example: github.com/astaxie/beego = tag:v1.9.2 , it will be splitted to two, 1- github.com/astaxie/beego , 2-tag:v1.9.2
                        String[] line = currLine.split(EQUAL);
                        if (line.length > 0) { //retrieving info from the first part {github.com/astaxie/beego}
                            //removing whitespaces
                            line[0] = line[0].trim();
                            dependencyInfo.setGroupId(getGroupId(line[0]));
                            dependencyInfo.setArtifactId(line[0]);
                        }
                        if (line.length > 1) {//retrieving info from the second part {tag:v1.9.2}
                            line[1] = line[1].trim();
                            if (line[1].contains(GOPM_TAG)) { //tag:v1.9.2
                                dependencyInfo.setVersion(line[1].substring(GOPM_TAG.length())); //extract the value after tag:
                            } else if (line[1].contains(GOPM_COMMIT)) {//commit:a210eea3bd1c3766d76968108dfcd83c331f549c
                                dependencyInfo.setCommit(line[1].substring(GOPM_COMMIT.length()));//extract the value after commit:
                            } else if (line[1].contains(GOPM_BRANCH)) { //branch:master
                                //toDo add branch
                                //dependencyInfo.(line[1].substring(GOPM_BRANCH.length()));
                                logger.warn("Using branch to define dependency is not supported, library {} will not be recognized by WSS", line[0]);
                            }
                        }
                        if (line.length <= 1 || line[1].equals(EMPTY_STRING)) {
                            logger.warn("Using dependency without tag/commit is not supported, library {}, will not be recognized by WSS", line[0]);
                            continue;
                        }
                        /*dependencyInfo.setDependencyType(DependencyType.GO);
                        dependencyInfo.setSystemPath(goPmFile.getPath());
                        setSha1(dependencyInfo);*/
                        dependencyInfos.add(dependencyInfo);
                    }
                } else if (currLine.contains(OPENNING_BRACKET + GOPM_DEPS + BRACKET)){ //if the current line contains [deps]
                    insideDeps = true;
                }
            }
        } catch (FileNotFoundException e) {
            logger.warn("FileNotFoundException: {}", e.getMessage());
            logger.debug("FileNotFoundException: {}", e.getStackTrace());
        } catch (IOException e) {
            logger.warn("IOException: {}", e.getMessage());
            logger.debug("IOException: {}", e.getStackTrace());
        } finally {
            if (fileReader != null){
                try {
                    fileReader.close();
                } catch (IOException e) {
                    logger.warn("IOException: {}", e.getMessage());
                    logger.debug("IOException: {}", e.getStackTrace());
                }
            }
        }
        dependencyInfos.stream().forEach(dependencyInfo -> {
            //dependencyInfo.setDependencyFile(goPmFile.getPath());
            dependencyInfo.setDependencyType(DependencyType.GO);
            setSha1(dependencyInfo);
        });
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
        // apparently when go.collectDependenciesAtRuntime=false, the rootDirectory includes the 'Godeps' folder as well - in such case removing it from the path
        File goDepJson = new File(rootDirectory + (!collectDependenciesAtRuntime && rootDirectory.endsWith(GODEPS) ? "" : fileSeparator + GODEPS) + fileSeparator +  GODEPS_JSON);
        if (goDepJson.isFile() || (collectDependenciesAtRuntime && runCmd(rootDirectory, cli.getCommandParams(GoDependencyManager.GO_DEP.getType(), GO_SAVE)))){
            dependencyInfos.addAll(parseGoDeps(goDepJson));
        } else {
            throw new Exception("Can't find " + GODEPS_JSON + " file.  Please make sure 'godep' is installed and run 'godep save' command");
        }
    }

    private List<DependencyInfo> parseGoDeps(File goDeps) throws IOException {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        HashMap<String, DependencyInfo> dependencyInfoHashMap = new HashMap<>();
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
                    //dependencyInfo.setDependencyFile(goDeps.getPath());
                    setSha1(dependencyInfo);
                    JsonElement commentElement = dep.get(COMMENT);
                    if (commentElement != null){
                        String comment = commentElement.getAsString();
                        if (comment.indexOf(Constants.DASH) > -1) {
                            comment = comment.substring(0, comment.indexOf(Constants.DASH));
                        }
                        dependencyInfo.setVersion(comment);
                    }
                    setInHierarchyTree(dependencyInfos, dependencyInfoHashMap, dependencyInfo, importPath);
                    //dependencyInfos.add(dependencyInfo);
                }
            }
        } catch (FileNotFoundException e){
            logger.warn("FileNotFoundException: {}", e.getMessage());
            logger.debug("FileNotFoundException: {}", e.getStackTrace());
        } finally {
            if (fileReader != null){
                fileReader.close();
            }
        }
        return dependencyInfos;
    }

    private String getGroupId(String name){
        String groupId =  EMPTY_STRING;
        if (name.contains(Constants.FORWARD_SLASH)) {
            String[] split = name.split( Constants.FORWARD_SLASH);
            groupId = split[1];
        }
        return groupId;
    }

    private void collectGoVendorDependencies(String rootDirectory, List<DependencyInfo> dependencyInfos) throws Exception {
        logger.debug("collecting dependencies using 'govendor'");
        // apparently when go.collectDependenciesAtRuntime=false, the rootDirectory includes the 'vendor' folder as well - in such case removing it from the path
        File goVendorJson = new File(rootDirectory  + (!collectDependenciesAtRuntime && rootDirectory.endsWith(VENDOR) ? "" : fileSeparator + VENDOR ) + fileSeparator + GOVENDOR_JSON);
        if (goVendorJson.isFile() || (collectDependenciesAtRuntime && runCmd(rootDirectory, cli.getCommandParams(GoDependencyManager.GO_VENDOR.getType(), GO_INIT)) &&
                runCmd(rootDirectory, cli.getCommandParams(GoDependencyManager.GO_VENDOR.getType(), GO_ADD_EXTERNAL)))){
            dependencyInfos.addAll(parseGoVendor(goVendorJson));
        } else  {
            throw new Exception("Can't find " + GOVENDOR_JSON + " file.  Please make sure 'govendor' is installed and run 'govendor init' command");
        }
    }

    private List<DependencyInfo> parseGoVendor(File goVendor) throws IOException {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        HashMap<String, DependencyInfo> dependencyInfoHashMap = new HashMap<>();
        JsonParser parser = new JsonParser();
        FileReader fileReader = null;
        //parse GoVendor dependency json file
        try {
            fileReader = new FileReader(goVendor.getPath());
            JsonElement dependencyElement = parser.parse(fileReader);
            if (dependencyElement.isJsonObject()){
                //foreach dependency info get relevant parameters
                JsonArray packages = dependencyElement.getAsJsonObject().getAsJsonArray(PACKAGE);
                if (packages != null) {
                    logger.debug("Number of packeges in json: {}", packages.size());
                    DependencyInfo dependencyInfo;
                    for (int i = 0; i < packages.size(); i++) {
                        logger.debug("Packeges in json #{} : {}", i, packages.get(i).toString());
                        dependencyInfo = new DependencyInfo();
                        JsonObject pck = packages.get(i).getAsJsonObject();
                        String name = pck.get(PATH).getAsString();
                        dependencyInfo.setGroupId(getGroupId(name));
                        dependencyInfo.setArtifactId(name);
                        dependencyInfo.setCommit(pck.get(REVISION_GOV).getAsString());
                        dependencyInfo.setDependencyType(DependencyType.GO);
                        //dependencyInfo.setDependencyFile(goVendor.getPath());
                        setSha1(dependencyInfo);
                        if (pck.get(VERSION_GOV) != null) {
                            dependencyInfo.setVersion(pck.get(VERSION_GOV).getAsString());
                        }
                        setInHierarchyTree(dependencyInfos, dependencyInfoHashMap, dependencyInfo, name);
                    }
                }
            }
        } catch (FileNotFoundException e){
            logger.warn("FileNotFoundException: {}", e.getMessage());
            logger.debug("FileNotFoundException: {}", e.getStackTrace());
        } finally {
            if (fileReader != null){
                fileReader.close();
            }
        }
        return dependencyInfos;
    }

    private void setInHierarchyTree(List<DependencyInfo> dependencyInfos, HashMap<String, DependencyInfo> dependencyInfoHashMap, DependencyInfo dependencyInfo, String name) {
        boolean childDependency = false;
        dependencyInfoHashMap.put(name, dependencyInfo);
        // checking if the dependency is child of another (if its name is contained inside the name of other dependency)
        while (name.contains(Constants.FORWARD_SLASH)){
            name = name.substring(0, name.lastIndexOf(Constants.FORWARD_SLASH));
            if (dependencyInfoHashMap.get(name) != null){
                dependencyInfoHashMap.get(name).getChildren().add(dependencyInfo);
                childDependency = true;
                break;
            }
        }
        if (!childDependency){
            dependencyInfos.add(dependencyInfo);
        }
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
                //dependencyInfo.setDependencyFile(vendorConf.getPath());
                setSha1(dependencyInfo);
                dependencyInfos.add(dependencyInfo);
            }
        } catch (FileNotFoundException e) {
            throw e;
        } finally {
            fileReader.close();
        }
        return dependencyInfos;
    }

    private void collectGoGradleDependencies(String rootDirectory, List<DependencyInfo> dependencyInfos) throws Exception {
        logger.debug("collecting dependencies using 'GoGradle'");
        GradleCli gradleCli = new GradleCli(this.gradlePreferredEnvironment);
        // WSE-1076 - this is actually a go-gradle issue, not gradle;
        // one cannot operate twice on the same stream - i.e. check the stream's count followed by forEach.
        // therefore putting the stream into a Supplier object, from which the stream can be accessed multiple times using 'get'
        Supplier<Stream<Path>> supplier = ()-> {
            try {
                return Files.walk(Paths.get(rootDirectory), Integer.MAX_VALUE).filter(file -> file.getFileName().toString().equals(Constants.BUILD_GRADLE));
            } catch (IOException e) {
                logger.warn("Error collecting go-gradle dependencies from {}, exception: {}", rootDirectory, e.getMessage());
                logger.debug("Exception: {}", e.getStackTrace());
            }
            return null;
        };
        if (supplier != null && supplier.get().count() > 0) {
            supplier.get().forEach(file -> {
                GradleMvnCommand command = this.goGradleEnableTaskAlias ? GradleMvnCommand.GO_DEPENDENCIES : GradleMvnCommand.DEPENDENCIES;
                List<String> lines = gradleCli.runGradleCmd(file.getParent().toString(), gradleCli.getGradleCommandParams(command), true);
                if (lines != null) {
                    parseGoGradleDependencies(lines, dependencyInfos, rootDirectory);
                    if (dependencyInfos.size() > 0) {
                        File goGradleLock = new File(rootDirectory + fileSeparator + GOGRADLE_LOCK);
                        // in case goGradle-enable-task-alias is true - use goLock instead of lock
                        if (goGradleLock.isFile() || (collectDependenciesAtRuntime && runCmd(rootDirectory, gradleCli.getGradleCommandParams(this.goGradleEnableTaskAlias ? GradleMvnCommand.GO_LOCK : GradleMvnCommand.LOCK)))) {
                            HashMap<String, String> gradleLockFile = parseGoGradleLockFile(goGradleLock);
                            // for each dependency - matching its full commit id
                            dependencyInfos.stream().forEach(dependencyInfo -> dependencyInfo.setCommit(gradleLockFile.get(dependencyInfo.getArtifactId())));
                            // removing dependencies without commit-id and version
                            dependencyInfos.stream().forEach(dependencyInfo -> {
                                if (dependencyInfo.getVersion() == null && dependencyInfo.getCommit() == null) {
                                    logger.debug("{}/{} has no version nor commit-id; removing it from the dependencies' list", dependencyInfo.getArtifactId(), dependencyInfo.getGroupId());
                                } else {
                                    setSha1(dependencyInfo);
                                }
                            });
                            dependencyInfos.removeIf(dependencyInfo -> dependencyInfo.getCommit() == null && dependencyInfo.getVersion() == null);
                        } else {
                            logger.warn("Can't find {} and verify dependencies commit-ids; make sure 'collectDependenciesAtRuntime' is set to true or run 'gradlew lock' manually", goGradleLock.getPath());
                        }
                    } else {
                        logger.warn("no dependencies found after running 'gradlew " + command.getCommand() + " command.  \n" +
                                "If your gradle.properties file includes 'org.gradle.jvmargs=-Dgogradle.alias=true' make sure that 'go.gogradle.enableTaskAlias' in the configuration file is set to 'true'");
                    }
                } else {
                    logger.warn("running `gradlew " + command.getCommand() + "` command failed.  \n" +
                            "If your gradle.properties file includes 'org.gradle.jvmargs=-Dgogradle.alias=true' make sure that 'go.gogradle.enableTaskAlias' in the configuration file is set to 'true';\n" +
                            "otherwise - set it to false");
                }
            });
        } else {
            throw new Exception("Can't find any 'build.gradle' file.  Please make sure Gradle is installed");
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
                //dependencyInfo.setDependencyFile(rootDirectory + fileSeparator + Constants.BUILD_GRADLE);
                dependencyInfos.add(dependencyInfo);
            } catch (Exception e){
                logger.warn("Error parsing line {}, exception: {}", currentLine, e.getMessage());
                logger.debug("Exception: {}", e.getStackTrace());
            }
        }
    }

    /*
     parsing such lines -
     apiVersion: "0.10"
     dependencies:
       build:
       - name: "golang.org/x/crypto"
         host:
           name: "github.com/astaxie/beego"
           commit: "053a075344c118a5cc41981b29ef612bb53d20ca"
           urls:
             - "https://github.com/astaxie/beego.git"
             - "git@github.com:astaxie/beego.git"
           vcs: "git"
         vendorPath: "vendor/golang.org/x/crypto"
         transitive: false
       - urls:
         - "https://github.com/google/go-querystring.git"
         - "git@github.com:google/go-querystring.git"
         vcs: "git"
         name: "github.com/google/go-querystring"
         commit: "53e6ce116135b80d037921a7fdd5138cf32d7a8a"
         transitive: false

     ignore lines not starting with 2 white-spaces or starting with 4 white-spaces & a dash or 6 white-spaces (meaning indentation)
     extract only the name and commit
     */
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
                String WS = Constants.WHITESPACE;
                while ((currLine = bufferedReader.readLine()) != null) {
                    if (currLine.startsWith(WS + WS) == false || currLine.startsWith(WS + WS + WS + WS + Constants.DASH) || currLine.startsWith(WS + WS + WS + WS + WS + WS))
                        continue;
                    if (currLine.startsWith(WS + WS + Constants.DASH + WS)) { // start of a block
                        if (name != null && commit != null){
                            dependenciesCommits.put(name, commit); // add previous block (if found)
                        }
                        name = null;
                        commit = null;
                    }
                    // WSE-823 - goGradle.lock file may contain quotation marks, apostrophes (probably - didn't meet any such example yet) or none
                    if (currLine.contains(NAME + Constants.COLON + WS)) {
                        name = currLine.substring(currLine.indexOf(Constants.COLON) + 1).trim();
                        name = name.replace(Constants.QUOTATION_MARK, EMPTY_STRING);
                        name = name.replace(Constants.APOSTROPHE, EMPTY_STRING);
                    } else if (currLine.contains(COMMIT)) {
                        commit = currLine.substring(currLine.indexOf(Constants.COLON) + 1).trim();
                        commit = commit.replace(Constants.QUOTATION_MARK, EMPTY_STRING);
                        commit = commit.replace(Constants.APOSTROPHE, EMPTY_STRING);
                    }
                }
                if (name != null && commit != null){ // finished last block
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
                    } else {
                        throw new Exception("Can't find " + GLIDE_LOCK + " file after running 'glide update'. Please make sure 'Glide' is installed and run 'Glide update' command");
                    }
                } else {
                    throw new Exception("Can't find " + GLIDE_LOCK + " file. Please make sure 'Glide' is installed and run 'Glide update' command");
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
            DependencyInfo currentDependency = null;
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
                            currentDependency = createGlideDependency(name, commit, glideLock.getAbsolutePath());
                            dependencies.add(currentDependency);
                        }
                    } else if (currLine.startsWith(SUBPACKAGES_GLIDE)) {
                        resolveSubPackages = true;
                    } else if (resolveSubPackages && currLine.startsWith(PREFIX_SUBPACKAGES_SECTION)) {
                        String subPackageName = currLine.substring(PREFIX_SUBPACKAGES_SECTION.length());
                        DependencyInfo childDependency = createGlideDependency(name + Constants.FORWARD_SLASH + subPackageName, commit, glideLock.getAbsolutePath());
                        if (currentDependency == null) {
                            dependencies.add(childDependency);
                        } else {
                            currentDependency.getChildren().add(childDependency);
                        }
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
        //dependency.setDependencyFile(systemPath);
        setSha1(dependency);
        return dependency;
    }

    private void setSha1(DependencyInfo dependencyInfo){
        if (this.addSha1) {
            String artifactId = dependencyInfo.getArtifactId();
            String version = dependencyInfo.getVersion();
            String commit = dependencyInfo.getCommit();
            if (StringUtils.isBlank(version) && StringUtils.isBlank(commit)) {
                logger.debug("Unable to calcluate SHA1 for {}, it has no version nor commit-id", artifactId);
                return;
            }
            String sha1 = null;
            String sha1Source = StringUtils.isNotBlank(version) ? version : commit;
            try {
                sha1 = this.hashCalculator.calculateSha1ByNameVersionAndType(artifactId, sha1Source, DependencyType.GO);
            } catch (IOException e) {
                logger.debug("Failed to calculate sha1 of: {}", artifactId);
            }
            if (sha1 != null) {
                dependencyInfo.setSha1(sha1);
            }
        }
    }

    private boolean runCmd(String rootDirectory, String[] params){
        try {
            CommandLineProcess commandLineProcess = new CommandLineProcess(rootDirectory, params);
            List<String> a = commandLineProcess.executeProcessWithErrorOutput();
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