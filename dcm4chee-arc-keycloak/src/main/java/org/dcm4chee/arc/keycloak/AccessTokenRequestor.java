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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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
import org.dcm4che3.net.KeycloakClient;
import org.dcm4che3.net.WebApplication;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.KeycloakServer;
import org.dcm4chee.arc.event.ArchiveServiceEvent;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.TokenVerifier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.token.TokenManager;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.keycloak.constants.ServiceUrlConstants;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.JWKSUtils;
import org.keycloak.util.JsonSerialization;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import java.io.InputStream;
import java.security.PublicKey;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2018
 */
@ApplicationScoped
public class AccessTokenRequestor {

    @Inject
    private Device device;

    private volatile CachedKeycloak cachedKeycloak;
    private volatile CachedKeycloak cachedKeycloakClient;
    private volatile CachedPublicKey cachedPublicKey;

    public void onArchiveServiceEvent(@Observes ArchiveServiceEvent event) {
        if (event.getType() == ArchiveServiceEvent.Type.RELOADED)
            cachedKeycloak = null;
    }
    
    public AccessTokenWithExpiration getAccessToken(String keycloakServerID) throws Exception {
        CachedKeycloak tmp = toCachedKeycloak(keycloakServerID);
        TokenManager tokenManager = tmp.keycloak.tokenManager();
        return new AccessTokenWithExpiration(
                tokenManager.getAccessTokenString(),
                tokenManager.getAccessToken().getExpiresIn());
    }

    public AccessTokenWithExpiration getAccessToken2(WebApplication webApp) throws Exception {
        CachedKeycloak tmp = toCachedKeycloakClient(webApp);
        TokenManager tokenManager = tmp.keycloak.tokenManager();
        return new AccessTokenWithExpiration(
                tokenManager.getAccessTokenString(),
                tokenManager.getAccessToken().getExpiresIn());
    }

    private CachedKeycloak toCachedKeycloak(String keycloakServerID) throws Exception {
        CachedKeycloak tmp = cachedKeycloak;
        if (tmp == null || !tmp.keycloakID.equals(keycloakServerID)) {
            KeycloakServer keycloakServer = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                    .getKeycloakServerNotNull(keycloakServerID);
            cachedKeycloak = tmp = cachedKeycloak(keycloakServerID,
                    keycloakServer.getServerURL(),
                    keycloakServer.getRealm(),
                    keycloakServer.getClientID(),
                    keycloakServer.getClientSecret(),
                    keycloakServer.getUserID(),
                    keycloakServer.getPassword(),
                    keycloakServer.getGrantType().name(),
                    keycloakServer.isTlsAllowAnyHostname(),
                    keycloakServer.isTlsDisableTrustManager());
        }
        return tmp;
    }

    private CachedKeycloak toCachedKeycloakClient(WebApplication webApp) throws Exception {
        CachedKeycloak tmp = cachedKeycloakClient;
        if (tmp == null || !tmp.keycloakID.equals(webApp.getKeycloakClientID())) {
            KeycloakClient keycloakClient = webApp.getKeycloakClient();
            if (keycloakClient == null)
                throw new IllegalArgumentException("No Keycloak Client configured with ID:" + webApp.getKeycloakClientID());

            cachedKeycloakClient = tmp = cachedKeycloak(webApp.getKeycloakClientID(),
                    keycloakClient.getKeycloakServerURL(),
                    keycloakClient.getKeycloakRealm(),
                    keycloakClient.getKeycloakClientID(),
                    keycloakClient.getKeycloakClientSecret(),
                    keycloakClient.getUserID(),
                    keycloakClient.getPassword(),
                    keycloakClient.getKeycloakGrantType().name(),
                    keycloakClient.isTLSAllowAnyHostname(),
                    keycloakClient.isTLSDisableTrustManager());
        }
        return tmp;
    }

    private CachedKeycloak cachedKeycloak(String keycloakID, String serverURL, String realm, String clientID,
                                          String clientSecret, String userID, String password, String grantType,
                                          boolean tlsAllowAnyHost, boolean tlsDisableTrustMgr) throws Exception {
        return new CachedKeycloak(keycloakID, KeycloakBuilder.builder()
                .serverUrl(serverURL)
                .realm(realm)
                .clientId(clientID)
                .clientSecret(clientSecret)
                .username(userID)
                .password(password)
                .grantType(grantType)
                .resteasyClient(resteasyClientBuilder(serverURL, tlsAllowAnyHost, tlsDisableTrustMgr).build())
                .build());
    }

    public ResteasyClientBuilder resteasyClientBuilder(
            String url, boolean allowAnyHostname, boolean disableTrustManager) throws Exception {
        ResteasyClientBuilder builder = new ResteasyClientBuilder();
        if (url.toLowerCase().startsWith("https")) {
            builder.sslContext(device.sslContext())
                    .hostnameVerification(allowAnyHostname
                            ? ResteasyClientBuilder.HostnameVerificationPolicy.ANY
                            : ResteasyClientBuilder.HostnameVerificationPolicy.WILDCARD);
            if (disableTrustManager)
                builder.disableTrustManager();
        }
        return builder;
    }

    public boolean isUserInRole(String username, char[] passcode, String role,
            String serverURL, String realm, String clientId,
            boolean allowAnyHostname, boolean disableTrustManager)
            throws Exception {
        //TODO
        return false;
    }

    public boolean isUserInRole(String tokenString, String role,
            String serverURL, String realm, boolean allowAnyHostname, boolean disableTrustManager)
            throws Exception {
        TokenVerifier<AccessToken> tokenVerifier = TokenVerifier.create(tokenString, AccessToken.class);
        String kid = tokenVerifier.getHeader().getKeyId();
        PublicKey publicKey = getPublicKey(kid, serverURL, realm, allowAnyHostname, disableTrustManager);
        tokenVerifier.publicKey(publicKey);
        AccessToken token = tokenVerifier.verify().getToken();
        return token.getRealmAccess().isUserInRole(role);
    }

    private PublicKey getPublicKey(String kid, String serverURL, String realmName,
            boolean allowAnyHostname, boolean disableTrustManager)
            throws Exception {
        CachedPublicKey tmp = cachedPublicKey;
        if (tmp != null
                && tmp.kid.equals(kid)
                && tmp.serverURL.equals(serverURL)
                && tmp.realmName.equals(realmName)) {
            return tmp.key;
        }
        ResteasyClient client = resteasyClientBuilder(serverURL, allowAnyHostname, disableTrustManager).build();
        try {
            KeycloakUriBuilder authUrlBuilder = KeycloakUriBuilder.fromUri(serverURL);
            String jwksUrl = authUrlBuilder.clone().path(ServiceUrlConstants.JWKS_URL)
                    .build(realmName).toString();
            WebTarget target = client.target(jwksUrl);
            Invocation.Builder request = target.request();
            try (InputStream is = request.get(InputStream.class)) {
                JSONWebKeySet jwks = JsonSerialization.readValue(is, JSONWebKeySet.class);
                Map<String, PublicKey> publicKeys = JWKSUtils.getKeysForUse(jwks, JWK.Use.SIG);
                PublicKey publicKey = publicKeys.get(kid);
                if (publicKey != null) {
                    cachedPublicKey = new CachedPublicKey(serverURL, realmName, kid, publicKey);
                }
                return publicKey;
            }
        } finally {
            client.close();
        }
    }

    private static class CachedKeycloak {
        final String keycloakID;
        final Keycloak keycloak;

        CachedKeycloak(String keycloakID, Keycloak keycloak) {
            this.keycloakID = keycloakID;
            this.keycloak = keycloak;
        }
    }
    
    public static class AccessTokenWithExpiration {
        final String token;
        final long expiration;
        
        AccessTokenWithExpiration(String tokenStr, long expiresIn) {
            token = tokenStr;
            expiration = expiresIn;
        }

        public String getToken() {
            return token;
        }

        public long getExpiration() {
            return expiration;
        }
    }

    private static class CachedPublicKey {
        final String serverURL;
        final String realmName;
        final String kid;
        final PublicKey key;

        private CachedPublicKey(String serverURL, String realmName, String kid, PublicKey key) {
            this.serverURL = serverURL;
            this.realmName = realmName;
            this.kid = kid;
            this.key = key;
        }
    }

}
