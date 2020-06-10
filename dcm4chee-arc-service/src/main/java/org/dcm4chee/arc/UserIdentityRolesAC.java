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
package org.dcm4chee.arc;

import org.dcm4che3.net.pdu.UserIdentityAC;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Martyn Klassen <lmklassen@gmail.com>
 * @since June 2020
 */

public class UserIdentityRolesAC extends UserIdentityAC {
    private Set<String> clientRoles = new HashSet<String>();
    private Set<String> realmRoles = new HashSet<String>();

    public UserIdentityRolesAC(byte[] serverResponse) {
        super(serverResponse);
    }

    public Set<String> getRealmRoles() { return realmRoles; }

    public void addRealmRoles(Set<String> realmRoles) {
        this.realmRoles.addAll(realmRoles);
    }

    public Set<String> getClientRoles() { return clientRoles; }

    public void addClientRoles(Set<String> clientRoles) {
        this.clientRoles.addAll(clientRoles);
    }

    private static boolean filterRoles(Set<String> accepted, Set<String> limit) {
        if (accepted.isEmpty())
            accepted.addAll(limit);
        else if (!limit.isEmpty()) {
            accepted.retainAll(limit);
            return !accepted.isEmpty();
        }
        return true;
    }

    public static Set<String> filterRoles(String[] accepted, Set<String> limit) {
        Set<String> acceptedRoles = new HashSet<>(Arrays.asList(accepted));
        filterRoles(acceptedRoles, limit);
        return acceptedRoles;
    }

    public final boolean filterRolesByClientRoles(Set<String> accepted) {
        return filterRoles(accepted, clientRoles);
    }

    public final boolean filterRolesByRealmRoles(Set<String> accepted) {
        return filterRoles(accepted, realmRoles);
    }
}
