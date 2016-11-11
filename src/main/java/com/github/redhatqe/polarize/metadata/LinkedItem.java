package com.github.redhatqe.polarize.metadata;

/**
 * Created by stoner on 11/10/16.
 */
public @interface LinkedItem {
    String workitemId();
    boolean suspect() default false;
    DefTypes.Role role();
    DefTypes.Project project();
    String revision() default "";
    // Leaving off status and assignees.  Don't want to assign a status here, nor does assignee make sense
}
