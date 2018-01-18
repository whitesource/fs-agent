/**
 * Copyright (C) 2014 WhiteSource Ltd.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.fs.Main;
import org.whitesource.fs.ProjectsDetails;
import org.whitesource.fs.StatusCode;
import org.whitesource.fs.configuration.ConfigurationSerializer;
import org.whitesource.fs.configuration.EndPointConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * Blocking Verticle that does the work on top of the FSA
 */
public class FsaVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(FsaVerticle.class);
    public static final String API_ANALYZE = "/analyze";
    public static final String API_SEND = "/send";
    public static final String HOME = "/";
    public static final String WELCOME_MESSAGE = "<h1>File system agent is up and running </h1>";
    public static final String CONFIGURATION = "configuration";
    private FSAConfiguration localFsaConfiguration;

    @Override
    public void start(Future<Void> fut) {
        Router router = Router.router(vertx);
        // add a handler which sets the request body on the RoutingContext.
        router.route().handler(BodyHandler.create());

        // expose a POST method endpoint on the URI: /analyze
        router.post(API_ANALYZE).blockingHandler(this::analyze);

        // expose a POST method endpoint on the URI: /send
        router.post(API_SEND).blockingHandler(this::send);

        router.get(HOME).handler(this::welcome);

        String config = config().getString(CONFIGURATION);
        if (config == null) {
            localFsaConfiguration = new FSAConfiguration();
        } else {
            localFsaConfiguration = ConfigurationSerializer.getFromString(config, FSAConfiguration.class, false);
        }

        // Create Http server and pass the 'accept' method to the request handler
        //vertx.createHttpServer().requestHandler(router::accept).requestHandler(router::accept).
        vertx.createHttpServer(new HttpServerOptions().setSsl(localFsaConfiguration.getEndpoint().isSsl()).setKeyStoreOptions(new JksOptions()
                .setPath(localFsaConfiguration.getEndpoint().getCertificate())
                .setPassword(localFsaConfiguration.getEndpoint().getPass())
        )).requestHandler(router::accept).
                listen(config().getInteger(ENDPOINT_PORT, EndPointConfiguration.DEFAULT_PORT),
                        result -> {
                            if (result.succeeded()) {
                                System.out.println("Http server completed..");
                                fut.complete();
                            } else {
                                fut.fail(result.cause());
                                System.out.println("Http server failed..");
                            }
                        }
                );
    }

    private void send(RoutingContext context) {
        ProjectsDetails resultProjects = getProjects(context, true);
        ResultDto resultDto = new ResultDto(resultProjects.getDetails(), resultProjects.getStatusCode());
        handleResponse(context, resultDto);
    }

    public void analyze(RoutingContext context) {
        ProjectsDetails result = getProjects(context, false);
        ResultDto resultDto = new ResultDto(new ProjectsDetails(result.getProjects(),result.getStatusCode(),result.getDetails()), result.getStatusCode());
        handleResponse(context, resultDto);
    }

    private void handleResponse(RoutingContext context, ResultDto resultDto) {
        String result = null;
        try {
            result = new ObjectMapper().writeValueAsString(resultDto);
        } catch (JsonProcessingException e) {
            logger.error("error writing json:", e);
            context.response().end("scanning has failed");
        }
        context.response().end(result);
    }

    private void welcome(RoutingContext context) {
        context.response().end(WELCOME_MESSAGE);
    }

    private ProjectsDetails getProjects(RoutingContext context, boolean shouldSend) {
        final FSAConfiguration webFsaConfiguration = ConfigurationSerializer.getFromString(context.getBodyAsString(), FSAConfiguration.class, false);

        if (webFsaConfiguration != null) {
            HashMap<String, Object> result = ConfigurationSerializer.getFromString(context.getBodyAsString(), HashMap.class, false);
            FSAConfiguration mergedFsaConfiguration = mergeConfigurations(localFsaConfiguration, result);

            Main main = new Main();
            return main.scanAndSend(mergedFsaConfiguration, shouldSend);
        }
        return new ProjectsDetails(new ArrayList<>(), StatusCode.ERROR, "Error parsing the request");
    }

    private FSAConfiguration mergeConfigurations(FSAConfiguration baseFsaConfiguration, HashMap<String, Object> parameterMap) {
        Properties properties = ConfigurationSerializer.getAsProperties(parameterMap);
        Properties propertiesLocal = ConfigurationSerializer.getAsProperties(baseFsaConfiguration);

        Properties merged = new Properties();
        merged.putAll(propertiesLocal);
        merged.putAll(properties);

        return new FSAConfiguration(merged);
    }
}
