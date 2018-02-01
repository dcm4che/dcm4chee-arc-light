package org.dcm4chee.arc.event;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2018
 */

public class BulkQueueMessageEvent {
    private final HttpServletRequest request;
    private final QueueMessageOperation operation;
    private String[] filters = {};
    private long count;
    private Exception exception;

    public BulkQueueMessageEvent(HttpServletRequest request, QueueMessageOperation operation) {
        this.request = request;
        this.operation = operation;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public QueueMessageOperation getOperation() {
        return operation;
    }

    public String[] getFilters() {
        return filters;
    }

    public void setFilters(String[] filters) {
        this.filters = filters;
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
