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
 * Portions created by the Initial Developer are Copyright (C) 2015-2021
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

package org.dcm4chee.arc.pdq.hl7;

import jakarta.enterprise.event.Event;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Issuer;
import org.dcm4che3.hl7.ERRSegment;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.PDQServiceDescriptor;
import org.dcm4chee.arc.hl7.HL7Sender;
import org.dcm4chee.arc.hl7.SAXTransformer;
import org.dcm4chee.arc.pdq.AbstractPDQService;
import org.dcm4chee.arc.pdq.PDQServiceContext;
import org.dcm4chee.arc.pdq.PDQServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2021
 */
public class HL7PDQService extends AbstractPDQService {
    private static final Logger LOG = LoggerFactory.getLogger(HL7PDQService.class);
    private static final String HL7_ADT_2_DCM_XSL = "${jboss.server.temp.url}/dcm4chee-arc/hl7-adt2dcm.xsl";

    private final Device device;
    private final IHL7ApplicationCache hl7AppCache;
    private final HL7Sender hl7Sender;
    private final Event<PDQServiceContext> pdqEvent;
    private final String msh3456;

    public HL7PDQService(PDQServiceDescriptor descriptor, Device device, IHL7ApplicationCache hl7AppCache,
                         HL7Sender hl7Sender, Event<PDQServiceContext> pdqEvent) {
        super(descriptor);
        this.device = device;
        this.hl7AppCache = hl7AppCache;
        this.hl7Sender = hl7Sender;
        this.pdqEvent = pdqEvent;
        this.msh3456 = descriptor.getPDQServiceURI().getSchemeSpecificPart();
    }

    @Override
    public Attributes query(PDQServiceContext ctx) throws PDQServiceException {
        requireQueryEntity(Entity.Patient);
        String[] appFacility = msh3456.split(":");
        if (appFacility.length != 2)
            throw new PDQServiceException("Sending and/or Receiving application and facility not specified in URI of " + descriptor);

        HL7Application sender = sender(appFacility[0].replace('/', '|'));
        HL7Application receiver = receiver(appFacility[1].replace('/', '|'));
        ctx.setSendingAppFacility(sender.getApplicationName());
        ctx.setReceivingAppFacility(receiver.getApplicationName());
        Attributes demographics = query(ctx, sender, receiver);
        ctx.setPatientAttrs(demographics);
        pdqEvent.fire(ctx);
        return demographics;
    }

    private String xslStylesheetURI() {
        return descriptor.getProperties().getOrDefault("XSLStylesheetURI", HL7_ADT_2_DCM_XSL);
    }

    private HL7Application sender(String sendingAppFacility) throws PDQServiceException {
        HL7Application sender = device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(sendingAppFacility, true);
        if (sender == null)
            throw new PDQServiceException(
                    "Sending HL7 Application " + sendingAppFacility + " not configured; used in " + descriptor);

        return sender;
    }

    private HL7Application receiver(String receivingAppFacility) throws PDQServiceException {
        try {
            return hl7AppCache.findHL7Application(receivingAppFacility);
        } catch (ConfigurationException e) {
            throw new PDQServiceException(
                    "Receiving HL7 Application " + receivingAppFacility + " not configured; used in " + descriptor);
        }
    }

    public Attributes query(PDQServiceContext ctx, HL7Application sender, HL7Application receiver)
            throws PDQServiceException {
        try {
            String[] queryParams = queryParams(ctx.getPatientID());
            HL7Message qbp = HL7Message.makePdqQuery(queryParams);
            HL7Segment msh = qbp.get(0);
            msh.setSendingApplicationWithFacility(sender.getApplicationName());
            msh.setReceivingApplicationWithFacility(receiver.getApplicationName());
            msh.setField(17, sender.getHL7SendingCharacterSet());
            UnparsedHL7Message msg = new UnparsedHL7Message(qbp.getBytes(sender.getHL7SendingCharacterSet()));
            ctx.setHl7Msg(msg);
            ctx.setRsp(hl7Sender.sendMessage(sender, receiver, msg));
            return parseRsp(ctx.getRsp(), sender);
        } catch (Exception e) {
            throw new PDQServiceException(e);
        }
    }

    private Attributes parseRsp(UnparsedHL7Message rsp, HL7Application sender) throws HL7Exception {
        HL7Message hl7RspMsg = HL7Message.parse(rsp.data(), sender.getHL7DefaultCharacterSet());
        HL7Segment msa = hl7RspMsg.getSegment("MSA");
        if (msa == null)
            throw new HL7Exception(new ERRSegment(rsp.msh()).setUserMessage("Missing MSA segment in response message"));

        String status = msa.getField(1, null);
        if (!status.equals("AA")) {
            LOG.info("Unsuccessful Patient Demographics Query using descriptor {} : \n{}", descriptor, hl7RspMsg);
            return null;
        }

        ArchiveHL7ApplicationExtension arcHL7App =
                sender.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        try {
            return SAXTransformer.transform(
                    rsp,
                    arcHL7App,
                    xslStylesheetURI(),
                    null);
        } catch (Exception e) {
            throw new HL7Exception(new ERRSegment(rsp.msh()).setUserMessage(e.getMessage()), e);
        }
    }

    private String[] queryParams(IDWithIssuer pid) {
        List<String> queryParams = new ArrayList<>();
        queryParams.add("@PID.3.1^" + pid.getID());
        Issuer issuer = pid.getIssuer();
        if (issuer != null) {
            if (issuer.getLocalNamespaceEntityID() != null)
                queryParams.add("@PID.3.4.1^" + issuer.getLocalNamespaceEntityID());
            if (issuer.getUniversalEntityID() != null)
                queryParams.add("@PID.3.4.2^" + issuer.getUniversalEntityID());
            if (issuer.getUniversalEntityIDType() != null)
                queryParams.add("@PID.3.4.3^" + issuer.getUniversalEntityIDType());
        }
        return queryParams.toArray(new String[0]);
    }
}
