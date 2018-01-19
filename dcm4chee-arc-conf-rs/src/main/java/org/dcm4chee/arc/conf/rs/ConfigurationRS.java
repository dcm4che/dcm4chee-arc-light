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
 * Portions created by the Initial Developer are Copyright (C) 2017
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
import org.dcm4che3.net.ApplicationEntityInfo;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DeviceInfo;
import org.dcm4che3.net.hl7.HL7ApplicationInfo;
import org.dcm4che3.util.ByteUtils;
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

    @QueryParam("options")
    @Pattern(regexp = "true|false")
    private String register;

    private ConfigurationDelegate configDelegate = new ConfigurationDelegate() {
        @Override
        public Device findDevice(String name) throws ConfigurationException {
            return conf.findDevice(name);
        }
    };

    @GET
    @NoCache
    @Path("/devices/{DeviceName}")
    @Produces("application/json")
    public StreamingOutput getDevice(@PathParam("DeviceName") String deviceName) {
        logRequest();
        final Device device;
        try {
            device = conf.findDevice(deviceName);
            return out -> {
                JsonGenerator w = Json.createGenerator(out);
                jsonConf.writeTo(device, w, true);
                w.flush();
            };
        } catch (ConfigurationNotFoundException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
        }
    }

    @GET
    @NoCache
    @Path("/devices")
    @Produces("application/json")
    public StreamingOutput listDevices() {
        logRequest();
        try {
            final DeviceInfo[] deviceInfos = conf.listDeviceInfos(new DeviceInfoBuilder(uriInfo).deviceInfo);
            Arrays.sort(deviceInfos, Comparator.comparing(DeviceInfo::getDeviceName));
            return out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    for (DeviceInfo deviceInfo : deviceInfos)
                        jsonConf.writeTo(deviceInfo, gen);
                    gen.writeEnd();
                    gen.flush();
            };
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
        }
    }

    @GET
    @NoCache
    @Path("/aes")
    @Produces("application/json")
    public StreamingOutput listAETs() {
        logRequest();
        try {
            final ApplicationEntityInfo[] aeInfos =
                    conf.listAETInfos(new ApplicationEntityInfoBuilder(uriInfo).aetInfo);
            Arrays.sort(aeInfos, Comparator.comparing(ApplicationEntityInfo::getAETitle));
            return out -> {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                for (ApplicationEntityInfo aeInfo : aeInfos)
                    jsonConf.writeTo(aeInfo, gen);
                gen.writeEnd();
                gen.flush();
            };
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
        }
    }

    @GET
    @NoCache
    @Path("/hl7apps")
    @Produces("application/json")
    public StreamingOutput listHL7Apps() {
        logRequest();
        try {
            HL7Configuration hl7Conf = conf.getDicomConfigurationExtension(HL7Configuration.class);
            final HL7ApplicationInfo[] hl7AppInfos =
                    hl7Conf.listHL7AppInfos(new HL7ApplicationInfoBuilder(uriInfo).hl7AppInfo);
            Arrays.sort(hl7AppInfos, Comparator.comparing(HL7ApplicationInfo::getHl7ApplicationName));
            return out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    for (HL7ApplicationInfo hl7AppInfo : hl7AppInfos)
                        jsonConf.writeTo(hl7AppInfo, gen);
                    gen.writeEnd();
                    gen.flush();
            };
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
        }
    }

    @GET
    @NoCache
    @Path("/unique/aets")
    @Produces("application/json")
    public StreamingOutput listRegisteredAETS() {
        logRequest();
        try {
            return writeJsonArray(conf.listRegisteredAETitles());
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
        }
    }

    @GET
    @NoCache
    @Path("/unique/hl7apps")
    @Produces("application/json")
    public StreamingOutput listRegisteredHL7Apps() {
        logRequest();
        try {
            return writeJsonArray(
                    conf.getDicomConfigurationExtension(HL7Configuration.class)
                            .listRegisteredHL7ApplicationNames());
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
        }
    }

    private StreamingOutput writeJsonArray(String[] values) {
        return out -> {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                for (String value : values)
                    gen.write(value);
                gen.writeEnd();
                gen.flush();
        };
    }

    private EnumSet<DicomConfiguration.Option> options() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
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
        try {
            Device device = jsonConf.loadDeviceFrom(Json.createParser(content), configDelegate);
            if (!device.getDeviceName().equals(deviceName))
                throw new IllegalArgumentException(
                        "Device name in content[" + device.getDeviceName() + "] does not match Device name in URL");
            ConfigurationChanges diffs = conf.persist(device, options());
            softwareConfigurationEvent.fire(new SoftwareConfiguration(request, deviceName, diffs));
        } catch (ConfigurationNotFoundException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.BAD_REQUEST));
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        } catch (AETitleAlreadyExistsException | HL7ApplicationAlreadyExistsException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
        }
    }

    @PUT
    @Path("/devices/{DeviceName}")
    @Consumes("application/json")
    public void updateDevice(@PathParam("DeviceName") String deviceName, Reader content) {
        logRequest();
        try {
            Device device = jsonConf.loadDeviceFrom(Json.createParser(content), configDelegate);
            if (!device.getDeviceName().equals(deviceName))
                throw new IllegalArgumentException(
                        "Device name in content[" + device.getDeviceName() + "] does not match Device name in URL");
            ConfigurationChanges diffs = conf.merge(device, options());
            if (!diffs.isEmpty())
                softwareConfigurationEvent.fire(new SoftwareConfiguration(request, deviceName, diffs));
        } catch (ConfigurationNotFoundException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.BAD_REQUEST));
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        } catch (AETitleAlreadyExistsException | HL7ApplicationAlreadyExistsException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
        }
    }

    @POST
    @Path("/unique/aets/{aet}")
    @Consumes("application/json")
    public void registerAET(@PathParam("aet") String aet) {
        logRequest();
        try {
            if (!conf.registerAETitle(aet))
                throw new WebApplicationException(errResponse(
                        "Application Entity Title " + aet + " already registered.", Response.Status.CONFLICT));
        } catch (ConfigurationException e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
        }
    }

    @DELETE
    @Path("/unique/aets/{aet}")
    public void unregisterAET(@PathParam("aet") String aet) {
        logRequest();
        try {
            List<String> aets = Arrays.asList(conf.listRegisteredAETitles());
            if (!aets.contains(aet))
                throw new WebApplicationException(errResponse(
                        "Application Entity Title " + aet + " not registered.", Response.Status.NOT_FOUND));
            conf.unregisterAETitle(aet);
        } catch (ConfigurationException e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
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
                throw new WebApplicationException(errResponse(
                        "HL7 Application " + appName + " already registered.", Response.Status.CONFLICT));
        } catch (ConfigurationException e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
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
                throw new WebApplicationException(errResponse(
                        "HL7 Application " + appName + " not registered.", Response.Status.NOT_FOUND));
                hl7Conf.unregisterHL7Application(appName);
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
        }
    }

    @DELETE
    @Path("/devices/{DeviceName}")
    public void deleteDevice(@PathParam("DeviceName") String deviceName) {
        logRequest();
        try {
            ConfigurationChanges diffs = conf.removeDevice(deviceName, options());
            softwareConfigurationEvent.fire(new SoftwareConfiguration(request, deviceName, diffs));
        } catch (ConfigurationNotFoundException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
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
            return errResponseAsTextPlain(e);
        }
        return Response.ok(content).status(status).type("application/zip").header("Content-Disposition", "attachment; filename=vendordata.zip").build();
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
            return errResponseAsTextPlain(e);
        }
        return Response.ok().status(Response.Status.NO_CONTENT).build();
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
            return errResponseAsTextPlain(e);
        }
        return Response.ok().status(Response.Status.NO_CONTENT).build();
    }

    private static class DeviceInfoBuilder {
        final DeviceInfo deviceInfo = new DeviceInfo();

        DeviceInfoBuilder(UriInfo info) {
            MultivaluedMap<String, String> map = info.getQueryParameters();
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                switch(entry.getKey()) {
                    case "dicomDeviceName":
                        deviceInfo.setDeviceName(toString(entry));
                        break;
                    case "dicomDescription":
                        deviceInfo.setDescription(toString(entry));
                        break;
                    case "dicomManufacturer":
                        deviceInfo.setManufacturer(toString(entry));
                        break;
                    case "dicomManufacturerModelName":
                        deviceInfo.setManufacturerModelName(toString(entry));
                        break;
                    case "dicomSoftwareVersion":
                        deviceInfo.setSoftwareVersions(toStrings(entry));
                        break;
                    case "dicomStationName":
                        deviceInfo.setStationName(toString(entry));
                        break;
                    case "dicomInstitutionName":
                        deviceInfo.setInstitutionNames(toStrings(entry));
                        break;
                    case "dicomInstitutionDepartmentName":
                        deviceInfo.setInstitutionalDepartmentNames(toStrings(entry));
                        break;
                    case "dicomPrimaryDeviceType":
                        deviceInfo.setPrimaryDeviceTypes(toStrings(entry));
                        break;
                    case "dicomInstalled":
                        deviceInfo.setInstalled(Boolean.parseBoolean(toString(entry)));
                        break;
                    case "hasArcDevExt":
                        deviceInfo.setArcDevExt(Boolean.parseBoolean(toString(entry)));
                        break;
                }
            }
        }

        static String[] toStrings(Map.Entry<String, List<String>> entry) {
            return entry.getValue().toArray(new String[entry.getValue().size()]);
        }

        static String toString(Map.Entry<String, List<String>> entry) {
            return entry.getValue().get(0);
        }
    }


    private static class ApplicationEntityInfoBuilder {
        final ApplicationEntityInfo aetInfo = new ApplicationEntityInfo();

        ApplicationEntityInfoBuilder(UriInfo info) {
            MultivaluedMap<String, String> map = info.getQueryParameters();
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                switch(entry.getKey()) {
                    case "dicomDeviceName":
                        aetInfo.setDeviceName(toString(entry));
                        break;
                    case "dicomAETitle":
                        aetInfo.setAETitle(toString(entry));
                        break;
                    case "dicomAssociationInitiator":
                        aetInfo.setAssociationInitiator(Boolean.parseBoolean(toString(entry)));
                        break;
                    case "dicomAssociationAcceptor":
                        aetInfo.setAssociationAcceptor(Boolean.parseBoolean(toString(entry)));
                        break;
                    case "dicomDescription":
                        aetInfo.setDescription(toString(entry));
                        break;
                    case "dicomApplicationCluster":
                        aetInfo.setApplicationCluster(toStrings(entry));
                        break;
                }
            }
        }

        static String[] toStrings(Map.Entry<String, List<String>> entry) {
            return entry.getValue().toArray(new String[entry.getValue().size()]);
        }

        static String toString(Map.Entry<String, List<String>> entry) {
            return entry.getValue().get(0);
        }
    }

    private static class HL7ApplicationInfoBuilder {
        final HL7ApplicationInfo hl7AppInfo = new HL7ApplicationInfo();

        HL7ApplicationInfoBuilder(UriInfo info) {
            MultivaluedMap<String, String> map = info.getQueryParameters();
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                switch (entry.getKey()) {
                    case "dicomDeviceName":
                        hl7AppInfo.setDeviceName(toString(entry));
                        break;
                    case "hl7ApplicationName":
                        hl7AppInfo.setHl7ApplicationName(toString(entry));
                        break;
                    case "dicomApplicationCluster":
                        hl7AppInfo.setApplicationClusters(toStrings(entry));
                        break;
                }
            }
        }

        static String[] toStrings(Map.Entry<String, List<String>> entry) {
            return entry.getValue().toArray(new String[entry.getValue().size()]);
        }

        static String toString(Map.Entry<String, List<String>> entry) {
            return entry.getValue().get(0);
        }
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private Response errResponse(Object errorMessage, Response.Status status) {
        Object entity = "{\"errorMessage\":\"" + errorMessage + "\"}";
        return Response.status(status).entity(entity).build();
    }

    private Response errResponseAsTextPlain(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String exceptionAsString = sw.toString();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(exceptionAsString).type("text/plain").build();
    }
}
