/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.keycloak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2017
 */

public class KeycloakUtils {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakUtils.class);

    private static String keycloakSecurityContextClassName = "org.keycloak.KeycloakSecurityContext";

    public static String getUserName(HttpServletRequest request) {
        Object refreshableKeycloakSecurityContext = request.getAttribute(keycloakSecurityContextClassName);
        if (refreshableKeycloakSecurityContext != null) {
            try {
                Method getIdToken = Class.forName(keycloakSecurityContextClassName)
                                        .getDeclaredMethod("getIdToken");
                Object idToken = getIdToken.invoke(refreshableKeycloakSecurityContext);
                Method getPreferredUsername = idToken.getClass().getDeclaredMethod("getPreferredUsername");
                return String.valueOf(getPreferredUsername.invoke(idToken));
            } catch (Exception e) {
                LOG.warn("Failed to get user name from Keycloak Security Context : ", e);
            }
        }
        return request.getRemoteAddr();
    }

    public static Set<String> getUserRoles(HttpServletRequest request) {
        Object refreshableKeycloakSecurityContext = request.getAttribute(keycloakSecurityContextClassName);
        if (refreshableKeycloakSecurityContext != null) {
            try {
                Method getToken = refreshableKeycloakSecurityContext.getClass().getDeclaredMethod("getToken");
                Object accessToken = getToken.invoke(refreshableKeycloakSecurityContext);
                Method getRealmAccess = accessToken.getClass().getDeclaredMethod("getRealmAccess");
                Object access = getRealmAccess.invoke(accessToken);
                Method getRoles = access.getClass().getDeclaredMethod("getRoles");
                return (Set<String>) getRoles.invoke(access);
            } catch (Exception e) {
                LOG.warn("Failed to get user roles from Keycloak Security Context : ", e);
            }
        }
        return Collections.EMPTY_SET;
    }

}
