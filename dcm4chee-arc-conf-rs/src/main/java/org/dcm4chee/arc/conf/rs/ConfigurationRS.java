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
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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

package org.dcm4chee.arc.conf.rs;

import org.dcm4che3.conf.api.*;
import org.dcm4che3.conf.api.hl7.HL7ApplicationAlreadyExistsException;
import org.dcm4che3.conf.api.hl7.HL7Configuration;
import org.dcm4che3.conf.json.ConfigurationDelegate;
import org.dcm4che3.conf.json.JsonConfiguration;
import org.dcm4che3.net.*;
import org.dcm4che3.net.hl7.HL7ApplicationInfo;
import org.dcm4che3.util.ByteUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.event.SoftwareConfiguration;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParsingException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Nov 2015
 */
@Path("/")
@RequestScoped
public class ConfigurationRS {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationRS.class);

    @Inject
    private DicomConfiguration conf;

    @Inject
    private JsonConfiguration jsonConf;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpServletRequest request;

    @Inject
    private Event<SoftwareConfiguration> softwareConfigurationEvent;

    @Inject
    private Device device;

    @QueryParam("register")
    @Pattern(regexp = "true|false")
    private String register;

    @QueryParam("dcmWebServiceClass")
    @Pattern(regexp = "WADO_URI|" +
            "WADO_RS|" +
            "STOW_RS|" +
            "QIDO_RS|" +
            "UPS_RS|" +
            "DCM4CHEE_ARC|" +
            "DCM4CHEE_ARC_AET|" +
            "DCM4CHEE_ARC_AET_DIFF|" +
            "PAM|" +
            "MOVE|" +
            "MOVE_MATCHING|" +
            "REJECT|" +
            "ELASTICSEARCH")
    private String dcmWebServiceClass;

    private ConfigurationDelegate configDelegate = new ConfigurationDelegate() {
        @Override
        public Device findDevice(String name) {
            try {
                return conf.findDevice(name);
            } catch (ConfigurationException e) {
                LOG.info("Failed to load device with name " + name, e);
            }
            return null;
        }
    };

    @GET
    @NoCache
    @Path("/devices/{DeviceName}")
    @Produces("application/json")
    public Response getDevice(@PathParam("DeviceName") String deviceName) {
        logRequest();
        final Device device;
        try {
            device = conf.findDevice(deviceName);
            return Response.ok((StreamingOutput) out -> {
                    JsonGenerator w = Json.createGenerator(out);
                    jsonConf.writeTo(device, w, true);
                    w.flush();
                }).build();
        } catch (ConfigurationNotFoundException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @NoCache
    @Path("/devices")
    @Produces("application/json")
    public Response listDevices() {
        logRequest();
        try {
            final DeviceInfo[] deviceInfos = conf.listDeviceInfos(toDeviceInfo(uriInfo));
            Arrays.sort(deviceInfos, Comparator.comparing(DeviceInfo::getDeviceName));
            return Response.ok((StreamingOutput) out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    for (DeviceInfo deviceInfo : deviceInfos)
                        jsonConf.writeTo(deviceInfo, gen);
                    gen.writeEnd();
                    gen.flush();
                }).build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @NoCache
    @Path("/aes")
    @Produces("application/json")
    public Response listAETs() {
        logRequest();
        try {
            final ApplicationEntityInfo[] aeInfos = conf.listAETInfos(toApplicationEntityInfo(uriInfo));
            Arrays.sort(aeInfos, Comparator.comparing(ApplicationEntityInfo::getAETitle));
            return Response.ok((StreamingOutput) out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    for (ApplicationEntityInfo aeInfo : aeInfos)
                        jsonConf.writeTo(aeInfo, gen);
                    gen.writeEnd();
                    gen.flush();
                }).build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @NoCache
    @Path("/webapps")
    @Produces("application/json")
    public Response listWebApps() {
        logRequest();
        try {
            final WebApplicationInfo[] webappInfos = conf.listWebApplicationInfos(toWebApplicationInfo(uriInfo));
            Arrays.sort(webappInfos, Comparator.comparing(WebApplicationInfo::getApplicationName));
            return Response.ok((StreamingOutput) out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    for (WebApplicationInfo webappInfo : webappInfos)
                        jsonConf.writeTo(webappInfo, gen, keycloakClientID(webappInfo));
                    gen.writeEnd();
                    gen.flush();
                }).build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private String keycloakClientID(WebApplicationInfo webAppInfo) {
        String authURL = System.getProperty("auth-server-url");
        KeycloakClient keycloakClient = webAppInfo.getKeycloakClient();
        return keycloakClient == null || authURL == null
                || (authURL.equals(keycloakClient.getKeycloakServerURL())
                    && System.getProperty("realm-name", "dcm4che").equals(keycloakClient.getKeycloakRealm()))
                ? null : webAppInfo.getKeycloakClientID();
    }

    @GET
    @NoCache
    @Path("/hl7apps")
    @Produces("application/json")
    public Response listHL7Apps() {
        logRequest();
        try {
            HL7Configuration hl7Conf = conf.getDicomConfigurationExtension(HL7Configuration.class);
            final HL7ApplicationInfo[] hl7AppInfos = hl7Conf.listHL7AppInfos(toHL7ApplicationInfo(uriInfo));
            Arrays.sort(hl7AppInfos, Comparator.comparing(HL7ApplicationInfo::getHl7ApplicationName));
            return Response.ok((StreamingOutput) out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    for (HL7ApplicationInfo hl7AppInfo : hl7AppInfos)
                        jsonConf.writeTo(hl7AppInfo, gen);
                    gen.writeEnd();
                    gen.flush();
                }).build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @NoCache
    @Path("/unique/aets")
    @Produces("application/json")
    public Response listRegisteredAETS() {
        logRequest();
        try {
            return writeJsonArray(conf.listRegisteredAETitles());
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @NoCache
    @Path("/unique/hl7apps")
    @Produces("application/json")
    public Response listRegisteredHL7Apps() {
        logRequest();
        try {
            return writeJsonArray(
                    conf.getDicomConfigurationExtension(HL7Configuration.class)
                            .listRegisteredHL7ApplicationNames());
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @NoCache
    @Path("/unique/webAppNames")
    @Produces("application/json")
    public Response listRegisteredWebAppNames() {
        logRequest();
        try {
            return writeJsonArray(conf.listRegisteredWebAppNames());
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Response writeJsonArray(String[] values) {
        return Response.ok((StreamingOutput) out -> {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                for (String value : values)
                    gen.write(value);
                gen.writeEnd();
                gen.flush();
            }).build();
    }

    private EnumSet<DicomConfiguration.Option> options() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        EnumSet<DicomConfiguration.Option> options = EnumSet.of(
                DicomConfiguration.Option.PRESERVE_VENDOR_DATA,
                DicomConfiguration.Option.PRESERVE_CERTIFICATE,
                arcDev.isAuditSoftwareConfigurationVerbose()
                    ? DicomConfiguration.Option.CONFIGURATION_CHANGES_VERBOSE : DicomConfiguration.Option.CONFIGURATION_CHANGES);
        if (register == null || Boolean.parseBoolean(register))
            options.add(DicomConfiguration.Option.REGISTER);
        return options;
    }

    @POST
    @Path("/devices/{DeviceName}")
    @Consumes("application/json")
    public void createDevice(@PathParam("DeviceName") String deviceName, Reader content) {
        logRequest();
        Device device = toDevice(deviceName, content);
        try {
            ConfigurationChanges diffs = conf.persist(device, options());
            softwareConfigurationEvent.fire(new SoftwareConfiguration(request, deviceName, diffs));
        } catch (IllegalStateException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        } catch (AETitleAlreadyExistsException | HL7ApplicationAlreadyExistsException | WebAppAlreadyExistsException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @PUT
    @Path("/devices/{DeviceName}")
    @Consumes("application/json")
    public void updateDevice(@PathParam("DeviceName") String deviceName, Reader content) {
        logRequest();
        Device device = toDevice(deviceName, content);
        try {
            ConfigurationChanges diffs = conf.merge(device, options());
            if (!diffs.isEmpty())
                softwareConfigurationEvent.fire(new SoftwareConfiguration(request, deviceName, diffs));
        } catch (IllegalStateException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        } catch (AETitleAlreadyExistsException | HL7ApplicationAlreadyExistsException | WebAppAlreadyExistsException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @POST
    @Path("/unique/aets/{aet}")
    @Consumes("application/json")
    public void registerAET(@PathParam("aet") String aet) {
        logRequest();
        try {
            if (!conf.registerAETitle(aet))
                throw new WebApplicationException(
                        errResponse("Application Entity Title " + aet + " already registered.",
                                Response.Status.CONFLICT));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @DELETE
    @Path("/unique/aets/{aet}")
    public void unregisterAET(@PathParam("aet") String aet) {
        logRequest();
        try {
            List<String> aets = Arrays.asList(conf.listRegisteredAETitles());
            if (!aets.contains(aet))
                throw new WebApplicationException(
                        errResponse("Application Entity Title " + aet + " not registered.",
                                Response.Status.NOT_FOUND));
            conf.unregisterAETitle(aet);
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @POST
    @Path("/unique/hl7apps/{appName}")
    @Consumes("application/json")
    public void registerHL7App(@PathParam("appName") String appName) {
        logRequest();
        try {
            HL7Configuration hl7Conf = conf.getDicomConfigurationExtension(HL7Configuration.class);
            if (!hl7Conf.registerHL7Application(appName))
                throw new WebApplicationException(
                        errResponse("HL7 Application " + appName + " already registered.",
                        Response.Status.CONFLICT));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @DELETE
    @Path("/unique/hl7apps/{appName}")
    public void unregisterHL7App(@PathParam("appName") String appName) {
        logRequest();
        try {
            HL7Configuration hl7Conf = conf.getDicomConfigurationExtension(HL7Configuration.class);
            List<String> hl7apps = Arrays.asList(hl7Conf.listRegisteredHL7ApplicationNames());
            if (!hl7apps.contains(appName))
                throw new WebApplicationException(
                        errResponse("HL7 Application " + appName + " not registered.",
                        Response.Status.NOT_FOUND));
                hl7Conf.unregisterHL7Application(appName);
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @POST
    @Path("/unique/webApps/{webAppName}")
    @Consumes("application/json")
    public void registerWebApp(@PathParam("webAppName") String webAppName) {
        logRequest();
        try {
            if (!conf.registerWebAppName(webAppName))
                throw new WebApplicationException(
                        errResponse("Web Application " + webAppName + " already registered.",
                        Response.Status.CONFLICT));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @DELETE
    @Path("/unique/webApps/{webAppName}")
    public void unregisterWebApp(@PathParam("webAppName") String webAppName) {
        logRequest();
        try {
            List<String> webApps = Arrays.asList(conf.listRegisteredWebAppNames());
            if (!webApps.contains(webAppName))
                throw new WebApplicationException(
                        errResponse("Web Application " + webAppName + " not registered.",
                        Response.Status.NOT_FOUND));
            conf.unregisterWebAppName(webAppName);
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @DELETE
    @Path("/devices/{DeviceName}")
    public void deleteDevice(@PathParam("DeviceName") String deviceName) {
        logRequest();
        try {
            ConfigurationChanges diffs = conf.removeDevice(deviceName, options());
            softwareConfigurationEvent.fire(new SoftwareConfiguration(request, deviceName, diffs));
        } catch (IllegalStateException | ConfigurationNotFoundException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @GET
    @NoCache
    @Path("/devices/{deviceName}/vendordata")
    @Produces("application/zip")
    public Response getVendorData(@PathParam("deviceName") String deviceName) {
        logRequest();
        byte[] content = ByteUtils.EMPTY_BYTES;
        Response.Status status = Response.Status.NO_CONTENT;
        try {
            byte[][] vendorData = conf.loadDeviceVendorData(deviceName);
            if (vendorData.length > 0) {
                content = vendorData[0];
                status = Response.Status.OK;
            }
        } catch (ConfigurationNotFoundException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
        return Response.status(status)
                .entity(content)
                .type("application/zip")
                .header("Content-Disposition", "attachment; filename=vendordata.zip")
                .build();
    }

    @PUT
    @Path("/devices/{deviceName}/vendordata")
    @Consumes("application/zip")
    public Response updateVendorData(@PathParam("deviceName") String deviceName, File file) {
        logRequest();
        try {
            ConfigurationChanges diffs = conf.updateDeviceVendorData(deviceName, Files.readAllBytes(file.toPath()));
            if (!diffs.isEmpty())
                softwareConfigurationEvent.fire(new SoftwareConfiguration(request, deviceName, diffs));
        } catch (ConfigurationNotFoundException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
        return Response.noContent().build();
    }

    @DELETE
    @Path("/devices/{deviceName}/vendordata")
    public Response deleteVendorData(@PathParam("deviceName") String deviceName) {
        logRequest();
        try {
            ConfigurationChanges diffs = conf.updateDeviceVendorData(deviceName);
            if (!diffs.isEmpty())
                softwareConfigurationEvent.fire(new SoftwareConfiguration(request, deviceName, diffs));
        } catch (ConfigurationNotFoundException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
        return Response.noContent().build();
    }

    private Device toDevice(String deviceName, Reader content) {
        Device device;
        try {
            device = jsonConf.loadDeviceFrom(Json.createParser(content), configDelegate);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        } catch (ConfigurationNotFoundException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }

        if (!device.getDeviceName().equals(deviceName))
            throw new WebApplicationException(
                    errResponse("Device name in content[" + device.getDeviceName() + "] does not match Device name in URL",
                    Response.Status.BAD_REQUEST));

        return device;
    }

    private static DeviceInfo toDeviceInfo(UriInfo info) {
        DeviceInfo deviceInfo = new DeviceInfo();
        info.getQueryParameters().forEach((key, value) -> {
            switch (key) {
                case "dicomDeviceName":
                    deviceInfo.setDeviceName(firstValueOf(value));
                    break;
                case "dicomDescription":
                    deviceInfo.setDescription(firstValueOf(value));
                    break;
                case "dicomManufacturer":
                    deviceInfo.setManufacturer(firstValueOf(value));
                    break;
                case "dicomManufacturerModelName":
                    deviceInfo.setManufacturerModelName(firstValueOf(value));
                    break;
                case "dicomSoftwareVersion":
                    deviceInfo.setSoftwareVersions(toArray(value));
                    break;
                case "dicomStationName":
                    deviceInfo.setStationName(firstValueOf(value));
                    break;
                case "dicomInstitutionName":
                    deviceInfo.setInstitutionNames(toArray(value));
                    break;
                case "dicomInstitutionDepartmentName":
                    deviceInfo.setInstitutionalDepartmentNames(toArray(value));
                    break;
                case "dicomPrimaryDeviceType":
                    deviceInfo.setPrimaryDeviceTypes(toArray(value));
                    break;
                case "dicomInstalled":
                    deviceInfo.setInstalled(Boolean.parseBoolean(firstValueOf(value)));
                    break;
                case "hasArcDevExt":
                    deviceInfo.setArcDevExt(Boolean.parseBoolean(firstValueOf(value)));
                    break;
            }
        });
        return deviceInfo;
    }

    private static String firstValueOf(List<String> value) {
        return value.get(0);
    }

    private static String[] toArray(List<String> value) {
        return value.toArray(StringUtils.EMPTY_STRING);
    }

    private static ApplicationEntityInfo toApplicationEntityInfo(UriInfo info) {
        ApplicationEntityInfo aetInfo = new ApplicationEntityInfo();
        info.getQueryParameters().forEach((key, value) -> {
            switch (key) {
                case "dicomDeviceName":
                    aetInfo.setDeviceName(firstValueOf(value));
                    break;
                case "dicomAETitle":
                    aetInfo.setAETitle(firstValueOf(value));
                    break;
                case "dicomAssociationInitiator":
                    aetInfo.setAssociationInitiator(Boolean.parseBoolean(firstValueOf(value)));
                    break;
                case "dicomAssociationAcceptor":
                    aetInfo.setAssociationAcceptor(Boolean.parseBoolean(firstValueOf(value)));
                    break;
                case "dicomDescription":
                    aetInfo.setDescription(firstValueOf(value));
                    break;
                case "dicomApplicationCluster":
                    aetInfo.setApplicationClusters(toArray(value));
                    break;
            }
        });
        return aetInfo;
    }

    private static WebApplicationInfo toWebApplicationInfo(UriInfo info) {
        WebApplicationInfo webappInfo = new WebApplicationInfo();
        info.getQueryParameters().forEach((key, value) -> {
            switch (key) {
                case "dicomDeviceName":
                    webappInfo.setDeviceName(firstValueOf(value));
                    break;
                case "dcmWebAppName":
                    webappInfo.setApplicationName(firstValueOf(value));
                    break;
                case "dicomDescription":
                    webappInfo.setDescription(firstValueOf(value));
                    break;
                case "dcmWebServicePath":
                    webappInfo.setServicePath(firstValueOf(value));
                    break;
                case "dcmWebServiceClass":
                    webappInfo.setServiceClasses(toServiceClasses(value));
                    break;
                case "dicomAETitle":
                    webappInfo.setAETitle(firstValueOf(value));
                    break;
                case "dicomApplicationCluster":
                    webappInfo.setApplicationClusters(toArray(value));
                    break;
                case "dcmKeycloakClientID":
                    webappInfo.setKeycloakClientID(firstValueOf(value));
                    break;
            }
        });
        return webappInfo;
    }

    private static WebApplication.ServiceClass[] toServiceClasses(List<String> values) {
        return values.stream()
                .map(WebApplication.ServiceClass::valueOf)
                .toArray(WebApplication.ServiceClass[]::new);
    }

    private static HL7ApplicationInfo toHL7ApplicationInfo(UriInfo info) {
        HL7ApplicationInfo hl7AppInfo = new HL7ApplicationInfo();
        info.getQueryParameters().forEach((key, value) -> {
            switch (key) {
                case "dicomDeviceName":
                    hl7AppInfo.setDeviceName(firstValueOf(value));
                    break;
                case "hl7ApplicationName":
                    hl7AppInfo.setHl7ApplicationName(firstValueOf(value));
                    break;
                case "dicomApplicationCluster":
                    hl7AppInfo.setApplicationClusters(toArray(value));
                    break;
            }
        });
        return hl7AppInfo;
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Response errResponse(String msg, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + msg + "\"}", status);
    }

    private Response errResponseAsTextPlain(String errorMsg, Response.Status status) {
        LOG.warn("Response {} caused by {}", status, errorMsg);
        return Response.status(status)
                .entity(errorMsg)
                .type("text/plain")
                .build();
    }

    private String exceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
