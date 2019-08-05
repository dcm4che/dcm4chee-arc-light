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
 * Portions created by the Initial Developer are Copyright (C) 2015
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

package org.dcm4chee.arc.query.scp;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.AttributesCoercion;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicQueryTask;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.SpanningCFindSCPPolicy;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.RunInTransaction;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
public class ArchiveQueryTask extends BasicQueryTask {
    private static final Logger LOG = LoggerFactory.getLogger(ArchiveQueryTask.class);

    private final QueryContext ctx;
    private final RunInTransaction runInTx;
    private final AttributesCoercion coercion;
    private final int uniqueKey;
    private final VR vrOfUniqueKey;
    private final String spanningCFindSCP;
    private final String[] spanningRetrieveAETs;
    private final SpanningCFindSCPPolicy spanningPolicy;
    private final int queryMaxNumberOfResults;
    private final int queryFetchSize;
    private Association spanningAssoc;
    private DimseRSP spanningCFindRSP;
    private Attributes spanningMatch;
    private Query query;
    private State state = State.NOT_INITALIZED;
    private Set<String> uniqueKeys = new HashSet<>();
    private int removeUniqueKeyFromSpanningMatch;

    public ArchiveQueryTask(Association as, PresentationContext pc, Attributes rq, Attributes keys, QueryContext ctx,
            RunInTransaction runInTx) {
        super(as, pc, rq, keys);
        this.ctx = ctx;
        this.runInTx = runInTx;
        this.coercion = ctx.getQueryService().getAttributesCoercion(ctx);
        uniqueKey = ctx.getQueryRetrieveLevel().uniqueKey();
        vrOfUniqueKey = ctx.getQueryRetrieveLevel().vrOfUniqueKey();
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        spanningCFindSCP = arcAE.spanningCFindSCP();
        spanningRetrieveAETs = arcAE.spanningCFindSCPRetrieveAETitles();
        spanningPolicy = arcAE.spanningCFindSCPPolicy();
        queryMaxNumberOfResults = arcAE.queryMaxNumberOfResults();
        queryFetchSize = arcAE.getArchiveDeviceExtension().getQueryFetchSize();
    }

    @Override
    public void run() {
        runInTx.execute(super::run);
    }

    @Override
    protected void close() {
        closeQuery();
        releaseSpanningAssociation();
    }

    private void closeQuery() {
        if (query != null) {
            query.close();
            query = null;
        }
    }

    private void releaseSpanningAssociation() {
        if (spanningAssoc != null) {
            try {
                spanningAssoc.release();
            } catch (IOException e) {
                LOG.info("{}: failed to release association", spanningAssoc, e);
            }
            spanningAssoc = null;
        }
    }

    @Override
    protected boolean hasMoreMatches() throws DicomServiceException {
        try {
            return state.hasMoreMatches(this);
        }  catch (DicomServiceException e) {
            throw e;
        }  catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    @Override
    protected Attributes nextMatch() throws DicomServiceException {
        try {
            return state.nextMatch(this);
        }  catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    @Override
    protected Attributes adjust(Attributes match) {
        return state.adjust(this, match);
    }

    private void initQuery() throws DicomServiceException {
        this.query = ctx.getQueryService().createQuery(ctx);
        setOptionalKeysNotSupported(query.isOptionalKeysNotSupported());
        if (queryMaxNumberOfResults > 0 && !ctx.containsUniqueKey()
                && query.fetchCount() > queryMaxNumberOfResults) {
            throw new DicomServiceException(Status.UnableToProcess, "Request entity too large");
        }
        query.executeQuery(queryFetchSize);
    }

    private void initSpanning() throws Exception {
        CFindSCU cfindscu = ctx.getQueryService().cfindSCU();
        spanningAssoc = cfindscu.openAssociation(
                as.getApplicationEntity(),
                spanningCFindSCP,
                ctx.getSOPClassUID(),
                as.getQueryOptionsFor(ctx.getSOPClassUID()));
        spanningCFindRSP = cfindscu.query(spanningAssoc, Priority.NORMAL, spanningQueryKeys(), 0, 1, null);
        spanningCFindRSP.next();
        nextSpanningMatch();
    }

    private Attributes spanningQueryKeys() {
        Attributes queryKeys = ctx.getQueryKeys();
        if (!queryKeys.contains(uniqueKey)) {
            queryKeys = new Attributes(queryKeys);
            queryKeys.setNull(uniqueKey, vrOfUniqueKey);
            removeUniqueKeyFromSpanningMatch = uniqueKey;
        }
        return queryKeys;
    }

    private enum State {
        NOT_INITALIZED {
            @Override
            public boolean hasMoreMatches(ArchiveQueryTask task) throws Exception {
                return task.initState().hasMoreMatches(task);
            }
        },
        QUERY {
            @Override
            public boolean hasMoreMatches(ArchiveQueryTask task) throws Exception {
                return task.hasMoreQueryMatches();
            }

            @Override
            public Attributes nextMatch(ArchiveQueryTask task) {
                return task.nullifyDuplicate(task.nextQueryMatch());
            }

            @Override
            public Attributes adjust(ArchiveQueryTask task, Attributes match) {
                return task.adjustQueryMatch(match);
            }
        },
        QUERY_BEFORE_SPANNING {
            @Override
            public boolean hasMoreMatches(ArchiveQueryTask task) throws Exception {
                return task.hasMoreQueryMatches() || task.initSpanningAfterQuery().hasMoreMatches(task);
            }

            @Override
            public Attributes nextMatch(ArchiveQueryTask task) {
                return task.registerUniqueKey(task.nextQueryMatch());
            }

            @Override
            public Attributes adjust(ArchiveQueryTask task, Attributes match) {
                return task.adjustQueryMatch(match);
            }
        },
        SPANNING {
            @Override
            public boolean hasMoreMatches(ArchiveQueryTask task) throws Exception {
                return task.hasMoreSpanningMatches();
            }

            @Override
            public Attributes nextMatch(ArchiveQueryTask task) throws Exception {
                return task.nullifyDuplicate(task.nextSpanningMatch());
            }

            @Override
            public Attributes adjust(ArchiveQueryTask task, Attributes match) {
                return task.adjustSpanningMatch(match);
            }
        },
        SPANNING_BEFORE_QUERY {
            @Override
            public boolean hasMoreMatches(ArchiveQueryTask task) throws Exception {
                return task.hasMoreSpanningMatches() || task.initQueryAfterSpanning().hasMoreMatches(task);
            }

            @Override
            public Attributes nextMatch(ArchiveQueryTask task) throws Exception {
                return task.registerUniqueKey(task.nextSpanningMatch());
            }

            @Override
            public Attributes adjust(ArchiveQueryTask task, Attributes match) {
                return task.adjustSpanningMatch(match);
            }
        };

        public abstract boolean hasMoreMatches(ArchiveQueryTask task) throws Exception;

        public Attributes nextMatch(ArchiveQueryTask task) throws Exception {
            throw new IllegalStateException("State: " + this);
        }

        public Attributes adjust(ArchiveQueryTask task, Attributes match) {
            throw new IllegalStateException("State: " + this);
        }
    }

    private Attributes registerUniqueKey(Attributes match) {
        if (match != null && (queryMaxNumberOfResults == 0 || uniqueKeys.size() < queryMaxNumberOfResults)) {
            uniqueKeys.add(match.getString(uniqueKey));
        }
        return match;
    }

    private Attributes nullifyDuplicate(Attributes match) {
        return match != null
                && !uniqueKeys.contains(match.getString(uniqueKey)) ? match : null;
    }

    private Attributes nextSpanningMatch() throws Exception {
        Attributes match = spanningMatch;
        spanningMatch = spanningCFindRSP.getDataset();
        spanningCFindRSP.next();
        return match;
    }


    private boolean hasMoreQueryMatches() throws DicomServiceException {
        return query.hasMoreMatches();
    }

    private boolean hasMoreSpanningMatches() {
        return spanningMatch != null;
    }

    public Attributes nextQueryMatch() {
        return query.nextMatch();
    }

    private Attributes adjustQueryMatch(Attributes match) {
        if (match == null)
            return null;

        if (coercion != null)
            coercion.coerce(match, null);

        Attributes adjust = query.adjust(match);
        adjust.addSelected(keys, null, Tag.QueryRetrieveLevel);
        return adjust;
    }

    private Attributes adjustSpanningMatch(Attributes match) {
        if (match == null)
            return null;

        if (removeUniqueKeyFromSpanningMatch != 0)
            match.remove(removeUniqueKeyFromSpanningMatch);

        if (spanningRetrieveAETs.length > 0)
            match.setString(Tag.RetrieveAETitle, VR.AE, spanningRetrieveAETs);

        return match;
    }

    private State initState() throws Exception {
        if (spanningCFindSCP == null || spanningPolicy == SpanningCFindSCPPolicy.SUPPLEMENT) {
            initQuery();
            state = spanningCFindSCP == null ? State.QUERY : State.QUERY_BEFORE_SPANNING;
        } else {
            initSpanning();
            state = spanningPolicy == SpanningCFindSCPPolicy.REPLACE ? State.SPANNING : State.SPANNING_BEFORE_QUERY;
        }
        return state;
    }

    private State initSpanningAfterQuery() throws Exception {
        closeQuery();
        initSpanning();
        return state = State.SPANNING;
    }

    private State initQueryAfterSpanning() throws Exception {
        releaseSpanningAssociation();
        initQuery();
        return state = State.QUERY;
    }
}
