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

package org.dcm4chee.arc.mpps.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.entity.MPPS;
import org.dcm4chee.arc.mpps.MPPSContext;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
public class MPPSContextImpl implements MPPSContext {

    private final Association as;
    private final ApplicationEntity ae;
    private Attributes attributes;
    private String sopInstanceUID;
    private Dimse dimse;
    private MPPS mpps;

    public MPPSContextImpl(Association as) {
        this.as = as;
        this.ae = as.getApplicationEntity();
    }

    @Override
    public Association getAssociation() {
        return as;
    }

    @Override
    public ApplicationEntity getLocalApplicationEntity() {
        return ae;
    }

    @Override
    public ArchiveAEExtension getArchiveAEExtension() {
        return ae.getAEExtension(ArchiveAEExtension.class);
    }

    @Override
    public String getCalledAET() {
        return as.getCalledAET();
    }

    @Override
    public String getCallingAET() {
        return as.getCallingAET();
    }

    @Override
    public String getRemoteHostName() {
        return ReverseDNS.hostNameOf(as.getSocket().getInetAddress());
    }

    @Override
    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    @Override
    public void setSopInstanceUID(String sopInstanceUID) {
        this.sopInstanceUID = sopInstanceUID;
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Attributes attrs) {
        this.attributes = attrs;
    }

    @Override
    public Dimse getDimse() {
        return dimse;
    }

    @Override
    public void setDimse(Dimse dimse) {
        this.dimse = dimse;
    }

    @Override
    public MPPS getMPPS() {
        return mpps;
    }

    @Override
    public void setMPPS(MPPS mpps) {
        this.mpps = mpps;
    }

    @Override
    public String toString() {
        return as.toString();
    }
}
