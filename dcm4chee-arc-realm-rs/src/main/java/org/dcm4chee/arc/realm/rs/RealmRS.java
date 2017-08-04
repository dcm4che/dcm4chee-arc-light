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

package org.dcm4chee.arc.realm.rs;

import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
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
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Collections;
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
                    UserRoles userRoles = new UserRoles(principal);
                    w.append("{\"user\":\"").append(userRoles.userName).append("\",\"roles\":[");
                    int count = 0;
                    for (String role : userRoles.roles) {
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

    private static class UserRoles {
        private final Logger LOG = LoggerFactory.getLogger(UserRoles.class);

        private Class keycloakPrincipalClass;
        private Class keycloakSecurityContextClass;
        private Object keycloakSecurityContext;

        private String userName;
        private Set<String> roles = Collections.EMPTY_SET;

        UserRoles(Principal principal) {
            userName = getUserName(principal);
            roles = getRoles();
        }

        private String getUserName(Principal principal) {
            try {
                keycloakPrincipalClass = Class.forName("org.keycloak.KeycloakPrincipal");
                keycloakSecurityContextClass = Class.forName("org.keycloak.KeycloakSecurityContext");
                Class idTokenClass = Class.forName("org.keycloak.representations.IDToken");
                Object keycloakPrincipal = keycloakPrincipalClass.cast(principal);
                Method getKeycloakSecurityContext = keycloakPrincipalClass.getDeclaredMethod("getKeycloakSecurityContext");
                keycloakSecurityContext = getKeycloakSecurityContext.invoke(keycloakPrincipal);
                Method getIdToken = keycloakSecurityContextClass.getDeclaredMethod("getIdToken");
                Object idToken = getIdToken.invoke(keycloakSecurityContext);
                Method getPreferredUsername = idTokenClass.getDeclaredMethod("getPreferredUsername");
                userName = String.valueOf(getPreferredUsername.invoke(idToken));
            } catch (Exception e) {
                LOG.warn("Failed to get username : " + e.getMessage());
            }
            return userName;
        }

        private Set<String> getRoles() {
            try {
                Method getToken = keycloakSecurityContextClass.getDeclaredMethod("getToken");
                Object accessToken = getToken.invoke(keycloakSecurityContext);
                Class accessTokenClass = Class.forName("org.keycloak.representations.AccessToken");
                Method getRealmAccess = accessTokenClass.getDeclaredMethod("getRealmAccess");
                Object access = getRealmAccess.invoke(accessToken);
                Class accessClass = access.getClass();
                Method getRoles = accessClass.getDeclaredMethod("getRoles");
                roles = (Set<String>) getRoles.invoke(access);
            } catch (Exception e) {
                LOG.warn("Failed to get user roles : " + e.getMessage());
            }
            return roles;
        }

    }
}
