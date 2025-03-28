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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.DateUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.Availability;

import java.util.*;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 */
@NamedQueries({
@NamedQuery(
    name=Instance.FIND_BY_SOP_IUID_EAGER,
    query="select i from Instance i " +
            "join fetch i.series se " +
            "join fetch se.study st " +
            "join fetch st.patient p " +
            "left join fetch p.patientIDs " +
            "left join fetch p.patientName " +
            "left join fetch st.referringPhysicianName " +
            "left join fetch se.performingPhysicianName " +
            "left join fetch i.conceptNameCode " +
            "join fetch i.attributesBlob " +
            "join fetch se.attributesBlob " +
            "join fetch st.attributesBlob " +
            "join fetch p.attributesBlob " +
            "where i.sopInstanceUID = ?1"),
@NamedQuery(
        name=Instance.FIND_LAST_MODIFIED_STUDY_LEVEL,
        query="SELECT p.updatedTime, st.modifiedTime, MAX(se.modifiedTime), MAX(i.updatedTime) from Instance i " +
                "JOIN i.series se " +
                "JOIN se.study st " +
                "JOIN st.patient p " +
                "where st.studyInstanceUID = ?1 " +
                "GROUP BY p, st"
),
@NamedQuery(
        name=Instance.FIND_LAST_MODIFIED_SERIES_LEVEL,
        query="SELECT p.updatedTime, st.modifiedTime, se.modifiedTime, MAX(i.updatedTime) from Instance i " +
                "JOIN i.series se " +
                "JOIN se.study st " +
                "JOIN st.patient p " +
                "where st.studyInstanceUID = ?1 " +
                "and se.seriesInstanceUID = ?2 " +
                "GROUP BY p, st, se"
),
@NamedQuery(
        name=Instance.FIND_LAST_MODIFIED_INSTANCE_LEVEL,
        query="SELECT p.updatedTime, st.modifiedTime, se.modifiedTime, i.updatedTime from Instance i " +
                "JOIN i.series se " +
                "JOIN se.study st " +
                "JOIN st.patient p " +
                "where st.studyInstanceUID = ?1 " +
                "and se.seriesInstanceUID = ?2 " +
                "and i.sopInstanceUID = ?3"
),
@NamedQuery(
        name=Instance.MAX_UPDATED_TIME_OF_SERIES,
        query="SELECT MAX(i.updatedTime) from Instance i " +
                "where i.series = ?1"),
@NamedQuery(
    name=Instance.FIND_BY_STUDY_SERIES_SOP_IUID_EAGER,
    query="select i from Instance i " +
            "join fetch i.series se " +
            "join fetch se.study st " +
            "join fetch st.patient p " +
            "left join fetch p.patientIDs " +
            "left join fetch p.patientName " +
            "left join fetch st.referringPhysicianName " +
            "left join fetch se.performingPhysicianName " +
            "left join fetch i.conceptNameCode " +
            "join fetch i.attributesBlob " +
            "join fetch se.attributesBlob " +
            "join fetch st.attributesBlob " +
            "join fetch p.attributesBlob " +
            "where st.studyInstanceUID = ?1 " +
            "and se.seriesInstanceUID = ?2 " +
            "and i.sopInstanceUID = ?3"),
@NamedQuery(
    name = Instance.COUNT_REJECTED_INSTANCES_OF_SERIES,
    query = "select count(i) from Instance i " +
            "where i.series = ?1 and i.sopInstanceUID in (" +
            "select ri.sopInstanceUID from RejectedInstance ri " +
            "where ri.studyInstanceUID = i.series.study.studyInstanceUID " +
            "and ri.seriesInstanceUID = i.series.seriesInstanceUID)"),
@NamedQuery(
    name = Instance.COUNT_NOT_REJECTED_INSTANCES_OF_SERIES,
    query ="select count(i) from Instance i " +
            "where i.series = ?1 and i.sopInstanceUID not in (" +
            "select ri.sopInstanceUID from RejectedInstance ri " +
            "where ri.studyInstanceUID = i.series.study.studyInstanceUID " +
            "and ri.seriesInstanceUID = i.series.seriesInstanceUID)"),
@NamedQuery(
    name=Instance.COUNT_INSTANCES_OF_SERIES,
    query="select count(i) from Instance i " +
            "where i.series = ?1"),
@NamedQuery(
    name = Instance.FIND_BY_STUDY_IUID,
    query = "select instance from Instance instance " +
            "where instance.series.study.studyInstanceUID = ?1"),
@NamedQuery(
    name = Instance.FIND_BY_STUDY_IUID_AND_SOP_CUID,
    query = "select i from Instance i " +
            "join fetch i.series se " +
            "join fetch i.attributesBlob " +
            "where se.study.studyInstanceUID = ?1 and i.sopClassUID = ?2"),
@NamedQuery(
    name = Instance.FIND_WITHOUT_LOCATIONS_BY_STUDY,
    query = "select i from Instance i " +
            "where i.series.study = ?1 and i.locations is empty"),
@NamedQuery(
    name = Instance.IUIDS_OF_STUDY,
    query = "select instance.series.study.studyInstanceUID, instance.series.seriesInstanceUID, instance.sopInstanceUID, instance.numberOfFrames " +
            "from Instance instance " +
            "where instance.series.study.studyInstanceUID = ?1 "),
@NamedQuery(
    name = Instance.IUIDS_OF_SERIES,
    query = "select instance.series.study.studyInstanceUID, instance.series.seriesInstanceUID, instance.sopInstanceUID, instance.numberOfFrames " +
            "from Instance instance " +
            "where instance.series.study.studyInstanceUID = ?1 and instance.series.seriesInstanceUID = ?2"),
@NamedQuery(
    name = Instance.IUIDS_OF_SERIES2,
    query = "select i.sopInstanceUID from Instance i " +
            "where i.series = ?1"),
@NamedQuery(
    name = Instance.NUMBER_OF_FRAMES,
    query = "select instance.numberOfFrames " +
            "from Instance instance " +
            "where instance.series.study.studyInstanceUID = ?1 " +
            "and instance.series.seriesInstanceUID = ?2 " +
            "and instance.sopInstanceUID = ?3"),
@NamedQuery(
    name = Instance.GREATER_AVAILABILITY_BY_STUDY_IUID,
    query = "select i.availability from Instance i " +
            "where i.series.study.studyInstanceUID = ?1 and i.availability > ?2"),
@NamedQuery(
    name = Instance.GREATER_AVAILABILITY_BY_SERIES_IUID,
    query = "select i.availability from Instance i " +
            "where i.series.seriesInstanceUID = ?1 and i.availability > ?2"),
@NamedQuery(
    name = Instance.UPDATE_AVAILABILITY_BY_STUDY_PK,
    query = "update Instance i set i.availability = ?2 " +
            "where i.availability != ?2 and i.series in (" +
            "select ser from Series ser where ser.study.pk = ?1)"),
@NamedQuery(
        name = Instance.UPDATE_AVAILABILITY_BY_STUDY_IUID,
        query = "update Instance i set i.availability = ?2 " +
                "where i.availability != ?2 and i.series in (" +
                "select ser from Series ser where ser.study.studyInstanceUID = ?1)"),
@NamedQuery(
        name = Instance.UPDATE_AVAILABILITY_BY_SERIES_IUID,
        query = "update Instance i set i.availability = ?2 " +
                "where i.availability != ?2 and i.series in (" +
                "select ser from Series ser where ser.seriesInstanceUID = ?1)"),
@NamedQuery(
        name = Instance.UPDATE_AVAILABILITY_BY_SOP_IUID,
        query = "update Instance i set i.availability = ?2 " +
                "where i.sopInstanceUID = ?1 and i.availability != ?2"),
@NamedQuery(
        name = Instance.UPDATE_EXTERNAL_RETRIEVE_AET_BY_SOP_IUIDS,
        query = "update Instance i set i.externalRetrieveAET = ?2 " +
                "where i.sopInstanceUID in ?1 and i.externalRetrieveAET != ?2"),
@NamedQuery(
        name = Instance.DISTINCT_EXTERNAL_RETRIEVE_AET_BY_SERIES_PK,
        query = "select distinct i.externalRetrieveAET from Instance i " +
                "where i.series.pk = ?1"),
@NamedQuery(
        name = Instance.DISTINCT_EXTERNAL_RETRIEVE_AET_BY_SERIES_IUID,
        query = "select distinct i.externalRetrieveAET from Instance i " +
                "where i.series.seriesInstanceUID = ?1")
})
@Entity
@Table(name = "instance",
    uniqueConstraints = @UniqueConstraint(columnNames = { "series_fk", "sop_iuid" }),
    indexes = {
        @Index(columnList = "sop_iuid"),
        @Index(columnList = "sop_cuid"),
        @Index(columnList = "inst_no"),
        @Index(columnList = "content_date"),
        @Index(columnList = "content_time"),
        @Index(columnList = "sr_verified"),
        @Index(columnList = "sr_complete"),
        @Index(columnList = "inst_custom1"),
        @Index(columnList = "inst_custom2"),
        @Index(columnList = "inst_custom3")
    })
public class Instance {

    public static final String FIND_BY_SOP_IUID_EAGER = "Instance.findBySopIUIDEager";
    public static final String FIND_BY_STUDY_SERIES_SOP_IUID_EAGER = "Instance.findByStudySeriesSopIUIDEager";
    public static final String COUNT_INSTANCES_OF_SERIES = "Instance.countInstancesOfSeries";
    public static final String COUNT_REJECTED_INSTANCES_OF_SERIES = "Instance.countRejectedInstancesOfSeries";
    public static final String COUNT_NOT_REJECTED_INSTANCES_OF_SERIES = "Instance.countNotRejectedInstancesOfSeries";
    public static final String FIND_BY_STUDY_IUID = "Instance.findByStudyIUID";
    public static final String FIND_BY_STUDY_IUID_AND_SOP_CUID = "Instance.findByStudyIUIDAndSOPCUID";
    public static final String FIND_WITHOUT_LOCATIONS_BY_STUDY = "Instance.findWithoutLocationsByStudy";
    public static final String IUIDS_OF_STUDY = "Instance.iuidsOfStudy";
    public static final String IUIDS_OF_SERIES = "Instance.iuidsOfSeries";
    public static final String IUIDS_OF_SERIES2 = "Instance.iuidsOfSeries2";
    public static final String NUMBER_OF_FRAMES = "Instance.numberOfFrames";
    public static final String FIND_LAST_MODIFIED_STUDY_LEVEL = "Instance.findLastModifiedStudyLevel";
    public static final String FIND_LAST_MODIFIED_SERIES_LEVEL = "Instance.findLastModifiedSeriesLevel";
    public static final String FIND_LAST_MODIFIED_INSTANCE_LEVEL = "Instance.findLastModifiedInstanceLevel";
    public static final String MAX_UPDATED_TIME_OF_SERIES = "Instance.maxUpdateTimeOfSeries";
    public static final String GREATER_AVAILABILITY_BY_STUDY_IUID = "Instance.greaterAvailabilityByStudyIUID";
    public static final String GREATER_AVAILABILITY_BY_SERIES_IUID = "Instance.greaterAvailabilityBySeriesIUID";
    public static final String UPDATE_AVAILABILITY_BY_STUDY_PK = "Instance.updateAvailabilityByStudyPk";
    public static final String UPDATE_AVAILABILITY_BY_STUDY_IUID = "Instance.updateAvailabilityByStudyIUID";
    public static final String UPDATE_AVAILABILITY_BY_SERIES_IUID = "Instance.updateAvailabilityBySeriesIUID";
    public static final String UPDATE_AVAILABILITY_BY_SOP_IUID = "Instance.updateAvailabilityBySopIUID";
    public static final String UPDATE_EXTERNAL_RETRIEVE_AET_BY_SOP_IUIDS = "Instance.updateExternalRetrieveAETBySopIUIDs";
    public static final String DISTINCT_EXTERNAL_RETRIEVE_AET_BY_SERIES_PK = "Instance.distinctExternalRetrieveAETsBySeriesPk";
    public static final String DISTINCT_EXTERNAL_RETRIEVE_AET_BY_SERIES_IUID = "Instance.distinctExternalRetrieveAETsBySeriesIUID";

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    
    @Version
    @Column(name = "version")
    private long version;  

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_time")
    private Date updatedTime;

    @Basic(optional = false)
    @Column(name = "sop_iuid", updatable = false)
    private String sopInstanceUID;

    @Basic(optional = false)
    @Column(name = "sop_cuid", updatable = false)
    private String sopClassUID;

    @Column(name = "inst_no")
    private Integer instanceNumber;

    @Basic(optional = false)
    @Column(name = "content_date")
    private String contentDate;

    @Basic(optional = false)
    @Column(name = "content_time")
    private String contentTime;

    @Basic(optional = false)
    @Column(name = "sr_complete")
    private String completionFlag;

    @Basic(optional = false)
    @Column(name = "sr_verified")
    private String verificationFlag;

    @Basic(optional = false)
    @Column(name = "inst_custom1")
    private String instanceCustomAttribute1;

    @Basic(optional = false)
    @Column(name = "inst_custom2")
    private String instanceCustomAttribute2;

    @Basic(optional = false)
    @Column(name = "inst_custom3")
    private String instanceCustomAttribute3;

    @Column(name = "num_frames")
    private Integer numberOfFrames;

    @Column(name = "retrieve_aets")
    private String retrieveAETs;

    @Basic(optional = false)
    @Column(name = "ext_retrieve_aet")
    private String externalRetrieveAET = "*";

    @Basic(optional = false)
    @Column(name = "availability")
    private Availability availability;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "dicomattrs_fk")
    private AttributesBlob attributesBlob;

    @ManyToOne
    @JoinColumn(name = "srcode_fk")
    private CodeEntity conceptNameCode;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "instance_fk")
    private Collection<InstanceRequestAttributes> requestAttributes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "instance_fk")
    private Collection<VerifyingObserver> verifyingObservers;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "instance_fk")
    private Collection<ContentItem> contentItems;

    @OneToMany(mappedBy = "instance")
    private Collection<Location> locations;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "series_fk")
    private Series series;

    @Override
    public String toString() {
        return "Instance[pk=" + pk
                + ", uid=" + sopInstanceUID
                + ", class=" + sopClassUID
                + ", no=" + instanceNumber
                + "]";
    }

    @PrePersist
    public void onPrePersist() {
        Date now = new Date();
        createdTime = now;
        updatedTime = now;
    }

    @PreUpdate
    public void onPreUpdate() {
        updatedTime = new Date();
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    public String getSopClassUID() {
        return sopClassUID;
    }

    public Integer getInstanceNumber() {
        return instanceNumber;
    }

    public String getContentDate() {
        return contentDate;
    }

    public String getContentTime() {
        return contentTime;
    }

    public String getCompletionFlag() {
        return completionFlag;
    }

    public String getVerificationFlag() {
        return verificationFlag;
    }

    public String getInstanceCustomAttribute1() {
        return instanceCustomAttribute1;
    }

    public String getInstanceCustomAttribute2() {
        return instanceCustomAttribute2;
    }

    public String getInstanceCustomAttribute3() {
        return instanceCustomAttribute3;
    }

    public String[] getRetrieveAETs() {
        return StringUtils.split(retrieveAETs, '\\');
    }

    public String getRawRetrieveAETs() {
        return retrieveAETs;
    }

    public void setRetrieveAETs(String... retrieveAETs) {
        this.retrieveAETs = StringUtils.concat(retrieveAETs, '\\');
    }

    public Availability getAvailability() {
        return availability;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }

    public String getEncodedRetrieveAETs() {
        return retrieveAETs;
    }

    public void setEncodedRetrieveAETs(String retrieveAETs) {
        this.retrieveAETs = retrieveAETs;
    }

    public String getExternalRetrieveAET() {
        return externalRetrieveAET;
    }

    public void setExternalRetrieveAET(String externalRetrieveAET) {
        this.externalRetrieveAET = externalRetrieveAET;
    }

    public CodeEntity getConceptNameCode() {
        return conceptNameCode;
    }

    public void setConceptNameCode(CodeEntity conceptNameCode) {
        this.conceptNameCode = conceptNameCode;
    }

    public Collection<InstanceRequestAttributes> getRequestAttributes() {
        if (requestAttributes == null)
            requestAttributes = new ArrayList<>();
        return requestAttributes;
    }

    public Collection<VerifyingObserver> getVerifyingObservers() {
        if (verifyingObservers == null)
            verifyingObservers = new ArrayList<>();
        return verifyingObservers;
    }

    public Collection<ContentItem> getContentItems() {
        if (contentItems == null)
            contentItems = new ArrayList<>();
        return contentItems;
    }

    public Collection<Location> getLocations() {
        if (locations == null)
            locations = new ArrayList<>();
        return locations;
    }

    public Series getSeries() {
        return series;
    }

    public void setSeries(Series series) {
        this.series = series;
    }

    public Attributes getAttributes() throws BlobCorruptedException {
        return attributesBlob.getAttributes();
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter, boolean withOriginalAttributesSequence,
            FuzzyStr fuzzyStr) {
        sopInstanceUID = attrs.getString(Tag.SOPInstanceUID);
        sopClassUID = attrs.getString(Tag.SOPClassUID);
        instanceNumber = getInt(attrs, Tag.InstanceNumber, null);
        numberOfFrames = attrs.contains(Tag.Rows) ? getInt(attrs, Tag.NumberOfFrames, "1") : Integer.valueOf(0);
        Date dt = attrs.getDate(Tag.ContentDateAndTime);
        if (dt != null) {
            contentDate = DateUtils.formatDA(null, dt);
            contentTime = 
                attrs.containsValue(Tag.ContentTime)
                    ? DateUtils.formatTM(null, dt)
                    : "*";
        } else {
            contentDate = "*";
            contentTime = "*";
        }
        completionFlag = attrs.getString(Tag.CompletionFlag, "*").toUpperCase();
        verificationFlag = attrs.getString(Tag.VerificationFlag, "*").toUpperCase();

        instanceCustomAttribute1 = 
                AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute1(), "*");
        instanceCustomAttribute2 =
                AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute2(), "*");
        instanceCustomAttribute3 =
                AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute3(), "*");

        Attributes blobAttrs = new Attributes(attrs, filter.getSelection(withOriginalAttributesSequence));
        if (attributesBlob == null)
            attributesBlob = new AttributesBlob(blobAttrs);
        else
            attributesBlob.setAttributes(blobAttrs);
        updatedTime = new Date();
    }

    public AttributesBlob getAttributesBlob() {
        return attributesBlob;
    }

    private Integer getInt(Attributes attrs, int tag, String defVal) {
        String val = attrs.getString(tag, defVal);
        if (val != null)
            try {
                return Integer.valueOf(val);
            } catch (NumberFormatException e) {
            }
        return null;
    }
}
