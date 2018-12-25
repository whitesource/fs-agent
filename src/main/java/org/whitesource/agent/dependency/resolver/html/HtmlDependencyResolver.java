package org.whitesource.agent.dependency.resolver.html;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.DependencyInfoFactory;
import org.whitesource.agent.TempFolders;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
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
    public static final String URL_PATH = "://";

    public final String[] includesPattern = new String[htmlTypeExtensions.size()];
    private final Pattern patternOfFirstLetter = Pattern.compile("[a-zA-Z].*");
    private final Pattern patternOfLegitSrcUrl = Pattern.compile("<%.*%>");
    private Map<String, String> urlResponseMap = new HashMap<>();

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
                dependencies.addAll(collectJsFilesAndCalcHashes(scriptUrls, htmlFile, this.urlResponseMap));
            } catch (IOException e) {
                logger.debug("Cannot parse the html file: {}", htmlFile);
            }
        }

        // delete parent folder of HTML Resolver
        try {
            new TempFolders().deleteTempFoldersHelper(Paths.get(System.getProperty("java.io.tmpdir"), TempFolders.UNIQUE_HTML_TEMP_FOLDER).toString());
        } catch (Exception e) {
            logger.debug("Failed to delete HTML Dependency Resolver Folder{}", e.getMessage());
        }
        // check the type and excludes
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    private boolean isLegitSrcUrl(String srcUrl) {
        // Remove parameters if JS is called with parameters
        // For example: http://somexample.com/test.js?a=1&b=3
        if (srcUrl.contains("?")) {
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

    private List<DependencyInfo> collectJsFilesAndCalcHashes(List<String> scriptUrls, String htmlFilePath, Map<String, String> urlResponseMap) {
        List<DependencyInfo> dependencies = new LinkedList<>();
        String body = null;
        String tempFolder = new FilesUtils().createTmpFolder(false, TempFolders.UNIQUE_HTML_TEMP_FOLDER);
        File tempFolderFile = new File(tempFolder);
        String dependencyFileName = null;
        if (tempFolder != null) {
            for (String scriptUrl : scriptUrls) {
                try {
                    if (urlResponseMap.containsKey(scriptUrl)) {
                        body = urlResponseMap.get(scriptUrl);
                    } else {
                        Client client = Client.create();
                        WebResource webResource = client.resource(scriptUrl);
                        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
                        if (response.getStatus() != 200) {
                            logger.debug("Could not reach the registry using the URL: {}.", scriptUrl);
                        } else {
                            logger.debug("Found a dependency in html file {}, URL: {}", htmlFilePath, scriptUrl);
                            body = response.getEntity(String.class);

                            String fileName = scriptUrl.substring(scriptUrl.lastIndexOf(Constants.FORWARD_SLASH) + 1);
                            dependencyFileName = tempFolder + File.separator + fileName;
                            PrintWriter writer = new PrintWriter(dependencyFileName, Constants.UTF8);
                            if (writer != null) {
                                writer.println(body);
                                writer.close();
                            }
                            DependencyInfoFactory dependencyInfoFactory = new DependencyInfoFactory();
                            DependencyInfo dependencyInfo = dependencyInfoFactory.createDependencyInfo(tempFolderFile, fileName);
                            if (dependencyInfo != null) {
                                dependencies.add(dependencyInfo);
                                dependencyInfo.setSystemPath(htmlFilePath);
                                dependencyInfo.setDependencyFile(htmlFilePath);
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.debug("Failed writing to file {}", dependencyFileName);
                } catch (Exception e) {
                    logger.debug("Could not reach the registry using the URL: {}.", scriptUrl);
                } finally {
                    if (StringUtils.isNotBlank(scriptUrl)) {
                        urlResponseMap.put(scriptUrl, body);
                    }
                }
            }
        }
        FilesUtils.deleteDirectory(tempFolderFile);
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
    public String[] getBomPattern() {
        return includesPattern;
    }

    @Override
    public Collection<String> getManifestFiles(){
        return htmlTypeExtensions;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }

    @Override
    protected Collection<String> getRelevantScannedFolders(Collection<String> scannedFolders) {
        // HTML resolver should scan all folders and should not remove any folder
        return scannedFolders == null ? Collections.emptyList() : scannedFolders;
    }
}
