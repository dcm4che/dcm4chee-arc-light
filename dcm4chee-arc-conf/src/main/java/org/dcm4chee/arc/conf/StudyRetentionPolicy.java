package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;

import java.time.Period;


/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2016
 */
public class StudyRetentionPolicy {

    private String commonName;

    private int priority;

    private Conditions conditions = new Conditions();

    private Period retentionPeriod;

    private boolean expireSeriesIndividually;

    public StudyRetentionPolicy() {
    }

    public StudyRetentionPolicy(String commonName) {
        setCommonName(commonName);
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Conditions getConditions() {
        return conditions;
    }

    public void setConditions(Conditions conditions) {
        this.conditions = conditions;
    }

    public Period getRetentionPeriod() {
        return retentionPeriod;
    }

    public void setRetentionPeriod(Period retentionPeriod) {
        this.retentionPeriod = retentionPeriod;
    }

    public boolean isExpireSeriesIndividually() {
        return expireSeriesIndividually;
    }

    public void setExpireSeriesIndividually(boolean expireSeriesIndividually) {
        this.expireSeriesIndividually = expireSeriesIndividually;
    }

    public boolean match(String hostname, String sendingAET, String receivingAET, Attributes attrs) {
        return conditions.match(hostname, sendingAET, receivingAET, attrs);
    }

    @Override
    public String toString() {
        return "StudyRetentionPolicy{" +
                "cn=" + commonName +
                ", retentionPeriod=" + retentionPeriod +
                ", expireSeriesIndividually=" + expireSeriesIndividually +
                '}';
    }
}


