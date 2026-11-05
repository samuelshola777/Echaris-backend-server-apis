    package com.echaris.api.utils;

public interface ApiResponseMessages {

    // User-related messages
    String ERROR_USER_INVALID_ACCOUNT = "Your account appears to be invalid. Please contact support for assistance.";
    String ERROR_USER_NOT_FOUND = "Error fetching user information.";
    String ERROR_USER_ACCOUNT_BLOCKED = "Your account has been temporarily blocked. Please contact our support team for help.";
    String ERROR_USER_ACCOUNT_NOT_VERIFIED = "Your account hasn't been verified yet. Please check your email for the activation link.";
    String ERROR_USER_INVALID_EMAIL_OR_PASSWORD = "The email or password you entered doesn't match.";
    String ERROR_USER_INCORRECT_OLD_PASSWORD = "The current password you entered is incorrect. Please try again.";
    String ERROR_USER_SAME_PASSWORD = "The new password cannot be the same as the current password.";
    String ERROR_USER_EMAIL_ALREADY_EXISTS = "This email is already in use.";
    String ERROR_ACCOUNT_ALREADY_IN_DESIRED_STATE =   "account already in desired status";
    String ERROR_USER_ACCOUNT_NOT_ACTIVE = "Your account is not active. Please contact our support team for help.";
   


}
