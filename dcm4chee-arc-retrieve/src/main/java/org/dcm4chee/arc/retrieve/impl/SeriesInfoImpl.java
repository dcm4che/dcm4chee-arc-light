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
 * Portions created by the Initial Developer are Copyright (C) 2013
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

import org.dcm4chee.arc.retrieve.SeriesInfo;

import java.util.Date;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2016
 */
public class SeriesInfoImpl implements SeriesInfo {
    private final String studyInstanceUID;
    private final String seriesInstanceUID;
    private final int failedRetrieves;
    private final String failedSOPInstanceUIDList;
    private final Date updatedTime;
    private final String expirationDate;
    private final String sourceAET;

    public SeriesInfoImpl(String studyInstanceUID, String seriesInstanceUID, int failedRetrieves,
            String failedSOPInstanceUIDList, Date updatedTime, String expirationDate, String sourceAET) {
        this.studyInstanceUID = studyInstanceUID;
        this.seriesInstanceUID = seriesInstanceUID;
        this.failedRetrieves = failedRetrieves;
        this.failedSOPInstanceUIDList = failedSOPInstanceUIDList;
        this.updatedTime = updatedTime;
        this.expirationDate = expirationDate;
        this.sourceAET = sourceAET;
    }

    @Override
    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    @Override
    public String getSeriesInstanceUID() {
        return seriesInstanceUID;
    }

    @Override
    public int getFailedRetrieves() {
        return failedRetrieves;
    }

    @Override
    public String getFailedSOPInstanceUIDList() {
        return failedSOPInstanceUIDList;
    }

    @Override
    public Date getUpdatedTime() {
        return updatedTime;
    }

    @Override
    public String getExpirationDate() {
        return expirationDate;
    }

    @Override
    public String getSourceAET() {
        return sourceAET;
    }
}
