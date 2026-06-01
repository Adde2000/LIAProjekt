package se.liaprojekt.controller.util;

public class Roles {
    public static final String ADMIN="hasRole('Admin')";
    public static final String COURSE_ADMIN="hasRole('Course_Admin')";
    public static final String PARTICIPANT="hasRole('Participant')";
    public static final String ADMIN_OR_COURSE_ADMIN="hasAnyRole('Admin','Course_Admin')";
}
