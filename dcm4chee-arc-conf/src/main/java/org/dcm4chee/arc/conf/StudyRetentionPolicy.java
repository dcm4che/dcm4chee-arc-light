package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;


/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2016
 */
public class StudyRetentionPolicy {

    private String commonName;

    private int priority;

    private Conditions conditions = new Conditions();

    private Duration retentionPeriod;

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

    public Duration getRetentionPeriod() {
        return retentionPeriod;
    }

    public void setRetentionPeriod(Duration retentionPeriod) {
        this.retentionPeriod = retentionPeriod;
    }

    public boolean match(String hostname, String sendingAET, String receivingAET, Attributes attrs) {
        return conditions.match(hostname, sendingAET, receivingAET, attrs);
    }

    @Override
    public String toString() {
        return "StudyRetentionPolicy{" +
                "cn=" + commonName +
                ", retentionPeriod=" + retentionPeriod +
                '}';
    }
}


