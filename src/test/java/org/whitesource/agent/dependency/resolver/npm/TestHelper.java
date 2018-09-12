package org.whitesource.agent.dependency.resolver.npm;

import org.junit.Assert;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.fs.CommandLineArgs;
import org.whitesource.fs.FSAConfigProperties;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * All tests runs on the this sample project : https://github.com/punkave/apostrophe
 *
 * @author eugen.horovitz
 */
public class TestHelper {

    /* --- Static Members --- */
    public static final File SUBFOLDER_WITH_OPTIONAL_DEPENDENCIES = TestHelper.getFileFromResources("resolver/npm/sample/package.json");

    public static String FOLDER_WITH_BOWER_PROJECTS = TestHelper.getFileFromResources("resolver/bower/angular.js/bower.json")
            .getParentFile().getParentFile().getAbsolutePath();
    public static String FOLDER_WITH_MVN_PROJECTS = TestHelper.getFileFromResources("resolver/maven/pom.xml")
            .getParentFile().getAbsolutePath();
    public static String FOLDER_WITH_NPN_PROJECTS = SUBFOLDER_WITH_OPTIONAL_DEPENDENCIES
            .getParentFile().getParentFile().getAbsolutePath();
    public static final String FOLDER_WITH_MIX_FOLDERS = new File(FOLDER_WITH_NPN_PROJECTS).getParent();

    /* --- Static Methods --- */

    public static File getTempFileWithReplace( String from,String to ) {
        // arrange
        File file = TestHelper.getFileFromResources(CommandLineArgs.CONFIG_FILE_NAME);
        final String JAVA_TEMP_DIR = System.getProperty("java.io.tmpdir");
        Path tmpPath = Paths.get(JAVA_TEMP_DIR, file.getName());
        try {
            Files.copy(file.toPath(), tmpPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        replaceSelected(tmpPath.toString(), from,to);
        return tmpPath.toFile();
    }

    public static File getTempFileWithProperty(String propertyName, Object val) {
        // arrange
        File file = TestHelper.getFileFromResources(CommandLineArgs.CONFIG_FILE_NAME);
        final String JAVA_TEMP_DIR = System.getProperty("java.io.tmpdir");

        Path tmpPath = Paths.get(JAVA_TEMP_DIR, file.getName());
        try {
            Files.copy(file.toPath(), tmpPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        insertProperty(tmpPath.toString(), propertyName, val.toString());
        return tmpPath.toFile();
    }

    private static void insertProperty(String filename, String propertyName, String propertyValue) {
        try (BufferedWriter output = new BufferedWriter(new FileWriter(filename, true))){
            output.append(System.lineSeparator());
            output.append(propertyName + Constants.EQUALS + propertyValue);
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void replaceSelected(String filename, String toFind, String replaceWith) {
        try {
            // input the file content to the StringBuffer "input"
            BufferedReader file = new BufferedReader(new FileReader(filename));
            String line;
            StringBuffer inputBuffer = new StringBuffer();

            while ((line = file.readLine()) != null) {
                inputBuffer.append(line);
                inputBuffer.append('\n');
            }
            String inputStr = inputBuffer.toString();

            file.close();

            System.out.println(inputStr); // check that it's inputted right

            inputStr = inputStr.replace(toFind, replaceWith);

            // check if the new input is right
            System.out.println("----------------------------------\n" + inputStr);

            // write the new String with the replaced line OVER the same file
            FileOutputStream fileOut = new FileOutputStream(filename);
            fileOut.write(inputStr.getBytes());
            fileOut.close();

        } catch (Exception e) {
            System.out.println("Problem reading file.");
        }
    }

    public static void testDependencyResult(boolean checkChildren, List<ResolutionResult> results) {
        results.forEach(resolutionResult -> {
            Assert.assertTrue(resolutionResult.getResolvedProjects().size() > 0);
            Assert.assertTrue(resolutionResult.getResolvedProjects().keySet().stream().findFirst().get().getDependencies().size() > 0);
            if (!checkChildren) {
                return;
            }
            List<DependencyInfo> dependencyInformation = resolutionResult
                    .getResolvedProjects().keySet().stream().findFirst().get().getDependencies().stream().filter(x -> x.getChildren().size() > 0).collect(Collectors.toList());
            Assert.assertTrue(dependencyInformation.size() > 0);
        });
    }

    public static String getFirstFolder(String dir) {
        File file = new File(dir);
        String files = Arrays.stream(file.listFiles()).filter(f -> f.isDirectory()).findFirst().get().getAbsolutePath();
        return files.toString();
    }

    public static Stream<String> getDependenciesWithNpm(String dir) {
        NpmLsJsonDependencyCollector collector = new NpmLsJsonDependencyCollector(false, 60, false, false);
        AgentProjectInfo projectInfo = collector.collectDependencies(dir).stream().findFirst().get();
        Collection<DependencyInfo> dependencies = projectInfo.getDependencies();
        return dependencies.stream().map(dep -> getShortNameByTgz(dep)).sorted();
    }

    public static String getShortNameByTgz(DependencyInfo dep) {
        String result = dep.getArtifactId()
                .replace(dep.getVersion(),  Constants.EMPTY_STRING)
                .replace(Constants.PIPE,  Constants.EMPTY_STRING)
                .replace(Constants.WHITESPACE, Constants.EMPTY_STRING)
                .replace("+",  Constants.EMPTY_STRING)
                .replace(Constants.DASH,  Constants.EMPTY_STRING)
                .replace(".tgz",  Constants.EMPTY_STRING)
                + "@" + dep.getVersion().replace(Constants.DASH, Constants.EMPTY_STRING);

        return result;
    }

    public static FSAConfigProperties getPropertiesFromFile() {
        FSAConfigProperties p = new FSAConfigProperties();
        try {
            File file = TestHelper.getFileFromResources(CommandLineArgs.CONFIG_FILE_NAME);
            InputStream input1 = new FileInputStream(file);
            p.load(input1);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //override if needed
        p.setProperty(ConfigPropertyKeys.FORCE_CHECK_ALL_DEPENDENCIES,  Constants.FALSE);
        p.setProperty(ConfigPropertyKeys.CHECK_POLICIES_PROPERTY_KEY,  Constants.FALSE);
        p.setProperty(ConfigPropertyKeys.PROJECT_VERSION_PROPERTY_KEY, "0");
        p.setProperty(ConfigPropertyKeys.FOLLOW_SYMBOLIC_LINKS, Constants.TRUE);
        p.setProperty(ConfigPropertyKeys.OFFLINE_PROPERTY_KEY, Constants.FALSE);
        p.setProperty(ConfigPropertyKeys.PRODUCT_NAME_PROPERTY_KEY, "NPM Test Pro hierarchy");
        p.setProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY, "**/*sources.jar **/*javadoc.jar");
        p.setProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY,  Constants.FALSE);
        p.setProperty(ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES, Constants.TRUE);
        p.setProperty(ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY, "testNpm");
        return p;
    }

    public static File getFileFromResources(String relativeFilePath) {
        ClassLoader classLoader = TestHelper.class.getClassLoader();
        String osFilePath = getOsRelativePath(relativeFilePath);
        File file = new File(classLoader.getResource(osFilePath).getFile());
        return file;
    }

    public static List<List> getExcludesFromDependencyResult(List<ResolutionResult> results, List<ResolutionResult> resultIgnoreSourceFiles,
                                                             DependencyType dependencyType){
        List excludesConfigFileList = new LinkedList();
        List excludesIgnoreSFList = new LinkedList();
        for (ResolutionResult result : results) {
            if (result.getDependencyType()!=null && result.getDependencyType().equals(dependencyType)){
                excludesConfigFileList.addAll(result.getExcludes());
            }
        }
        for (ResolutionResult resultIsf : resultIgnoreSourceFiles){
            if (resultIsf.getDependencyType()!=null && resultIsf.getDependencyType().equals(dependencyType)){
                excludesIgnoreSFList.addAll(resultIsf.getExcludes());
            }
        }
        List<List> bothExcludes= new ArrayList<>(2);
        bothExcludes.add(excludesConfigFileList);
        bothExcludes.add(excludesIgnoreSFList);
        return bothExcludes;
    }

    public static boolean checkResultOfScanFiles(String folderPath,List<List<String>> excludesNormal, List<List<String>> excludesIsf, String[] includes,
                                                 DependencyType dependencyType) {
        List<String> scannerBaseDirs = new LinkedList<>();
        scannerBaseDirs.add(folderPath);
        Set<String> pathsToScan = new HashSet<>();
        for (String path : scannerBaseDirs) {
            try {
                pathsToScan.add(new File(path).getCanonicalPath());
            } catch (IOException e) {
                pathsToScan.add(path);
            }
    }

        Collection<String> excludesNormal1 = excludesNormal.get(0);
        Collection<String> excludesIsf1= excludesIsf.get(0);
        String[] excludesNormalStr = excludesNormal1.toArray(new String[excludesNormal1.size()]);
        String[] excludesIsfStr = excludesIsf1.toArray(new String[excludesIsf1.size()]);
        Map<File, Collection<String>> fileMap = new FilesUtils().fillFilesMap(pathsToScan, includes, excludesNormalStr, false, false);
        Map<File, Collection<String>> fileMapIsf = new FilesUtils().fillFilesMap(pathsToScan, includes, excludesIsfStr, false, false);
        return fileMap.values().toString().equals(fileMapIsf.values().toString());
    }
    public static String getOsRelativePath(String relativeFilePath) {
        return relativeFilePath.replace("\\", String.valueOf(File.separatorChar).replace(Constants.FORWARD_SLASH, String.valueOf(File.separatorChar)));
    }
}