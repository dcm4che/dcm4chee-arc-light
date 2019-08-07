/*
 * **** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * **** END LICENSE BLOCK *****
 */
package org.dcm4chee.arc.conf;

import org.dcm4che3.util.StringUtils;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
    private Availability instanceAvailability = Availability.ONLINE;
    private String storageClusterID;
    private String exportStorageID;
    private String retrieveCacheStorageID;
    private int retrieveCacheStorageMaxParallel = 10;
    private int deleterThreads = 1;
    private String[] externalRetrieveAETitles = {};
    private boolean readOnly;
    private boolean noDeletionConstraint;
    private StorageDuration storageDuration = StorageDuration.PERMANENT;
    private StorageThreshold storageThreshold;
    private final ArrayList<DeleterThreshold> deleterThresholds = new ArrayList<>();
    private final Map<String, String> properties = new HashMap<>();
    private final EnumMap<RetentionPeriod.DeleteStudies,List<RetentionPeriod>> retentionPeriods =
            new EnumMap(RetentionPeriod.DeleteStudies.class);

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

    public String[] getExternalRetrieveAETitles() {
        return externalRetrieveAETitles;
    }

    public void setExternalRetrieveAETitles(String... externalRetrieveAETitles) {
        Arrays.sort(this.externalRetrieveAETitles = externalRetrieveAETitles);
    }

    public String getExportStorageID() {
        return exportStorageID;
    }

    public void setExportStorageID(String exportStorageID) {
        this.exportStorageID = exportStorageID;
    }

    public String getRetrieveCacheStorageID() {
        return retrieveCacheStorageID;
    }

    public void setRetrieveCacheStorageID(String retrieveCacheStorageID) {
        this.retrieveCacheStorageID = retrieveCacheStorageID;
    }

    public int getRetrieveCacheMaxParallel() {
        return retrieveCacheStorageMaxParallel;
    }

    public void setRetrieveCacheMaxParallel(int retrieveCacheStorageMaxParallel) {
        this.retrieveCacheStorageMaxParallel = retrieveCacheStorageMaxParallel;
    }

    public int getDeleterThreads() {
        return deleterThreads;
    }

    public void setDeleterThreads(int deleterThreads) {
        this.deleterThreads = deleterThreads;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isNoDeletionConstraint() {
        return noDeletionConstraint;
    }

    public void setNoDeletionConstraint(boolean noDeletionConstraint) {
        this.noDeletionConstraint = noDeletionConstraint;
    }

    public StorageThreshold getStorageThreshold() {
        return storageThreshold;
    }

    public void setStorageThreshold(StorageThreshold storageThreshold) {
        this.storageThreshold = storageThreshold;
    }

    public boolean hasDeleterThresholds() {
        return !deleterThresholds.isEmpty();
    }

    public String[] getDeleterThresholdsAsStrings() {
        String[] ss = new String[deleterThresholds.size()];
        for (int i = 0; i < ss.length; i++) {
            ss[i] = deleterThresholds.get(i).toString();
        }
        return ss;
    }

    public List<DeleterThreshold> getDeleterThresholds() {
        return deleterThresholds;
    }

    public void setDeleterThresholdsFromStrings(String... ss) {
        deleterThresholds.clear();
        for (String s : ss) {
            deleterThresholds.add(DeleterThreshold.valueOf(s));
        }
        Collections.sort(deleterThresholds);
    }

    public long getDeleterThresholdMinUsableSpace(Calendar cal) {
        for (DeleterThreshold deleterThreshold : deleterThresholds) {
            if (deleterThreshold.match(cal))
                return deleterThreshold.getMinUsableDiskSpace();
        }
        return -1L;
    }

    public boolean hasRetentionPeriods() {
        return !retentionPeriods.isEmpty();
    }

    public String[] getRetentionPeriodsAsStrings(RetentionPeriod.DeleteStudies deleteStudies) {
        List<RetentionPeriod> retentionPeriods = this.retentionPeriods.get(deleteStudies);
        return retentionPeriods == null ? StringUtils.EMPTY_STRING
                : retentionPeriods.stream().map(RetentionPeriod::toString).toArray(String[]::new);
    }

    public List<RetentionPeriod> getRetentionPeriods(RetentionPeriod.DeleteStudies deleteStudies) {
        return retentionPeriods.getOrDefault(deleteStudies, Collections.emptyList());
    }

    public void setRetentionPeriods(RetentionPeriod.DeleteStudies deleteStudies, String... ss) {
        if (ss.length == 0)
            retentionPeriods.remove(deleteStudies);
        else
            retentionPeriods.put(deleteStudies,
                    Stream.of(ss)
                    .map(RetentionPeriod::valueOf)
                    .sorted()
                    .collect(Collectors.toList()));
    }

    public Optional<Period> getRetentionPeriod(RetentionPeriod.DeleteStudies deleteStudies, Calendar cal) {
        List<RetentionPeriod> retentionPeriods = this.retentionPeriods.get(deleteStudies);
        return retentionPeriods == null ? Optional.empty()
                : retentionPeriods.stream().filter(x -> x.match(cal)).map(RetentionPeriod::getPeriod).findFirst();
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
                throw new IllegalArgumentException("Property in incorrect format : " + s);
            setProperty(s.substring(0, index), s.substring(index+1));
        }
    }

    public StorageDuration getStorageDuration() {
        return storageDuration;
    }

    public void setStorageDuration(StorageDuration storageDuration) {
        this.storageDuration = storageDuration;
    }

    @Override
    public String toString() {
        return "Storage[id=" + storageID + ", uri=" + storageURI + ']';
    }

    public static String[] storageIDsOf(List<StorageDescriptor> descriptors) {
        String[] storageIDs = new String[descriptors.size()];
        for (int i = 0; i < storageIDs.length; i++) {
            storageIDs[i] = descriptors.get(i).getStorageID();
        }
        return storageIDs;
    }

    public String getStorageClusterID() {
        return storageClusterID;
    }

    public void setStorageClusterID(String storageClusterID) {
        this.storageClusterID = storageClusterID;
    }

    public List<String> getStudyStorageIDs(List<String> otherStorageIDs) {
        return exportStorageID != null
                ? addPowerSet(false, otherStorageIDs, exportStorageID, storageID)
                : addPowerSet(false, otherStorageIDs, storageID);
    }

    public List<String> getStudyStorageIDs(List<String> otherStorageIDs,
                                           Boolean storageClustered, Boolean storageExported) {
        if (storageClusterID == null || storageClustered != null && !storageClustered) {
            return exportStorageID == null || storageExported != null && !storageExported
                    ? Collections.singletonList(storageID)
                    : storageExported == null
                    ? addPowerSet(false, Collections.singletonList(exportStorageID), storageID)
                    : addPowerSet(false, Collections.emptyList(), exportStorageID, storageID);
        }

        if (exportStorageID == null || storageExported != null && !storageExported) {
            return addPowerSet(storageClustered != null, otherStorageIDs, storageID);
        }

        List<String> studyStorageIDs = addPowerSet(
                storageClustered != null, otherStorageIDs, exportStorageID, storageID);
        if (storageExported == null) {
            studyStorageIDs.addAll(addPowerSet(storageClustered != null, otherStorageIDs, storageID));
        }
        return studyStorageIDs;
    }

    private static List<String> addPowerSet(boolean excludeEmptySet, List<String> storageIDs, String... common) {
        if (storageIDs.isEmpty()) {
            if (excludeEmptySet)
                return Collections.emptyList();

            Arrays.sort(common);
            return Collections.singletonList(StringUtils.concat(common, '\\'));
        }
        return IntStream.range(excludeEmptySet ? 1 : 0, 1 << storageIDs.size()).mapToObj(i -> {
            String[] a = Arrays.copyOf(common, common.length + Integer.bitCount(i));
            int j = common.length;
            int mask = 1;
            for (String storageID : storageIDs) {
                if ((i & mask) != 0) a[j++] = storageID;
                mask <<= 1;
            }
            Arrays.sort(a);
            return StringUtils.concat(a, '\\');
        }).collect(Collectors.toList());
    }
}
