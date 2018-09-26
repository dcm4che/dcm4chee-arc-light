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

package org.dcm4chee.arc.study.impl;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyService;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.net.Socket;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
@ApplicationScoped
public class StudyServiceImpl implements StudyService {
    @Inject
    private Device device;

    @Inject
    private StudyServiceEJB ejb;

    @Inject
    private Event<StudyMgtContext> updateStudyEvent;

    @Override
    public StudyMgtContext createStudyMgtContextWEB(HttpServletRequest httpRequest, ApplicationEntity ae) {
        return new StudyMgtContextImpl(device).withHttpRequest(httpRequest).withApplicationEntity(ae);
    }

    @Override
    public StudyMgtContext createStudyMgtContextHL7(Socket socket, UnparsedHL7Message msg) {
        return new StudyMgtContextImpl(device).withSocket(socket).withUnparsedHL7Message(msg);
    }

    @Override
    public void updateStudy(StudyMgtContext ctx) {
        try {
            ejb.updateStudy(ctx);
        } catch (RuntimeException e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                updateStudyEvent.fire(ctx);
        }
    }

    @Override
    public void updateExpirationDate(StudyMgtContext ctx) {
        try {
            if (ctx.getSeriesInstanceUID() != null)
                ejb.updateSeriesExpirationDate(ctx);
            else
                ejb.updateStudyExpirationDate(ctx);
        } catch (Exception e) {
            ctx.setException(e);
            throw e;
        } finally {
            updateStudyEvent.fire(ctx);
        }
    }

}
