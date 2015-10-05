package org.dcm4chee.arc.conf;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class QueueDescriptor {

    private final String queueName;
    private String jndiName;
    private String description;

    public QueueDescriptor(String queueName) {
        this.queueName = queueName;
    }

    public QueueDescriptor(String queueName, String description, String jndiName) {
        this(queueName);
        setDescription(description);
        setJndiName(jndiName);
    }

    public String getQueueName() {
        return queueName;
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
}
