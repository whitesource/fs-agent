package org.whitesource.fs;

import com.aragost.javahg.log.Logger;
import com.aragost.javahg.log.LoggerFactory;
import com.google.common.io.ByteStreams;
import org.apache.commons.lang.StringUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.packageManger.LinuxPkgManagerCommand;
import org.whitesource.agent.dependency.resolver.packageManger.PackageManagerExtractor;

import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;


public class PackageManagerTest {

    private final Logger logger = LoggerFactory.getLogger(PackageManagerExtractor.class);

    private static final String NON_ASCII_CHARS = "[^\\x20-\\x7e]";


    private static final String DEBIAN_INSTALLED_PACKAGE_PREFIX = "ii";
    private static final int DEBIAN_PACKAGE_NAME_INDEX = 0;
    private static final int DEBIAN_PACKAGE_VERSION_INDEX = 1;
    private static final int DEBIAN_PACKAGE_ARCH_INDEX = 2;
    private static final String DEBIAN_PACKAGE_PATTERN = "{0}_{1}_{2}.deb";
    private static final String NEW_LINE = "\\r?\\n";
    private static final String RPM_PACKAGE_PATTERN = "{0}.rpm";

    @Ignore
    @Test()
    public void createProjects() throws IOException {

        List<DependencyInfo> packages = new LinkedList<>();
        Collection<AgentProjectInfo> projectInfos = new LinkedList<>();
        InputStream inputStream = null;
        byte[] bytes = null;
        Process p = null;
        //Foreach loop on every flavor command object
        for (LinuxPkgManagerCommand linuxPkgManagerCommand : LinuxPkgManagerCommand.values()) {
            try {
                p = Runtime.getRuntime().exec(linuxPkgManagerCommand.getCommand());
                inputStream = p.getInputStream();
                if (inputStream.read() == -1) {
                    //todo add logs
                    //return null;
                } else {
                    bytes = ByteStreams.toByteArray(inputStream);
                    switch (linuxPkgManagerCommand) {
                        case DEBIAN:
                            createDebianProject(bytes, packages);
                            break;
                        case RPM:
                            createRpmProject(bytes, packages);
                            break;
                        case ARCH_LINUX:
                            //createArchLinuxProject(bytes, inputStream, packages);
                            break;
                        case ALPINE:
                            //createAlpineProject(bytes, inputStream, packages);
                            break;
                        default:
                            break;
                    }
                    //Create new AgentProjectInfo object and add him into the list of AgentProjectInfo
                    AgentProjectInfo projectInfo = new AgentProjectInfo();
                    projectInfo.setDependencies(packages);
                    projectInfos.add(projectInfo);
                }

            } catch (IOException e) {

            }
        }
        //return projectInfos;
    }

    public void createDebianProject(byte[] bytes, List<DependencyInfo> packages) {

            String linesStr = new String(bytes);
            String[] lines = linesStr.split(NEW_LINE);
            for (String line : lines) {
                line = line.replaceAll(NON_ASCII_CHARS, Constants.EMPTY_STRING);
                if (line.startsWith(DEBIAN_INSTALLED_PACKAGE_PREFIX)) {
                    List<String> args = new ArrayList<>();
                    for (String s : line.split(Constants.WHITESPACE)) {
                        if (StringUtils.isNotBlank(s) && !s.equals(DEBIAN_INSTALLED_PACKAGE_PREFIX)) {
                            args.add(s);
                        }
                    }
                    if (args.size() >= 3) {
                        // names may contain the arch (i.e. package_name:amd64) - remove it
                        String name = args.get(DEBIAN_PACKAGE_NAME_INDEX);
                        if (name.contains(Constants.COLON)) {
                            name = name.substring(0, name.indexOf(Constants.COLON));
                        }
                        // versions may contain a
                        String version = args.get(DEBIAN_PACKAGE_VERSION_INDEX);
                        if (version.contains(Constants.COLON)) {
                            version = version.substring(version.indexOf(Constants.COLON) + 1);
                        }
                        String arch = args.get(DEBIAN_PACKAGE_ARCH_INDEX);
                        packages.add(new DependencyInfo(
                                null, MessageFormat.format(DEBIAN_PACKAGE_PATTERN, name, version, arch), version));
                    }
                }
            }

    }

    public void createRpmProject(byte[] bytes, List<DependencyInfo> packages) {
        String linesStr = new String(bytes);
        String[] lines = linesStr.split("\\r?\\n");
        for (String line : lines) {
            if (StringUtils.isNotBlank(line)) {
                packages.add(new DependencyInfo(null, MessageFormat.format(RPM_PACKAGE_PATTERN, line), null));
            }
        }
    }
}
