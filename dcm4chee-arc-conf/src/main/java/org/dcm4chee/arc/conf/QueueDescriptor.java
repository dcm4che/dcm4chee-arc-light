package org.dcm4chee.arc.conf;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class QueueDescriptor {

    private static final int DEFAULT_RETRY_DELAY = 60;

    private String queueName;
    private String jndiName;
    private String description;
    private int maxRetries = 0;
    private Duration retryDelay;
    private Duration maxRetryDelay;
    private int retryDelayMultiplier = 100;
    private boolean retryOnWarning;
    private Duration purgeQueueMessageCompletedDelay;

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

        long delay = retryDelay != null ? retryDelay.getSeconds() : DEFAULT_RETRY_DELAY;
        while (--retry > 0)
            delay = delay * retryDelayMultiplier / 100;

        return maxRetryDelay != null ? Math.min(delay, maxRetryDelay.getSeconds()) : delay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
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
}
