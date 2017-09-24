package org.whitesource.agent.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class FileWalker implements Iterator<Path> {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(FileWalker.class);

    /* --- Private members --- */

    final BlockingQueue<Path> pathBlockingQueue;

    /* --- Constructors --- */

    public FileWalker(String scannerBaseDir, final int size, final String[] includes, final String[] excludes) {
        pathBlockingQueue = new ArrayBlockingQueue<>(size);
        Thread thread = new Thread(() -> {

            final PathMatcher pathMatcherIncludes = FileSystems.getDefault().getPathMatcher(
                    "glob:{" + String.join(",", includes) + "}");

            final PathMatcher pathMatcherExcludes = FileSystems.getDefault().getPathMatcher(
                    "glob:{" + String.join(",", excludes) + "}");

            try {
                Files.walkFileTree(Paths.get(scannerBaseDir), new FileVisitor<Path>() {
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try {
                            if (attrs.isSymbolicLink()) {
                                System.out.format("Symbolic link: %s ", file);
                            } else if (attrs.isRegularFile()) {
                                System.out.format("Regular file: %s ", file);
                            } else {
                                System.out.format("Other: %s ", file);
                            }

                            if (pathMatcherIncludes.matches(file) && !pathMatcherExcludes.matches(file)) {
                                pathBlockingQueue.offer(file, 4242, TimeUnit.HOURS);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /* --- Iterator implementation --- */

    public Iterator<Path> iterator() {
        return this;
    }

    public boolean hasNext() {
        boolean hasNext = false;
        long dropDeadMS = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < dropDeadMS) {
            if (pathBlockingQueue.peek() != null) {
                hasNext = true;
                break;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return hasNext;
    }

    public Path next() {
        Path path = null;
        try {
            path = pathBlockingQueue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return path;
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

    /* --- Static methods --- */

    public static Iterable<String> getFileNames(String scannerBaseDir, String[] includes, String[] excludes, boolean followSymlinks, boolean globCaseSensitive) {
        List<String> files = new ArrayList<>();

        final PathMatcher pathMatcherIncludes = FileSystems.getDefault().getPathMatcher(
                "glob:{" + String.join(",", includes) + "}");

        final PathMatcher pathMatcherExcludes = FileSystems.getDefault().getPathMatcher(
                "glob:{" + String.join(",", excludes) + "}");

        try {
            Path pathToScan = Paths.get(scannerBaseDir);
            Files.walkFileTree(pathToScan, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path path,
                                                 BasicFileAttributes attrs) throws IOException {
                    if (pathMatcherIncludes.matches(path) && !pathMatcherExcludes.matches(path)) {
                        files.add(path.toString().replace(pathToScan.toString() + File.separator, ""));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return files;
    }

    public static Iterable<String> getAllFileNames(String scannerBaseDir, String[] includes, String[] excludes, boolean followSymlinks, boolean globCaseSensitive) {
        FileWalker z = null; // start path, queue size
        try {
            z = new FileWalker(scannerBaseDir, 1024, includes, excludes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Iterator<Path> i = z.iterator();
        List<String> files = new ArrayList<>();
        while (i.hasNext()) {
            Path p = i.next();
            String s = p.toString().replace(scannerBaseDir, "");
            files.add(s);
        }
        return files;
    }
}