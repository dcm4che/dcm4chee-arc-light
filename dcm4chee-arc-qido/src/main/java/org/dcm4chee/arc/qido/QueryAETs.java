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

package org.dcm4chee.arc.qido;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.jboss.resteasy.annotations.cache.NoCache;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
@Path("aets")
@RequestScoped
public class QueryAETs {

    @Inject
    private Device device;

    @Context
    private HttpServletRequest request;

    private final String keycloakClassName = "org.keycloak.KeycloakSecurityContext";

    @GET
    @NoCache
    @Produces("application/json")
    public StreamingOutput query() throws Exception {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                Writer w = new OutputStreamWriter(out, "UTF-8");
                int count = 0;
                w.write('[');
                for (String aet : device.getApplicationAETitles()) {
                    ApplicationEntity ae = device.getApplicationEntity(aet);
                    if (!ae.isInstalled())
                        continue;
                    if (count++ > 0)
                        w.write(',');
                    w.write("{\"title\":\"");
                    w.write(aet);
                    w.write('"');
                    String desc = ae.getDescription();
                    if (desc != null) {
                        w.write(",\"description\":\"");
                        w.write(desc);
                        w.write('"');
                    }
                    if (ae.getAEExtension(ArchiveAEExtension.class)
                            .getQueryRetrieveView().isHideNotRejectedInstances())
                        w.write(",\"dcmHideNotRejectedInstances\":true");
                    String[] acceptedUserRoles = ae.getAEExtension(ArchiveAEExtension.class).getAcceptedUserRoles();
                    if (acceptedUserRoles.length != 0) {
                        w.write(",\"dcmAcceptedUserRole\":[\"");
                        for (int i = 0; i < acceptedUserRoles.length; i++) {
                            if (i > 0)
                                w.write("\",\"");
                            w.write(acceptedUserRoles[i]);
                        }
                        w.write("\"]");
                    }
                    w.write('}');
                }
                w.write(']');
                w.flush();
            }
        };
    }
}
