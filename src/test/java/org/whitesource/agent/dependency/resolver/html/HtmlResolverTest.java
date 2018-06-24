package org.whitesource.agent.dependency.resolver.html;

import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.jsoup.Jsoup;
import org.jsoup.helper.Validate;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.File;
import java.io.IOException;

import static jdk.nashorn.internal.objects.Global.print;

/**
 * Created by anna.rozin
 */
public class HtmlResolverTest {


//    public static final File HTML_FILE_EXMPLE = TestHelper.getFileFromResources("resolver/html/index.html");

    @Test
    public void parseHtmlFile() throws IOException {
        HtmlDependencyResolver htmlDependencyResolver = new HtmlDependencyResolver();
        htmlDependencyResolver.resolveDependencies("C:\\Users\\RazNitzan\\Desktop\\htmlTest\\bootstrap-master",
                "C:\\Users\\RazNitzan\\Desktop\\htmlTest\\bootstrap-master", null);

    }


    public static String getOsRelativePath(String relativeFilePath) {
        return relativeFilePath.replace("\\", String.valueOf(File.separatorChar).replace(Constants.FORWARD_SLASH, String.valueOf(File.separatorChar)));
    }
}
