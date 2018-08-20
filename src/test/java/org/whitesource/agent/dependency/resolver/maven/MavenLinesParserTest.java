package org.whitesource.agent.dependency.resolver.maven;

import fr.dutra.tools.maven.deptree.core.Node;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class MavenLinesParserTest {

    private MavenLinesParser mavenLinesParser;
    @Before
    public void setUp(){
        mavenLinesParser = new MavenLinesParser();
    }
    @Ignore
    @Test
    public void shouldParseLinesWithWarnings(){
        File file = TestHelper.getFileFromResources("resolver/maven/lines.txt");
        List<String> lines = readFileAsList(file.getAbsolutePath());
        List<Node> nodes = mavenLinesParser.parseLines(lines);
        Assert.assertEquals(nodes.size() , 1);
        Assert.assertEquals(nodes.get(0).getArtifactId(),"junrar");
    }

    @Ignore
    @Test
    public void parseVerboseLines(){
        File file = TestHelper.getFileFromResources("resolver/maven/verboseLines.txt");
        List<String> lines = readFileAsList(file.getAbsolutePath());
        List<Node> nodes = mavenLinesParser.parseLines(lines);
        Assert.assertTrue(nodes.size() > 0);
    }

    @Ignore
    @Test
    public void parseLines(){
        File file = TestHelper.getFileFromResources("resolver/maven/lines2.txt");
        List<String> lines = readFileAsList(file.getAbsolutePath());
        List<Node> nodes = mavenLinesParser.parseLines(lines);
        Assert.assertTrue(nodes.size() > 0);
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

    @Test
    public void parseLinesWithDownloadLines(){
        File file = TestHelper.getFileFromResources("resolver/maven/lines3.txt");
        List<String> lines = readFileAsList(file.getAbsolutePath());
        List<Node> nodes = mavenLinesParser.parseLines(lines);
        Assert.assertEquals(nodes.size(), 1);
        Assert.assertEquals("single-module-project", nodes.get(0).getArtifactId());
        Assert.assertEquals("servlet-api", nodes.get(0).getChildNode(0).getArtifactId());
    }
}
