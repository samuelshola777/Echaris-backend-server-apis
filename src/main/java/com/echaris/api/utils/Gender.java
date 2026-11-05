package com.echaris.api.utils;

public enum Gender {
    MALE("Male"),
    FEMALE("Female"),
    NON_BINARY("Non-binary");

    private final String displayValue;

    Gender(String displayValue) {
        this.displayValue = displayValue;
    }

    public String getDisplayValue() {
        return displayValue;
    }
}