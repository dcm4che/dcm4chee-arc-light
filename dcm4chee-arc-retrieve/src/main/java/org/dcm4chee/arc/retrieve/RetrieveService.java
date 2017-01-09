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

package org.dcm4chee.arc.retrieve;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.AttributesCoercion;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.entity.Series;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
public interface RetrieveService {

    int MOVE_DESTINATION_NOT_ALLOWED = 0xC801;

    String MOVE_DESTINATION_NOT_ALLOWED_MSG = "Move Destination not allowed";

    RetrieveContext newRetrieveContextGET(ArchiveAEExtension arcAE,
            Association as, Attributes cmd, QueryRetrieveLevel2 qrLevel, Attributes keys);

    RetrieveContext newRetrieveContextMOVE(ArchiveAEExtension arcAE,
            Association as, Attributes cmd, QueryRetrieveLevel2 qrLevel, Attributes keys)
            throws ConfigurationException;

    RetrieveContext newRetrieveContextWADO(
            HttpServletRequest request, String localAET, String studyUID, String seriesUID, String objectUID);

    RetrieveContext newRetrieveContextSTORE(
            String localAET, String studyUID, String seriesUID, String objectUID, String destAET)
            throws ConfigurationException;

    RetrieveContext newRetrieveContextIOCM(
            HttpServletRequest request, String localAET, String studyUID, String... seriesUIDs);

    RetrieveContext newRetrieveContextSeriesMetadata(Series.MetadataUpdate metadataUpdate);

    boolean calculateMatches(RetrieveContext ctx) throws DicomServiceException;

    InstanceLocations newInstanceLocations(Attributes attrs);

    Transcoder openTranscoder(RetrieveContext ctx, InstanceLocations inst, Collection<String> tsuids, boolean fmi)
            throws IOException;

    DicomInputStream openDicomInputStream(RetrieveContext ctx, InstanceLocations inst) throws IOException;

    Attributes loadMetadata(RetrieveContext ctx, InstanceLocations inst) throws IOException;

    Map<String,Collection<InstanceLocations>> removeNotAccessableMatches(RetrieveContext ctx);

    AttributesCoercion getAttributesCoercion(RetrieveContext ctx, InstanceLocations inst);

    void waitForPendingCStoreForward(RetrieveContext ctx);

    void waitForPendingCMoveForward(RetrieveContext ctx);

    void updateFailedSOPInstanceUIDList(RetrieveContext ctx);

    Date getLastModifiedFromMatches(RetrieveContext ctx);

    Date getLastModified(RetrieveContext ctx);
}
