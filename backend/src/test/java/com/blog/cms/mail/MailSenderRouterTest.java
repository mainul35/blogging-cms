package com.blog.cms.mail;

import com.blog.cms.model.MailSettings;
import com.blog.cms.repository.MailSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

// NOTE on scope: only the branches of MailSenderRouter.dispatch() itself are
// exercised here (no DB row -> Log fallback, "log" provider -> Log, an
// unrecognized provider value -> Log fallback). The actual protocol-level
// behavior of each provider is covered separately, at the point where each
// sender's endpoint became testable: SmtpMailSenderTest (GreenMail, a real
// local SMTP server -- SmtpMailSender already takes host/port from the
// MailSettings row it's handed, so no production change was needed) and
// ResendMailSenderTest/SendGridMailSenderTest (a local JDK HttpServer, via
// the package-private send(message, settings, baseUrl) overload added to
// each of those senders specifically to make this possible -- the public
// 2-arg overload still always hits the real endpoint). dispatch() itself
// still calls those senders' real 2-arg overloads directly (not through an
// injectable interface), so exercising the smtp/resend/sendgrid *branches*
// of dispatch would mean this test hitting the real endpoints again -- that
// remains out of scope here on purpose; the per-sender tests already cover
// what those branches would delegate to.
@ExtendWith(MockitoExtension.class)
class MailSenderRouterTest {

    @Mock private MailSettingsRepository mailSettingsRepository;

    private MailSenderRouter router;

    @BeforeEach
    void setUp() {
        router = new MailSenderRouter(mailSettingsRepository);
    }

    private MailMessage message() {
        return MailMessage.builder().to("reader@example.com").subject("Hi").text("Body").build();
    }

    @Test
    void send_noSettingsRow_fallsBackToLogSender_doesNotError() {
        when(mailSettingsRepository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(router.send(message())).verifyComplete();
    }

    @Test
    void send_logProvider_routesToLogSender_doesNotError() {
        MailSettings settings = MailSettings.builder().id(1L).provider("log").build();
        when(mailSettingsRepository.findById(1L)).thenReturn(Mono.just(settings));

        StepVerifier.create(router.send(message())).verifyComplete();
    }

    @Test
    void send_unrecognizedProviderValue_fallsBackToLogSenderRatherThanErroring() {
        MailSettings settings = MailSettings.builder().id(1L).provider("some-future-provider").build();
        when(mailSettingsRepository.findById(1L)).thenReturn(Mono.just(settings));

        StepVerifier.create(router.send(message())).verifyComplete();
    }
}
