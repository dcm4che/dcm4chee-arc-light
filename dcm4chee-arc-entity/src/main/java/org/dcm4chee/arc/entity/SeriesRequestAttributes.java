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
import org.dcm4che3.data.Issuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.soundex.FuzzyStr;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Feb 29, 2008
 */
@Entity
@Table(name = "series_req", indexes = {
    @Index(columnList = "accession_no"),
    @Index(columnList = "req_service"),
    @Index(columnList = "req_proc_id"),
    @Index(columnList = "sps_id"),
    @Index(columnList = "study_iuid")
})
public class SeriesRequestAttributes {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "accession_no")
    private String accessionNumber;

    @Column(name = "accno_entity_id")
    private String accessionNumberLocalNamespaceEntityID;

    @Column(name = "accno_entity_uid")
    private String accessionNumberUniversalEntityID;

    @Column(name = "accno_entity_uid_type")
    private String accessionNumberUniversalEntityIDType;

    @Basic(optional = false)
    @Column(name = "study_iuid")
    private String studyInstanceUID;

    @Basic(optional = false)
    @Column(name = "req_proc_id")
    private String requestedProcedureID;

    @Basic(optional = false)
    @Column(name = "sps_id")
    private String scheduledProcedureStepID;

    @Basic(optional = false)
    @Column(name = "req_service")
    private String requestingService;

    @OneToOne(cascade=CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "req_phys_name_fk")
    private PersonName requestingPhysician;
    
    public SeriesRequestAttributes() {}

    public SeriesRequestAttributes(Attributes attrs, FuzzyStr fuzzyStr) {
        studyInstanceUID = attrs.getString(Tag.StudyInstanceUID, "*");
        accessionNumber = attrs.getString(Tag.AccessionNumber, "*");
        Issuer issuer = Issuer.valueOf(attrs.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
        if (issuer != null) {
            accessionNumberLocalNamespaceEntityID = issuer.getLocalNamespaceEntityID();
            accessionNumberUniversalEntityID = issuer.getUniversalEntityID();
            accessionNumberUniversalEntityIDType = issuer.getUniversalEntityIDType();
        } else {
            accessionNumberLocalNamespaceEntityID = null;
            accessionNumberUniversalEntityID = null;
            accessionNumberUniversalEntityIDType = null;
        }
        requestedProcedureID = attrs.getString(Tag.RequestedProcedureID, "*");
        scheduledProcedureStepID = attrs.getString(
                Tag.ScheduledProcedureStepID, "*");
        requestingService = attrs.getString(Tag.RequestingService, "*");
        requestingPhysician = PersonName.valueOf(
                attrs.getString(Tag.RequestingPhysician), fuzzyStr, null);
    }

    public long getPk() {
        return pk;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getRequestedProcedureID() {
        return requestedProcedureID;
    }

    public String getScheduledProcedureStepID() {
        return scheduledProcedureStepID;
    }

    public String getRequestingService() {
        return requestingService;
    }

    public PersonName getRequestingPhysician() {
        return requestingPhysician;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    @Override
    public String toString() {
        return "RequestAttributes[pk=" + pk
                + ", suid=" + studyInstanceUID
                + ", rpid=" + requestedProcedureID
                + ", spsid=" + scheduledProcedureStepID
                + "]";
    }

}
