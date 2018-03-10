package org.whitesource.agent.dependency.resolver.docker;

import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.*;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;


/**
 * @author chen.luigi
 */
public class ArchLinuxParser extends AbstractParser {

    public static final String PACKAGE = "%NAME%";
    public static final String VERSION = "%VERSION%";
    public static final String ARCHITECTURE = "%ARCH%";
    public static final String DESC = "desc";
    private static final String ARCH_LINUX_PACKAGE_PATTERN = "{0}-{1}-{2}.pkg.tar.xz";
    public static final String DESC_PATH = "var\\lib\\pacman\\local";

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
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        closeStream(br, fr);
                    }
                }
            }
        }
        return dependencyInfos;
    }

    @Override
    public File findFile(String[] files, String filename) {
        Map<File, Integer> filesMap = new HashedMap();
        Map.Entry<File, Integer> maxEntery = null;
        for (String filepath : files) {
            if (filepath.contains(filename) && filepath.endsWith(DESC)) {
                int i = filepath.lastIndexOf(DESC_PATH);
                String descPath = filepath.substring(0, i + 20);
                File file = new File(descPath);
                if (filesMap.get(file.getPath()) == null && file.isDirectory()) {
                    filesMap.put(file, file.listFiles().length);
                }
            }
        }
        if (!filesMap.isEmpty()) {
            for (Map.Entry<File, Integer> entry : filesMap.entrySet()) {
                if (maxEntery == null || entry.getValue().compareTo(maxEntery.getValue()) > 0) {
                    maxEntery = entry;
                }
            }
            return maxEntery.getKey();
        }

        return null;
    }

    // Get the dec files from specific folder (every dec contains dependency info data)
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

    public DependencyInfo createDependencyInfo(Package packageInfo) {
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
