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
import org.whitesource.agent.ProjectsSender;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.fs.Main;
import org.whitesource.fs.StatusCode;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
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
        Pair<Collection<AgentProjectInfo>, StatusCode> projects = getProjects(context, true);
        if (projects.getValue().equals(StatusCode.SUCCESS)) {
            Properties props = getPropertiesFromBody(context);
            FSAConfiguration fsaConfiguration = new FSAConfiguration(props);
            ProjectsSender projectsSender = new ProjectsSender(fsaConfiguration);
            Pair<String, StatusCode> resultProjects = projectsSender.sendProjects(projects.getKey());
            String result = null;
            try {
                result = new ObjectMapper().writeValueAsString(resultProjects);
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
        Pair<Collection<AgentProjectInfo>,StatusCode> projects = getProjects(context,false);
        if(projects.getValue().equals(StatusCode.SUCCESS)){
            String result = "error";
            try {
                result = new ObjectMapper().writeValueAsString(projects);
            } catch (JsonProcessingException e) {
                logger.error("error writing json:", e);
            }
            context.response().end(result);
        }else {
            context.response().end("scanning has failed");
        }
    }

    private Pair<Collection<AgentProjectInfo>,StatusCode> getProjects(RoutingContext context, boolean shouldSend) {
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
