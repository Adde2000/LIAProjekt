package se.liaprojekt.controller.util;

public class Roles {
    public static final String ROLE_ADMIN ="hasRole('Admin')";
    public static final String ROLE_COURSE_ADMIN ="hasRole('Course_Admin')";
    public static final String ROLE_PARTICIPANT ="hasRole('Participant')";
    public static final String ANY_ROLE_ADMIN_COURSE_ADMIN ="hasAnyRole('Admin','Course_Admin')";

    public static final String ADMIN="Admin";
    public static final String COURSE_ADMIN="Course_Admin";
    public static final String PARTICIPANT="Participant";
}
