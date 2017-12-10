package org.whitesource.agent.dependency.resolver.maven;

import fr.dutra.tools.maven.deptree.core.Node;
import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MavenLinesParserTest {

    @Test
    public void shouldParseLinesWithWarnings(){
        File file = TestHelper.getFileFromResources("resolver\\maven\\lines.txt");
        List<String> lines = readFileAsList(file.getAbsolutePath());
        MavenLinesParser mavenLinesParser = new MavenLinesParser();
        List<Node> nodes = mavenLinesParser.parseLines(lines);
        Assert.assertEquals(nodes.size() , 1);
        Assert.assertEquals(nodes.get(0).getArtifactId(),"junrar");
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

}
