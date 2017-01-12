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

package org.dcm4chee.arc.query;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.entity.SeriesQueryAttributes;
import org.dcm4chee.arc.entity.StudyQueryAttributes;
import org.dcm4chee.arc.query.util.QueryParam;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
public interface QueryService {

    QueryContext newQueryContextFIND(Association as, String sopClassUID, EnumSet<QueryOption> queryOpts);

    QueryContext newQueryContextQIDO(
            HttpServletRequest httpRequest, String searchMethod, ApplicationEntity ae, QueryParam queryParam);

    Query createQuery(QueryContext ctx);

    Query createPatientQuery(QueryContext ctx);

    Query createStudyQuery(QueryContext ctx);

    Query createSeriesQuery(QueryContext ctx);

    Query createInstanceQuery(QueryContext ctx);

    Query createMWLQuery(QueryContext ctx);

    Attributes getSeriesAttributes(Long seriesPk, QueryParam queryParam);

    StudyQueryAttributes calculateStudyQueryAttributes(Long studyPk, QueryParam queryParam);

    SeriesQueryAttributes calculateSeriesQueryAttributesIfNotExists(Long seriesPk, QueryParam queryParam);

    SeriesQueryAttributes calculateSeriesQueryAttributes(Long seriesPk, QueryRetrieveView qrView);

    Attributes getStudyAttributesWithSOPInstanceRefs(
            String studyUID, ApplicationEntity ae, Collection<Attributes> seriesAttrs);

    Attributes createIAN(ApplicationEntity ae, String studyInstanceUID, String seriesInstanceUID,
                         Availability instanceAvailability, String... retrieveAETs);

    Attributes createRejectionNote(
            ApplicationEntity ae, String studyUID, String seriesUID, String objectUID, RejectionNote rjNote);

    Attributes createRejectionNote(Attributes sopInstanceRefs, RejectionNote rjNote);

    Attributes createActionInfo(String studyIUID, String seriesIUID, String sopIUID, ApplicationEntity ae);

    List<Object[]> getSeriesInstanceUIDs(String studyUID);

    List<Object[]> getSOPInstanceUIDs(String studyUID);

    List<Object[]> getSOPInstanceUIDs(String studyUID, String seriesUID);

    ZipInputStream openZipInputStream(QueryContext ctx, String storageID, String storagePath) throws IOException;
}
