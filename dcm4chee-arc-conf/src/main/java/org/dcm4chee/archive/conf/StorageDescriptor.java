package org.dcm4chee.archive.conf;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public final class StorageDescriptor {

    private final String storageID;
    private URI storageURI;
    private String digestAlgorithm;
    private String[] retrieveAETitles = {};
    private Availability instanceAvailability;
    private final Map<String, String> properties = new HashMap<String, String>();

    public StorageDescriptor(String storageID) {
        this(storageID, null);
    }

    public StorageDescriptor(String storageID, URI storageURI) {
        this.storageID = storageID;
        this.storageURI = storageURI;
    }

    public String getStorageID() {
        return storageID;
    }

    public URI getStorageURI() {
        return storageURI;
    }

    public void setStorageURI(URI storageURI) {
        this.storageURI = storageURI;
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    public String[] getRetrieveAETitles() {
        return retrieveAETitles;
    }

    public void setRetrieveAETitles(String... retrieveAETitles) {
        this.retrieveAETitles = retrieveAETitles;
    }

    public Availability getInstanceAvailability() {
        return instanceAvailability;
    }

    public void setInstanceAvailability(Availability instanceAvailability) {
        this.instanceAvailability = instanceAvailability;
    }

    public void setProperty(String name, String value) {
        properties.put(name, value);
    }

    public String getProperty(String name, String defValue) {
        String value = properties.get(name);
        return value != null ? value : defValue;
    }

    public Map<String,String> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "StorageDescriptor{" +
                "storageID=" + storageID +
                ", storageURI=" + storageURI +
                ", digestAlg=" + digestAlgorithm +
                ", retrieveAETs=" + retrieveAETitles +
                ", availability=" + instanceAvailability +
                ", properties=" + properties +
                '}';
    }
}
