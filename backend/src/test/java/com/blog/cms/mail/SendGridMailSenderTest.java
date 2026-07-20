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

class SendGridMailSenderTest {

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
        return MailSettings.builder().fromAddress("noreply@blog.com").sendgridApiKey("test-api-key").build();
    }

    @Test
    void send_postsExpectedPayloadAndAuthHeader() throws Exception {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v3/mail/send", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1); // SendGrid's real API returns 202 with no body
            exchange.close();
        });

        StepVerifier.create(SendGridMailSender.send(message(), settings(), baseUrl)).verifyComplete();

        assertThat(capturedPath.get()).isEqualTo("/v3/mail/send");
        assertThat(capturedAuth.get()).isEqualTo("Bearer test-api-key");
        assertThat(capturedBody.get()).contains("\"email\":\"reader@example.com\"")
                .contains("\"email\":\"noreply@blog.com\"")
                .contains("\"subject\":\"New post\"");
    }

    @Test
    void send_includesReplyToWhenSet() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v3/mail/send", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        MailSettings settingsWithReplyTo = MailSettings.builder()
                .fromAddress("noreply@blog.com").sendgridApiKey("key").replyTo("owner@blog.com").build();

        StepVerifier.create(SendGridMailSender.send(message(), settingsWithReplyTo, baseUrl)).verifyComplete();

        assertThat(capturedBody.get()).contains("\"reply_to\":{\"email\":\"owner@blog.com\"}");
    }

    @Test
    void send_serverErrorResponse_propagatesAsMonoError() {
        server.createContext("/v3/mail/send", exchange -> {
            byte[] body = "{\"errors\":[{\"message\":\"invalid api key\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        StepVerifier.create(SendGridMailSender.send(message(), settings(), baseUrl))
                .expectError()
                .verify();
    }
}
