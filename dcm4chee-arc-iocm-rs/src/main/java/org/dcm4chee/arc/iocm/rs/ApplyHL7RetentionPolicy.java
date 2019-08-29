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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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

package org.dcm4chee.arc.iocm.rs;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.conf.HL7Fields;
import org.dcm4chee.arc.conf.HL7StudyRetentionPolicy;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryParam;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2018
 */
@ApplicationScoped
public class ApplyHL7RetentionPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(ApplyHL7RetentionPolicy.class);

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @Inject
    private StudyService studyService;

    public void onHL7Connection(@Observes HL7ConnectionEvent event) {
        if (event.getType() != HL7ConnectionEvent.Type.MESSAGE_PROCESSED
            || event.getException() != null)
            return;

        UnparsedHL7Message msg = event.getHL7Message();
        HL7Application hl7App = device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(msg.msh().getReceivingApplicationWithFacility(), true);
        if (hl7App == null)
            return;

        ArchiveHL7ApplicationExtension arcHL7App =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (arcHL7App == null || !arcHL7App.hasHL7StudyRetentionPolicies())
            return;

        String host = ReverseDNS.hostNameOf(event.getSocket().getInetAddress());
        HL7Fields hl7Fields = new HL7Fields(msg, hl7App.getHL7DefaultCharacterSet());
        arcHL7App.hl7StudyRetentionPolicies()
                .sorted(Comparator.comparingInt(HL7StudyRetentionPolicy::getPriority).reversed())
                .filter(rule -> rule.match(host, hl7Fields))
                .findFirst()
                .ifPresent(policy -> apply(event, policy, hl7Fields));
    }

    private void apply(HL7ConnectionEvent event, HL7StudyRetentionPolicy policy, HL7Fields hl7Fields) {
        LOG.info("{}: Apply {}:", event.getSocket(), policy);
        IDWithIssuer pid = new IDWithIssuer(hl7Fields.get("PID-3", null));
        ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        ApplicationEntity ae = device.getApplicationEntity(policy.getAETitle(), true);
        QueryParam queryParam = queryParam(policy, ae);
        QueryContext queryCtx = queryService.newQueryContext(ae, queryParam);
        queryCtx.setQueryRetrieveLevel(QueryRetrieveLevel2.STUDY);
        queryCtx.setPatientIDs(pid);
        queryCtx.setQueryKeys(new Attributes(0));
        queryCtx.setReturnPrivate(true);
        try (Query query = queryService.createStudyQuery(queryCtx)) {
            query.executeQuery(arcdev.getQueryFetchSize());
            while (query.hasMoreMatches()) {
                Attributes match = query.nextMatch();
                if (match != null)
                    updateExpirationDate(event, policy, match);
            }
        } catch (Exception e) {
            LOG.warn("{}: Failed to apply {}:\n", event.getSocket(), policy, e);
        }
    }

    private QueryParam queryParam(HL7StudyRetentionPolicy policy, ApplicationEntity ae) {
        QueryParam queryParam = new QueryParam(ae);
        if (policy.protectStudy())
            queryParam.setExpirationState(ExpirationState.UPDATEABLE, ExpirationState.FROZEN);
        else
            queryParam.setExpirationState(ExpirationState.UPDATEABLE);
        return queryParam;
    }

    private void updateExpirationDate(HL7ConnectionEvent event, HL7StudyRetentionPolicy policy, Attributes match) {
        StudyMgtContext ctx = studyService.createStudyMgtContextHL7(event.getSocket(), event.getHL7Message());
        String suid = match.getString(Tag.StudyInstanceUID);
        ctx.setStudyInstanceUID(suid);
        ctx.setExpirationExporterID(policy.getExporterID());
        ctx.setFreezeExpirationDate(policy.isFreezeExpirationDate());

        if (policy.protectStudy()) {
            ctx.setExpirationDate(null);
            updateExpirationDate(ctx, event);
        }
        else {
            LocalDate prevExpirationDate = studyExpirationDateOf(match);
            LocalDate expirationDate = prevExpirationDate;
            LocalDate retentionStartDate = policy.retentionStartDate(match);
            if (policy.getMinRetentionPeriod() != null) {
                LocalDate minExpirationDate = retentionStartDate.plus(policy.getMinRetentionPeriod());
                if (expirationDate == null || expirationDate.isBefore(minExpirationDate)) {
                    expirationDate = minExpirationDate;
                }
            }
            if (policy.getMaxRetentionPeriod() != null) {
                LocalDate maxExpirationDate = retentionStartDate.plus(policy.getMaxRetentionPeriod());
                if (expirationDate == null || expirationDate.isAfter(maxExpirationDate)) {
                    expirationDate = maxExpirationDate;
                }
            }
            if (expirationDate != prevExpirationDate) {
                ctx.setExpirationDate(expirationDate);
                updateExpirationDate(ctx, event);
            }
        }
    }

    private void updateExpirationDate(StudyMgtContext ctx, HL7ConnectionEvent event) {
        try {
            studyService.updateExpirationDate(ctx);
            LOG.info("{}: Update expiration date of Study[uid={}]", event.getSocket(), ctx.getStudyInstanceUID());
        } catch (Exception e) {
            LOG.warn("{}: Failed to update expiration date of Study[uid={}]:\n",
                    event.getSocket(), ctx.getStudyInstanceUID(), e);
        }
    }

    private LocalDate studyExpirationDateOf(Attributes match) {
        String s = match.getString(PrivateTag.PrivateCreator, PrivateTag.StudyExpirationDate);
        return s != null ? LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE) : null;
    }

}
