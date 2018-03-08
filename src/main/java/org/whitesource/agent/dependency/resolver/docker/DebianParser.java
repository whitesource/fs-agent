package org.whitesource.agent.dependency.resolver.docker;

import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.api.model.ChecksumType;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.*;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * @author chen.luigi
 */
public class DebianParser extends AbstractParser {

    /* --- Static members --- */

    public static final String PACKAGE = "Package";
    public static final String VERSION = "Version";
    public static final String ARCHITECTURE = "Architecture";
    public static final String SYSTEMPATH = "Filename";
    public static final String MD5 = "MD5sum";
    public static final String COLON = ":";

    private static final String DEBIAN_PACKAGE_PATTERN = "{0}_{1}_{2}.deb";
    public static final String SLASH_SEPERATOR = "/";


    /* --- Overridden methods --- */

    /**
     * Parse the available file to create DependencyInfo
     * Field to parse - Package,Version,Architecture,Filename,SystemPath,MD5sum
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
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty()) {
                    String[] lineSplit = line.split(COLON);
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
                        case SYSTEMPATH:
                            packageInfo.setSystemPath(dependencyParameter);
                            int lastSlashPos = dependencyParameter.lastIndexOf(SLASH_SEPERATOR);
                            String filename = dependencyParameter.substring(lastSlashPos + 1, dependencyParameter.length());
                            packageInfo.setFilename(filename);
                            break;
                        case MD5:
                            packageInfo.setMd5(dependencyParameter);
                            break;
                        default:
                    }
                } else {
                    DependencyInfo dependencyInfo = null;
                    if (StringUtils.isNotBlank(packageInfo.getPackageName()) && StringUtils.isNotBlank(packageInfo.getVersion()) && StringUtils.isNotBlank(packageInfo.getArchitecture())) {
                        dependencyInfo = new DependencyInfo(
                                null, MessageFormat.format(DEBIAN_PACKAGE_PATTERN, packageInfo.getPackageName(), packageInfo.getVersion(), packageInfo.getArchitecture()), packageInfo.getVersion());
                    }
                    if (StringUtils.isNotBlank(packageInfo.getFilename())) {
                        dependencyInfo.setFilename(packageInfo.getFilename());
                    }
                    if (StringUtils.isNotBlank(packageInfo.getSystemPath())) {
                        dependencyInfo.setSystemPath(packageInfo.getSystemPath());
                    }
                    if (StringUtils.isNotBlank(packageInfo.getMd5())) {
                        HashMap<ChecksumType, String> checksums = new HashMap<>();
                        checksums.put(ChecksumType.MD5, packageInfo.getMd5());
                        dependencyInfo.setChecksums(checksums);
                    }
                    packageInfo = new Package();
                    dependencyInfos.add(dependencyInfo);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null)
                    br.close();
                if (fr != null)
                    fr.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return dependencyInfos;
    }
}
