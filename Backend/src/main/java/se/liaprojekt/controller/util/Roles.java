package se.liaprojekt.controller.util;

import se.liaprojekt.exception.BadRequestException;

import java.util.List;
import java.util.Set;

public class Roles {
    public static final String ADMIN="Admin";
    public static final String COURSE_ADMIN="CourseAdmin";
    public static final String PARTICIPANT="Participant";

    public static final Set<String> roles = Set.of(
            ADMIN.toLowerCase(),
            COURSE_ADMIN.toLowerCase(),
            PARTICIPANT.toLowerCase()
    );

    public static final String ROLE_ADMIN ="hasRole('" + ADMIN + "')";
    public static final String ROLE_COURSE_ADMIN ="hasRole('" + COURSE_ADMIN + "')";
    public static final String ROLE_PARTICIPANT ="hasRole('" + PARTICIPANT + "')";
    public static final String ANY_ROLE_ADMIN_COURSE_ADMIN ="hasAnyRole('" + ADMIN + "','" + COURSE_ADMIN + "')";

    public static void checkRolesValid(List<String> rolesToCheck) {
        for (String role : rolesToCheck) {
            if (!roles.contains(role.toLowerCase())) {
                throw new BadRequestException("Invalid role: " + role);
            }
        }
    }
}
