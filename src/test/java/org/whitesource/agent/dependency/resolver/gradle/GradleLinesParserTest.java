package org.whitesource.agent.dependency.resolver.gradle;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GradleLinesParserTest {

    private GradleLinesParser gradleLinesParser;

    @Before
    public void setup() {
        gradleLinesParser = new GradleLinesParser(false, new GradleCli(Constants.GRADLE_WRAPPER), Constants.EMPTY_STRING);
    }

    @Ignore
    @Test
    public void parseLines() throws IOException {
        String[] params = new String[]{Constants.CMD, "/c", "gradle", Constants.DEPENDENCIES};
        String folderPath = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() +
                TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\gradle\\sample\\");
        CommandLineProcess commandLineProcess = new CommandLineProcess(folderPath, params);
        List<String> lines = commandLineProcess.executeProcess();
        gradleLinesParser.parseLines(lines, Constants.EMPTY_STRING, Constants.EMPTY_STRING ,new String[0], folderPath + "\\build.gradle");
    }

    @Ignore
    @Test
    public void parseLineFromFile() {
        File file = TestHelper.getFileFromResources("resolver/gradle/lines.txt");
        List<String> lines = readFileAsList(file.getAbsolutePath());
        List<DependencyInfo> dependencyInfoList = gradleLinesParser.parseLines(lines, Constants.EMPTY_STRING, Constants.EMPTY_STRING,new String[0], null);
        Assert.assertTrue(dependencyInfoList.size() > 0);
    }

    private List<String> readFileAsList(String fileName) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                // process the line.
                lines.add(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    @Ignore
    @Test
    public void parseLinesFromString() {
        List<String> lines = new ArrayList<>();
        lines.add("+--- com.google.guava:guava:23.0");
        lines.add("|    +--- com.google.code.findbugs:jsr305:1.3.9");
        lines.add("|    +--- com.google.errorprone:error_prone_annotations:2.0.18");
        lines.add("|    +--- com.google.j2objc:j2objc-annotations:1.1");
        lines.add("|    \\--- org.codehaus.mojo:animal-sniffer-annotations:1.14");
        lines.add("+--- org.webjars.npm:isurl:1.0.0");
        lines.add("|    +--- org.webjars.npm:has-to-string-tag-x:[1.2.0,2) -> 1.4.1");
        lines.add("|    |    \\--- org.webjars.npm:has-symbol-support-x:[1.4.1,2) -> 1.4.1");
        lines.add("|    \\--- org.webjars.npm:is-object:[1.0.1,2) -> 1.0.1");
        lines.add("\\--- junit:junit:4.12");
        lines.add("     \\--- org.hamcrest:hamcrest-core:1.3");
        gradleLinesParser.parseLines(lines, Constants.EMPTY_STRING, Constants.EMPTY_STRING, new String[0],null);
    }

    @Ignore
    @Test
    public void parseLinesFromString2() {
        List<String> lines = new ArrayList<>();
        lines.add("+--- org.slf4j:jcl-over-slf4j:1.7.12");
        lines.add("|    \\--- org.slf4j:slf4j-api:1.7.12");
        lines.add("+--- ch.qos.logback:logback-classic:1.1.3");
        lines.add("|    +--- ch.qos.logback:logback-core:1.1.3");
        lines.add("|    \\--- org.slf4j:slf4j-api:1.7.7 -> 1.7.12");
        lines.add("|         \\--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("+--- org.springframework:spring-webmvc:4.1.6.RELEASE");
        lines.add("|    +--- org.springframework:spring-beans:4.1.6.RELEASE");
        lines.add("|    |    \\--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("|    +--- org.springframework:spring-context:4.1.6.RELEASE");
        lines.add("|    |    +--- org.springframework:spring-aop:4.1.6.RELEASE");
        lines.add("|    |    |    +--- aopalliance:aopalliance:1.0");
        lines.add("|    |    |    +--- org.springframework:spring-beans:4.1.6.RELEASE");
        lines.add("|    |    |    \\--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("|    |    +--- org.springframework:spring-beans:4.1.6.RELEASE");
        lines.add("|    |    +--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("|    |    \\--- org.springframework:spring-expression:4.1.6.RELEASE");
        lines.add("|    |         \\--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("|    +--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("|    +--- org.springframework:spring-expression:4.1.6.RELEASE");
        lines.add("|    \\--- org.springframework:spring-web:4.1.6.RELEASE");
        lines.add("|         +--- org.springframework:spring-aop:4.1.6.RELEASE");
        lines.add("|         +--- org.springframework:spring-beans:4.1.6.RELEASE");
        lines.add("|         +--- org.springframework:spring-context:4.1.6.RELEASE");
        lines.add("|         \\--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("+--- org.hsqldb:hsqldb:2.3.2");
        lines.add("\\--- javax.servlet:servlet-api:2.5");
        List<DependencyInfo> dependencyInfos = gradleLinesParser.parseLines(lines, Constants.EMPTY_STRING, Constants.EMPTY_STRING, new String[0],null);

        Assert.assertTrue(dependencyInfos.get(0).getVersion().equals("1.7.12"));
        Assert.assertTrue(dependencyInfos.get(4).getVersion().equals("2.5"));
    }

    @Ignore
    @Test
    public void parseLinesFromString3() {
        List<String> lines = new ArrayList<>();
        lines.add("archives - Configuration for archive artifacts.");
        lines.add("No dependencies");
        lines.add("compile - Compile classpath for source set 'main'.");
        lines.add("+--- org.slf4j:jcl-over-slf4j:1.7.12");
        lines.add("|    \\--- org.slf4j:slf4j-api:1.7.12");
        lines.add("+--- ch.qos.logback:logback-classic:1.1.3");
        lines.add("|    +--- ch.qos.logback:logback-core:1.1.3");
        lines.add("|    \\--- org.slf4j:slf4j-api:1.7.7 -> 1.7.12");
        lines.add("+--- org.springframework:spring-webmvc:4.1.6.RELEASE");
        lines.add("|    +--- org.springframework:spring-beans:4.1.6.RELEASE");
        lines.add("|    |    \\--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("|    +--- org.springframework:spring-context:4.1.6.RELEASE");
        lines.add("|    |    +--- org.springframework:spring-aop:4.1.6.RELEASE");
        lines.add("|    |    |    +--- aopalliance:aopalliance:1.0");
        lines.add("|    |    |    +--- org.springframework:spring-beans:4.1.6.RELEASE (*)");
        lines.add("|    |    |    \\--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("|    |    +--- org.springframework:spring-beans:4.1.6.RELEASE");
        lines.add("|    |    +--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("|    |    \\--- org.springframework:spring-expression:4.1.6.RELEASE");
        lines.add("|    |         \\--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("|    +--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("|    +--- org.springframework:spring-expression:4.1.6.RELEASE");
        lines.add("|    \\--- org.springframework:spring-web:4.1.6.RELEASE");
        lines.add("|         +--- org.springframework:spring-aop:4.1.6.RELEASE");
        lines.add("|         +--- org.springframework:spring-beans:4.1.6.RELEASE");
        lines.add("|         +--- org.springframework:spring-context:4.1.6.RELEASE");
        lines.add("|         \\--- org.springframework:spring-core:4.1.6.RELEASE");
        lines.add("+--- org.hsqldb:hsqldb:2.3.2");
        lines.add("\\--- javax.servlet:servlet-api:2.5");
        lines.add("default - Configuration for default artifacts.");
        lines.add("+--- org.slf4j:jcl-over-slf4j:1.7.12 ");
        lines.add("|    \\--- org.slf4j:slf4j-api:1.7.12");
        lines.add("+--- ch.qos.logback:logback-classic:1.1.3");
        lines.add("|    +--- ch.qos.logback:logback-core:1.1.3");
        lines.add("|    \\--- org.slf4j:slf4j-api:1.7.7 -> 1.7.12");
        lines.add("providedCompile - Additional compile classpath for libraries that should not be part of the WAR arc");
        lines.add("\\--- javax.servlet:servlet-api:2.5");
        lines.add("providedRuntime - Additional runtime classpath for libraries that should not be part of the WAR arc");
        lines.add("\\--- javax.servlet:servlet-api:2.5");
        lines.add("runtime - Runtime classpath for source set 'main'.");
        lines.add("+--- org.slf4j:jcl-over-slf4j:1.7.12");
        lines.add("|    \\--- org.slf4j:slf4j-api:1.7.12");
        lines.add("+--- ch.qos.logback:logback-classic:1.1.3");
        lines.add("|    +--- ch.qos.logback:logback-core:1.1.3");
        lines.add("|    \\--- org.slf4j:slf4j-api:1.7.7 -> 1.7.12");
        lines.add("testCompile - Compile classpath for source set 'test'.");
        lines.add("+--- org.slf4j:jcl-over-slf4j:1.7.12");
        lines.add("|    \\--- org.slf4j:slf4j-api:1.7.12");
        lines.add("+--- ch.qos.logback:logback-classic:1.1.3");
        lines.add("|    +--- ch.qos.logback:logback-core:1.1.3");
        lines.add("|    \\--- org.slf4j:slf4j-api:1.7.7 -> 1.7.12");

        List<DependencyInfo> dependencyInfos = gradleLinesParser.parseLines(lines, Constants.EMPTY_STRING, Constants.EMPTY_STRING, new String[0], null);
        Assert.assertTrue(dependencyInfos.get(2).getChildren().iterator().next().getVersion().equals("4.1.6.RELEASE"));
    }

    @Ignore
    @Test
    public void parseLinesFromString4() {
        List<String> lines = new ArrayList<>();
        lines.add("Found go 1.10.3 in C:\\Go\\bin\\go.exe, use it.");
        lines.add(":prepare");
        lines.add("Found global GOPATH: C:\\Users\\ErezHuberman\\Documents\\Go.");
        lines.add(":resolveBuildDependencies");
        lines.add("Resolving github.com/astaxie/beego: commit='LATEST_COMMIT', urls=[https://github.com/astaxie/beego.git, git@github.com:astaxie/beego.git]");
        lines.add("Resolving github.com/eRez-ws/go-stringUtil: commit='LATEST_COMMIT', urls=[https://github.com/eRez-ws/go-stringUtil.git, git@github.com:eRez-ws/go-stringUtil.git]");
        lines.add("Resolving github.com/garyburd/redigo: commit='LATEST_COMMIT', urls=[https://github.com/garyburd/redigo.git, git@github.com:garyburd/redigo.git]");
        lines.add("Resolving github.com/google/go-querystring: commit='LATEST_COMMIT', urls=[https://github.com/google/go-querystring.git, git@github.com:google/go-querystring.git]");
        lines.add(":resolveTestDependencies");
        lines.add(":dependencies");
        lines.add("build:");
        lines.add("hello");
        lines.add("|-- github.com/astaxie/beego:053a075");
        lines.add("|   |-- golang.org/x/crypto:github.com/astaxie/beego#053a075344c118a5cc41981b29ef612bb53d20ca/vendor/golang.org/x/crypto");
        lines.add("|   |-- golang.org/x/net:github.com/astaxie/beego#053a075344c118a5cc41981b29ef612bb53d20ca/vendor/golang.org/x/net");
        lines.add("|   |-- google.golang.org/appengine:github.com/astaxie/beego#053a075344c118a5cc41981b29ef612bb53d20ca/vendor/google.golang.org/appengine");
        lines.add("|   \\-- gopkg.in/yaml.v2:github.com/astaxie/beego#053a075344c118a5cc41981b29ef612bb53d20ca/vendor/gopkg.in/yaml.v2");
        lines.add("|-- github.com/eRez-ws/go-stringUtil:99cfd8b");
        lines.add("|-- github.com/garyburd/redigo:569eae5");
        lines.add("\\-- github.com/google/go-querystring:53e6ce1");
        lines.add("");
        lines.add("test:");
        lines.add("hello");
        lines.add("");
        lines.add("BUILD SUCCESSFUL in 15s");
        lines.add("4 actionable tasks: 4 executed");
        List<DependencyInfo> dependencyInfos = gradleLinesParser.parseLines(lines, Constants.EMPTY_STRING, Constants.EMPTY_STRING, new String[0], null);
        //Assert.assertTrue(dependencyInfos.get(2).getChildren().iterator().next().getVersion().equals("4.1.6.RELEASE"));
    }

    @Ignore
    @Test
    public void parseLinesFromString5() {
        List<String> lines = new ArrayList<>();
        lines.add("|-- github.com/astaxie/beego:053a075");
        lines.add("|-- github.com/eRez-ws/go-stringUtil:99cfd8b");
        lines.add("|-- github.com/eladSalti/go_test-t:99cfd8b");
        lines.add("|-- github.com/garyburd/redigo:569eae5");
        lines.add("|-- github.com/google/go-querystring:44c6ddd");
        lines.add("|-- golang.org/x/crypto:github.com/astaxie/beego#053a075344c118a5cc41981b29ef612bb53d20ca/vendor/golang.org/x/crypto");
        lines.add("|-- golang.org/x/net:github.com/astaxie/beego#053a075344c118a5cc41981b29ef612bb53d20ca/vendor/golang.org/x/net");
        lines.add("|-- google.golang.org/appengine:github.com/astaxie/beego#053a075344c118a5cc41981b29ef612bb53d20ca/vendor/google.golang.org/appengine");
        lines.add("\\-- gopkg.in/yaml.v2:github.com/astaxie/beego#053a075344c118a5cc41981b29ef612bb53d20ca/vendor/gopkg.in/yaml.v2");
        gradleLinesParser.parseLines(lines, Constants.EMPTY_STRING, Constants.EMPTY_STRING, new String[0],null);
    }

}