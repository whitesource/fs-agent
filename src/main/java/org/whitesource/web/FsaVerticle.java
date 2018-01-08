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
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.fs.Main;
import org.whitesource.fs.StatusCode;
import org.whitesource.fs.ProjectsDetails;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

public class FsaVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(FsaVerticle.class);
    public static final String API_ANALYZE = "/analyze";
    public static final String API_SEND = "/send";
    public static final String HOME = "/";
    public static final int DEFAULT_PORT = 8383;
    public static final String WELCOME_MESSAGE = "<h1>File system agent is up and running </h1>";

    @Override
    public void start(Future<Void> fut) {
        Router router = Router.router(vertx);
        // add a handler which sets the request body on the RoutingContext.
        router.route().handler(BodyHandler.create());

        // expose a POST method endpoint on the URI: /analyze
        router.post(API_ANALYZE).blockingHandler(this::analyze);

        // expose a POST method endpoint on the URI: /analyze
        router.post(API_SEND).blockingHandler(this::send);

        router.get(HOME).handler(this::welcome);

        // Create Http server and pass the 'accept' method to the request handler
        vertx.createHttpServer().requestHandler(router::accept).
                listen(config().getInteger("http.port", DEFAULT_PORT),
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
        ProjectsDetails projects = getProjects(context, true);
        if (projects.getStatusCode().equals(StatusCode.SUCCESS)) {
            ProjectsDetails resultProjects = getProjects(context,true);
            ResultDto resultDto = new ResultDto(resultProjects.getProjects(),resultProjects.getStatusCode());
            String result = null;
            try {
                result = new ObjectMapper().writeValueAsString(resultDto);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            context.response().end(result);
        } else {
            context.response().end("scanning has failed");
        }
    }

    private void welcome(RoutingContext context) {
        context.response().end(WELCOME_MESSAGE);
    }

    public void analyze(RoutingContext context) {
        ProjectsDetails resultProjects = getProjects(context, false);
        ResultDto resultDto = new ResultDto(resultProjects, resultProjects.getStatusCode());
        String result = "error";
        try {
            result = new ObjectMapper().writeValueAsString(resultDto);
        } catch (JsonProcessingException e) {
            logger.error("error writing json:", e);
            context.response().end("scanning has failed");
        }
        context.response().end(result);
    }

    private ProjectsDetails getProjects(RoutingContext context, boolean shouldSend) {
        // the POSTed content is available in context.getBodyAsJson()
        //JsonObject body = context.getBodyAsJson();
        // a JsonObject wraps a map and it exposes type-aware getters
        //String postedText = body.getString("text");
        Properties props = getPropertiesFromBody(context);
        Main main = new Main();
        return main.scanAndSend(props, shouldSend);
    }

    private Properties getPropertiesFromBody(RoutingContext context) {
        String postedText = context.getBodyAsString();
        logger.debug(postedText);
        Properties properties = null;
        try {
            properties = parsePropertiesString(postedText);
        } catch (IOException e) {
            logger.error("error parsing properties:", e);
        }
        return properties;
    }

    private Properties parsePropertiesString(String strProperties) throws IOException {
        final Properties properties = new Properties();
        properties.load(new StringReader(strProperties));
        return properties;
    }
}
