package com.echaris.api.userauthmgt.user;


import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/us-s/users")
public class UserController {

    private final UserService userService;




    


}
