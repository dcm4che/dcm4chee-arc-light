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
 * **** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.impl;

import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.AssociationHandler;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.service.HL7Service;
import org.dcm4che3.net.hl7.service.HL7ServiceRegistry;
import org.dcm4che3.net.pdu.AAssociateAC;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.UserIdentityAC;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomService;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4chee.arc.*;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.Patient;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@Singleton
@Startup
public class ArchiveServiceImpl implements ArchiveService {

    @Inject
    private ArchiveDeviceProducer deviceProducer;

    @Inject
    private Instance<DicomService> dicomServices;

    @Inject
    private Instance<HL7Service> hl7Services;

    @Inject
    private Instance<Scheduler> schedulers;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private IHL7ApplicationCache hl7AppCache;

    @Inject
    private LeadingCFindSCPQueryCache leadingCFindSCPQueryCache;

    @Inject
    private MergeMWLCache mergeMWLCache;

    @Inject
    private StorePermissionCache storePermissionCache;

    @Inject
    private Device device;

    @Resource
    private ManagedExecutorService executor;

    @Resource
    private ManagedScheduledExecutorService scheduledExecutor;

    @Inject
    private AssociationHandler associationHandler;

    @Inject
    private Event<ArchiveServiceEvent> archiveServiceEvent;

    @Inject
    private ConnectionEventSource connectionEventSource;

    private Status status = Status.STOPPED;

    private final DicomService echoscp = new BasicCEchoSCP();

    private final DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();

    private final HL7ServiceRegistry hl7ServiceRegistry = new HL7ServiceRegistry();

    @PostConstruct
    public void init() {
        try {
            device.setConnectionMonitor(connectionEventSource);
            device.setExecutor(executor);
            device.setScheduledExecutor(scheduledExecutor);
            device.setAssociationHandler(associationHandler);
            serviceRegistry.addDicomService(echoscp);
            for (DicomService service : dicomServices) {
                serviceRegistry.addDicomService(service);
            }
            for (HL7Service service : hl7Services) {
                hl7ServiceRegistry.addHL7Service(service);
            }
            device.setDimseRQHandler(serviceRegistry);
            HL7DeviceExtension hl7Extension = device.getDeviceExtension(HL7DeviceExtension.class);
            if (hl7Extension != null) {
                hl7Extension.setHL7MessageListener(hl7ServiceRegistry);
            }
            configure();
            start(null);
        } catch (RuntimeException re) {
            destroy();
            throw re;
        } catch (Exception e) {
            destroy();
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public void destroy() {
        stop(null);

        serviceRegistry.removeDicomService(echoscp);
        for (DicomService service : dicomServices) {
            serviceRegistry.removeDicomService(service);
        }
        for (HL7Service service : hl7Services) {
            hl7ServiceRegistry.removeHL7Service(service);
        }
    }

    @Override
    public void start(HttpServletRequest request) throws Exception {
        for (Scheduler scheduler : schedulers) scheduler.start();
        device.bindConnections();
        status = Status.STARTED;
        archiveServiceEvent.fire(new ArchiveServiceEvent(ArchiveServiceEvent.Type.STARTED, request));
    }

    @Override
    public void stop(HttpServletRequest request) {
        for (Scheduler scheduler : schedulers) scheduler.stop();
        device.unbindConnections();
        status = Status.STOPPED;
        archiveServiceEvent.fire(new ArchiveServiceEvent(ArchiveServiceEvent.Type.STOPPED, request));
    }

    @Override
    public Status status(HttpServletRequest request) {
        return status;
    }

    @Override
    public void reload(HttpServletRequest request) throws Exception {
        deviceProducer.reloadConfiguration();
        for (Scheduler scheduler : schedulers) scheduler.reload();
        device.rebindConnections();
        aeCache.clear();
        hl7AppCache.clear();
        configure();
        archiveServiceEvent.fire(new ArchiveServiceEvent(ArchiveServiceEvent.Type.RELOADED, request));
    }

    private void configure() {
        ArchiveDeviceExtension arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        aeCache.setStaleTimeout(arcdev.getAECacheStaleTimeoutSeconds());
        hl7AppCache.setStaleTimeout(arcdev.getAECacheStaleTimeoutSeconds());
        leadingCFindSCPQueryCache.setStaleTimeout(
                arcdev.getLeadingCFindSCPQueryCacheStaleTimeoutSeconds() * 1000L);
        leadingCFindSCPQueryCache.setMaxSize(arcdev.getLeadingCFindSCPQueryCacheSize());
        mergeMWLCache.setStaleTimeout(
                arcdev.getMergeMWLCacheStaleTimeoutSeconds() * 1000L);
        mergeMWLCache.setMaxSize(arcdev.getMergeMWLCacheSize());
        storePermissionCache.setStaleTimeout(
                arcdev.getStorePermissionCacheStaleTimeoutSeconds() * 1000L);
        storePermissionCache.setMaxSize(arcdev.getStorePermissionCacheSize());
        Patient.setShowPatientInfo(arcdev.showPatientInfoInSystemLog());
    }

}
