package org.whitesource.agent.dependency.resolver.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.FilesScanner;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Created by anna.rozin
 */
public class HtmlDependencyResolver extends AbstractDependencyResolver {


    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(HtmlDependencyResolver.class);

    private final String[] archiveIncludesPattern = {Constants.PATTERN +Constants.HTML, Constants.PATTERN +Constants.HTM};

    /* --- Members --- */

    private boolean htmlRestoreDependencies;


    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) throws FileNotFoundException {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        FilesScanner filesScanner = new FilesScanner();
        String[] directoryContent = filesScanner.getDirectoryContent(topLevelFolder, archiveIncludesPattern, null, true, true);
        for (String htmlFile : directoryContent) {
            Document htmlFileDocument;
            try {
                htmlFileDocument = Jsoup.parse(new File(topLevelFolder + File.separator + htmlFile), "UTF-8");
                Elements script = htmlFileDocument.getElementsByAttribute(Constants.SRC);
                //create list of links for .js files for each html file
                List<String> scriptUrls = new LinkedList<>();
                for (Element srcLink : script) {
                    String src = srcLink.attr(Constants.SRC);
                    scriptUrls.add(src);
                    fixUrls(scriptUrls); //todo  https://github.com/angular-ui/bootstrap
                    collectJsFilesAndCalcHashes(scriptUrls);
                }

            } catch (IOException e) {
                e.printStackTrace();//todo
            }


        }
        return null;
    }

    private void collectJsFilesAndCalcHashes(List<String> scriptUrls) {
        for (String scriptUrl : scriptUrls) {
            RestTemplate restTemplate = new RestTemplate();
            URI uriScopeDep = null;
            try {
                uriScopeDep = new URI("https:" + scriptUrls);
            } catch (URISyntaxException e) {
                e.printStackTrace();//todo change to logger
            }
            HttpHeaders httpHeaders = new HttpHeaders();
            HttpEntity entity = new HttpEntity(httpHeaders);
            String body = restTemplate.exchange(uriScopeDep, HttpMethod.GET, entity, String.class).getBody();
            PrintWriter writer = null;
            try {
                //todo https://stackoverflow.com/questions/2885173/how-do-i-create-a-file-and-write-to-it-in-java
                writer = new PrintWriter("the-file-name.txt", "UTF-8"); // todo change to real name with temp path
            } catch (FileNotFoundException e) {
                e.printStackTrace();//todo change to logger
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();//todo change to logger
            }
            writer.println(body);
            writer.close();
            //todo - write to file
            //todo calc hashes
            //todo create dependencyInfo

        }

    }

    private void fixUrls(List<String> scriptUrls) {
        // todo add collect and fix links
    }

    @Override
    protected Collection<String> getExcludes() {
        return new ArrayList<>();
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return  Arrays.asList(Constants.HTML);//todo check htm as well
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.NPM;//todo donot put any type of dependency
    }

    @Override
    protected String getBomPattern() {
        return Constants.PATTERN + Constants.DOT + Constants.HTML; //todo check htm as well
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }
}
