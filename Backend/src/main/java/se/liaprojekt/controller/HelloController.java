package se.liaprojekt.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
public class HelloController {
    @GetMapping("admin")
    @ResponseBody
    @PreAuthorize("hasAuthority('APPROLE_Participant')")
    public String Participant(Principal principal) {
        return "Participant message: " + principal.getName();
    }
}
