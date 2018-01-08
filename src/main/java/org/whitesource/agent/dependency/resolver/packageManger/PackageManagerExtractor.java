package org.whitesource.agent.dependency.resolver.packageManger;

import com.aragost.javahg.log.Logger;
import com.aragost.javahg.log.LoggerFactory;
import com.google.common.io.ByteStreams;
import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by anna.rozin
 */
public class PackageManagerExtractor {

    /* --- Statics Members --- */

    private static final Logger logger = LoggerFactory.getLogger(PackageManagerExtractor.class);

    private static final String COLON = ":";
    private static final String NON_ASCII_CHARS = "[^\\x20-\\x7e]";
    private static final String EMPTY_STRING = "";
    private static final String DEBIAN_INSTALLED_PACKAGE_PREFIX = "ii";
    private static final int DEBIAN_PACKAGE_NAME_INDEX = 0;
    private static final int DEBIAN_PACKAGE_VERSION_INDEX = 1;
    private static final int DEBIAN_PACKAGE_ARCH_INDEX = 2;
    private static final String DEBIAN_PACKAGE_PATTERN = "{0}_{1}_{2}.deb";
    private static final String RPM_PACKAGE_PATTERN = "{0}.rpm";
    private static final String ALPINE_PACKAGE_PATTERN = "{0}.apk";
    private static final String ALPINE_PACKAGE_SPLIT_PATTERN = " - ";
    private static final String ARCH_LINUX_PACKAGE_PATTERN = "{0}-{1}-{2}.pkg.tar.xz";
    private static final String ARCH_LINUX_PACKAGE_SPLIT_PATTERN = " ";
    private static final String WHITE_SPACE = " ";
    private static final String NEW_LINE = "\\r?\\n";

    /* --- Members --- */

    private final boolean showProgressBar;
    private DependencyResolutionService dependencyResolutionService;

    /* --- Constructors --- */

    public PackageManagerExtractor(boolean showProgressBar, DependencyResolutionService dependencyResolutionService) {
        this.showProgressBar = showProgressBar;
        this.dependencyResolutionService = dependencyResolutionService;
    }

    /* --- Public methods --- */

    public Collection<AgentProjectInfo> createProjects() {
        List<DependencyInfo> packages = new LinkedList<>();
        Collection<AgentProjectInfo> projectInfos = new LinkedList<>();
        InputStream inputStream = null;
        byte[] bytes = null;
        Process p = null;

        //Foreach loop on every flavor command object
        for (LinuxPkgManagerCommand linuxPkgManagerCommand : LinuxPkgManagerCommand.values()) {
            try {
                logger.info("Trying to run command {}", linuxPkgManagerCommand.getCommand());
                p = Runtime.getRuntime().exec(linuxPkgManagerCommand.getCommand());
                inputStream = p.getInputStream();
                if (inputStream.read() == -1) {
                    logger.error("Unable to execute - {} , flavor does not support this command ", linuxPkgManagerCommand.getCommand());
                } else {
                    bytes = ByteStreams.toByteArray(inputStream);
                    //Get the installed packages (name,version,architecture) from the inputStream
                    logger.info("Succeed to run the command - {} ", linuxPkgManagerCommand.getCommand());
                    switch (linuxPkgManagerCommand) {
                        case DEBIAN_COMMAND:
                            logger.info("Trying to create Debian Project");
                            createDebianProject(bytes, packages);
                            break;
                        case RPM_COMMAND:
                            logger.info("Trying to create Rpm Project");
                            createRpmProject(bytes, packages);
                            break;
                        case ARCH_LINUX_COMMAND:
                            logger.info("Trying to create Arch Linux Project");
                            createArchLinuxProject(bytes, packages);
                            break;
                        case ALPINE_COMMAND:
                            logger.info("Trying to create Alpine Project");
                            createAlpineProject(bytes, packages);
                            break;
                        default:
                            break;
                    }
                }
                //Create new AgentProjectInfo object and add him into the list of AgentProjectInfo
                if (packages.size() > 0) {
                    logger.info("Creating new AgentProjectInfo");
                    AgentProjectInfo projectInfo = new AgentProjectInfo();
                    projectInfo.setDependencies(packages);
                    projectInfos.add(projectInfo);
                    packages = new LinkedList<>();
                }

            } catch (IOException e) {

            }
        }
        try {
            if (inputStream != null) {
                inputStream.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return projectInfos;
    }

    public void createDebianProject(byte[] bytes, List<DependencyInfo> packages) {
        String linesStr = new String(bytes);
        String[] lines = linesStr.split(NEW_LINE);
        for (String line : lines) {
            line = line.replaceAll(NON_ASCII_CHARS, EMPTY_STRING);
            if (line.startsWith(DEBIAN_INSTALLED_PACKAGE_PREFIX)) {
                List<String> args = new ArrayList<>();
                for (String s : line.split(WHITE_SPACE)) {
                    if (StringUtils.isNotBlank(s) && !s.equals(DEBIAN_INSTALLED_PACKAGE_PREFIX)) {
                        args.add(s);
                    }
                }
                if (args.size() >= 3) {
                    // names may contain the arch (i.e. package_name:amd64) - remove it
                    String name = args.get(DEBIAN_PACKAGE_NAME_INDEX);
                    if (name.contains(COLON)) {
                        name = name.substring(0, name.indexOf(COLON));
                    }
                    // versions may contain a
                    String version = args.get(DEBIAN_PACKAGE_VERSION_INDEX);
                    if (version.contains(COLON)) {
                        version = version.substring(version.indexOf(COLON) + 1);
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

    public void createArchLinuxProject(byte[] bytes, List<DependencyInfo> packages) {

        String linesStr = new String(bytes);
        String[] lines = linesStr.split("\\r?\\n");
        for (String line : lines) {
            line = line.replaceAll(NON_ASCII_CHARS, EMPTY_STRING);
            String[] split = line.split(ARCH_LINUX_PACKAGE_SPLIT_PATTERN);
            if (split.length == 2) {
                //packages.add(new DependencyInfo(null, MessageFormat.format(ARCH_LINUX_PACKAGE_PATTERN, split[0], split[1], arch), null));
            }
        }
    }

    public void createAlpineProject(byte[] bytes, List<DependencyInfo> packages) {
        String linesStr = new String(bytes);
        String[] lines = linesStr.split("\\r?\\n");
        for (String line : lines) {
            line = line.replaceAll(NON_ASCII_CHARS, EMPTY_STRING);
            if (line.contains(ALPINE_PACKAGE_SPLIT_PATTERN)) {
                String[] split = line.split(ALPINE_PACKAGE_SPLIT_PATTERN);
                if (split.length > 0) {
                    packages.add(new DependencyInfo(null, MessageFormat.format(ALPINE_PACKAGE_PATTERN, split[0]), null));
                }
            }
        }
    }
}