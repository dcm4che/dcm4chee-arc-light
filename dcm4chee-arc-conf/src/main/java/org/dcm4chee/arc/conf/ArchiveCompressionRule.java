package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.util.Property;

import java.util.Arrays;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class ArchiveCompressionRule {

    private String commonName;

    private int priority;

    private Conditions conditions = new Conditions();

    private Duration delay;

    private String transferSyntax;

    private Property[] imageWriteParams = {};

    public ArchiveCompressionRule() {
    }

    public ArchiveCompressionRule(String commonName) {
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

    public Duration getDelay() {
        return delay;
    }

    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    public String getTransferSyntax() {
        return transferSyntax;
    }

    public void setTransferSyntax(String transferSyntax) {
        this.transferSyntax = transferSyntax;
    }

    public Property[] getImageWriteParams() {
        return imageWriteParams;
    }

    public void setImageWriteParams(Property[] imageWriteParams) {
        this.imageWriteParams = imageWriteParams;
    }

    public boolean match(String hostname, String sendingAET, String receivingAET, Attributes attrs) {
        return conditions.match(hostname, sendingAET, receivingAET, attrs);
    }

    @Override
    public String toString() {
        return "CompressionRule{" +
                "cn=" + commonName +
                ", delay=" + delay +
                ", transferSyntax=" + transferSyntax +
                ", imageWriteParams=" + Arrays.toString(imageWriteParams) +
                '}';
    }
}


