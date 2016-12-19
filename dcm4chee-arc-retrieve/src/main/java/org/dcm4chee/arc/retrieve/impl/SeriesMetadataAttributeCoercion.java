/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2016
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.retrieve.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.AttributesCoercion;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.ArchiveTag;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.QInstance;
import org.dcm4chee.arc.entity.QSeries;
import org.dcm4chee.arc.entity.QStudy;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.SeriesInfo;
import org.dcm4chee.arc.retrieve.StudyInfo;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2016
 */
public class SeriesMetadataAttributeCoercion implements AttributesCoercion {
    private final RetrieveContext ctx;
    private final InstanceLocations inst;

    public SeriesMetadataAttributeCoercion(RetrieveContext ctx, InstanceLocations inst) {
        this.ctx = ctx;
        this.inst = inst;
    }

    @Override
    public void coerce(Attributes attrs, Attributes modified) {
        attrs.setString(Tag.RetrieveAETitle, VR.AE, inst.getRetrieveAETs());
        attrs.setString(Tag.InstanceAvailability, VR.CS, inst.getAvailability().toString());

        StudyInfo studyInfo = ctx.getStudyInfos().get(0);
        if (studyInfo.getExpirationDate() != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyExpirationDate, VR.DA,
                    studyInfo.getExpirationDate());
        if (studyInfo.getAccessControlID() != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyAccessControlID, VR.LO,
                    studyInfo.getAccessControlID());

        SeriesInfo seriesInfo = ctx.getSeriesInfos().get(0);
        if (seriesInfo.getExpirationDate() != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesExpirationDate, VR.DA,
                    seriesInfo.getExpirationDate());
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SendingApplicationEntityTitleOfSeries, VR.AE,
                seriesInfo.getSourceAET());

        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.InstanceReceiveDateTime, VR.DT,
                inst.getCreatedTime());
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.InstanceUpdateDateTime, VR.DT,
                inst.getUpdatedTime());
        if (inst.getRejectionCode() != null)
            attrs.newSequence(ArchiveTag.PrivateCreator, ArchiveTag.RejectionCodeSequence, 1).
                    add(inst.getRejectionCode());
        if (inst.getExternalRetrieveAET() != null) {
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.InstanceExternalRetrieveAETitle, VR.AE,
                    inst.getExternalRetrieveAET());
        }
        for (Location location : inst.getLocations()) {
            if (location.getObjectType() == Location.ObjectType.DICOM_FILE) {
                attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StorageID, VR.LO,
                        location.getStorageID());
                attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StoragePath, VR.LO,
                        StringUtils.split(location.getStoragePath(), '/'));
                attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StorageTransferSyntaxUID, VR.UI,
                        location.getTransferSyntaxUID());
                attrs.setInt(ArchiveTag.PrivateCreator, ArchiveTag.StorageObjectSize, VR.UL,
                        (int) location.getSize());
                if (location.getDigestAsHexString() != null)
                    attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StorageObjectDigest, VR.LO,
                            location.getDigestAsHexString());
            }
        }
    }
}
