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

package org.dcm4chee.arc.diff.impl;

import org.dcm4che3.data.*;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.diff.DiffContext;
import org.dcm4chee.arc.diff.DiffSCU;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2018
 */
public class DiffSCUImpl implements DiffSCU {

    private static final Logger LOG = LoggerFactory.getLogger(DiffSCUImpl.class);

    private final DiffContext ctx;
    private final CFindSCU findSCU;

    private Association as1;
    private Association as2;
    private DimseRSP dimseRSP;
    private DimseRSP dimseRSP2;
    private int missing;
    private int different;
    private int matches;
    private volatile boolean canceled;

    public DiffSCUImpl(DiffContext ctx, CFindSCU findSCU) {
        this.ctx = ctx;
        this.findSCU = findSCU;
    }

    @Override
    public int init() throws Exception {
        EnumSet<QueryOption> queryOptions = EnumSet.of(QueryOption.DATETIME);
        if (ctx.isFuzzymatching())
            queryOptions.add(QueryOption.FUZZY);
        as1 = findSCU.openAssociation(ctx.getLocalAE(), ctx.getPrimaryAE().getAETitle(),
                UID.StudyRootQueryRetrieveInformationModelFIND, queryOptions);
        as2 = findSCU.openAssociation(ctx.getLocalAE(), ctx.getSecondaryAE().getAETitle(),
                UID.StudyRootQueryRetrieveInformationModelFIND, queryOptions);
        if (!ctx.isForceQueryByStudyUID() && ctx.supportSorting()) {
            dimseRSP2 = findSCU.query(as2, ctx.priority(), ctx.getQueryKeys(), 0, 1, ctx.getSplitStudyDateRange());
            dimseRSP2.next();
            checkRSP(dimseRSP2);
        }
        dimseRSP = findSCU.query(as1, ctx.priority(), ctx.getQueryKeys(), 0, 1, ctx.getSplitStudyDateRange());
        dimseRSP.next();
        return checkRSP(dimseRSP);
    }

    @Override
    public void countDiffs() throws Exception {
        do {
            Attributes match = dimseRSP.getDataset();
            if (match != null) {
                matches++;
                Attributes other = findOther(match.getString(Tag.StudyInstanceUID));
                if (other == null)
                    missing++;
                else if (other.diff(match, ctx.getCompareKeys(),null) > 0)
                    different++;
            }
        } while (dimseRSP.next());
    }

    @Override
    public Attributes nextDiff() throws Exception {
        boolean next;
        do {
            Attributes match = dimseRSP.getDataset();
            next = dimseRSP.next();
            if (match != null) {
                matches++;
                Attributes other = findOther(match.getString(Tag.StudyInstanceUID));
                if (other == null) {
                    if (ctx.isCheckMissing()) {
                        missing++;
                        return addOriginalAttributesSequence(match, modifiedAttributesForMissing());
                    }
                } else if (ctx.isCheckDifferent()) {
                    Attributes modified = new Attributes(match.size());
                    if (other.diff(match, ctx.getCompareKeys(), modified) > 0) {
                        different++;
                        return addOriginalAttributesSequence(match, modified);
                    }
                }
            }
        } while (next && !canceled);
        waitForOutstandingRSP(as1, dimseRSP);
        waitForOutstandingRSP(as2, dimseRSP2);
        return null;
    }

    @Override
    public int missing() {
        return missing;
    }

    @Override
    public int different() {
        return different;
    }

    @Override
    public int matches() {
        return matches;
    }

    @Override
    public void cancel() {
        safeCancel(as1, dimseRSP);
        safeCancel(as2, dimseRSP2);
        canceled = true;
    }

    @Override
    public void close() {
        safeRelease(as1);
        safeRelease(as2);
    }

    private static Attributes modifiedAttributesForMissing() {
        Attributes modified = new Attributes(2);
        modified.setInt(Tag.NumberOfStudyRelatedSeries, VR.IS, 0);
        modified.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, 0);
        return modified;
    }

    private Attributes addOriginalAttributesSequence(Attributes match, Attributes modified) {
        Sequence sq = match.newSequence(Tag.OriginalAttributesSequence, 1);
        Attributes item = new Attributes();
        sq.add(item);
        item.newSequence(Tag.ModifiedAttributesSequence, 1).add(modified);
        item.setString(Tag.SourceOfPreviousValues, VR.LO, ctx.getSecondaryAE().getAETitle());
        item.setDate(Tag.AttributeModificationDateTime, VR.DT, new Date());
        item.setString(Tag.ModifyingSystem, VR.LO, ctx.getPrimaryAE().getAETitle());
        item.setString(Tag.ReasonForTheAttributeModification, VR.CS, "DIFFS");
        return match;
    }

    private static int checkRSP(DimseRSP rsp) throws DicomServiceException {
        Attributes cmd = rsp.getCommand();
        int status = cmd.getInt(Tag.Status, -1);
        if (!Status.isPending(status) && status != Status.Success)
            throw new DicomServiceException(status, cmd.getString(Tag.ErrorComment));
        return status;
    }

    private static void safeCancel(Association as, DimseRSP rsp) {
        if (as != null && rsp != null)
            try {
                rsp.cancel(as);
            } catch (IOException e) {
                LOG.info("{}: Failed to cancel C-FIND at association:\\n", as, e);
            }
    }

    private static void waitForOutstandingRSP(Association as, DimseRSP dimseRSP) {
        if (as != null && dimseRSP != null)
            try {
                while (dimseRSP.next())
                    ;
            } catch (Exception e) {
                LOG.info("{}: Failed to wait for outstanding C-FIND RSPs at association:\\n", as, e);
            }
    }

    private static void safeRelease(Association as) {
        if (as != null)
            try {
                as.release();
            } catch (IOException e) {
                LOG.info("{}: Failed to release association:\\n", as, e);
            }
    }

    private Attributes findOther(String studyIUID) throws Exception {
        if (dimseRSP2 != null) {
            do {
                Attributes other = dimseRSP2.getDataset();
                if (other == null) break;
                int compare = studyIUID.compareTo(other.getString(Tag.StudyInstanceUID));
                if (compare == 0) {
                    dimseRSP2.next();
                    return other;
                }
                if (compare < 0) break;
            } while (dimseRSP2.next());
        }
        List<Attributes> matches = findSCU.findStudy(as2, ctx.priority(), studyIUID, ctx.getReturnKeys());
        return !matches.isEmpty() ? matches.get(0) : null;
    }
}
