/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.export.impax.report;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.impax.report.ImpaxReportConverter;
import org.dcm4chee.arc.impax.report.ReportServiceProvider;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;

import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
public class ImpaxReportExporter extends AbstractExporter {
    private final QueryService queryService;
    private final ReportServiceProvider reportService;
    private final StoreService storeService;
    private final Device device;

    protected ImpaxReportExporter(ExporterDescriptor descriptor, QueryService queryService,
                                  ReportServiceProvider reportService, StoreService storeService, Device device) {
        super(descriptor);
        this.queryService = queryService;
        this.reportService = reportService;
        this.storeService = storeService;
        this.device = device;
    }

    @Override
    public Outcome export(ExportContext ctx) throws Exception {
        String studyUID = ctx.getStudyInstanceUID();
        Attributes studyAttrs = queryService.getStudyAttributes(studyUID);
        if (studyAttrs == null)
            return new Outcome(QueueMessage.Status.WARNING, "No such Study: " + studyUID);

        ApplicationEntity ae = device.getApplicationEntity(ctx.getAETitle(), true);
        Map<String, String> props =
                device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getImpaxReportProperties();
        ImpaxReportConverter converter = new ImpaxReportConverter(props, studyAttrs);
        List<String> xmlReports = reportService.queryReportByStudyUid(studyUID);
        List<Attributes> srReports = converter.convert(xmlReports);
        try (StoreSession session = storeService.newStoreSession(ctx.getHttpServletRequestInfo(), ae, props.get("SourceAET"))) {
            session.setImpaxReportEndpoint(converter.getEndpoint());
            for (Attributes sr : srReports) {
                StoreContext storeCtx = storeService.newStoreContext(session);
                storeCtx.setImpaxReportPatientMismatch(converter.patientMismatchOf(sr));
                storeCtx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
                storeService.store(storeCtx, sr);
            }
        }
        return xmlReports.isEmpty()
                ? new Outcome(QueueMessage.Status.WARNING, "No Report for Study: " + studyUID + " found")
                : new Outcome(statusOf(srReports), messageOf(studyUID, srReports));
    }

    private QueueMessage.Status statusOf(List<Attributes> srReports) {
        Attributes sr = srReports.get(srReports.size() - 1);
        String[] flags = StringUtils.split(descriptor.getExportURI().getSchemeSpecificPart(), '/');
        return (flags[0].equals("*") || flags[0].equals(sr.getString(Tag.CompletionFlag)))
                && (flags.length == 1 || flags[1].equals("*") || flags[1].equals(sr.getString(Tag.VerificationFlag)))
                ? QueueMessage.Status.COMPLETED
                : QueueMessage.Status.WARNING;
    }

    private String messageOf(String studyUID, List<Attributes> srReports) {
        if (srReports.size() > 1)
            return "Imported " + srReports.size() + " Reports for Study: " + studyUID;

        Attributes sr = srReports.get(0);
        return "Imported " + sr.getString(Tag.CompletionFlag) + '/' + sr.getString(Tag.VerificationFlag)
                + " Report for Study: " + studyUID;
    }

}
