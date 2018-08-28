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
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.fs.*;
import org.whitesource.fs.configuration.ConfigurationSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;

import static org.whitesource.agent.ConfigPropertyKeys.ENDPOINT_PORT;

/**
 * Blocking Verticle that does the work on top of the FSA
 */
public class FsaVerticle extends AbstractVerticle {

    private final Logger logger = LoggerFactory.getLogger(FsaVerticle.class);
    public static final String API_ANALYZE = "/analyze";
    public static final String API_SEND = "/send";
    public static final String HOME = "/";
    public static final String WELCOME_MESSAGE = "<h1>File system agent is up and running </h1>";
    public static final String CONFIGURATION = "configuration";
    public static final String KEYSTORE_JKS = "keystore.jks";
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

        String certificate = localFsaConfiguration.getEndpoint().getCertificate();
        String pass = localFsaConfiguration.getEndpoint().getPass();

        if (StringUtils.isEmpty(certificate) || StringUtils.isEmpty(pass) && localFsaConfiguration.getEndpoint().isSsl()) {
            certificate = KEYSTORE_JKS;
            pass = UUID.randomUUID().toString();
            generateCertificateAndPass(certificate, pass);
        }

        // Create Http server and pass the 'accept' method to the request handler
        vertx.createHttpServer(new HttpServerOptions().setSsl(localFsaConfiguration.getEndpoint().isSsl()).setKeyStoreOptions(new JksOptions()
                .setPath(certificate)
                .setPassword(pass)
        )).requestHandler(router::accept).
                listen(config().getInteger(ENDPOINT_PORT, localFsaConfiguration.getEndpoint().getPort()),
                        result -> {
                            if (result.succeeded()) {
                                logger.info("Http server completed..");
                                fut.complete();
                            } else {
                                fut.fail(result.cause());
                                logger.warn("Http server failed..");
                            }
                        }
                );
    }

    private boolean generateCertificateAndPass(String keystoreName, String password) {
        String keyToolPath = Paths.get(System.getProperty("java.home"), "bin", "keytool").toString();
        String[] params = new String[]{keyToolPath, "-genkey", "-alias", "replserver", "-keyalg", "RSA", "-keystore", keystoreName, "-dname",
                "\"CN=author, OU=Whitesource, O=WS, L=Location, S=State, C=US\"", "-storepass", password, "-keypass", password};

        if (SystemUtils.IS_OS_LINUX) {
            params = new String[]{keyToolPath, "-genkey", "-alias", "replserver", "-keyalg", "RSA", "-keystore", keystoreName, "-dname",
                    "CN=author, OU=Whitesource, O=WS, L=Location, S=State, C=US", "-storepass", password, "-keypass", password};
        }

        CommandLineProcess commandLineProcess = new CommandLineProcess(System.getProperty("user.dir"), params);
        try {
            if (Files.exists(Paths.get(keystoreName))) {
                Files.delete(Paths.get(keystoreName));
            }
            logger.debug("Running: " + String.join(Constants.WHITESPACE, params));
            commandLineProcess.executeProcess();
            if (commandLineProcess.isErrorInProcess()) {
                logger.error("Error creating self signed certificate");
                return false;
            } else {
                logger.info("Self signed certificate created");
                return true;
            }
        } catch (IOException e) {
            logger.debug("Error creating certificate" + e);
            logger.error("Error creating self signed certificate");
            return false;
        }
    }

    private void send(RoutingContext context) {
        vertx.executeBlocking(future -> {
            ProjectsDetails resultProjects = getProjects(context, true);
            future.complete(resultProjects);
        }, false, res -> {
            if (res.failed()) {
                logger.error("error running blocking request:", res.cause().getMessage());
            } else {
                ProjectsDetails resultProjects = (ProjectsDetails)res.result();
                ResultDto resultDto = new ResultDto(resultProjects.getDetails(), resultProjects.getStatusCode());
                handleResponse(context, resultDto);
            }
        });

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
            logger.error("Error writing json:", e);
            context.response().end("Scanning has failed");
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
        FSAConfiguration.ignoredWebProperties.forEach(property->{
            if (properties.containsKey(property)) {
                logger.info("Property "+ property +" will be ignored");
                properties.remove(property);
            }
        });

        Properties propertiesLocal = ConfigurationSerializer.getAsProperties(baseFsaConfiguration);

        FSAConfigProperties merged = new FSAConfigProperties();
        merged.putAll(propertiesLocal);
        merged.putAll(properties);

        return new FSAConfiguration(merged);
    }
}
