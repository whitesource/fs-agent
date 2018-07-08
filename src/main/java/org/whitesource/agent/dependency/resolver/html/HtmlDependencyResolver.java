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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.whitesource.agent.Constants;
import org.whitesource.agent.DependencyInfoFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.FilesUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by anna.rozin
 */
public class HtmlDependencyResolver extends AbstractDependencyResolver {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(HtmlDependencyResolver.class);

    public static final List<String> htmlTypeExtensions = Arrays.asList(Constants.HTM, Constants.HTML, Constants.SHTML,
            Constants.XHTML, Constants.JSP, Constants.ASP, Constants.DO, Constants.ASPX);
    public final String[] includesPattern = new String[htmlTypeExtensions.size()];

    public static final String WHITESOURCE_HTML_RESOLVER = "whitesource-html-resolver";
    public static final String URL_PATH = "://";
    private final Pattern patternOfFirstLetter = Pattern.compile("[a-zA-Z].*");
    private final Pattern patternOfLegitSrcUrl = Pattern.compile("<%.*%>");

    /* --- Constructors --- */

    public HtmlDependencyResolver() {
        int i = 0;
        for (String extension : htmlTypeExtensions) {
            this.includesPattern[i++] = Constants.PATTERN + Constants.DOT + extension;
        }
    }

    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        for (String htmlFile : bomFiles) {
            Document htmlFileDocument;
            try {
                // todo consider collect other tags - not ser only
                htmlFileDocument = Jsoup.parse(new File(htmlFile), Constants.UTF8);
                Elements script = htmlFileDocument.getElementsByAttribute(Constants.SRC);
                // create list of links for .js files for each html file
                List<String> scriptUrls = new LinkedList<>();
                for (Element srcLink : script) {
                    String src = srcLink.attr(Constants.SRC);
                    if (src != null && isLegitSrcUrl(src)) {
                        String srcUrl = fixUrls(src);
                        if (srcUrl != null) {
                            scriptUrls.add(srcUrl);
                        }
                    }
                }
                dependencies.addAll(collectJsFilesAndCalcHashes(scriptUrls, htmlFile));
            } catch (IOException e) {
                logger.debug("Cannot parse the html file: {}", htmlFile);
            }
        }
        // check the type and excludes
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    private boolean isLegitSrcUrl(String srcUrl) {
        // Remove parameters if JS is called with parameters
        // For example: http://somexample.com/test.js?a=1&b=3
        if(srcUrl.contains("?"))
        {
            String[] srcURLSplit = srcUrl.split("\\?");
            srcUrl = srcURLSplit[0];
        }
        if (srcUrl.endsWith(Constants.JS_EXTENSION)) {
            Matcher matcher = this.patternOfLegitSrcUrl.matcher(srcUrl);
            if (!matcher.find()) {
                return true;
            }
        }
        return false;
    }

    private List<DependencyInfo> collectJsFilesAndCalcHashes(List<String> scriptUrls, String htmlFilePath) {
        List<DependencyInfo> dependencies = new LinkedList<>();
        String tempFolder = new FilesUtils().createTmpFolder(false, WHITESOURCE_HTML_RESOLVER);
        File tempFolderFile = new File(tempFolder);
        RestTemplate restTemplate = new RestTemplate();
        String dependencyFileName = null;
        if (tempFolder != null) {
            for (String scriptUrl : scriptUrls) {
                URI uriScopeDep;
                try {
                    uriScopeDep = new URI(scriptUrl);
                    HttpHeaders httpHeaders = new HttpHeaders();
                    HttpEntity entity = new HttpEntity(httpHeaders);
                    String body = restTemplate.exchange(uriScopeDep, HttpMethod.GET, entity, String.class).getBody();
                    String fileName = scriptUrl.substring(scriptUrl.lastIndexOf(Constants.FORWARD_SLASH) + 1);
                    dependencyFileName = tempFolder + File.separator + fileName;
                    PrintWriter writer = new PrintWriter(dependencyFileName, Constants.UTF8);
                    writer.println(body);
                    writer.close();
                    DependencyInfoFactory dependencyInfoFactory = new DependencyInfoFactory();
                    DependencyInfo dependencyInfo = dependencyInfoFactory.createDependencyInfo(tempFolderFile,fileName);
                    if (dependencyInfo != null) {
                        dependencies.add(dependencyInfo);
                        dependencyInfo.setSystemPath(htmlFilePath);
                    }
                } catch (RestClientException e) {
                    logger.debug("Could not reach the registry using the URL: {}. Got an error: {}", scriptUrl, e.getMessage());
                } catch (URISyntaxException e) {
                    logger.debug("Failed creating uri of {}", scriptUrl);
                } catch (IOException e) {
                    logger.debug("Failed writing to file {}", dependencyFileName);
                }
            }
            FilesUtils.deleteDirectory(tempFolderFile);
        }
        return dependencies;
    }

    private String fixUrls(String scriptUrl) {
        if (scriptUrl.startsWith(Constants.HTTP) || scriptUrl.startsWith(Constants.HTTPS)) {
            return scriptUrl;
        }
        Matcher matcher = this.patternOfFirstLetter.matcher(scriptUrl);
        matcher.find();
        if (matcher.group(0) != null) {
            return Constants.HTTP + URL_PATH + matcher.group(0);
        } else {
            return null;
        }
    }

    @Override
    protected Collection<String> getExcludes() {
        return new ArrayList<>();
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return htmlTypeExtensions;
    }

    @Override
    protected DependencyType getDependencyType() {
        return null;
    }

    @Override
    protected String getDependencyTypeName() {
        return Constants.HTML.toUpperCase();
    }

    @Override
    protected boolean printResolvedFolder() {
        return false;
    }

    @Override
    protected String[] getBomPattern() {
        return includesPattern;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }
}
