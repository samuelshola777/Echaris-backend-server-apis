package com.echaris.api.utils.emailsenderservice;

import com.echaris.api.utils.exception.ErrorProcessingRequestException;
import com.echaris.api.utils.exception.InvalidInputException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service("mailgunEmailSenderServiceImpl")
public class MailgunEmailSenderServiceImpl
        implements EmailSenderService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    MailgunEmailSenderServiceImpl.class
            );

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final TaskExecutor mailExecutor;
    private final String configuredSender;
    private final boolean mailEnabled;

    public MailgunEmailSenderServiceImpl(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            @Qualifier("applicationTaskExecutor")
            TaskExecutor mailExecutor,
            @Value("${app.mail-from:}")
            String configuredSender,
            @Value("${app.mail-enabled:false}")
            boolean mailEnabled
    ) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.mailExecutor = mailExecutor;
        this.configuredSender = configuredSender;
        this.mailEnabled = mailEnabled;
    }

    @Override
    public void sendTemplateEmailAfterCommit(
            String recipientEmail,
            String subject,
            String templateName,
            Map<String, String> variables
    ) {
        Map<String, String> values = variables == null
                ? Map.of()
                : new LinkedHashMap<>(variables);

        Runnable send = () -> {
            try {
                sendTemplateEmail(
                        recipientEmail,
                        subject,
                        templateName,
                        values
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "Template email failed: template={}, errorType={}",
                        templateName,
                        exception.getClass().getSimpleName()
                );
            }
        };

        Runnable dispatch = () -> {
            try {
                mailExecutor.execute(send);
            } catch (RuntimeException exception) {
                log.warn(
                        "Template email could not be scheduled: template={}, errorType={}",
                        templateName,
                        exception.getClass().getSimpleName()
                );
            }
        };

        if (TransactionSynchronizationManager
                .isActualTransactionActive()
                && TransactionSynchronizationManager
                .isSynchronizationActive()) {

            TransactionSynchronizationManager
                    .registerSynchronization(
                            new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    dispatch.run();
                                }
                            }
                    );

        } else {
            dispatch.run();
        }
    }

    @Override
    public void sendTemplateEmail(
            String recipientEmail,
            String subject,
            String templateName,
            Map<String, String> variables
    ) {
        if (isBlank(recipientEmail)
                || isBlank(subject)
                || subject.length() > 200
                || hasLineBreak(recipientEmail)
                || hasLineBreak(subject)
                || templateName == null
                || !templateName.matches("[A-Za-z0-9_-]{1,100}")) {

            throw new InvalidInputException(
                    "Valid email recipient, subject and template are required"
            );
        }

        Context context = new Context();

        if (variables != null) {
            variables.forEach(context::setVariable);
        }

        String html = templateEngine.process(
                templateName,
                context
        );

        sendHtmlEmail(
                configuredSender,
                recipientEmail.trim(),
                subject.trim(),
                html
        );
    }

    @Override
    public void sendHtmlEmail(
            String senderEmail,
            String recipientEmail,
            String subject,
            String body
    ) {
        deliver(
                senderEmail,
                recipientEmail,
                subject,
                body,
                true
        );
    }

    @Override
    public void sendEmail(
            String senderEmail,
            String recipientEmail,
            String subject,
            String body
    ) {
        deliver(
                senderEmail,
                recipientEmail,
                subject,
                body,
                false
        );
    }

    @Override
    public void sendAccountActivationEmail(
            String emailAddress,
            String id,
            String fullName,
            String otp
    ) {
        sendCodeEmail(
                emailAddress,
                fullName,
                otp,
                "Verify your Echaris account",
                "Enter this code in the app to verify your email address."
        );
    }

    @Override
    public void sendPasswordChangeEmail(
            String emailAddress,
            String userId,
            String otp,
            String fullName
    ) {
        sendCodeEmail(
                emailAddress,
                fullName,
                otp,
                "Confirm your Echaris password change",
                "Enter this code in the app to confirm your password change."
        );
    }

    @Override
    public void sendPasswordResetEmail(
            String emailAddress,
            String userId,
            String otp,
            String fullName
    ) {
        sendCodeEmail(
                emailAddress,
                fullName,
                otp,
                "Reset your Echaris password",
                "Enter this code in the app to reset your password."
        );
    }

    @Override
    public void sendBuyerPasswordResetEmail(
            String emailAddress,
            String userId,
            String otp,
            String fullName
    ) {
        sendPasswordResetEmail(
                emailAddress,
                userId,
                otp,
                fullName
        );
    }

    @Override
    public void sendAdminPasswordResetEmail(
            String emailAddress,
            String userId,
            String otp,
            String fullName
    ) {
        sendCodeEmail(
                emailAddress,
                fullName,
                otp,
                "Reset your Echaris admin password",
                "Enter this code in the admin portal to reset your password."
        );
    }

    @Override
    public void sendAccountSetupCompletionEmail(
            String emailAddress,
            String fullName
    ) {
        String body = emailBody(
                fullName,
                "Your account is ready",
                "Your Echaris account setup is complete. You can now sign in.",
                null
        );

        sendHtmlEmail(
                configuredSender,
                emailAddress,
                "Welcome to Echaris",
                body
        );
    }

    @Override
    public void sendAdminAccountActivationEmail(
            String emailAddress,
            String fullName,
            String password,
            String otp
    ) {
        // Preserve the existing signature; never email passwords.
        sendCodeEmail(
                emailAddress,
                fullName,
                otp,
                "Verify your Echaris admin account",
                "Enter this code in the admin portal to verify your email address."
        );
    }

    @Override
    public void sendDirectorAccountActivationEmail(
            String emailAddress,
            String id,
            String otp
    ) {
        sendAccountActivationEmail(
                emailAddress,
                id,
                null,
                otp
        );
    }

    private void sendCodeEmail(
            String recipient,
            String fullName,
            String otp,
            String title,
            String instruction
    ) {
        if (isBlank(otp)) {
            throw new ErrorProcessingRequestException(
                    "Email verification code is missing"
            );
        }

        sendHtmlEmail(
                configuredSender,
                recipient,
                title,
                emailBody(
                        fullName,
                        title,
                        instruction,
                        otp
                )
        );
    }

    private void deliver(
            String sender,
            String recipient,
            String subject,
            String body,
            boolean html
    ) {
        String from = isBlank(sender)
                ? configuredSender
                : sender;

        if (isBlank(from)
                || isBlank(recipient)
                || isBlank(subject)
                || body == null
                || hasLineBreak(from)
                || hasLineBreak(recipient)
                || hasLineBreak(subject)) {

            throw new ErrorProcessingRequestException(
                    "Invalid email configuration or message"
            );
        }

        try {
            InternetAddress fromAddress =
                    new InternetAddress(from.trim(), true);

            InternetAddress toAddress =
                    new InternetAddress(recipient.trim(), true);

            fromAddress.validate();
            toAddress.validate();

            MimeMessage message =
                    mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message,
                            false,
                            StandardCharsets.UTF_8.name()
                    );

            helper.setValidateAddresses(true);
            helper.setFrom(fromAddress);
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(body, html);

            mailSender.send(message);

        } catch (
                MessagingException
                | MailException
                | IllegalArgumentException exception
        ) {
            log.warn(
                    "Email delivery failed: {}",
                    exception.getClass().getSimpleName()
            );

            throw new ErrorProcessingRequestException(
                    "Unable to send email. Please try again later."
            );
        }
    }

    private String emailBody(
            String fullName,
            String title,
            String instruction,
            String otp
    ) {
        Context context = new Context();

        context.setVariable(
                "name",
                isBlank(fullName) ? "there" : fullName.trim()
        );

        context.setVariable("title", title);
        context.setVariable("instruction", instruction);
        context.setVariable("otp", otp);

        return templateEngine.process(
                "email-message",
                context
        );
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0;
    }
}