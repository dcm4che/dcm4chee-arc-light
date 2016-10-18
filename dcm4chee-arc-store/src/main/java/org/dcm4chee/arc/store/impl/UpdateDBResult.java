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

package org.dcm4chee.arc.store.impl;

import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.storage.WriteContext;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
class UpdateDBResult {
    private final List<Location> locations = new ArrayList<>();
    private final List<WriteContext> writeContexts = new ArrayList<>(2);
    private RejectionNote rejectionNote;
    private Instance previousInstance;
    private Instance createdInstance;
    private Patient createdPatient;
    private Study createdStudy;
    private Instance storedInstance;

    public List<Location> getLocations() {
        return locations;
    }

    public List<WriteContext> getWriteContexts() {
        return writeContexts;
    }

    public void setRejectionNote(RejectionNote rejectionNote) {
        this.rejectionNote = rejectionNote;
    }

    public RejectionNote getRejectionNote() {
        return rejectionNote;
    }

    public void setPreviousInstance(Instance previousInstance) {
        this.previousInstance = previousInstance;
    }

    public Instance getPreviousInstance() {
        return previousInstance;
    }

    public Patient getCreatedPatient() {
        return createdPatient;
    }

    public void setCreatedPatient(Patient createdPatient) {
        this.createdPatient = createdPatient;
    }

    public Study getCreatedStudy() {
        return createdStudy;
    }

    public void setCreatedStudy(Study createdStudy) {
        this.createdStudy = createdStudy;
    }

    public Instance getCreatedInstance() {
        return createdInstance;
    }

    public void setCreatedInstance(Instance createdInstance) {
        this.createdInstance = createdInstance;
    }

    public Instance getStoredInstance() {
        return storedInstance;
    }

    public void setStoredInstance(Instance storedInstance) {
        this.storedInstance = storedInstance;
    }
}
