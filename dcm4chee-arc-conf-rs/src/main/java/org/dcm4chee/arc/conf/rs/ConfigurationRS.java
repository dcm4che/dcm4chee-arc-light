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

package org.dcm4chee.arc.conf.rs;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.api.hl7.HL7Configuration;
import org.dcm4che3.conf.json.ConfigurationDelegate;
import org.dcm4che3.conf.json.JsonConfiguration;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.net.ApplicationEntityInfo;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DeviceInfo;
import org.jboss.resteasy.annotations.cache.NoCache;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Nov 2015
 */
@Path("/")
@RequestScoped
public class ConfigurationRS {

    @Inject
    private DicomConfiguration conf;

    @Inject
    private JsonConfiguration jsonConf;

    @Context
    private UriInfo uriInfo;

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
    public StreamingOutput getDevice(@PathParam("DeviceName") String deviceName) throws Exception {
        final Device device;
        device = conf.findDevice(deviceName);
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                JsonGenerator w = Json.createGenerator(out);
                jsonConf.writeTo(device, w);
                w.flush();
            }
        };
    }

    @GET
    @NoCache
    @Path("/auth")
    @Produces("application/json")
    public String getAuthServerURL() throws Exception {
        return "{\"url\":\"" + System.getProperty("auth-server-url", "/auth") + "\"}";
    }

    @GET
    @NoCache
    @Path("/devices")
    @Produces("application/json")
    public StreamingOutput listDevices() throws Exception {
        final DeviceInfo[] deviceInfos = conf.listDeviceInfos(new DeviceInfoBuilder(uriInfo).deviceInfo);
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                for (DeviceInfo deviceInfo : deviceInfos)
                    jsonConf.writeTo(deviceInfo, gen);
                gen.writeEnd();
                gen.flush();
            }
        };
    }

    @GET
    @NoCache
    @Path("/aes")
    @Produces("application/json")
    public StreamingOutput listAETs() throws Exception {
        final ApplicationEntityInfo[] aetInfos = conf.listAETInfos(new ApplicationEntityInfoBuilder(uriInfo).aetInfo);
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                for (ApplicationEntityInfo aetInfo : aetInfos)
                    jsonConf.writeTo(aetInfo, gen);
                gen.writeEnd();
                gen.flush();
            }
        };
    }

    @GET
    @NoCache
    @Path("/unique/aets")
    @Produces("application/json")
    public StreamingOutput listRegisteredAETS() throws Exception {
        String[] registeredAETs = conf.listRegisteredAETitles();
        return writeJsonArray(registeredAETs);
    }

    @GET
    @NoCache
    @Path("/unique/hl7apps")
    @Produces("application/json")
    public StreamingOutput listRegisteredHL7Apps() throws Exception {
        HL7Configuration hl7Conf = conf.getDicomConfigurationExtension(HL7Configuration.class);
        String[] registeredHL7Apps = hl7Conf.listRegisteredHL7ApplicationNames();
        return writeJsonArray(registeredHL7Apps);
    }

    private StreamingOutput writeJsonArray(String[] values) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                for (String value : values)
                    gen.write(value);
                gen.writeEnd();
                gen.flush();
            }
        };
    }

    @POST
    @Path("/devices/{DeviceName}")
    @Consumes("application/json")
    public void createDevice(@PathParam("DeviceName") String deviceName, Reader content) throws Exception {
        Device device = jsonConf.loadDeviceFrom(Json.createParser(content), configDelegate);
        if (!device.getDeviceName().equals(deviceName))
            throw new WebApplicationException(
                    "Device name in content[" + device.getDeviceName() + "] does not match Device name in URL",
                    Response.Status.BAD_REQUEST);
        conf.persist(device);
    }

    @PUT
    @Path("/devices/{DeviceName}")
    @Consumes("application/json")
    public void updateDevice(@PathParam("DeviceName") String deviceName, Reader content) throws Exception {
        Device device = jsonConf.loadDeviceFrom(Json.createParser(content), configDelegate);
        if (!device.getDeviceName().equals(deviceName))
            throw new WebApplicationException(
                    "Device name in content[" + device.getDeviceName() + "] does not match Device name in URL",
                    Response.Status.BAD_REQUEST);
        conf.merge(device);
    }

    @POST
    @Path("/unique/aets/{aet}")
    @Consumes("application/json")
    public void registerAET(@PathParam("aet") String aet) throws Exception {
        try {
            if (!conf.registerAETitle(aet))
                throw new WebApplicationException(
                        "Application Entity Title " + aet + " already registered.", Response.Status.CONFLICT);
        } catch (ConfigurationException e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @DELETE
    @Path("/unique/aets/{aet}")
    public void unregisterAET(@PathParam("aet") String aet) throws Exception {
        List<String> aets = Arrays.asList(conf.listRegisteredAETitles());
        if (!aets.contains(aet))
            throw new WebApplicationException(
                    "Application Entity Title " + aet + " not registered.", Response.Status.NOT_FOUND);
        try {
            conf.unregisterAETitle(aet);
        } catch (ConfigurationException e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/unique/hl7apps/{appName}")
    @Consumes("application/json")
    public void registerHL7App(@PathParam("appName") String appName) throws Exception {
        HL7Configuration hl7Conf = conf.getDicomConfigurationExtension(HL7Configuration.class);
        try {
            if (!hl7Conf.registerHL7Application(appName))
                throw new WebApplicationException(
                        "HL7 Application " + appName + " already registered.", Response.Status.CONFLICT);
        } catch (ConfigurationException e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @DELETE
    @Path("/unique/hl7apps/{appName}")
    public void unregisterHL7App(@PathParam("appName") String appName) throws Exception {
        HL7Configuration hl7Conf = conf.getDicomConfigurationExtension(HL7Configuration.class);
        List<String> hl7apps = Arrays.asList(hl7Conf.listRegisteredHL7ApplicationNames());
        if (!hl7apps.contains(appName))
            throw new WebApplicationException(
                    "HL7 Application " + appName + " not registered.", Response.Status.NOT_FOUND);
        try {
            hl7Conf.unregisterHL7Application(appName);
        } catch (ConfigurationException e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @DELETE
    @Path("/devices/{DeviceName}")
    public void deleteDevice(@PathParam("DeviceName") String deviceName) throws Exception {
        conf.removeDevice(deviceName);
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
                        aetInfo.setAeTitle(toString(entry));
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

}
