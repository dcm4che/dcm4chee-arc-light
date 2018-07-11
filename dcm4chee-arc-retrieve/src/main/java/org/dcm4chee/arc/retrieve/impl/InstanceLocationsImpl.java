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

package org.dcm4chee.arc.retrieve.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.store.InstanceLocations;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
public class InstanceLocationsImpl implements InstanceLocations {
    private final String sopClassUID;
    private final String sopInstanceUID;
    private final Attributes attributes;
    private Long instancePk;
    private Attributes rejectionCode;
    private String retrieveAETs;
    private String extRetrieveAET;
    private Availability availability;
    private Date createdTime;
    private Date updatedTime;
    private boolean containsMetadata;
    private final ArrayList<Location> locations = new ArrayList<>(1);

    public InstanceLocationsImpl(Attributes attrs) {
        this.sopClassUID = attrs.getString(Tag.SOPClassUID);
        this.sopInstanceUID = attrs.getString(Tag.SOPInstanceUID);
        this.attributes = attrs;
    }

    @Override
    public void setInstancePk(Long instancePk) {
        this.instancePk = instancePk;
    }

    public void setRejectionCode(Attributes rejectionCode) {
        this.rejectionCode = rejectionCode;
    }

    public void setRetrieveAETs(String retrieveAETs) {
        this.retrieveAETs = retrieveAETs;
    }

    public void setExternalRetrieveAET(String extRetrieveAET) {
        this.extRetrieveAET = extRetrieveAET;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }

    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }

    public void setUpdatedTime(Date updatedTime) {
        this.updatedTime = updatedTime;
    }

    public void setContainsMetadata(boolean containsMetadata) {
        this.containsMetadata = containsMetadata;
    }

    @Override
    public String toString() {
        return "Instance[iuid=" + sopInstanceUID + ",cuid=" + sopClassUID + "]";
    }

    @Override
    public Long getInstancePk() {
        return instancePk;
    }

    @Override
    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    @Override
    public String getSopClassUID() {
        return sopClassUID;
    }

    @Override
    public List<Location> getLocations() {
        return locations;
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public String getRetrieveAETs() {
        return retrieveAETs;
    }

    @Override
    public String getExternalRetrieveAET() {
        return extRetrieveAET;
    }

    @Override
    public Availability getAvailability() {
        return availability;
    }

    @Override
    public Date getCreatedTime() { return createdTime; }

    @Override
    public Date getUpdatedTime() { return updatedTime; }

    @Override
    public Attributes getRejectionCode() {
        return rejectionCode;
    }

    @Override
    public boolean isContainsMetadata() {
        return containsMetadata;
    }
}
