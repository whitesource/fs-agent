package org.whitesource.fs.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.api.dispatch.UpdateType;
import org.whitesource.agent.client.ClientConstants;
import org.whitesource.fs.FSAConfiguration;

import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.*;
import static org.whitesource.agent.ConfigPropertyKeys.PROXY_PASS_PROPERTY_KEY;

public class SenderConfiguration {

    private final boolean checkPolicies;
    private final String senderServiceUrl;
    private final String senderProxyHost;
    private final int senderConnectionTimeOut;
    private final int senderProxyPort;
    private final String senderProxyUser;
    private final String senderProxyPassword;
    private final boolean forceCheckAllDependencies;
    private final boolean forceUpdate;
    private final String updateTypeValue;

    public SenderConfiguration(
            @JsonProperty("checkPolicies") boolean checkPolicies,
            @JsonProperty("senderServiceUrl") String senderServiceUrl,
            @JsonProperty("senderProxyHost") String senderProxyHost,
            @JsonProperty("senderConnectionTimeOut") int senderConnectionTimeOut,
            @JsonProperty("senderProxyPort") int senderProxyPort,
            @JsonProperty("senderProxyUser") String senderProxyUser,
            @JsonProperty("senderProxyPassword") String senderProxyPassword,
            @JsonProperty("forceCheckAllDependencies") boolean forceCheckAllDependencies,
            @JsonProperty("forceUpdate") boolean forceUpdate,
            @JsonProperty("updateTypeValue") String updateTypeValue) {
        this.checkPolicies = checkPolicies;
        this.senderServiceUrl = senderServiceUrl;
        this.senderProxyHost = senderProxyHost;
        this.senderConnectionTimeOut = senderConnectionTimeOut;
        this.senderProxyPort = senderProxyPort;
        this.senderProxyUser = senderProxyUser;
        this.senderProxyPassword = senderProxyPassword;
        this.forceCheckAllDependencies = forceCheckAllDependencies;
        this.forceUpdate = forceUpdate;
        this.updateTypeValue = updateTypeValue;
    }

    public SenderConfiguration(Properties config) {

        updateTypeValue = config.getProperty(UPDATE_TYPE, UpdateType.OVERRIDE.toString());
        checkPolicies =  FSAConfiguration.getBooleanProperty(config, CHECK_POLICIES_PROPERTY_KEY, false);
        forceCheckAllDependencies = FSAConfiguration.getBooleanProperty(config, FORCE_CHECK_ALL_DEPENDENCIES, false);
        forceUpdate = FSAConfiguration.getBooleanProperty(config, FORCE_UPDATE, false);
        senderServiceUrl = config.getProperty(ClientConstants.SERVICE_URL_KEYWORD, ClientConstants.DEFAULT_SERVICE_URL);
        senderProxyHost = config.getProperty(PROXY_HOST_PROPERTY_KEY);
        senderConnectionTimeOut = Integer.parseInt(config.getProperty(ClientConstants.CONNECTION_TIMEOUT_KEYWORD,
                String.valueOf(ClientConstants.DEFAULT_CONNECTION_TIMEOUT_MINUTES)));

        String senderPort = config.getProperty(PROXY_PORT_PROPERTY_KEY);
        if(StringUtils.isNotEmpty(senderPort)){
            senderProxyPort = Integer.parseInt(senderPort);
        }else{
            senderProxyPort = -1;
        }

        senderProxyUser = config.getProperty(PROXY_USER_PROPERTY_KEY);
        senderProxyPassword = config.getProperty(PROXY_PASS_PROPERTY_KEY);
    }

    public String getSenderServiceUrl() {
        return senderServiceUrl;
    }

    public String getUpdateTypeValue() {
        return updateTypeValue;
    }

    public boolean isCheckPolicies() {
        return checkPolicies;
    }

    public String getSenderProxyHost() {
        return senderProxyHost;
    }

    public int getSenderConnectionTimeOut() {
        return senderConnectionTimeOut;
    }

    public int getSenderProxyPort() {
        return senderProxyPort;
    }

    public String getSenderProxyUser() {
        return senderProxyUser;
    }

    public String getSenderProxyPassword() {
        return senderProxyPassword;
    }

    public boolean isForceCheckAllDependencies() {
        return forceCheckAllDependencies;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }
}
