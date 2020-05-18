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

package org.dcm4chee.arc.ups.impl;

import org.dcm4che3.data.*;
import org.dcm4che3.hl7.HL7Charset;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.hl7.HL7SAXTransformer;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.issuer.IssuerService;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Tuple;
import javax.xml.transform.TransformerConfigurationException;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2019
 */
@Stateless
public class UPSServiceEJB {

    private static Logger LOG = LoggerFactory.getLogger(UPSServiceEJB.class);

    @PersistenceContext(unitName = "dcm4chee-arc")
    EntityManager em;

    @Inject
    private PatientService patientService;

    @Inject
    private CodeCache codeCache;

    @Inject
    private IssuerService issuerService;

    public UPS createUPS(UPSContext ctx) {
        ArchiveDeviceExtension arcDev = ctx.getArchiveDeviceExtension();
        Attributes attrs = ctx.getAttributes();
        UPS ups = new UPS();
        ups.setUpsInstanceUID(ctx.getUPSInstanceUID());
        ups.setPatient(findOrCreatePatient(ctx));
        ups.setIssuerOfAdmissionID(
                findOrCreateIssuer(attrs, Tag.IssuerOfAdmissionIDSequence));
        ups.setScheduledWorkitemCode(
                findOrCreateCode(attrs, Tag.ScheduledWorkitemCodeSequence));
        ups.setScheduledStationNameCode(
                findOrCreateCode(attrs, Tag.ScheduledStationNameCodeSequence));
        ups.setScheduledStationClassCode(
                findOrCreateCode(attrs, Tag.ScheduledStationClassCodeSequence));
        ups.setScheduledStationGeographicLocationCode(
                findOrCreateCode(attrs, Tag.ScheduledStationGeographicLocationCodeSequence));
        setHumanPerformerCodes(ups.getHumanPerformerCodes(),
                attrs.getSequence(Tag.ScheduledHumanPerformersSequence));
        setReferencedRequests(ups.getReferencedRequests(),
                attrs.getSequence(Tag.ReferencedRequestSequence),
                arcDev.getFuzzyStr());
        ups.setAttributes(attrs, arcDev.getAttributeFilter(Entity.UPS));
        em.persist(ups);
        LOG.info("{}: Create {}", ctx, ups);
        for (GlobalSubscription globalSubscription : globalSubscriptions(attrs)) {
            createSubscription(ctx, ups, globalSubscription.getSubscriberAET(), globalSubscription.isDeletionLock());
        }
        List<String> subcribers = subscribersOf(ups);
        if (!subcribers.isEmpty()) {
            ctx.addUPSEvent(UPSEvent.Type.StateReport, ups.getUPSInstanceUID(), stateReportOf(attrs), subcribers);
            for (Attributes eventInformation : assigned(attrs,
                    attrs.containsValue(Tag.ScheduledStationNameCodeSequence),
                    attrs.containsValue(Tag.ScheduledHumanPerformersSequence))) {
                ctx.addUPSEvent(UPSEvent.Type.Assigned, ups.getUPSInstanceUID(), eventInformation, subcribers);
            }
        }
        return ups;
    }

    private Patient findOrCreatePatient(UPSContext ctx) {
        Patient pat = ctx.getPatient();
        if (pat == null) {
            PatientMgtContext patMgtCtx = ctx.getAssociation() != null
                    ? patientService.createPatientMgtContextDIMSE(ctx.getAssociation())
                    : patientService.createPatientMgtContextWEB(ctx.getHttpRequestInfo());
            patMgtCtx.setAttributes(ctx.getAttributes());
            pat = patientService.findPatient(patMgtCtx);
            if (pat == null) {
                pat = patientService.createPatient(patMgtCtx);
            }
        }
        return pat;
    }

    public UPS updateUPS(UPSContext ctx) throws DicomServiceException {
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        UPS ups = findUPS(ctx);
        String transactionUID = ctx.getAttributes().getString(Tag.TransactionUID);
        switch (ups.getProcedureStepState()) {
            case SCHEDULED:
                if (transactionUID != null)
                    throw new DicomServiceException(Status.UPSNotYetInProgress,
                            "The submitted request is inconsistent with the current state of the UPS Instance.", false);
                break;
            case IN_PROGRESS:
                if (transactionUID == null)
                    throw new DicomServiceException(Status.UPSTransactionUIDNotCorrect,
                            "The Transaction UID is missing.", false);
                if (!transactionUID.equals(ups.getTransactionUID()))
                    throw new DicomServiceException(Status.UPSTransactionUIDNotCorrect,
                            "The Transaction UID is incorrect.", false);
                break;
            case CANCELED:
            case COMPLETED:
                throw new DicomServiceException(Status.UPSMayNoLongerBeUpdated,
                        "The submitted request is inconsistent with the current state of the UPS Instance.", false);
        }
        AttributeFilter filter = arcDev.getAttributeFilter(Entity.UPS);
        Attributes modified = new Attributes();
        Attributes attrs = ups.getAttributes();
        boolean prevIssuerOfAdmissionID = attrs.containsValue(Tag.IssuerOfAdmissionIDSequence);
        boolean prevWorkitemCode = attrs.containsValue(Tag.ScheduledWorkitemCodeSequence);
        boolean prevStationName = attrs.containsValue(Tag.ScheduledStationNameCodeSequence);
        boolean prevStationClass = attrs.containsValue(Tag.ScheduledStationClassCodeSequence);
        boolean prevStationLocation =
                attrs.containsValue(Tag.ScheduledStationGeographicLocationCodeSequence);
        boolean prevPerformers = attrs.containsValue(Tag.ScheduledHumanPerformersSequence);
        boolean prevRequest = attrs.containsValue(Tag.ReferencedRequestSequence);
        boolean prevProgressInformation = attrs.containsValue(Tag.ProcedureStepProgressInformationSequence);
        if (!attrs.updateSelected(Attributes.UpdatePolicy.OVERWRITE, ctx.getAttributes(), modified,
                filter.getSelection())) {
            return ups;
        }
        boolean issuerOfAdmissionIDUpdated = (prevIssuerOfAdmissionID ? modified : attrs)
                .containsValue(Tag.IssuerOfAdmissionIDSequence);
        if (issuerOfAdmissionIDUpdated) {
            ups.setIssuerOfAdmissionID(
                    findOrCreateIssuer(attrs, Tag.IssuerOfAdmissionIDSequence));
        }
        boolean workitemCodeUpdated = (prevWorkitemCode ? modified : attrs)
                .containsValue(Tag.ScheduledWorkitemCodeSequence);
        if (workitemCodeUpdated) {
            ups.setScheduledWorkitemCode(
                    findOrCreateCode(attrs, Tag.ScheduledWorkitemCodeSequence));
        }
        boolean stationNameUpdated = (prevStationName ? modified : attrs)
                .containsValue(Tag.ScheduledStationNameCodeSequence);
        if (stationNameUpdated) {
            ups.setScheduledStationNameCode(
                    findOrCreateCode(attrs, Tag.ScheduledStationNameCodeSequence));
        }
        boolean stationClassUpdated = (prevStationClass ? modified : attrs)
                .containsValue(Tag.ScheduledStationClassCodeSequence);
        if (stationClassUpdated) {
            ups.setScheduledStationClassCode(
                    findOrCreateCode(attrs, Tag.ScheduledStationClassCodeSequence));
        }
        boolean stationLocationUpdated = (prevStationLocation ? modified : attrs)
                .containsValue(Tag.ScheduledStationGeographicLocationCodeSequence);
        if (stationLocationUpdated) {
            ups.setScheduledStationGeographicLocationCode(
                    findOrCreateCode(attrs, Tag.ScheduledStationGeographicLocationCodeSequence));
        }
        boolean performerUpdated = (prevPerformers ? modified : attrs)
                .containsValue(Tag.ScheduledHumanPerformersSequence);
        if (performerUpdated) {
            setHumanPerformerCodes(ups.getHumanPerformerCodes(),
                    attrs.getSequence(Tag.ScheduledHumanPerformersSequence));
        }
        boolean requestUpdated = (prevRequest ? modified : attrs)
                .containsValue(Tag.ReferencedRequestSequence);
        if (requestUpdated) {
            setReferencedRequests(ups.getReferencedRequests(),
                    attrs.getSequence(Tag.ReferencedRequestSequence),
                    arcDev.getFuzzyStr());
        }
        ups.setAttributes(attrs, filter);
        LOG.info("{}: Update {}", ctx, ups);
        List<String> subcribers = subscribersOf(ups);
        if (subcribers.isEmpty()) {
            return ups;
        }
        if (modified.contains(Tag.InputReadinessState)) {
            ctx.addUPSEvent(UPSEvent.Type.StateReport, ups.getUPSInstanceUID(), stateReportOf(attrs), subcribers);
        }
        boolean progressInformationUpdated = (prevProgressInformation ? modified : attrs)
                .containsValue(Tag.ProcedureStepProgressInformationSequence);
        if (progressInformationUpdated) {
            ctx.addUPSEvent(UPSEvent.Type.ProgressReport, ups.getUPSInstanceUID(), progressReportOf(attrs), subcribers);
        }
        for (Attributes eventInformation : assigned(attrs, stationNameUpdated, performerUpdated)) {
            ctx.addUPSEvent(UPSEvent.Type.Assigned, ups.getUPSInstanceUID(), eventInformation, subcribers);
        }
        return ups;
    }

    public UPS findUPS(UPSContext ctx) throws DicomServiceException {
        try {
            return findUPS(ctx.getUPSInstanceUID());
        } catch (NoResultException e) {
            throw new DicomServiceException(Status.UPSDoesNotExist,
                    "Specified SOP Instance UID does not exist", false);
        }
    }

    public boolean exists(UPSContext ctx) {
        return !em.createNamedQuery(UPS.FIND_BY_IUID)
                .setParameter(1, ctx.getUPSInstanceUID())
                .getResultList().isEmpty();
    }

    private UPS findUPS(String upsInstanceUID) {
        return em.createNamedQuery(UPS.FIND_BY_IUID_EAGER, UPS.class)
                .setParameter(1, upsInstanceUID)
                .getSingleResult();
    }

    private void setReferencedRequests(Collection<UPSRequest> referencedRequests,
            Sequence seq, FuzzyStr fuzzyStr) {
        referencedRequests.clear();
        if (seq != null) {
            for (Attributes item : seq) {
                referencedRequests.add(new UPSRequest(
                        item,
                        findOrCreateIssuer(item, Tag.IssuerOfAccessionNumberSequence),
                        fuzzyStr));
            }
        }
    }

    private IssuerEntity findOrCreateIssuer(Attributes attrs, int tag) {
        Issuer issuer = Issuer.valueOf(attrs.getNestedDataset(tag));
        return issuer != null ? issuerService.mergeOrCreate(issuer) : null;
    }

    private CodeEntity findOrCreateCode(Attributes attrs, int seqTag) {
        Attributes item = attrs.getNestedDataset(seqTag);
        if (item != null)
            try {
                return codeCache.findOrCreate(new Code(item));
            } catch (Exception e) {
                LOG.info("Illegal code item in Sequence {}:\n{}", TagUtils.toString(seqTag), item);
            }
        return null;
    }

    private void setHumanPerformerCodes(Collection<CodeEntity> codes, Sequence seq) {
        codes.clear();
        if (seq != null) {
            for (Attributes item : seq) {
                try {
                    codes.add(codeCache.findOrCreate(
                            new Code(item.getNestedDataset(Tag.HumanPerformerCodeSequence))));
                } catch (Exception e) {
                    LOG.info("Missing or invalid Human Performer Code:\n{}", item);
                }
            }
        }
    }

    public UPS changeUPSState(UPSContext ctx, UPSState upsState, String transactionUID)
            throws DicomServiceException {
        UPS ups = findUPS(ctx);
        Attributes attrs = ups.getAttributes();
        if (ctx.getMergeAttributes() != null)
            attrs.addAll(ctx.getMergeAttributes());
        switch (upsState) {
            case IN_PROGRESS:
                switch (ups.getProcedureStepState()) {
                    case IN_PROGRESS:
                        throw new DicomServiceException(Status.UPSAlreadyInProgress,
                                "The submitted request is inconsistent with the current state of the UPS Instance.",
                                false);
                    case CANCELED:
                    case COMPLETED:
                        throw new DicomServiceException(Status.UPSMayNoLongerBeUpdated,
                                "The submitted request is inconsistent with the current state of the UPS Instance.",
                                false);
                }
                ups.setTransactionUID(transactionUID);
                ups.setPerformerAET(ctx.getRequesterAET());
                break;
            case CANCELED:
                switch (ups.getProcedureStepState()) {
                    case SCHEDULED:
                        throw new DicomServiceException(Status.UPSNotYetInProgress,
                                "The submitted request is inconsistent with the current state of the UPS Instance.",
                                false);
                    case CANCELED:
                        ctx.setStatus(Status.UPSAlreadyInRequestedStateOfCanceled);
                        return ups;
                    case COMPLETED:
                        throw new DicomServiceException(Status.UPSMayNoLongerBeUpdated,
                                "The submitted request is inconsistent with the current state of the UPS Instance.",
                                false);
                }
                if (!transactionUID.equals(ups.getTransactionUID()))
                    throw new DicomServiceException(Status.UPSTransactionUIDNotCorrect,
                            "The Transaction UID is incorrect.", false);
                supplementDiscontinuationReasonCode(ensureProgressInformation(attrs));
                ups.setTransactionUID(null);
                break;
           case COMPLETED:
               switch (ups.getProcedureStepState()) {
                   case SCHEDULED:
                       throw new DicomServiceException(Status.UPSNotYetInProgress,
                               "The submitted request is inconsistent with the current state of the UPS Instance.",
                               false);
                   case CANCELED:
                       throw new DicomServiceException(Status.UPSMayNoLongerBeUpdated,
                               "The submitted request is inconsistent with the current state of the UPS Instance.",
                               false);
                   case COMPLETED:
                       ctx.setStatus(Status.UPSAlreadyInRequestedStateOfCompleted);
                       return ups;
               }
               if (!transactionUID.equals(ups.getTransactionUID()))
                   throw new DicomServiceException(Status.UPSTransactionUIDNotCorrect,
                           "The Transaction UID is incorrect.", false);
               if (!meetFinalStateRequirementsOfCompleted(attrs))
                   throw new DicomServiceException(Status.UPSNotMetFinalStateRequirements,
                           "The submitted request is inconsistent with the current state of the UPS Instance.", false);
               ups.setTransactionUID(null);
               break;
        }
        attrs.setString(Tag.ProcedureStepState, VR.CS, upsState.toString());
        List<String> subcribers = subscribersOf(ups);
        if (!subcribers.isEmpty()) {
            ctx.addUPSEvent(UPSEvent.Type.StateReport, ctx.getUPSInstanceUID(), stateReportOf(attrs), subcribers);
        }
        ups.setAttributes(attrs, ctx.getArchiveDeviceExtension().getAttributeFilter(Entity.UPS));
        LOG.info("{}: Update {}", ctx, ups);
        return ups;
    }

    public UPS requestUPSCancel(UPSContext ctx, UPSServiceImpl upsService) throws DicomServiceException {
        UPS ups = findUPS(ctx);
        switch (ups.getProcedureStepState()) {
            case IN_PROGRESS:
                addCancelRequestedEvent(ctx, ups, upsService);
                return ups;
            case CANCELED:
                ctx.setStatus(Status.UPSAlreadyInRequestedStateOfCanceled);
                return ups;
            case COMPLETED:
                throw new DicomServiceException(Status.UPSAlreadyCompleted,
                        "The UPS is already COMPLETED.");
        }
        cancelUPS(ctx, ups);
        return ups;
    }

    private void addCancelRequestedEvent(UPSContext ctx, UPS ups, UPSServiceImpl upsService)
            throws DicomServiceException {
        List<String> subcribers = subscribersOf(ups);
        Predicate<String> isUPSEventSCU = ctx.getArchiveAEExtension()::isUPSEventSCU;
        subcribers.removeIf(isUPSEventSCU.or(upsService::websocketChannelsExists).negate());
        if (!subcribers.contains(ups.getPerformerAET())) {
            throw new DicomServiceException(Status.UPSPerformerCannotBeContacted,
                    "The performer cannot be contacted");
        }
        ctx.addUPSEvent(UPSEvent.Type.CancelRequested, ctx.getUPSInstanceUID(), cancelRequestedBy(ctx), subcribers);
    }

    private void cancelUPS(UPSContext ctx, UPS ups) {
        Attributes attrs = ups.getAttributes();
        Attributes progressInformation = ensureProgressInformation(attrs);
        progressInformation.addSelected(ctx.getAttributes(),
                Tag.ProcedureStepDiscontinuationReasonCodeSequence,
                Tag.ReasonForCancellation);
        Attributes communicationsURI = new Attributes(ctx.getAttributes(),
                Tag.ContactURI,
                Tag.ContactDisplayName);
        if (!communicationsURI.isEmpty()) {
            progressInformation.ensureSequence(Tag.ProcedureStepCommunicationsURISequence, 1)
                    .add(communicationsURI);
        }
        supplementDiscontinuationReasonCode(progressInformation);
        attrs.setString(Tag.ProcedureStepState, VR.CS, "CANCELED");
        List<String> subcribers = subscribersOf(ups);
        if (!subcribers.isEmpty()) {
            ctx.addUPSEvent(UPSEvent.Type.StateReportInProcessAndCanceled,
                    ctx.getUPSInstanceUID(),
                    stateReportOf(attrs),
                    subcribers);
        }
        ups.setAttributes(attrs, ctx.getArchiveDeviceExtension().getAttributeFilter(Entity.UPS));
        LOG.info("{}: Update {}", ctx, ups);
    }

    private static Attributes cancelRequestedBy(UPSContext ctx) {
        Attributes eventInformation = new Attributes(ctx.getAttributes(),
                Tag.ContactURI,
                Tag.ContactDisplayName,
                Tag.ProcedureStepDiscontinuationReasonCodeSequence,
                Tag.ReasonForCancellation);
        eventInformation.setString(Tag.RequestingAE, VR.AE, ctx.getRequesterAET());
        return eventInformation;
    }

    private static Attributes stateReportOf(Attributes attrs) {
        Attributes eventInformation = new Attributes(3);
        eventInformation.setString(Tag.InputReadinessState, VR.CS, attrs.getString(Tag.InputReadinessState));
        String state = attrs.getString(Tag.ProcedureStepState);
        eventInformation.setString(Tag.ProcedureStepState, VR.CS, state);
        if ("CANCELED".equals(state)) {
            Attributes item = attrs.getNestedDataset(Tag.ProcedureStepProgressInformationSequence);
            if (item != null) {
                eventInformation.addSelected(item,
                        Tag.ProcedureStepDiscontinuationReasonCodeSequence,
                        Tag.ReasonForCancellation);
            }
        }
        return eventInformation;
    }

    private static Attributes progressReportOf(Attributes attrs) {
        return new Attributes(attrs, Tag.ProcedureStepProgressInformationSequence);
    }

    private static List<Attributes> assigned(Attributes attrs, boolean stationNameUpdated, boolean performerUpdated) {
        if (!performerUpdated) {
            return stationNameUpdated
                    ? Collections.singletonList(new Attributes(attrs, Tag.ScheduledStationNameCodeSequence))
                    : Collections.emptyList();
        }
        return attrs.getSequence(Tag.ScheduledHumanPerformersSequence)
                .stream()
                .map(item -> assignedOf(attrs, stationNameUpdated, item))
                .collect(Collectors.toList());
        }

    private static Attributes assignedOf(Attributes attrs, boolean stationNameUpdated, Attributes item) {
        Attributes eventInformation = new Attributes(3);
        eventInformation.addSelected(item, Tag.HumanPerformerCodeSequence, Tag.HumanPerformerOrganization);
        if (stationNameUpdated) {
            eventInformation.addSelected(attrs, Tag.ScheduledStationNameCodeSequence);
        }
        return item;
    }

    private List<String> subscribersOf(UPS ups) {
        return em.createNamedQuery(Subscription.AETS_BY_UPS, String.class)
                .setParameter(1, ups)
                .getResultList();
    }

    public List<UPSEvent> statusChangeEvents(ArchiveAEExtension arcAE, Attributes eventInformation) {
        List<UPSEvent> list = em.createNamedQuery(Subscription.ALL_IUID_AND_AET, Tuple.class)
                .getResultStream()
                .collect(Collectors.groupingBy(
                        tuple -> tuple.get(0, String.class),
                        Collectors.mapping(
                                tuple -> tuple.get(1, String.class),
                                Collectors.toList())))
                .entrySet().stream()
                .map(entry -> new UPSEvent(
                        arcAE, UPSEvent.Type.StatusChange, entry.getKey(), eventInformation, entry.getValue()))
                .collect(Collectors.toList());
        addStatusChangeEvents(list, arcAE, eventInformation,
                UID.UPSGlobalSubscriptionSOPInstance, GlobalSubscription.GLOBAL_AETS);
        addStatusChangeEvents(list, arcAE, eventInformation,
                UID.UPSFilteredGlobalSubscriptionSOPInstance, GlobalSubscription.FILTERED_AETS);
        return list;
    }

    private void addStatusChangeEvents(List<UPSEvent> list, ArchiveAEExtension arcAE, Attributes eventInformation,
            String iuid, String queryName) {
        List<String> subscriberAETs = em.createNamedQuery(queryName, String.class).getResultList();
        if (!subscriberAETs.isEmpty()) {
            list.add(new UPSEvent(arcAE, UPSEvent.Type.StatusChange, iuid, eventInformation, subscriberAETs));
        }
    }

    public Subscription createOrUpdateSubscription(UPSContext ctx) throws DicomServiceException {
        UPS ups = findUPS(ctx);
        Subscription sub;
        try {
            sub = updateSubscription(ctx, ups);
        } catch (NoResultException e) {
            sub = createSubscription(ctx, ups, ctx.getSubscriberAET(), ctx.isDeletionLock());
        }
        addInitialEvent(ctx, ups, ctx.getSubscriberAET());
        return sub;
    }

    public int deleteSubscription(UPSContext ctx) {
        if (em.createNamedQuery(Subscription.DELETE_BY_IUID_AND_AET)
                .setParameter(1, ctx.getUPSInstanceUID())
                .setParameter(2, ctx.getSubscriberAET())
                .executeUpdate() > 0) {
            LOG.info("{}: Delete Subscription[uid={}, aet={}]", ctx, ctx.getUPSInstanceUID(), ctx.getSubscriberAET());
            return 1;
        }
        return 0;
    }

    public List<GlobalSubscription> findGlobalSubscriptions() {
        return em.createNamedQuery(GlobalSubscription.FIND_ALL_EAGER, GlobalSubscription.class).getResultList();
    }

    public GlobalSubscription createOrUpdateGlobalSubscription(UPSContext ctx, List<Attributes> notSubscribedUPS) {
        GlobalSubscription sub;
        try {
            sub = updateGlobalSubscription(ctx);
        } catch (NoResultException e) {
            sub = createGlobalSubscription(ctx);
        }
        for (Attributes attrs : notSubscribedUPS) {
            UPS ups = findUPS(attrs.getString(Tag.SOPInstanceUID));
            createSubscription(ctx, ups, ctx.getSubscriberAET(), ctx.isDeletionLock());
            if (ctx.isDeletionLock())
                addInitialEvent(ctx, ups, ctx.getSubscriberAET());
        }
        return sub;
    }

    public int suspendGlobalSubscription(UPSContext ctx) {
        try {
            GlobalSubscription sub = em.createNamedQuery(GlobalSubscription.FIND_BY_AET, GlobalSubscription.class)
                    .setParameter(1, ctx.getSubscriberAET())
                    .getSingleResult();
            em.remove(sub);
            LOG.info("{}: Delete {}", ctx, sub);
            return 1;
        } catch (NoResultException e) {
            return 0;
        }
    }

    public int deleteGlobalSubscription(UPSContext ctx) {
        suspendGlobalSubscription(ctx);
        int n = em.createNamedQuery(Subscription.DELETE_BY_AET)
                .setParameter(1, ctx.getSubscriberAET())
                .executeUpdate();
        if (n > 0) {
            LOG.info("{}: Delete {} Subscriptions[aet={}]", ctx, n, ctx.getSubscriberAET());
        }
        return n;
    }

    public boolean purgeUPSWithoutDeletionLock(ArchiveDeviceExtension arcdev) {
        int fetchSize = arcdev.getDeleteUPSFetchSize();
        List<UPS> list = findUPSWithoutDeletionLock(
                arcdev.getDeleteUPSCompletedDelay(),
                arcdev.getDeleteUPSCanceledDelay(),
                fetchSize);
        list.forEach(this::deleteUPS);
        return list.size() == fetchSize && arcdev.getDeleteUPSPollingInterval() != null;
    }

    private List<UPS> findUPSWithoutDeletionLock(Duration completedDelay, Duration canceledDelay, int fetchSize) {
        long now = System.currentTimeMillis();
        return em.createNamedQuery(UPS.FIND_WO_DELETION_LOCK, UPS.class)
                .setParameter(1, UPSState.COMPLETED)
                .setParameter(2, before(now, completedDelay))
                .setParameter(3, UPSState.CANCELED)
                .setParameter(4, before(now, canceledDelay))
                .setParameter(5, true)
                .setMaxResults(fetchSize)
                .getResultList();
    }

    private static Date before(long now, Duration delay) {
        return new Date(delay != null ? now - delay.getSeconds() * 1000L : now);
    }

    private void deleteUPS(UPS ups) {
        em.createNamedQuery(Subscription.DELETE_BY_UPS).setParameter(1, ups).executeUpdate();
        em.remove(ups);
        LOG.info("Delete {}", ups);
    }

    private Subscription updateSubscription(UPSContext ctx, UPS ups) {
        Subscription sub = em.createNamedQuery(Subscription.FIND_BY_UPS_AND_AET,
                Subscription.class)
                .setParameter(1, ups)
                .setParameter(2, ctx.getSubscriberAET())
                .getSingleResult();
        sub.setDeletionLock(ctx.isDeletionLock());
        LOG.info("{}: Update {}", ctx, sub);
        return sub;
    }

    private Subscription createSubscription(UPSContext ctx, UPS ups, String subscriberAET, boolean deletionLock) {
        Subscription sub = new Subscription();
        sub.setUPS(ups);
        sub.setSubscriberAET(subscriberAET);
        sub.setDeletionLock(deletionLock);
        em.persist(sub);
        LOG.info("{}: Create {}", ctx, sub);
        return sub;
    }

    private static void addInitialEvent(UPSContext ctx, UPS ups, String subscriberAET) {
        ctx.addUPSEvent(UPSEvent.Type.StateReport, ups.getUPSInstanceUID(),
                stateReportOf(ups.getAttributes()),
                Collections.singletonList(subscriberAET));
    }

    private GlobalSubscription updateGlobalSubscription(UPSContext ctx) {
        GlobalSubscription sub = em.createNamedQuery(GlobalSubscription.FIND_BY_AET,
                GlobalSubscription.class)
                .setParameter(1, ctx.getSubscriberAET())
                .getSingleResult();
        sub.setDeletionLock(ctx.isDeletionLock());
        sub.setMatchKeys(ctx.getAttributes());
        LOG.info("{}: Update {}", ctx, sub);
        return sub;
    }

    private GlobalSubscription createGlobalSubscription(UPSContext ctx) {
        GlobalSubscription sub = new GlobalSubscription();
        sub.setSubscriberAET(ctx.getSubscriberAET());
        sub.setDeletionLock(ctx.isDeletionLock());
        sub.setMatchKeys(ctx.getAttributes());
        em.persist(sub);
        LOG.info("{}: Create {}", ctx, sub);
        return sub;
    }

    private static boolean meetFinalStateRequirementsOfCompleted(Attributes attrs) {
        Attributes performedProcedure = attrs.getNestedDataset(Tag.UnifiedProcedureStepPerformedProcedureSequence);
        if (performedProcedure != null
                && performedProcedure.containsValue(Tag.PerformedProcedureStepStartDateTime)
                && performedProcedure.containsValue(Tag.PerformedProcedureStepEndDateTime)) {
            try {
                new Code(performedProcedure.getNestedDataset(Tag.PerformedStationNameCodeSequence));
                new Code(performedProcedure.getNestedDataset(Tag.PerformedWorkitemCodeSequence));
                return true;
            } catch (Exception e) {}
        }
        return false;
    }

    private static Attributes ensureProgressInformation(Attributes attrs) {
        Sequence sq = attrs.ensureSequence(Tag.ProcedureStepProgressInformationSequence, 1);
        if (!sq.isEmpty()) {
            return sq.get(0);
        }
        Attributes progressInformation = new Attributes();
        sq.add(progressInformation);
        return progressInformation;
    }

    private static void supplementDiscontinuationReasonCode(Attributes attrs) {
        if (!attrs.containsValue(Tag.ProcedureStepCancellationDateTime)) {
            attrs.setDate(Tag.ProcedureStepCancellationDateTime, VR.DT, new Date());
        }
        Attributes reasonCode =
                attrs.getNestedDataset(Tag.ProcedureStepDiscontinuationReasonCodeSequence);
        if (reasonCode == null || reasonCode.isEmpty()) {
            attrs.newSequence(Tag.ProcedureStepDiscontinuationReasonCodeSequence, 1)
                    .add(new Code(
                            "110513",
                            "DCM",
                            null,
                            "Discontinued for unspecified reason")
                            .toItem());
        }
    }

    public UPS createOrUpdateOnStore(StoreContext ctx, Calendar now, UPSOnStore rule) {
        LOG.info("{}: Apply {}", ctx.getStoreSession(), rule);
        String iuid = rule.getInstanceUID(ctx.getAttributes());
        try {
            UPS ups = findUPS(iuid);
            UPSOnStore.IncludeInputInformation includeInputInformation = rule.getIncludeInputInformation();
            switch (includeInputInformation) {
                case APPEND:
                    if (ups.getProcedureStepState() == UPSState.SCHEDULED) break;
                case SINGLE:
                case NO:
                    LOG.info("{}: {} already exists", ctx.getStoreSession(), ups);
                    return null;
                default:
                    while (includeInputInformation == UPSOnStore.IncludeInputInformation.SINGLE_OR_CREATE
                        || ups.getProcedureStepState() != UPSState.SCHEDULED) {
                        ups = findUPS(iuid = UIDUtils.createNameBasedUID(iuid.getBytes()));
                    }
            }
            LOG.info("{}: update existing {}", ctx.getStoreSession(), ups);
            updateIncludeInputInformation(ups.getAttributes().getSequence(Tag.InputInformationSequence), ctx);
            ups.setAttributes(ups.getAttributes(),
                    ctx.getStoreSession().getArchiveDeviceExtension().getAttributeFilter(Entity.UPS));
            return ups;
        } catch (NoResultException e) {
            return createOnStore(iuid, ctx, now, rule);
        }
    }

    private UPS createOnStore(String iuid, StoreContext storeCtx, Calendar now, UPSOnStore rule) {
        UPSContext ctx = new UPSContextImpl(storeCtx);
        ctx.setUPSInstanceUID(iuid);
        ctx.setAttributes(createOnStore(storeCtx, now, rule));
        UPS ups = createUPS(ctx);
        LOG.info("{}: create {}", storeCtx.getStoreSession(), ups);
        return ups;
    }

    private static Attributes createOnStore(StoreContext storeCtx, Calendar now, UPSOnStore rule) {
        Attributes attrs = applyXSLT(rule, storeCtx);
        if (rule.isIncludeStudyInstanceUID() && !attrs.contains(Tag.StudyInstanceUID)) {
            attrs.setString(Tag.StudyInstanceUID, VR.UI, storeCtx.getStudyInstanceUID());
        }
        if (!attrs.contains(Tag.AdmissionID)) {
            attrs.setString(Tag.AdmissionID, VR.LO, rule.getAdmissionID(storeCtx.getAttributes()));
            setIssuer(attrs, Tag.IssuerOfAdmissionID, rule.getIssuerOfAdmissionID());
        }
        if (!attrs.contains(Tag.ScheduledProcedureStepStartDateTime)) {
            attrs.setDate(Tag.ScheduledProcedureStepStartDateTime, VR.DT, add(now, rule.getStartDateTimeDelay()));
        }
        if (rule.getCompletionDateTimeDelay() != null && !attrs.contains(Tag.ExpectedCompletionDateTime)) {
            attrs.setDate(Tag.ExpectedCompletionDateTime, VR.DT, add(now, rule.getCompletionDateTimeDelay()));
        }
        if (rule.getScheduledHumanPerformer() != null && !attrs.contains(Tag.ScheduledHumanPerformersSequence)) {
            attrs.newSequence(Tag.ScheduledHumanPerformersSequence, 1)
                    .add(rule.getScheduledHumanPerformerItem(storeCtx.getAttributes()));
        }
        if (!attrs.contains(Tag.ScheduledWorkitemCodeSequence)) {
            setCode(attrs, Tag.ScheduledWorkitemCodeSequence, rule.getScheduledWorkitemCode());
        }
        if (!attrs.contains(Tag.ScheduledStationNameCodeSequence)) {
            setCode(attrs, Tag.ScheduledStationNameCodeSequence, rule.getScheduledStationName());
        }
        if (!attrs.contains(Tag.ScheduledStationClassCodeSequence)) {
            setCode(attrs, Tag.ScheduledStationClassCodeSequence, rule.getScheduledStationClass());
        }
        if (!attrs.contains(Tag.ScheduledStationGeographicLocationCodeSequence)) {
            setCode(attrs, Tag.ScheduledStationGeographicLocationCodeSequence, rule.getScheduledStationLocation());
        }
        if (!attrs.contains(Tag.InputReadinessState)) {
            attrs.setString(Tag.InputReadinessState, VR.CS, rule.getInputReadinessState().toString());
        }
        if (!attrs.contains(Tag.ReferencedRequestSequence)) {
            if (rule.isIncludeReferencedRequest()) {
                attrs.newSequence(Tag.ReferencedRequestSequence, 1).add(referencedRequest(storeCtx, rule));
            } else {
                attrs.setNull(Tag.ReferencedRequestSequence, VR.SQ);
            }
        }
        attrs.setString(Tag.ProcedureStepState, VR.CS, "SCHEDULED");
        if (!attrs.contains(Tag.ScheduledProcedureStepPriority)) {
            attrs.setString(Tag.ScheduledProcedureStepPriority, VR.CS, rule.getUPSPriority().toString());
        }
        if (!attrs.contains(Tag.WorklistLabel)) {
            attrs.setString(Tag.WorklistLabel, VR.LO, worklistLabel(storeCtx, rule));
        }
        if (!attrs.contains(Tag.ProcedureStepLabel)) {
            attrs.setString(Tag.ProcedureStepLabel, VR.LO, rule.getProcedureStepLabel(storeCtx.getAttributes()));
        }
        if (rule.getIncludeInputInformation() != UPSOnStore.IncludeInputInformation.NO
                && !attrs.contains(Tag.InputInformationSequence)) {
            updateIncludeInputInformation(attrs.newSequence(Tag.InputInformationSequence, 1), storeCtx);
        }
        return attrs;
    }

    private static Attributes referencedRequest(StoreContext storeCtx, UPSOnStore rule) {
        Attributes item = new Attributes(8);
        item.setString(Tag.AccessionNumber, VR.SH, rule.getAccessionNumber(storeCtx.getAttributes()));
        setIssuer(item, Tag.IssuerOfAccessionNumberSequence, rule.getIssuerOfAccessionNumber());
        item.setString(Tag.StudyInstanceUID, VR.UI, storeCtx.getStudyInstanceUID());
        setNotNull(item, Tag.RequestingPhysician, VR.PN, rule.getRequestingPhysician(storeCtx.getAttributes()));
        setNotNull(item, Tag.RequestingService, VR.LO, rule.getRequestingService(storeCtx.getAttributes()));
        item.setString(Tag.RequestedProcedureDescription, VR.LO,
                rule.getRequestedProcedureDescription(storeCtx.getAttributes()));
        item.setNull(Tag.RequestedProcedureCodeSequence, VR.SQ);
        item.setString(Tag.RequestedProcedureID, VR.SH, rule.getRequestedProcedureID(storeCtx.getAttributes()));
        return item;
    }

    public void createOnHL7(
            Socket socket, ArchiveHL7ApplicationExtension arcHL7App, UnparsedHL7Message msg, HL7Fields hl7Fields,
            Calendar now, UPSOnHL7 upsOnHL7) {
        LOG.info("{}: Apply {}", socket, upsOnHL7);
        String iuid = upsOnHL7.getInstanceUID(hl7Fields);
        try {
            UPS ups = findUPS(iuid);
            LOG.info("UPS {} exists, return", ups);
        } catch (NoResultException e) {
            createOnHL7(socket, arcHL7App, msg, hl7Fields, now, upsOnHL7, iuid);
        }
    }

    private void createOnHL7(
            Socket socket, ArchiveHL7ApplicationExtension arcHL7App, UnparsedHL7Message msg, HL7Fields hl7Fields,
            Calendar now, UPSOnHL7 upsOnHL7, String iuid) {
        Attributes attrs = applyXSLT(arcHL7App, msg, upsOnHL7);
        if (attrs.size() == 0)
            return;

        UPSContext ctx = new UPSContextImpl(socket, arcHL7App);
        ctx.setUPSInstanceUID(iuid);
        ctx.setAttributes(createOnHL7(arcHL7App, attrs, hl7Fields, now, upsOnHL7));
        UPS ups = createUPS(ctx);
        LOG.info("{}: Create {}", socket, ups);
    }

    private static Attributes createOnHL7(
            ArchiveHL7ApplicationExtension arcHL7App, Attributes attrs, HL7Fields hl7Fields,
            Calendar now, UPSOnHL7 upsOnHL7) {
        if (!attrs.contains(Tag.ScheduledProcedureStepStartDateTime))
            attrs.setDate(Tag.ScheduledProcedureStepStartDateTime, VR.DT, spsStartDateTime(now, upsOnHL7, attrs));
        if (upsOnHL7.getCompletionDateTimeDelay() != null)
            attrs.setDate(Tag.ExpectedCompletionDateTime, VR.DT, add(now, upsOnHL7.getCompletionDateTimeDelay()));
        if (upsOnHL7.getScheduledHumanPerformer() != null)
            attrs.newSequence(Tag.ScheduledHumanPerformersSequence, 1)
                    .add(upsOnHL7.getScheduledHumanPerformerItem(hl7Fields));
        if (!attrs.contains(Tag.ScheduledWorkitemCodeSequence))
            setCode(attrs, Tag.ScheduledWorkitemCodeSequence, upsOnHL7.getScheduledWorkitemCode());
        if (!attrs.contains(Tag.ScheduledStationNameCodeSequence))
            setCode(attrs, Tag.ScheduledStationNameCodeSequence, upsOnHL7.getScheduledStationName());
        if (!attrs.contains(Tag.ScheduledStationClassCodeSequence))
            setCode(attrs, Tag.ScheduledStationClassCodeSequence, upsOnHL7.getScheduledStationClass());
        if (!attrs.contains(Tag.ScheduledStationGeographicLocationCodeSequence))
            setCode(attrs, Tag.ScheduledStationGeographicLocationCodeSequence, upsOnHL7.getScheduledStationLocation());
        if (!attrs.contains(Tag.InputReadinessState))
            attrs.setString(Tag.InputReadinessState, VR.CS, upsOnHL7.getInputReadinessState().name());
        attrs.setString(Tag.ProcedureStepState, VR.CS, "SCHEDULED");
        if (!attrs.contains(Tag.ScheduledProcedureStepPriority))
            attrs.setString(Tag.ScheduledProcedureStepPriority, VR.CS, upsOnHL7.getUPSPriority().name());
        if (!attrs.containsValue(Tag.StudyInstanceUID)) {
            attrs.setNull(Tag.StudyInstanceUID, VR.UI);
            attrs.setNull(Tag.ReferencedRequestSequence, VR.SQ);
        }
        if (!attrs.contains(Tag.WorklistLabel))
            attrs.setString(Tag.WorklistLabel, VR.LO, worklistLabel(arcHL7App, hl7Fields, upsOnHL7));
        if (!attrs.contains(Tag.ProcedureStepLabel))
            attrs.setString(Tag.ProcedureStepLabel, VR.LO, upsOnHL7.getProcedureStepLabel(hl7Fields));
        return attrs;
    }

    private static void setNotNull(Attributes item, int tag, VR vr, String value) {
        if (value != null) {
            item.setString(tag, vr, value);
        }
    }

    private static void setIssuer(Attributes attrs, int sqtag, Issuer issuer) {
        if (issuer != null) {
            attrs.newSequence(sqtag, 1).add(issuer.toItem());
        } else {
            attrs.setNull(sqtag, VR.SQ);
        }
    }

    private static void setSequence(Attributes item, int sqTag, Attributes attrs) {
        Attributes sqItem = attrs.getNestedDataset(sqTag);
        if (sqItem != null) {
            item.newSequence(sqTag, 1).add(new Attributes(sqItem));
        } else {
            item.setNull(sqTag, VR.SQ);
        }
    }

    private static String worklistLabel(StoreContext storeCtx, UPSOnStore rule) {
        String worklistLabel = rule.getWorklistLabel(storeCtx.getAttributes());
        return worklistLabel != null ? worklistLabel
                : storeCtx.getStoreSession().getArchiveAEExtension().upsWorklistLabel();
    }

    private static String worklistLabel(ArchiveHL7ApplicationExtension arcHL7App, HL7Fields hl7Fields, UPSOnHL7 upsOnHL7) {
        String worklistLabel = upsOnHL7.getWorklistLabel(hl7Fields);
        return worklistLabel != null ? worklistLabel : arcHL7App.getHL7Application().getApplicationName();
    }

    private static Date spsStartDateTime(Calendar now, UPSOnHL7 upsOnHL7, Attributes attrs) {
        Date spsStartDateTime = attrs.getDate(Tag.ScheduledProcedureStepStartDateTime);
        return spsStartDateTime != null ? spsStartDateTime : add(now, upsOnHL7.getStartDateTimeDelay());
    }

    private static Date add(Calendar now, Duration delay) {
        return delay != null ? new Date(now.getTimeInMillis() + delay.getSeconds() * 1000) : now.getTime();
    }

    private static void setCode(Attributes attrs, int sqtag, Code code) {
        if (code != null) {
            attrs.newSequence(sqtag, 1).add(code.toItem());
        } else {
            attrs.setNull(sqtag, VR.SQ);
        }
    }

    private static Attributes applyXSLT(UPSOnStore upsOnStore, StoreContext ctx) {
        String uri = upsOnStore.getXSLTStylesheetURI();
        if (uri != null) {
            try {
                return SAXTransformer.transform(
                        ctx.getAttributes(),
                        TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(uri)),
                        false,
                        !upsOnStore.isNoKeywords(),
                        setupTransformer(ctx.getStoreSession()));
            } catch (SAXException e) {
                LOG.warn("{}: Failed to apply XSL: {}", ctx.getStoreSession(), uri, e);
            } catch (TransformerConfigurationException e) {
                LOG.warn("{}: Failed to compile XSL: {}", ctx.getStoreSession(), uri, e);
            }
        }
        return new Attributes();
    }

    private static Attributes applyXSLT(
            ArchiveHL7ApplicationExtension arcHL7App, UnparsedHL7Message msg, UPSOnHL7 upsOnHL7) {
        try {
            String hl7Charset = msg.msh().getField(17, arcHL7App.getHL7Application().getHL7DefaultCharacterSet());
            return HL7SAXTransformer.transform(
                    msg.data(),
                    hl7Charset,
                    arcHL7App.hl7DicomCharacterSet() != null
                            ? arcHL7App.hl7DicomCharacterSet()
                            : HL7Charset.toDicomCharacterSetCode(hl7Charset),
                    TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(upsOnHL7.getXSLTStylesheetURI())),
                    null);
        } catch (SAXException e) {
            LOG.warn("Failed to apply XSL: {}", upsOnHL7.getXSLTStylesheetURI(), e);
        } catch (TransformerConfigurationException e) {
            LOG.warn("Failed to compile XSL: {}", upsOnHL7.getXSLTStylesheetURI(), e);
        } catch (IOException e) {
            LOG.warn("Failed to parse HL7 Message{}: {}", msg, upsOnHL7.getXSLTStylesheetURI(), e);
        }
        return new Attributes();
    }

    private static SAXTransformer.SetupTransformer setupTransformer(StoreSession session) {
        return t -> {
            t.setParameter("LocalAET", session.getCalledAET());
            if (session.getCallingAET() != null)
                t.setParameter("RemoteAET", session.getCallingAET());
            if (session.getRemoteHostName() != null)
                t.setParameter("RemoteHost", session.getRemoteHostName());
        };
    }

    private List<GlobalSubscription> globalSubscriptions(Attributes attrs) {
        return findGlobalSubscriptions().stream()
                .filter(sub -> matches(attrs, sub.getMatchKeys()))
                .collect(Collectors.toList());
    }

    private static boolean matches(Attributes attrs, Attributes keys) {
        return keys == null || attrs.matches(keys, false, false);
    }

    private static void updateIncludeInputInformation(Sequence sq, StoreContext ctx) {
        refSOPSequence(sq, ctx).add(toSOPRef(ctx));
    }

    private static Attributes toSOPRef(StoreContext ctx) {
        Attributes item = new Attributes(2);
        item.setString(Tag.ReferencedSOPClassUID, VR.UI, ctx.getSopClassUID());
        item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, ctx.getSopInstanceUID());
        return item;
    }

    private static Sequence refSOPSequence(Sequence sq, StoreContext ctx) {
        for (Attributes item : sq) {
            if (ctx.getStudyInstanceUID().equals(item.getString(Tag.StudyInstanceUID))
                && ctx.getSeriesInstanceUID().equals(item.getString(Tag.SeriesInstanceUID))) {
                return item.getSequence(Tag.ReferencedSOPSequence);
            }
        }
        Attributes item = new Attributes(5);
        sq.add(item);
        Sequence refSOPSequence = item.newSequence(Tag.ReferencedSOPSequence, 10);
        item.setString(Tag.StudyInstanceUID, VR.UI, ctx.getStudyInstanceUID());
        item.setString(Tag.SeriesInstanceUID, VR.UI, ctx.getSeriesInstanceUID());
        item.setString(Tag.TypeOfInstances, VR.CS, "DICOM");
        item.newSequence(Tag.DICOMRetrievalSequence, 1).add(retrieveAETItem(ctx));
        return refSOPSequence;
    }

    private static Attributes retrieveAETItem(StoreContext ctx) {
        Attributes item = new Attributes(1);
        item.setString(Tag.RetrieveAETitle, VR.AE, ctx.getRetrieveAETs());
        return item;
    }
}
