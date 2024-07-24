package org.dcm4chee.arc.event;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Feb 2018
 */

public class BulkTaskEvent {
    private HttpServletRequest request;
    private final TaskOperation operation;
    private long count;
    private int failed;
    private Exception exception;
    private String queueName;

    public BulkTaskEvent(HttpServletRequest request, TaskOperation operation) {
        this.request = request;
        this.operation = operation;
    }

    public BulkTaskEvent(String queueName, TaskOperation operation) {
        this.queueName = queueName;
        this.operation = operation;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public TaskOperation getOperation() {
        return operation;
    }

    public int getCount() {
        return (int) count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public String getQueueName() {
        return queueName;
    }

    @Override
    public String toString() {
        return request == null
                ? "TaskEvent[queueName=" + queueName
                    + ", operation=" + operation
                    + ']'
                : "TaskEvent[" + request.getRemoteUser() + '@' + request.getRemoteHost()
                    + ", operation=" + operation
                    + ']';
    }
}
