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

import org.dcm4che3.util.StringUtils;
import org.keycloak.KeycloakSecurityContext;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2017
 */

public class KeycloakContext {

    private final HttpServletRequest request;
    private final KeycloakSecurityContext ksc;

    public static KeycloakContext valueOf(HttpServletRequest request) {
        return new KeycloakContext(request);
    }

    private KeycloakContext(HttpServletRequest req) {
        request = req;
        ksc = (KeycloakSecurityContext) request.getAttribute("org.keycloak.KeycloakSecurityContext");
    }

    public String getUserName() {
        return ksc != null ? ksc.getToken().getPreferredUsername() : request.getRemoteAddr();
    }

    public String getToken() {
        return ksc != null ? ksc.getTokenString() : null;
    }

    public int getExpiration() {
        return ksc != null ? ksc.getToken().getExpiration() : 0;
    }

    public String[] getUserRoles() {
        return ksc != null
                ? ksc.getToken().getRealmAccess().getRoles().toArray(StringUtils.EMPTY_STRING)
                : StringUtils.EMPTY_STRING;
    }
}
