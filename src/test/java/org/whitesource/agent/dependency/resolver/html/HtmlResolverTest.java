package org.whitesource.agent.dependency.resolver.html;

import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.Constants;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

/**
 * Created by anna.rozin
 */
public class HtmlResolverTest {


//    public static final File HTML_FILE_EXMPLE = TestHelper.getFileFromResources("resolver/html/index.html");

    @Ignore
    @Test
    public void parseHtmlFile() throws IOException {
        HashSet<String> set = new HashSet<>();
        set.add("src\\test\\resources\\resolver\\html\\template\\modal\\window.html");
        HtmlDependencyResolver htmlDependencyResolver = new HtmlDependencyResolver();
        htmlDependencyResolver.resolveDependencies("src\\test\\resources\\resolver\\html\\template",
                "src\\test\\resources\\resolver\\html\\template", set);
    }

    public static String getOsRelativePath(String relativeFilePath) {
        return relativeFilePath.replace("\\", String.valueOf(File.separatorChar).replace(Constants.FORWARD_SLASH, String.valueOf(File.separatorChar)));
    }
}
