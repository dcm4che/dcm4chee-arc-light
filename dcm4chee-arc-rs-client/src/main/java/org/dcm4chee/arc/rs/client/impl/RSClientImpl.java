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
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2016-2019
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

package org.dcm4chee.arc.rs.client.impl;

import org.dcm4che3.conf.api.IDeviceCache;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4chee.arc.conf.RSOperation;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.keycloak.AccessTokenRequestor;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.dcm4chee.arc.rs.client.RSClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Nov 2016
 */
@ApplicationScoped
public class RSClientImpl implements RSClient {

    private static final Logger LOG = LoggerFactory.getLogger(RSClientImpl.class);

    @Inject
    private TaskManager taskManager;

    @Inject
    private AccessTokenRequestor accessTokenRequestor;

    @Inject
    private IDeviceCache iDeviceCache;

    @Inject
    private IWebApplicationCache iWebAppCache;

    @Inject
    private Device device;

    @Override
    public void scheduleRequest(
            RSOperation rsOp,
            String requestURI,
            String requestQueryStr,
            String webAppName,
            String patientID,
            byte[] content,
            boolean tlsAllowAnyHostName,
            boolean tlsDisableTrustManager) {
        Task task = new Task();
        task.setDeviceName(device.getDeviceName());
        task.setQueueName(QUEUE_NAME);
        task.setType(Task.Type.REST);
        task.setScheduledTime(new Date());
        task.setRSOperation(rsOp.name());
        task.setRequestURI(requestURI);
        task.setQueryString(requestQueryStr);
        task.setWebApplicationName(webAppName);
        task.setPatientID(patientID);
        task.setTLSAllowAnyHostname(tlsAllowAnyHostName);
        task.setTLSDisableTrustManager(tlsDisableTrustManager);
        task.setPayload(content);
        task.setStatus(Task.Status.SCHEDULED);
        taskManager.scheduleTask(task);
    }

    @Override
    public Outcome request(String rsOp,
                           String requestURI,
                           String requestQueryString,
                           String webAppName,
                           String patientID,
                           boolean tlsAllowAnyHostname,
                           boolean tlsDisableTrustManager,
                           byte[] content) throws Exception {
        RSOperation rsOperation = RSOperation.valueOf(rsOp);
        WebApplication webApplication;
        Task.Status status = Task.Status.WARNING;
        try {
            webApplication = iWebAppCache.findWebApplication(webAppName);
        } catch (Exception e) {
            LOG.warn(e.getMessage());
            return new Outcome(status, "No such Web Application found: " + webAppName);
        }

        String targetURI = targetURI(rsOperation, requestURI, webApplication, patientID);
        if (targetURI == null)
            return new Outcome(status, "Target URL not available.");
        if (targetURI.equals(requestURI))
            return new Outcome(status, "Target URL same as Source Request URL!");

        if (requestQueryString != null)
            targetURI += "?" + requestQueryString;

        Response response = toResponse(
                getMethod(rsOperation),
                targetURI,
                tlsAllowAnyHostname,
                tlsDisableTrustManager,
                content,
                accessTokenFromWebApp(webApplication));
        Outcome outcome = buildOutcome(Response.Status.fromStatusCode(response.getStatus()), response.getStatusInfo());
        response.close();
        return outcome;
    }

    private String accessTokenFromWebApp(WebApplication webApp) throws Exception {
        if (webApp.getKeycloakClientID() == null)
            return null;

        return "Bearer " + accessTokenRequestor.getAccessToken2(webApp).getToken();
    }

    private String targetURI(RSOperation rsOp,
                       String requestURI,
                       WebApplication webApplication,
                       String patientID) {
        String targetURI = null;
        try {
            if (webApplication.containsServiceClass(WebApplication.ServiceClass.DCM4CHEE_ARC_AET)) {
                String serviceURL = webApplication.getServiceURL().toString();
                targetURI = serviceURL
                            + (serviceURL.endsWith("rs/") ? "" : "/")
                            + requestURI.substring(requestURI.indexOf("/rs/") + 4);
                if (rsOp == RSOperation.CreatePatient)
                    targetURI += patientID;
            }
        } catch (Exception e) {
            LOG.warn("Failed to construct Target URL. \n", e);
        }
        LOG.info("Target URL is {}", targetURI);
        return targetURI;
    }

    private Response toResponse(String method,
                                String uri,
                                boolean allowAnyHostname,
                                boolean disableTrustManager,
                                byte[] content,
                                String authorization) throws Exception {

        ResteasyClient client = accessTokenRequestor.resteasyClientBuilder(uri, allowAnyHostname, disableTrustManager)
                .build();
        WebTarget target = client.target(uri);
        Invocation.Builder request = target.request();
        if (authorization != null)
            request.header("Authorization", authorization);

        LOG.info("Restful Service Forward : {} {}", method, uri);

        return method.equals("POST")
                ? content != null
                    ? request.post(Entity.json(content))
                    : request.post(Entity.json(""))
                : method.equals("PUT")
                    ? request.put(Entity.json(content))
                    : request.delete();
    }

    @Override
    public Response forward(HttpServletRequest request, String deviceName, String append) throws Exception {
        LOG.info("Forward {} {} from {}@{} to device {}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost(), deviceName);
        String authorization = request.getHeader("Authorization");
        String targetURI = null;
        String requestURI = request.getRequestURI();
        Device device = iDeviceCache.findDevice(deviceName);
        for (WebApplication webApplication : device.getWebApplications())
            if (webApplication.containsServiceClass(WebApplication.ServiceClass.DCM4CHEE_ARC))
                targetURI = webApplication.getServiceURL().toString()
                                + requestURI.substring(requestURI.indexOf("/", requestURI.indexOf("/") + 1))
                                + "?" + request.getQueryString() + append;

        return targetURI == null
                ? Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Either Web Application with Service Class 'DCM4CHEE_ARC' not configured for device: "
                            + deviceName
                            + " or HTTP connection not configured for WebApplication with Service Class 'DCM4CHEE_ARC' of this device.")
                    .build()
                : toResponse("POST", targetURI, true, false,
                null, authorization);
    }

    private Outcome buildOutcome(Response.Status status, Response.StatusType st) {
        switch (status) {
            case OK:
            case NO_CONTENT:
                return new Outcome(Task.Status.COMPLETED, "Completed : " + st);
            case REQUEST_TIMEOUT:
            case SERVICE_UNAVAILABLE:
            case NOT_FOUND:
            case FORBIDDEN:
            case BAD_REQUEST:
            case UNAUTHORIZED:
            case INTERNAL_SERVER_ERROR:
                return new Outcome(Task.Status.FAILED, st.toString());
        }
        return new Outcome(Task.Status.WARNING, "Http Response Status from other archive is : " + status.toString());
    }

    private String getMethod(RSOperation rsOp) {
        String method = null;
        switch (rsOp) {
            case CreatePatient:
            case UpdatePatient:
            case UpdateStudyExpirationDate:
            case UpdateSeriesExpirationDate:
            case UpdateStudyAccessControlID:
            case ChangePatientID2:
            case MergePatient2:
            case UpdateStudy:
            case UpdateStudyRequest:
            case UpdateSeries:
            case UpdateSeriesRequest:
                method = "PUT";
                break;
            case ChangePatientID:
            case MergePatient:
            case UnmergePatient:
            case SupplementIssuer:
            case UpdateCharset:
            case ReimportStudy:
            case RejectStudy:
            case RejectSeries:
            case RejectInstance:
            case CreateMWL:
            case UpdateMWL:
            case ApplyRetentionPolicy:
            case MoveStudyToPatient:
                method = "POST";
                break;
            case DeletePatient:
            case DeleteStudy:
            case DeleteMWL:
                method = "DELETE";
                break;
        }
        return method;
    }
}
