package org.whitesource.agent.dependency.resolver.go;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.Cli;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;

public class GoDependencyResolver extends AbstractDependencyResolver {

    private final Logger logger = LoggerFactory.getLogger(GoDependencyResolver.class);

    private static final String PROJECTS =      "[[projects]]";
    private static final String DEPS            = "Deps";
    private static final String REV             = "Rev";
    private static final String COMMENT         = "Comment";
    private static final String IMPORT_PATH     = "ImportPath";
    private static final String NAME            = "name = ";
    private static final String VERSION         = "version = ";
    private static final String REVISION        = "revision = ";
    private static final String PACKAGES        = "packages = ";
    private static final String BRACKET         = "]";
    private static final String DOT =           ".";
    private static final String GOPKG_LOCK      = "Gopkg.lock";
    private static final String GODEPS_JSON     = "Godeps.json";
    private static final String VNDR_CONF       = "vendor.conf";
    private static final String GO_EXTENTION    = ".go";
    private static final String GO_ENSURE       = "ensure";
    private static final String GO_INIT         = "init";
    private static final String GO_SAVE         = "save";
    private static final List<String> GO_SCRIPT_EXTENSION = Arrays.asList(".lock", ".json", GO_EXTENTION);

    private Cli cli;
    private GoDependencyManager goDependencyManager;
    private boolean collectDependenciesAtRuntime;
    private boolean isDependenciesOnly;

    public GoDependencyResolver(GoDependencyManager goDependencyManager, boolean collectDependenciesAtRuntime, boolean isDependenciesOnly){
        super();
        this.cli = new Cli();
        this.goDependencyManager = goDependencyManager;
        this.collectDependenciesAtRuntime = collectDependenciesAtRuntime;
        this.isDependenciesOnly = isDependenciesOnly;
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
            excludes.add(Constants.PATTERN + GO_EXTENTION);
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
            return new String[]{Constants.PATTERN + GO_EXTENTION};
        }
        if (goDependencyManager != null) {
            switch (goDependencyManager) {
                case DEP:
                    return new String[]{Constants.PATTERN + GOPKG_LOCK};
                case GO_DEP:
                    return new String[]{Constants.PATTERN + GODEPS_JSON};
                case VNDR:
                    return new String[]{Constants.PATTERN + VNDR_CONF};
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
                    error = "Couldn't collect dependencies - no dependency manager is installed";
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
            if (cli.runCmd(rootDirectory, cli.getCommandParams(GoDependencyManager.DEP.getType(), GO_ENSURE)) == null) {
                logger.warn("Can't run 'dep ensure' command, output might be outdated.  Run the 'dep ensure' command manually.");
            }
            dependencyInfos.addAll(parseGopckLock(goPkgLock));
        } else if (collectDependenciesAtRuntime) {
            if (cli.runCmd(rootDirectory, cli.getCommandParams(GoDependencyManager.DEP.getType(), GO_INIT))!= null) {
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
                        } else if (currLine.contains(NAME)){
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
        if (goDepJson.isFile() || (collectDependenciesAtRuntime && cli.runCmd(rootDirectory, cli.getCommandParams(GoDependencyManager.GO_DEP.getType(), GO_SAVE)) != null)){
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
        if (vndrConf.isFile() || (collectDependenciesAtRuntime && cli.runCmd(rootDirectory,
                cli.getCommandParams(GoDependencyManager.VNDR.getType(), GO_INIT)) != null)) {
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
