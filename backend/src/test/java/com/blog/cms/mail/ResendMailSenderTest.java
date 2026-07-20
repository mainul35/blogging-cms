package com.blog.cms.mail;

import com.blog.cms.model.MailSettings;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// Real HTTP request/response over loopback via the JDK's own
// com.sun.net.httpserver.HttpServer -- no new test dependency needed, unlike
// SmtpMailSender's GreenMail. Uses the package-private 3-arg send() overload
// (see ResendMailSender) so the real endpoint is never touched.
class ResendMailSenderTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private MailMessage message() {
        return MailMessage.builder().to("reader@example.com").subject("New post").text("Body text").build();
    }

    private MailSettings settings() {
        return MailSettings.builder().fromAddress("noreply@blog.com").resendApiKey("test-api-key").build();
    }

    @Test
    void send_postsExpectedPayloadAndAuthHeader() throws Exception {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/emails", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        StepVerifier.create(ResendMailSender.send(message(), settings(), baseUrl)).verifyComplete();

        assertThat(capturedPath.get()).isEqualTo("/emails");
        assertThat(capturedAuth.get()).isEqualTo("Bearer test-api-key");
        assertThat(capturedBody.get()).contains("\"to\":\"reader@example.com\"")
                .contains("\"from\":\"noreply@blog.com\"")
                .contains("\"subject\":\"New post\"");
    }

    @Test
    void send_includesReplyToWhenSet() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/emails", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        MailSettings settingsWithReplyTo = MailSettings.builder()
                .fromAddress("noreply@blog.com").resendApiKey("key").replyTo("owner@blog.com").build();

        StepVerifier.create(ResendMailSender.send(message(), settingsWithReplyTo, baseUrl)).verifyComplete();

        assertThat(capturedBody.get()).contains("\"reply_to\":\"owner@blog.com\"");
    }

    @Test
    void send_serverErrorResponse_propagatesAsMonoError() {
        server.createContext("/emails", exchange -> {
            byte[] body = "{\"error\":\"invalid api key\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        StepVerifier.create(ResendMailSender.send(message(), settings(), baseUrl))
                .expectError()
                .verify();
    }
}
