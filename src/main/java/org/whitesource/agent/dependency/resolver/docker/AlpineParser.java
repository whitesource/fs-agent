package org.whitesource.agent.dependency.resolver.docker;

import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.*;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;

/**
 * @author chen.luigi
 */
public class AlpineParser extends AbstractParser {


    /* --- Static members --- */

    private static final String PACKAGE = "P";
    private static final String VERSION = "V";
    private static final String ARCHITECTURE = "A";
    private static final String ALPINE_PACKAGE_PATTERN = "{0}.apk";

    /* --- Overridden methods --- */

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
            // Create Alpine package - package-version-architecture.apk
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty()) {
                    if (packageInfo.getPackageName() == null || packageInfo.getVersion() == null || packageInfo.getArchitecture() == null) {
                        String[] lineSplit = line.split(Constants.COLON);
                        String dependencyParameter = lineSplit[1].trim();
                        switch (lineSplit[0]) {
                            case PACKAGE:
                                packageInfo.setPackageName(dependencyParameter);
                                break;
                            case VERSION:
                                packageInfo.setVersion(dependencyParameter);
                                break;
                            case ARCHITECTURE:
                                packageInfo.setArchitecture(dependencyParameter);
                                break;
                            default:
                                break;
                        }
                    }
                } else {
                    DependencyInfo dependencyInfo = createDependencyInfo(packageInfo);
                    packageInfo = new Package();
                    dependencyInfos.add(dependencyInfo);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.debug("{}", e.getStackTrace());
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
            dependencyInfo = new DependencyInfo(
                    null, MessageFormat.format(ALPINE_PACKAGE_PATTERN, packageInfo.getPackageName() + Constants.DASH
                    + packageInfo.getVersion()), packageInfo.getVersion() + Constants.DASH +
                    packageInfo.getArchitecture());
        }
        if (dependencyInfo != null) {
            return dependencyInfo;
        } else {
            return null;

        }
    }
}
