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

package org.dcm4chee.arc.export.pr2ko;

import org.dcm4che3.data.*;
import org.dcm4che3.dict.archive.ArchiveTag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2019
 */
public class PR2KOExporter extends AbstractExporter {
    private static final ElementDictionary dict = ElementDictionary.getStandardElementDictionary();
    private static final String DOC_TITLE = "(PR2KO, DCM4CHEE, \"Referenced by Presentation State\")";
    private static final String KO_DESC = "(113012, DCM, \"Key Object Description\")";
    private static final String LANGUAGE = "(en, RFC5646, \"English\")";

    private final RetrieveService retrieveService;
    private final StoreService storeService;
    private final Attributes propsAttrs = new Attributes();

    private static final int[] TYPE2_ATTRS = {
            Tag.SpecificCharacterSet,
            Tag.PatientName,
            Tag.PatientID,
            Tag.PatientBirthDate,
            Tag.PatientSex,
            Tag.StudyDate,
            Tag.StudyTime,
            Tag.ReferringPhysicianName,
            Tag.StudyID,
            Tag.AccessionNumber,
            Tag.ReferencedPerformedProcedureStepSequence,
            Tag.Manufacturer
    };

    private int[] IUID_TAGS = {
        Tag.SeriesInstanceUID,
        Tag.SOPInstanceUID
    };

    public PR2KOExporter(ExporterDescriptor descriptor, RetrieveService retrieveService, StoreService storeService) {
        super(descriptor);
        this.retrieveService = retrieveService;
        this.storeService = storeService;
        descriptor.getProperties().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("KO"))
                .forEach(entry -> {
                    int tag = TagUtils.forName(entry.getKey().substring(3));
                    propsAttrs.setString(tag, dict.vrOf(tag), entry.getValue());
                });
    }

    @Override
    public Outcome export(ExportContext ctx) throws Exception {
        ApplicationEntity ae;
        List<Attributes> results = new ArrayList<>();
        try (RetrieveContext retrieveContext = retrieveService.newRetrieveContext(
                ctx.getAETitle(),
                ctx.getStudyInstanceUID(),
                ctx.getSeriesInstanceUID(),
                ctx.getSopInstanceUID())) {
            ae = retrieveContext.getLocalApplicationEntity();
            retrieveContext.setHttpServletRequestInfo(ctx.getHttpServletRequestInfo());
            if (!retrieveService.calculateMatches(retrieveContext))
                return new Outcome(QueueMessage.Status.WARNING, noMatches(ctx));

            retrieveContext.getMatches().stream()
                    .filter(instLoc -> isPresentationState(instLoc, descriptor.getExportURI().getSchemeSpecificPart().split(":")))
                    .forEach(instanceLocation -> pr2ko(instanceLocation, results));
        }
        if (results.isEmpty())
            return new Outcome(QueueMessage.Status.COMPLETED, noKeyObjectCreated(ctx));

        String sourceAET = descriptor.getProperty("SourceAET", null);
        try (StoreSession session = storeService.newStoreSession(
                ctx.getHttpServletRequestInfo(), ae, sourceAET)) {
            for (Attributes ko : results) {
                ko.setString(ArchiveTag.SendingApplicationEntityTitleOfSeries, VR.AE, sourceAET);
                StoreContext storeCtx = storeService.newStoreContext(session);
                storeCtx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
                storeService.store(storeCtx, ko);
            }
        }
        return new Outcome(QueueMessage.Status.COMPLETED, toMessage(ctx, results.size()));
    }

    private void pr2ko(InstanceLocations instanceLocation, List<Attributes> results) {
        Attributes inst = instanceLocation.getAttributes();
        Attributes attrs = new Attributes(inst, TYPE2_ATTRS);
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        attrs.setDate(Tag.ContentDateAndTime, new Date());
        setUIDs(attrs, inst);
        attrs.setString(Tag.Modality, VR.CS, "KO");
        attrs.setString(Tag.ValueType, VR.CS, "CONTAINER");
        attrs.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
        attrs.addAll(propsAttrs);
        setAttribute(attrs, Tag.SeriesDescription, KeyObjectProperty.SeriesDescription, inst);
        setAttribute(attrs, Tag.SeriesNumber, KeyObjectProperty.SeriesNumber, inst);
        setAttribute(attrs, Tag.InstanceNumber, KeyObjectProperty.InstanceNumber, inst);
        attrs.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(new Code(descriptor.getProperties().getOrDefault("DocumentTitle", DOC_TITLE)).toItem());
        attrs.newSequence(Tag.ContentTemplateSequence, 1).add(templateIdentifier());
        addContentSeq(attrs, inst);
        addCurrentRequestedProcedureEvidenceSeq(attrs, inst);
        results.add(attrs);
    }

    private void addCurrentRequestedProcedureEvidenceSeq(Attributes attrs, Attributes inst) {
        String studyIUID = inst.getString(Tag.StudyInstanceUID);
        String seriesIUID = inst.getString(Tag.SeriesInstanceUID);
        String iuid = inst.getString(Tag.SOPInstanceUID);
        String cuid = inst.getString(Tag.SOPClassUID);
        Sequence evidenceSeq = attrs.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 2);
        refSOPSeq(refSeriesSeq(studyIUID, evidenceSeq), seriesIUID).add(refSOP(cuid, iuid));
        addPresentationStateRefSerSeq(inst, evidenceSeq);
    }

    private void addPresentationStateRefSerSeq(Attributes inst, Sequence evidenceSeq) {
        Sequence presentationStateRefSerSeq = inst.getSequence(Tag.ReferencedSeriesSequence);
        Sequence refSeriesSeq = refSeriesSeq(inst.getString(Tag.StudyInstanceUID), evidenceSeq);
        presentationStateRefSerSeq.forEach(presentationStateRefSer -> {
            String seriesIUID = presentationStateRefSer.getString(Tag.SeriesInstanceUID);
            Sequence refSOPSeq = refSOPSeq(refSeriesSeq, seriesIUID);
            presentationStateRefSer.getSequence(Tag.ReferencedImageSequence)
                    .forEach(prRefSop -> refSOPSeq.add(refSOP(
                                            prRefSop.getString(Tag.ReferencedSOPClassUID),
                                            prRefSop.getString(Tag.ReferencedSOPInstanceUID))));
        });
    }

    private Sequence refSeriesSeq(String studyIUID, Sequence evidenceSeq) {
        Attributes refStudy = new Attributes(2);
        Sequence refSeriesSeq = refStudy.newSequence(Tag.ReferencedSeriesSequence, 10);
        refStudy.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        evidenceSeq.add(refStudy);
        return refSeriesSeq;
    }

    private Sequence refSOPSeq(Sequence refSeriesSeq, String seriesIUID) {
        Attributes refSeries = new Attributes(5);
        Sequence refSOPSeq = refSeries.newSequence(Tag.ReferencedSOPSequence, 100);
        refSeries.setString(Tag.SeriesInstanceUID, VR.UI, seriesIUID);
        refSeriesSeq.add(refSeries);
        return refSOPSeq;
    }

    private Attributes refSOP(String cuid, String iuid) {
        Attributes item = new Attributes(2);
        item.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        return item;
    }

    private Attributes templateIdentifier() {
        Attributes attrs = new Attributes(2);
        attrs.setString(Tag.MappingResource, VR.CS, "DCMR");
        attrs.setString(Tag.TemplateIdentifier, VR.CS, "2010");
        return attrs ;
    }

    private void setUIDs(Attributes attrs, Attributes inst) {
        for (int tag : IUID_TAGS)
            attrs.setString(tag, VR.UI, UIDUtils.createNameBasedUID(inst.getString(tag).getBytes()));
        attrs.setString(Tag.StudyInstanceUID, VR.UI, inst.getString(Tag.StudyInstanceUID));
    }

    private void addContentSeq(Attributes attrs, Attributes inst) {
        Sequence contentSeq = attrs.newSequence(Tag.ContentSequence, 2);
        contentSeq.add(new Code(descriptor.getProperties().getOrDefault("Language", LANGUAGE)).toItem());
        contentSeq.add(keyObjectDescription(inst));
    }

    private Attributes keyObjectDescription(Attributes inst) {
        Attributes item = new Attributes(4);
        item.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        item.setString(Tag.ValueType, VR.CS, "TEXT");
        item.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(new Code(KO_DESC).toItem());
        setAttribute(item, Tag.TextValue, KeyObjectProperty.KeyObjectDescription, inst);
        return item;
    }

    private void setAttribute(Attributes attrs, int tag, KeyObjectProperty koProperty, Attributes inst) {
        String propsAttr = propsAttrs.getString(tag);
        attrs.setString(
                tag,
                koProperty.getVr(),
                new AttributesFormat(propsAttr != null ? propsAttr : koProperty.getDefVal()).format(inst));
    }

    private boolean isPresentationState(InstanceLocations inst, String... cuids) {
        String sopCUID = inst.getAttributes().getString(Tag.SOPClassUID);
        for (String cuidInUri : cuids)
            if (sopCUID.equals(cuidInUri)
                    || UID.nameOf(sopCUID).replaceAll(" ", "").equals(cuidInUri))
                return true;

        return false;
    }

    private int toTag(String tagOrKeyword) {
        try {
            return Integer.parseInt(tagOrKeyword, 16);
        } catch (IllegalArgumentException e) {
            int tag = ElementDictionary.tagForKeyword(tagOrKeyword, null);
            if (tag == -1)
                throw new IllegalArgumentException(tagOrKeyword);
            return tag;
        }
    }

    private static String noMatches(ExportContext ctx) {
        return appendEntity(ctx, new StringBuilder("Could not find ")).toString();
    }

    private static String noKeyObjectCreated(ExportContext ctx) {
        return appendEntity(ctx, new StringBuilder("No Key Object created for ")).toString();
    }

    private enum KeyObjectProperty {
        KeyObjectDescription("{00700080} - {00700081}", VR.UT),
        SeriesDescription("{0008103E}", VR.LO),
        SeriesNumber("{00200011,offset,100}", VR.IS),
        InstanceNumber("{00200013,offset,100}", VR.IS);

        private String defVal;
        private VR vr;

        KeyObjectProperty(String defVal, VR vr) {
            this.defVal = defVal;
            this.vr = vr;
        }

        String getDefVal() {
            return defVal;
        }

        VR getVr() {
            return vr;
        }
    }

    private static StringBuilder appendEntity(ExportContext exportContext, StringBuilder sb) {
        String studyInstanceUID = exportContext.getStudyInstanceUID();
        String seriesInstanceUID = exportContext.getSeriesInstanceUID();
        String sopInstanceUID = exportContext.getSopInstanceUID();
        if (sopInstanceUID != null && !sopInstanceUID.equals("*"))
            sb.append("Instance[uid=").append(sopInstanceUID).append("] of ");
        if (seriesInstanceUID != null && !seriesInstanceUID.equals("*"))
            sb.append("Series[uid=").append(seriesInstanceUID).append("] of ");
        return sb.append("Study[uid=").append(studyInstanceUID).append("]");
    }

    private static String toMessage(ExportContext ctx, int numKOs) {
        return appendEntity(ctx,
                new StringBuilder("Created ")
                        .append(numKOs)
                        .append(" Key Object(s)."))
                .toString();
    }
}
