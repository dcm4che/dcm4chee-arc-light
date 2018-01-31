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

package org.dcm4chee.arc.query.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.util.OrderByTag;
import org.dcm4chee.arc.query.util.QueryParam;
import org.dcm4chee.arc.storage.Storage;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
class QueryContextImpl implements QueryContext {
    private Association as;
    private HttpServletRequest httpRequest;
    private final ApplicationEntity ae;
    private final QueryParam queryParam;
    private final QueryService queryService;
    private QueryRetrieveLevel2 qrLevel;
    private IDWithIssuer[] patientIDs = {};
    private Attributes queryKeys;
    private Attributes returnKeys;
    private String sopClassUID;
    private String searchMethod;
    private final HashMap<String, Storage> storageMap = new HashMap<>();
    private List<OrderByTag> orderByTags;

    public QueryContextImpl(HttpServletRequest httpRequest, String searchMethod, ApplicationEntity ae,
                            QueryParam queryParam, QueryService queryService) {
        this(ae, queryParam, queryService);
        this.httpRequest = httpRequest;
        this.searchMethod = searchMethod;
    }

    private QueryContextImpl(ApplicationEntity ae, QueryParam queryParam, QueryService queryService) {
        this.ae = ae;
        this.queryService = queryService;
        this.queryParam = queryParam;
    }

    public QueryContextImpl(Association as, String sopClassUID, ApplicationEntity ae, QueryParam queryParam,
                            QueryServiceImpl queryService) {
        this(ae, queryParam, queryService);
        this.as = as;
        this.sopClassUID = sopClassUID;
    }

    @Override
    public QueryRetrieveLevel2 getQueryRetrieveLevel() {
        return qrLevel;
    }

    @Override
    public void setQueryRetrieveLevel(QueryRetrieveLevel2 qrLevel) {
        this.qrLevel = qrLevel;
    }

    @Override
    public Association getAssociation() {
        return as;
    }

    @Override
    public String getSOPClassUID() {
        return sopClassUID;
    }

    @Override
    public void setSOPClassUID(String sopClassUID) {
        this.sopClassUID = sopClassUID;
    }

    @Override
    public String getSearchMethod() {
        return searchMethod;
    }

    @Override
    public HttpServletRequest getHttpRequest() {
        return httpRequest;
    }

    @Override
    public ApplicationEntity getLocalApplicationEntity() {
        return ae;
    }

    @Override
    public String getCalledAET() {
        return as != null ? as.getCalledAET() : ae.getAETitle();
    }

    @Override
    public String getCallingAET() {
        return as != null ? as.getCallingAET() : null;
    }

    @Override
    public String getRemoteHostName() {
        return httpRequest != null ? httpRequest.getRemoteHost() : as.getSocket().getInetAddress().getHostName();
    }

    @Override
    public ArchiveAEExtension getArchiveAEExtension() {
        return ae.getAEExtension(ArchiveAEExtension.class);
    }

    @Override
    public IDWithIssuer[] getPatientIDs() {
        return patientIDs;
    }

    @Override
    public void setPatientIDs(IDWithIssuer... patientIDs) {
        this.patientIDs = patientIDs != null ? patientIDs : IDWithIssuer.EMPTY;
    }

    @Override
    public QueryService getQueryService() {
        return queryService;
    }

    @Override
    public Attributes getReturnKeys() {
        return returnKeys;
    }

    @Override
    public void setReturnKeys(Attributes returnKeys) {
        this.returnKeys = returnKeys;
    }

    @Override
    public Attributes getQueryKeys() {
        return queryKeys;
    }

    @Override
    public void setQueryKeys(Attributes keys) {
        this.queryKeys = keys;
    }

    @Override
    public QueryParam getQueryParam() {
        return queryParam;
    }

    @Override
    public boolean containsUniqueKey() {
        return patientIDs.length > 0
                || queryKeys.containsValue(Tag.StudyInstanceUID)
                || qrLevel.compareTo(QueryRetrieveLevel2.SERIES) >= 0 && (queryKeys.containsValue(Tag.SeriesInstanceUID)
                || qrLevel == QueryRetrieveLevel2.IMAGE && queryKeys.containsValue(Tag.SOPInstanceUID));
    }

    @Override
    public boolean isOrderByPatientName() {
        return orderByTags != null && orderByTags.stream().anyMatch(x -> x.tag == Tag.PatientName);
    }

    @Override
    public List<OrderByTag> getOrderByTags() {
        return orderByTags;
    }

    @Override
    public void setOrderByTags(List<OrderByTag> orderByTags) {
        this.orderByTags = orderByTags;
    }

    @Override
    public boolean isConsiderPurgedInstances() {
        return qrLevel == QueryRetrieveLevel2.IMAGE
                && getArchiveDeviceExtension().getPurgeInstanceRecordsPollingInterval() != null
                && queryKeys.containsValue(Tag.SeriesInstanceUID);
    }

    private ArchiveDeviceExtension getArchiveDeviceExtension() {
        return ae.getDevice().getDeviceExtension(ArchiveDeviceExtension.class);
    }

    @Override
    public Storage getStorage(String storageID) {
        return storageMap.get(storageID);
    }

    @Override
    public void putStorage(String storageID, Storage storage) {
        storageMap.put(storageID, storage);
    }

    @Override
    public void close() {
        for (Storage storage : storageMap.values())
            SafeClose.close(storage);
    }
}
