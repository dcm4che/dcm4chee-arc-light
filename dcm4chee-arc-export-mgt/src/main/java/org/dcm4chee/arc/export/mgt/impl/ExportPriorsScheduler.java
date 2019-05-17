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

package org.dcm4chee.arc.export.mgt.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.OrderByTag;
import org.dcm4chee.arc.query.util.QueryParam;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.net.Socket;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2018
 */
@ApplicationScoped
public class ExportPriorsScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ExportPriorsScheduler.class);

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @Inject
    private ExportManager exportManager;

    public void onStore(@Observes StoreContext ctx) {
        if (ctx.getException() != null)
            return;

        Calendar now = Calendar.getInstance();
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcdev = arcAE.getArchiveDeviceExtension();
        arcAE.prefetchRules()
                .filter(((Predicate<ExportPriorsRule>) session::isNotProcessed)
                        .and(ctx::isExportReoccurredInstances)
                        .and(rule -> rule.match(
                                session.getRemoteHostName(),
                                session.getCallingAET(),
                                session.getCalledAET(),
                                ctx.getAttributes(), now)))
                .forEach(rule -> {
                    export(ctx, rule, arcdev, now);
                    session.markAsProcessed(rule);
                });
    }

    public void onHL7Connection(@Observes HL7ConnectionEvent event) {
        if (!(event.getType() == HL7ConnectionEvent.Type.MESSAGE_PROCESSED && event.getException() == null))
            return;

        UnparsedHL7Message hl7Message = event.getHL7Message();
        HL7Application hl7App = device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(hl7Message.msh().getReceivingApplicationWithFacility(), true);
        if (hl7App == null)
            return;

        ArchiveHL7ApplicationExtension arcHL7App =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (arcHL7App == null || !arcHL7App.hasHL7ExportRules())
            return;

        Socket sock = event.getSocket();
        String host = ReverseDNS.hostNameOf(sock.getInetAddress());
        HL7Fields hl7Fields = new HL7Fields(hl7Message, hl7App.getHL7DefaultCharacterSet());
        Calendar now = Calendar.getInstance();
        ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        arcHL7App.hl7ExportRules()
                .filter(rule -> rule.match(host, hl7Fields))
                .forEach(rule -> export(sock, hl7Fields, rule, arcdev, now));
    }

    private void export(StoreContext ctx, ExportPriorsRule rule, ArchiveDeviceExtension arcdev, Calendar now) {
        try {
            LOG.info("{}: Apply {}", ctx.getStoreSession(), rule);
            Date notExportedAfter = new Date(
                    now.getTimeInMillis() - rule.getSuppressDuplicateExportInterval().getSeconds() * 1000L);
            Attributes attrs = ctx.getAttributes();
            IDWithIssuer pid = IDWithIssuer.pidOf(attrs);
            String siuid = attrs.getString(Tag.StudyInstanceUID);
            String batchID = rule.getCommonName() + '[' + siuid + ']';
            Map<String, List<ExporterDescriptor>> exporterByAET = Stream.of(rule.getExporterIDs())
                    .map(arcdev::getExporterDescriptorNotNull)
                    .collect(Collectors.groupingBy(ExporterDescriptor::getAETitle));
            if (rule.getEntitySelectors().length == 0) {
                Attributes queryKeys = new Attributes(0);
                exporterByAET.forEach((aet, exporters) ->
                        export(pid, siuid, batchID, queryKeys, -1,
                                arcdev, aet, exporters, notExportedAfter));
            } else {
                Stream.of(rule.getEntitySelectors())
                        .forEach(selector -> {
                            Attributes queryKeys = selector.getQueryKeys(attrs);
                            exporterByAET.forEach((aet, exporters) ->
                                    export(pid, siuid, batchID, queryKeys, selector.getNumberOfPriors(),
                                            arcdev, aet, exporters, notExportedAfter));
                        });
            }
        } catch (Exception e) {
            LOG.warn("{}: Failed to apply {}:\n", ctx.getStoreSession(), rule, e);
        }
    }

    private void export(Socket sock, HL7Fields hl7Fields, HL7ExportRule rule, ArchiveDeviceExtension arcdev, Calendar now) {
        try {
            LOG.info("{}: Apply {}", sock, rule);
            Date notExportedAfter = new Date(
                    now.getTimeInMillis() - rule.getSuppressDuplicateExportInterval().getSeconds() * 1000L);
            String cx = hl7Fields.get("PID-3", null);
            IDWithIssuer pid = rule.ignoreAssigningAuthorityOfPatientID(new IDWithIssuer(cx));
            String batchID = rule.getCommonName() + '[' + pid + ']';
            Map<String, List<ExporterDescriptor>> exporterByAET = Stream.of(rule.getExporterIDs())
                    .map(arcdev::getExporterDescriptorNotNull)
                    .collect(Collectors.groupingBy(ExporterDescriptor::getAETitle));
            if (rule.getEntitySelectors().length == 0) {
                Attributes queryKeys = new Attributes(0);
                exporterByAET.forEach((aet, exporters) ->
                        export(pid, null, batchID, queryKeys, -1,
                                arcdev, aet, exporters, notExportedAfter));
            } else {
                Stream.of(rule.getEntitySelectors())
                        .forEach(selector -> {
                            Attributes queryKeys = selector.getQueryKeys(hl7Fields);
                            exporterByAET.forEach((aet, exporters) ->
                                    export(pid, null, batchID, queryKeys, selector.getNumberOfPriors(),
                                            arcdev, aet, exporters, notExportedAfter));
                        });
            }
        } catch (Exception e) {
            LOG.warn("{}: Failed to apply {}:\n", sock, rule, e);
        }
    }

    private void export(IDWithIssuer pid, String receivedStudyUID, String batchID, Attributes queryKeys,
                        int numberOfPriors, ArchiveDeviceExtension arcdev, String aet,
                        List<ExporterDescriptor> exporters, Date notExportedAfter) {
        ApplicationEntity ae = arcdev.getDevice().getApplicationEntity(aet, true);
        QueryContext queryCtx = queryService.newQueryContext(ae, new QueryParam(ae));
        queryCtx.setQueryRetrieveLevel(QueryRetrieveLevel2.STUDY);
        queryCtx.setPatientIDs(pid);
        queryCtx.setQueryKeys(queryKeys);
        queryCtx.setOrderByTags(Collections.singletonList(OrderByTag.desc(Tag.StudyDate)));
        int remaining = numberOfPriors;
        try (Query query = queryService.createStudyQuery(queryCtx)) {
            query.executeQuery(arcdev.getQueryFetchSize());
            while (query.hasMoreMatches() && remaining != 0) {
                Attributes match = query.nextMatch();
                String suid;
                if (match != null && !(suid = match.getString(Tag.StudyInstanceUID)).equals(receivedStudyUID)) {
                    exporters.forEach(exporter ->
                        exportManager.scheduleStudyExport(suid, exporter, notExportedAfter, batchID));
                    --remaining;
                }
            }
        } catch (DicomServiceException e) {
            throw new RuntimeException(e);
        }
    }
}
