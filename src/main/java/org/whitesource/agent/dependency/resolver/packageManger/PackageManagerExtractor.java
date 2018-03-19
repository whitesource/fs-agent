package org.whitesource.agent.dependency.resolver.packageManger;

import com.aragost.javahg.log.Logger;
import com.aragost.javahg.log.LoggerFactory;
import com.google.common.io.ByteStreams;
import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.*;

/**
 * Created by anna.rozin
 */
public class PackageManagerExtractor {

    /* --- Statics Members --- */

    private final Logger logger = LoggerFactory.getLogger(PackageManagerExtractor.class);

    private static final int DEBIAN_PACKAGE_NAME_INDEX = 0;
    private static final int DEBIAN_PACKAGE_VERSION_INDEX = 1;
    private static final int DEBIAN_PACKAGE_ARCH_INDEX = 2;
    private static final String DEBIAN_INSTALLED_PACKAGE_PREFIX = "ii";
    private static final String DEBIAN_PACKAGE_PATTERN = "{0}_{1}_{2}.deb";
    private static final String RPM_PACKAGE_PATTERN = "{0}.rpm";
    private static final String ALPINE_PACKAGE_PATTERN = "{0}.apk";
    private static final String ALPINE_PACKAGE_SPLIT_PATTERN = " - ";
    private static final String ARCH_LINUX_PACKAGE_PATTERN = "{0}-{1}-{2}.pkg.tar.xz";
    private static final List<String> SYSTEM_ARCHITECTURES = Arrays.asList("x86_64", "i686", "any");
    private static final String ARCH_LINUX_PACKAGE_SPLIT_PATTERN = " ";
    private static final String WHITE_SPACE = " ";
    private static final String NEW_LINE = "\\r?\\n";
    private static final String COLON = ":";
    private static final String NON_ASCII_CHARS = "[^\\x20-\\x7e]";
    private static final String EMPTY_STRING = "";
    private static final String ARCH_LINUX_ARCHITECTURE_COMMAND = "uname -m";
    /* --- Constructors --- */

    public PackageManagerExtractor() {
    }

    /* --- Public methods --- */

    public Collection<AgentProjectInfo> createProjects() {
        List<DependencyInfo> packages = new LinkedList<>();
        Collection<AgentProjectInfo> projectInfos = new LinkedList<>();
        InputStream inputStream = null;
        byte[] bytes = null;
        Process process = null;

        //For each flavor command check installed packages
        for (LinuxPkgManagerCommand linuxPkgManagerCommand : LinuxPkgManagerCommand.values()) {
            try {
                logger.info("Trying to run command {}", linuxPkgManagerCommand.getCommand());
                process = Runtime.getRuntime().exec(linuxPkgManagerCommand.getCommand());
                inputStream = process.getInputStream();
                if (inputStream.read() == -1) {
                    logger.error("Unable to execute - {} , flavor does not support this command ", linuxPkgManagerCommand.getCommand());
                } else {
                    bytes = ByteStreams.toByteArray(inputStream);
                    //Get the installed packages (name,version,architecture) from inputStream
                    logger.info("Succeed to run the command - {} ", linuxPkgManagerCommand.getCommand());
                    switch (linuxPkgManagerCommand) {
                        case DEBIAN_COMMAND:
                            logger.debug("Getting Debian installed Packages");
                            createDebianProject(bytes, packages);
                            break;
                        case RPM_COMMAND:
                            logger.debug("Getting RPM installed Packages");
                            createRpmProject(bytes, packages);
                            break;
                        case ARCH_LINUX_COMMAND:
                            logger.debug("Getting Arch Linux installed Packages");
                            createArchLinuxProject(bytes, packages);
                            break;
                        case ALPINE_COMMAND:
                            logger.debug("Getting Alpine installed Packages");
                            createAlpineProject(bytes, packages);
                            break;
                        default:
                            break;
                    }
                }
                //Create new AgentProjectInfo object and add it into a list of AgentProjectInfo
                if (packages.size() > 0) {
                    logger.debug("Creating new AgentProjectInfo object");
                    AgentProjectInfo projectInfo = new AgentProjectInfo();
                    projectInfo.setDependencies(packages);
                    projectInfos.add(projectInfo);
                    packages = new LinkedList<>();
                }
            } catch (IOException e) {
                logger.warn("Command line error : {}", e.getMessage());
            }
        }
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            logger.error("InputStream exception : {}", e.getMessage());
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
        String[] lines = linesStr.split(NEW_LINE);
        for (String line : lines) {
            if (StringUtils.isNotBlank(line)) {
                packages.add(new DependencyInfo(null, MessageFormat.format(RPM_PACKAGE_PATTERN, line), null));
            }
        }
    }

    public void createArchLinuxProject(byte[] bytes, List<DependencyInfo> packages) {

        String linesStr = new String(bytes);
        String[] lines = linesStr.split(NEW_LINE);
        String arch = getSystemArchitecture();
        if (StringUtils.isNotBlank(arch)) {
            for (String line : lines) {
                line = line.replaceAll(NON_ASCII_CHARS, EMPTY_STRING);
                String[] split = line.split(ARCH_LINUX_PACKAGE_SPLIT_PATTERN);
                logger.info(split[0]);
                if (split.length == 2) {
                    packages.add(new DependencyInfo(null, MessageFormat.format(ARCH_LINUX_PACKAGE_PATTERN, split[0], split[1], arch), null));

                }
            }
        }
    }

    public void createAlpineProject(byte[] bytes, List<DependencyInfo> packages) {
        String linesStr = new String(bytes);
        String[] lines = linesStr.split(NEW_LINE);
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


    /* --- Private  methods --- */

    private String getSystemArchitecture() {
        String arch = "";
        String outputStr = null;
        BufferedReader bufferedReader = null;
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(ARCH_LINUX_ARCHITECTURE_COMMAND);
            process.waitFor();
            bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            outputStr = bufferedReader.readLine();
            if (StringUtils.isNotBlank(outputStr) && SYSTEM_ARCHITECTURES.contains(outputStr)) {
                arch = outputStr;
            }
            bufferedReader.close();
        } catch (IOException e) {
            logger.warn("Error processing arch linux command {}, error : {}", LinuxPkgManagerCommand.ARCH_LINUX_COMMAND, e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return arch;
    }

}