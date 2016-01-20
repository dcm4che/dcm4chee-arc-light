package org.dcm4chee.arc.conf;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public final class StorageDescriptor {
    public StorageDescriptor() {
    }

    private String storageID;
    private URI storageURI;
    private String digestAlgorithm;
    private String[] retrieveAETitles = {};
    private Availability instanceAvailability;
    private final Map<String, String> properties = new HashMap<>();

    public StorageDescriptor(String storageID) {
//        this(storageID, null);
        setStorageID(storageID);
    }

    public void setStorageID (String storageID) {
        this.storageID = storageID;
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
        getMessageDigest(digestAlgorithm);
        this.digestAlgorithm = digestAlgorithm;
    }

    public MessageDigest getMessageDigest() {
        return getMessageDigest(digestAlgorithm);
    }

    private static MessageDigest getMessageDigest(String algorithm) {
        try {
            return algorithm != null ? MessageDigest.getInstance(algorithm) : null;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such algorithm: " + algorithm);
        }
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


    public void setProperties(String[] ss) {
        properties.clear();
        for (String s : ss) {
            int index = s.indexOf('=');
            if (index < 0)
                throw new IllegalArgumentException(s);
            setProperty(s.substring(0, index), s.substring(index+1));
        }
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
