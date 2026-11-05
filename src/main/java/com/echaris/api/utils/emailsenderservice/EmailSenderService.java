package com.echaris.api.utils.emailsenderservice;

import java.util.Map;

public interface EmailSenderService {

    void sendTemplateEmailAfterCommit(
            String recipientEmail,
            String subject,
            String templateName,
            Map<String, String> variables
    );

    void sendTemplateEmail(
            String recipientEmail,
            String subject,
            String templateName,
            Map<String, String> variables
    );

    void sendHtmlEmail(
            String senderEmail,
            String recipientEmail,
            String subject,
            String body
    );

    void sendEmail(
            String senderEmail,
            String recipientEmail,
            String subject,
            String body
    );

    void sendAccountActivationEmail(
            String emailAddress,
            String id,
            String fullName,
            String otp
    );

    void sendPasswordChangeEmail(
            String emailAddress,
            String userId,
            String otp,
            String fullName
    );

    void sendPasswordResetEmail(
            String emailAddress,
            String userId,
            String otp,
            String fullName
    );

    void sendBuyerPasswordResetEmail(
            String emailAddress,
            String userId,
            String otp,
            String fullName
    );

    void sendAdminPasswordResetEmail(
            String emailAddress,
            String userId,
            String otp,
            String fullName
    );

    void sendAccountSetupCompletionEmail(
            String emailAddress,
            String fullName
    );

    void sendAdminAccountActivationEmail(
            String emailAddress,
            String fullName,
            String password,
            String otp
    );

    void sendDirectorAccountActivationEmail(
            String emailAddress,
            String id,
            String otp
    );
}