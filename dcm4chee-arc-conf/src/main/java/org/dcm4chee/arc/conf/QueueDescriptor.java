package org.dcm4chee.arc.conf;

import java.util.Objects;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class QueueDescriptor {

    public static final Duration DEFAULT_RETRY_DELAY = Duration.valueOf("PT1M");

    private String queueName;
    private String description;
    private int maxTasksParallel = 1;
    private int maxRetries = 0;
    private Duration retryDelay = DEFAULT_RETRY_DELAY;
    private Duration maxRetryDelay;
    private int retryDelayMultiplier = 100;
    private boolean retryOnWarning;
    private Duration purgeTaskCompletedDelay;
    private Duration purgeTaskFailedDelay;
    private Duration purgeTaskWarningDelay;
    private Duration purgeTaskCanceledDelay;
    private ScheduleExpression[] schedules = {};
    private boolean installed = true;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getMaxTasksParallel() {
        return maxTasksParallel;
    }

    public void setMaxTasksParallel(int maxTasksParallel) {
        this.maxTasksParallel = maxTasksParallel;
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

    public Duration getPurgeTaskCompletedDelay() {
        return purgeTaskCompletedDelay;
    }

    public void setPurgeTaskCompletedDelay(Duration purgeTaskCompletedDelay) {
        this.purgeTaskCompletedDelay = purgeTaskCompletedDelay;
    }

    public Duration getPurgeTaskFailedDelay() {
        return purgeTaskFailedDelay;
    }

    public void setPurgeTaskFailedDelay(Duration purgeTaskFailedDelay) {
        this.purgeTaskFailedDelay = purgeTaskFailedDelay;
    }

    public Duration getPurgeTaskWarningDelay() {
        return purgeTaskWarningDelay;
    }

    public void setPurgeTaskWarningDelay(Duration purgeTaskWarningDelay) {
        this.purgeTaskWarningDelay = purgeTaskWarningDelay;
    }

    public Duration getPurgeTaskCanceledDelay() {
        return purgeTaskCanceledDelay;
    }

    public void setPurgeTaskCanceledDelay(Duration purgeTaskCanceledDelay) {
        this.purgeTaskCanceledDelay = purgeTaskCanceledDelay;
    }

    public ScheduleExpression[] getSchedules() {
        return schedules;
    }

    public void setSchedules(ScheduleExpression[] schedules) {
        this.schedules = schedules;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    @Override
    public String toString() {
        return "Queue{" + queueName + '}';
    }
}
