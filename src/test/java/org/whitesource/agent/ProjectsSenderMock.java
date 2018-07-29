package org.whitesource.agent;

import com.google.gson.Gson;
import org.whitesource.agent.api.dispatch.UpdateInventoryResult;
import org.whitesource.agent.api.dispatch.UpdateType;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.agent.client.WssServiceException;
import org.whitesource.contracts.PluginInfo;
import org.whitesource.fs.configuration.OfflineConfiguration;
import org.whitesource.fs.configuration.RequestConfiguration;
import org.whitesource.fs.configuration.SenderConfiguration;

import java.util.Collection;

/**
 * @author chen.luigi
 */
public class ProjectsSenderMock extends ProjectsSender {

    /* --- Private methods --- */

    private final WhitesourceServiceMock whitesourceService;

    public String getJson() {
        return whitesourceService.getJson();
    }

    public static class WhitesourceServiceMock extends WhitesourceService {

        public WhitesourceServiceMock(final String agent, final String agentVersion, String pluginVersion, final String serviceUrl, boolean setProxy,
                                      int connectionTimeoutMinutes, boolean ignoreCertificateCheck) {
            super(agent, agentVersion, pluginVersion, serviceUrl, setProxy, connectionTimeoutMinutes, ignoreCertificateCheck);
        }

        String json = null;

        public String getJson() {
            return json;
        }

        @Override
        public UpdateInventoryResult update(String orgToken, String requesterEmail, UpdateType updateType, String product, String productVersion, Collection<AgentProjectInfo> projectInfos, String userKey) throws WssServiceException {
            json = new Gson().toJson(this.getRequestFactory().newUpdateInventoryRequest(orgToken, updateType, requesterEmail, product, productVersion, projectInfos, userKey));
            return new UpdateInventoryResult("via-test", true);
        }

    }

    public ProjectsSenderMock(SenderConfiguration senderConfig, OfflineConfiguration offlineConfig, RequestConfiguration requestConfig, PluginInfo pluginInfo) {
        super(senderConfig, offlineConfig, requestConfig, pluginInfo);
        whitesourceService = new WhitesourceServiceMock(pluginInfo.getAgentType(), pluginInfo.getAgentVersion(), pluginInfo.getPluginVersion(),
                senderConfig.getServiceUrl(), false, 1, senderConfig.isIgnoreCertificateCheck());
    }


    @Override
    protected WhitesourceService createService() {
        return whitesourceService;
    }
}
