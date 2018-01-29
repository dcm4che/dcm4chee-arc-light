package org.dcm4chee.arc.event;

import org.dcm4chee.arc.entity.QueueMessage;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2017
 */

public class QueueMessageEvent {
    private final HttpServletRequest request;
    private String[] filters = {};
    private QueueMessage queueMsg;
    private long count;
    private Exception exception;
    private Type type;

    public enum Type {
        CancelTask,
        CancelTasks,
        RescheduleTask,
        RescheduleTasks,
        DeleteTask,
        DeleteTasks
    }

    public QueueMessageEvent(HttpServletRequest request, Type type) {
        this.request = request;
        this.type = type;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public Type getType() {
        return type;
    }

    public String[] getFilters() {
        return filters;
    }

    public void setFilters(String[] filters) {
        this.filters = filters;
    }

    public QueueMessage getQueueMsg() {
        return queueMsg;
    }

    public void setQueueMsg(QueueMessage queueMsg) {
        this.queueMsg = queueMsg;
    }

    public int getCount() {
        return (int) count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }
}
