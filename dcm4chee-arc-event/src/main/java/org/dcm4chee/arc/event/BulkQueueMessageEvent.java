package org.dcm4chee.arc.event;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2018
 */

public class BulkQueueMessageEvent {
    private HttpServletRequest request;
    private final QueueMessageOperation operation;
    private long count;
    private int failed;
    private Exception exception;
    private String queueName;

    public BulkQueueMessageEvent(HttpServletRequest request, QueueMessageOperation operation) {
        this.request = request;
        this.operation = operation;
    }

    public BulkQueueMessageEvent(String queueName, QueueMessageOperation operation) {
        this.queueName = queueName;
        this.operation = operation;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public QueueMessageOperation getOperation() {
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
}
