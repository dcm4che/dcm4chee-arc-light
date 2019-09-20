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

package org.dcm4chee.arc.ups.scp;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicQueryTask;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.RunInTransaction;

import java.io.IOException;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2019
 */
public class UPSQueryTask extends BasicQueryTask {
    private final Query query;
    private final RunInTransaction runInTx;

    public UPSQueryTask(Association as, PresentationContext pc, Attributes rq, Attributes keys, Query query,
            RunInTransaction runInTx) {
        super(as, pc, rq, keys);
        this.query = query;
        this.runInTx = runInTx;
        setOptionalKeysNotSupported(query.isOptionalKeysNotSupported());
    }

    @Override
    public void run() {
        runInTx.execute(this::run0);
    }

    private void run0() {
        try {
            QueryContext ctx = query.getQueryContext();
            ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
            ArchiveDeviceExtension arcdev = arcAE.getArchiveDeviceExtension();
            query.executeQuery(arcdev.getQueryFetchSize());
            super.run();
        } catch (Exception e) {
            writeDimseRSP(new DicomServiceException(Status.UnableToProcess, e));
        } finally {
            query.close();
        }
    }

    private void writeDimseRSP(DicomServiceException e) {
        int msgId = rq.getInt(Tag.MessageID, -1);
        Attributes rsp = e.mkRSP(Dimse.C_FIND_RSP.commandField(), msgId);
        try {
            as.writeDimseRSP(pc, rsp, null);
        } catch (IOException e1) {
            // handled by Association
        }
    }

    @Override
    protected boolean hasMoreMatches() throws DicomServiceException {
        try {
            return query.hasMoreMatches();
        }  catch (DicomServiceException e) {
            throw e;
        }  catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    @Override
    protected Attributes nextMatch() throws DicomServiceException {
        try {
            return query.nextMatch();
        }  catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    @Override
    protected Attributes adjust(Attributes match) {
        return query.adjust(match);
    }
}
