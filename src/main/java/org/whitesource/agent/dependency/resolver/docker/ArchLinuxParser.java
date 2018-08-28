package org.whitesource.agent.dependency.resolver.docker;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.*;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;


/**
 * @author chen.luigi
 */
public class ArchLinuxParser extends AbstractParser {

    /* --- Static members --- */
    private final Logger logger = LoggerFactory.getLogger(ArchLinuxParser.class);

    private static final String PACKAGE = "%NAME%";
    private static final String VERSION = "%VERSION%";
    private static final String ARCHITECTURE = "%ARCH%";
    private static final String DESC = "desc";
    private static final String ARCH_LINUX_PACKAGE_PATTERN = "{0}-{1}-{2}.pkg.tar.xz";

    /* --- Overridden methods --- */

    @Override
    public Collection<DependencyInfo> parse(File dir) {
        BufferedReader br = null;
        FileReader fr = null;
        Collection<DependencyInfo> dependencyInfos = new LinkedList<>();
        if (dir.isDirectory()) {
            Collection<File> files = new LinkedList<>();
            getDescFiles(dir, files);
            if (!files.isEmpty()) {
                for (File file : files) {
                    try {
                        DependencyInfo dependencyInfo = null;
                        Package packageInfo = new Package();
                        fr = new FileReader(file);
                        br = new BufferedReader(fr);
                        String line = null;
                        // Create Arch Linux package - package-version-architecture.pkg.tar.xz
                        while ((line = br.readLine()) != null) {
                            switch (line) {
                                case PACKAGE:
                                    packageInfo.setPackageName(br.readLine());
                                    break;
                                case VERSION:
                                    packageInfo.setVersion(br.readLine());
                                    break;
                                case ARCHITECTURE:
                                    packageInfo.setArchitecture(br.readLine());
                                    break;
                                default:
                                    break;
                            }
                        }
                        dependencyInfos.add(createDependencyInfo(packageInfo));
                    } catch (FileNotFoundException e) {
                        logger.error("Error getting package data", e.getMessage());
                    } catch (IOException e) {
                        logger.error("Error getting package data", e.getMessage());
                    } finally {
                        closeStream(br, fr);
                    }
                }
            }
        }
        return dependencyInfos;
    }

    /**
     * @param files                      - list of files to look for
     * @param pathToPackageManagerFolder the relevant path for the folder with all the installed packages
     * @return Folder file with all the information about the installed packages
     */
    @Override
    public File findFile(String[] files, String pathToPackageManagerFolder) {
        int max = 0;
        File archLinuxPackageManagerFile = null;
        for (String filepath : files) {
            if (filepath.contains(pathToPackageManagerFolder) && filepath.endsWith(DESC)) {
                int descStartIndex = filepath.lastIndexOf(pathToPackageManagerFolder);
                if (descStartIndex > 0) {
                    String descPath = filepath.substring(0, descStartIndex + pathToPackageManagerFolder.length());
                    File file = new File(descPath);
                    if (file.listFiles() != null) {
                        if (max < file.listFiles().length) {
                            max = file.listFiles().length;
                            archLinuxPackageManagerFile = file;
                        }
                    }
                }
            }
        }
        if (archLinuxPackageManagerFile != null) {
            return archLinuxPackageManagerFile;
        }
        return null;
    }

    /* --- Private methods --- */

    // Get the desc files from specific folder (every dec contains dependency info data)
    private void getDescFiles(File dir, Collection<File> files) {
        if (dir.isDirectory()) {
            for (File file : dir.listFiles()) {
                if (file.isDirectory()) {
                    getDescFiles(file, files);
                } else if (file.getName().equals(DESC)) {
                    files.add(file);
                }
            }
        }
    }

    private DependencyInfo createDependencyInfo(Package packageInfo) {
        DependencyInfo dependencyInfo = null;
        if (StringUtils.isNotBlank(packageInfo.getPackageName()) && StringUtils.isNotBlank(packageInfo.getVersion())
                && StringUtils.isNotBlank(packageInfo.getArchitecture())) {
            dependencyInfo = new DependencyInfo(
                    null, MessageFormat.format(ARCH_LINUX_PACKAGE_PATTERN, packageInfo.getPackageName(),
                    packageInfo.getVersion(), packageInfo.getArchitecture()), packageInfo.getVersion());
        }
        if (dependencyInfo != null) {
            return dependencyInfo;
        } else {
            return null;

        }
    }

}
