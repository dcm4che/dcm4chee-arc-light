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
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.issuer.IssuerService;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Tuple;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
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

    public UPS createUPS(UPSContext ctx, List<GlobalSubscription> globalSubscriptions) {
        ArchiveDeviceExtension arcDev = ctx.getArchiveDeviceExtension();
        Attributes attrs = ctx.getAttributes();
        UPS ups = new UPS();
        ups.setUpsInstanceUID(ctx.getUpsInstanceUID());
        PatientMgtContext patMgtCtx = ctx.getAssociation() != null
                ? patientService.createPatientMgtContextDIMSE(ctx.getAssociation())
                : patientService.createPatientMgtContextWEB(ctx.getHttpRequestInfo());
        patMgtCtx.setAttributes(attrs);
        Patient pat = patientService.findPatient(patMgtCtx);
        if (pat == null) {
            pat = patientService.createPatient(patMgtCtx);
        }
        ups.setPatient(pat);
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
        for (GlobalSubscription globalSubscription : globalSubscriptions) {
            createSubscription(ctx, ups, globalSubscription.getSubscriberAET(), globalSubscription.isDeletionLock());
        }
        List<String> subcribers = subscribersOf(ups);
        if (!subcribers.isEmpty()) {
            ctx.addUPSEvent(UPSEvent.Type.StateReport, ups.getUpsInstanceUID(), stateReportOf(attrs), subcribers);
            for (Attributes eventInformation : assigned(attrs,
                    attrs.containsValue(Tag.ScheduledStationNameCodeSequence),
                    attrs.containsValue(Tag.ScheduledHumanPerformersSequence))) {
                ctx.addUPSEvent(UPSEvent.Type.Assigned, ups.getUpsInstanceUID(), eventInformation, subcribers);
            }
        }
        return ups;
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
            ctx.addUPSEvent(UPSEvent.Type.StateReport, ups.getUpsInstanceUID(), stateReportOf(attrs), subcribers);
        }
        boolean progressInformationUpdated = (prevProgressInformation ? modified : attrs)
                .containsValue(Tag.ProcedureStepProgressInformationSequence);
        if (progressInformationUpdated) {
            ctx.addUPSEvent(UPSEvent.Type.ProgressReport, ups.getUpsInstanceUID(), progressReportOf(attrs), subcribers);
        }
        for (Attributes eventInformation : assigned(attrs, stationNameUpdated, performerUpdated)) {
            ctx.addUPSEvent(UPSEvent.Type.Assigned, ups.getUpsInstanceUID(), eventInformation, subcribers);
        }
        return ups;
    }

    public UPS findUPS(UPSContext ctx) throws DicomServiceException {
        try {
            return findUPS(ctx.getUpsInstanceUID());
        } catch (NoResultException e) {
            throw new DicomServiceException(Status.UPSDoesNotExist,
                    "Specified SOP Instance UID does not exist", false);
        }
    }

    public boolean exists(UPSContext ctx) {
        return !em.createNamedQuery(UPS.FIND_BY_IUID)
                .setParameter(1, ctx.getUpsInstanceUID())
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
            ctx.addUPSEvent(UPSEvent.Type.StateReport, ctx.getUpsInstanceUID(), stateReportOf(attrs), subcribers);
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
        ctx.addUPSEvent(UPSEvent.Type.CancelRequested, ctx.getUpsInstanceUID(), cancelRequestedBy(ctx), subcribers);
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
                    ctx.getUpsInstanceUID(),
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
                .setParameter(1, ctx.getUpsInstanceUID())
                .setParameter(2, ctx.getSubscriberAET())
                .executeUpdate() > 0) {
            LOG.info("{}: Delete Subscription[uid={}, aet={}]", ctx, ctx.getUpsInstanceUID(), ctx.getSubscriberAET());
            return 1;
        }
        return 0;
    }

    public List<GlobalSubscription> findGlobalSubscriptions() {
        return em.createNamedQuery(GlobalSubscription.FIND_ALL_EAGER).getResultList();
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
        ctx.addUPSEvent(UPSEvent.Type.StateReport, ups.getUpsInstanceUID(),
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
}
