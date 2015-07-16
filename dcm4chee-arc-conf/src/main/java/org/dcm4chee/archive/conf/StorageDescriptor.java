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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StorageDescriptor)) return false;

        StorageDescriptor that = (StorageDescriptor) o;
        return storageID.equals(that.storageID)
                && (storageURI == null ? storageURI == null : storageURI.equals(that.storageURI))
                && properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        int result = storageID.hashCode();
        result = 31 * result + (storageURI == null ? 0 : storageURI.hashCode());
        result = 31 * result + properties.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "StorageDescriptor{" +
                "storageID='" + storageID + '\'' +
                ", storageURI=" + storageURI +
                ", properties=" + properties +
                '}';
    }
}
