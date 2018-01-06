package org.whitesource.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.fs.ProjectsCalculator;
import org.whitesource.fs.StatusCode;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.Properties;

public class FsaVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> fut) {
        //vertx
        //        .createHttpServer()
        //        .requestHandler(r -> {
        //            r.response().end("<h1>Hello from my first " +
        //                    "Vert.x 3 application</h1>");
        //        })
        //        .listen(8383, result -> {
        //            if (result.succeeded()) {
        //                fut.complete();
        //            } else {
        //                fut.fail(result.cause());
        //            }
        //        });

        Router router = Router.router(vertx);
        // add a handler which sets the request body on the RoutingContext.
        router.route().handler(BodyHandler.create());
        // expose a POST method endpoint on the URI: /analyze
        router.post("/analyze").handler(this::analyze);

        // Create Http server and pass the 'accept' method to the request handler
        vertx.createHttpServer().requestHandler(router::accept).
                listen(config().getInteger("http.port", 8383),
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

    // handle anything POSTed to /analyze
    public void analyze(RoutingContext context) {
        // the POSTed content is available in context.getBodyAsJson()
        //JsonObject body = context.getBodyAsJson();
        // a JsonObject wraps a map and it exposes type-aware getters
        //String postedText = body.getString("text");

        String postedText = context.getBodyAsString();
        Properties properties = null;
        try {
            properties = parsePropertiesString(postedText);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ProjectsCalculator projectsCalculator = new ProjectsCalculator();

        FSAConfiguration fsaConfiguration = new FSAConfiguration(properties);
        Pair<Collection<AgentProjectInfo>,StatusCode> projects = projectsCalculator.getAllProjects(fsaConfiguration);
        if(projects.getValue().equals(StatusCode.SUCCESS)){
            // projects.getValue().getValue();
            String result = "error";
            try {
                result = new ObjectMapper().writeValueAsString(projects);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            context.response().end(result);
        }else {
            context.response().end("scanning has failed");
        }
    }

    private Properties parsePropertiesString(String strProperties) throws IOException {
        // grr at load() returning void rather than the Properties object
        // so this takes 3 lines instead of "return new Properties().load(...);"
        final Properties properties = new Properties();
        properties.load(new StringReader(strProperties));
        return properties;
    }
}
