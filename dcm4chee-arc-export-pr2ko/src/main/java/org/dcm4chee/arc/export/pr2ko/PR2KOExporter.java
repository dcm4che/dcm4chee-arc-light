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
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryParam;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;

import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2019
 */
public class PR2KOExporter extends AbstractExporter {
    private static final ElementDictionary dict = ElementDictionary.getStandardElementDictionary();
    private static final String KEY_OBJECT_DESCRIPTION = "KeyObjectDescription";
    private static final String DOCUMENT_TITLE = "DocumentTitle";
    private static final String LANGUAGE = "Language";
    private static final String[] DEF_PROPS = {
            DOCUMENT_TITLE, "(PR2KO, DCM4CHEE, \"Referenced by Presentation State\")",
            LANGUAGE, "(en, RFC5646, \"English\")",
            KEY_OBJECT_DESCRIPTION, "{00700080} - {00700081}",
            "KO.Manufacturer", "",
            "KO.SeriesDescription", "{0008103E}",
            "KO.SeriesNumber", "{00200011,offset,100}",
            "KO.InstanceNumber", "{00200013,offset,0}"
    };

    private final QueryService queryService;
    private final StoreService storeService;
    private final int[] patStudyTags;
    private final Device device;
    private final Map<String,String> properties = new HashMap<>();

    PR2KOExporter(ExporterDescriptor descriptor, QueryService queryService, StoreService storeService, Device device) {
        super(descriptor);
        this.queryService = queryService;
        this.storeService = storeService;
        this.patStudyTags = patStudyTags(device.getDeviceExtension(ArchiveDeviceExtension.class));
        this.device = device;
        for (int i = 1; i < DEF_PROPS.length; i++, i++) {
            properties.put(DEF_PROPS[i-1], DEF_PROPS[i]);
        }
        descriptor.getProperties().forEach(properties::put);
    }

    private static int[] patStudyTags(ArchiveDeviceExtension arcdev) {
        int[] patTags = arcdev.getAttributeFilter(Entity.Patient).getSelection(false);
        int[] studyTags = arcdev.getAttributeFilter(Entity.Study).getSelection(false);
        int[] dst = new int[patTags.length + studyTags.length - 1];
        System.arraycopy(patTags, 0, dst, 0, patTags.length);
        System.arraycopy(studyTags, 1, dst, patTags.length, studyTags.length - 1);
        Arrays.sort(dst);
        return dst;
    }

    @Override
    public Outcome export(ExportContext ctx) throws Exception {
        ApplicationEntity ae = device.getApplicationEntity(ctx.getAETitle(), true);
        List<Attributes> matches = new ArrayList<>();
        try (Query query = queryService.createInstanceQuery(
                queryContext(ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(), ctx.getSopInstanceUID(), ae))) {
            query.executeQuery(device.getDeviceExtension(ArchiveDeviceExtension.class).getQueryFetchSize());
            while (query.hasMoreMatches()) {
                Attributes match = query.nextMatch();
                if (match != null)
                    matches.add(match);
            }
        }

        if (matches.isEmpty())
            return new Outcome(QueueMessage.Status.COMPLETED, noKeyObjectCreated(ctx));

        try (StoreSession session = storeService.newStoreSession(
                ctx.getHttpServletRequestInfo(), ae, properties.get("SourceAET"))) {
            for (Attributes prAttrs : matches) {
                StoreContext storeCtx = storeService.newStoreContext(session);
                storeCtx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
                storeService.store(storeCtx, pr2ko(prAttrs));
            }
        }
        return new Outcome(QueueMessage.Status.COMPLETED, toMessage(ctx, matches.size()));
    }

    private QueryContext queryContext(String studyIUID, String seriesIUID, String sopIUID, ApplicationEntity ae) {
        QueryContext ctx = queryService.newQueryContext(ae, new QueryParam(ae));
        ctx.setQueryRetrieveLevel(QueryRetrieveLevel2.IMAGE);
        ctx.setQueryKeys(queryKeys(studyIUID, seriesIUID, sopIUID));
        return ctx;
    }

    private Attributes queryKeys(String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID) {
        Attributes keys = new Attributes();
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        if (seriesInstanceUID != null)
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        if (sopInstanceUID != null)
            keys.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUID);
        keys.setString(Tag.SOPClassUID, VR.UI, cuids());
        return keys;
    }

    private String[] cuids() {
        return Arrays.stream(StringUtils.split(descriptor.getExportURI().getSchemeSpecificPart(),':'))
                .map(PR2KOExporter::toUID)
                .toArray(String[]::new);
    }

    private static String toUID(String uidOrName) {
        return uidOrName.startsWith("1") ? uidOrName : UID.forName(uidOrName);
    }

    private Attributes pr2ko(Attributes prAttrs) {
        Attributes koAttrs = new Attributes(prAttrs, patStudyTags);
        Date now = new Date();
        koAttrs.setDate(Tag.InstanceCreationDateAndTime, now);
        koAttrs.setDate(Tag.ContentDateAndTime, contentDateAndTime(prAttrs, now));
        koAttrs.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        koAttrs.setString(Tag.SOPInstanceUID, VR.UI,
                UIDUtils.createNameBasedUID(prAttrs.getString(Tag.SOPInstanceUID).getBytes()));
        koAttrs.setString(Tag.SeriesInstanceUID, VR.UI,
                UIDUtils.createNameBasedUID(prAttrs.getString(Tag.SeriesInstanceUID).getBytes()));
        koAttrs.setString(Tag.Modality, VR.CS, "KO");
        koAttrs.setString(Tag.ValueType, VR.CS, "CONTAINER");
        koAttrs.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
        koAttrs.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
        properties.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("KO"))
                .forEach(entry -> setString(koAttrs, entry, prAttrs));
        koAttrs.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(new Code(properties.get(DOCUMENT_TITLE)).toItem());
        koAttrs.newSequence(Tag.ContentTemplateSequence, 1).add(templateIdentifier());
        addContentSeq(koAttrs, prAttrs);
        addCurrentRequestedProcedureEvidenceSeq(koAttrs, prAttrs);
        return koAttrs;
    }

    private static Date contentDateAndTime(Attributes prAttrs, Date now) {
        Date date = prAttrs.getDate(Tag.PresentationCreationDateAndTime);
        if (date == null) {
            date = prAttrs.getDate(Tag.ContentDateAndTime);
            if (date == null) {
                date = now;
            }
        }
        return date;
    }

    private static void setString(Attributes attrs, Map.Entry<String, String> entry, Attributes prAttrs) {
        int tag = TagUtils.forName(entry.getKey().substring(3));
        attrs.setString(tag, dict.vrOf(tag), new AttributesFormat(entry.getValue()).format(prAttrs));
    }

    private static void addCurrentRequestedProcedureEvidenceSeq(Attributes attrs, Attributes prAttrs) {
        String studyIUID = prAttrs.getString(Tag.StudyInstanceUID);
        String seriesIUID = prAttrs.getString(Tag.SeriesInstanceUID);
        String iuid = prAttrs.getString(Tag.SOPInstanceUID);
        String cuid = prAttrs.getString(Tag.SOPClassUID);
        Sequence evidenceSeq = attrs.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 2);
        refSOPSeq(refSeriesSeq(studyIUID, evidenceSeq), seriesIUID).add(refSOP(cuid, iuid));
        addPresentationStateRefSerSeq(prAttrs, evidenceSeq);
    }

    private static void addPresentationStateRefSerSeq(Attributes prAttrs, Sequence evidenceSeq) {
        Sequence presentationStateRefSerSeq = prAttrs.getSequence(Tag.ReferencedSeriesSequence);
        Sequence refSeriesSeq = refSeriesSeq(prAttrs.getString(Tag.StudyInstanceUID), evidenceSeq);
        presentationStateRefSerSeq.forEach(presentationStateRefSer -> {
            String seriesIUID = presentationStateRefSer.getString(Tag.SeriesInstanceUID);
            Sequence refSOPSeq = refSOPSeq(refSeriesSeq, seriesIUID);
            presentationStateRefSer.getSequence(Tag.ReferencedImageSequence)
                    .forEach(prRefSop -> refSOPSeq.add(refSOP(
                                            prRefSop.getString(Tag.ReferencedSOPClassUID),
                                            prRefSop.getString(Tag.ReferencedSOPInstanceUID))));
        });
    }

    private static Sequence refSeriesSeq(String studyIUID, Sequence evidenceSeq) {
        Attributes refStudy = new Attributes(2);
        Sequence refSeriesSeq = refStudy.newSequence(Tag.ReferencedSeriesSequence, 10);
        refStudy.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        evidenceSeq.add(refStudy);
        return refSeriesSeq;
    }

    private static Sequence refSOPSeq(Sequence refSeriesSeq, String seriesIUID) {
        Attributes refSeries = new Attributes(5);
        Sequence refSOPSeq = refSeries.newSequence(Tag.ReferencedSOPSequence, 100);
        refSeries.setString(Tag.SeriesInstanceUID, VR.UI, seriesIUID);
        refSeriesSeq.add(refSeries);
        return refSOPSeq;
    }

    private static Attributes refSOP(String cuid, String iuid) {
        Attributes item = new Attributes(2);
        item.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        return item;
    }

    private static Attributes templateIdentifier() {
        Attributes attrs = new Attributes(2);
        attrs.setString(Tag.MappingResource, VR.CS, "DCMR");
        attrs.setString(Tag.TemplateIdentifier, VR.CS, "2010");
        return attrs ;
    }

    private void addContentSeq(Attributes attrs, Attributes prAttrs) {
        Sequence contentSeq = attrs.newSequence(Tag.ContentSequence, 2);
        contentSeq.add(new Code(properties.get(LANGUAGE)).toItem());
        contentSeq.add(keyObjectDescription(prAttrs));
        prAttrs.getSequence(Tag.ReferencedSeriesSequence)
                .forEach(presentationStateRefSer ->
                    presentationStateRefSer.getSequence(Tag.ReferencedImageSequence)
                            .forEach(prRefSop -> contentSeq.add(imageRef(prRefSop, prAttrs))));
    }

    private Attributes keyObjectDescription(Attributes prAttrs) {
        Attributes item = new Attributes(4);
        item.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        item.setString(Tag.ValueType, VR.CS, "TEXT");
        item.newSequence(Tag.ConceptNameCodeSequence, 1).add(keyObjectDescriptionConceptName());
        item.setString(Tag.TextValue, VR.UT,
                new AttributesFormat(properties.get(KEY_OBJECT_DESCRIPTION)).format(prAttrs));
        return item;
    }

    private static Attributes keyObjectDescriptionConceptName() {
        Attributes codeItem = new Attributes(3);
        codeItem.setString(Tag.CodeValue, VR.SH, "113012");
        codeItem.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
        codeItem.setString(Tag.CodeMeaning, VR.LO, "Key Object Description");
        return codeItem;
    }

    private static String noKeyObjectCreated(ExportContext ctx) {
        return appendEntity(ctx, new StringBuilder("No Key Object created for ")).toString();
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
                        .append(" Key Object(s) for "))
                .toString();
    }

    private static Attributes imageRef(Attributes prRefSop, Attributes prAttrs) {
        Attributes item = new Attributes(3);
        item.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        item.setString(Tag.ValueType, VR.CS, "IMAGE");
        Attributes refSOP = refSOP(
                prRefSop.getString(Tag.ReferencedSOPClassUID),
                prRefSop.getString(Tag.ReferencedSOPInstanceUID));
        refSOP.newSequence(Tag.ReferencedSOPSequence, 1).add(refSOP(
                prAttrs.getString(Tag.SOPClassUID),
                prAttrs.getString(Tag.SOPInstanceUID)));
        item.newSequence(Tag.ReferencedSOPSequence, 1).add(refSOP);
        return item;
    }
}
