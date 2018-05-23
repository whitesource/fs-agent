package org.whitesource.agent.dependency.resolver.ruby;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.stream.Stream;

public class RubyDependencyResolver extends AbstractDependencyResolver {

    private static final String GEM_FILE_LOCK = "Gemfile.lock";
    private static final String GEM_FILE_LOCK_ORIG = "Gemfile.lock.orig";
    private static final List<String> RUBY_SCRIPT_EXTENSION = Arrays.asList(".rb");
    private static final String BUNDLE         = "bundle";
    private static final String INSTALL        = "install";
    private static final String GEM            = "gem";
    private static final String ENVIRONMENT    = "environment gemdir";
    protected static final String REGEX = "\\S";
    protected static final String SPECS = "specs:";
    protected static final String CACHE = "cache";
    protected static final String SPACE = " ";
    protected static final String V = "-v";
    protected static final String ERROR = "ERROR";

    private final Logger logger = LoggerFactory.getLogger(RubyDependencyResolver.class);

    private RubyCli cli;
    private boolean runBundleInstall;
    private boolean overwriteGemFile;
    private boolean installMissingGems;
    String rootDirectory;

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
        excludes.add(PATTERN + RUBY_SCRIPT_EXTENSION);
        return excludes;
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return RUBY_SCRIPT_EXTENSION;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.RUBY;
    }

    @Override
    protected String getBomPattern() {
        return PATTERN + GEM_FILE_LOCK;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    private List<DependencyInfo> collectDependencies() {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        File gemFileLock = new File(rootDirectory + fileSeparator + GEM_FILE_LOCK);
        File gemFileLockOrig = new File(rootDirectory + fileSeparator + GEM_FILE_LOCK_ORIG);

        if (runBundleInstall) {
            runBundleInstall(gemFileLock, gemFileLockOrig);
        }
        if (gemFileLock.isFile()){
            parseGemFileLock(gemFileLock, dependencyInfos);
        } else {
            logger.error("Can't scan Gemlock.file - not found");
        }
        if (gemFileLockOrig.isFile()){
            removeTempFile(gemFileLock, gemFileLockOrig);
        }
        return dependencyInfos;
    }

    private boolean runBundleInstall(File gemFileLock, File origGemFileLock) {
        if (!overwriteGemFile && gemFileLock.isFile()){
            // when not overwriting the original Gemfile.lock (and it exists) - renaming it
            gemFileLock.renameTo(origGemFileLock);
        }
        boolean bundleInstallSuccess = cli.runCmd(rootDirectory, cli.getCommandParams(BUNDLE, INSTALL)) != null && gemFileLock.isFile();
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
            while ((currLine = bufferedReader.readLine()) != null) {
                if (insideGem && insideSpecs){
                    if (currLine.isEmpty()) {
                        break;
                    } else {
                        matcher = pattern.matcher(currLine);
                        if (matcher.find()){
                            int index = matcher.start();
                            if ((index > previousIndex && previousIndex > 0) || (indented && index == previousIndex)){
                                // ignoring indented lines
                                indented = true;
                                previousIndex = index;
                                continue;
                            } else {
                                String[] split = currLine.trim().split(SPACE);
                                String name = split[0];
                                String version = split[1].substring(1, split[1].length()-1);
                                try {
                                    String sha1 = getSha1(name, version, pathToGems);
                                    if (sha1 == null){
                                        logger.warn("Can't find gem file for {}-{}", name, version);
                                        continue;
                                    }
                                    dependencyInfo = new DependencyInfo(sha1);
                                    dependencyInfo.setArtifactId(name + "-" + version + "." + GEM);
                                    dependencyInfo.setGroupId(name);
                                    dependencyInfo.setVersion(version);
                                    dependencyInfo.setDependencyType(DependencyType.RUBY);
                                    dependencyInfo.setSystemPath(gemLockFile.getPath());
                                    dependencyInfo.setFilename(pathToGems + fileSeparator + name + "-" + version + "." + GEM);
                                    dependencyInfos.add(dependencyInfo);
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
        } catch (FileNotFoundException e){
            logger.warn("Could not Gemfile.lock {}", e.getMessage());
            logger.debug("stacktrace {}", e.getStackTrace());
        } catch (IOException e) {
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

    private String getSha1(String name, String version, String pathToGems) throws IOException {
        String sha1 = null;
        File file = new File(pathToGems + fileSeparator + name + "-" + version + "." + GEM);
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
            String param = INSTALL.concat(" " + name + " " + V + " " + version);
            String[] commandParams = cli.getCommandParams(GEM, param);
            List<String> lines = cli.runCmd(rootDirectory, commandParams);
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
                List<String> installed = lines.stream().filter(line -> line.startsWith("Successfully installed") && line.contains(name)).collect(Collectors.toList());
                String gem = installed.get(0).split(" ")[2];
                version = gem.substring(gem.indexOf("-") + 1);
                File newFile = new File(file.getParent() + fileSeparator + name + "-" + version + "." + GEM);
                if (newFile.isFile()){
                    return newFile;
                }
            }
        }
        return null;
    }
}