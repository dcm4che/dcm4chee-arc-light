package org.dcm4chee.arc.conf;

import java.util.Objects;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class QueueDescriptor {

    public static final Duration DEFAULT_RETRY_DELAY = Duration.valueOf("PT1M");

    private String queueName;
    private String jndiName;
    private String description;
    private int maxRetries = 0;
    private Duration retryDelay = DEFAULT_RETRY_DELAY;
    private Duration maxRetryDelay;
    private int retryDelayMultiplier = 100;
    private boolean retryOnWarning;
    private Duration purgeQueueMessageCompletedDelay;
    private Duration purgeQueueMessageFailedDelay;
    private Duration purgeQueueMessageWarningDelay;
    private Duration purgeQueueMessageCanceledDelay;
    private int maxQueueSize = 0;

    public QueueDescriptor(String queueName) {
        setQueueName(queueName);
    }

    public QueueDescriptor() {
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getJndiName() {
        return jndiName;
    }

    public void setJndiName(String jndiName) {
        this.jndiName = jndiName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public long getRetryDelayInSeconds(int retry) {
        if (retry > maxRetries)
            return -1L;

        long delay = retryDelay.getSeconds();
        while (--retry > 0)
            delay = delay * retryDelayMultiplier / 100;

        return maxRetryDelay != null ? Math.min(delay, maxRetryDelay.getSeconds()) : delay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = Objects.requireNonNull(retryDelay, "RetryDelay");
    }

    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    public void setMaxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = maxRetryDelay;
    }

    public int getRetryDelayMultiplier() {
        return retryDelayMultiplier;
    }

    public void setRetryDelayMultiplier(int retryDelayMultiplier) {
        this.retryDelayMultiplier = retryDelayMultiplier;
    }

    public boolean isRetryOnWarning() {
        return retryOnWarning;
    }

    public void setRetryOnWarning(boolean retryOnWarning) {
        this.retryOnWarning = retryOnWarning;
    }

    public Duration getPurgeQueueMessageCompletedDelay() {
        return purgeQueueMessageCompletedDelay;
    }

    public void setPurgeQueueMessageCompletedDelay(Duration purgeQueueMessageCompletedDelay) {
        this.purgeQueueMessageCompletedDelay = purgeQueueMessageCompletedDelay;
    }

    public Duration getPurgeQueueMessageFailedDelay() {
        return purgeQueueMessageFailedDelay;
    }

    public void setPurgeQueueMessageFailedDelay(Duration purgeQueueMessageFailedDelay) {
        this.purgeQueueMessageFailedDelay = purgeQueueMessageFailedDelay;
    }

    public Duration getPurgeQueueMessageWarningDelay() {
        return purgeQueueMessageWarningDelay;
    }

    public void setPurgeQueueMessageWarningDelay(Duration purgeQueueMessageWarningDelay) {
        this.purgeQueueMessageWarningDelay = purgeQueueMessageWarningDelay;
    }

    public Duration getPurgeQueueMessageCanceledDelay() {
        return purgeQueueMessageCanceledDelay;
    }

    public void setPurgeQueueMessageCanceledDelay(Duration purgeQueueMessageCanceledDelay) {
        this.purgeQueueMessageCanceledDelay = purgeQueueMessageCanceledDelay;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }
}
