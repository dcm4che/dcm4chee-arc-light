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

package org.dcm4chee.arc.realm.rs;

import org.jboss.resteasy.annotations.cache.NoCache;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.representations.IDToken;

import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.Principal;
import java.util.Arrays;
import java.util.Set;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2016
 */
@RequestScoped
@Path("realm")
public class RealmRS {

    @Context
    private SecurityContext sc;

    @GET
    @NoCache
    @Produces("application/json")
    public StreamingOutput query() throws Exception {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                Writer w = new OutputStreamWriter(out, "UTF-8");
                Principal principal = sc.getUserPrincipal();
                if (principal == null)
                    w.write("{\"user\":null,\"roles\":[]}");
                else {
                    w.append("{\"user\":\"").append(user(principal)).append("\",\"roles\":[");
                    int count = 0;
                    for (String role : roles(principal)) {
                        if (count++ > 0)
                            w.write(',');
                        w.append('\"').append(role).append('\"');
                    }
                    w.write("]}");
                }
                w.flush();
            }
        };
    }

    private String user(Principal principal) {
        KeycloakPrincipal<KeycloakSecurityContext> kp1 = (KeycloakPrincipal<KeycloakSecurityContext>) principal;
        return kp1.getKeycloakSecurityContext().getIdToken().getPreferredUsername();
    }

    private Iterable<String> roles(Principal principal) {
        KeycloakPrincipal<KeycloakSecurityContext> kp1 = (KeycloakPrincipal<KeycloakSecurityContext>) principal;
        return kp1.getKeycloakSecurityContext().getToken().getRealmAccess().getRoles();
    }
}
