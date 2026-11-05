package com.echaris.api.utils.exception;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ResourceNotFoundException extends RuntimeException{

    private boolean return404;
    private boolean paginated;

    public ResourceNotFoundException(String message){
        super(message);
        this.return404 = true;
        paginated = true;
    }

    public ResourceNotFoundException(String message, boolean return404){
        super(message);
        this.return404 = return404;
        paginated = true;
    }

    public ResourceNotFoundException(String message, boolean return404, boolean paginated){
        super(message);
        this.return404 = return404;
        this.paginated = paginated;
    }
}
