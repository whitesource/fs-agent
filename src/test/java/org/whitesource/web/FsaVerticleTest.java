package org.whitesource.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.*;
import org.junit.runner.RunWith;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.fs.FSAConfigProperties;
import org.whitesource.fs.ProjectsDetails;
import org.whitesource.fs.StatusCode;
import org.whitesource.fs.configuration.ConfigurationSerializer;

import java.io.IOException;
import java.util.Collection;
import java.util.Properties;

@RunWith(VertxUnitRunner.class)
public class FsaVerticleTest {

    private static final String GIT_SAMPLE = "https://github.com/adutra/maven-dependency-tree-parser.git";
    private Vertx vertx;

    @Before
    public void setUp(TestContext context) {
        //VertxOptions options = new VertxOptions();
        //options.setMaxEventLoopExecuteTime(Long.MAX_VALUE);
        //vertx = Vertx.vertx(options);
        //new DeploymentOptions().setWorker(true);
        vertx = Vertx.vertx();

        vertx.deployVerticle(FsaVerticle.class.getName(),
                context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    // requires admin rights to install dependencies in linux
    @Ignore
    @Test
    public void testHome(TestContext context) {
        final Async async = context.async();

        vertx.createHttpClient().getNow(FSAConfiguration.DEFAULT_PORT, "localhost", FsaVerticle.HOME,
                response -> response.handler(body -> {
                    context.assertTrue(body.toString().equals(FsaVerticle.WELCOME_MESSAGE));
                    async.complete();
                }));
    }

    // requires admin rights to install dependencies in linux
    @Ignore
    @Test
    public void testAnalyzeApi(TestContext context) {
        final Async async = context.async();
        vertx.createHttpClient().post("localhost:" + FSAConfiguration.DEFAULT_PORT + "\\analyze",
                response -> response.handler(body -> {
                    context.assertTrue(body.toString().contains("Hello"));
                    async.complete();
                }));


        FSAConfigProperties properties = new FSAConfigProperties();
        properties.setProperty(ConfigPropertyKeys.SCM_TYPE_PROPERTY_KEY, "git");
        properties.setProperty(ConfigPropertyKeys.SCM_USER_PROPERTY_KEY, Constants.EMPTY_STRING);
        properties.setProperty(ConfigPropertyKeys.SCM_PASS_PROPERTY_KEY, Constants.EMPTY_STRING);
        properties.setProperty(ConfigPropertyKeys.SCM_URL_PROPERTY_KEY, GIT_SAMPLE);

        // just to validate
        properties.setProperty(ConfigPropertyKeys.ORG_TOKEN_PROPERTY_KEY, "token");
        properties.setProperty(ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY, "projectName");
        properties.setProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY, "**/*.java");

        FSAConfiguration fsaConfiguration = new FSAConfiguration(properties);

        String json = new ConfigurationSerializer().getAsString(fsaConfiguration,false);
        assert json != null;
        Assert.assertTrue(json.contains(GIT_SAMPLE));
        final String length = Integer.toString(json.length());

        HttpClientOptions options = new HttpClientOptions();
        options.setMaxWaitQueueSize(1000);
        options.setKeepAlive(true);
        options.setMaxPoolSize(500);

        vertx.createHttpClient(options).post(FSAConfiguration.DEFAULT_PORT, "localhost", FsaVerticle.API_ANALYZE)
                .putHeader("content-type", "application/json")
                .putHeader("content-length", length)
                .handler(response -> {
                    context.assertEquals(response.statusCode(), 200);
                    //context.assertTrue(response.headers().get("content-type").contains("application/json"));
                    response.bodyHandler(body -> {
                        //final Pair<Collection<AgentProjectInfo>,StatusCode> projects = getProjects(body);
                        //context.assertNotNull(projects.getKey().size()>0);
                        context.assertTrue(body.length() > 20000);
                        Collection<AgentProjectInfo> projects = getProjects(body).getResult().getProjects();
                        context.assertTrue(projects.size() > 0);
                        async.complete();
                    });
                })
                .write(json)
                .end();
    }

    private ResultDto<ProjectsDetails, StatusCode> getProjects(Buffer body) {
        String result = body.toString();
        try {
            return new ObjectMapper().readValue(result, new TypeReference<ResultDto<ProjectsDetails, StatusCode>>() {
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getPropertyAsString(Properties prop) {
        StringBuilder sb = new StringBuilder();

        prop.stringPropertyNames().forEach(p -> {
            sb.append(p);
            sb.append(Constants.EQUALS);
            sb.append(prop.getProperty(p));
            sb.append(System.lineSeparator());
        });

        return sb.toString();
    }
}
