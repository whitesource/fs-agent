package org.whitesource.fs.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.whitesource.agent.api.dispatch.UpdateType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class FSAConfiguration {
    public FSAConfiguration() {

    }

    public FSAConfiguration(Properties configProps) {
        this();
        Class curClass = FSAConfiguration.class;
        Method[] allMethods = curClass.getMethods();
        List<Method> setters = Arrays.stream(allMethods).filter(method->method.getName().startsWith("set")).collect(Collectors.toList());

        configProps.entrySet().forEach(entry->{
           Optional<Method> method = setters.stream()
                   .filter(setter-> setter.toString().toUpperCase().contains(entry.getKey().toString().replace(".","").toUpperCase()))
                   .findFirst();

           if(method.isPresent()){
               try {
                   Class<?>[] params = method.get().getParameterTypes();

                   if (params.length == 1) {
                       if (params[0].equals(int.class)){
                           method.get().invoke(this, Integer.valueOf(entry.getValue().toString()));
                       }
                       else if(params[0].equals(boolean.class)){
                           method.get().invoke(this, Boolean.valueOf(entry.getValue().toString()));
                       }
                       else if(params[0].equals(UpdateType.class)){
                           method.get().invoke(this, UpdateType.valueOf(entry.getValue().toString()));
                       }
                       else {
                           method.get().invoke(this, entry.getValue().toString());
                       }
                   }
               } catch (IllegalAccessException e) {
                   e.printStackTrace();
               } catch (InvocationTargetException e) {
                   e.printStackTrace();
               }
           }
        });
    }

    private boolean checkPolicies;
    private boolean forceUpdate;
    private boolean forceCheckAllDependencies;
    private String apiKey;
    private boolean partialSha1Match;
    private String productToken;
    private String productName;
    private String productVersion;
    private String projectToken;
    private String projectName;
    private String projectVersion;
    private String includes;
    private String excludes;
    private int archiveExtractionDepth;
    private String archiveIncludes;
    private String archiveExcludes;
    private String archiveFastUnpack;
    private String proxyHost;
    private String proxyPort;
    private String proxyUser;
    private String proxyPass;
    private boolean calculateHints;
    private boolean getCalculateMd5;
    private String requesterEmail;
    private boolean caseSensitive;
    private String host;
    private int port;
    private String user;
    private String pass;
    private boolean offline ;
    private boolean offlineZip;
    private boolean offlinePrettyJson;
    private String copyrightExcludes;
    private int wssConnectionTimeoutMinutes=60;
    private boolean followSymbolicLinks;
    private boolean showProgressBar;
    private boolean npmResolveDependencies;
    private boolean npmIncludeDevDependencies;
    private boolean npmIgnoreJavaScriptFiles;
    private boolean bowerResolveDependencies;
    private boolean nugetResolveDependencies;
    private boolean projectPerFolder;
    private UpdateType updateType;

    public boolean isCheckPolicies() {
        return checkPolicies;
    }

    public void setCheckPolicies(boolean checkPolicies) {
        this.checkPolicies = checkPolicies;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    public boolean isForceCheckAllDependencies() {
        return forceCheckAllDependencies;
    }

    public void setForceCheckAllDependencies(boolean forceCheckAllDependencies) {
        this.forceCheckAllDependencies = forceCheckAllDependencies;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isPartialSha1Match() {
        return partialSha1Match;
    }

    public void setPartialSha1Match(boolean partialSha1Match) {
        this.partialSha1Match = partialSha1Match;
    }

    public String getProductToken() {
        return productToken;
    }

    public void setProductToken(String productToken) {
        this.productToken = productToken;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public void setProductVersion(String productVersion) {
        this.productVersion = productVersion;
    }

    public String getProjectToken() {
        return projectToken;
    }

    public void setProjectToken(String projectToken) {
        this.projectToken = projectToken;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectVersion() {
        return projectVersion;
    }

    public void setProjectVersion(String projectVersion) {
        this.projectVersion = projectVersion;
    }

    public String getIncludes() {
        return includes;
    }

    public void setIncludes(String includes) {
        this.includes = includes;
    }

    public String getExcludes() {
        return excludes;
    }

    public void setExcludes(String excludes) {
        this.excludes = excludes;
    }

    public int getArchiveExtractionDepth() {
        return archiveExtractionDepth;
    }

    public void setArchiveExtractionDepth(int archiveExtractionDepth) {
        this.archiveExtractionDepth = archiveExtractionDepth;
    }

    public String getArchiveIncludes() {
        return archiveIncludes;
    }

    public void setArchiveIncludes(String archiveIncludes) {
        this.archiveIncludes = archiveIncludes;
    }

    public String getArchiveExcludes() {
        return archiveExcludes;
    }

    public void setArchiveExcludes(String archiveExcludes) {
        this.archiveExcludes = archiveExcludes;
    }

    public String getArchiveFastUnpack() {
        return archiveFastUnpack;
    }

    public void setArchiveFastUnpack(String archiveFastUnpack) {
        this.archiveFastUnpack = archiveFastUnpack;
    }

    @JsonProperty("proxy.host")
    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    @JsonProperty("proxy.port")
    public String getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
    }

    @JsonProperty("proxy.user")
    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }

    @JsonProperty("proxy.pass")
    public String getProxyPass() {
        return proxyPass;
    }

    public void setProxyPass(String proxyPass) {
        this.proxyPass = proxyPass;
    }

    public boolean isCalculateHints() {
        return calculateHints;
    }

    public void setCalculateHints(boolean calculateHints) {
        this.calculateHints = calculateHints;
    }

    public boolean isGetCalculateMd5() {
        return getCalculateMd5;
    }

    public void setGetCalculateMd5(boolean getCalculateMd5) {
        this.getCalculateMd5 = getCalculateMd5;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public void setRequesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail;
    }


    //@JsonIgnoreProperties(ignoreUnknown = true)
    @JsonProperty("case.sensitive.glob")
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    //@JsonIgnoreProperties(ignoreUnknown = true)
    @JsonProperty("caseSensitive")
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public boolean isOffline() {
        return offline;
    }

    @JsonProperty("offlineZip")
    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    @JsonProperty("offline.zip")
    public boolean getOfflineZip() {
        return offlineZip;
    }

    public void setOfflineZip(boolean offlineZip) {
        this.offlineZip = offlineZip;
    }

    @JsonProperty("offline.prettyJson")
    public boolean getOfflinePrettyJson() {
        return offlinePrettyJson;
    }

    @JsonProperty("offlinePrettyJson")
    public void setOfflinePrettyJson(boolean offlinePrettyJson) {
        this.offlinePrettyJson = offlinePrettyJson;
    }

    public String getCopyrightExcludes() {
        return copyrightExcludes;
    }

    public void setCopyrightExcludes(String copyrightExcludes) {
        this.copyrightExcludes = copyrightExcludes;
    }

    @JsonProperty("wss.connectionTimeoutMinutes")
    public int getWssConnectionTimeoutMinutes() {
        return wssConnectionTimeoutMinutes;
    }

    @JsonProperty("setWssConnectionTimeoutMinutes")
    public void setWssConnectionTimeoutMinutes(int wssConnectionTimeoutMinutes) {
        this.wssConnectionTimeoutMinutes = wssConnectionTimeoutMinutes;
    }

    public boolean isFollowSymbolicLinks() {
        return followSymbolicLinks;
    }

    public void setFollowSymbolicLinks(boolean followSymbolicLinks) {
        this.followSymbolicLinks = followSymbolicLinks;
    }

    public boolean isShowProgressBar() {
        return showProgressBar;
    }

    public void setShowProgressBar(boolean showProgressBar) {
        this.showProgressBar = showProgressBar;
    }

    @JsonProperty("npm.resolveDependencies")
    public boolean isNpmResolveDependencies() {
        return npmResolveDependencies;
    }

    @JsonProperty("npmResolveDependencies")
    public void setNpmResolveDependencies(boolean npmResolveDependencies) {
        this.npmResolveDependencies = npmResolveDependencies;
    }

    @JsonProperty("npm.includeDevDependencies")
    public boolean isNpmIncludeDevDependencies() {
        return npmIncludeDevDependencies;
    }

    @JsonProperty("npmIncludeDevDependencies")
    public void setNpmIncludeDevDependencies(boolean npmIncludeDevDependencies) {
        this.npmIncludeDevDependencies = npmIncludeDevDependencies;
    }

    @JsonProperty("npm.ignoreJavaScriptFiles")
    public boolean isNpmIgnoreJavaScriptFiles() {
        return npmIgnoreJavaScriptFiles;
    }

    @JsonProperty("npmIgnoreJavaScriptFiles")
    public void setNpmIgnoreJavaScriptFiles(boolean npmIgnoreJavaScriptFiles) {
        this.npmIgnoreJavaScriptFiles = npmIgnoreJavaScriptFiles;
    }

    @JsonProperty("bower.resolveDependencies")
    public boolean isBowerResolveDependencies() {
        return bowerResolveDependencies;
    }

    @JsonProperty("bowerResolveDependencies")
    public void setBowerResolveDependencies(boolean bowerResolveDependencies) {
        this.bowerResolveDependencies = bowerResolveDependencies;
    }

    @JsonProperty("nuget.resolveDependencies")
    public boolean isNugetResolveDependencies() {
        return nugetResolveDependencies;
    }

    @JsonProperty("nugetResolveDependencies")
    public void setNugetResolveDependencies(boolean nugetResolveDependencies) {
        this.nugetResolveDependencies = nugetResolveDependencies;
    }

    public boolean isProjectPerFolder() {
        return projectPerFolder;
    }

    public void setProjectPerFolder(boolean projectPerFolder) {
        this.projectPerFolder = projectPerFolder;
    }

    public UpdateType getUpdateType() {
        return updateType;
    }

    public void setUpdateType(UpdateType updateType) {
        this.updateType = updateType;
    }

    public List<FSAScmConfiguration> getScms() {
        return scms;
    }

    public void setScms(List<FSAScmConfiguration> scms) {
        this.scms = scms;
    }

    public List<FSAScmConfiguration> scms;
}
