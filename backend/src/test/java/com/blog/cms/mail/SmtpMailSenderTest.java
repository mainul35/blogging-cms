package com.blog.cms.mail;

import com.blog.cms.model.MailSettings;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

// Real SMTP protocol exchange against an in-JVM fake server -- no production
// code changes needed here, unlike the Resend/SendGrid senders: SmtpMailSender
// already takes host/port from the MailSettings row it's handed, so this just
// points those fields at GreenMail's bound test server instead of a real one.
class SmtpMailSenderTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetup.SMTP.dynamicPort());

    private MailMessage message() {
        return MailMessage.builder().to("reader@example.com").subject("New post").text("Body text").build();
    }

    private MailSettings.MailSettingsBuilder settingsBuilder() {
        return MailSettings.builder()
                .fromAddress("noreply@blog.com")
                .smtpHost("127.0.0.1")
                .smtpPort(greenMail.getSmtp().getPort())
                .smtpAuth(false)
                .smtpStarttls(false);
    }

    @Test
    void send_deliversMessageWithExpectedFromToSubjectAndBody() throws Exception {
        StepVerifier.create(SmtpMailSender.send(message(), settingsBuilder().build())).verifyComplete();

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getFrom()[0].toString()).isEqualTo("noreply@blog.com");
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("reader@example.com");
        assertThat(received[0].getSubject()).isEqualTo("New post");
        assertThat(GreenMailUtil.getBody(received[0])).contains("Body text");
    }

    @Test
    void send_replyToFromMessageOverridesSettingsReplyTo() throws Exception {
        MailMessage messageWithReplyTo = MailMessage.builder()
                .to("reader@example.com").subject("New post").text("Body text").replyTo("message-reply@blog.com")
                .build();
        MailSettings settings = settingsBuilder().replyTo("settings-reply@blog.com").build();

        StepVerifier.create(SmtpMailSender.send(messageWithReplyTo, settings)).verifyComplete();

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage received = greenMail.getReceivedMessages()[0];
        assertThat(received.getReplyTo()[0].toString()).isEqualTo("message-reply@blog.com");
    }

    @Test
    void send_noReplyToAnywhere_omitsReplyToHeader() throws Exception {
        StepVerifier.create(SmtpMailSender.send(message(), settingsBuilder().build())).verifyComplete();

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage received = greenMail.getReceivedMessages()[0];
        // Checking the raw header, not MimeMessage.getReplyTo() -- JavaMail's
        // getReplyTo() falls back to the From address when no Reply-To header
        // is present at all, which would make an isEmpty() assertion on it
        // fail even though SmtpMailSender correctly never set one.
        assertThat(received.getHeader("Reply-To")).isNull();
    }
}
