package com.echaris.api.utils.exception;

public class ProviderPaymentNotFoundException
        extends InvalidOperationException {

    public ProviderPaymentNotFoundException(String provider) {
        super(provider + " transaction reference was not found");
    }
}