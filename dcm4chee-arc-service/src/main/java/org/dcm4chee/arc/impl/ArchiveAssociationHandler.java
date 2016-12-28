/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2016
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.impl;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateAC;
import org.dcm4che3.net.pdu.AAssociateRJ;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.UserIdentityAC;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Dec 2016
 */
@ApplicationScoped
public class ArchiveAssociationHandler extends AssociationHandler {

    @Inject
    private IApplicationEntityCache aeCache;

    @Override
    protected AAssociateAC makeAAssociateAC(Association as, AAssociateRQ rq, UserIdentityAC userIdentity)
            throws IOException {
        if (!validateCallingAEHostname(as))
            throw new AAssociateRJ(AAssociateRJ.RESULT_REJECTED_PERMANENT,
                AAssociateRJ.SOURCE_SERVICE_USER,
                AAssociateRJ.REASON_CALLING_AET_NOT_RECOGNIZED);
        return super.makeAAssociateAC(as, rq, userIdentity);
    }

    private boolean validateCallingAEHostname(Association as) {
        ArchiveAEExtension arcAE = as.getApplicationEntity().getAEExtension(ArchiveAEExtension.class);
        if (arcAE.validateCallingAEHostname()) {
            try {
                ApplicationEntity ae = aeCache.findApplicationEntity(as.getAAssociateRQ().getCallingAET());
                InetAddress remote = as.getSocket().getInetAddress();
                for (Connection c : ae.getConnections()) {
                    if (StringUtils.isIPAddr(c.getHostname()) && c.getHostname().equals(remote.getHostAddress()))
                        return true;
                    else {
                            String[] ss = StringUtils.split(c.getHostname(), '.');
                            String[] ss1 = StringUtils.split(remote.getCanonicalHostName(), '.');
                            return ((ss.length == 1 || ss1.length == 1) && ss[0].equalsIgnoreCase(ss1[0]))
                                    || (c.getHostname().equalsIgnoreCase(remote.getCanonicalHostName()));
                        }
                }
            } catch (ConfigurationException e) {
                return false;
            }
        }
        return true;
    }
}
