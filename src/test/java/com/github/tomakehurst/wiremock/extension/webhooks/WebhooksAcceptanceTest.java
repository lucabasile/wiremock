package com.github.tomakehurst.wiremock.extension.webhooks;

import com.github.tomakehurst.wiremock.AcceptanceTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestListener;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.testsupport.TestNotifier;
import com.github.tomakehurst.wiremock.testsupport.WireMockTestClient;
import org.apache.http.entity.StringEntity;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.extension.webhooks.Webhooks.webhook;
import static com.github.tomakehurst.wiremock.http.RequestMethod.POST;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class WebhooksAcceptanceTest extends AcceptanceTestBase {

    static Webhooks webhooks = new Webhooks();

    static CountDownLatch latch;
    static WireMockServer targetServer;
    static TestNotifier notifier = new TestNotifier();

    WireMockTestClient client;

    @ClassRule
    public static WireMockRule wm = new WireMockRule(
        options()
            .dynamicPort()
            .notifier(notifier)
            .extensions(webhooks));



    @BeforeClass
    public static void initClass() {
        targetServer = wireMockServer;

        targetServer.addMockServiceRequestListener(new RequestListener() {
            @Override
            public void requestReceived(Request request, Response response) {
                latch.countDown();
            }
        });

        System.out.println("Target server port: " + targetServer.port());
        System.out.println("Under test server port: " + wm.port());
    }

    @Before
    public void init() {
        reset();
        notifier.reset();
        targetServer.stubFor(any(anyUrl())
            .willReturn(aResponse().withStatus(200)));
        latch = new CountDownLatch(1);
        client = new WireMockTestClient(wm.port());
    }

    @Test
    public void firesASingleWebhookWhenRequested() throws Exception {
        wm.stubFor(post(urlPathEqualTo("/something-async"))
            .willReturn(aResponse().withStatus(200))
            .withPostServeAction("webhook", webhook()
                .withMethod(POST)
                .withUrl("http://localhost:" + wireMockServer.port() + "/callback")
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"result\": \"SUCCESS\" }"))
        );

        verify(0, postRequestedFor(anyUrl()));

        client.post("/something-async", new StringEntity("", TEXT_PLAIN));

        waitForRequestToTargetServer();

        verify(1, postRequestedFor(urlEqualTo("/callback"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("{ \"result\": \"SUCCESS\" }"))
        );

        assertThat(notifier.getInfoMessages(), hasItem(
            containsString("Webhook POST request to http://localhost:" + targetServer.port() + "/callback returned status HTTP/1.1 200 OK")
        ));
    }

    private void waitForRequestToTargetServer() throws Exception {
        latch.await(2, SECONDS);
        assertThat("Timed out waiting for target server to receive a request",
            latch.getCount(), is(0L));
    }

}
