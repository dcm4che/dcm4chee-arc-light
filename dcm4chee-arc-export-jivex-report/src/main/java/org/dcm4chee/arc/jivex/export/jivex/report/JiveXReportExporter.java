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
 * Portions created by the Initial Developer are Copyright (C) 2013-2021
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

package org.dcm4chee.arc.jivex.export.jivex.report;

import org.dcm4che3.data.*;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.Outcome;

import java.util.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2023
 */
public class JiveXReportExporter extends AbstractExporter {
    private final JiveXReportExporterEJB ejb;

    protected JiveXReportExporter(ExporterDescriptor descriptor, JiveXReportExporterEJB ejb) {
        super(descriptor);
        this.ejb = ejb;
    }

    @Override
    public Outcome export(ExportContext exportContext) throws Exception {
        String iuid = exportContext.getSopInstanceUID();
        if (iuid == null)
            return new Outcome(Task.Status.WARNING,
                    "JiveX Report Exporter can only process Export Tasks on Instance Level.");
        String suid = exportContext.getStudyInstanceUID();
        List<Instance> insts = ejb.findInstances(suid, UID.EncapsulatedPDFStorage);
        Instance received = removeWithIUID(insts, iuid);
        if (received == null)
            return new Outcome(Task.Status.WARNING,
                    "Encapsulated PDF[uid=" + iuid + "] of Study[uid=" + suid +
                            "] not found.");
        Attributes attrs = received.getAttributes();
        String hl7InstanceIdentifier = attrs.getString(Tag.HL7InstanceIdentifier);
        if (hl7InstanceIdentifier == null)
            return new Outcome(Task.Status.COMPLETED,
                    "Encapsulated PDF[uid=" + iuid + "] of Study[uid=" + suid +
                            "] does not contain (0040,E001) HL7 Instance Identifier.");
        String contentDate = attrs.getString(Tag.ContentDate);
        String contentTime = attrs.getString(Tag.ContentTime);
        if (contentDate == null || contentTime == null)
            return new Outcome(Task.Status.COMPLETED,
                    "Encapsulated PDF[uid=" + iuid + "] of Study[uid=" + suid +
                            "] does not contain (0040,E001) Content Date and (0040,E001) Content Date.");
        Sequence predecessorDocumentsSequence = attrs.ensureSequence(Tag.PredecessorDocumentsSequence, 1);
        if (!predecessorDocumentsSequence.isEmpty()) {
            return new Outcome(Task.Status.COMPLETED,
                    "Encapsulated PDF[uid=" + iuid + "] of Study[uid=" + suid +
                            "] already refers Predecessor Documents.");
        }
        retainPredecessorDocuments(insts, hl7InstanceIdentifier, contentDate, contentTime);
        if (insts.isEmpty()) {
            return new Outcome(Task.Status.COMPLETED,
                    "No Predecessor Documents found for Encapsulated PDF[uid=" + iuid +
                            "] of Study[uid=" + suid + "].");
        }
        Collections.sort(insts, JiveXReportExporter::compareContentDateAndTime);
        Attributes predecessorDocumentsItem = new Attributes(2);
        Sequence refSeriesSeq = predecessorDocumentsItem.newSequence(Tag.ReferencedSeriesSequence, insts.size());
        for (Instance inst : insts) {
            Sequence refSOPSeq = refSOPSeq(refSeriesSeq, inst);
            refSOPSeq.add(refSOP(inst));
        }
        predecessorDocumentsSequence.add(predecessorDocumentsItem);
        received.getAttributesBlob().setAttributes(attrs);
        ejb.updateAttributesBlob(received);
        return new Outcome(Task.Status.COMPLETED,
                "Added references to " + insts.size() +
                        " Predecessor Documents to Encapsulated PDF[uid=" + iuid +
                        "] of Study[uid=" + suid + "].");
    }

    private static Sequence refSOPSeq(Sequence refSeriesSeq, Instance inst) {
        String seriesInstanceUID = inst.getSeries().getSeriesInstanceUID();
        for (Attributes refSeries : refSeriesSeq) {
            if (refSeries.getString(Tag.SeriesInstanceUID).equals(seriesInstanceUID))
                return refSeries.getSequence(Tag.ReferencedSOPSequence);
        }
        Attributes seriesRef = new Attributes(2);
        Sequence refSOPSeq = seriesRef.newSequence(Tag.ReferencedSOPSequence, 1);
        seriesRef.setString(Tag.SeriesInstanceUID, VR.UI, inst.getSeries().getSeriesInstanceUID());
        refSeriesSeq.add(seriesRef);
        return refSOPSeq;
    }

    private static Attributes refSOP(Instance inst) {
        Attributes refSOP = new Attributes(2);
        refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, inst.getSopClassUID());
        refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, inst.getSopInstanceUID());
        return refSOP;
    }

    private static void retainPredecessorDocuments(List<Instance> insts, String hl7InstanceIdentifier,
                                            String contentDate, String contentTime) {
        Iterator<Instance> iter = insts.iterator();
        while (iter.hasNext()) {
            Instance inst = iter.next();
            Attributes attrs = inst.getAttributes();
            if (!hl7InstanceIdentifier.equals(attrs.getString(Tag.HL7InstanceIdentifier))
                || compareContentDateAndTime(contentDate, contentTime,
                        attrs.getString(Tag.ContentDate),
                        attrs.getString(Tag.ContentTime)) < 0) {
                iter.remove();
            }
        }
    }

    private static int compareContentDateAndTime(
            String contentDate, String contentTime,
            String otherContentDate, String otherContentTime) {
        if (contentDate == null || otherContentDate == null)
            return 0;
        int cmpda = contentDate.compareTo(otherContentDate);
        return cmpda != 0 && contentTime == null || otherContentTime == null ? cmpda
                : contentTime.compareTo(otherContentTime);
    }

    private static int compareContentDateAndTime(Instance i1, Instance i2) {
        Attributes a1 = i1.getAttributes();
        Attributes a2 = i2.getAttributes();
        return compareContentDateAndTime(
                a2.getString(Tag.ContentDate),
                a2.getString(Tag.ContentTime),
                a1.getString(Tag.ContentDate),
                a1.getString(Tag.ContentTime));
    }

    private Instance removeWithIUID(List<Instance> insts, String iuid) {
        Iterator<Instance> iter = insts.iterator();
        while (iter.hasNext()) {
            Instance inst = iter.next();
            if (inst.getSopInstanceUID().equals(iuid)) {
                iter.remove();
                return inst;
            }
        }
        return null;
    }
}
