package org.dcm4chee.arc.conf;

import org.dcm4che3.util.StringUtils;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public final class StorageDescriptor {
    private String storageID;
    private String storageURIStr;
    private URI storageURI;
    private String digestAlgorithm;
    private Availability instanceAvailability;
    private String externalRetrieveAETitle;
    private boolean readOnly;
    private boolean cache;

    private final ArrayList<StorageThreshold> storageThresholds = new ArrayList<>();
    private final Map<String, String> properties = new HashMap<>();

    public StorageDescriptor() {
    }

    public StorageDescriptor(String storageID) {
        setStorageID(storageID);
    }

    public void setStorageID (String storageID) {
        this.storageID = storageID;
    }

    public String getStorageID() {
        return storageID;
    }

    public String getStorageURIStr() {
        return storageURIStr;
    }

    public void setStorageURIStr(String str) {
        this.storageURI = URI.create(StringUtils.replaceSystemProperties(str));
        this.storageURIStr = str;
    }

    public URI getStorageURI() {
        return storageURI;
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

    public Availability getInstanceAvailability() {
        return instanceAvailability;
    }

    public void setInstanceAvailability(Availability instanceAvailability) {
        this.instanceAvailability = instanceAvailability;
    }

    public String getExternalRetrieveAETitle() {
        return externalRetrieveAETitle;
    }

    public void setExternalRetrieveAETitle(String externalRetrieveAETitle) {
        this.externalRetrieveAETitle = externalRetrieveAETitle;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isCache() {
        return cache;
    }

    public void setCache(boolean cache) {
        this.cache = cache;
    }

    public boolean hasStorageThresholds() {
        return !storageThresholds.isEmpty();
    }

    public String[] getStorageThresholdsAsStrings() {
        String[] ss = new String[storageThresholds.size()];
        for (int i = 0; i < ss.length; i++) {
            ss[i] = storageThresholds.get(i).toString();
        }
        return ss;
    }

    public void setStorageThresholdsFromStrings(String... ss) {
        storageThresholds.clear();
        for (String s : ss) {
            storageThresholds.add(new StorageThreshold(s));
        }
        Collections.sort(storageThresholds);
    }

    public long getMinUsableSpace(Calendar cal) {
        for (StorageThreshold storageThreshold : storageThresholds) {
            if (storageThreshold.match(cal))
                return storageThreshold.getMinUsableDiskSpace();
        }
        return -1L;
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
                ", availability=" + instanceAvailability +
                ", externalRetrieveAETitle=" + externalRetrieveAETitle +
                ", readOnly=" + readOnly +
                ", cache=" + cache +
                ", storageThresholds=" + storageThresholds +
                ", properties=" + properties +
                '}';
    }

    public static String[] storageIDsOf(List<StorageDescriptor> descriptors) {
        String[] storageIDs = new String[descriptors.size()];
        for (int i = 0; i < storageIDs.length; i++) {
            storageIDs[i] = descriptors.get(i).getStorageID();
        }
        return storageIDs;
    }
}
