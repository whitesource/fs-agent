package org.whitesource.agent.dependency.resolver.docker;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.*;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;

/**
 * @author chen.luigi
 */
public class DebianParser extends AbstractParser {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(DebianParser.class);
    private static final String PACKAGE = "Package";
    private static final String VERSION = "Version";
    private static final String ARCHITECTURE = "Architecture";
    private static final String DEBIAN_PACKAGE_PATTERN = "{0}_{1}_{2}.deb";

    /* --- Overridden methods --- */

    /**
     * Parse the available file to create DependencyInfo
     * Field to parse - Package, Version, Architecture, Filename, SystemPath, MD5sum
     */
    @Override
    public Collection<DependencyInfo> parse(File file) {
        BufferedReader br = null;
        FileReader fr = null;
        Collection<DependencyInfo> dependencyInfos = new LinkedList<>();
        try {
            fr = new FileReader(file.getAbsoluteFile());
            br = new BufferedReader(fr);
            String line = null;
            Package packageInfo = new Package();
            // Create Debian package - package-version-architecture.deb
            while ((line = br.readLine()) != null) {
                // Some fields (like 'Description') can be extended over several lines (which don't have ":")
                // We don't need to parse these lines
                if (!line.isEmpty() && line.contains(Constants.COLON)) {
                    String[] lineSplit = line.split(Constants.COLON);
                    // To prevent checking lines that look like - "Text:"
                    if (lineSplit.length > 1) {
                        String dependencyParameter = lineSplit[1].trim();
                        switch (lineSplit[0]) {
                            case PACKAGE:
                                packageInfo.setPackageName(dependencyParameter);
                                break;
                            case VERSION:
                                if (packageInfo.getPackageName() != null) {
                                    packageInfo.setVersion(dependencyParameter);
                                }
                                break;
                            case ARCHITECTURE:
                                if (packageInfo.getPackageName() != null) {
                                    packageInfo.setArchitecture(dependencyParameter);
                                }
                                break;
                            default:
                                break;
                        }
                    }
                } else {
                    if (packageInfo.getPackageName() != null) {
                        DependencyInfo dependencyInfo = createDependencyInfo(packageInfo);
                        packageInfo = new Package();
                        dependencyInfos.add(dependencyInfo);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            logger.error("Error getting package data {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error getting package data {}", e.getMessage());
        } finally {
            closeStream(br, fr);
        }
        return dependencyInfos;
    }

    @Override
    public File findFile(String[] files, String filename) {
        for (String filepath : files) {
            if (filepath.endsWith(filename)) {
                return new File(filepath);
            }
        }
        return null;
    }

    /* --- Private methods --- */

    private DependencyInfo createDependencyInfo(Package packageInfo) {
        DependencyInfo dependencyInfo = null;
        if (StringUtils.isNotBlank(packageInfo.getPackageName()) && StringUtils.isNotBlank(packageInfo.getVersion()) &&
                StringUtils.isNotBlank(packageInfo.getArchitecture())) {
            if (packageInfo.getVersion().contains(Constants.PLUS)) {
                dependencyInfo = new DependencyInfo(
                        null, MessageFormat.format(DEBIAN_PACKAGE_PATTERN, packageInfo.getPackageName(),
                        packageInfo.getVersion().substring(0, packageInfo.getVersion().lastIndexOf(Constants.PLUS)), packageInfo.getArchitecture()), packageInfo.getVersion());
            } else {
                dependencyInfo = new DependencyInfo(
                        null, MessageFormat.format(DEBIAN_PACKAGE_PATTERN, packageInfo.getPackageName(),
                        packageInfo.getVersion(), packageInfo.getArchitecture()), packageInfo.getVersion());
            }
        }
        if (dependencyInfo != null) {
            return dependencyInfo;
        } else {
            return null;
        }
    }

}
