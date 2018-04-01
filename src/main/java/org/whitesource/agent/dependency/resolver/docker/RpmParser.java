package org.whitesource.agent.dependency.resolver.docker;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;

import static org.whitesource.agent.dependency.resolver.docker.DockerResolver.*;

/**
 * @author chen.luigi
 */
public class RpmParser extends AbstractParser {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(RpmParser.class);
    private static final String DASH = "-";
    private static final String DOT = ".";
    private static final String RPM_PACKAGE_PATTERN = "{0}.rpm";

    /* --- Overridden methods --- */

    @Override
    public Collection<DependencyInfo> parse(File file) {
        BufferedReader br = null;
        FileReader fr = null;
        Collection<DependencyInfo> dependencyInfos = new LinkedList<>();
        try {
            File[] files = file.listFiles();
            for (File directory : files) {
                File[] packageDirectories = directory.listFiles();
                for (File packageDirectory : packageDirectories) {
                    // parse the package name from package directory
                    // get the start index of package name
                    // package Directory Name for
                    int firstHyphenIndex = packageDirectory.getName().indexOf(DASH);
                    String packageInfoString = packageDirectory.getName().substring(firstHyphenIndex + 1, packageDirectory.getName().length());
                    // change rpm pattern name to application pattern
                    int lastIndexOfHyphen = packageInfoString.lastIndexOf(DASH);
                    packageInfoString = packageInfoString.substring(0, lastIndexOfHyphen) + DOT + packageInfoString.substring(lastIndexOfHyphen + 1);
                    // create dependencyInfo object
                    DependencyInfo dependencyInfo = null;
                    String packVersion = getPackageVersion(packageInfoString);
                    if (packVersion != null) {
                        dependencyInfo = new DependencyInfo(
                                null, MessageFormat.format(RPM_PACKAGE_PATTERN, packageInfoString), packVersion);
                        dependencyInfos.add(dependencyInfo);
                    }
                }
            }
        }catch (Exception e){
            logger.warn("Failed to parse {} : {}",file,e.getMessage());
        }
        finally {
            closeStream(br, fr);
        }
        return dependencyInfos;
    }

    // get rpm package version
    private String getPackageVersion(String packageInfoString) {
        // packageInfoString for example - audit-libs-2.7.6-3.el7-x86_64
        try {
            String firstDotString = packageInfoString.substring(0, packageInfoString.indexOf(DOT));
            int lastIndexOfHyphen = firstDotString.lastIndexOf(DASH);
            int lastIndexOfDot = packageInfoString.lastIndexOf(DOT);
            String packVersion = packageInfoString.substring(lastIndexOfHyphen + 1, lastIndexOfDot);
            if (StringUtils.isNotBlank(packVersion)) {
                return packVersion;
            }
        }catch (Exception e){
            logger.warn("Failed to create package version : {}",e.getMessage());
        }
        return null;
    }

    @Override
    public File findFile(String[] files, String filename,String operatingSystem) {
        return null;
    }

    // find yumdb folder from collection
    public File checkFolders(Collection<String> yumDbFolders,String yumDbFolderPath,String osName) {
        if (!osName.startsWith(WINDOWS)){
            yumDbFolderPath = yumDbFolderPath.replace(WINDOWS_SEPARATOR,LINUX_SEPARATOR);
        }
        if(!yumDbFolders.isEmpty()){
            for (String folderPath:yumDbFolders) {
                File file = new File(folderPath);
                if(file.listFiles().length > 0 && folderPath.contains(yumDbFolderPath)){
                    return file;
                }
            }
        }
        return null;
    }
}
