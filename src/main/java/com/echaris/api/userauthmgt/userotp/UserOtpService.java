package com.echaris.api.userauthmgt.userotp;



import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.InvalidPasswordException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service

public class UserOtpService {

    private final UserOtpRepository userOtpRepository;
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss");
    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH);

    public UserOtpService(UserOtpRepository userOtpRepository) {
        this.userOtpRepository = userOtpRepository;
    }


    private void updateUserOtp(UserOtp userOtp) {
        userOtp.setUpdatedAt(LocalDateTime.now());
        userOtpRepository.save(userOtp);
    }

    /**
     * Saves or updates a User OTP (One-Time Password) in the repository.
     * @param newUserOtp the UserOtp object containing the user's ID, OTP type, OTP value,
     *                   expiration time, and other details to be saved or updated.
     */
    public void saveUserOtp(UserOtp newUserOtp){
        Optional<UserOtp> existingUserOtp = userOtpRepository.
                findByUserIdAndUserOtpType(newUserOtp.getUserId(), newUserOtp.getUserOtpType());

        if(existingUserOtp.isEmpty()){
            userOtpRepository.save(newUserOtp);
            return;
        }

        existingUserOtp.get().setOtp(newUserOtp.getOtp());
        existingUserOtp.get().setExpiresAt(newUserOtp.getExpiresAt());
        existingUserOtp.get().setUpdatedAt(
                LocalDateTime.parse(dateTimeFormatter.format(LocalDateTime.now()),dateTimeFormatter)
        );
        existingUserOtp.get().setActive(true);

        userOtpRepository.save(existingUserOtp.get());
    }

    /**
     * Generates and saves a One-Time Password (OTP)
     * @param userId the ID of the user for whom the OTP is being generated.
     * @return the plain text OTP (not encrypted) that can be used for user 2-fa.
     */
    public Map<String, String> generateOtpForUser(String userId, UserOtpType otpType){

        String otp = generateOtp(6);
        LocalDateTime expiresAt;


        System.out.println(otp);
        switch(otpType){
            case ACCOUNT_ACTIVATION -> expiresAt = LocalDateTime.now().plusDays(30);
            case LOGIN -> expiresAt = LocalDateTime.now().plusMinutes(10);
            case PASSWORD_RESET -> expiresAt = LocalDateTime.now().plusHours(1);
            default -> throw new InvalidInputException("Invalid OTP Type");
        }
        UserOtp userOtp = new UserOtp(
                userId,
                passwordEncoder.encode(otp),
                LocalDateTime.parse(dateTimeFormatter.format(expiresAt), dateTimeFormatter),
                otpType,
                true
        );
        saveUserOtp(userOtp);

        return Map.of(
                "otp", otp,
                "expiresAt", formatter.format(expiresAt)
        );
    }

    /**
     * Invalidates the OTP for a given user and OTP type.
     *
     * @param userId the ID of the user whose OTP should be invalidated
     * @param otpType the type of OTP to invalidate
     * @throws ResourceNotFoundException if no OTP exists for the given user and type
     * @throws InvalidPasswordException if the OTP is invalid or expired
     */
    public void destroyOtpForUser(String userId, UserOtpType otpType){

        UserOtp existingUserOtp = userOtpRepository.findByUserIdAndUserOtpType(userId, otpType)
                .orElseThrow(()-> new ResourceNotFoundException("Invalid OTP"));

        checkIfOtpIsValid(existingUserOtp);
        destroyOtp(existingUserOtp);
    }

    public String generatePasswordForUser(){

        String otp = generateOtp(10);
//        LocalDateTime expiresAt;
//        switch(otpType){
//            case ACCOUNT_ACTIVATION -> expiresAt = LocalDateTime.now().plusDays(7);
//            case LOGIN -> expiresAt = LocalDateTime.now().plusMinutes(10);
//            case PASSWORD_RESET -> expiresAt = LocalDateTime.now().plusHours(1);
//            default -> throw new InvalidInputException("Invalid OTP Type");
//        }
//        UserOtp userOtp = new UserOtp(
//                userId,
//                passwordEncoder.encode(otp),
//                LocalDateTime.parse(dateTimeFormatter.format(expiresAt), dateTimeFormatter),
//                otpType,
//                true
//        );
//        saveUserOtp(userOtp);
        return otp;
    }


    /**
     * Validates a user's OTP (One-Time Password) based on the provided parameters.
     *
     * @param otp the OTP provided by the user
     * @param userId the unique identifier of the user
     * @param otpType the type of OTP (e.g., LOGIN, PASSWORD_RESET)
     * @param destroyOtp a boolean indicating whether the OTP should be invalidated after successful validation
     *
     * @throws ResourceNotFoundException if no OTP exists for the given user and type
     * @throws InvalidPasswordException if the provided OTP does not match or has expired
     */
    public void validateOtp(String otp, String userId, UserOtpType otpType, boolean destroyOtp){

        UserOtp existingUserOtp = userOtpRepository.findByUserIdAndUserOtpType(userId, otpType)
                    .orElseThrow(()-> new ResourceNotFoundException("Invalid OTP"));

        if(passwordEncoder.matches(otp, existingUserOtp.getOtp())){
            checkIfOtpIsValid(existingUserOtp);
            if(destroyOtp) {
                destroyOtp(existingUserOtp);
            }
        }else{
            System.out.println("jn fvka");
            throw new InvalidPasswordException("Invalid OTP");
        }
    }



    private String generateOtp(int length){
        StringBuilder stringBuilder = new StringBuilder();
        String characters = "0123456789";
        while(stringBuilder.length()<length) {
            Random random = new Random();
            stringBuilder.append(characters.charAt(random.nextInt(characters.length())));
        }
        return stringBuilder.toString();
    }

    private void destroyOtp(UserOtp userOtp) {
        userOtp.setActive(false);
        updateUserOtp(userOtp);
    }

    private static void checkIfOtpIsValid(UserOtp userOtp) {
        if((userOtp.getExpiresAt().isBefore(LocalDateTime.now())) || (userOtp.getExpiresAt().isEqual(LocalDateTime.now()))) {
            throw new InvalidPasswordException("OTP expired please click Resend OTP");
        }
        if(!userOtp.isActive()){
            throw new InvalidPasswordException("Invalid Otp");
        }
    }



}
