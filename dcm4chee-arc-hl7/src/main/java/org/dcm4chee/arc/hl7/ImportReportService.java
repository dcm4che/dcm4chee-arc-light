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
 * Portions created by the Initial Developer are Copyright (C) 2013-2019
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

package org.dcm4chee.arc.hl7;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.data.*;
import org.dcm4che3.hl7.ERRSegment;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.hl7.service.DefaultHL7Service;
import org.dcm4che3.net.hl7.service.HL7Service;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2016
 */
@ApplicationScoped
@Typed(HL7Service.class)
class ImportReportService extends DefaultHL7Service {
    private final Logger LOG = LoggerFactory.getLogger(ImportReportService.class);
    private static final String ACCESSION_NUMBER = "OBR^1^18^1^1";
    private static final String MIME_TYPE_ENCODING = "OBX^1^5^1^2";
    private static final String ENCAPSULATED_DOC_DATA = "OBX^1^5^1^5";
    private static final String STUDY_UID = "OBX^1^5^1^1";
    private static final String ADMISSION_ID = "PV1^1^19^1^1";

    @Inject
    private PatientService patientService;

    @Inject
    private StoreService storeService;

    @Inject
    private ProcedureService procedureService;

    @Inject
    private CFindSCU cfindscu;

    public ImportReportService() {
        super("ORU^R01");
    }

    @Override
    public UnparsedHL7Message onMessage(HL7Application hl7App, Connection conn, Socket s, UnparsedHL7Message msg)
            throws HL7Exception {
        ArchiveHL7Message archiveHL7Message = new ArchiveHL7Message(
                HL7Message.makeACK(msg.msh(), HL7Exception.AA, null).getBytes(null));

        PatientMgtContext ctx = patientService.createPatientMgtContextHL7(hl7App, conn, s, msg);
        try {
            transform(ctx);
            if (ctx.getAttributes() == null) {
                LOG.info("No actions configured on receive of HL7 ORU message.");
                return archiveHL7Message;
            }

            ctx.setPatient(PatientUpdateService.updatePatient(ctx, patientService, archiveHL7Message));
            importReport(ctx);
        } catch(HL7Exception e) {
            throw e;
        } catch (Exception e) {
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.APPLICATION_RECORD_LOCKED)
                            .setUserMessage(e.getMessage()));
        }
        return archiveHL7Message;
    }

    private void transform(PatientMgtContext ctx) throws Exception {
        HL7Application hl7App = ctx.getHL7Application();
        ArchiveHL7ApplicationExtension arcHL7App = hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        UnparsedHL7Message msg = ctx.getUnparsedHL7Message();
        HL7ORUAction[] hl7ORUActions = arcHL7App.hl7ORUAction();
        if (hl7ORUActions.length == 0)
            return;

        String aet = arcHL7App.getAETitle();
        if (aet == null) {
            throw new ConfigurationException("No AE Title associated with HL7 Application: "
                    + hl7App.getApplicationName());
        }
        ApplicationEntity ae = hl7App.getDevice().getApplicationEntity(aet, true);
        if (ae == null)
            throw new ConfigurationException("No local AE with AE Title " + aet
                    + " associated with HL7 Application: " + hl7App.getApplicationName());

        Issuer hl7PrimaryAssigningAuthorityOfPatientID = arcHL7App.hl7PrimaryAssigningAuthorityOfPatientID();
        try {
            ctx.setAttributes(SAXTransformer.transform(
                    msg,
                    arcHL7App,
                    arcHL7App.importReportTemplateURI(),
                    tr -> {
                        if (hl7PrimaryAssigningAuthorityOfPatientID != null)
                            tr.setParameter("hl7PrimaryAssigningAuthorityOfPatientID",
                                    hl7PrimaryAssigningAuthorityOfPatientID.toString());
                        arcHL7App.importReportTemplateParams().forEach(tr::setParameter);
                    }));
        }  catch (Exception e) {
            throw new HL7Exception(new ERRSegment(msg.msh()).setUserMessage(e.getMessage()), e);
        }
    }

    private void importReport(PatientMgtContext ctx) throws Exception {
        if (ctx.getPatient() == null) {
            LOG.info("Abort creation of report, as no patient associated with report was created / updated.");
            return;
        }

        Attributes attrs = ctx.getAttributes();
        UnparsedHL7Message msg = ctx.getUnparsedHL7Message();
        HL7Application hl7App = ctx.getHL7Application();
        ArchiveHL7ApplicationExtension arcHL7App = hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        ApplicationEntity ae = hl7App.getDevice().getApplicationEntity(arcHL7App.getAETitle(), true);
        if (!attrs.contains(Tag.SOPClassUID) && !attrs.contains(Tag.MIMETypeOfEncapsulatedDocument))
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                            .setErrorLocation(MIME_TYPE_ENCODING)
                            .setUserMessage("Invalid encoding of encapsulated document in components 2 and/or 3 and/or 4 of field 5"));

        if (attrs.containsValue(Tag.MIMETypeOfEncapsulatedDocument) && isNotEncapsulatedDoc(attrs))
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                            .setErrorLocation(ENCAPSULATED_DOC_DATA)
                            .setUserMessage("Encapsulated document data missing"));

        if (!attrs.containsValue(Tag.AdmissionID))
            adjustAdmissionID(arcHL7App, attrs, msg);

        if (!attrs.containsValue(Tag.StudyInstanceUID)) {
            String accNo = attrs.getString(Tag.AccessionNumber);
            List<String> suids = storeService.studyIUIDsByAccessionNo(accNo);
            if (suids.isEmpty()) {
                String cFindSCP = arcHL7App.hl7ImportReportMissingStudyIUIDCFindSCP();
                if (cFindSCP != null) {
                    suids = cfindscu.findStudiesByAccessionNumber(
                                    ae, cFindSCP, 0, accNo, Tag.StudyInstanceUID).stream()
                            .map(match -> match.getString(Tag.StudyInstanceUID))
                            .collect(Collectors.toList());
                }
            }
            switch (suids.size()) {
                case 0:
                    adjustStudyIUID(attrs, arcHL7App, msg.msh());
                    break;
                case 1:
                    attrs.setString(Tag.StudyInstanceUID, VR.UI, suids.get(0));
                    break;
                default:
                    mstore(ctx, ae, suids);
                    return;
            }
        }
        if (!attrs.containsValue(Tag.SOPInstanceUID))
            attrs.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        if (!attrs.containsValue(Tag.SeriesInstanceUID))
            attrs.setString(Tag.SeriesInstanceUID, VR.UI,
                    UIDUtils.createNameBasedUID(attrs.getBytes(Tag.SOPInstanceUID)));
        if (arcHL7App.hl7ImportReportAdjustIUID() == HL7ImportReportAdjustIUID.APPEND_HASH_OF_STUDY_INSTANCE_UID)
            appendToSeriesAndSOPInstanceUID(attrs, "." + (attrs.getString(Tag.StudyInstanceUID).hashCode() & 0xFFFFFFFFL));
        ensureStudyInstanceUIDInPredecessors(attrs);
        processHL7ORUAction(ae, ctx);
    }

    private static void appendToSeriesAndSOPInstanceUID(Attributes attrs, String suffix) {
        attrs.setString(Tag.SOPInstanceUID, VR.UI, attrs.getString(Tag.SOPInstanceUID) + suffix);
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, attrs.getString(Tag.SeriesInstanceUID) + suffix);
        Sequence predecessors = attrs.getSequence(Tag.PredecessorDocumentsSequence);
        if (predecessors != null)
            for (Attributes predecessor : predecessors) {
                for (Attributes refSeries : predecessor.getSequence(Tag.ReferencedSeriesSequence)) {
                    refSeries.setString(Tag.SeriesInstanceUID, VR.UI, refSeries.getString(Tag.SeriesInstanceUID) + suffix);
                    for (Attributes refSOP : refSeries.getSequence(Tag.ReferencedSOPSequence))
                        refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, refSOP.getString(Tag.ReferencedSOPInstanceUID) + suffix);
                }
            }
    }

    private void adjustAdmissionID(ArchiveHL7ApplicationExtension arcHL7App, Attributes attrs, UnparsedHL7Message msg)
            throws HL7Exception {
        HL7ImportReportMissingAdmissionIDPolicy hl7ImportReportMissingAdmissionIDPolicy
                = arcHL7App.hl7ImportReportMissingAdmissionIDPolicy();
        HL7Segment msh = msg.msh();
        switch (hl7ImportReportMissingAdmissionIDPolicy) {
            case REJECT:
                throw new HL7Exception(
                        new ERRSegment(msh)
                                .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                                .setErrorLocation(ADMISSION_ID)
                                .setUserMessage("Missing admission ID"));
            case ACCESSION_AS_ADMISSION:
                String accNo = attrs.getString(Tag.AccessionNumber);
                if (accNo == null)
                    throw new HL7Exception(
                            new ERRSegment(msh)
                                    .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                                    .setErrorLocation(ACCESSION_NUMBER)
                                    .setUserMessage("Failed to derive Admission ID from accession number"));
                attrs.setString(Tag.AdmissionID, VR.LO, accNo);
        }
    }

    private boolean isNotEncapsulatedDoc(Attributes attrs) {
        if (attrs.getString(Tag.MIMETypeOfEncapsulatedDocument).equals("text/xml")) {
            String cdaTxt = attrs.getString(Tag.TextValue);
            if (cdaTxt == null)
                return true;

            attrs.setBytes(Tag.EncapsulatedDocument, VR.OB, cdaTxt.getBytes());
            attrs.remove(Tag.TextValue);
        }

        return !attrs.containsValue(Tag.EncapsulatedDocument);
    }

    private void adjustStudyIUID(Attributes attrs, ArchiveHL7ApplicationExtension arcHL7App, HL7Segment msh)
            throws HL7Exception {
        String accessionNum = attrs.getString(Tag.AccessionNumber);
        String reqProcID = attrs.getNestedDataset(Tag.ReferencedRequestSequence).getString(Tag.RequestedProcedureID);
        String studyIUID = null;
        if (reqProcID != null) {
            studyIUID = UIDUtils.createNameBasedUID(reqProcID.getBytes());
            LOG.info("Derived StudyInstanceUID from RequestedProcedureID[={}] : {} ",
                    reqProcID, studyIUID);
        } else switch (arcHL7App.hl7ImportReportMissingStudyIUIDPolicy()) {
            case REJECT:
                throw new HL7Exception(
                        new ERRSegment(msh)
                                .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                                .setErrorLocation(STUDY_UID)
                                .setUserMessage("Missing Study Instance UID"));
            case ACCESSION_BASED:
                if (accessionNum == null)
                    throw new HL7Exception(
                            new ERRSegment(msh)
                                    .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                                    .setErrorLocation(ACCESSION_NUMBER)
                                    .setUserMessage("Missing accession number"));
                else {
                    studyIUID = UIDUtils.createNameBasedUID(accessionNum.getBytes());
                    LOG.info("Derived StudyInstanceUID from AccessionNumber[={}] : {}",
                            accessionNum, studyIUID);
                }
                break;
            case GENERATE:
                studyIUID = UIDUtils.createUID();
                break;
        }
        attrs.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
    }

    private void store(ApplicationEntity ae, PatientMgtContext ctx) throws Exception {
        try (StoreSession session = storeService.newStoreSession(ae, ctx)) {
            StoreContext storeCtx = storeService.newStoreContext(session);
            storeCtx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
            storeService.store(storeCtx, ctx.getAttributes());
        }
    }

    private void ensureStudyInstanceUIDInPredecessors(Attributes attrs) {
        Sequence predecessors = attrs.getSequence(Tag.PredecessorDocumentsSequence);
        if (predecessors == null)
            return;

        for (Attributes predecessor : predecessors)
            if (predecessor.getString(Tag.StudyInstanceUID) == null)
                predecessor.setString(Tag.StudyInstanceUID, VR.UI, attrs.getStrings(Tag.StudyInstanceUID));
    }

    private void processHL7ORUAction(ApplicationEntity ae, PatientMgtContext ctx) throws Exception {
        HL7Application hl7App = ctx.getHL7Application();
        for (HL7ORUAction action : hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class)
                                          .hl7ORUAction())
            if (action == HL7ORUAction.IMPORT_REPORT)
                store(ae, ctx);
            else
                procedureService.updateMWLStatus(ctx.getAttributes().getString(Tag.StudyInstanceUID), SPSStatus.COMPLETED);
    }

    private void mstore(PatientMgtContext ctx, ApplicationEntity ae, List<String> suids) throws Exception {
        Attributes attrs = ctx.getAttributes();
        int n = suids.size();
        Sequence seq = attrs.newSequence(Tag.IdenticalDocumentsSequence, n);
        for (String suid : suids)
            seq.add(refStudy(suid, attrs));
        for (int i = 0; i < n; i++) {
            Attributes refStudy = seq.remove(i);
            Attributes refSeries = refStudy.getNestedDataset(Tag.ReferencedSeriesSequence);
            Attributes refSOP = refSeries.getNestedDataset(Tag.ReferencedSOPSequence);
            attrs.setString(Tag.StudyInstanceUID, VR.UI, refStudy.getString(Tag.StudyInstanceUID));
            attrs.setString(Tag.SeriesInstanceUID, VR.UI, refSeries.getString(Tag.SeriesInstanceUID));
            attrs.setString(Tag.SOPInstanceUID, VR.UI, refSOP.getString(Tag.ReferencedSOPInstanceUID));
            ensureStudyInstanceUIDInPredecessors(attrs);
            processHL7ORUAction(ae, ctx);
            seq.add(i, refStudy);
        }
    }

    private Attributes refStudy(String studyUID, Attributes attrs) {
        Attributes refStudyAttrs = new Attributes(2);
        refStudyAttrs.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
        refStudyAttrs.newSequence(Tag.ReferencedSeriesSequence, 2).add(refSeries(attrs));
        return refStudyAttrs;
    }

    private Attributes refSeries(Attributes attrs) {
        Attributes refSeriesAttrs = new Attributes(2);
        refSeriesAttrs.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        refSeriesAttrs.newSequence(Tag.ReferencedSOPSequence, 1).add(refSOP(attrs));
        return refSeriesAttrs;
    }

    private Attributes refSOP(Attributes attrs) {
        Attributes refSOP = new Attributes(2);
        refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
        refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, attrs.getString(Tag.SOPClassUID));
        return refSOP;
    }
}
