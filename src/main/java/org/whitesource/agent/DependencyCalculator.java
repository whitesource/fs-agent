package org.whitesource.agent;

import org.whitesource.agent.api.model.DependencyInfo;

import java.io.File;
import java.text.MessageFormat;
import java.util.*;

public class DependencyCalculator {

    private static final List<String> progressAnimation = Arrays.asList("|", "/", Constants.DASH, "\\");
    private static final int ANIMATION_FRAMES = progressAnimation.size();
    private final boolean showProgressBar;
    private int animationIndex = 0;

    public DependencyCalculator(boolean showProgressBar) {
        this.showProgressBar = showProgressBar;
        this.animationIndex = 0;
    }

    public Collection<DependencyInfo> createDependencies(boolean scmConnector, int totalFiles, Map<File, Collection<String>> fileMap,
                                                         Collection<String> excludedCopyrights, boolean partialSha1Match) {
        return createDependencies(scmConnector, totalFiles, fileMap, excludedCopyrights, partialSha1Match, false, false);
    }

    public Collection<DependencyInfo> createDependencies(boolean scmConnector, int totalFiles, Map<File, Collection<String>> fileMap,
                                                         Collection<String> excludedCopyrights, boolean partialSha1Match, boolean calculateHints, boolean calculateMd5) {
        List<DependencyInfo> allDependencies = new ArrayList<>();
        if (showProgressBar) {
            displayProgress(0, totalFiles);
        }

        int index = 1;
        for (Map.Entry<File, Collection<String>> entry : fileMap.entrySet()) {
            for (String fileName : entry.getValue()) {
                DependencyInfoFactory factory = new DependencyInfoFactory(excludedCopyrights, partialSha1Match, calculateHints, calculateMd5);
                DependencyInfo originalDependencyInfo = factory.createDependencyInfo(entry.getKey(), fileName);
                if (originalDependencyInfo != null) {
                    if (scmConnector) {
                        originalDependencyInfo.setSystemPath(fileName.replace(Constants.BACK_SLASH, Constants.FORWARD_SLASH));
                    }
                    allDependencies.add(originalDependencyInfo);
                }
                if (showProgressBar) {
                    displayProgress(index, totalFiles);
                }
                index++;
            }
        }
        return allDependencies;
    }

    private void displayProgress(int index, int totalFiles) {
        StringBuilder sb = new StringBuilder("[INFO] ");

        // draw each animation for 4 frames
        int actualAnimationIndex = animationIndex % (ANIMATION_FRAMES * 4);
        sb.append(progressAnimation.get((actualAnimationIndex / 4) % ANIMATION_FRAMES));
        animationIndex++;

        // draw progress bar
        sb.append(" [");
        double percentage = ((double) index / totalFiles) * 100;
        int progressionBlocks = (int) (percentage / 3);
        for (int i = 0; i < progressionBlocks; i++) {
            sb.append(Constants.POUND);
        }
        for (int i = progressionBlocks; i < 33; i++) {
            sb.append(Constants.WHITESPACE);
        }
        sb.append("] {0}% - {1} of {2} files\r");
        System.out.print(MessageFormat.format(sb.toString(), (int) percentage, index, totalFiles));

        if (index == totalFiles) {
            // clear progress animation
            System.out.print("                                                                                  \r");
        }
    }
}
