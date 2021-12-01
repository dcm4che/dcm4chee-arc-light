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

package org.dcm4chee.arc.coerce.query;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.TransferCapability;
import org.dcm4chee.arc.Cache;
import org.dcm4chee.arc.LeadingCFindSCPQueryCache;
import org.dcm4chee.arc.coerce.CoercionProcessor;
import org.dcm4chee.arc.conf.ArchiveAttributeCoercion2;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2021
 */
@ApplicationScoped
@Named("leading-arc")
public class LeadingArchiveCoercionProcessor implements CoercionProcessor {

    static final Logger LOG = LoggerFactory.getLogger(LeadingArchiveCoercionProcessor.class);

    @Inject
    private Device device;

    @Inject
    private LeadingCFindSCPQueryCache queryCache;

    @Inject
    private CFindSCU cfindSCU;

    @Override
    public boolean coerce(ArchiveAttributeCoercion2 coercion,
                          String sopClassUID, String sendingHost, String sendingAET,
                          String receivingHost, String receivingAET,
                          Attributes attrs, Attributes modified)
            throws Exception {
        String studyIUID = attrs.getString(Tag.StudyInstanceUID);
        String findSCP = coercion.getSchemeSpecificPart();
        Attributes newAttrs = queryStudy(
                coercion.getRole() == TransferCapability.Role.SCU ? receivingAET : sendingAET,
                findSCP,
                studyIUID);
        if (newAttrs == null) {
            return false;
        }
        if (attrs.update(coercion.getAttributeUpdatePolicy(), newAttrs, modified)) {
            LOG.info("Coerce Attributes from matching Study at {}", findSCP);
        }
        return true;
    }

    private Attributes queryStudy(String localAET, String leadingCFindSCP, String studyIUID) throws Exception {
        LeadingCFindSCPQueryCache.Key key = new LeadingCFindSCPQueryCache.Key(leadingCFindSCP, studyIUID);
        Cache.Entry<Attributes> entry = queryCache.getEntry(key);
        if (entry != null)
            return entry.value();

        Attributes newAttrs = null;
        ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        List<Attributes> matches = cfindSCU.findStudy(
                device.getApplicationEntity(localAET, true),
                leadingCFindSCP,
                Priority.NORMAL,
                studyIUID,
                arcdev.returnKeysForLeadingCFindSCP(leadingCFindSCP));
        if (!matches.isEmpty())
            newAttrs = matches.get(0);
        queryCache.put(key, newAttrs);
        return newAttrs;
    }
}
