package se.liaprojekt.controller.util;

public class Roles {
    public static final String ADMIN="Admin";
    public static final String COURSE_ADMIN="CourseAdmin";
    public static final String PARTICIPANT="Participant";

    public static final String ROLE_ADMIN ="hasRole('" + ADMIN + "')";
    public static final String ROLE_COURSE_ADMIN ="hasRole('" + COURSE_ADMIN + "')";
    public static final String ROLE_PARTICIPANT ="hasRole('" + PARTICIPANT + "')";
    public static final String ANY_ROLE_ADMIN_COURSE_ADMIN ="hasAnyRole('" + ADMIN + "','" + COURSE_ADMIN + "')";


}
