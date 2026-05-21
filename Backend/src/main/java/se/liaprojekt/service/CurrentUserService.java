package se.liaprojekt.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    // =========================
    // GET ENTRA ID (OID FROM JWT)
    // =========================
    public String getEntraId() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth instanceof JwtAuthenticationToken jwt)) {
            throw new IllegalStateException("No JWT authentication found");
        }

        String oid = jwt.getToken().getClaimAsString("oid");

        if (oid == null) {
            throw new IllegalStateException("OID claim missing in token");
        }

        return oid;
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