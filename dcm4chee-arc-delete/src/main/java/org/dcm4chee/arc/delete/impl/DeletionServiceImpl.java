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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2013
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

package org.dcm4chee.arc.delete.impl;

import org.dcm4che3.data.Code;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.delete.DeletionService;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.delete.StudyNotFoundException;
import org.dcm4chee.arc.entity.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Nov 2015
 */
@ApplicationScoped
public class DeletionServiceImpl implements DeletionService {

    private static final Logger LOG = LoggerFactory.getLogger(DeletionServiceImpl.class);

    @Inject
    private DeletionServiceEJB ejb;

    @Inject
    private Device device;

    @Inject
    private Event<StudyDeleteContext> studyDeletedEvent;

    @Override
    public int deleteRejectedInstancesBefore(Code rjCode, Date before, int fetchSize) {
        return delete(before != null ? Location.FIND_BY_REJECTION_CODE_BEFORE : Location.FIND_BY_REJECTION_CODE,
                rjCode, before, fetchSize);
    }

    @Override
    public int deleteRejectionNotesBefore(Code rjCode, Date before, int fetchSize) {
        return delete(before != null ? Location.FIND_BY_CONCEPT_NAME_CODE_BEFORE : Location.FIND_BY_CONCEPT_NAME_CODE,
                rjCode, before, fetchSize);
    }

    private int delete(String queryName, Code rjCode, Date before, int fetchSize) {
        int total = 0;
        int deleted;
        do {
            total += deleted = ejb.deleteRejectedInstancesOrRejectionNotesBefore(queryName, rjCode, before, fetchSize);
        } while (deleted == fetchSize);
        return total;
    }

    @Override
    public StudyDeleteContext createStudyDeleteContext(String studyUID, HttpServletRequest request) {
        StudyDeleteContext ctx = new StudyDeleteContextImpl(null, studyUID);
        ctx.setHttpRequest(request);
        return ctx;
    }

    @Override
    public void deleteStudy(StudyDeleteContext ctx) {
        boolean studyRemoved;
        try {
            studyRemoved = ejb.removeStudyOnStorage(ctx);
            if (studyRemoved) {
                LOG.info("Successfully delete {} from database", ctx.getStudy());
                studyDeletedEvent.fire(ctx);
            } else {
                ejb.deleteEmptyStudy(ctx);
                LOG.warn("Successfully delete empty study {} from database", ctx.getStudyIUID());
            }
        } catch (StudyNotFoundException e) {
            throw new NotFoundException("Study having study instance UID : " + ctx.getStudyIUID() + " not found.");
        } catch (Exception e) {
            LOG.warn("Failed to delete {} on {}", ctx.getStudy(), e);
            ctx.setException(e);
            studyDeletedEvent.fire(ctx);
        }
    }

}
