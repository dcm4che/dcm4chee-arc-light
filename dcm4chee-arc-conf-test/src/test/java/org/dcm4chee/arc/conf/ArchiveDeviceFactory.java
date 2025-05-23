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
 * **** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.conf;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.*;
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory;
import org.dcm4che3.io.BasicBulkDataDescriptor;
import org.dcm4che3.net.*;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditLoggerDeviceExtension;
import org.dcm4che3.net.audit.AuditRecordRepository;
import org.dcm4che3.net.audit.AuditSuppressCriteria;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.imageio.ImageReaderExtension;
import org.dcm4che3.net.imageio.ImageWriterExtension;
import org.dcm4che3.util.Property;

import java.net.URI;
import java.time.Period;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

import static org.dcm4che3.net.TransferCapability.Role.SCP;
import static org.dcm4che3.net.TransferCapability.Role.SCU;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
class ArchiveDeviceFactory {

    static final String AE_TITLE_DESC = "Hide instances rejected for Quality Reasons";
    static final String WORKLIST_DESC = "Modality and Unified Worklist";
    static final String IOCM_REGULAR_USE_DESC = "Show instances rejected for Quality Reasons";
    static final String IOCM_QUALITY_DESC = "Only show instances rejected for Quality Reasons";
    static final String IOCM_EXPIRED_DESC = "Only show instances rejected for Data Retention Expired";
    static final String IOCM_PAT_SAFETY_DESC = "Only show instances rejected for Patient Safety Reasons";
    static final String IOCM_WRONG_MWL_DESC = "Only show instances rejected for Incorrect Modality Worklist Entry";
    static final String AS_RECEIVED_DESC = "Retrieve instances as received without hiding rejected instances";

    enum ConfigType {
        DEFAULT,
        SAMPLE,
        DOCKER {
            @Override
            void configureKeyAndTrustStore(Device device) {
                device.setTrustStoreURL("file://${env.TRUSTSTORE}");
                device.setTrustStoreType("${env.TRUSTSTORE_TYPE}");
                device.setTrustStorePin("${env.TRUSTSTORE_PASSWORD}");
                device.setKeyStoreURL("file://${env.KEYSTORE}");
                device.setKeyStoreType("${env.KEYSTORE_TYPE}");
                device.setKeyStorePin("${env.KEYSTORE_PASSWORD}");
                device.setKeyStoreKeyPin("${env.KEY_PASSWORD}");
            }
        };

        void configureKeyAndTrustStore(Device device) {
            device.setTrustStoreURL("${jboss.server.config.url}/keystores/cacerts.p12");
            device.setTrustStoreType("PKCS12");
            device.setTrustStorePin("secret");
            device.setKeyStoreURL("${jboss.server.config.url}/keystores/key.p12");
            device.setKeyStoreType("PKCS12");
            device.setKeyStorePin("secret");
        }
    }
    static final String[] OTHER_DEVICES = {
            "scheduledstation",
            "dcmqrscp",
            "stgcmtscu",
            "mppsscp",
            "ianscp",
            "storescu",
            "mppsscu",
            "findscu",
            "getscu",
            "movescu",
            "upsscu",
            "hl7snd"
    };
    static final String[] OTHER_DEVICE_TYPES = {
            null,
            "ARCHIVE",
            "CT",
            "DSS",
            "DSS",
            "CT",
            "CT",
            "WSD",
            "WSD",
            "WSD",
            "DSS",
            "DSS"
    };
    static final String[] OTHER_AES = {
            "SCHEDULEDSTATION",
            "DCMQRSCP",
            "STGCMTSCU",
            "MPPSSCP",
            "IANSCP",
            "STORESCU",
            "MPPSSCU",
            "FINDSCU",
            "GETSCU",
            "MOVESCU",
            "UPSSCU"
    };
    static final int SCHEDULED_STATION_INDEX = 0;
    static final int STORESCU_INDEX = 5;
    static final int MPPSSCU_INDEX = 6;
    static final Issuer SITE_A =
            new Issuer("Site A", "1.2.40.0.13.1.1.999.111.1111", "ISO");
    static final Issuer SITE_B =
            new Issuer("Site B", "1.2.40.0.13.1.1.999.222.2222", "ISO");
    static final Code INST_A =
            new Code("111.1111", "99DCM4CHEE", null, "Site A");
    static final Code INST_B =
            new Code("222.2222", "99DCM4CHEE", null, "Site B");
    static final Issuer[] OTHER_ISSUER = {
            null, // SCHEDULEDSTATION
            SITE_B, // DCMQRSCP
            null, // STGCMTSCU
            SITE_A, // MPPSSCP
            null, // IANSCP
            SITE_A, // STORESCU
            SITE_A, // MPPSSCU
            SITE_A, // FINDSCU
            SITE_A, // GETSCU
            SITE_A, // MOVESCU
            SITE_A, // UPSSCU
            null // hl7snd
    };
    static final Code[] OTHER_INST_CODES = {
            null, // SCHEDULEDSTATION
            INST_B, // DCMQRSCP
            null, // STGCMTSCU
            null, // MPPSSCP
            null, // IANSCP
            INST_A, // STORESCU
            null, // MPPSSCU
            null, // FINDSCU
            null, // GETSCU
            null, // MOVESCU
            INST_A, // upsscu
            null, // hl7snd
    };
    static final int[] OTHER_PORTS = {
            104, -2, // SCHEDULEDSTATION
            11113, 2763, // DCMQRSCP
            11114, 2765, // STGCMTSCU
            11116, 2767, // MPPSSCP
            11117, 2768, // IANSCP
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // STORESCU
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // MPPSSCU
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // FINDSCU
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // GETSCU
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // MOVESCU
            11119, 2769 // UPSSCU
    };

    static final QueueDescriptor[] QUEUE_DESCRIPTORS = {
        newQueueDescriptor("MPPSSCU", "MPPS Forward Tasks", true),
        newQueueDescriptor("IANSCU", "IAN Tasks", true),
        newQueueDescriptor("StgCmtSCP", "Storage Commitment SCP Tasks", true),
        newQueueDescriptor("StgCmtSCU", "Storage Commitment SCU Tasks", true),
        newQueueDescriptor("StgVerTasks", "Storage Verification Tasks", true),
        newQueueDescriptor("Export", "Export Tasks", true),
        newQueueDescriptor("HL7Send", "HL7 Forward Tasks", true),
        newQueueDescriptor("RSClient", "RESTful Forward Tasks", true),
        newQueueDescriptor("Retrieve", "Dicom Retrieve Tasks", true),
        newQueueDescriptor("DiffTasks", "Diff Tasks", true),
        newQueueDescriptor("Rejection", "Rejection Tasks", true)
    };

    static final MetricsDescriptor[] METRICS_DESCRIPTORS = {
            newMetricsDescriptor("db-update-on-store","DB Update Time on Store", "ms"),
            newMetricsDescriptor("receive-from-STORESCU","Receive Data-rate from STORESCU", "MB/s"),
            newMetricsDescriptor("send-to-STORESCP","Send Data-rate to STORESCP", "MB/s"),
            newMetricsDescriptor("assoc-from-STORESCU","Number of concurrent associations from STORESCU", null),
            newMetricsDescriptor("assoc-to-STORESCP","Number of concurrent associations to STORESCP", null),
            newMetricsDescriptor("write-to-fs1","Write Data-rate to fs1", "MB/s"),
            newMetricsDescriptor("read-from-fs1","Read Data-rate from fs1", "MB/s"),
            newMetricsDescriptor("delete-from-fs1","Object Delete Time on fs1", "ms")
    };

    static final HL7OrderSPSStatus[] HL7_ORDER_SPS_STATUSES = {
            newHL7OrderSPSStatus("SCHEDULED", "NW_SC", "NW_IP", "XO_SC"),
            newHL7OrderSPSStatus("CANCELED", "CA_CA"),
            newHL7OrderSPSStatus("DISCONTINUED", "DC_CA"),
            newHL7OrderSPSStatus("COMPLETED", "XO_CM", "SC_CM", "SC_A")
    };

    private static QueueDescriptor newQueueDescriptor(
            String name, String description, boolean installed) {
        QueueDescriptor desc = new QueueDescriptor(name);
        desc.setDescription(description);
        desc.setMaxRetries(10);
        desc.setRetryDelay(Duration.valueOf("PT30S"));
        desc.setRetryDelayMultiplier(200);
        desc.setMaxRetryDelay(Duration.valueOf("PT10M"));
        desc.setPurgeTaskCompletedDelay(Duration.valueOf("P1D"));
        desc.setInstalled(installed);
        return desc;
    }

    private static MetricsDescriptor newMetricsDescriptor(String name, String description, String unit) {
        MetricsDescriptor desc = new MetricsDescriptor();
        desc.setMetricsName(name);
        desc.setDescription(description);
        desc.setUnit(unit);
        return desc;
    }

    private static HL7OrderSPSStatus newHL7OrderSPSStatus(String spsStatus, String... orderStatuses) {
        HL7OrderSPSStatus hl7OrderSPSStatus = new HL7OrderSPSStatus();
        hl7OrderSPSStatus.setSPSStatus(SPSStatus.valueOf(spsStatus));
        hl7OrderSPSStatus.setOrderControlStatusCodes(orderStatuses);
        return hl7OrderSPSStatus;
    }

    private static IDGenerator newIDGenerator(String name, String format) {
        IDGenerator gen = new IDGenerator();
        gen.setName(name);
        gen.setFormat(format);
        return gen;
    }

    private static AuditSuppressCriteria suppressAuditQueryFromArchive() {
        AuditSuppressCriteria auditSuppressCriteria = new AuditSuppressCriteria("Suppress Query from own Archive AE");
        auditSuppressCriteria.setEventIDs(AuditMessages.EventID.Query);
        auditSuppressCriteria.setUserIDs(AE_TITLE);
        auditSuppressCriteria.setUserIsRequestor(true);
        return auditSuppressCriteria;
    }

    static final int[] PATIENT_ATTRS = {
            Tag.SpecificCharacterSet,
            Tag.PatientName,
            Tag.PatientID,
            Tag.IssuerOfPatientID,
            Tag.TypeOfPatientID,
            Tag.IssuerOfPatientIDQualifiersSequence,
            Tag.PatientBirthDate,
            Tag.PatientBirthTime,
            Tag.PatientBirthDateInAlternativeCalendar,
            Tag.PatientDeathDateInAlternativeCalendar,
            Tag.PatientAlternativeCalendar,
            Tag.PatientSex,
            Tag.PatientInsurancePlanCodeSequence,
            Tag.PatientPrimaryLanguageCodeSequence,
            Tag.QualityControlSubject,
            Tag.StrainDescription,
            Tag.StrainNomenclature,
            Tag.StrainStockSequence,
            Tag.StrainAdditionalInformation,
            Tag.StrainCodeSequence,
            Tag.GeneticModificationsCodeSequence,
            Tag.OtherPatientNames,
            Tag.OtherPatientIDsSequence,
            Tag.PatientBirthName,
            Tag.PatientAddress,
            Tag.PatientMotherBirthName,
            Tag.MilitaryRank,
            Tag.BranchOfService,
            Tag.MedicalRecordLocator,
            Tag.ReferencedPatientPhotoSequence,
            Tag.CountryOfResidence,
            Tag.RegionOfResidence,
            Tag.PatientTelephoneNumbers,
            Tag.PatientTelecomInformation,
            Tag.EthnicGroup,
            Tag.PatientReligiousPreference,
            Tag.PatientSpeciesDescription,
            Tag.PatientSpeciesCodeSequence,
            Tag.PatientBreedDescription,
            Tag.PatientBreedCodeSequence,
            Tag.BreedRegistrationSequence,
            Tag.ResponsiblePerson,
            Tag.ResponsiblePersonRole,
            Tag.ResponsibleOrganization,
            Tag.PatientComments,
            Tag.ClinicalTrialSponsorName,
            Tag.ClinicalTrialProtocolID,
            Tag.ClinicalTrialProtocolName,
            Tag.ClinicalTrialSiteID,
            Tag.ClinicalTrialSiteName,
            Tag.ClinicalTrialSubjectID,
            Tag.ClinicalTrialSubjectReadingID,
            Tag.PatientIdentityRemoved,
            Tag.DeidentificationMethod,
            Tag.DeidentificationMethodCodeSequence,
            Tag.ClinicalTrialProtocolEthicsCommitteeName,
            Tag.ClinicalTrialProtocolEthicsCommitteeApprovalNumber,
            Tag.SpecialNeeds,
            Tag.PertinentDocumentsSequence,
            Tag.PatientClinicalTrialParticipationSequence,
            Tag.ConfidentialityConstraintOnPatientDataDescription
    };

    static final int[] STUDY_ATTRS = {
            Tag.SpecificCharacterSet,
            Tag.StudyDate,
            Tag.StudyTime,
            Tag.AccessionNumber,
            Tag.IssuerOfAccessionNumberSequence,
            Tag.ReferringPhysicianName,
            Tag.ReferringPhysicianIdentificationSequence,
            Tag.TimezoneOffsetFromUTC,
            Tag.StudyDescription,
            Tag.ProcedureCodeSequence,
            Tag.PhysiciansOfRecord,
            Tag.PatientAge,
            Tag.PatientSize,
            Tag.PatientSizeCodeSequence,
            Tag.PatientWeight,
            Tag.PatientBodyMassIndex,
            Tag.MeasuredAPDimension,
            Tag.MeasuredLateralDimension,
            Tag.MedicalAlerts,
            Tag.Allergies,
            Tag.SmokingStatus,
            Tag.PregnancyStatus,
            Tag.LastMenstrualDate,
            Tag.PatientState,
            Tag.AdmittingDiagnosesDescription,
            Tag.AdmittingDiagnosesCodeSequence,
            Tag.AdmissionID,
            Tag.IssuerOfAdmissionIDSequence,
            Tag.RouteOfAdmissions,
            Tag.ReasonForVisit,
            Tag.ReasonForVisitCodeSequence,
            Tag.Occupation,
            Tag.AdditionalPatientHistory,
            Tag.ServiceEpisodeID,
            Tag.ServiceEpisodeDescription,
            Tag.IssuerOfServiceEpisodeIDSequence,
            Tag.PatientSexNeutered,
            Tag.StudyInstanceUID,
            Tag.StudyID,
            Tag.ReasonForPerformedProcedureCodeSequence
    };
    static final int[] SERIES_ATTRS = {
            Tag.SpecificCharacterSet,
            Tag.SeriesDate,
            Tag.SeriesTime,
            Tag.Modality,
            Tag.Manufacturer,
            Tag.InstitutionName,
            Tag.InstitutionCodeSequence,
            Tag.TimezoneOffsetFromUTC,
            Tag.StationName,
            Tag.SeriesDescription,
            Tag.InstitutionalDepartmentName,
            Tag.InstitutionalDepartmentTypeCodeSequence,
            Tag.InstitutionAddress,
            Tag.PerformingPhysicianName,
            Tag.ManufacturerModelName,
            Tag.ReferencedPerformedProcedureStepSequence,
            Tag.AnatomicRegionSequence,
            Tag.BodyPartExamined,
            Tag.SeriesInstanceUID,
            Tag.SeriesNumber,
            Tag.Laterality,
            Tag.PerformedProcedureStepStartDate,
            Tag.PerformedProcedureStepStartTime,
            Tag.PerformedProcedureStepEndDate,
            Tag.PerformedProcedureStepEndTime,
            Tag.PerformedProtocolCodeSequence,
            Tag.CommentsOnThePerformedProcedureStep,
            Tag.RequestAttributesSequence,
            Tag.SeriesDescriptionCodeSequence,
            Tag.OperatorsName,
            Tag.OperatorIdentificationSequence,
            Tag.ViewPosition
    };
    static final int[] INSTANCE_ATTRS = {
            Tag.SpecificCharacterSet,
            Tag.ImageType,
            Tag.InstanceCreationDate,
            Tag.InstanceCreationTime,
            Tag.SOPClassUID,
            Tag.SOPInstanceUID,
            Tag.ContentDate,
            Tag.ContentTime,
            Tag.TimezoneOffsetFromUTC,
            Tag.ReferencedSeriesSequence,
            Tag.AnatomicRegionSequence,
            Tag.ContributingEquipmentSequence,
            Tag.InstanceNumber,
            Tag.NumberOfFrames,
            Tag.Rows,
            Tag.Columns,
            Tag.BitsAllocated,
            Tag.ObservationDateTime,
            Tag.ConceptNameCodeSequence,
            Tag.VerifyingObserverSequence,
            Tag.ReferencedRequestSequence,
            Tag.CompletionFlag,
            Tag.VerificationFlag,
            Tag.ContentTemplateSequence,
            Tag.HL7InstanceIdentifier,
            Tag.DocumentTitle,
            Tag.MIMETypeOfEncapsulatedDocument,
            Tag.ContentLabel,
            Tag.ContentDescription,
            Tag.PresentationCreationDate,
            Tag.PresentationCreationTime,
            Tag.ContentCreatorName,
            Tag.IdenticalDocumentsSequence,
            Tag.CurrentRequestedProcedureEvidenceSequence,
            Tag.ConcatenationUID,
            Tag.SOPInstanceUIDOfConcatenationSource,
            Tag.ContainerIdentifier,
            Tag.AlternateContainerIdentifierSequence,
            Tag.IssuerOfTheContainerIdentifierSequence,
            Tag.SpecimenUID,
            Tag.SpecimenIdentifier,
            Tag.IssuerOfTheSpecimenIdentifierSequence,
            Tag.PredecessorDocumentsSequence,
            Tag.ImageLaterality,
            Tag.PrimaryAnatomicStructureSequence,
            Tag.SegmentSequence,
            Tag.QuantityDefinitionSequence,
            Tag.AuthorObserverSequence,
            Tag.ViewPosition,
            Tag.ViewCodeSequence,
            Tag.SharedFunctionalGroupsSequence,
            Tag.PerFrameFunctionalGroupsSequence
    };
    static final int[] LEADING_CFIND_SCP_ATTRS = {
            Tag.StudyDate,
            Tag.StudyTime,
            Tag.AccessionNumber,
            Tag.IssuerOfAccessionNumberSequence,
            Tag.ReferringPhysicianName,
            Tag.StudyDescription,
            Tag.ProcedureCodeSequence,
            Tag.PatientName,
            Tag.PatientID,
            Tag.IssuerOfPatientID,
            Tag.PatientBirthDate,
            Tag.PatientSex,
            Tag.PatientAge,
            Tag.PatientSize,
            Tag.PatientWeight,
            Tag.StudyInstanceUID,
            Tag.StudyID
    };
    static final int[] DIFF_PAT_ATTRS = {
            Tag.PatientName,
            Tag.PatientID,
            Tag.IssuerOfPatientID,
            Tag.PatientBirthDate,
            Tag.PatientSex,
    };
    static final int[] DIFF_STUDY_ATTRS = {
            Tag.StudyDate,
            Tag.StudyTime,
            Tag.AccessionNumber,
            Tag.IssuerOfAccessionNumberSequence,
            Tag.ReferringPhysicianName,
            Tag.StudyDescription,
            Tag.ProcedureCodeSequence,
            Tag.PatientAge,
            Tag.PatientSize,
            Tag.PatientWeight,
            Tag.StudyInstanceUID,
            Tag.StudyID,
            Tag.NumberOfStudyRelatedSeries,
            Tag.NumberOfStudyRelatedInstances
    };
    static final int[] DIFF_ACCESSION_NUMBER = {
            Tag.AccessionNumber,
            Tag.IssuerOfAccessionNumberSequence,
    };
    static final int[] QIDO_STUDY_ATTRS = {
            Tag.StudyDate,
            Tag.StudyTime,
            Tag.AccessionNumber,
            Tag.ModalitiesInStudy,
            Tag.ReferringPhysicianName,
            Tag.PatientName,
            Tag.PatientID,
            Tag.PatientBirthDate,
            Tag.PatientSex,
            Tag.StudyID,
            Tag.StudyInstanceUID,
            Tag.StudyDescription,
            Tag.NumberOfStudyRelatedSeries,
            Tag.NumberOfStudyRelatedInstances
    };
    static final int[] MPPS_ATTRS = {
            Tag.SpecificCharacterSet,
            Tag.Modality,
            Tag.TimezoneOffsetFromUTC,
            Tag.ProcedureCodeSequence,
            Tag.AnatomicStructureSpaceOrRegionSequence,
            Tag.DistanceSourceToDetector,
            Tag.ImageAndFluoroscopyAreaDoseProduct,
            Tag.StudyID,
            Tag.AdmissionID,
            Tag.IssuerOfAdmissionIDSequence,
            Tag.ServiceEpisodeID,
            Tag.ServiceEpisodeDescription,
            Tag.IssuerOfServiceEpisodeIDSequence,
            Tag.PerformedStationAETitle,
            Tag.PerformedStationName,
            Tag.PerformedLocation,
            Tag.PerformedProcedureStepStartDate,
            Tag.PerformedProcedureStepStartTime,
            Tag.PerformedProcedureStepEndDate,
            Tag.PerformedProcedureStepEndTime,
            Tag.PerformedProcedureStepStatus,
            Tag.PerformedProcedureStepID,
            Tag.PerformedProcedureStepDescription,
            Tag.PerformedProcedureTypeDescription,
            Tag.PerformedProtocolCodeSequence,
            Tag.ScheduledStepAttributesSequence,
            Tag.CommentsOnThePerformedProcedureStep,
            Tag.PerformedProcedureStepDiscontinuationReasonCodeSequence,
            Tag.TotalTimeOfFluoroscopy,
            Tag.TotalNumberOfExposures,
            Tag.EntranceDose,
            Tag.ExposedArea,
            Tag.DistanceSourceToEntrance,
            Tag.ExposureDoseSequence,
            Tag.CommentsOnRadiationDose,
            Tag.BillingProcedureStepSequence,
            Tag.FilmConsumptionSequence,
            Tag.BillingSuppliesAndDevicesSequence,
            Tag.PerformedSeriesSequence,
            Tag.ReasonForPerformedProcedureCodeSequence,
            Tag.EntranceDoseInmGy
    };
    static final int[] MWL_ATTRS = {
            Tag.SpecificCharacterSet,
            Tag.StudyDate,
            Tag.StudyTime,
            Tag.AccessionNumber,
            Tag.IssuerOfAccessionNumberSequence,
            Tag.InstitutionName,
            Tag.InstitutionAddress,
            Tag.InstitutionCodeSequence,
            Tag.ReferringPhysicianName,
            Tag.ReferringPhysicianAddress,
            Tag.ReferringPhysicianTelephoneNumbers,
            Tag.ReferringPhysicianIdentificationSequence,
            Tag.ConsultingPhysicianName,
            Tag.ConsultingPhysicianIdentificationSequence,
            Tag.TimezoneOffsetFromUTC,
            Tag.InstitutionalDepartmentName,
            Tag.InstitutionalDepartmentTypeCodeSequence,
            Tag.AdmittingDiagnosesDescription,
            Tag.AdmittingDiagnosesCodeSequence,
            Tag.ReferencedStudySequence,
            Tag.ReferencedPatientSequence,
            Tag.PatientAge,
            Tag.PatientSize,
            Tag.PatientSizeCodeSequence,
            Tag.PatientWeight,
            Tag.PatientBodyMassIndex,
            Tag.MeasuredAPDimension,
            Tag.MeasuredLateralDimension,
            Tag.Occupation,
            Tag.AdditionalPatientHistory,
            Tag.PatientSexNeutered,
            Tag.MedicalAlerts,
            Tag.Allergies,
            Tag.SmokingStatus,
            Tag.PregnancyStatus,
            Tag.LastMenstrualDate,
            Tag.StudyInstanceUID,
            Tag.RequestingPhysicianIdentificationSequence,
            Tag.RequestingPhysician,
            Tag.RequestingService,
            Tag.RequestingServiceCodeSequence,
            Tag.RequestedProcedureDescription,
            Tag.RequestedProcedureCodeSequence,
            Tag.VisitStatusID,
            Tag.AdmissionID,
            Tag.IssuerOfAdmissionIDSequence,
            Tag.RouteOfAdmissions,
            Tag.AdmittingDate,
            Tag.AdmittingTime,
            Tag.ReasonForVisit,
            Tag.ReasonForVisitCodeSequence,
            Tag.SpecialNeeds,
            Tag.ServiceEpisodeID,
            Tag.ServiceEpisodeDescription,
            Tag.IssuerOfServiceEpisodeIDSequence,
            Tag.PertinentDocumentsSequence,
            Tag.CurrentPatientLocation,
            Tag.PatientInstitutionResidence,
            Tag.PatientState,
            Tag.VisitComments,
            Tag.ScheduledProcedureStepSequence,
            Tag.RequestedProcedureID,
            Tag.ReasonForTheRequestedProcedure,
            Tag.RequestedProcedurePriority,
            Tag.PatientTransportArrangements,
            Tag.RequestedProcedureLocation,
            Tag.ConfidentialityCode,
            Tag.ReportingPriority,
            Tag.NamesOfIntendedRecipientsOfResults,
            Tag.ReasonForRequestedProcedureCodeSequence,
            Tag.IntendedRecipientsOfResultsIdentificationSequence,
            Tag.OrderPlacerIdentifierSequence,
            Tag.OrderFillerIdentifierSequence,
            Tag.RequestedProcedureComments,
            Tag.IssueDateOfImagingServiceRequest,
            Tag.IssueTimeOfImagingServiceRequest,
            Tag.OrderEnteredBy,
            Tag.OrderEntererLocation,
            Tag.OrderCallbackPhoneNumber,
            Tag.OrderCallbackTelecomInformation,
            Tag.PlacerOrderNumberImagingServiceRequest,
            Tag.FillerOrderNumberImagingServiceRequest,
            Tag.ImagingServiceRequestComments,
            Tag.StudyStatusID,
            Tag.WorklistLabel
    };
    static final int[] UPS_ATTRS = {
            Tag.SpecificCharacterSet,
            Tag.SOPClassUID,
            Tag.SOPInstanceUID,
            Tag.InstitutionName,
            Tag.InstitutionAddress,
            Tag.InstitutionCodeSequence,
            Tag.ReferringPhysicianName,
            Tag.ReferringPhysicianAddress,
            Tag.ReferringPhysicianTelephoneNumbers,
            Tag.ReferringPhysicianIdentificationSequence,
            Tag.ConsultingPhysicianName,
            Tag.ConsultingPhysicianIdentificationSequence,
            Tag.InstitutionalDepartmentName,
            Tag.InstitutionalDepartmentTypeCodeSequence,
            Tag.AdmittingDiagnosesDescription,
            Tag.AdmittingDiagnosesCodeSequence,
            Tag.PatientAge,
            Tag.PatientSize,
            Tag.PatientSizeCodeSequence,
            Tag.MeasuredAPDimension,
            Tag.MeasuredLateralDimension,
            Tag.PatientWeight,
            Tag.Occupation,
            Tag.AdditionalPatientHistory,
            Tag.PatientSexNeutered,
            Tag.MedicalAlerts,
            Tag.Allergies,
            Tag.SmokingStatus,
            Tag.PregnancyStatus,
            Tag.LastMenstrualDate,
            Tag.StudyInstanceUID,
            Tag.ReasonForVisit,
            Tag.ReasonForVisitCodeSequence,
            Tag.AdmissionID,
            Tag.IssuerOfAdmissionIDSequence,
            Tag.SpecialNeeds,
            Tag.PertinentDocumentsSequence,
            Tag.PertinentResourcesSequence,
            Tag.PatientState,
            Tag.PatientClinicalTrialParticipationSequence,
            Tag.VisitStatusID,
            Tag.RouteOfAdmissions,
            Tag.AdmittingDate,
            Tag.AdmittingTime,
            Tag.ServiceEpisodeID,
            Tag.ServiceEpisodeDescription,
            Tag.IssuerOfServiceEpisodeIDSequence,
            Tag.CurrentPatientLocation,
            Tag.PatientInstitutionResidence,
            Tag.VisitComments,
            Tag.ScheduledProcedureStepStartDateTime,
            Tag.ScheduledProcedureStepExpirationDateTime,
            Tag.ExpectedCompletionDateTime,
            Tag.ScheduledWorkitemCodeSequence,
            Tag.InputInformationSequence,
            Tag.ScheduledStationNameCodeSequence,
            Tag.ScheduledStationClassCodeSequence,
            Tag.ScheduledStationGeographicLocationCodeSequence,
            Tag.ScheduledHumanPerformersSequence,
            Tag.InputReadinessState,
            Tag.OutputDestinationSequence,
            Tag.ReferencedRequestSequence,
            Tag.ProcedureStepState,
            Tag.ProcedureStepProgressInformationSequence,
            Tag.ScheduledProcedureStepPriority,
            Tag.WorklistLabel,
            Tag.ProcedureStepLabel,
            Tag.ScheduledProcessingParametersSequence,
            Tag.UnifiedProcedureStepPerformedProcedureSequence,
            Tag.ReplacedProcedureStepSequence,
    };
    static final String[] IMAGE_CUIDS = {
            UID.ComputedRadiographyImageStorage,
            UID.DigitalXRayImageStorageForPresentation,
            UID.DigitalXRayImageStorageForProcessing,
            UID.DigitalMammographyXRayImageStorageForPresentation,
            UID.DigitalMammographyXRayImageStorageForProcessing,
            UID.DigitalIntraOralXRayImageStorageForPresentation,
            UID.DigitalIntraOralXRayImageStorageForProcessing,
            UID.CTImageStorage,
            UID.EnhancedCTImageStorage,
            UID.LegacyConvertedEnhancedCTImageStorage,
            UID.UltrasoundMultiFrameImageStorageRetired,
            UID.UltrasoundMultiFrameImageStorage,
            UID.MRImageStorage,
            UID.EnhancedMRImageStorage,
            UID.EnhancedMRColorImageStorage,
            UID.LegacyConvertedEnhancedMRImageStorage,
            UID.NuclearMedicineImageStorageRetired,
            UID.UltrasoundImageStorageRetired,
            UID.UltrasoundImageStorage,
            UID.EnhancedUSVolumeStorage,
            UID.PhotoacousticImageStorage,
            UID.SecondaryCaptureImageStorage,
            UID.MultiFrameGrayscaleByteSecondaryCaptureImageStorage,
            UID.MultiFrameGrayscaleWordSecondaryCaptureImageStorage,
            UID.MultiFrameTrueColorSecondaryCaptureImageStorage,
            UID.XRayAngiographicImageStorage,
            UID.EnhancedXAImageStorage,
            UID.XRayRadiofluoroscopicImageStorage,
            UID.EnhancedXRFImageStorage,
            UID.XRayAngiographicBiPlaneImageStorage,
            UID.XRay3DAngiographicImageStorage,
            UID.XRay3DCraniofacialImageStorage,
            UID.BreastTomosynthesisImageStorage,
            UID.BreastProjectionXRayImageStorageForPresentation,
            UID.BreastProjectionXRayImageStorageForProcessing,
            UID.IntravascularOpticalCoherenceTomographyImageStorageForPresentation,
            UID.IntravascularOpticalCoherenceTomographyImageStorageForProcessing,
            UID.NuclearMedicineImageStorage,
            UID.VLImageStorageTrial,
            UID.VLMultiFrameImageStorageTrial,
            UID.VLEndoscopicImageStorage,
            UID.VLMicroscopicImageStorage,
            UID.VLSlideCoordinatesMicroscopicImageStorage,
            UID.VLPhotographicImageStorage,
            UID.OphthalmicPhotography8BitImageStorage,
            UID.OphthalmicPhotography16BitImageStorage,
            UID.OphthalmicTomographyImageStorage,
            UID.WideFieldOphthalmicPhotographyStereographicProjectionImageStorage,
            UID.WideFieldOphthalmicPhotography3DCoordinatesImageStorage,
            UID.OphthalmicOpticalCoherenceTomographyEnFaceImageStorage,
            UID.OphthalmicOpticalCoherenceTomographyBscanVolumeAnalysisStorage,
            UID.VLWholeSlideMicroscopyImageStorage,
            UID.DermoscopicPhotographyImageStorage,
            UID.ConfocalMicroscopyImageStorage,
            UID.ConfocalMicroscopyTiledPyramidalImageStorage,
            UID.OphthalmicThicknessMapStorage,
            UID.CornealTopographyMapStorage,
            UID.PositronEmissionTomographyImageStorage,
            UID.LegacyConvertedEnhancedPETImageStorage,
            UID.EnhancedPETImageStorage,
            UID.RTImageStorage,
            UID.EnhancedRTImageStorage,
            UID.EnhancedContinuousRTImageStorage
    };
    static final String[] PRIVATE_IMAGE_CUIDS = {
            UID.PrivateFujiCRImageStorage,
            UID.PrivateGEDicomCTImageInfoObject,
            UID.PrivateGEDicomDisplayImageInfoObject,
            UID.PrivateGEDicomMRImageInfoObject,
            UID.PrivatePhilipsCTSyntheticImageStorage,
            UID.PrivatePhilipsCXImageStorage,
            UID.PrivatePhilipsCXSyntheticImageStorage,
            UID.PrivatePhilipsMRColorImageStorage,
            UID.PrivatePhilipsMRSyntheticImageStorage,
            UID.PrivatePhilipsPerfusionImageStorage,
            UID.PrivatePixelMedFloatingPointImageStorage,
            UID.PrivatePixelMedLegacyConvertedEnhancedCTImageStorage,
            UID.PrivatePixelMedLegacyConvertedEnhancedMRImageStorage,
            UID.PrivatePixelMedLegacyConvertedEnhancedPETImageStorage,
            UID.PrivatePMODMultiFrameImageStorage,
            UID.PrivateToshibaUSImageStorage
    };
    static final String[] IMAGE_TSUIDS = {
            UID.ImplicitVRLittleEndian,
            UID.ExplicitVRLittleEndian,
            UID.JPEGBaseline8Bit,
            UID.JPEGExtended12Bit,
            UID.JPEGLosslessSV1,
            UID.JPEGLossless,
            UID.JPEGLSLossless,
            UID.JPEGLSNearLossless,
            UID.JPEG2000Lossless,
            UID.JPEG2000,
            UID.HTJ2KLossless,
            UID.HTJ2KLosslessRPCL,
            UID.HTJ2K,
            UID.RLELossless
    };
    static final String[] VIDEO_CUIDS = {
            UID.VideoEndoscopicImageStorage,
            UID.VideoMicroscopicImageStorage,
            UID.VideoPhotographicImageStorage,
    };

    static final String[] VIDEO_TSUIDS = {
            UID.JPEGBaseline8Bit,
            UID.MPEG2MPML,
            UID.MPEG2MPMLF,
            UID.MPEG2MPHL,
            UID.MPEG2MPHLF,
            UID.MPEG4HP41BD,
            UID.MPEG4HP41BDF,
            UID.MPEG4HP41,
            UID.MPEG4HP41F,
            UID.MPEG4HP422D,
            UID.MPEG4HP422DF,
            UID.MPEG4HP423D,
            UID.MPEG4HP423DF,
            UID.MPEG4HP42STEREO,
            UID.MPEG4HP42STEREOF,
            UID.HEVCMP51,
            UID.HEVCM10P51
    };

    private static final String[] SR_CUIDS = {
            UID.SpectaclePrescriptionReportStorage,
            UID.MacularGridThicknessAndVolumeReportStorage,
            UID.BasicTextSRStorage,
            UID.EnhancedSRStorage,
            UID.ComprehensiveSRStorage,
            UID.Comprehensive3DSRStorage,
            UID.ExtensibleSRStorage,
            UID.ProcedureLogStorage,
            UID.MammographyCADSRStorage,
            UID.KeyObjectSelectionDocumentStorage,
            UID.ChestCADSRStorage,
            UID.XRayRadiationDoseSRStorage,
            UID.RadiopharmaceuticalRadiationDoseSRStorage,
            UID.ColonCADSRStorage,
            UID.ImplantationPlanSRStorage,
            UID.AcquisitionContextSRStorage,
            UID.SimplifiedAdultEchoSRStorage,
            UID.PatientRadiationDoseSRStorage,
            UID.PlannedImagingAgentAdministrationSRStorage,
            UID.PerformedImagingAgentAdministrationSRStorage,
            UID.EnhancedXRayRadiationDoseSRStorage,
            UID.WaveformAnnotationSRStorage
    };

    static final String[] SR_TSUIDS = {
            UID.ImplicitVRLittleEndian,
            UID.ExplicitVRLittleEndian,
            UID.DeflatedExplicitVRLittleEndian
    };

    static final String[] OTHER_CUIDS = {
            UID.StoredPrintStorage,
            UID.HardcopyGrayscaleImageStorage,
            UID.HardcopyColorImageStorage,
            UID.MRSpectroscopyStorage,
            UID.MultiFrameSingleBitSecondaryCaptureImageStorage,
            UID.StandaloneOverlayStorage,
            UID.StandaloneCurveStorage,
            UID.TwelveLeadECGWaveformStorage,
            UID.GeneralECGWaveformStorage,
            UID.General32bitECGWaveformStorage,
            UID.AmbulatoryECGWaveformStorage,
            UID.HemodynamicWaveformStorage,
            UID.CardiacElectrophysiologyWaveformStorage,
            UID.BasicVoiceAudioWaveformStorage,
            UID.GeneralAudioWaveformStorage,
            UID.ArterialPulseWaveformStorage,
            UID.RespiratoryWaveformStorage,
            UID.MultichannelRespiratoryWaveformStorage,
            UID.RoutineScalpElectroencephalogramWaveformStorage,
            UID.ElectromyogramWaveformStorage,
            UID.ElectrooculogramWaveformStorage,
            UID.SleepElectroencephalogramWaveformStorage,
            UID.BodyPositionWaveformStorage,
            UID.WaveformPresentationStateStorage,
            UID.WaveformAcquisitionPresentationStateStorage,
            UID.StandaloneModalityLUTStorage,
            UID.StandaloneVOILUTStorage,
            UID.GrayscaleSoftcopyPresentationStateStorage,
            UID.ColorSoftcopyPresentationStateStorage,
            UID.PseudoColorSoftcopyPresentationStateStorage,
            UID.BlendingSoftcopyPresentationStateStorage,
            UID.XAXRFGrayscaleSoftcopyPresentationStateStorage,
            UID.GrayscalePlanarMPRVolumetricPresentationStateStorage,
            UID.CompositingPlanarMPRVolumetricPresentationStateStorage,
            UID.AdvancedBlendingPresentationStateStorage,
            UID.VolumeRenderingVolumetricPresentationStateStorage,
            UID.SegmentedVolumeRenderingVolumetricPresentationStateStorage,
            UID.MultipleVolumeRenderingVolumetricPresentationStateStorage,
            UID.VariableModalityLUTSoftcopyPresentationStateStorage,
            UID.ParametricMapStorage,
            UID.RawDataStorage,
            UID.SpatialRegistrationStorage,
            UID.SpatialFiducialsStorage,
            UID.DeformableSpatialRegistrationStorage,
            UID.SegmentationStorage,
            UID.SurfaceSegmentationStorage,
            UID.TractographyResultsStorage,
            UID.LabelMapSegmentationStorage,
            UID.HeightMapSegmentationStorage,
            UID.RealWorldValueMappingStorage,
            UID.SurfaceScanMeshStorage,
            UID.SurfaceScanPointCloudStorage,
            UID.StereometricRelationshipStorage,
            UID.LensometryMeasurementsStorage,
            UID.AutorefractionMeasurementsStorage,
            UID.KeratometryMeasurementsStorage,
            UID.SubjectiveRefractionMeasurementsStorage,
            UID.VisualAcuityMeasurementsStorage,
            UID.OphthalmicAxialMeasurementsStorage,
            UID.IntraocularLensCalculationsStorage,
            UID.OphthalmicVisualFieldStaticPerimetryMeasurementsStorage,
            UID.BasicStructuredDisplayStorage,
            UID.EncapsulatedPDFStorage,
            UID.EncapsulatedCDAStorage,
            UID.EncapsulatedSTLStorage,
            UID.EncapsulatedOBJStorage,
            UID.EncapsulatedMTLStorage,
            UID.StandalonePETCurveStorage,
            UID.TextSRStorageTrial,
            UID.AudioSRStorageTrial,
            UID.DetailSRStorageTrial,
            UID.ComprehensiveSRStorageTrial,
            UID.ContentAssessmentResultsStorage,
            UID.MicroscopyBulkSimpleAnnotationsStorage,
            UID.CTPerformedProcedureProtocolStorage,
            UID.XAPerformedProcedureProtocolStorage,
            UID.RTDoseStorage,
            UID.RTStructureSetStorage,
            UID.RTBeamsTreatmentRecordStorage,
            UID.RTPlanStorage,
            UID.RTBrachyTreatmentRecordStorage,
            UID.RTTreatmentSummaryRecordStorage,
            UID.RTIonPlanStorage,
            UID.RTIonBeamsTreatmentRecordStorage,
            UID.RTPhysicianIntentStorage,
            UID.RTSegmentAnnotationStorage,
            UID.RTRadiationSetStorage,
            UID.CArmPhotonElectronRadiationStorage,
            UID.TomotherapeuticRadiationStorage,
            UID.RoboticArmRadiationStorage,
            UID.RTRadiationRecordSetStorage,
            UID.RTRadiationSalvageRecordStorage,
            UID.TomotherapeuticRadiationRecordStorage,
            UID.CArmPhotonElectronRadiationRecordStorage,
            UID.RoboticRadiationRecordStorage,
            UID.RTRadiationSetDeliveryInstructionStorage,
            UID.RTTreatmentPreparationStorage,
            UID.RTPatientPositionAcquisitionInstructionStorage,
            UID.RTBeamsDeliveryInstructionStorage,
            UID.RTBrachyApplicationSetupDeliveryInstructionStorage,
    };

    static final String[] PRIVATE_CUIDS = {
            UID.PrivateDcm4cheEncapsulatedGenozipStorage,
            UID.PrivateDcm4cheEncapsulatedBzip2VCFStorage,
            UID.PrivateDcm4cheEncapsulatedBzip2DocumentStorage,
            UID.PrivateAgfaArrivalTransaction,
            UID.PrivateAgfaBasicAttributePresentationState,
            UID.PrivateAgfaDictationTransaction,
            UID.PrivateAgfaReportApprovalTransaction,
            UID.PrivateAgfaReportTranscriptionTransaction,
            UID.PrivateERADPracticeBuilderReportDictationStorage,
            UID.PrivateERADPracticeBuilderReportTextStorage,
            UID.PrivateGE3DModelStorage,
            UID.PrivateGECollageStorage,
            UID.PrivateGEeNTEGRAProtocolOrNMGenieStorage,
            UID.PrivateGEPETRawDataStorage,
            UID.PrivateGERTPlanStorage,
            UID.PrivatePhilips3DObjectStorage,
            UID.PrivatePhilips3DObjectStorageRetired,
            UID.PrivatePhilips3DPresentationStateStorage,
            UID.PrivatePhilipsCompositeObjectStorage,
            UID.PrivatePhilipsHPLive3D01Storage,
            UID.PrivatePhilipsHPLive3D02Storage,
            UID.PrivatePhilipsLiveRunStorage,
            UID.PrivatePhilipsMRCardioAnalysisStorage,
            UID.PrivatePhilipsMRCardioAnalysisStorageRetired,
            UID.PrivatePhilipsMRCardioProfileStorage,
            UID.PrivatePhilipsMRCardioStorage,
            UID.PrivatePhilipsMRCardioStorageRetired,
            UID.PrivatePhilipsMRExamcardStorage,
            UID.PrivatePhilipsMRSeriesDataStorage,
            UID.PrivatePhilipsMRSpectrumStorage,
            UID.PrivatePhilipsPerfusionStorage,
            UID.PrivatePhilipsReconstructionStorage,
            UID.PrivatePhilipsRunStorage,
            UID.PrivatePhilipsSpecialisedXAStorage,
            UID.PrivatePhilipsSurfaceStorage,
            UID.PrivatePhilipsSurfaceStorageRetired,
            UID.PrivatePhilipsVolumeSetStorage,
            UID.PrivatePhilipsVolumeStorage,
            UID.PrivatePhilipsVolumeStorageRetired,
            UID.PrivatePhilipsVRMLStorage,
            UID.PrivatePhilipsXRayMFStorage,
            UID.PrivateSiemensAXFrameSetsStorage,
            UID.PrivateSiemensCSANonImageStorage,
            UID.PrivateSiemensCTMRVolumeStorage,
            UID.PrivateTomTecAnnotationStorage
    };

    static final String[] OTHER_TSUIDS = {
            UID.ImplicitVRLittleEndian,
            UID.ExplicitVRLittleEndian
    };

    static final String[] QUERY_CUIDS = {
            UID.PatientRootQueryRetrieveInformationModelFind,
            UID.StudyRootQueryRetrieveInformationModelFind,
            UID.PatientStudyOnlyQueryRetrieveInformationModelFind
    };
    static final String[] RETRIEVE_CUIDS = {
            UID.PatientRootQueryRetrieveInformationModelGet,
            UID.PatientRootQueryRetrieveInformationModelMove,
            UID.StudyRootQueryRetrieveInformationModelGet,
            UID.StudyRootQueryRetrieveInformationModelMove,
            UID.PatientStudyOnlyQueryRetrieveInformationModelGet,
            UID.PatientStudyOnlyQueryRetrieveInformationModelMove
    };
    static final SPSStatus[] HIDE_SPS_WITH_STATUS_FROM_MWL = {
            SPSStatus.STARTED, SPSStatus.DEPARTED, SPSStatus.CANCELED, SPSStatus.DISCONTINUED, SPSStatus.COMPLETED
    };

    static final Code DICOM_EXPORT =
            new Code("DICOM_EXPORT", "99DCM4CHEE", null, "Export by DICOM Storage");
    static final Code DICOM_RETRIEVE =
            new Code("DICOM_RETRIEVE", "99DCM4CHEE", null, "Retrieve by DICOM Study Root Query/Retrieve Information Model - MOVE");
    static final Code REQUEST_STGCMT =
            new Code("REQUEST_STGCMT", "99DCM4CHEE", null, "Request Storage Commitment");
    static final Code SEND_IAN =
            new Code("SEND_IAN", "99DCM4CHEE", null, "Send Instance Availability Notification");
    static final Code DCM4CHEE_ARC =
            new Code("dcm4chee-arc", "99DCM4CHEE", null, "dcm4chee-arc");
    static final Code INCORRECT_WORKLIST_ENTRY_SELECTED =
            new Code("110514", "DCM", null, "Incorrect worklist entry selected");
    static final Code REJECTED_FOR_QUALITY_REASONS =
            new Code("113001", "DCM", null, "Rejected for Quality Reasons");
    static final Code REJECTED_FOR_PATIENT_SAFETY_REASONS =
            new Code("113037", "DCM", null, "Rejected for Patient Safety Reasons");
    static final Code INCORRECT_MODALITY_WORKLIST_ENTRY =
            new Code("113038", "DCM", null, "Incorrect Modality Worklist Entry");
    static final Code DATA_RETENTION_POLICY_EXPIRED =
            new Code("113039", "DCM", null, "Data Retention Policy Expired");
    static final Code REVOKE_REJECTION =
            new Code("REVOKE_REJECTION", "99DCM4CHEE", null, "Restore rejected Instances");
    static final Code[] REJECTION_CODES = {
            REJECTED_FOR_QUALITY_REASONS,
            REJECTED_FOR_PATIENT_SAFETY_REASONS,
            INCORRECT_MODALITY_WORKLIST_ENTRY,
            DATA_RETENTION_POLICY_EXPIRED
    };

    static final QueryRetrieveView REGULAR_USE_VIEW =
            createQueryRetrieveView("regularUse",
                    new Code[]{REJECTED_FOR_QUALITY_REASONS},
                    new Code[0],
                    false);
    static final QueryRetrieveView HIDE_REJECTED_VIEW =
            createQueryRetrieveView("hideRejected",
                    new Code[0],
                    new Code[]{DATA_RETENTION_POLICY_EXPIRED},
                    false);
    static final QueryRetrieveView IOCM_EXPIRED_VIEW =
            createQueryRetrieveView("dataRetentionPolicyExpired",
                    new Code[]{DATA_RETENTION_POLICY_EXPIRED},
                    new Code[0],
                    true);
    static final QueryRetrieveView IOCM_QUALITY_VIEW =
            createQueryRetrieveView("rejectedForQualityReasons",
                    new Code[]{REJECTED_FOR_QUALITY_REASONS},
                    new Code[0],
                    true);
    static final QueryRetrieveView IOCM_PAT_SAFETY_VIEW =
            createQueryRetrieveView("rejectedForPatientSafetyReasons",
                    new Code[]{REJECTED_FOR_PATIENT_SAFETY_REASONS},
                    new Code[0],
                    true);
    static final QueryRetrieveView IOCM_WRONG_MWL_VIEW =
            createQueryRetrieveView("incorrectModalityWorklistEntry",
                    new Code[]{INCORRECT_MODALITY_WORKLIST_ENTRY},
                    new Code[0],
                    true);
   static final QueryRetrieveView IOCM_DISABLED_VIEW =
            createQueryRetrieveView("iocmDisabled",
                    REJECTION_CODES,
                    new Code[0],
                    false);

    static final String USER = "user";
    static final String ONLY_ADMIN = "admin";

    static final ArchiveCompressionRule JPEG_BASELINE = createCompressionRule(
            "JPEG 8-bit Lossy",
            new Conditions(
                    "SendingApplicationEntityTitle=JPEG_LOSSY",
                    "PhotometricInterpretation=MONOCHROME1|MONOCHROME2|RGB",
                    "BitsStored=8",
                    "PixelRepresentation=0"
            ),
            UID.JPEGBaseline8Bit,
            "compressionQuality=0.8",
            "maxPixelValueError=10",
            "avgPixelValueBlockSize=8"
    );
    static final ArchiveCompressionRule JPEG_EXTENDED = createCompressionRule(
            "JPEG 12-bit Lossy",
            new Conditions(
                    "SendingApplicationEntityTitle=JPEG_LOSSY",
                    "PhotometricInterpretation=MONOCHROME1|MONOCHROME2",
                    "BitsStored=9|10|11|12",
                    "PixelRepresentation=0"
            ),
            UID.JPEGExtended12Bit,
            "compressionQuality=0.8",
            "maxPixelValueError=20",
            "avgPixelValueBlockSize=8"
    );
    static final ArchiveCompressionRule JPEG_LOSSLESS = createCompressionRule(
            "JPEG Lossless",
            new Conditions(
                    "SendingApplicationEntityTitle=JPEG_LOSSLESS"
            ),
            UID.JPEGLossless,
            "maxPixelValueError=0"
    );
    static final ArchiveCompressionRule JPEG_LS = createCompressionRule(
            "JPEG LS Lossless",
            new Conditions(
                    "SendingApplicationEntityTitle=JPEG_LS"
            ),
            UID.JPEGLSLossless,
            "maxPixelValueError=0"
    );
    static final ArchiveCompressionRule JPEG_LS_LOSSY = createCompressionRule(
            "JPEG LS Lossy",
            new Conditions(
                    "SendingApplicationEntityTitle=JPEG_LS_LOSSY"
            ),
            UID.JPEGLSNearLossless,
            "maxPixelValueError=2"
    );
    static final ArchiveCompressionRule JPEG_2000 = createCompressionRule(
            "JPEG 2000 Lossless",
            new Conditions(
                    "SendingApplicationEntityTitle=JPEG_2000"
            ),
            UID.JPEG2000Lossless,
            "maxPixelValueError=0"
    );

    static final StudyRetentionPolicy THICK_SLICE = createStudyRetentionPolicy(
            "THICK_SLICE",
            "P4D",
            1,
            false,
            new Conditions(
                    "SendingApplicationEntityTitle=STORESCU"
            )
    );

    static final StudyRetentionPolicy THIN_SLICE = createStudyRetentionPolicy(
            "THIN_SLICE",
            "P1D",
            2,
            true,
            new Conditions(
                    "SendingApplicationEntityTitle=STORESCU",
                    "SliceThickness=0.75"
            )
    );

    static final String[] HL7_MESSAGE_TYPES = {
            "ADT^A01",
            "ADT^A02",
            "ADT^A03",
            "ADT^A04",
            "ADT^A05",
            "ADT^A06",
            "ADT^A07",
            "ADT^A08",
            "ADT^A10",
            "ADT^A11",
            "ADT^A12",
            "ADT^A13",
            "ADT^A28",
            "ADT^A31",
            "ADT^A38",
            "ADT^A40",
            "ADT^A47",
            "ORM^O01",
            "OMI^O23",
            "OMG^O19",
            "ORU^R01",
            "SIU^S12",
            "SIU^S13",
            "SIU^S15"
    };

    static final String[] HL7PSU_PARAMS = {
            "RequestedProcedureID={StudyInstanceUID,hash}",
            "AccessionNumber={StudyInstanceUID,hash}",
            "PlacerOrderNumberImagingServiceRequest={StudyInstanceUID,hash}",
            "FillerOrderNumberImagingServiceRequest={StudyInstanceUID,hash}"
    };

    static final String AE_TITLE = "DCM4CHEE";
    static final String HL7_ADT2DCM_XSL = "${jboss.server.temp.url}/dcm4chee-arc/hl7-adt2dcm.xsl";
    static final String HL7_DCM2ADT_XSL = "${jboss.server.temp.url}/dcm4chee-arc/hl7-dcm2adt.xsl";
    static final String DSR2HTML_XSL = "${jboss.server.temp.url}/dcm4chee-arc/dsr2html.xsl";
    static final String DSR2TEXT_XSL = "${jboss.server.temp.url}/dcm4chee-arc/dsr2text.xsl";
    static final String CDA2HTML_XSL = "/dcm4chee-arc/xsl/cda.xsl";
    static final String HL7_ORU2DSR_XSL = "${jboss.server.temp.url}/dcm4chee-arc/hl7-oru2dsr.xsl";
    static final String HL7_ORDER2DCM_XSL = "${jboss.server.temp.url}/dcm4chee-arc/hl7-order2dcm.xsl";
    static final String UNZIP_VENDOR_DATA = "${jboss.server.temp.url}/dcm4chee-arc";
    static final String NULLIFY_PN = "xslt:${jboss.server.temp.url}/dcm4chee-arc/nullify-pn.xsl";
    static final String CORRECT_VR = "xslt:${jboss.server.temp.url}/dcm4chee-arc/correct-vr.xsl";
    static final String ENSURE_PID = "xslt:${jboss.server.temp.url}/dcm4chee-arc/ensure-pid.xsl";
    static final String MERGE_MWL = "merge-mwl:${jboss.server.temp.url}/dcm4chee-arc/mwl2series.xsl";
    static final String MERGE_MWL_STUDY = "merge-mwl:${jboss.server.temp.url}/dcm4chee-arc/mwl2study.xsl";
    static final String MERGE_MWL_MPPS = "merge-mwl:${jboss.server.temp.url}/dcm4chee-arc/mwl2mpps.xsl";
    static final String COERCE_MWL_AGFA2ARC = "xslt:${jboss.server.temp.url}/dcm4chee-arc/mwl-agfa2arc.xsl";
    static final String AUDIT2JSONFHIR_XSL = "${jboss.server.temp.url}/dcm4chee-arc/audit2json+fhir.xsl";
    static final String AUDIT2XMLFHIR_XSL = "${jboss.server.temp.url}/dcm4chee-arc/audit2xml+fhir.xsl";
    static final String MPPS2HL7_PSU_XSL = "${jboss.server.temp.url}/dcm4chee-arc/mpps2hl7-psu.xsl";
    static final String STUDY2HL7_PSU_XSL = "${jboss.server.temp.url}/dcm4chee-arc/dcm2hl7-psu.xsl";
    static final String AUDIT_LOGGER_SPOOL_DIR_URI = "${jboss.server.temp.url}";
    static final String PIX_CONSUMER = "DCM4CHEE|DCM4CHEE";
    static final String PIX_MANAGER = "HL7RCV|DCM4CHEE";
    static final String STORAGE_ID = "fs1";
    static final String STORAGE_URI = "${jboss.server.data.url}/fs1/";
    static final String NEARLINE_STORAGE_ID = "nearline";
    static final String NEARLINE_STORAGE_URI = "${jboss.server.data.url}/nearline/";
    static final String NEARLINE_PATH_FORMAT = "{now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}";
    static final String METADATA_STORAGE_ID = "metadata";
    static final String METADATA_STORAGE_URI = "${jboss.server.data.url}/metadata/";
    static final String METADATA_PATH_FORMAT = "{now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}.json";
    static final String SERIES_METADATA_STORAGE_ID = "series-metadata";
    static final String SERIES_METADATA_STORAGE_URI = "${jboss.server.data.url}/series-metadata/";
    static final String SERIES_METADATA_PATH_FORMAT = "{now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{now,date,HHmmss}.zip";
    static final Duration SERIES_METADATA_DELAY = Duration.valueOf("PT2M");
    static final Duration SERIES_METADATA_POLLING_INTERVAL = Duration.valueOf("PT1M");
    static final String WADO_JPEG_STORAGE_ID = "wado-jpeg";
    static final String WADO_JPEG_STORAGE_URI = "${jboss.server.data.url}/wado/";
    static final String WADO_JPEG_PATH_FORMAT = "{0020000D}/{0020000E}/{00080018}/{00081160}.jpeg";
    static final String WADO_JSON_STORAGE_ID = "wado-json";
    static final String QIDO_JSON_STORAGE_ID = "qido-json";
    static final String WADO_JSON_STORAGE_URI = "${jboss.server.data.url}/wado/";
    static final String QIDO_JSON_STORAGE_URI = "${jboss.server.data.url}/qido/";
    static final String WADO_JSON_PATH_FORMAT = "{0020000D}-{0020000E}.json";
    static final String QIDO_JSON_PATH_FORMAT = "{0020000D}-{0020000E}.json";
    static final boolean SEND_PENDING_C_GET = true;
    static final Duration SEND_PENDING_C_MOVE_INTERVAL = Duration.valueOf("PT5S");
    static final Duration DIFF_TASK_UPDATE_INTERVAL = Duration.valueOf("PT10S");
    static final int QIDO_MAX_NUMBER_OF_RESULTS = 1000;
    static final Duration IAN_TASK_POLLING_INTERVAL = Duration.valueOf("PT1M");
    static final Duration PURGE_QUEUE_MSG_POLLING_INTERVAL = Duration.valueOf("PT1H");
    static final Duration CALCULATE_STUDY_SIZE_DELAY = Duration.valueOf("PT5M");
    static final Duration CALCULATE_STUDY_SIZE_POLLING_INTERVAL = Duration.valueOf("PT5M");

    static final String NEARLINE_STORAGE_EXPORTER_ID = "CopyToNearlineStorage";
    static final String NEARLINE_STORAGE_EXPORTER_DESC = "Copy to NEARLINE Storage";
    static final URI NEARLINE_STORAGE_EXPORTER_URI = URI.create("storage:nearline");
    static final Duration NEARLINE_STORAGE_DELAY = Duration.valueOf("PT1M");

    static final String DICOM_EXPORTER_ID = "STORESCP";
    static final String DICOM_EXPORTER_DESC = "Export to STORESCP";
    static final URI DICOM_EXPORT_URI = URI.create("dicom:STORESCP");
    static final String WADO_JPEG_EXPORTER_ID = "WADO-JPEG";
    static final String WADO_JSON_EXPORTER_ID = "WADO-JSON";
    static final String QIDO_JSON_EXPORTER_ID = "QIDO-JSON";
    static final String WADO_JPEG_EXPORTER_DESC = "Export to WADO-JPEG";
    static final String WADO_JSON_EXPORTER_DESC = "Export to WADO-JSON";
    static final String QIDO_JSON_EXPORTER_DESC = "Export to QIDO-JSON";
    static final URI WADO_EXPORT_URI = URI.create("wado:DCM4CHEE-WADO");
    static final String WADO_JPEG_EXPORT_SERVICE = "?requestType=WADO&studyUID=[0]&seriesUID=[1]&objectUID=[2]&frameNumber=[3]";
    static final String WADO_CACHE_CONTROL = "no-cache";
    static final URI WADO_JSON_EXPORT_URL = URI.create("wado:DCM4CHEE");
    static final String WADO_JSON_EXPORT_SERVICE = "/studies/[0]/series/[1]/metadata";
    static final String QIDO_JSON_EXPORT_SERVICE = "/studies/[0]/series/[1]/instances";
    static final String WADO_JSON_ACCEPT = "application/json";
    static final String XDSI_EXPORTER_ID = "XDS-I";
    static final String XDSI_EXPORTER_DESC = "XDS-I Provide and Register";
    static final URI XDSI_EXPORT_URI = URI.create("xds-i:https://localhost:9443/xdstools4/sim/default__rr/rep/prb");
    static final String XDSI_TLS_PROTOCOL = "TLSv1.2" ;
    static final String XDSI_TLS_CIPHERSUITES = "TLS_RSA_WITH_AES_128_CBC_SHA, SSL_RSA_WITH_3DES_EDE_CBC_SHA";
    static final String XDSI_SOURCE_ID = "1.3.6.1.4.1.21367.13.80.110";
    static final String XDSI_ASSIGNING_AUTHORITY = "1.3.6.1.4.1.21367.2005.13.20.1000";
    static final String XDSI_LANGUAGE_CODE = "en-us";
    static final Code XDSI_KON_TYPECODE = new Code(
            "DICOM KON TYPECODE",
            "1.3.6.1.4.1.21367.100.1",
            null,
            "DICOM Key Object Note Type Code");
    static final Code XDSI_CLASS_CODE = new Code(
            "*",
            "1.3.6.1.4.1.21367.100.1",
            null,
            "*");
    static final Code XDSI_CONFIDENTIALITY_CODE = new Code(
            "N",
            "2.16.840.1.113883.5.25",
            null,
            "Normal");
    static final Code XDSI_MANIFEST_TITLE = new Code(
            "113030",
            "DCM",
            null,
            "Manifest");
    static final Code XDSI_HEALTH_CARE_FACILITY_TYPE_CODE = new Code(
            "RADDX",
            "2.16.840.1.113883.5.11",
            null,
            "Radiology diagnostics or therapeutics unit");
    static final Code XDSI_PRACTICE_SETTING_CODE = new Code(
            "R-3027B",
            "SRT",
            null,
            "Radiology");
    static final Duration TASK_POLLING_INTERVAL = Duration.valueOf("PT1M");
    static final Duration UPS_PROCESSING_POLLING_INTERVAL = Duration.valueOf("PT1M");
    static final Duration DELETE_UPS_POLLING_INTERVAL = Duration.valueOf("PT1H");
    static final Duration DELETE_UPS_CANCELED_DELAY = Duration.valueOf("P7D");
    static final Duration DELETE_UPS_COMPLETED_DELAY = Duration.valueOf("P1D");
    static final Duration PURGE_STORAGE_POLLING_INTERVAL = Duration.valueOf("PT5M");
    static final Duration DELETE_STUDY_INTERVAL = Duration.valueOf("P1D");
    static final Duration DELETE_REJECTED_POLLING_INTERVAL = Duration.valueOf("PT5M");
    static final String AUDIT_SPOOL_DIR =  "${jboss.server.data.dir}/audit-spool";
    static final Duration AUDIT_POLLING_INTERVAL = Duration.valueOf("PT1M");
    static final Duration AUDIT_AGGREGATE_DURATION = Duration.valueOf("PT1M");
    static final Duration DELETE_REJECTED_INSTANCE_DELAY = Duration.valueOf("P1D");
    static final Duration MAX_ACCESS_TIME_STALENESS = Duration.valueOf("PT5M");
    static final Duration AE_CACHE_STALE_TIMEOUT = Duration.valueOf("PT5M");
    static final Duration LEADING_C_FIND_SCP_QUERY_CACHE_STALE_TIMEOUT = Duration.valueOf("PT5M");
    static final Duration REJECT_EXPIRED_STUDIES_POLLING_INTERVAL = Duration.valueOf("P1D");
    static final int REJECT_EXPIRED_STUDIES_SERIES_FETCH_SIZE = 10;
    static final Duration PURGE_STGCMT_COMPLETED_DELAY = Duration.valueOf("P1D");
    static final Duration PURGE_STGCMT_POLLING_INTERVAL = Duration.valueOf("PT1H");
    static final String BULK_DATA_DESCRIPTOR_ID = "default";
    static final String BULK_DATA_LENGTH_THRESHOLD = "DS,FD,FL,IS,LT,OB,OD,OF,OL,OW,UC,UN,UR,UT=1024";

    static {
        System.setProperty("jboss.server.data.url", "file:///opt/wildfly/standalone/data");
        System.setProperty("jboss.server.temp.url", "file:///opt/wildfly/standalone/tmp");
    }

    public static Device createARRDevice(ConfigType configType) {
        Device arrDevice = new Device("logstash");
        AuditRecordRepository arr = new AuditRecordRepository();
        arrDevice.addDeviceExtension(arr);

        String syslogHost = configType == ConfigType.DOCKER ? "syslog-host" : "localhost";
        Connection syslog = new Connection("syslog", syslogHost, 514);
        syslog.setProtocol(Connection.Protocol.SYSLOG_UDP);
        arrDevice.addConnection(syslog);
        arr.addConnection(syslog);

        Connection syslogTLS = new Connection("syslog-tls", syslogHost, 6514);
        syslogTLS.setProtocol(Connection.Protocol.SYSLOG_TLS);
        syslogTLS.setTlsCipherSuites(
                Connection.TLS_RSA_WITH_AES_128_CBC_SHA,
                Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
        syslogTLS.setInstalled(Boolean.FALSE);
        arrDevice.addConnection(syslogTLS);
        arr.addConnection(syslogTLS);

        arrDevice.setPrimaryDeviceTypes("LOG");
        return arrDevice ;
    }

    public static Device qualifyDevice(Device device, String primaryDeviceType, Issuer issuer, Code institutionCode) {
        if (primaryDeviceType != null)
            device.setPrimaryDeviceTypes(primaryDeviceType);
        device.setIssuerOfPatientID(issuer);
        device.setIssuerOfAccessionNumber(issuer);
        device.setIssuerOfAdmissionID(issuer);
        if (institutionCode != null) {
            device.setInstitutionNames(institutionCode.getCodeMeaning());
            device.setInstitutionCodes(institutionCode);
        }
        return device;
    }

    public static Device createOtherDevice(int i) {
        return ArchiveDeviceFactory.qualifyDevice(i < OTHER_AES.length
                    ? ArchiveDeviceFactory.createDevice(
                            ArchiveDeviceFactory.OTHER_DEVICES[i],
                            ArchiveDeviceFactory.OTHER_AES[i],
                            "localhost",
                            ArchiveDeviceFactory.OTHER_PORTS[i << 1],
                            ArchiveDeviceFactory.OTHER_PORTS[(i << 1) + 1])
                    : new Device(ArchiveDeviceFactory.OTHER_DEVICES[i]),
                ArchiveDeviceFactory.OTHER_DEVICE_TYPES[i],
                ArchiveDeviceFactory.OTHER_ISSUER[i],
                ArchiveDeviceFactory.OTHER_INST_CODES[i]);
    }

    public static Device createDevice(String name, String aet, String host, int port, int tlsPort) {
        Device device = new Device(name);
        ApplicationEntity ae = new ApplicationEntity(aet);
        ae.setAssociationAcceptor(true);
        device.addApplicationEntity(ae);
        Connection dicom = new Connection("dicom", host, port);
        device.addConnection(dicom);
        ae.addConnection(dicom);
        if (tlsPort > -2) {
            Connection dicomTLS = new Connection("dicom-tls", host, tlsPort);
            dicomTLS.setTlsCipherSuites(
                    Connection.TLS_RSA_WITH_AES_128_CBC_SHA,
                    Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
            device.addConnection(dicomTLS);
            ae.addConnection(dicomTLS);
        }
        return device;
    }

    public static Device createStoreSCPDevice(ConfigType configType) {
        Device device = createDevice("storescp", "STORESCP",
                configType == ConfigType.DOCKER ? "storescp-host" : "localhost", 11117, -2);
        ApplicationEntity ae = device.getApplicationEntity("STORESCP");
        addTC(ae, null, SCP, UID.Verification, UID.ImplicitVRLittleEndian);
        String[][] CUIDS = { IMAGE_CUIDS, PRIVATE_IMAGE_CUIDS, SR_CUIDS, OTHER_CUIDS, PRIVATE_CUIDS };
        for (int i = 0; i < CUIDS.length; i++) {
            addTCs(ae, null, SCP, CUIDS[i], OTHER_TSUIDS);
        }
        addTCs(ae, null, SCP, VIDEO_CUIDS, VIDEO_TSUIDS);
        return device;
    }

    public static Device createStowRSDevice() {
        Device device = new Device("stowrsd");
        Connection http = new Connection("http", "localhost", 18080);
        http.setProtocol(Connection.Protocol.HTTP);
        device.addConnection(http);
        WebApplication webapp = new WebApplication("stowrsd");
        webapp.setServicePath("/stowrs");
        webapp.setServiceClasses(WebApplication.ServiceClass.STOW_RS);
        webapp.addConnection(http);
        device.addWebApplication(webapp);
        device.setPrimaryDeviceTypes("ARCHIVE");
        return device ;
    }

    public static Device createHL7Device(String name, String appName, String host, int port, int tlsPort) {
        Device device = new Device(name);
        HL7DeviceExtension hl7Device = new HL7DeviceExtension();
        device.addDeviceExtension(hl7Device);
        HL7Application hl7app = new HL7Application(appName);
        hl7Device.addHL7Application(hl7app);
        Connection hl7 = new Connection("hl7", host, port);
        hl7.setProtocol(Connection.Protocol.HL7);
        device.addConnection(hl7);
        hl7app.addConnection(hl7);
        Connection hl7TLS = new Connection("hl7-tls", host, tlsPort);
        hl7TLS.setProtocol(Connection.Protocol.HL7);
        hl7TLS.setTlsCipherSuites(
                Connection.TLS_RSA_WITH_AES_128_CBC_SHA,
                Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
        device.addConnection(hl7TLS);
        hl7app.addConnection(hl7TLS);
        return device;
    }

    public static Device createKeycloakDevice(String name, Device arrDevice, ConfigType configType) {
        Device device = new Device(name);
        String keycloakHost = configType == ConfigType.DOCKER ? "keycloak-host" : "localhost";
        device.setInstalled(true);
        device.setPrimaryDeviceTypes("AUTH");
        addAuditLoggerDeviceExtension(device, arrDevice, keycloakHost);
        configType.configureKeyAndTrustStore(device);
        return device;
    }

    public static Device createArchiveDevice(String name, ConfigType configType, Device arrDevice,
                                             Device scheduledStation, Device storescu, Device mppsscu)  {
        Device device = new Device(name);
        String archiveHost = configType == ConfigType.DOCKER ? "archive-host" : "localhost";
        Connection dicom = dicomConnection("dicom", archiveHost, 11112);
        device.addConnection(dicom);

        Connection dicomTLS = dicomConnection("dicom-tls", archiveHost, 2762);
        dicomTLS.setTlsCipherSuites(
                Connection.TLS_RSA_WITH_AES_128_CBC_SHA,
                Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
        device.addConnection(dicomTLS);;

        addArchiveDeviceExtension(device, configType, storescu, mppsscu, scheduledStation);
        addHL7DeviceExtension(device, configType, archiveHost);
        addAuditLoggerDeviceExtension(device, arrDevice, archiveHost, suppressAuditQueryFromArchive());
        device.addDeviceExtension(new ImageReaderExtension(ImageReaderFactory.getDefault()));
        device.addDeviceExtension(new ImageWriterExtension(ImageWriterFactory.getDefault()));

        device.setManufacturer("dcm4che.org");
        device.setManufacturerModelName("dcm4chee-arc");
        device.setPrimaryDeviceTypes("ARCHIVE");

        configType.configureKeyAndTrustStore(device);

        device.addApplicationEntity(createAE(AE_TITLE, AE_TITLE_DESC,
                dicom, dicomTLS, HIDE_REJECTED_VIEW,
                true, true, true, true, false, true, true, false,
                new ArchiveAttributeCoercion2[] {
                    new ArchiveAttributeCoercion2()
                        .setCommonName("SupplementIssuerOfPatientID")
                        .setDIMSE(Dimse.C_STORE_RQ)
                        .setRole(SCU)
                        .setURI("merge-attrs:")
                        .setConditions(new Conditions("IssuerOfPatientID!=.+"))
                        .setMergeAttributes("IssuerOfPatientID=DCM4CHEE.{PatientName,hash}.{PatientBirthDate,hash}"),
                    new ArchiveAttributeCoercion2()
                        .setCommonName("SupplementIssuerOfPatientIDOnMPPS")
                        .setDIMSE(Dimse.N_CREATE_RQ)
                        .setSOPClasses(UID.ModalityPerformedProcedureStep)
                        .setRole(SCU)
                        .setURI("merge-attrs:")
                        .setConditions(new Conditions("IssuerOfPatientID!=.+"))
                        .setMergeAttributes("IssuerOfPatientID=DCM4CHEE.{PatientName,hash}.{PatientBirthDate,hash}")
                },
                configType));
        device.addApplicationEntity(createAE("WORKLIST", WORKLIST_DESC,
                dicom, dicomTLS, HIDE_REJECTED_VIEW,
                false, false, false, false, true, true, true, true,
                new ArchiveAttributeCoercion2[] {
                        new ArchiveAttributeCoercion2()
                                .setCommonName("SupplementIssuerOfPatientIDOnMPPS")
                                .setDIMSE(Dimse.N_CREATE_RQ)
                                .setSOPClasses(UID.ModalityPerformedProcedureStep)
                                .setRole(SCU)
                                .setURI("merge-attrs:")
                                .setConditions(new Conditions("IssuerOfPatientID!=.+"))
                                .setMergeAttributes("IssuerOfPatientID=DCM4CHEE.{PatientName,hash}.{PatientBirthDate,hash}")
                },
                configType));
        device.addApplicationEntity(createAE("IOCM_REGULAR_USE", IOCM_REGULAR_USE_DESC,
                dicom, dicomTLS, REGULAR_USE_VIEW,
                false, true, true, true, false, false, false, false, null,
                configType));
        device.addApplicationEntity(createAE("IOCM_EXPIRED", IOCM_EXPIRED_DESC,
                dicom, dicomTLS, IOCM_EXPIRED_VIEW,
                false, false, false, true, false, false, false, false, null,
                configType));
        device.addApplicationEntity(createAE("IOCM_QUALITY", IOCM_QUALITY_DESC,
                dicom, dicomTLS, IOCM_QUALITY_VIEW,
                false, false, false, true, false, false, false, false, null,
                configType));
        device.addApplicationEntity(createAE("IOCM_PAT_SAFETY", IOCM_PAT_SAFETY_DESC,
                dicom, dicomTLS, IOCM_PAT_SAFETY_VIEW,
                false, false, false, true, false, false, false, false, null,
                configType));
        device.addApplicationEntity(createAE("IOCM_WRONG_MWL", IOCM_WRONG_MWL_DESC,
                dicom, dicomTLS, IOCM_WRONG_MWL_VIEW,
                false, false, false, true, false, false, false, false, null,
                configType));
        device.addApplicationEntity(createAE("AS_RECEIVED", AS_RECEIVED_DESC,
                dicom, dicomTLS, IOCM_DISABLED_VIEW,
                false, true, false, true, false, false, false, false,
                new ArchiveAttributeCoercion2[] {
                    new ArchiveAttributeCoercion2()
                        .setCommonName("RetrieveAsReceived")
                        .setDIMSE(Dimse.C_STORE_RQ)
                        .setRole(SCP)
                        .setURI(ArchiveAttributeCoercion2.RETRIEVE_AS_RECEIVED + ":")
                },
                configType));

        WebApplication webapp = createWebApp("DCM4CHEE", AE_TITLE_DESC,
                "/dcm4chee-arc/aets/DCM4CHEE/rs", AE_TITLE, null,
                WebApplication.ServiceClass.MPPS_RS,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.QIDO_COUNT,
                WebApplication.ServiceClass.STOW_RS,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET);
        device.addWebApplication(webapp);
        if (configType == configType.DOCKER) {
            webapp.setProperty("IID_PATIENT_URL", "");
            webapp.setProperty("IID_STUDY_URL", "");
            webapp.setProperty("IID_URL_TARGET", "");
        }

        device.addWebApplication(createWebApp("DCM4CHEE-WADO", AE_TITLE_DESC,
                "/dcm4chee-arc/aets/DCM4CHEE/wado", AE_TITLE, null,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("WORKLIST", WORKLIST_DESC,
                "/dcm4chee-arc/aets/WORKLIST/rs", "WORKLIST", null,
                WebApplication.ServiceClass.UPS_RS,
                WebApplication.ServiceClass.UPS_MATCHING,
                WebApplication.ServiceClass.MWL_RS,
                WebApplication.ServiceClass.MPPS_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("IOCM_REGULAR_USE", IOCM_REGULAR_USE_DESC,
                "/dcm4chee-arc/aets/IOCM_REGULAR_USE/rs", "IOCM_REGULAR_USE", ONLY_ADMIN,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.QIDO_COUNT,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("IOCM_REGULAR_USE-WADO", IOCM_REGULAR_USE_DESC,
                "/dcm4chee-arc/aets/IOCM_REGULAR_USE/wado", "IOCM_REGULAR_USE", ONLY_ADMIN,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("IOCM_EXPIRED", IOCM_EXPIRED_DESC,
                "/dcm4chee-arc/aets/IOCM_EXPIRED/rs", "IOCM_EXPIRED", ONLY_ADMIN,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.QIDO_COUNT,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("IOCM_EXPIRED-WADO", IOCM_EXPIRED_DESC,
                "/dcm4chee-arc/aets/IOCM_EXPIRED/wado", "IOCM_EXPIRED", ONLY_ADMIN,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("IOCM_QUALITY", IOCM_QUALITY_DESC,
                "/dcm4chee-arc/aets/IOCM_QUALITY/rs", "IOCM_QUALITY", ONLY_ADMIN,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.QIDO_COUNT,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("IOCM_QUALITY-WADO", IOCM_QUALITY_DESC,
                "/dcm4chee-arc/aets/IOCM_QUALITY/wado", "IOCM_QUALITY", ONLY_ADMIN,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("IOCM_PAT_SAFETY", IOCM_PAT_SAFETY_DESC,
                "/dcm4chee-arc/aets/IOCM_PAT_SAFETY/rs", "IOCM_PAT_SAFETY", ONLY_ADMIN,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.QIDO_COUNT,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("IOCM_PAT_SAFETY-WADO", IOCM_PAT_SAFETY_DESC,
                "/dcm4chee-arc/aets/IOCM_PAT_SAFETY/wado", "IOCM_PAT_SAFETY", ONLY_ADMIN,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("IOCM_WRONG_MWL", IOCM_WRONG_MWL_DESC,
                "/dcm4chee-arc/aets/IOCM_WRONG_MWL/rs", "IOCM_WRONG_MWL", ONLY_ADMIN,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.QIDO_COUNT,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("IOCM_WRONG_MWL-WADO", IOCM_WRONG_MWL_DESC,
                "/dcm4chee-arc/aets/IOCM_WRONG_MWL/wado", "IOCM_WRONG_MWL", ONLY_ADMIN,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("AS_RECEIVED", AS_RECEIVED_DESC,
                "/dcm4chee-arc/aets/AS_RECEIVED/rs", "AS_RECEIVED", ONLY_ADMIN,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.QIDO_COUNT,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("AS_RECEIVED-WADO", AS_RECEIVED_DESC,
                "/dcm4chee-arc/aets/AS_RECEIVED/wado", "AS_RECEIVED", ONLY_ADMIN,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("dcm4chee-arc", "Forward Reschedule Task(s)",
                "/dcm4chee-arc", null, null,
                WebApplication.ServiceClass.DCM4CHEE_ARC));
        return device;
    }

    private static Connection dicomConnection(String commonName, String hostname, int port) {
        Connection dicom = new Connection(commonName, hostname, port);
        dicom.setBindAddress("0.0.0.0");
        dicom.setClientBindAddress("0.0.0.0");
        dicom.setMaxOpsInvoked(0);
        dicom.setMaxOpsPerformed(0);
        dicom.setConnectTimeout(5000);
        dicom.setRequestTimeout(5000);
        dicom.setAcceptTimeout(5000);
        dicom.setReleaseTimeout(5000);
        dicom.setSendTimeout(5000);
        dicom.setStoreTimeout(300000);
        dicom.setResponseTimeout(5000);
        dicom.setRetrieveTimeout(300000);
        dicom.setIdleTimeout(300000);
        dicom.setAbortTimeout(5000);
        return dicom;
    }

    private static void addImageWriterParam(ImageWriterFactory writerFactory, String tsuid, Property prop) {
        ImageWriterFactory.ImageWriterParam param = writerFactory.remove(tsuid);
        writerFactory.put(tsuid, new ImageWriterFactory.ImageWriterParam(
                param.formatName, param.className, param.patchJPEGLS, cat(param.getImageWriteParams(), prop)));
    }

    private static Property[] cat(Property[] src, Property prop) {
        Property[] dst = new Property[src.length + 1];
        System.arraycopy(src, 0, dst, 0, src.length);
        dst[src.length] = prop;
        return dst;
    }

    private static WebApplication createWebApp(
            String name, String desc, String path, String aet, String acceptedUserRoles,
            WebApplication.ServiceClass... serviceClasses) {
        WebApplication webapp = new WebApplication(name);
        webapp.setDescription(desc);
        webapp.setServicePath(path);
        webapp.setAETitle(aet);
        webapp.setServiceClasses(serviceClasses);
        if (acceptedUserRoles != null) webapp.setProperty("roles", acceptedUserRoles);
        return webapp;
    }

    private static QueryRetrieveView createQueryRetrieveView(
            String viewID, Code[] showInstancesRejectedByCodes, Code[] hideRejectionNoteCodes,
            boolean hideNotRejectedInstances) {

        QueryRetrieveView view = new QueryRetrieveView();
        view.setViewID(viewID);
        view.setShowInstancesRejectedByCodes(showInstancesRejectedByCodes);
        view.setHideRejectionNotesWithCodes(hideRejectionNoteCodes);
        view.setHideNotRejectedInstances(hideNotRejectedInstances);
        return view;
    }

    private static ArchiveCompressionRule createCompressionRule(String cn, Conditions conditions, String tsuid,
                                                         String... writeParams) {
        ArchiveCompressionRule rule = new ArchiveCompressionRule(cn);
        rule.setConditions(conditions);
        rule.setTransferSyntax(tsuid);
        rule.setImageWriteParams(Property.valueOf(writeParams));
        return rule;
    }

    private static StudyRetentionPolicy createStudyRetentionPolicy(String cn, String retentionPeriod,
                                          int priority, boolean expireSeriesIndividually, Conditions conditions) {
        StudyRetentionPolicy policy = new StudyRetentionPolicy(cn);
        policy.setRetentionPeriod(Period.parse(retentionPeriod));
        policy.setExpireSeriesIndividually(expireSeriesIndividually);
        policy.setPriority(priority);
        policy.setConditions(conditions);
        return policy;
    }

    private static void addAuditLoggerDeviceExtension(Device device, Device arrDevice, String hostname,
                                                      AuditSuppressCriteria... suppressCriteria) {
        Connection syslog = new Connection("syslog", hostname);
        syslog.setClientBindAddress("0.0.0.0");
        syslog.setProtocol(Connection.Protocol.SYSLOG_UDP);
        device.addConnection(syslog);

        Connection syslogTLS = new Connection("syslog-tls", hostname);
        syslogTLS.setClientBindAddress("0.0.0.0");
        syslogTLS.setProtocol(Connection.Protocol.SYSLOG_TLS);
        syslogTLS.setTlsCipherSuites(
                Connection.TLS_RSA_WITH_AES_128_CBC_SHA,
                Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
        syslogTLS.setInstalled(Boolean.FALSE);
        device.addConnection(syslogTLS);

        AuditLoggerDeviceExtension ext = new AuditLoggerDeviceExtension();
        AuditLogger auditLogger = new AuditLogger("Audit Logger");
        auditLogger.addConnection(syslog);
        auditLogger.addConnection(syslogTLS);
        auditLogger.setAuditSourceTypeCodes("4");
        auditLogger.setAuditRecordRepositoryDevice(arrDevice);
        auditLogger.setSpoolDirectoryURI(AUDIT_LOGGER_SPOOL_DIR_URI);
        auditLogger.getAuditSuppressCriteriaList().addAll(Arrays.asList(suppressCriteria));
        ext.addAuditLogger(auditLogger);
        device.addDeviceExtension(ext);
    }

    static HL7OrderScheduledStation newScheduledStation(Device scheduledStation) {
        HL7OrderScheduledStation ss = new HL7OrderScheduledStation();
        ss.setCommonName("Default Scheduled Station");
        ss.setDevice(scheduledStation);
        return ss;
    }

    private static void addHL7DeviceExtension(Device device, ConfigType configType, String archiveHost) {
        HL7DeviceExtension ext = new HL7DeviceExtension();
        device.addDeviceExtension(ext);

        HL7Application hl7App = new HL7Application("*");
        hl7App.setDescription("Default HL7 Receiver");
        ArchiveHL7ApplicationExtension hl7AppExt = new ArchiveHL7ApplicationExtension();
        hl7AppExt.setAETitle(AE_TITLE);
        hl7App.addHL7ApplicationExtension(hl7AppExt);
        hl7App.setAcceptedMessageTypes(HL7_MESSAGE_TYPES);
        hl7App.setHL7DefaultCharacterSet("8859/1");
        hl7App.setHL7SendingCharacterSet("8859/1");
        ext.addHL7Application(hl7App);

        Connection hl7 = hl7Connection("hl7", archiveHost, 2575);
        device.addConnection(hl7);
        hl7App.addConnection(hl7);

        Connection hl7TLS = hl7Connection("hl7-tls", archiveHost, 12575);
        hl7TLS.setTlsCipherSuites(
                Connection.TLS_RSA_WITH_AES_128_CBC_SHA,
                Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
        device.addConnection(hl7TLS);
        hl7App.addConnection(hl7TLS);
    }

    private static Connection hl7Connection(String commonName, String hostname, int port) {
        Connection hl7 = new Connection(commonName, hostname, port);
        hl7.setBindAddress("0.0.0.0");
        hl7.setClientBindAddress("0.0.0.0");
        hl7.setProtocol(Connection.Protocol.HL7);
        hl7.setConnectTimeout(5000);
        hl7.setResponseTimeout(5000);
        hl7.setIdleTimeout(300000);
        return hl7;
    }

    private static void addArchiveDeviceExtension(Device device, ConfigType configType,
                                                  Device storescu, Device mppsscu, Device scheduledStation) {
        ArchiveDeviceExtension ext = new ArchiveDeviceExtension();
        device.addDeviceExtension(ext);
        ext.setFuzzyAlgorithmClass("org.dcm4che3.soundex.ESoundex");
        ext.setOverwritePolicy(OverwritePolicy.SAME_SOURCE);
        ext.setExternalRetrieveAEDestination(AE_TITLE);
        ext.setXDSiImagingDocumentSourceAETitle(AE_TITLE);
        ext.addQueryRetrieveView(HIDE_REJECTED_VIEW);
        ext.addQueryRetrieveView(REGULAR_USE_VIEW);
        ext.addQueryRetrieveView(IOCM_EXPIRED_VIEW);
        ext.addQueryRetrieveView(IOCM_PAT_SAFETY_VIEW);
        ext.addQueryRetrieveView(IOCM_QUALITY_VIEW);
        ext.addQueryRetrieveView(IOCM_WRONG_MWL_VIEW);
        ext.addQueryRetrieveView(IOCM_DISABLED_VIEW);

        BasicBulkDataDescriptor bulkDataDescriptor = new BasicBulkDataDescriptor(BULK_DATA_DESCRIPTOR_ID);
        bulkDataDescriptor.setLengthsThresholdsFromStrings(BULK_DATA_LENGTH_THRESHOLD);
        ext.addBulkDataDescriptor(bulkDataDescriptor);
        ext.setBulkDataDescriptorID(BULK_DATA_DESCRIPTOR_ID);

        ext.setSendPendingCGet(SEND_PENDING_C_GET);
        ext.setSendPendingCMoveInterval(SEND_PENDING_C_MOVE_INTERVAL);
        ext.setDiffTaskProgressUpdateInterval(DIFF_TASK_UPDATE_INTERVAL);
        ext.setWadoSupportedSRClasses(SR_CUIDS);
        ext.setWadoSupportedPRClasses(UID.GrayscaleSoftcopyPresentationStateStorage);
        ext.setWadoSR2HtmlTemplateURI(DSR2HTML_XSL);
        ext.setWadoSR2TextTemplateURI(DSR2TEXT_XSL);
        ext.setWadoCDA2HtmlTemplateURI(CDA2HTML_XSL);
        ext.setPatientUpdateTemplateURI(HL7_ADT2DCM_XSL);
        ext.setOutgoingPatientUpdateTemplateURI(HL7_DCM2ADT_XSL);
        ext.setHl7PSUMppsTemplateURI(MPPS2HL7_PSU_XSL);
        ext.setHl7PSUStudyTemplateURI(STUDY2HL7_PSU_XSL);
        ext.setHL7PSUTemplateParams(HL7PSU_PARAMS);
        ext.setUnzipVendorDataToURI(UNZIP_VENDOR_DATA);
        ext.setQidoMaxNumberOfResults(QIDO_MAX_NUMBER_OF_RESULTS);
        ext.setIanTaskPollingInterval(IAN_TASK_POLLING_INTERVAL);
        ext.setPurgeTaskPollingInterval(PURGE_QUEUE_MSG_POLLING_INTERVAL);
        ext.setPurgeStoragePollingInterval(PURGE_STORAGE_POLLING_INTERVAL);
        ext.setDeleteStudyInterval(DELETE_STUDY_INTERVAL);
        ext.setDeleteRejectedPollingInterval(DELETE_REJECTED_POLLING_INTERVAL);
        ext.setPurgeStgCmtCompletedDelay(PURGE_STGCMT_COMPLETED_DELAY);
        ext.setPurgeStgCmtPollingInterval(PURGE_STGCMT_POLLING_INTERVAL);
        ext.setAuditSpoolDirectory(AUDIT_SPOOL_DIR);
        ext.setAuditPollingInterval(AUDIT_POLLING_INTERVAL);
        ext.setAuditAggregateDuration(AUDIT_AGGREGATE_DURATION);
        ext.setImportReportTemplateURI(HL7_ORU2DSR_XSL);
        ext.setAECacheStaleTimeout(AE_CACHE_STALE_TIMEOUT);
        ext.setLeadingCFindSCPQueryCacheStaleTimeout(LEADING_C_FIND_SCP_QUERY_CACHE_STALE_TIMEOUT);
        ext.setScheduleProcedureTemplateURI(HL7_ORDER2DCM_XSL);
        ext.setStorageVerificationAETitle(AE_TITLE);
        ext.setCompressionAETitle(AE_TITLE);
        ext.setStudySizeDelay(CALCULATE_STUDY_SIZE_DELAY);
        ext.setCalculateStudySizePollingInterval(CALCULATE_STUDY_SIZE_POLLING_INTERVAL);
        ext.setCalculateQueryAttributes(true);

        ext.setTaskPollingInterval(TASK_POLLING_INTERVAL);

        ext.setUPSProcessingPollingInterval(UPS_PROCESSING_POLLING_INTERVAL);
        ext.setDeleteUPSPollingInterval(DELETE_UPS_POLLING_INTERVAL);
        ext.setDeleteUPSCanceledDelay(DELETE_UPS_CANCELED_DELAY);
        ext.setDeleteUPSCompletedDelay(DELETE_UPS_COMPLETED_DELAY);

        ext.addUPSProcessingRule(newUPSProcessingRule(
                "DICOM_EXPORT", DICOM_EXPORT, DCM4CHEE_ARC, "storescu:STORESCP"));
        ext.addUPSProcessingRule(newUPSProcessingRule(
                "DICOM_RETRIEVE", DICOM_RETRIEVE, DCM4CHEE_ARC, "movescu:DCM4CHEE"));
        ext.addUPSProcessingRule(newUPSProcessingRule(
                "REQUEST_STGCMT", REQUEST_STGCMT, DCM4CHEE_ARC, "stgcmtscu:DCMQRSCP"));
        ext.addUPSProcessingRule(newUPSProcessingRule(
                "SEND_IAN", SEND_IAN, DCM4CHEE_ARC, "ianscu:IANSCP"));

        ext.setRejectExpiredStudiesPollingInterval(REJECT_EXPIRED_STUDIES_POLLING_INTERVAL);
        ext.setRejectExpiredStudiesAETitle(AE_TITLE);
        ext.setRejectExpiredStudiesFetchSize(REJECT_EXPIRED_STUDIES_SERIES_FETCH_SIZE);
        ext.setRejectExpiredSeriesFetchSize(REJECT_EXPIRED_STUDIES_SERIES_FETCH_SIZE);

        ext.setAudit2JsonFhirTemplateURI(AUDIT2JSONFHIR_XSL);
        ext.setAudit2XmlFhirTemplateURI(AUDIT2XMLFHIR_XSL);

        ext.setAttributeFilter(Entity.Patient, newAttributeFilter(PATIENT_ATTRS, Attributes.UpdatePolicy.SUPPLEMENT));
        ext.setAttributeFilter(Entity.Study, newAttributeFilter(STUDY_ATTRS, Attributes.UpdatePolicy.SUPPLEMENT));
        ext.setAttributeFilter(Entity.Series, newAttributeFilter(SERIES_ATTRS, Attributes.UpdatePolicy.SUPPLEMENT));
        ext.setAttributeFilter(Entity.Instance, new AttributeFilter(INSTANCE_ATTRS));
        ext.setAttributeFilter(Entity.MPPS, new AttributeFilter(MPPS_ATTRS));
        ext.setAttributeFilter(Entity.MWL, new AttributeFilter(MWL_ATTRS));
        ext.setAttributeFilter(Entity.UPS, new AttributeFilter(UPS_ATTRS));

        ext.setHL7PatientArrivalMessageType("ADT^A10");
        ext.addHL7OrderScheduledStation(newScheduledStation(scheduledStation));

        ext.addIDGenerator(newIDGenerator("PatientID", "P-%08d"));
        ext.addIDGenerator(newIDGenerator("AccessionNumber", "A-%08d"));
        ext.addIDGenerator(newIDGenerator("RequestedProcedureID", "RP-%08d"));
        ext.addIDGenerator(newIDGenerator("ScheduledProcedureStepID", "SPS-%08d"));

        StorageDescriptor storageDescriptor = new StorageDescriptor(STORAGE_ID);
        storageDescriptor.setStorageURIStr(STORAGE_URI);
        storageDescriptor.setCheckMountFilePath("NO_MOUNT");
        storageDescriptor.setDigestAlgorithm("MD5");
        storageDescriptor.setInstanceAvailability(Availability.ONLINE);
        ext.addStorageDescriptor(storageDescriptor);

        for (QueueDescriptor descriptor : QUEUE_DESCRIPTORS)
            ext.addQueueDescriptor(descriptor);

        for (MetricsDescriptor descriptor : METRICS_DESCRIPTORS)
            ext.addMetricsDescriptor(descriptor);

        for (HL7OrderSPSStatus hl7OrderSPSStatus : HL7_ORDER_SPS_STATUSES)
            ext.addHL7OrderSPSStatus(hl7OrderSPSStatus);

        ext.addAttributeSet(newAttributeSet(AttributeSet.Type.DIFF_RS,
                1, "study",
                "Study attributes",
                "Compares only Study attributes",
                DIFF_STUDY_ATTRS,
                "groupButtons=export,reject,reimport",
                "actions=study-reject,study-export,study-reimport"));
        ext.addAttributeSet(newAttributeSet(AttributeSet.Type.DIFF_RS,
                2, "patient",
                "Patient attributes",
                "Compares only Patient attributes",
                DIFF_PAT_ATTRS,
                "groupButtons=synchronize",
                "actions=patient-update"));
        ext.addAttributeSet(newAttributeSet(AttributeSet.Type.DIFF_RS,
                3, "accno",
                "Request attributes",
                "Compares Request attributes",
                DIFF_ACCESSION_NUMBER,
                "groupButtons=export,reject,reimport",
                "actions=study-reject,study-export,study-reimport"));
        ext.addAttributeSet(newAttributeSet(AttributeSet.Type.DIFF_RS,
                4, "all",
                "Patient and Study attributes",
                "Compares Patient and Study attributes",
                union(DIFF_PAT_ATTRS, DIFF_STUDY_ATTRS)));
        ext.addAttributeSet(newAttributeSet(AttributeSet.Type.WADO_RS,
                0, "AttributeFilters",
                "Attribute Filters",
                null,
                union(PATIENT_ATTRS, STUDY_ATTRS, SERIES_ATTRS, INSTANCE_ATTRS)));
        ext.addAttributeSet(newAttributeSet(AttributeSet.Type.LEADING_CFIND_SCP,
                0, "*",
                "Default",
                null,
                LEADING_CFIND_SCP_ATTRS));
        ext.addAttributeSet(newAttributeSet(AttributeSet.Type.QIDO_RS,
                0, "study",
                "Sample Study Attribute Set",
                null,
                QIDO_STUDY_ATTRS));

        ext.addRejectionNote(createRejectionNote("Quality",
                RejectionNote.Type.REJECTED_FOR_QUALITY_REASONS,
                REJECTED_FOR_QUALITY_REASONS,
                RejectionNote.AcceptPreviousRejectedInstance.IGNORE));
        ext.addRejectionNote(createRejectionNote("Patient Safety",
                RejectionNote.Type.REJECTED_FOR_PATIENT_SAFETY_REASONS,
                REJECTED_FOR_PATIENT_SAFETY_REASONS,
                RejectionNote.AcceptPreviousRejectedInstance.REJECT,
                REJECTED_FOR_QUALITY_REASONS));
        ext.addRejectionNote(createRejectionNote("Incorrect MWL Entry",
                RejectionNote.Type.INCORRECT_MODALITY_WORKLIST_ENTRY,
                INCORRECT_MODALITY_WORKLIST_ENTRY,
                RejectionNote.AcceptPreviousRejectedInstance.REJECT,
                REJECTED_FOR_QUALITY_REASONS, REJECTED_FOR_PATIENT_SAFETY_REASONS));
        RejectionNote retentionExpired = createRejectionNote("Retention Expired",
                RejectionNote.Type.DATA_RETENTION_POLICY_EXPIRED,
                DATA_RETENTION_POLICY_EXPIRED,
                RejectionNote.AcceptPreviousRejectedInstance.RESTORE,
                REJECTED_FOR_QUALITY_REASONS, REJECTED_FOR_PATIENT_SAFETY_REASONS, INCORRECT_MODALITY_WORKLIST_ENTRY);
        retentionExpired.setDeleteRejectedInstanceDelay(DELETE_REJECTED_INSTANCE_DELAY);
        retentionExpired.setDeleteRejectionNoteDelay(DELETE_REJECTED_INSTANCE_DELAY);
        ext.addRejectionNote(retentionExpired);
        ext.addRejectionNote(createRejectionNote("Revoke Rejection",
                RejectionNote.Type.REVOKE_REJECTION,
                REVOKE_REJECTION, RejectionNote.AcceptPreviousRejectedInstance.REJECT,
                REJECTION_CODES));
        ext.setHideSPSWithStatusFrom(HIDE_SPS_WITH_STATUS_FROM_MWL);
        ext.setHideSPSWithStatusFromMWLRS(HIDE_SPS_WITH_STATUS_FROM_MWL);
        ext.setRejectionNoteStorageAET(AE_TITLE);

        if (configType == configType.SAMPLE) {
            StorageDescriptor metadataStorageDescriptor = new StorageDescriptor(METADATA_STORAGE_ID);
            metadataStorageDescriptor.setStorageURIStr(METADATA_STORAGE_URI);
            metadataStorageDescriptor.setStoragePathFormat(METADATA_PATH_FORMAT);
            metadataStorageDescriptor.setCheckMountFilePath("NO_MOUNT");
            ext.addStorageDescriptor(metadataStorageDescriptor);

            StorageDescriptor seriesMetadataStorageDescriptor = new StorageDescriptor(SERIES_METADATA_STORAGE_ID);
            seriesMetadataStorageDescriptor.setStorageURIStr(SERIES_METADATA_STORAGE_URI);
            seriesMetadataStorageDescriptor.setStoragePathFormat(SERIES_METADATA_PATH_FORMAT);
            seriesMetadataStorageDescriptor.setCheckMountFilePath("NO_MOUNT");
            ext.addStorageDescriptor(seriesMetadataStorageDescriptor);
            ext.setSeriesMetadataStorageIDs(SERIES_METADATA_STORAGE_ID);
            ext.setSeriesMetadataDelay(SERIES_METADATA_DELAY);
            ext.setSeriesMetadataPollingInterval(SERIES_METADATA_POLLING_INTERVAL);

            StorageDescriptor wadoJpegStorageDescriptor = new StorageDescriptor(WADO_JPEG_STORAGE_ID);
            wadoJpegStorageDescriptor.setStorageURIStr(WADO_JPEG_STORAGE_URI);
            wadoJpegStorageDescriptor.setStoragePathFormat(WADO_JPEG_PATH_FORMAT);
            wadoJpegStorageDescriptor.setCheckMountFilePath("NO_MOUNT");
            ext.addStorageDescriptor(wadoJpegStorageDescriptor);

            StorageDescriptor wadoJsonStorageDescriptor = new StorageDescriptor(WADO_JSON_STORAGE_ID);
            wadoJsonStorageDescriptor.setStorageURIStr(WADO_JSON_STORAGE_URI);
            wadoJsonStorageDescriptor.setStoragePathFormat(WADO_JSON_PATH_FORMAT);
            wadoJsonStorageDescriptor.setCheckMountFilePath("NO_MOUNT");
            ext.addStorageDescriptor(wadoJsonStorageDescriptor);

            StorageDescriptor qidoJsonStorageDescriptor = new StorageDescriptor(QIDO_JSON_STORAGE_ID);
            qidoJsonStorageDescriptor.setStorageURIStr(QIDO_JSON_STORAGE_URI);
            qidoJsonStorageDescriptor.setStoragePathFormat(QIDO_JSON_PATH_FORMAT);
            qidoJsonStorageDescriptor.setCheckMountFilePath("NO_MOUNT");
            ext.addStorageDescriptor(qidoJsonStorageDescriptor);

            StorageDescriptor nearlineStorageDescriptor = new StorageDescriptor(NEARLINE_STORAGE_ID);
            nearlineStorageDescriptor.setStorageURIStr(NEARLINE_STORAGE_URI);
            nearlineStorageDescriptor.setStoragePathFormat(NEARLINE_PATH_FORMAT);
            nearlineStorageDescriptor.setCheckMountFilePath("NO_MOUNT");
            nearlineStorageDescriptor.setInstanceAvailability(Availability.NEARLINE);
            ext.addStorageDescriptor(nearlineStorageDescriptor);

            ext.addQueueDescriptor(
                    newQueueDescriptor("WADO QIDO Export", "WADO QIDO Export Tasks", true));
            ext.addQueueDescriptor(
                    newQueueDescriptor("XDS-I Export", "XDS-I Export Tasks", true));
            ext.addQueueDescriptor(
                    newQueueDescriptor("Nearline Storage Export", "Nearline Storage Export Tasks", true));

            ExporterDescriptor nearlineExporter = new ExporterDescriptor(NEARLINE_STORAGE_EXPORTER_ID);
            nearlineExporter.setDescription(NEARLINE_STORAGE_EXPORTER_DESC);
            nearlineExporter.setExportURI(NEARLINE_STORAGE_EXPORTER_URI);
            nearlineExporter.setQueueName("Nearline Storage Export");
            nearlineExporter.setAETitle(AE_TITLE);
            ext.addExporterDescriptor(nearlineExporter);

            ExportRule nearlineStorageRule = new ExportRule(NEARLINE_STORAGE_EXPORTER_DESC);
            nearlineStorageRule.getConditions().setSendingAETitle("NEARLINE");
            nearlineStorageRule.setEntity(Entity.Series);
            nearlineStorageRule.setExportDelay(NEARLINE_STORAGE_DELAY);
            nearlineStorageRule.setExporterIDs(NEARLINE_STORAGE_EXPORTER_ID);
            ext.addExportRule(nearlineStorageRule);

            ExporterDescriptor dicomExporter = new ExporterDescriptor(DICOM_EXPORTER_ID);
            dicomExporter.setDescription(DICOM_EXPORTER_DESC);
            dicomExporter.setExportURI(DICOM_EXPORT_URI);
            dicomExporter.setQueueName("Export");
            dicomExporter.setAETitle(AE_TITLE);
            ext.addExporterDescriptor(dicomExporter);

            ExportRule exportRule = new ExportRule("Forward to STORESCP");
            exportRule.getConditions().setSendingAETitle("FORWARD");
            exportRule.getConditions().setCondition("Modality", "CT|MR");
            exportRule.setEntity(Entity.Series);
            exportRule.setExportDelay(Duration.valueOf("PT1M"));
            exportRule.setExporterIDs(DICOM_EXPORTER_ID);
            ext.addExportRule(exportRule);

            ExporterDescriptor wadoJpegExportDescriptor = new ExporterDescriptor(WADO_JPEG_EXPORTER_ID);
            wadoJpegExportDescriptor.setDescription(WADO_JPEG_EXPORTER_DESC);
            wadoJpegExportDescriptor.setExportURI(WADO_EXPORT_URI);
            wadoJpegExportDescriptor.setQueueName("WADO QIDO Export");
            wadoJpegExportDescriptor.setAETitle(AE_TITLE);
            wadoJpegExportDescriptor.setProperty("WadoService", WADO_JPEG_EXPORT_SERVICE);
            wadoJpegExportDescriptor.setProperty("Cache-Control", WADO_CACHE_CONTROL);
            wadoJpegExportDescriptor.setProperty("StorageID", WADO_JPEG_STORAGE_ID);
            ext.addExporterDescriptor(wadoJpegExportDescriptor);

            ExporterDescriptor wadoJsonExportDescriptor = new ExporterDescriptor(WADO_JSON_EXPORTER_ID);
            wadoJsonExportDescriptor.setDescription(WADO_JSON_EXPORTER_DESC);
            wadoJsonExportDescriptor.setExportURI(WADO_JSON_EXPORT_URL);
            wadoJsonExportDescriptor.setQueueName("WADO QIDO Export");
            wadoJsonExportDescriptor.setAETitle(AE_TITLE);
            wadoJsonExportDescriptor.setProperty("WadoService", WADO_JSON_EXPORT_SERVICE);
            wadoJsonExportDescriptor.setProperty("Accept", WADO_JSON_ACCEPT);
            wadoJpegExportDescriptor.setProperty("Cache-Control", WADO_CACHE_CONTROL);
            wadoJsonExportDescriptor.setProperty("StorageID", WADO_JSON_STORAGE_ID);
            ext.addExporterDescriptor(wadoJsonExportDescriptor);

            ExporterDescriptor qidoJsonExportDescriptor = new ExporterDescriptor(QIDO_JSON_EXPORTER_ID);
            qidoJsonExportDescriptor.setDescription(QIDO_JSON_EXPORTER_DESC);
            qidoJsonExportDescriptor.setExportURI(WADO_JSON_EXPORT_URL);
            qidoJsonExportDescriptor.setQueueName("WADO QIDO Export");
            qidoJsonExportDescriptor.setAETitle(AE_TITLE);
            qidoJsonExportDescriptor.setProperty("QidoService", QIDO_JSON_EXPORT_SERVICE);
            qidoJsonExportDescriptor.setProperty("Accept", WADO_JSON_ACCEPT);
            qidoJsonExportDescriptor.setProperty("StorageID", QIDO_JSON_STORAGE_ID);
            ext.addExporterDescriptor(qidoJsonExportDescriptor);

            ExportRule wadoJpegExportRule = new ExportRule("Forward to WADO-JPEG");
            wadoJpegExportRule.getConditions().setSendingAETitle("WADO_JPEG");
            wadoJpegExportRule.setEntity(Entity.Series);
            wadoJpegExportRule.setExportDelay(Duration.valueOf("PT1M"));
            wadoJpegExportRule.setExporterIDs(WADO_JPEG_EXPORTER_ID);
            ext.addExportRule(wadoJpegExportRule);

            ExportRule wadoJsonExportRule = new ExportRule("Forward to WADO-JSON");
            wadoJsonExportRule.getConditions().setSendingAETitle("WADO_JSON");
            wadoJsonExportRule.setEntity(Entity.Series);
            wadoJsonExportRule.setExportDelay(Duration.valueOf("PT1M"));
            wadoJsonExportRule.setExporterIDs(WADO_JSON_EXPORTER_ID);
            ext.addExportRule(wadoJsonExportRule);

            ExportRule qidoJsonExportRule = new ExportRule("Forward to QIDO-JSON");
            qidoJsonExportRule.getConditions().setSendingAETitle("QIDO_JSON");
            qidoJsonExportRule.setEntity(Entity.Series);
            qidoJsonExportRule.setExportDelay(Duration.valueOf("PT1M"));
            qidoJsonExportRule.setExporterIDs(QIDO_JSON_EXPORTER_ID);
            ext.addExportRule(qidoJsonExportRule);

            ExporterDescriptor xdsiExportDescriptor = new ExporterDescriptor(XDSI_EXPORTER_ID);
            xdsiExportDescriptor.setDescription(XDSI_EXPORTER_DESC);
            xdsiExportDescriptor.setExportURI(XDSI_EXPORT_URI);
            xdsiExportDescriptor.setQueueName("XDS-I Export");
            xdsiExportDescriptor.setAETitle(AE_TITLE);
            xdsiExportDescriptor.setRetrieveAETitles(AE_TITLE);
            xdsiExportDescriptor.setRetrieveLocationUID(XDSI_SOURCE_ID);
            xdsiExportDescriptor.setProperty("TLS.protocol", XDSI_TLS_PROTOCOL);
            xdsiExportDescriptor.setProperty("TLS.ciphersuites", XDSI_TLS_CIPHERSUITES);
            xdsiExportDescriptor.setProperty("TLS.disableCNCheck", String.valueOf(true));
            xdsiExportDescriptor.setProperty("Manifest.title", XDSI_MANIFEST_TITLE.toString());
            xdsiExportDescriptor.setProperty("AssigningAuthority.patientId", XDSI_ASSIGNING_AUTHORITY);
            xdsiExportDescriptor.setProperty("AssigningAuthority.accessionNumber", XDSI_ASSIGNING_AUTHORITY);
            xdsiExportDescriptor.setProperty("XDSSubmissionSet.sourceId", XDSI_SOURCE_ID);
            xdsiExportDescriptor.setProperty("XDSSubmissionSet.contentType", XDSI_KON_TYPECODE.toString());
            xdsiExportDescriptor.setProperty("DocumentEntry.typeCode", XDSI_KON_TYPECODE.toString());
            xdsiExportDescriptor.setProperty("DocumentEntry.languageCode", XDSI_LANGUAGE_CODE);
            xdsiExportDescriptor.setProperty("DocumentEntry.classCode", XDSI_CLASS_CODE.toString());
            xdsiExportDescriptor.setProperty("DocumentEntry.confidentialityCode", XDSI_CONFIDENTIALITY_CODE.toString());
            xdsiExportDescriptor.setProperty("DocumentEntry.healthCareFacilityTypeCode", XDSI_HEALTH_CARE_FACILITY_TYPE_CODE.toString());
            xdsiExportDescriptor.setProperty("DocumentEntry.practiceSettingCode", XDSI_PRACTICE_SETTING_CODE.toString());
            xdsiExportDescriptor.setProperty("DocumentEntry.includeModalityCodes", String.valueOf(true));
            xdsiExportDescriptor.setProperty("DocumentEntry.includeAnatomicRegionCodes", String.valueOf(true));
            xdsiExportDescriptor.setProperty("DocumentEntry.useProcedureCodeAsTypeCode", String.valueOf(false));
            ext.addExporterDescriptor(xdsiExportDescriptor);

            HL7ForwardRule hl7ForwardRule = new HL7ForwardRule("Forward to HL7RCV|DCM4CHEE");
            hl7ForwardRule.getConditions().setCondition("MSH-3", "FORWARD");
            hl7ForwardRule.setDestinations(PIX_MANAGER);
            ext.addHL7ForwardRule(hl7ForwardRule);

            ext.addCompressionRule(JPEG_BASELINE);
            ext.addCompressionRule(JPEG_EXTENDED);
            ext.addCompressionRule(JPEG_LOSSLESS);
            ext.addCompressionRule(JPEG_LS);
            ext.addCompressionRule(JPEG_LS_LOSSY);
            ext.addCompressionRule(JPEG_2000);

            ext.addStudyRetentionPolicy(THICK_SLICE);
            ext.addStudyRetentionPolicy(THIN_SLICE);

            ext.addAttributeCoercion2(new ArchiveAttributeCoercion2()
                    .setCommonName("Ensure PID")
                    .setURI(ENSURE_PID)
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCU)
                    .setSendingAETitle("ENSURE_PID")
                    .setCoercionParam("xsl-no-keyword", "true"));

            ext.addAttributeCoercion2(new ArchiveAttributeCoercion2()
                    .setCommonName("Merge MWL")
                    .setURI(MERGE_MWL)
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCU)
                    .setSendingAETitle("MERGE_MWL")
                    .setCoercionParam("match-by", MergeMWLMatchingKey.StudyInstanceUID.name())
                    .setCoercionParam("xsl-no-keyword", "true"));

            ext.addAttributeCoercion2(new ArchiveAttributeCoercion2()
                    .setCommonName("Merge MWL Study")
                    .setURI(MERGE_MWL_STUDY)
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCU)
                    .setSendingAETitle("MERGE_MWL_STUDY")
                    .setCoercionParam("match-by", MergeMWLMatchingKey.AccessionNumber.name())
                    .setCoercionParam("xsl-no-keyword", "true"));

            ext.addAttributeCoercion2(new ArchiveAttributeCoercion2()
                    .setCommonName("Merge MWL MPPS")
                    .setURI(MERGE_MWL_STUDY)
                    .setDIMSE(Dimse.N_CREATE_RQ)
                    .setRole(SCU)
                    .setSendingAETitle("MERGE_MWL_MPPS")
                    .setCoercionParam("match-by", MergeMWLMatchingKey.AccessionNumber.name())
                    .setCoercionParam("mwl-entity-to-merge", "mpps")
                    .setCoercionParam("xsl-no-keyword", "true"));

            ext.addAttributeCoercion2(new ArchiveAttributeCoercion2()
                    .setCommonName("Nullify PN")
                    .setURI(NULLIFY_PN)
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCP)
                    .setReceivingAETitle("NULLIFY_PN")
                    .setCoercionParam("xsl-no-keyword", "true"));

            ext.addAttributeCoercion2(new ArchiveAttributeCoercion2()
                    .setCommonName("Correct VR")
                    .setURI(CORRECT_VR)
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCP)
                    .setReceivingAETitle("CORRECT_VR")
                    .setCoercionParam("xsl-no-keyword", "true"));

            ext.addAttributeCoercion2(new ArchiveAttributeCoercion2()
                    .setCommonName("Leading DCMQRSCP STORE")
                    .setURI("leading-arc:DCMQRSCP")
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCP)
                    .setReceivingAETitle("LEADING_DCMQRSCP"));

            ext.addAttributeCoercion2(new ArchiveAttributeCoercion2()
                    .setCommonName("Leading DCMQRSCP FIND")
                    .setURI("leading-arc:DCMQRSCP")
                    .setDIMSE(Dimse.C_FIND_RSP)
                    .setRole(SCU)
                    .setSendingAETitle("LEADING_DCMQRSCP"));

            ext.addAttributeCoercion2(new ArchiveAttributeCoercion2()
                    .setCommonName("Supplement Composite")
                    .setURI("sup-from-dev:")
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCU)
                    .setSendingAETitle("STORESCU")
                    .setOtherDevice(storescu));

            ext.addAttributeCoercion2(new ArchiveAttributeCoercion2()
                    .setCommonName("Supplement MPPS")
                    .setURI("sup-from-dev:")
                    .setDIMSE(Dimse.N_CREATE_RQ)
                    .setRole(SCU)
                    .setSendingAETitle("MPPSSCU")
                    .setSOPClasses(UID.ModalityPerformedProcedureStep)
                    .setOtherDevice(mppsscu));

            ext.addAttributeCoercion2(new ArchiveAttributeCoercion2()
                    .setCommonName("Coerce MWL Agfa to Archive")
                    .setURI(COERCE_MWL_AGFA2ARC)
                    .setDIMSE(Dimse.C_FIND_RSP)
                    .setRole(SCP)
                    .setSOPClasses(UID.ModalityWorklistInformationModelFind)
                    .setReceivingAETitle("AGFA_WL"));

            StoreAccessControlIDRule storeAccessControlIDRule =
                    new StoreAccessControlIDRule("StoreAccessControlIDRule1");
            storeAccessControlIDRule.getConditions().setSendingAETitle("ACCESS_CONTROL");
            storeAccessControlIDRule.setStoreAccessControlID("ACCESS_CONTROL_ID");
            ext.addStoreAccessControlIDRule(storeAccessControlIDRule);
        }
    }

    private static UPSProcessingRule newUPSProcessingRule(
            String ruleID, Code workItemCode, Code stationNameCode, String uri) {
        UPSProcessingRule rule = new UPSProcessingRule(ruleID);
        rule.setAETitle(AE_TITLE);
        rule.setScheduledWorkitemCode(workItemCode);
        rule.setPerformedWorkitemCode(workItemCode);
        rule.setPerformedStationNameCode(stationNameCode);
        rule.setUPSProcessorURI(URI.create(uri));
        return rule;
    }

    private static UPSOnStore newUPSOnStore(String upsOnStoreID, String procedureStepLabel, Code workItemCode,
            String instanceUIDBasedOnName, Duration startDelay) {
        UPSOnStore rule = new UPSOnStore(upsOnStoreID);
        rule.setProcedureStepLabel(procedureStepLabel);
        rule.setScheduledWorkitemCode(workItemCode);
        rule.setIncludeInputInformation(UPSOnStore.IncludeInputInformation.APPEND_OR_CREATE);
        rule.setInstanceUIDBasedOnName(instanceUIDBasedOnName);
        rule.setStartDateTimeDelay(startDelay);
        return rule;
    }

    private static AttributeSet newAttributeSet(
            AttributeSet.Type type, int number, String id, String title, String desc, int[] tags, String... props) {
        AttributeSet attributeSet = new AttributeSet();
        attributeSet.setType(type);
        attributeSet.setID(id);
        attributeSet.setTitle(title);
        attributeSet.setNumber(number);
        attributeSet.setDescription(desc);
        attributeSet.setSelection(tags);
        attributeSet.setProperties(props);
        return attributeSet;
    }

    private static int[] union(int[]... srcs) {
        Set<Integer> c = new TreeSet<>();
        for (int[] src : srcs)
            for (int i : src)
                c.add(i);

        int[] a = new int[c.size()];
        int j = 0;
        for (int i : c)
            a[j++] = i;

        return a;
    }

    private static AttributeFilter newAttributeFilter(int[] patientAttrs, Attributes.UpdatePolicy attrUpdate) {
        AttributeFilter filter = new AttributeFilter(patientAttrs);
        filter.setAttributeUpdatePolicy(attrUpdate);
        return filter;
    }

    private static RejectionNote createRejectionNote(
            String label, RejectionNote.Type type, Code code,
            RejectionNote.AcceptPreviousRejectedInstance acceptPreviousRejectedInstance,
            Code... overwritePreviousRejection) {
        RejectionNote rjNote = new RejectionNote();
        rjNote.setRejectionNoteLabel(label);
        rjNote.setRejectionNoteType(type);
        rjNote.setRejectionNoteCode(code);
        rjNote.setAcceptPreviousRejectedInstance(acceptPreviousRejectedInstance);
        rjNote.setOverwritePreviousRejection(overwritePreviousRejection);
        return rjNote;
    }

    private static ApplicationEntity createAE(String aet, String description,
                                              Connection dicom, Connection dicomTLS, QueryRetrieveView qrView,
                                              boolean storeSCP, boolean storeSCU, boolean ianSCU, boolean querySCP,
                                              boolean mwlSCP, boolean mppsSCP, boolean mppsSCU, boolean upsSCP,
                                              ArchiveAttributeCoercion2[] coercions, ConfigType configType,
                                              String... acceptedUserRoles) {
        ApplicationEntity ae = new ApplicationEntity(aet);
        ae.setDescription(description);
        ae.addConnection(dicom);
        if (dicomTLS != null)
            ae.addConnection(dicomTLS);

        ArchiveAEExtension aeExt = new ArchiveAEExtension();
        ae.addAEExtension(aeExt);
        ae.setAssociationAcceptor(true);
        ae.setAssociationInitiator(true);
        addTC(ae, null, SCP, UID.Verification, UID.ImplicitVRLittleEndian);
        addTC(ae, null, SCU, UID.Verification, UID.ImplicitVRLittleEndian);
        EnumSet<QueryOption> allQueryOpts = EnumSet.allOf(QueryOption.class);
        if (querySCP) {
            addTCs(ae, allQueryOpts, SCP, QUERY_CUIDS, UID.ImplicitVRLittleEndian);
        }
        if (mwlSCP) {
            addTC(ae, allQueryOpts, SCP,
                    UID.ModalityWorklistInformationModelFind,
                    UID.ImplicitVRLittleEndian);
        }
        if (upsSCP) {
            addTCs(ae, allQueryOpts, SCP, new String[] {
                            UID.UnifiedProcedureStepPull,
                            UID.UnifiedProcedureStepWatch,
                            UID.UnifiedProcedureStepQuery
                    },
                    UID.ImplicitVRLittleEndian);
            addTCs(ae, null, SCP, new String[]{
                            UID.UnifiedProcedureStepPush,
                            UID.UnifiedProcedureStepEvent
                    },
                    UID.ImplicitVRLittleEndian);
        }
        String[][] CUIDS = { IMAGE_CUIDS, PRIVATE_IMAGE_CUIDS, VIDEO_CUIDS, SR_CUIDS, OTHER_CUIDS, PRIVATE_CUIDS };
        String[][] TSUIDS = { IMAGE_TSUIDS, IMAGE_TSUIDS, VIDEO_TSUIDS, SR_TSUIDS, OTHER_TSUIDS, OTHER_TSUIDS };
        if (storeSCU) {
            addTCs(ae, EnumSet.of(QueryOption.RELATIONAL), SCP, RETRIEVE_CUIDS, UID.ImplicitVRLittleEndian);
            for (int i = 0; i < CUIDS.length; i++)
                addTCs(ae, null, SCU, CUIDS[i], TSUIDS[i]);
            addTC(ae, null, SCU, UID.StorageCommitmentPushModel, UID.ImplicitVRLittleEndian);
        }
        if (storeSCP) {
            for (int i = 0; i < CUIDS.length; i++)
                addTCs(ae, null, SCP, CUIDS[i], TSUIDS[i]);
            addTC(ae, null, SCP, UID.StorageCommitmentPushModel, UID.ImplicitVRLittleEndian);
            if (configType == ConfigType.SAMPLE)
                aeExt.setMetadataStorageIDs(METADATA_STORAGE_ID);
            aeExt.setObjectStorageIDs(STORAGE_ID);
        }
        if (mppsSCP) {
            addTC(ae, null, SCP, UID.ModalityPerformedProcedureStep, UID.ImplicitVRLittleEndian);
        }
        if (mppsSCU) {
            addTC(ae, null, SCU, UID.ModalityPerformedProcedureStep, UID.ImplicitVRLittleEndian);
        }
        if (ianSCU) {
            addTC(ae, null, SCU, UID.InstanceAvailabilityNotification, UID.ImplicitVRLittleEndian);
        }
        aeExt.setQueryRetrieveViewID(qrView.getViewID());
        aeExt.setAcceptedUserRoles(acceptedUserRoles);
        if (coercions != null)
            for (ArchiveAttributeCoercion2 coercion : coercions) {
                aeExt.addAttributeCoercion2(coercion);
            }
        return ae;
    }

    private static void addTCs(ApplicationEntity ae, EnumSet<QueryOption> queryOpts,
                        TransferCapability.Role role, String[] cuids, String... tss) {
        for (String cuid : cuids)
            addTC(ae, queryOpts, role, cuid, tss);
    }

    private static void addTC(ApplicationEntity ae, EnumSet<QueryOption> queryOpts,
                       TransferCapability.Role role, String cuid, String... tss) {
        String name = UID.nameOf(cuid).replace('/', ' ');
        TransferCapability tc = new TransferCapability(name + ' ' + role, cuid, role, tss);
        tc.setQueryOptions(queryOpts);
        ae.addTransferCapability(tc);
    }

}
