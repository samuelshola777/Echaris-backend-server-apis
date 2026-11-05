package com.echaris.api.utils.exception;

public class InvalidInputException extends RuntimeException{

    private byte[] errorData;

    public InvalidInputException(String message){
        super(message);
    }

    public InvalidInputException(String message, byte[] errorData){
        super(message);
        this.errorData = errorData;
    }

    public byte[] getErrorData() {
        return errorData;
    }
}
