package org.whitesource.agent.dependency.resolver.sbt;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.dependency.resolver.BomFile;
import org.whitesource.agent.dependency.resolver.IBomParser;
import org.whitesource.agent.dependency.resolver.maven.MavenPomParser;

import java.io.File;

public class SbtBomParser implements IBomParser {
    private final Logger logger = LoggerFactory.getLogger(SbtBomParser.class);
    @Override
    public BomFile parseBomFile(String bomPath) {
        File bomFile = new File(bomPath);
        if (bomFile.isFile()){
            Serializer serializer = new Persister();
            try {
                IvyReport ivyReport = serializer.read(IvyReport.class, bomFile);
                String groupId = ivyReport.getInfo().getGroupId();
                String artifactId = ivyReport.getInfo().getArtifactId();
                String version = ivyReport.getInfo().getVersion();
                return new BomFile(groupId, artifactId, version, bomPath);
            } catch (Exception e) {
                logger.warn("Couldn't parse {}, {}", bomPath, e.getMessage());
                logger.debug("stacktrace {}", e.getStackTrace());
            }
        }
        return null;
    }
}
