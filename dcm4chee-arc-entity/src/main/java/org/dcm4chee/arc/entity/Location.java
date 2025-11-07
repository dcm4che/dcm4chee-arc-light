/*
 * *** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015
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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.entity;

import jakarta.persistence.*;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.LocationStatus;

import java.util.Date;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 */
@Entity
@Table(name = "location", indexes = {
    @Index(columnList = "storage_id,status"),
    @Index(columnList = "multi_ref")
})
@NamedQueries({
        @NamedQuery(name = Location.FIND_BY_STORAGE_ID_AND_STATUS,
                query = "select l from Location l where l.storageID=?1 and l.status=?2"),
        @NamedQuery(name = Location.FIND_BY_STATUS_CREATED_BEFORE,
                query = "select new org.dcm4chee.arc.entity.Location$LocationWithUIDs(" +
                        "l, l.instance.sopClassUID, l.instance.sopInstanceUID, l.instance.series.pk, l.instance.series.seriesInstanceUID, l.instance.series.study.studyInstanceUID) " +
                        "from Location l where l.storageID=?1 and l.status=?2 and l.createdTime<?3"),
        @NamedQuery(name = Location.FIND_BY_STUDY_PK,
                query = "select l from Location l join fetch l.instance inst " +
                        "where inst.series.study.pk=?1"),
        @NamedQuery(name = Location.FIND_BY_SERIES_PK,
                query = "select l from Location l where l.instance.series.pk=?1"),
        @NamedQuery(name = Location.FIND_WITH_SOP_IUID_BY_SERIES_PK_AND_OBJECT_TYPE,
                query = "select l.instance.sopInstanceUID, l from Location l " +
                        "where l.instance.series.pk=?1 and l.objectType=?2"),
        @NamedQuery(name = Location.FIND_BY_STUDY_PK_AND_STORAGE_IDS,
                query = "select l from Location l join fetch l.instance inst " +
                        "where inst.series.study.pk=?1 and l.storageID in ?2"),
        @NamedQuery(name = Location.FIND_BY_SOP_IUID_AND_STORAGE_ID,
                query = "select l from Location l where l.instance.sopInstanceUID=?1 and l.storageID=?2"),
        @NamedQuery(name = Location.INSTANCE_PKS_BY_STUDY_PK_AND_STORAGE_IDS,
                query = "select l.instance.pk from Location l where l.instance.series.study.pk=?1 and l.storageID in ?2"),
        @NamedQuery(name = Location.INSTANCE_PKS_BY_STUDY_PK_AND_STORAGE_IDS_AND_STATUS,
                query = "select l.instance.pk from Location l where l.instance.series.study.pk=?1 and l.storageID in ?2 and l.status=?3"),
        @NamedQuery(name = Location.STORAGE_IDS_BY_STUDY_PK_AND_OBJECT_TYPE,
                query = "select distinct l.storageID from Location l " +
                        "where l.instance.series.study.pk=?1 and l.objectType=?2"),
        @NamedQuery(name = Location.FIND_BY_REJECTION_CODE,
                query = "select l from Location l join fetch l.instance i join i.series ser join ser.study st " +
                        "where exists (" +
                        "select ri from RejectedInstance ri " +
                        "where ri.studyInstanceUID = st.studyInstanceUID " +
                        "and ri.seriesInstanceUID = ser.seriesInstanceUID " +
                        "and ri.sopInstanceUID = i.sopInstanceUID " +
                        "and ri.rejectionNoteCode = ?1)"),
        @NamedQuery(name = Location.FIND_BY_CONCEPT_NAME_CODE,
                query = "select l from Location l join fetch l.instance i " +
                        "where i.conceptNameCode=?1"),
        @NamedQuery(name = Location.FIND_BY_REJECTION_CODE_BEFORE,
                query = "select l from Location l join fetch l.instance i join i.series ser join ser.study st " +
                        "where exists (" +
                        "select ri from RejectedInstance ri " +
                        "where ri.studyInstanceUID = st.studyInstanceUID " +
                        "and ri.seriesInstanceUID = ser.seriesInstanceUID " +
                        "and ri.sopInstanceUID = i.sopInstanceUID " +
                        "and ri.rejectionNoteCode = ?1 and ri.createdTime < ?2)"),
        @NamedQuery(name = Location.FIND_BY_CONCEPT_NAME_CODE_BEFORE,
                query = "select l from Location l join fetch l.instance i " +
                        "where i.conceptNameCode=?1 and i.updatedTime<?2"),
        @NamedQuery(name = Location.FIND_BY_MULTI_REF,
                query = "select l from Location l where l.multiReference=?1"),
        @NamedQuery(name = Location.COUNT_BY_MULTI_REF,
                query = "select count(l) from Location l where l.multiReference=?1"),
        @NamedQuery(name = Location.COUNT_BY_UIDMAP,
                query = "select count(l) from Location l where l.uidMap=?1"),
        @NamedQuery(name = Location.COUNT_BY_SERIES_PK_AND_NOT_STATUS,
                query = "select count(l) from Location l where l.instance.series.pk=?1 and l.status!=?2"),
        @NamedQuery(name = Location.SET_DIGEST,
                query = "update Location l set l.digest = ?2 where l.pk = ?1"),
        @NamedQuery(name = Location.SET_STATUS,
                query = "update Location l set l.status = ?2 where l.pk = ?1"),
        @NamedQuery(name = Location.SET_STATUS_BY_MULTI_REF,
                query = "update Location l set l.status = ?2 where l.multiReference = ?1"),
        @NamedQuery(name = Location.MARK_FOR_DELETION_BY_STUDY,
                query = "update Location l set l.status = ?2, l.instance = null " +
                        "where l in (" +
                        "select l2 from Location l2 where l2.instance.series.study = ?1 " +
                        "and l2.status != ?2 and l2.objectType = ?3 " +
                        "and l2.multiReference is null and l2.uidMap.pk is null)"),
        @NamedQuery(name = Location.UPDATE_STATUS_FROM,
                query = "update Location l set l.status = ?3 where l.pk = ?1 and l.status = ?2"),
        @NamedQuery(name = Location.UPDATE_STATUS_BY_STORAGE_ID_FROM,
                query = "update Location l set l.status = ?3 where l.storageID = ?1 and l.status = ?2"),
        @NamedQuery(name = Location.DELETE_BY_PK,
                query = "delete from Location l where l.pk = ?1"),
        @NamedQuery(name = Location.EXISTS,
                query = "select l.pk from Location l where l.pk = ?1"),
        @NamedQuery(name = Location.STATUS_COUNTS_BY_STORAGE_ID,
                query = "select new org.dcm4chee.arc.entity.Location$StatusCounts(l.status, count(l)) " +
                        "from Location l " +
                        "where l.storageID = ?1 and l.status != org.dcm4chee.arc.conf.LocationStatus.OK " +
                        "group by l.status")
})
@NamedNativeQueries({
        @NamedNativeQuery(name = Location.SIZE_OF_SERIES,
                query = "select sum(x.max_object_size) from (" +
                        "select max(object_size) max_object_size from location " +
                        "join instance on location.instance_fk = instance.pk " +
                        "where series_fk = ?1 and location.object_type = ?2 " +
                        "group by instance_fk) x")
})
public class Location {

    public static final String FIND_BY_STORAGE_ID_AND_STATUS = "Location.FindByStorageIDAndStatus";
    public static final String FIND_BY_STATUS_CREATED_BEFORE = "Location.FindByStatusCreatedBefore";
    public static final String FIND_BY_STUDY_PK = "Location.FindByStudyPk";
    public static final String FIND_BY_SERIES_PK = "Location.FindBySeriesPk";
    public static final String FIND_WITH_SOP_IUID_BY_SERIES_PK_AND_OBJECT_TYPE = "Location.FindWithSOPIUIDBySeriesPkAndObjectType";
    public static final String FIND_BY_STUDY_PK_AND_STORAGE_IDS = "Location.FindByStudyPkAndStorageIDs";
    public static final String FIND_BY_SOP_IUID_AND_STORAGE_ID = "Location.FindBySOPIUIDAndStorageID";
    public static final String INSTANCE_PKS_BY_STUDY_PK_AND_STORAGE_IDS = "Location.InstancePksByStudyPkAndStorageIDs";
    public static final String INSTANCE_PKS_BY_STUDY_PK_AND_STORAGE_IDS_AND_STATUS = "Location.InstancePksByStudyPkAndStorageIDsAndStatus";
    public static final String STORAGE_IDS_BY_STUDY_PK_AND_OBJECT_TYPE = "Location.StorageIDsByStudyPkAndObjectType";
    public static final String FIND_BY_REJECTION_CODE = "Location.FindByRejectionCode";
    public static final String FIND_BY_CONCEPT_NAME_CODE = "Location.FindByConceptNameCode";
    public static final String FIND_BY_REJECTION_CODE_BEFORE = "Location.FindByRejectionCodeBefore";
    public static final String FIND_BY_CONCEPT_NAME_CODE_BEFORE = "Location.FindByConceptNameCodeBefore";
    public static final String FIND_BY_MULTI_REF = "Location.FindByMultiRef";
    public static final String COUNT_BY_MULTI_REF = "Location.CountByMultiRef";
    public static final String COUNT_BY_UIDMAP = "Location.CountByUIDMap";
    public static final String COUNT_BY_SERIES_PK_AND_NOT_STATUS = "Location.CountBySeriesPkAndNotStatus";
    public static final String SET_DIGEST = "Location.SetDigest";
    public static final String SET_STATUS = "Location.SetStatus";
    public static final String SET_STATUS_BY_MULTI_REF = "Location.SetStatusByMultiRef";
    public static final String MARK_FOR_DELETION_BY_STUDY = "Location.MarkForDeletionByStudy";
    public static final String UPDATE_STATUS_FROM = "Location.UpdateStatusFrom";
    public static final String UPDATE_STATUS_BY_STORAGE_ID_FROM = "Location.UpdateStatusByStorageIDFrom";
    public static final String DELETE_BY_PK = "Location.DeleteByPk";
    public static final String SIZE_OF_SERIES = "Location.SizeOfSeries";
    public static final String EXISTS = "Location.Exists";
    public static final String STATUS_COUNTS_BY_STORAGE_ID = "Location.StatusCountsByStorageID";

    public enum ObjectType { DICOM_FILE, METADATA }

    public static class StatusCounts {
        public final LocationStatus status;
        public final long count;

        public StatusCounts(LocationStatus status, long count) {
            this.status = status;
            this.count = count;
        }
    }

    public static class LocationWithUIDs {
        public final Location location;
        public final String sopClassUID;
        public final String sopInstanceUID;
        public final Long seriesPk;
        public final String seriesInstanceUID;
        public final String studyInstanceUID;

        public LocationWithUIDs(Location location, String sopClassUID, String sopInstanceUID,
                                Long seriesPk, String seriesInstanceUID, String studyInstanceUID) {
            this.location = location;
            this.sopClassUID = sopClassUID;
            this.sopInstanceUID = sopInstanceUID;
            this.seriesPk = seriesPk;
            this.seriesInstanceUID = seriesInstanceUID;
            this.studyInstanceUID = studyInstanceUID;
        }
    }

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Column(name = "storage_id", updatable = false)
    private String storageID;

    @Basic(optional = false)
    @Column(name = "storage_path", updatable = false)
    private String storagePath;

    @Basic(optional = true)
    @Column(name = "tsuid", updatable = false)
    private String transferSyntaxUID;

    @Basic(optional = false)
    @Column(name = "object_size", updatable = false)
    private long size;

    @Basic(optional = true)
    @Column(name = "digest", updatable = false)
    private String digest;

    @Basic(optional = false)
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status", updatable = true)
    private LocationStatus status;

    @Basic(optional = false)
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "object_type", updatable = false)
    private ObjectType objectType;

    @Column(name = "multi_ref", updatable = true)
    private Integer multiReference;

    @Column(name = "uidmap_fk", insertable = false, updatable = false)
    private Long uidMapFk;

    @ManyToOne
    @JoinColumn(name = "uidmap_fk", updatable = false)
    private UIDMap uidMap;

    @ManyToOne
    @JoinColumn(name = "instance_fk", updatable = true)
    private Instance instance;

    public static boolean isDicomFile(Location location) {
        return location.objectType == ObjectType.DICOM_FILE;
    }

    public static final class Builder {
        private long pk;
        private String storageID;
        private String storagePath;
        private String transferSyntaxUID;
        private long size;
        private String digest;
        private LocationStatus status = LocationStatus.OK;
        private ObjectType objectType = ObjectType.DICOM_FILE;
        public Integer multiReference;
        private UIDMap uidMap;

        public Builder pk(long pk) {
            this.pk = pk;
            return this;
        }

        public Builder storageID(String storageID) {
            this.storageID = storageID;
            return this;
        }

        public Builder storagePath(String storagePath) {
            this.storagePath = storagePath;
            return this;
        }

        public Builder transferSyntaxUID(String transferSyntaxUID) {
            this.transferSyntaxUID = transferSyntaxUID;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder digest(String digest) {
            this.digest = digest;
            return this;
        }

        public Builder digest(byte[] digest) {
            return digest(digest != null ? TagUtils.toHexString(digest) : null);
        }

        public Builder status(LocationStatus status) {
            this.status = status;
            return this;
        }

        public Builder status(String status) {
            this.status = status != null ? LocationStatus.valueOf(status) : LocationStatus.OK;
            return this;
        }

        public Builder objectType(ObjectType objectType) {
            this.objectType = objectType;
            return this;
        }

        public Builder multiReference(Integer multiReference) {
            this.multiReference = multiReference;
            return this;
        }

        public Builder multiReference(String multiReference) {
            return multiReference(parseIntegerOrNull(multiReference));
        }

        private static Integer parseIntegerOrNull(String s) {
            if (s != null)
                try {
                    return Integer.valueOf(s);
                } catch (NumberFormatException e) {
                }
            return null;
        }

        public Builder uidMap(UIDMap uidMap) {
            this.uidMap = uidMap;
            return this;
        }

        public Location build() {
            return new Location(this);
        }
    }

    public Location() {}

    private Location(Builder builder) {
        pk = builder.pk;
        storageID = builder.storageID;
        storagePath = builder.storagePath;
        transferSyntaxUID = builder.transferSyntaxUID;
        size = builder.size;
        digest = builder.digest;
        status = builder.status;
        objectType = builder.objectType;
        multiReference = builder.multiReference;
        uidMap = builder.uidMap;
    }

    public Location(Location other) {
        this.createdTime = other.createdTime;
        this.storageID = other.storageID;
        this.storagePath = other.storagePath;
        this.transferSyntaxUID = other.transferSyntaxUID;
        this.size = other.size;
        this.digest = other.digest;
        this.status = other.status;
        this.objectType = other.objectType;
        this.multiReference = other.multiReference;
    }

    @PrePersist
    public void onPrePersist() {
        createdTime = new Date();
    }

    public long getPk() {
        return pk;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public String getStorageID() {
        return storageID;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getTransferSyntaxUID() {
        return transferSyntaxUID;
    }

    public long getSize() {
        return size;
    }

    public byte[] getDigest() {
        return digest != null ? TagUtils.fromHexString(digest) : null;
    }

    public String getDigestAsHexString() {
        return digest;
    }

    public LocationStatus getStatus() {
        return status;
    }

    public boolean isStatusOK() {
        return status == LocationStatus.OK;
    }

    public void setStatus(LocationStatus status) {
        this.status = status;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public Integer getMultiReference() {
        return multiReference;
    }

    public void setMultiReference(Integer multiReference) {
        this.multiReference = multiReference;
    }

    public UIDMap getUidMap() {
        return uidMap;
    }

    public void setUidMap(UIDMap uidMap) {
        this.uidMap = uidMap;
    }

    public Instance getInstance() {
        return instance;
    }

    public void setInstance(Instance instance) {
        this.instance = instance;
    }

    @Override
    public String toString() {
        return "Location[pk=" + pk
                + ", systemID=" + storageID
                + ", path=" + storagePath
                + ", tsuid=" + transferSyntaxUID
                + ", size=" + size
                + ", status=" + status
                + ", objectType=" + objectType
                + "]";
    }

}
