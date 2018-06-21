package org.whitesource.agent.dependency.resolver.ruby;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.hash.ChecksumUtils;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.whitesource.agent.dependency.resolver.ruby.RubyDependencyResolver.GEM;

public class RubyDependencyResolver extends AbstractDependencyResolver {

    private static final String GEM_FILE_LOCK = "Gemfile.lock";
    private static final String ORIG = ".orig";
    private static final List<String> RUBY_SCRIPT_EXTENSION = Arrays.asList(".rb");
    private static final String BUNDLE         = "bundle";
    private static final String ENVIRONMENT    = "environment gemdir";
    private static final char TILDE = '~';
    protected static final String GEM          = "gem";
    protected static final String REGEX = "\\S";
    protected static final String SPECS = "specs:";
    protected static final String CACHE = "cache";
    protected static final String V     = "-v";
    protected static final String ERROR = "ERROR";
    protected static final String MINGW = "mingw";

    private final Logger logger = LoggerFactory.getLogger(RubyDependencyResolver.class);

    private RubyCli cli;
    private boolean runBundleInstall;
    private boolean overwriteGemFile;
    private boolean installMissingGems;
    private String rootDirectory;

    public RubyDependencyResolver(boolean runBundleInstall, boolean overwriteGemFile, boolean installMissingGems){
        super();
        cli = new RubyCli();
        this.runBundleInstall = runBundleInstall;
        this.overwriteGemFile = overwriteGemFile;
        this.installMissingGems = installMissingGems;
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        rootDirectory = topLevelFolder;
        List<DependencyInfo> dependencies = collectDependencies();
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        excludes.add(Constants.PATTERN + RUBY_SCRIPT_EXTENSION);
        return excludes;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return RUBY_SCRIPT_EXTENSION;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.RUBY;
    }

    @Override
    protected String getDependencyTypeName() {
        return DependencyType.RUBY.name();
    }

    @Override
    protected String[] getBomPattern() {
        return new String[]{Constants.PATTERN + GEM_FILE_LOCK};
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    private List<DependencyInfo> collectDependencies() {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        File gemFileLock = new File(rootDirectory + fileSeparator + GEM_FILE_LOCK);
        File gemFileLockOrig = new File(rootDirectory + fileSeparator + GEM_FILE_LOCK + ORIG);

        if (runBundleInstall) {
            runBundleInstall(gemFileLock, gemFileLockOrig);
        }
        if (gemFileLock.isFile()){
            parseGemFileLock(gemFileLock, dependencyInfos);
        } else {
            // actually we should never reach here - if Gemlock.file isn't found the RubyDependencyResolver won't run
            logger.warn("Can't scan Gemlock.file - not found");
        }
        if (gemFileLockOrig.isFile()){
            removeTempFile(gemFileLock, gemFileLockOrig);
        }
        return dependencyInfos;
    }

    private boolean runBundleInstall(File gemFileLock, File origGemFileLock) {
        if (!overwriteGemFile && gemFileLock.isFile()){
            // rename the original Gemfile.lock (if it exists)
            gemFileLock.renameTo(origGemFileLock);
        }
        boolean bundleInstallSuccess = cli.runCmd(rootDirectory, cli.getCommandParams(BUNDLE, Constants.INSTALL)) != null && gemFileLock.isFile();
        if (!bundleInstallSuccess && !overwriteGemFile){
            // when running the 'bundle install' command failed and the original file was renamed - restore its name
            origGemFileLock.renameTo(gemFileLock);
        }
        return bundleInstallSuccess;
    }

    private void removeTempFile(File gemFileLock, File origGemFileLock){
        // when the original Gemfile.lock was renamed - remove the temp file and restore the original file its name
        if (origGemFileLock.isFile()){
            try {
                FileUtils.forceDelete(gemFileLock);
                origGemFileLock.renameTo(gemFileLock);
            } catch (IOException e) {
                logger.warn("can't remove {}: {}", gemFileLock.getPath(), e.getMessage());
            }
        }
    }

    private void parseGemFileLock(File gemLockFile, List<DependencyInfo> dependencyInfos){
        /*
        * Gemfile.lock's (relevant) content structure:
         GEM
            remote: https://rubygems.org/
            specs:
                httparty (0.13.7)
                    json (~> 1.8)
                    multi_xml (>= 0.5.2)
                json (1.8.6)
                kramdown (1.8.0)
                multi_xml (0.5.5)
                parallel (1.6.1)
        * */
        String pathToGems = null;
        try {
            pathToGems = findPathToGems();
            if (pathToGems == null){
                logger.warn("Can't find path to gems' cache folder");
                return;
            }
        } catch (FileNotFoundException e) {
            logger.warn("Can't find path to gems' cache folder {}", e.getMessage());
            return;
        }
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(gemLockFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String currLine;
            boolean insideGem = false;
            boolean insideSpecs = false;
            Pattern pattern = Pattern.compile(REGEX);
            Matcher matcher;
            Integer previousIndex = 0;
            boolean indented = false;
            DependencyInfo dependencyInfo = null;
            List<DependencyInfo> parentsList = new LinkedList<>();
            List<DependencyInfo> childrenList = new LinkedList<>();
            List<DependencyInfo> partialDependencies = new LinkedList<>();
            DependencyInfo parentDependency = null;
            whileLoop:
            while ((currLine = bufferedReader.readLine()) != null) {
                if (insideGem && insideSpecs){
                    if (currLine.isEmpty()) {
                        break;
                    } else {
                        matcher = pattern.matcher(currLine);
                        if (matcher.find()){
                            int index = matcher.start();
                            if ((index > previousIndex && previousIndex > 0) || (indented && index == previousIndex)){
                                // inside indentation - a child dependency
                                if (parentDependency == null){
                                    continue whileLoop;
                                }
                                indented = true;
                                previousIndex = index;
                                int spaceIndex = currLine.indexOf(Constants.WHITESPACE, index);
                                String name = currLine.substring(index, spaceIndex > -1 ? spaceIndex : currLine.length());
                                // looking for the dependency in the parents' list
                                dependencyInfo = parentsList.stream().filter(d -> d.getGroupId().equals(name)).findFirst().orElse(null);
                                if (dependencyInfo == null) {
                                    dependencyInfo = childrenList.stream().filter(d -> d.getGroupId().equals(name)).findFirst().orElse(null);
                                    if (dependencyInfo == null) {
                                        dependencyInfo = new DependencyInfo();
                                        dependencyInfo.setGroupId(name);
                                        int indexOfOpenBracket = currLine.indexOf(Constants.OPEN_BRACKET);
                                        int indexOfCloseBracket = currLine.indexOf(Constants.CLOSE_BRACKET);
                                        if (indexOfOpenBracket != -1 && indexOfCloseBracket != -1) {
                                            String version = currLine.substring(currLine.indexOf(Constants.OPEN_BRACKET) + 1, currLine.indexOf(Constants.CLOSE_BRACKET));
                                            int indexSeparator = version.indexOf(Constants.COMMA);
                                            if (indexSeparator != -1) {
                                                version = version.substring(0, indexSeparator);
                                            }
                                            // take only the version number in case of version with tilde. For example: concurrent-ruby (~> 1.0)
                                            if (version.charAt(0) == TILDE) {
                                                dependencyInfo.setVersion(version.substring(3));
                                            } else {
                                                dependencyInfo.setVersion(version);
                                            }
                                        }
                                        if (partialDependencies.contains(dependencyInfo) == false) {
                                            partialDependencies.add(dependencyInfo);
                                        }
                                    }
                                }
                                // adding this dependency as a child to its parent
                                parentDependency.getChildren().add(dependencyInfo);
                                // using loop with `equal` and not `contains` since the contains would fail when the key of a hash map is modified after its creation (as in this case)
                                // if this dependency is already found in the children's list - continue
                                for (DependencyInfo d : childrenList){
                                    if (d.equals(dependencyInfo)){
                                        continue whileLoop;
                                    }
                                }
                                childrenList.add(dependencyInfo);
                            } else {
                                // inside a parent dependency
                                String[] split = currLine.trim().split(Constants.WHITESPACE);
                                String name = split[0];
                                String version = split[1].substring(1, split[1].length()-1);
                                try {
                                    String sha1 = getRubyDependenciesSha1(name, version, pathToGems);
                                    if (sha1 == null){
                                        logger.warn("Can't find gem file for {}-{}", name, version);
                                        continue whileLoop;
                                    }
                                    // looking for this dependency in the children's list (in case its already a child of some other dependency)
                                    dependencyInfo = childrenList.stream().filter(d -> d.getGroupId().equals(name)).findFirst().orElse(null);
                                    if (dependencyInfo == null) {
                                        dependencyInfo = new DependencyInfo(sha1);
                                        dependencyInfo.setGroupId(name);
                                    } else {
                                        dependencyInfo.setSha1(sha1);
                                    }
                                    partialDependencies.remove(dependencyInfo);
                                    setDependencyInfoProperties(dependencyInfo, name, version,gemLockFile, pathToGems);
                                    parentsList.add(dependencyInfo);
                                    parentDependency = dependencyInfo;
                                } catch (IOException e){
                                    logger.warn("Can't find gem file for {}-{}", name, version);
                                } finally {
                                    indented = false;
                                    previousIndex = index;
                                }
                            }
                        }
                    }
                } else if (currLine.contains(GEM.toUpperCase())){
                    insideGem = true;
                } else if (insideGem && currLine.contains(SPECS)){
                    insideSpecs = true;
                }
            }
            // creating the dependencies list by using only the parent dependencies, i.e. - those that aren't found in the children's list
            // using loop with `equal` and not `contains` since the contains would fail when the key of a hash map is modified after its creation (as in this case)
            for (DependencyInfo parent : parentsList){
                boolean foundChild = false;
                for (DependencyInfo child : childrenList){
                    if (parent.equals(child)) {
                        foundChild = true;
                        break;
                    }
                }
                if (!foundChild){
                    dependencyInfos.add(parent);
                }
            }

            // partial dependencies are those who appear in the Gemfile.lock only as child dependencies, thus without valid version.
            // in such case, remove that dependency from its parent
            for (DependencyInfo partialDependency : partialDependencies){
                String version = findGemVersion(partialDependency.getGroupId(), pathToGems);
                String versionToCompare = null;
                if (partialDependency.getVersion() != null) {
                    char firstChar = partialDependency.getVersion().charAt(0);
                    if (firstChar == '>' || firstChar == Constants.EQUALS_CHAR) {
                        versionToCompare = partialDependency.getVersion().substring(3);
                    }
                }
                if (version == null || (versionToCompare != null && versionCompare(versionToCompare, version) > 0)) {
                    List<String> lines = installGem(partialDependency.getGroupId(), Constants.APOSTROPHE + partialDependency.getVersion() + Constants.APOSTROPHE);
                    if (lines != null) {
                        File file = findMaxVersionFile(partialDependency.getGroupId(), pathToGems);
                        if (file != null) {
                            String sha1 = ChecksumUtils.calculateSHA1(file);
                            fillDependency(sha1, partialDependency, getVersionFromFileName(file.getName(), partialDependency.getGroupId()), gemLockFile, pathToGems, dependencyInfos);
                        } else {
                            logger.warn("Can't find version for {}-{}", partialDependency.getGroupId());
                            removeChildren(dependencyInfos, partialDependency);
                        }
                    } else {
                        logger.warn("Can't find version for {}-{}", partialDependency.getGroupId());
                        removeChildren(dependencyInfos, partialDependency);
                    }
                } else {
                    String sha1 = getRubyDependenciesSha1(partialDependency.getGroupId(), version, pathToGems);
                    fillDependency(sha1, partialDependency, version, gemLockFile, pathToGems, dependencyInfos);
                }
            }
        } catch (FileNotFoundException e){
            logger.warn("Could not find Gemfile.lock {}", e.getMessage());
            logger.debug("stacktrace {}", e.getStackTrace());
        } catch (Exception e) {
            logger.warn("Could not parse Gemfile.lock {}", e.getMessage());
            logger.debug("stacktrace {}", e.getStackTrace());
        } finally {
            try {
                fileReader.close();
            } catch (IOException e) {
                logger.warn("Can't close Gemfile.lock {}", e.getMessage());
                logger.debug("stacktrace {}", e.getStackTrace());
            }
        }
    }

    private void fillDependency(String sha1, DependencyInfo partialDependency, String version, File gemLockFile, String pathToGems, List<DependencyInfo> dependencyInfos) {
        if (sha1 == null){
            logger.warn("Can't find gem file for {}-{}", partialDependency.getGroupId(), version);
            removeChildren(dependencyInfos, partialDependency);
        } else {
            partialDependency.setSha1(sha1);
            setDependencyInfoProperties(partialDependency, partialDependency.getGroupId(), version, gemLockFile, pathToGems);
        }
    }

    /*
     * The result is a negative integer if str1 is _numerically_ less than str2.
     *         The result is a positive integer if str1 is _numerically_ greater than str2.
     *         The result is zero if the strings are _numerically_ equal.
     *
     */
    public static int versionCompare(String str1, String str2) {
        String[] vals1 = str1.split("\\.");
        String[] vals2 = str2.split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        return Integer.signum(vals1.length - vals2.length);
    }

    private void removeChildren(Collection<DependencyInfo> dependencyInfos, DependencyInfo child){
        Iterator<DependencyInfo> iterator = dependencyInfos.iterator();
        while (iterator.hasNext()){
            DependencyInfo dependencyInfo = iterator.next();
            if (dependencyInfo.getChildren().size() > 0){
                removeChildren(dependencyInfo.getChildren(), child);
            } else if (dependencyInfo.equals(child)){
                iterator.remove();
            }
        }
    }

    private void setDependencyInfoProperties(DependencyInfo dependencyInfo, String name, String version, File gemLockFile, String pathToGems){
        dependencyInfo.setArtifactId(name + Constants.DASH + version + Constants.DOT + GEM);
        dependencyInfo.setVersion(version);
        dependencyInfo.setDependencyType(DependencyType.RUBY);
        dependencyInfo.setSystemPath(gemLockFile.getPath());
        dependencyInfo.setFilename(pathToGems + fileSeparator + name + Constants.DASH + version + Constants.DOT + GEM);
    }

    // Ruby's cache is inside the installation folder.  path can be found by running command 'gem environment gemdir'
    private String findPathToGems() throws FileNotFoundException {
        String[] commandParams = cli.getCommandParams(GEM, ENVIRONMENT);
        List<String> lines = cli.runCmd(rootDirectory, commandParams);
        String path = null;
        if (lines != null){
            path = lines.get(0) + fileSeparator + CACHE;
            if (new File(path).isDirectory() == false){
                throw new FileNotFoundException();
            }
        }
        return path;
    }

    private String getRubyDependenciesSha1(String name, String version, String pathToGems) throws IOException {
        String sha1 = null;
        File file = new File(pathToGems + fileSeparator + name + Constants.DASH + version + Constants.DOT + GEM);
        if (file.isFile()){
            sha1 = ChecksumUtils.calculateSHA1(file);
        } else {
            file = installMissingGem(name,version, file);
            if (file != null) {
                sha1 = ChecksumUtils.calculateSHA1(file);
            }
        }
        return sha1;
    }

    private File installMissingGem(String name, String version, File file){
        if (installMissingGems) {
            logger.info("installing gem file for {}-{}", name, version);
            if (version.toLowerCase().contains(MINGW)) {
                version = version.substring(0, version.indexOf(Constants.DASH));
            }
            List<String> lines = installGem(name, version);
            if (file.isFile()) {
                return file;
            }
            if (lines != null) {
                List<String> errors = lines.stream().filter(line -> line.startsWith(ERROR)).collect(Collectors.toList());
                if (errors.size() > 0) {
                    return null;
                }
                /* there are some cases where a gem is installed successfully, but with a slightly different name, e.g.
                    'pg -v 0.21.0' becomes 'pg-0.21.0-x64-mingw32'
                   for those cases, this piece of code extracts the updated version and return the downloaded file
                 */
                try {
                    String installed = lines.stream().filter(line -> line.startsWith("Successfully installed") && line.contains(name)).findFirst().orElse(Constants.EMPTY_STRING);
                    String gem = installed.split(Constants.WHITESPACE)[2];
                    File newFile = new File(file.getParent() + fileSeparator + gem + Constants.DOT + GEM);
                    if (newFile.isFile()) {
                        return newFile;
                    }
                } catch (IndexOutOfBoundsException e){
                    logger.warn("failed installing gem file for {}-{}", name, version);
                    logger.debug("stacktrace {}", e.getStackTrace());
                }
            }
        }
        return null;
    }

    private List<String> installGem(String name, String version) {
        String param = Constants.INSTALL.concat(Constants.WHITESPACE + name + Constants.WHITESPACE + V + Constants.WHITESPACE + version);
        String[] commandParams = cli.getCommandParams(GEM, param);
        return cli.runCmd(rootDirectory, commandParams);
    }

    // there are cases where a dependency appears in the Gemfile.lock only as a child.
    // in such cases, look for the relevant gem file in the cache with the highest version
    private String findGemVersion(String gemName, String pathToGems){
        String version = null;
        File maxVersionFile = findMaxVersionFile(gemName, pathToGems);
        if (maxVersionFile != null) {
            String fileName = maxVersionFile.getName();
            version = getVersionFromFileName(fileName, gemName);
        }
        return version;
    }

    private String getVersionFromFileName(String fileName, String gemName) {
        return fileName.substring(gemName.length()+1,fileName.lastIndexOf(Constants.DOT));
    }

    private File findMaxVersionFile(String gemName, String pathToGems) {
        File gemsFolder = new File(pathToGems);
        File[] files = gemsFolder.listFiles(new GemFileNameFilter(gemName));
        if (files.length > 0) {
            Arrays.sort(files, Collections.reverseOrder());
            return files[0];
        }
        return null;
    }
}

class GemFileNameFilter implements FilenameFilter{

    private String fileName;

    public GemFileNameFilter(String name){
        fileName = name;
    }
    @Override
    public boolean accept(File dir, String name) {
        return name.toLowerCase().startsWith(fileName) && name.endsWith(Constants.DOT + GEM);
    }
}