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

package org.dcm4chee.arc.export.curve2pr;

import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Entity;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2019
 */
public class Curve2PRExporter extends AbstractExporter {
    private static final ElementDictionary dict = ElementDictionary.getStandardElementDictionary();
    private static final int[] TOP_LEFT = {1, 1};
    private static final int[] VOI_LUT_TAGS = {
            Tag.WindowCenter,
            Tag.WindowWidth,
            Tag.WindowCenterWidthExplanation,
            Tag.VOILUTFunction,
            Tag.VOILUTSequence
    };
    private static final int[] MOD_LUT_TAGS = {
            Tag.RescaleIntercept,
            Tag.RescaleSlope,
            Tag.RescaleType,
            Tag.ModalityLUTSequence
    };

    private static final String GraphicLayer = "GraphicLayer";
    private static final String GraphicLayerOrder = "GraphicLayerOrder";
    private static final String GraphicLayerRecommendedDisplayGrayscaleValue = "GraphicLayerRecommendedDisplayGrayscaleValue";
    private static final String[] DEF_PROPS = {
            GraphicLayer, "CURVEDATA",
            GraphicLayerOrder, "1",
            GraphicLayerRecommendedDisplayGrayscaleValue, "65535",
            "PR.Manufacturer", "",
            "PR.ContentCreatorName", "",
            "PR.SeriesDescription", "{0008103E}",
            "PR.SeriesNumber", "{00200011,offset,100}",
            "PR.ContentLabel", "CURVEDATA",
            "PR.ContentDescription", "Created from Curve Data in Image(s)"
    };

    private final RetrieveService retrieveService;
    private final StoreService storeService;
    private final int[] patStudyTags;
    private final Map<String,String> properties = new HashMap<>();

    Curve2PRExporter(ExporterDescriptor descriptor, RetrieveService retrieveService, StoreService storeService) {
        super(descriptor);
        this.retrieveService = retrieveService;
        this.storeService = storeService;
        this.patStudyTags = patStudyTags(retrieveService.getArchiveDeviceExtension());
        for (int i = 1; i < DEF_PROPS.length; i++, i++) {
            properties.put(DEF_PROPS[i-1], DEF_PROPS[i]);
        }
        descriptor.getProperties().forEach(properties::put);
    }

    private int[] patStudyTags(ArchiveDeviceExtension arcdev) {
        int[] patTags = arcdev.getAttributeFilter(Entity.Patient).getSelection(false);
        int[] studyTags = arcdev.getAttributeFilter(Entity.Study).getSelection(false);
        int[] dst = new int[patTags.length + studyTags.length - 1 + MOD_LUT_TAGS.length];
        System.arraycopy(patTags, 0, dst, 0, patTags.length);
        System.arraycopy(studyTags, 1, dst, patTags.length, studyTags.length - 1);
        System.arraycopy(MOD_LUT_TAGS, 0, dst, dst.length - MOD_LUT_TAGS.length, MOD_LUT_TAGS.length);
        Arrays.sort(dst);
        return dst;
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

            for (InstanceLocations instanceLocations : retrieveContext.getMatches()) {
                if (isImage(instanceLocations))
                    curve2pr(retrieveContext, instanceLocations, results);
            }
        }
        if (results.isEmpty())
            return new Outcome(QueueMessage.Status.COMPLETED, noPresentationStateCreated(ctx));

        int totInstanceRefs = 0;
        int instanceRefs;
        try (StoreSession session = storeService.newStoreSession(
                ctx.getHttpServletRequestInfo(), ae, properties.get("SourceAET"))) {
            for (Attributes pr : results) {
                totInstanceRefs += instanceRefs = countInstanceRefs(pr);
                trimSoftcopyVOILUT(pr, instanceRefs);
                trimDisplayAreaSelection(pr);
                StoreContext storeCtx = storeService.newStoreContext(session);
                storeCtx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
                storeService.store(storeCtx, pr);
            }
        }
        return new Outcome(QueueMessage.Status.COMPLETED, toMessage(ctx, results.size(), totInstanceRefs));
    }

    private int countInstanceRefs(Attributes pr) {
        return (int) pr.getSequence(Tag.ReferencedSeriesSequence).stream()
                .mapToInt(x -> x.getSequence(Tag.ReferencedImageSequence).size())
                .count();
    }

    private boolean isImage(InstanceLocations inst) {
        Attributes attrs = inst.getAttributes();
        return attrs.getInt(Tag.Rows, -1) > 0
                && attrs.getInt(Tag.Columns, -1) > 0;
    }

    private void curve2pr(RetrieveContext ctx, InstanceLocations inst, List<Attributes> results) throws IOException {
        Attributes metadata = loadWithoutPixelData(ctx, inst);
        float[] pixelSpacing = metadata.getFloats(Tag.PixelSpacing);
        byte[] curveData;
        Attributes graphicAnnotationItem = null;
        for (int offset = 0; (curveData = metadata.getBytes(Tag.CurveData | offset)) != null; offset += 0x20000) {
            if (isConvertable(curveData, metadata, offset)) {
                if (graphicAnnotationItem == null)
                    graphicAnnotationItem = graphicAnnotationItem(ctx, inst, results, metadata);
                addPolyline(graphicAnnotationItem, VR.FL.toFloats(curveData, false), pixelSpacing);
            }
        }
    }

    private static Attributes loadWithoutPixelData(RetrieveContext ctx, InstanceLocations inst) throws IOException {
        try (DicomInputStream dis = ctx.getRetrieveService().openDicomInputStream(ctx, inst)) {
            Attributes attrs = dis.readDataset(-1, Tag.PixelData);
            ctx.getRetrieveService().getAttributesCoercion(ctx, inst).coerce(attrs, null);
            return attrs;
        }
    }

    private static boolean isConvertable(byte[] curveData, Attributes metadata, int offset) {
        return curveData.length == 16
                && metadata.getInt(Tag.CurveDimensions | offset, -1) == 2
                && metadata.getInt(Tag.NumberOfPoints | offset, -1) == 2
                && "LINE".equals(metadata.getString(Tag.TypeOfData | offset))
                && metadata.getInt(Tag.DataValueRepresentation | offset, -1) == 2;
    }

    private Attributes graphicAnnotationItem(RetrieveContext ctx, InstanceLocations inst,
            List<Attributes> results, Attributes metadata) {
        Attributes pr = createPR(ctx, results, metadata);
        imageRef(seriesRef(pr, inst.getAttributes().getString(Tag.SeriesInstanceUID)), inst);
        Attributes voiLUT = new Attributes(metadata, VOI_LUT_TAGS);
        if (!voiLUT.isEmpty()) {
            imageRef(softcopyVOILUTItem(pr, voiLUT), inst);
        }
        Attributes graphicAnnotationItem = new Attributes(4);
        pr.ensureSequence(Tag.GraphicAnnotationSequence, 10).add(graphicAnnotationItem);
        imageRef(graphicAnnotationItem, inst);
        graphicAnnotationItem.setString(Tag.GraphicLayer, VR.CS, properties.get(GraphicLayer));
        imageRef(displayAreaSelectionItem(
                    pr, metadata.getInt(Tag.Rows, 1), metadata.getInt(Tag.Columns, 1)),
                inst);
        return graphicAnnotationItem;
    }

    private Attributes createPR(RetrieveContext ctx, List<Attributes> results, Attributes metadata) {
        Attributes modLUT = new Attributes(metadata, MOD_LUT_TAGS);
        for (Attributes pr : results) {
            if (modLUT.equals(new Attributes(pr, MOD_LUT_TAGS)))
                return pr;
        }
        String instanceNumber = Integer.toString(results.size() + 1);
        String seriesInstanceUID = seriesInstanceUID(ctx, results);
        String sopInstanceUID = sopInstanceUID(instanceNumber, seriesInstanceUID);
        Attributes pr = new Attributes();
        pr.setString(Tag.InstanceNumber, VR.IS, instanceNumber);
        pr.addSelected(metadata, patStudyTags);
        properties.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("PR"))
                .forEach(entry -> setString(pr, entry, metadata));
        pr.setString(Tag.SOPClassUID, VR.UI, UID.GrayscaleSoftcopyPresentationStateStorage);
        pr.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUID);
        pr.setString(Tag.Modality, VR.CS, "PR");
        pr.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        pr.setString(Tag.ImageHorizontalFlip, VR.CS, "N");
        pr.setInt(Tag.ImageRotation, VR.US, 0);
        pr.newSequence(Tag.GraphicLayerSequence, 1).add(graphicLayerItem());
        Date now = new Date();
        pr.setDate(Tag.InstanceCreationDateAndTime, now);
        pr.setDate(Tag.PresentationCreationDateAndTime, now);
        results.add(pr);
        return pr;
    }

    private static void setString(Attributes pr, Map.Entry<String, String> entry, Attributes metadata) {
        int tag = TagUtils.forName(entry.getKey().substring(3));
        pr.setString(tag, dict.vrOf(tag), new AttributesFormat(entry.getValue()).format(metadata));
    }

    private static String seriesInstanceUID(RetrieveContext ctx, List<Attributes> results) {
        return !results.isEmpty() ? results.get(0).getString(Tag.SeriesInstanceUID)
                : UIDUtils.createNameBasedUID(
                (ctx.getStudyInstanceUID() + ctx.getSeriesInstanceUID() + ctx.getSopInstanceUID())
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static String sopInstanceUID(String instanceNumber, String seriesInstanceUID) {
        return UIDUtils.createNameBasedUID(
                (seriesInstanceUID + instanceNumber).getBytes(StandardCharsets.UTF_8));
    }

    private Attributes graphicLayerItem() {
        Attributes item = new Attributes(3);
        item.setString(Tag.GraphicLayer, VR.CS, properties.get(GraphicLayer));
        item.setString(Tag.GraphicLayerOrder, VR.IS, properties.get(GraphicLayerOrder));
        item.setString(Tag.GraphicLayerRecommendedDisplayGrayscaleValue, VR.US,
                properties.get(GraphicLayerRecommendedDisplayGrayscaleValue));
        return item;
    }

    private static Attributes softcopyVOILUTItem(Attributes pr, Attributes voiLUT) {
        Sequence seq = pr.ensureSequence(Tag.SoftcopyVOILUTSequence, 10);
        for (Attributes item : seq) {
            if (voiLUT.equals(new Attributes(item, VOI_LUT_TAGS)))
                return item;
        }
        seq.add(voiLUT);
        return voiLUT;
    }

    private static Attributes displayAreaSelectionItem(Attributes pr, int... bottomRight) {
        Sequence seq = pr.ensureSequence(Tag.DisplayedAreaSelectionSequence, 10);
        for (Attributes item : seq) {
            if (Arrays.equals(bottomRight, item.getInts(Tag.DisplayedAreaBottomRightHandCorner)))
                return item;
        }
        Attributes item = new Attributes(4);
        seq.add(item);
        item.setInt(Tag.DisplayedAreaTopLeftHandCorner, VR.SL, TOP_LEFT);
        item.setInt(Tag.DisplayedAreaBottomRightHandCorner, VR.SL, bottomRight);
        item.setString(Tag.PresentationSizeMode, VR.CS, "SCALE TO FIT");
        return item;
    }

    private static Attributes seriesRef(Attributes pr, String seriesIUID) {
        Sequence seq = pr.ensureSequence(Tag.ReferencedSeriesSequence, 10);
        for (Attributes item : seq) {
            if (seriesIUID.equals(item.getString(Tag.SeriesInstanceUID)))
                return item;
        }
        Attributes item = new Attributes(2);
        seq.add(item);
        item.setString(Tag.SeriesInstanceUID, VR.UI, seriesIUID);
        return item;
    }

    private static void imageRef(Attributes attrs, InstanceLocations inst) {
        attrs.ensureSequence(Tag.ReferencedImageSequence, 10).add(sopRef(inst));
    }

    private static Attributes sopRef(InstanceLocations inst) {
        Attributes sopRef = new Attributes(2);
        sopRef.setString(Tag.ReferencedSOPClassUID, VR.UI, inst.getSopClassUID());
        sopRef.setString(Tag.ReferencedSOPInstanceUID, VR.UI, inst.getSopInstanceUID());
        return sopRef;
    }

    private static void addPolyline(Attributes graphicAnnotationItem, float[] curveData, float[] pixelSpacing) {
        shiftCoordsOnePixel(curveData);
        if (pixelSpacing != null && pixelSpacing.length == 2) {
            graphicAnnotationItem.ensureSequence(Tag.TextObjectSequence, 10)
                    .add(createAnchorPoint(curveData, pixelSpacing));
        }
        graphicAnnotationItem.ensureSequence(Tag.GraphicObjectSequence, 10)
                .add(createPolyline(curveData));
    }

    private static void shiftCoordsOnePixel(float[] curveData) {
        for (int i = 0; i < curveData.length; i++) {
            curveData[i] += 1;
        }
    }

    private static Attributes createPolyline(float[] curveData) {
        Attributes polyline = new Attributes(6);
        polyline.setString(Tag.GraphicAnnotationUnits, VR.CS, "PIXEL");
        polyline.setInt(Tag.GraphicDimensions, VR.US, 2);
        polyline.setInt(Tag.NumberOfGraphicPoints, VR.US, 2);
        polyline.setFloat(Tag.GraphicData, VR.FL, curveData);
        polyline.setString(Tag.GraphicType, VR.CS, "POLYLINE");
        polyline.setString(Tag.GraphicFilled, VR.CS, "N");
        return polyline;
    }

    private static Attributes createAnchorPoint(float[] curveData, float[] pixelSpacing) {
        Attributes anchorPoint = new Attributes(4);
        anchorPoint.setString(Tag.AnchorPointAnnotationUnits, VR.CS, "PIXEL");
        anchorPoint.setString(Tag.UnformattedTextValue, VR.ST, toText(curveData, pixelSpacing));
        anchorPoint.setFloat(Tag.AnchorPoint, VR.FL, toAnchorPoint(curveData));
        anchorPoint.setString(Tag.AnchorPointVisibility, VR.CS, "N");
        return anchorPoint;
    }

    private static String toText(float[] curveData, float[] pixelSpacing) {
        float x = (curveData[0] - curveData[2]) * pixelSpacing[0];
        float y = (curveData[1] - curveData[3]) * pixelSpacing[1];
        return String.format("%.2f mm", Math.sqrt(x * x + y * y));
    }

    private static float[] toAnchorPoint(float[] curveData) {
        return new float[]{curveData[2], curveData[3] + Math.signum(curveData[3] - curveData[1]) * 10};
    }

    private static void trimSoftcopyVOILUT(Attributes pr, int totRefs) {
        Sequence seq = pr.getSequence(Tag.SoftcopyVOILUTSequence);
        if (seq == null || seq.size() != 1)
            return;

        Attributes item = seq.get(0);
        if (item.getSequence(Tag.ReferencedImageSequence).size() == totRefs)
            item.remove(Tag.ReferencedImageSequence);
    }

    private static void trimDisplayAreaSelection(Attributes pr) {
        Sequence displayAreaSelectionSeq = pr.getSequence(Tag.DisplayedAreaSelectionSequence);
        if (displayAreaSelectionSeq.size() == 1)
            displayAreaSelectionSeq.get(0).remove(Tag.ReferencedImageSequence);
    }

    private static String noMatches(ExportContext ctx) {
        return appendEntity(ctx, new StringBuilder("Could not find ")).toString();
    }

    private static String noPresentationStateCreated(ExportContext ctx) {
        return appendEntity(ctx, new StringBuilder("No Presentation State created for ")).toString();
    }

    private static String toMessage(ExportContext ctx, int numPRs, int numImages) {
        return appendEntity(ctx,
                new StringBuilder("Created ")
                        .append(numPRs)
                        .append(" Presentation State(s) applied to ")
                        .append(numImages)
                        .append(" Images of "))
                .toString();
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
}
