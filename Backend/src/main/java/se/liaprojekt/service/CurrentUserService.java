package se.liaprojekt.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class CurrentUserService {

    // =========================
    // GET ENTRA ID (OID FROM JWT)
    // =========================
    public String getEntraId() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (!(auth instanceof JwtAuthenticationToken jwt)) {
            throw new IllegalStateException("No JWT authentication found");
        }

        String oid = jwt.getToken().getClaimAsString("oid");

        if (oid == null) {
            throw new IllegalStateException("OID claim missing in token");
        }

        return oid;
    }

    public Set<String> getRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (!(auth instanceof JwtAuthenticationToken jwt)) {
            throw new IllegalStateException("No JWT authentication found");
        }

        List<String> rolesList = jwt.getToken().getClaim("roles");
        Set<String> roles = Set.copyOf(rolesList);

        if (roles == null) {
            throw new IllegalStateException("Roles claim missing in token");
        }

        return roles;
    }

    public String getName() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (!(auth instanceof JwtAuthenticationToken jwt)) {
            throw new IllegalStateException("No JWT authentication found");
        }

        String name = jwt.getToken().getClaimAsString("name");

        if (name == null) {
            throw new IllegalStateException("Name claim missing in token");
        }

        return name;
    }

//    public String getEntraId() {
//
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//
//        // =========================
//        // DEV FALLBACK
//        // =========================
//        if (auth == null || auth.getName() == null || auth.getName().equals("anonymousUser")) {
//            return "dev-user-1";
//        }
//
//        return auth.getName();
//    }
}