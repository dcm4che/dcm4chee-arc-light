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
 * Portions created by the Initial Developer are Copyright (C) 2015-2017
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

import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2016
 */
public class StudyDeleteContextImpl implements StudyDeleteContext {

    private final Long studyPk;
    private final List<Instance> instances = new ArrayList<>();
    private Exception exception;
    private HttpServletRequestInfo httpServletRequestInfo;
    private Study study;
    private boolean patientDeletionTriggered;

    public StudyDeleteContextImpl(Long studyPk) {
        this.studyPk = studyPk;
    }

    @Override
    public Long getStudyPk() {
        return studyPk;
    }

    @Override
    public Study getStudy() {
        return study != null ? study : instances.isEmpty() ? null : instances.get(0).getSeries().getStudy();
    }

    @Override
    public void setStudy(Study study) {
        this.study = study;
    }

    @Override
    public List<Instance> getInstances() {
        return instances;
    }

    @Override
    public void addInstance(Instance inst) {
        instances.add(inst);
    }

    @Override
    public Exception getException() {
        return exception;
    }

    @Override
    public void setException(Exception exception) {
        this.exception = exception;
    }

    @Override
    public HttpServletRequestInfo getHttpServletRequestInfo() {
        return httpServletRequestInfo;
    }

    @Override
    public void setHttpServletRequestInfo(HttpServletRequestInfo httpServletRequestInfo) {
        this.httpServletRequestInfo = httpServletRequestInfo;
    }

    @Override
    public boolean isPatientDeletionTriggered() {
        return patientDeletionTriggered;
    }

    @Override
    public void setPatientDeletionTriggered(boolean patientDeletionTriggered) {
        this.patientDeletionTriggered = patientDeletionTriggered;
    }
}
