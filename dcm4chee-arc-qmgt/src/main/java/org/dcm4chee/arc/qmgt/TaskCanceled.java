package org.dcm4chee.arc.qmgt;

import org.dcm4chee.arc.entity.Task;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class TaskCanceled {

    public final Task task;

    public TaskCanceled(Task task) {
        this.task = task;
    }
}
