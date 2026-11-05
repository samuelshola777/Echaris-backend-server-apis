package com.echaris.api.utils.exception;



import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.appsecurity.ApiResponse;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class CustomExceptionHandler {

    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxFileSize;

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse> handleJsonParseError(HttpMessageNotReadableException ex) {
        String message = "Invalid JSON request. A required field might be missing or incorrectly formatted ("+ex.getMessage()+")";
        return new ResponseEntity<>(new ApiResponse(false,message,null), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        return new ResponseEntity<>(new ApiResponse(false,"File Size Must Be Less Than "+maxFileSize,null), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public final ResponseEntity<ApiResponse> handleValidationException(MethodArgumentNotValidException ex) {
        return new ResponseEntity<>(new ApiResponse(false,ex.getMessage(),null), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public final ResponseEntity<ApiResponse> handleMissingRequestParameterException(IllegalArgumentException ex) {
        return new ResponseEntity<>(new ApiResponse(false,ex.getMessage(),null), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadRequestException.class)
    public final ResponseEntity<ApiResponse> handleBadRequestException(BadRequestException ex) {
        return new ResponseEntity<>(new ApiResponse(false,ex.getMessage(),null), HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(ResourceExistsException.class)
    public final ResponseEntity<ApiResponse> handleResourceExistsException(ResourceExistsException exception){
        return new ResponseEntity<>(new ApiResponse(false,exception.getMessage(),null), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public final ResponseEntity<ApiResponse> handleResourceNotFoundException(ResourceNotFoundException exception){
        if(exception.isReturn404()) {
            return new ResponseEntity<>(new ApiResponse(false, exception.getMessage(), null), HttpStatus.NOT_FOUND);
        }

        if(exception.isPaginated()) {
            return ResponseEntity.ok(new ApiResponse(
                    true,
                    exception.getMessage(),
                    new PaginatedData(0, 0, 0, new Object[0])
            ));
        }else{
            return ResponseEntity.ok(new ApiResponse(
                    true,
                    exception.getMessage(),
                    null
            ));
        }
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public final ResponseEntity<ApiResponse> handlePasswordMismatchException(InvalidPasswordException exception){
        return new ResponseEntity<>(new ApiResponse(false,exception.getMessage(),null), HttpStatus.UNPROCESSABLE_ENTITY);
    }
    @ExceptionHandler(UnauthorizedException.class)
    public final ResponseEntity<ApiResponse> handleUnauthorizedException(UnauthorizedException exception){
        return new ResponseEntity<>(new ApiResponse(false,exception.getMessage(),null), HttpStatus.UNAUTHORIZED);
    }
    @ExceptionHandler(InvalidOperationException.class)
    public final ResponseEntity<ApiResponse> handleInvalidOperationException(InvalidOperationException exception){
        return new ResponseEntity<>(new ApiResponse(false,exception.getMessage(),null), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InvalidInputException.class)
    public final ResponseEntity<ApiResponse> handleInvalidInputException(InvalidInputException exception){
        if(exception.getErrorData()==null) {
            return new ResponseEntity<>(new ApiResponse(false, exception.getMessage(), null), HttpStatus.BAD_REQUEST);
        }else {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
//                    .header("Content-Type", "text/plain")
                    .header("Content-Disposition", "attachment; filename=\"errorReport.txt\"")
                    .body(new ApiResponse(false, exception.getMessage(), exception.getErrorData()));
        }
    }

    @ExceptionHandler(ErrorProcessingRequestException.class)
    public final ResponseEntity<ApiResponse> handleErrorProcessingRequestException(ErrorProcessingRequestException exception){
        return new ResponseEntity<>(new ApiResponse(false,exception.getMessage(),null), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public final ResponseEntity<ApiResponse> handleUnsupportedOperationException(UnsupportedOperationException exception){
        return new ResponseEntity<>(new ApiResponse(false,exception.getMessage(),null), HttpStatus.NOT_ACCEPTABLE);
    }



//    @ExceptionHandler(AccountNotActivatedException.class)
//    public final ResponseEntity<CustomApiResponse> handleAccountNotActivatedException(AccountNotActivatedException exception){
//        return new ResponseEntity<>(new CustomApiResponse(exception.getMessage(),new CustomerProfileDAO(
//                null,null,false, exception.accountStatus.equals(AccountStatus.ACTIVATED),exception.accountStatus.equals(AccountStatus.BLOCKED)
//        ,null,null,null,null,null)), HttpStatus.UNAUTHORIZED);
//    }
//
//
    ////    @ExceptionHandler(ExpiredJwtException.class)
    ////    public final ResponseEntity<CustomApiResponse> handleExpiredJWTException(){
    ////        return new ResponseEntity<>(new CustomApiResponse("Expired Jwt"), HttpStatus.FORBIDDEN);
    ////    }
//
//    @ExceptionHandler(ErrorProcessingFileException.class)
//    public final ResponseEntity<CustomApiResponse> errorProcessingFileException(ErrorProcessingFileException exception,  Object data){
//        return new ResponseEntity<>(new CustomApiResponse("error processing image: "+exception.getMessage()), HttpStatus.FORBIDDEN);
//    }
//
//    @ExceptionHandler(ErrorReadingDataFromFileException.class)
//    public final ResponseEntity<CustomApiResponse> errorReadingDataFromFileException(ErrorReadingDataFromFileException exception){
//        return new ResponseEntity<>(new CustomApiResponse("error reading file: "+exception.getMessage(), exception.getErrorInformation()), HttpStatus.FORBIDDEN);
//    }
//
//    @ExceptionHandler(InvalidLoginTypeException.class)
//    public final ResponseEntity<CustomApiResponse> handleInvalidLoginTypeException(InvalidLoginTypeException exception){
//        return new ResponseEntity<>(new CustomApiResponse(exception.getMessage()), HttpStatus.BAD_REQUEST);
//    }
//    @ExceptionHandler(InvalidRequestException.class)
//    public final ResponseEntity<CustomApiResponse> handleInvalidRequestException(InvalidRequestException exception){
//        return new ResponseEntity<>(new CustomApiResponse(exception.getMessage()), HttpStatus.BAD_REQUEST);
//    }
//    @ExceptionHandler(OTPExpiredException.class)
//    public final ResponseEntity<CustomApiResponse> handleOTPExpiredException(OTPExpiredException exception){
//        return new ResponseEntity<>(new CustomApiResponse(exception.getMessage()), HttpStatus.NOT_ACCEPTABLE);
//    }
//
//    @ExceptionHandler(ErrorFetchingData.class)
//    public final ResponseEntity<CustomApiResponse> handleErrorFetchingDataException(ErrorFetchingData exception){
//        return new ResponseEntity<>(new CustomApiResponse(exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
//    }


    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse> handleConcurrentChange(org.springframework.dao.OptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(false, "The record changed. Reload before trying again", null));
    }
}