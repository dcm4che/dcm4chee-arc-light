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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.DateUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.Duration;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    name=Study.FIND_BY_PATIENT,
    query="select st from Study st " +
            "where st.patient = ?1"),
@NamedQuery(
    name=Study.FIND_BY_STUDY_IUID,
    query="select st from Study st " +
            "where st.studyInstanceUID = ?1"),
@NamedQuery(
    name=Study.FIND_BY_STUDY_IUID_EAGER,
    query="select st from Study st " +
            "join fetch st.patient p " +
            "left join fetch p.patientName " +
            "left join fetch st.referringPhysicianName " +
            "join fetch st.attributesBlob " +
            "join fetch p.attributesBlob " +
            "where st.studyInstanceUID = ?1"),
@NamedQuery(
    name=Study.FIND_PK_BY_STORAGE_ID_ORDER_BY_ACCESS_TIME,
    query="select st.pk from Study st " +
            "where st.storageIDs = ?1 " +
            "order by st.accessTime"),
@NamedQuery(
    name=Study.FIND_PK_BY_STORAGE_ID_AND_EXT_RETR_AET,
    query="select st.pk from Study st " +
            "where st.storageIDs = ?1 and st.externalRetrieveAET = ?2 " +
            "order by st.accessTime"),
@NamedQuery(
    name=Study.UPDATE_ACCESS_TIME,
    query="update Study st set st.accessTime = CURRENT_TIMESTAMP where st.pk = ?1"),
@NamedQuery(
    name=Study.SET_FAILED_SOP_INSTANCE_UID_LIST,
    query="update Study st set st.failedSOPInstanceUIDList = ?2 " +
            "where st.studyInstanceUID = ?1"),
@NamedQuery(
    name=Study.INCREMENT_FAILED_RETRIEVES,
    query="update Study st set st.failedRetrieves = st.failedRetrieves + 1, st.failedSOPInstanceUIDList = ?2 " +
            "where st.studyInstanceUID = ?1"),
@NamedQuery(
    name=Study.CLEAR_FAILED_SOP_INSTANCE_UID_LIST,
    query="update Study st set st.failedSOPInstanceUIDList = NULL where st.studyInstanceUID = ?1"),
@NamedQuery(
    name=Study.COUNT_STUDIES_OF_PATIENT,
    query="select count(st) from Study st " +
            "where st.patient = ?1"),
@NamedQuery(
    name=Study.GET_EXPIRED_STUDIES,
    query="select st from Study st " +
            "where st.expirationDate <= ?1"),
@NamedQuery(
    name = Study.FIND_BY_ACCESS_TIME_AND_ACCESS_CONTROL_ID,
    query = "select st from Study st " +
            "where st.accessControlID = ?1 and st.accessTime = ?2")
})
@Entity
@Table(name = "study",
    uniqueConstraints = @UniqueConstraint(columnNames = "study_iuid"),
    indexes = {
        @Index(columnList = "access_time"),
        @Index(columnList = "created_time"),
        @Index(columnList = "access_control_id"),
        @Index(columnList = "rejection_state"),
        @Index(columnList = "storage_ids"),
        @Index(columnList = "study_date"),
        @Index(columnList = "study_time"),
        @Index(columnList = "accession_no"),
        @Index(columnList = "study_desc"),
        @Index(columnList = "study_custom1"),
        @Index(columnList = "study_custom2"),
        @Index(columnList = "study_custom3"),
        @Index(columnList = "expiration_date"),
        @Index(columnList = "failed_retrieves"),
        @Index(columnList = "failed_iuids"),
        @Index(columnList = "ext_retrieve_aet")
    })
public class Study {

    public static final String FIND_BY_PATIENT = "Study.findByPatient";
    public static final String FIND_BY_STUDY_IUID = "Study.findByStudyIUID";
    public static final String FIND_BY_STUDY_IUID_EAGER = "Study.findByStudyIUIDEager";
    public static final String FIND_PK_BY_STORAGE_ID_ORDER_BY_ACCESS_TIME = "Study.findPkByStorageIDOrderByAccessTime";
    public static final String FIND_PK_BY_STORAGE_ID_AND_EXT_RETR_AET = "Study.findPkByStorageIDAndExtRetrAET";
    public static final String UPDATE_ACCESS_TIME = "Study.UpdateAccessTime";
    public static final String SET_FAILED_SOP_INSTANCE_UID_LIST = "Study.SetFailedSOPInstanceUIDList";
    public static final String INCREMENT_FAILED_RETRIEVES = "Study.IncrementFailedRetrieves";
    public static final String CLEAR_FAILED_SOP_INSTANCE_UID_LIST = "Study.ClearFailedSOPInstanceUIDList";
    public static final String COUNT_STUDIES_OF_PATIENT = "Study.CountStudiesOfPatient";
    public static final String GET_EXPIRED_STUDIES = "Study.GetExpiredStudies";
    public static final String FIND_BY_ACCESS_TIME_AND_ACCESS_CONTROL_ID = "Study.FindByAccessTimeAndAccessControlID";

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
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "modified_time")
    private Date modifiedTime;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "access_time")
    private Date accessTime;

    @Basic
    @Column(name = "storage_ids")
    private String storageIDs;

    @Column(name = "failed_iuids", length = 4000)
    private String failedSOPInstanceUIDList;

    @Basic(optional = false)
    @Column(name = "failed_retrieves")
    private int failedRetrieves;

    @Basic(optional = false)
    @Column(name = "study_iuid", updatable = false)
    private String studyInstanceUID;

    @Basic(optional = false)
    @Column(name = "study_id")
    private String studyID;

    @Basic(optional = false)
    @Column(name = "study_date")
    private String studyDate;

    @Basic(optional = false)
    @Column(name = "study_time")
    private String studyTime;

    @Basic(optional = false)
    @Column(name = "accession_no")
    private String accessionNumber;

    @Basic(optional = false)
    @Column(name = "study_desc")
    private String studyDescription;

    @Basic(optional = false)
    @Column(name = "study_custom1")
    private String studyCustomAttribute1;

    @Basic(optional = false)
    @Column(name = "study_custom2")
    private String studyCustomAttribute2;

    @Basic(optional = false)
    @Column(name = "study_custom3")
    private String studyCustomAttribute3;

    @Basic(optional = false)
    @Column(name = "access_control_id")
    private String accessControlID = "*";

    @Basic(optional = false)
    @Column(name = "rejection_state")
    private RejectionState rejectionState;

    @Basic
    @Column(name = "expiration_date")
    private String expirationDate;

    @Column(name = "ext_retrieve_aet")
    private String externalRetrieveAET;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "dicomattrs_fk")
    private AttributesBlob attributesBlob;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "ref_phys_name_fk")
    private PersonName referringPhysicianName;

    @ManyToOne
    @JoinColumn(name = "accno_issuer_fk")
    private IssuerEntity issuerOfAccessionNumber;

    @ManyToMany
    @JoinTable(name = "rel_study_pcode", 
        joinColumns = @JoinColumn(name = "study_fk", referencedColumnName = "pk"),
        inverseJoinColumns = @JoinColumn(name = "pcode_fk", referencedColumnName = "pk"))
    private Collection<CodeEntity> procedureCodes;

    @OneToMany(mappedBy = "study", cascade=CascadeType.ALL)
    private Collection<StudyQueryAttributes> queryAttributes;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_fk")
    private Patient patient;

    @Override
    public String toString() {
        return "Study[pk=" + pk
                + ", uid=" + studyInstanceUID
                + ", id=" + studyID
                + "]";
    }

    @PrePersist
    public void onPrePersist() {
        Date now = new Date();
        createdTime = now;
        updatedTime = now;
        accessTime = now;
        modifiedTime = now;
    }

    @PreUpdate
    public void onPreUpdate() {
        Date now = new Date();
        updatedTime = now;
        accessTime = now;
    }

    public long getPk() {
        return pk;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public Date getModifiedTime() {
        return modifiedTime;
    }

    public Date getAccessTime() {
        return accessTime;
    }

    public void setAccessTime(Date accessTime) {
        this.accessTime = accessTime;
    }

    public boolean updateAccessTime(Duration maxAccessTimeStaleness) {
        if (maxAccessTimeStaleness == null)
            return false;

        Date now = new Date();
        if (accessTime.getTime() + maxAccessTimeStaleness.getSeconds() * 1000 < now.getTime()) {
            accessTime = now;
            return true;
        }
        return false;
    }

    public String[] getStorageIDs() {
        return StringUtils.split(storageIDs, '\\');
    }

    public boolean addStorageID(String storageID) {
        if (storageID.equals(storageIDs))
            return false;

        if (storageIDs == null) {
            storageIDs = storageID;
            return true;
        }
        TreeSet<String> set = new TreeSet<>(Arrays.asList(getStorageIDs()));
        if (!set.add(storageID))
            return false;

        this.storageIDs = StringUtils.concat(set.toArray(new String[set.size()]), '\\');
        return true;
    }

    public void clearStorageIDs() {
        storageIDs = null;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getStudyID() {
        return studyID;
    }

    public String getStudyDate() {
        return studyDate;
    }

    public String getStudyTime() {
        return studyTime;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public IssuerEntity getIssuerOfAccessionNumber() {
        return issuerOfAccessionNumber;
    }

    public void setIssuerOfAccessionNumber(IssuerEntity issuerOfAccessionNumber) {
        this.issuerOfAccessionNumber = issuerOfAccessionNumber;
    }

    public PersonName getReferringPhysicianName() {
        return referringPhysicianName;
    }
    
    public AttributesBlob getAttributesBlob() {
        return attributesBlob;
    }
    
    public Attributes getAttributes() throws BlobCorruptedException {
        return attributesBlob.getAttributes();
    }

    public String getStudyDescription() {
        return studyDescription;
    }

    public String getStudyCustomAttribute1() {
        return studyCustomAttribute1;
    }

    public String getStudyCustomAttribute2() {
        return studyCustomAttribute2;
    }

    public String getStudyCustomAttribute3() {
        return studyCustomAttribute3;
    }

    public String getAccessControlID() {
        return StringUtils.nullify(accessControlID, "*");
    }

    public void setAccessControlID(String accessControlID) {
        this.accessControlID = StringUtils.maskNull(accessControlID, "*");
    }

    public RejectionState getRejectionState() {
        return rejectionState;
    }

    public void setRejectionState(RejectionState rejectionState) {
        this.rejectionState = rejectionState;
    }

    public LocalDate getExpirationDate() {
        return expirationDate != null ? LocalDate.parse(expirationDate, DateTimeFormatter.BASIC_ISO_DATE) : null;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        if (expirationDate != null) {
            try {
                this.expirationDate = DateTimeFormatter.BASIC_ISO_DATE.format(expirationDate);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else
            this.expirationDate = null;
    }

    public String getFailedSOPInstanceUIDList() {
        return failedSOPInstanceUIDList;
    }

    public void setFailedSOPInstanceUIDList(String failedSOPInstanceUIDList) {
        this.failedSOPInstanceUIDList = failedSOPInstanceUIDList;
    }

    public String getExternalRetrieveAET() {
        return externalRetrieveAET;
    }

    public void setExternalRetrieveAET(String externalRetrieveAET) {
        this.externalRetrieveAET = externalRetrieveAET;
    }

    public Collection<CodeEntity> getProcedureCodes() {
        if (procedureCodes == null)
            procedureCodes = new ArrayList<>();

        return procedureCodes;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter, FuzzyStr fuzzyStr) {
        studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);
        studyID = attrs.getString(Tag.StudyID, "*");
        studyDescription = attrs.getString(Tag.StudyDescription, "*");
        Date dt = attrs.getDate(Tag.StudyDateAndTime);
        if (dt != null) {
            studyDate = DateUtils.formatDA(null, dt);
            studyTime = attrs.containsValue(Tag.StudyTime)
                    ? DateUtils.formatTM(null, dt)
                    : "*";
        } else {
            studyDate = "*";
            studyTime = "*";
        }
        accessionNumber = attrs.getString(Tag.AccessionNumber, "*");
        referringPhysicianName = PersonName.valueOf(
                attrs.getString(Tag.ReferringPhysicianName), fuzzyStr,
                referringPhysicianName);
        studyCustomAttribute1 = 
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute1(), "*");
        studyCustomAttribute2 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute2(), "*");
        studyCustomAttribute3 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute3(), "*");

        if (attributesBlob == null)
            attributesBlob = new AttributesBlob(new Attributes(attrs, filter.getSelection()));
        else
            attributesBlob.setAttributes(new Attributes(attrs, filter.getSelection()));
        modifiedTime = new Date();
    }
}
