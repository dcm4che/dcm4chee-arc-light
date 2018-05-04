/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.keycloak;

import org.dcm4che3.net.Device;
import org.dcm4che3.net.SSLManagerFactory;
import org.dcm4chee.arc.conf.KeycloakServer;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.security.KeyStore;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <gunterze@gmail.com>
 * @since May 2018
 */
@ApplicationScoped
public class AccessTokenRequestor {

    @Inject
    private Device device;

    private CachedKeycloak cachedKeycloak;

    public String getAccessTokenString(KeycloakServer server) throws Exception {
        CachedKeycloak tmp = cachedKeycloak;
        if (tmp == null || !tmp.keycloakServerID.equals(server.getKeycloakServerID()))
            cachedKeycloak = tmp = new CachedKeycloak(server.getKeycloakServerID(), KeycloakBuilder.builder()
                    .serverUrl(server.getServerURL())
                    .realm(server.getRealm())
                    .clientId(server.getClientID())
                    .clientSecret(server.getClientSecret())
                    .username(server.getUserID())
                    .password(server.getPassword())
                    .grantType(server.getGrantType().name())
                    .resteasyClient(
                            new ResteasyClientBuilder()
                                    .hostnameVerification(ResteasyClientBuilder.HostnameVerificationPolicy.ANY)
                                    .trustStore(truststore()).build())
                    .build());

        return tmp.keycloak.tokenManager().getAccessTokenString();
    }

    private KeyStore truststore() throws Exception {
        return device.getTrustStoreURL() != null
                ? SSLManagerFactory.loadKeyStore(device.getTrustStoreType(), device.getTrustStoreURL(), device.getTrustStorePin())
                : SSLManagerFactory.createKeyStore(device.getAllAuthorizedNodeCertificates());
    }

    private static class CachedKeycloak {
        final String keycloakServerID;
        final Keycloak keycloak;

        CachedKeycloak(String keycloakServerID, Keycloak keycloak) {
            this.keycloakServerID = keycloakServerID;
            this.keycloak = keycloak;
        }
    }
}
