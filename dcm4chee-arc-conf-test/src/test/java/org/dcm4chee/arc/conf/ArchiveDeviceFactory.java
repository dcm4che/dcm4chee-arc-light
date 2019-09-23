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
    static final String IOCM_REGULAR_USE_DESC = "Show instances rejected for Quality Reasons";
    static final String IOCM_QUALITY_DESC = "Only show instances rejected for Quality Reasons";
    static final String IOCM_EXPIRED_DESC = "Only show instances rejected for Data Retention Expired";
    static final String IOCM_PAT_SAFETY_DESC = "Only show instances rejected for Patient Safety Reasons";
    static final String IOCM_WRONG_MWL_DESC = "Only show instances rejected for Incorrect Modality Worklist Entry";
    static final String AS_RECEIVED_DESC = "Retrieve instances as received";

    enum ConfigType {
        DEFAULT,
        SAMPLE,
        DOCKER {
            @Override
            void configureKeyAndTrustStore(Device device) {
                device.setTrustStoreURL("file://${env.TRUSTSTORE}");
                device.setTrustStoreType("JKS");
                device.setTrustStorePin("${env.TRUSTSTORE_PASSWORD}");
                device.setKeyStoreURL("file://${env.KEYSTORE}");
                device.setKeyStoreType("${env.KEYSTORE_TYPE}");
                device.setKeyStorePin("${env.KEYSTORE_PASSWORD}");
                device.setKeyStoreKeyPin("${env.KEY_PASSWORD}");
            }
        };

        void configureKeyAndTrustStore(Device device) {
            device.setTrustStoreURL("${jboss.server.config.url}/dcm4chee-arc/cacerts.jks");
            device.setTrustStoreType("JKS");
            device.setTrustStorePin("secret");
            device.setKeyStoreURL("${jboss.server.config.url}/dcm4chee-arc/key.jks");
            device.setKeyStoreType("JKS");
            device.setKeyStorePin("secret");
        }
    }
    static final String[] OTHER_DEVICES = {
            "scheduledstation",
            "dcmqrscp",
            "stgcmtscu",
            "storescp",
            "mppsscp",
            "ianscp",
            "storescu",
            "mppsscu",
            "findscu",
            "getscu",
            "movescu",
            "hl7snd"
    };
    static final String[] OTHER_DEVICE_TYPES = {
            null,
            "ARCHIVE",
            "CT",
            "WSD",
            "DSS",
            "DSS",
            "CT",
            "CT",
            "WSD",
            "WSD",
            "WSD",
            "DSS"
    };
    static final String[] OTHER_AES = {
            "SCHEDULEDSTATION",
            "DCMQRSCP",
            "STGCMTSCU",
            "STORESCP",
            "MPPSSCP",
            "IANSCP",
            "STORESCU",
            "MPPSSCU",
            "FINDSCU",
            "GETSCU",
            "MOVESCU"
    };
    static final int SCHEDULED_STATION_INDEX = 0;
    static final int STORESCU_INDEX = 6;
    static final int MPPSSCU_INDEX = 7;
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
            SITE_A, // STORESCP
            SITE_A, // MPPSSCP
            null, // IANSCP
            SITE_A, // STORESCU
            SITE_A, // MPPSSCU
            SITE_A, // FINDSCU
            SITE_A, // GETSCU
            SITE_A, // MOVESCU
            null // hl7snd
    };
    static final Code[] OTHER_INST_CODES = {
            null, // SCHEDULEDSTATION
            INST_B, // DCMQRSCP
            null, // STGCMTSCU
            null, // STORESCP
            null, // MPPSSCP
            null, // IANSCP
            INST_A, // STORESCU
            null, // MPPSSCU
            null, // FINDSCU
            null, // GETSCU
            null, // MOVESCU
            null, // hl7snd
    };
    static final int[] OTHER_PORTS = {
            104, -2, // SCHEDULEDSTATION
            11113, 2763, // DCMQRSCP
            11114, 2765, // STGCMTSCU
            11115, 2766, // STORESCP
            11116, 2767, // MPPSSCP
            11117, 2768, // IANSCP
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // STORESCU
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // MPPSSCU
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // FINDSCU
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // GETSCU
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // MOVESCU
    };

    static final QueueDescriptor[] QUEUE_DESCRIPTORS = {
        newQueueDescriptor("MPPSSCU", "MPPS Forward Tasks"),
        newQueueDescriptor("IANSCU", "IAN Tasks"),
        newQueueDescriptor("StgCmtSCP", "Storage Commitment SCP Tasks"),
        newQueueDescriptor("StgCmtSCU", "Storage Commitment SCU Tasks"),
        newQueueDescriptor("StgVerTasks", "Storage Verification Tasks"),
        newQueueDescriptor("Export1", "Dicom Export Tasks"),
        newQueueDescriptor("Export2", "WADO Export Tasks"),
        newQueueDescriptor("Export3", "XDS-I Export Tasks"),
        newQueueDescriptor("Export4", "Calculate Query Attributes and Study size Export Tasks"),
        newQueueDescriptor("Export5", "Nearline Storage Export Tasks"),
        newQueueDescriptor("Export6", "Export6"),
        newQueueDescriptor("Export7", "Export7"),
        newQueueDescriptor("Export8", "Export8"),
        newQueueDescriptor("Export9", "Export9"),
        newQueueDescriptor("Export10", "Export10"),
        newQueueDescriptor("HL7Send", "HL7 Forward Tasks"),
        newQueueDescriptor("RSClient", "RESTful Forward Tasks"),
        newQueueDescriptor("Retrieve1", "Dicom Retrieve Tasks 1"),
        newQueueDescriptor("Retrieve2", "Dicom Retrieve Tasks 2"),
        newQueueDescriptor("Retrieve3", "Dicom Retrieve Tasks 3"),
        newQueueDescriptor("Retrieve4", "Dicom Retrieve Tasks 4"),
        newQueueDescriptor("Retrieve5", "Dicom Retrieve Tasks 5"),
        newQueueDescriptor("Retrieve6", "Dicom Retrieve Tasks 6"),
        newQueueDescriptor("Retrieve7", "Dicom Retrieve Tasks 7"),
        newQueueDescriptor("Retrieve8", "Dicom Retrieve Tasks 8"),
        newQueueDescriptor("Retrieve9", "Dicom Retrieve Tasks 9"),
        newQueueDescriptor("Retrieve10", "Dicom Retrieve Tasks 10"),
        newQueueDescriptor("Retrieve11", "Dicom Retrieve Tasks 11"),
        newQueueDescriptor("Retrieve12", "Dicom Retrieve Tasks 12"),
        newQueueDescriptor("Retrieve13", "Dicom Retrieve Tasks 13"),
        newQueueDescriptor("DiffTasks", "Diff Tasks"),
        newQueueDescriptor("Rejection", "Rejection Tasks")
    };

    static final MetricsDescriptor[] METRICS_DESCRIPTORS = {
            newMetricsDescriptor("db-update-on-store","DB Update Time on Store", "ms"),
            newMetricsDescriptor("receive-from-STORESCU","Receive Data-rate from STORESCU", "MB/s"),
            newMetricsDescriptor("send-to-STORESCP","Send Data-rate to STORESCP", "MB/s"),
            newMetricsDescriptor("write-to-fs1","Write Data-rate to fs1", "MB/s"),
            newMetricsDescriptor("read-from-fs1","Read Data-rate from fs1", "MB/s"),
            newMetricsDescriptor("delete-from-fs1","Object Delete Time on fs1", "ms")
    };

    static final HL7OrderSPSStatus[] HL7_ORDER_SPS_STATUSES = {
            newHL7OrderSPSStatus("SCHEDULED", "NW_SC", "NW_IP", "XO_SC"),
            newHL7OrderSPSStatus("CANCELLED", "CA_CA"),
            newHL7OrderSPSStatus("DISCONTINUED", "DC_CA"),
            newHL7OrderSPSStatus("COMPLETED", "XO_CM")
    };

    private static QueueDescriptor newQueueDescriptor(String name, String description) {
        QueueDescriptor desc = new QueueDescriptor(name);
        desc.setDescription(description);
        desc.setJndiName("jms/queue/" + name);
        desc.setMaxRetries(10);
        desc.setRetryDelay(Duration.valueOf("PT30S"));
        desc.setRetryDelayMultiplier(200);
        desc.setMaxRetryDelay(Duration.valueOf("PT10M"));
        desc.setPurgeQueueMessageCompletedDelay(Duration.valueOf("P1D"));
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

    private static IDGenerator newIDGenerator(IDGenerator.Name name, String format) {
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
            Tag.ReasonForVisit,
            Tag.ReasonForVisitCodeSequence,
            Tag.Occupation,
            Tag.AdditionalPatientHistory,
            Tag.ServiceEpisodeID,
            Tag.ServiceEpisodeDescription,
            Tag.IssuerOfServiceEpisodeIDSequence,
            Tag.PatientSexNeutered,
            Tag.StudyInstanceUID,
            Tag.StudyID
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
            Tag.RequestAttributesSequence
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
            Tag.IssuerOfTheSpecimenIdentifierSequence
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
            Tag.ImagingServiceRequestComments
    };
    static final int[] UPS_ATTRS = {
            Tag.SpecificCharacterSet,
            Tag.SOPClassUID,
            Tag.SOPInstanceUID,
            Tag.AdmittingDiagnosesDescription,
            Tag.AdmittingDiagnosesCodeSequence,
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
            Tag.ReasonForVisit,
            Tag.ReasonForVisitCodeSequence,
            Tag.AdmissionID,
            Tag.IssuerOfAdmissionIDSequence,
            Tag.SpecialNeeds,
            Tag.PertinentDocumentsSequence,
            Tag.PertinentResourcesSequence,
            Tag.PatientState,
            Tag.PatientClinicalTrialParticipationSequence,
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
            UID.SecondaryCaptureImageStorage,
            UID.MultiFrameGrayscaleByteSecondaryCaptureImageStorage,
            UID.MultiFrameGrayscaleWordSecondaryCaptureImageStorage,
            UID.MultiFrameTrueColorSecondaryCaptureImageStorage,
            UID.XRayAngiographicImageStorage,
            UID.EnhancedXAImageStorage,
            UID.XRayRadiofluoroscopicImageStorage,
            UID.EnhancedXRFImageStorage,
            UID.XRayAngiographicBiPlaneImageStorageRetired,
            UID.XRay3DAngiographicImageStorage,
            UID.XRay3DCraniofacialImageStorage,
            UID.BreastTomosynthesisImageStorage,
            UID.BreastProjectionXRayImageStorageForPresentation,
            UID.BreastProjectionXRayImageStorageForProcessing,
            UID.IntravascularOpticalCoherenceTomographyImageStorageForPresentation,
            UID.IntravascularOpticalCoherenceTomographyImageStorageForProcessing,
            UID.NuclearMedicineImageStorage,
            UID.VLImageStorageTrialRetired,
            UID.VLMultiFrameImageStorageTrialRetired,
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
            UID.OphthalmicOpticalCoherenceTomographyBScanVolumeAnalysisStorage,
            UID.VLWholeSlideMicroscopyImageStorage,
            UID.OphthalmicThicknessMapStorage,
            UID.CornealTopographyMapStorage,
            UID.PositronEmissionTomographyImageStorage,
            UID.LegacyConvertedEnhancedPETImageStorage,
            UID.EnhancedPETImageStorage,
            UID.RTImageStorage,
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
            UID.PrivatePMODMultiframeImageStorage,
            UID.PrivateToshibaUSImageStorage
    };
    static final String[] IMAGE_TSUIDS = {
            UID.ImplicitVRLittleEndian,
            UID.ExplicitVRLittleEndian,
            UID.JPEGBaseline1,
            UID.JPEGExtended24,
            UID.JPEGLossless,
            UID.JPEGLosslessNonHierarchical14,
            UID.JPEGLSLossless,
            UID.JPEGLSLossyNearLossless,
            UID.JPEG2000LosslessOnly,
            UID.JPEG2000,
            UID.RLELossless
    };
    static final String[] VIDEO_CUIDS = {
            UID.VideoEndoscopicImageStorage,
            UID.VideoMicroscopicImageStorage,
            UID.VideoPhotographicImageStorage,
    };

    static final String[] VIDEO_TSUIDS = {
            UID.JPEGBaseline1,
            UID.MPEG2,
            UID.MPEG2MainProfileHighLevel,
            UID.MPEG4AVCH264BDCompatibleHighProfileLevel41,
            UID.MPEG4AVCH264HighProfileLevel41,
            UID.MPEG4AVCH264HighProfileLevel42For2DVideo,
            UID.MPEG4AVCH264HighProfileLevel42For3DVideo,
            UID.MPEG4AVCH264StereoHighProfileLevel42
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
            UID.PerformedImagingAgentAdministrationSRStorage
    };

    static final String[] SR_TSUIDS = {
            UID.ImplicitVRLittleEndian,
            UID.ExplicitVRLittleEndian,
            UID.DeflatedExplicitVRLittleEndian
    };

    static final String[] OTHER_CUIDS = {
            UID.StoredPrintStorageSOPClassRetired,
            UID.HardcopyGrayscaleImageStorageSOPClassRetired,
            UID.HardcopyColorImageStorageSOPClassRetired,
            UID.MRSpectroscopyStorage,
            UID.MultiFrameSingleBitSecondaryCaptureImageStorage,
            UID.StandaloneOverlayStorageRetired,
            UID.StandaloneCurveStorageRetired,
            UID.TwelveLeadECGWaveformStorage,
            UID.GeneralECGWaveformStorage,
            UID.AmbulatoryECGWaveformStorage,
            UID.HemodynamicWaveformStorage,
            UID.CardiacElectrophysiologyWaveformStorage,
            UID.BasicVoiceAudioWaveformStorage,
            UID.GeneralAudioWaveformStorage,
            UID.ArterialPulseWaveformStorage,
            UID.RespiratoryWaveformStorage,
            UID.StandaloneModalityLUTStorageRetired,
            UID.StandaloneVOILUTStorageRetired,
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
            UID.ParametricMapStorage,
            UID.RawDataStorage,
            UID.SpatialRegistrationStorage,
            UID.SpatialFiducialsStorage,
            UID.DeformableSpatialRegistrationStorage,
            UID.SegmentationStorage,
            UID.SurfaceSegmentationStorage,
            UID.TractographyResultsStorage,
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
            UID.StandalonePETCurveStorageRetired,
            UID.TextSRStorageTrialRetired,
            UID.AudioSRStorageTrialRetired,
            UID.DetailSRStorageTrialRetired,
            UID.ComprehensiveSRStorageTrialRetired,
            UID.ContentAssessmentResultsStorage,
            UID.CTPerformedProcedureProtocolStorage,
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
            UID.RTBeamsDeliveryInstructionStorage,
            UID.RTBrachyApplicationSetupDeliveryInstructionStorage,
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
            UID.PatientRootQueryRetrieveInformationModelFIND,
            UID.StudyRootQueryRetrieveInformationModelFIND,
            UID.PatientStudyOnlyQueryRetrieveInformationModelFINDRetired
    };
    static final String[] RETRIEVE_CUIDS = {
            UID.PatientRootQueryRetrieveInformationModelGET,
            UID.PatientRootQueryRetrieveInformationModelMOVE,
            UID.StudyRootQueryRetrieveInformationModelGET,
            UID.StudyRootQueryRetrieveInformationModelMOVE,
            UID.PatientStudyOnlyQueryRetrieveInformationModelGETRetired,
            UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired
    };
    static final SPSStatus[] HIDE_SPS_WITH_STATUS_FROM_MWL = {
            SPSStatus.STARTED, SPSStatus.DEPARTED, SPSStatus.CANCELLED, SPSStatus.DISCONTINUED, SPSStatus.COMPLETED
    };

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

    static final String[] USER_AND_ADMIN = { "user", "admin" };
    static final String[] ONLY_ADMIN = { "admin" };

    static final ArchiveCompressionRule JPEG_BASELINE = createCompressionRule(
            "JPEG 8-bit Lossy",
            new Conditions(
                    "SendingApplicationEntityTitle=JPEG_LOSSY",
                    "PhotometricInterpretation=MONOCHROME1|MONOCHROME2|RGB",
                    "BitsStored=8",
                    "PixelRepresentation=0"
            ),
            UID.JPEGBaseline1,
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
            UID.JPEGExtended24,
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
            UID.JPEGLSLossyNearLossless,
            "maxPixelValueError=2"
    );
    static final ArchiveCompressionRule JPEG_2000 = createCompressionRule(
            "JPEG 2000 Lossless",
            new Conditions(
                    "SendingApplicationEntityTitle=JPEG_2000"
            ),
            UID.JPEG2000LosslessOnly,
            "maxPixelValueError=0"
    );

    static final StudyRetentionPolicy THICK_SLICE = createStudyRetentionPolicy(
            "THICK_SLICE",
            "P4D",
            2,
            false,
            new Conditions(
                    "SendingApplicationEntityTitle=STORESCU",
                    "SliceThickness=1.5"
            )
    );

    static final StudyRetentionPolicy THIN_SLICE = createStudyRetentionPolicy(
            "THIN_SLICE",
            "P1D",
            1,
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
            "ORU^R01"
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
    static final String NULLIFY_PN = "${jboss.server.temp.url}/dcm4chee-arc/nullify-pn.xsl";
    static final String CORRECT_VR = "${jboss.server.temp.url}/dcm4chee-arc/correct-vr.xsl";
    static final String ENSURE_PID = "${jboss.server.temp.url}/dcm4chee-arc/ensure-pid.xsl";
    static final String MERGE_MWL = "${jboss.server.temp.url}/dcm4chee-arc/mwl2series.xsl";
    static final String AUDIT2JSONFHIR_XSL = "${jboss.server.temp.url}/dcm4chee-arc/audit2json+fhir.xsl";
    static final String AUDIT2XMLFHIR_XSL = "${jboss.server.temp.url}/dcm4chee-arc/audit2xml+fhir.xsl";
    static final String AUDIT_LOGGER_SPOOL_DIR_URI = "${jboss.server.temp.url}";
    static final String PIX_CONSUMER = "DCM4CHEE|DCM4CHEE";
    static final String PIX_MANAGER = "HL7RCV|DCM4CHEE";
    static final String STORAGE_ID = "fs1";
    static final String STORAGE_URI = "${jboss.server.data.url}/fs1/";
    static final String PATH_FORMAT = "{now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}";
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
    static final String WADO_JSON_STORAGE_URI = "${jboss.server.data.url}/wado/";
    static final String WADO_JSON_PATH_FORMAT = "{0020000D}.json";
    static final boolean SEND_PENDING_C_GET = true;
    static final Duration SEND_PENDING_C_MOVE_INTERVAL = Duration.valueOf("PT5S");
    static final Duration DIFF_TASK_UPDATE_INTERVAL = Duration.valueOf("PT10S");
    static final int QIDO_MAX_NUMBER_OF_RESULTS = 1000;
    static final Duration IAN_TASK_POLLING_INTERVAL = Duration.valueOf("PT1M");
    static final Duration PURGE_QUEUE_MSG_POLLING_INTERVAL = Duration.valueOf("PT1H");

    static final String CALC_STUDY_SIZE_EXPORTER_ID = "CalculateStudySize";
    static final String CALC_STUDY_SIZE_EXPORTER_DESC = "Calculate Study Size";
    static final URI CALC_STUDY_SIZE_EXPORTER_URI = URI.create("study-size:dummyPath");
    static final Duration CALC_STUDY_SIZE_DELAY = Duration.valueOf("PT6M");

    static final String CALC_QUERY_ATTRS_EXPORTER_ID = "CalculateQueryAttributes";
    static final String CALC_QUERY_ATTRS_EXPORTER_DESC = "Calculate Query Attributes";
    static final URI CALC_QUERY_ATTRS_EXPORTER_URI = URI.create("query-attrs:hideRejected");
    static final Duration CALC_QUERY_ATTRS_DELAY = Duration.valueOf("PT5M");

    static final String NEARLINE_STORAGE_EXPORTER_ID = "CopyToNearlineStorage";
    static final String NEARLINE_STORAGE_EXPORTER_DESC = "Copy to NEARLINE Storage";
    static final URI NEARLINE_STORAGE_EXPORTER_URI = URI.create("storage:nearline");
    static final Duration NEARLINE_STORAGE_DELAY = Duration.valueOf("PT1M");

    static final String DICOM_EXPORTER_ID = "STORESCP";
    static final String DICOM_EXPORTER_DESC = "Export to STORESCP";
    static final URI DICOM_EXPORT_URI = URI.create("dicom:STORESCP");
    static final String WADO_EXPORTER_ID = "WADO";
    static final String WADO_EXPORTER_DESC = "Export to WADO";
    static final URI WADO_EXPORT_URI = URI.create("wado:http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/wado?requestType=WADO&studyUID=[0]&seriesUID=[1]&objectUID=[2]&frameNumber=[3]");
    static final String WADO_CACHE_CONTROL = "no-cache";
    static final String WADO_JSON_EXPORT_URL = "http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies/[0]/metadata";
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
    static final Duration EXPORT_TASK_POLLING_INTERVAL = Duration.valueOf("PT1M");
    static final Duration PURGE_STORAGE_POLLING_INTERVAL = Duration.valueOf("PT5M");
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
    static final String AUDIT_RECORD_REPOSITORY_URL = "http://kibana:5601";
    static final String BULK_DATA_DESCRIPTOR_ID = "default";
    static final String BULK_DATA_LENGTH_THRESHOLD = "DS,FD,FL,IS,LT,OB,OD,OF,OL,OW,UC,UN,UR,UT=1024";

    static {
        System.setProperty("jboss.server.data.url", "file:///opt/wildfly/standalone/data");
        System.setProperty("jboss.server.temp.url", "file:///opt/wildfly/standalone/tmp");
    }

    public static Device createARRDevice(String name, Connection.Protocol protocol, int port, ConfigType configType) {
        Device arrDevice = new Device(name);
        AuditRecordRepository arr = new AuditRecordRepository();
        arrDevice.addDeviceExtension(arr);
        String syslogHost = configType == ConfigType.DOCKER ? "syslog-host" : "localhost";
        Connection syslog = new Connection("syslog", syslogHost, port);
        syslog.setProtocol(protocol);
        arrDevice.addConnection(syslog);
        arr.addConnection(syslog);
        arrDevice.setPrimaryDeviceTypes("LOG");
        return arrDevice ;
    }

    public static Device qualifyDevice(Device device, String primaryDeviceType, Issuer issuer, Code institutionCode) {
        if (primaryDeviceType != null)
            device.setPrimaryDeviceTypes(primaryDeviceType);
        device.setIssuerOfPatientID(issuer);
        device.setIssuerOfAccessionNumber(issuer);
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
        Connection dicom = new Connection("dicom", archiveHost, 11112);
        dicom.setBindAddress("0.0.0.0");
        dicom.setClientBindAddress("0.0.0.0");
        dicom.setMaxOpsInvoked(0);
        dicom.setMaxOpsPerformed(0);
        device.addConnection(dicom);

        Connection http = new Connection("http", archiveHost, 8080);
        http.setProtocol(Connection.Protocol.HTTP);
        device.addConnection(http);

        Connection dicomTLS = null;
        Connection https = null;
        if (configType == configType.SAMPLE) {
            dicomTLS = new Connection("dicom-tls", archiveHost, 2762);
            dicomTLS.setBindAddress("0.0.0.0");
            dicomTLS.setClientBindAddress("0.0.0.0");
            dicomTLS.setMaxOpsInvoked(0);
            dicomTLS.setMaxOpsPerformed(0);
            dicomTLS.setTlsCipherSuites(
                    Connection.TLS_RSA_WITH_AES_128_CBC_SHA,
                    Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
            device.addConnection(dicomTLS);

            https = new Connection("https", archiveHost, 8443);
            https.setProtocol(Connection.Protocol.HTTP);
            https.setTlsCipherSuites(
                    Connection.TLS_RSA_WITH_AES_128_CBC_SHA,
                    Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
            device.addConnection(https);
        }
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
                dicom, dicomTLS, HIDE_REJECTED_VIEW, true, true, true, true, null,
                configType, USER_AND_ADMIN));
        device.addApplicationEntity(createAE("IOCM_REGULAR_USE", IOCM_REGULAR_USE_DESC,
                dicom, dicomTLS, REGULAR_USE_VIEW, false, true, false, false, null,
                configType, ONLY_ADMIN));
        device.addApplicationEntity(createAE("IOCM_EXPIRED", IOCM_EXPIRED_DESC,
                dicom, dicomTLS, IOCM_EXPIRED_VIEW, false, false, false, false, null,
                configType, USER_AND_ADMIN));
        device.addApplicationEntity(createAE("IOCM_QUALITY", IOCM_QUALITY_DESC,
                dicom, dicomTLS, IOCM_QUALITY_VIEW, false, false, false, false, null,
                configType, ONLY_ADMIN));
        device.addApplicationEntity(createAE("IOCM_PAT_SAFETY", IOCM_PAT_SAFETY_DESC,
                dicom, dicomTLS, IOCM_PAT_SAFETY_VIEW, false, false, false, false, null,
                configType, ONLY_ADMIN));
        device.addApplicationEntity(createAE("IOCM_WRONG_MWL", IOCM_WRONG_MWL_DESC,
                dicom, dicomTLS, IOCM_WRONG_MWL_VIEW, false, false, false, false, null,
                configType, ONLY_ADMIN));
        device.addApplicationEntity(createAE("AS_RECEIVED", AS_RECEIVED_DESC,
                dicom, dicomTLS, REGULAR_USE_VIEW, false, true, false, false,
                new ArchiveAttributeCoercion()
                        .setCommonName("RetrieveAsReceived")
                        .setDIMSE(Dimse.C_STORE_RQ)
                        .setRole(SCP)
                        .setRetrieveAsReceived(true),
                configType, ONLY_ADMIN));

        device.addWebApplication(createWebApp("DCM4CHEE", AE_TITLE_DESC,
                "/dcm4chee-arc/aets/DCM4CHEE/rs", AE_TITLE, http, https,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.STOW_RS,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("DCM4CHEE-WADO", AE_TITLE_DESC,
                "/dcm4chee-arc/aets/DCM4CHEE/wado", AE_TITLE, http, https,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("IOCM_REGULAR_USE", IOCM_REGULAR_USE_DESC,
                "/dcm4chee-arc/aets/IOCM_REGULAR_USE/rs", "IOCM_REGULAR_USE", http, https,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("IOCM_REGULAR_USE-WADO", IOCM_REGULAR_USE_DESC,
                "/dcm4chee-arc/aets/IOCM_REGULAR_USE/wado", "IOCM_REGULAR_USE", http, https,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("IOCM_EXPIRED", IOCM_EXPIRED_DESC,
                "/dcm4chee-arc/aets/IOCM_EXPIRED/rs", "IOCM_EXPIRED", http, https,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("IOCM_EXPIRED-WADO", IOCM_EXPIRED_DESC,
                "/dcm4chee-arc/aets/IOCM_EXPIRED/wado", "IOCM_EXPIRED", http, https,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("IOCM_QUALITY", IOCM_QUALITY_DESC,
                "/dcm4chee-arc/aets/IOCM_QUALITY/rs", "IOCM_QUALITY", http, https,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("IOCM_QUALITY-WADO", IOCM_QUALITY_DESC,
                "/dcm4chee-arc/aets/IOCM_QUALITY/wado", "IOCM_QUALITY", http, https,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("IOCM_PAT_SAFETY", IOCM_PAT_SAFETY_DESC,
                "/dcm4chee-arc/aets/IOCM_PAT_SAFETY/rs", "IOCM_PAT_SAFETY", http, https,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("IOCM_PAT_SAFETY-WADO", IOCM_PAT_SAFETY_DESC,
                "/dcm4chee-arc/aets/IOCM_PAT_SAFETY/wado", "IOCM_PAT_SAFETY", http, https,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("IOCM_WRONG_MWL", IOCM_WRONG_MWL_DESC,
                "/dcm4chee-arc/aets/IOCM_WRONG_MWL/rs", "IOCM_WRONG_MWL", http, https,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("IOCM_WRONG_MWL-WADO", IOCM_WRONG_MWL_DESC,
                "/dcm4chee-arc/aets/IOCM_WRONG_MWL/wado", "IOCM_WRONG_MWL", http, https,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("AS_RECEIVED", AS_RECEIVED_DESC,
                "/dcm4chee-arc/aets/AS_RECEIVED/rs", "AS_RECEIVED", http, https,
                WebApplication.ServiceClass.QIDO_RS,
                WebApplication.ServiceClass.WADO_RS,
                WebApplication.ServiceClass.DCM4CHEE_ARC_AET));
        device.addWebApplication(createWebApp("AS_RECEIVED-WADO", AS_RECEIVED_DESC,
                "/dcm4chee-arc/aets/AS_RECEIVED/wado", "AS_RECEIVED", http, https,
                WebApplication.ServiceClass.WADO_URI));
        device.addWebApplication(createWebApp("dcm4chee-arc", "Forward Reschedule Task(s)",
                "/dcm4chee-arc", null, http, https,
                WebApplication.ServiceClass.DCM4CHEE_ARC));
        return device;
    }

    private static WebApplication createWebApp(
            String name, String desc, String path, String aet, Connection http, Connection https,
            WebApplication.ServiceClass... serviceClasses) {
        WebApplication webapp = new WebApplication(name);
        webapp.setDescription(desc);
        webapp.setServicePath(path);
        webapp.setAETitle(aet);
        webapp.setServiceClasses(serviceClasses);
        webapp.addConnection(http);
        if (https != null) webapp.addConnection(https);

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
        AuditLoggerDeviceExtension ext = new AuditLoggerDeviceExtension();
        AuditLogger auditLogger = new AuditLogger("Audit Logger");
        auditLogger.addConnection(syslog);
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

        Connection hl7 = new Connection("hl7", archiveHost, 2575);
        hl7.setBindAddress("0.0.0.0");
        hl7.setClientBindAddress("0.0.0.0");
        hl7.setProtocol(Connection.Protocol.HL7);
        device.addConnection(hl7);
        hl7App.addConnection(hl7);

        if (configType == configType.SAMPLE) {
            Connection hl7TLS = new Connection("hl7-tls", archiveHost, 12575);
            hl7TLS.setBindAddress("0.0.0.0");
            hl7TLS.setClientBindAddress("0.0.0.0");
            hl7TLS.setProtocol(Connection.Protocol.HL7);
            hl7TLS.setTlsCipherSuites(
                    Connection.TLS_RSA_WITH_AES_128_CBC_SHA,
                    Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
            device.addConnection(hl7TLS);
            hl7App.addConnection(hl7TLS);
        }
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
        ext.setUnzipVendorDataToURI(UNZIP_VENDOR_DATA);
        ext.setQidoMaxNumberOfResults(QIDO_MAX_NUMBER_OF_RESULTS);
        ext.setIanTaskPollingInterval(IAN_TASK_POLLING_INTERVAL);
        ext.setPurgeQueueMessagePollingInterval(PURGE_QUEUE_MSG_POLLING_INTERVAL);
        ext.setExportTaskPollingInterval(EXPORT_TASK_POLLING_INTERVAL);
        ext.setPurgeStoragePollingInterval(PURGE_STORAGE_POLLING_INTERVAL);
        ext.setPurgeStoragePollingInterval(PURGE_STORAGE_POLLING_INTERVAL);
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

        ext.setRejectExpiredStudiesPollingInterval(REJECT_EXPIRED_STUDIES_POLLING_INTERVAL);
        ext.setRejectExpiredStudiesAETitle(AE_TITLE);
        ext.setRejectExpiredStudiesFetchSize(REJECT_EXPIRED_STUDIES_SERIES_FETCH_SIZE);
        ext.setRejectExpiredSeriesFetchSize(REJECT_EXPIRED_STUDIES_SERIES_FETCH_SIZE);

        ext.setAuditRecordRepositoryURL(AUDIT_RECORD_REPOSITORY_URL);
        ext.setAudit2JsonFhirTemplateURI(AUDIT2JSONFHIR_XSL);
        ext.setAudit2XmlFhirTemplateURI(AUDIT2XMLFHIR_XSL);

        ext.setAttributeFilter(Entity.Patient, newAttributeFilter(PATIENT_ATTRS, Attributes.UpdatePolicy.SUPPLEMENT));
        ext.setAttributeFilter(Entity.Study, newAttributeFilter(STUDY_ATTRS, Attributes.UpdatePolicy.MERGE));
        ext.setAttributeFilter(Entity.Series, newAttributeFilter(SERIES_ATTRS, Attributes.UpdatePolicy.MERGE));
        ext.setAttributeFilter(Entity.Instance, new AttributeFilter(INSTANCE_ATTRS));
        ext.setAttributeFilter(Entity.MPPS, new AttributeFilter(MPPS_ATTRS));
        ext.setAttributeFilter(Entity.MWL, new AttributeFilter(MWL_ATTRS));
        ext.setAttributeFilter(Entity.UPS, new AttributeFilter(UPS_ATTRS));

        ext.addHL7OrderScheduledStation(newScheduledStation(scheduledStation));

        ext.addIDGenerator(newIDGenerator(IDGenerator.Name.PatientID, "P-%08d"));
        ext.addIDGenerator(newIDGenerator(IDGenerator.Name.AccessionNumber, "A-%08d"));
        ext.addIDGenerator(newIDGenerator(IDGenerator.Name.RequestedProcedureID, "RP-%08d"));
        ext.addIDGenerator(newIDGenerator(IDGenerator.Name.ScheduledProcedureStepID, "SPS-%08d"));

        StorageDescriptor storageDescriptor = new StorageDescriptor(STORAGE_ID);
        storageDescriptor.setStorageURIStr(STORAGE_URI);
        storageDescriptor.setProperty("pathFormat", PATH_FORMAT);
        storageDescriptor.setProperty("checkMountFile", "NO_MOUNT");
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
                "groupButtons=synchronize,export,reject",
                "actions=study-reject-export,study-reject,study-export"));
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
                "groupButtons=synchronize,export,reject",
                "actions=study-reject-export,study-reject,study-export"));
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
        ext.setRejectionNoteStorageAET(AE_TITLE);

        ExporterDescriptor studySizeExporter = new ExporterDescriptor(CALC_STUDY_SIZE_EXPORTER_ID);
        studySizeExporter.setDescription(CALC_STUDY_SIZE_EXPORTER_DESC);
        studySizeExporter.setExportURI(CALC_STUDY_SIZE_EXPORTER_URI);
        studySizeExporter.setQueueName("Export4");
        studySizeExporter.setAETitle(AE_TITLE);
        ext.addExporterDescriptor(studySizeExporter);

        ExportRule calcStudySizeRule = new ExportRule(CALC_STUDY_SIZE_EXPORTER_DESC);
        calcStudySizeRule.setEntity(Entity.Study);
        calcStudySizeRule.setExportDelay(CALC_STUDY_SIZE_DELAY);
        calcStudySizeRule.setExporterIDs(CALC_STUDY_SIZE_EXPORTER_ID);
        ext.addExportRule(calcStudySizeRule);

        ExporterDescriptor studySeriesQueryAttrExporter = new ExporterDescriptor(CALC_QUERY_ATTRS_EXPORTER_ID);
        studySeriesQueryAttrExporter.setDescription(CALC_QUERY_ATTRS_EXPORTER_DESC);
        studySeriesQueryAttrExporter.setExportURI(CALC_QUERY_ATTRS_EXPORTER_URI);
        studySeriesQueryAttrExporter.setQueueName("Export4");
        studySeriesQueryAttrExporter.setAETitle(AE_TITLE);
        ext.addExporterDescriptor(studySeriesQueryAttrExporter);

        ExportRule studySeriesQueryAttrExportRule = new ExportRule(CALC_QUERY_ATTRS_EXPORTER_DESC);
        studySeriesQueryAttrExportRule.setEntity(Entity.Study);
        studySeriesQueryAttrExportRule.setExportDelay(CALC_QUERY_ATTRS_DELAY);
        studySeriesQueryAttrExportRule.setExporterIDs(CALC_QUERY_ATTRS_EXPORTER_ID);
        ext.addExportRule(studySeriesQueryAttrExportRule);

        if (configType == configType.SAMPLE) {
            StorageDescriptor metadataStorageDescriptor = new StorageDescriptor(METADATA_STORAGE_ID);
            metadataStorageDescriptor.setStorageURIStr(METADATA_STORAGE_URI);
            metadataStorageDescriptor.setProperty("pathFormat", METADATA_PATH_FORMAT);
            metadataStorageDescriptor.setProperty("checkMountFile", "NO_MOUNT");
            ext.addStorageDescriptor(metadataStorageDescriptor);

            StorageDescriptor seriesMetadataStorageDescriptor = new StorageDescriptor(SERIES_METADATA_STORAGE_ID);
            seriesMetadataStorageDescriptor.setStorageURIStr(SERIES_METADATA_STORAGE_URI);
            seriesMetadataStorageDescriptor.setProperty("pathFormat", SERIES_METADATA_PATH_FORMAT);
            seriesMetadataStorageDescriptor.setProperty("checkMountFile", "NO_MOUNT");
            ext.addStorageDescriptor(seriesMetadataStorageDescriptor);
            ext.setSeriesMetadataStorageIDs(SERIES_METADATA_STORAGE_ID);
            ext.setSeriesMetadataDelay(SERIES_METADATA_DELAY);
            ext.setSeriesMetadataPollingInterval(SERIES_METADATA_POLLING_INTERVAL);

            StorageDescriptor wadoJpegStorageDescriptor = new StorageDescriptor(WADO_JPEG_STORAGE_ID);
            wadoJpegStorageDescriptor.setStorageURIStr(WADO_JPEG_STORAGE_URI);
            wadoJpegStorageDescriptor.setProperty("pathFormat", WADO_JPEG_PATH_FORMAT);
            wadoJpegStorageDescriptor.setProperty("checkMountFile", "NO_MOUNT");
            ext.addStorageDescriptor(wadoJpegStorageDescriptor);

            StorageDescriptor wadoJsonStorageDescriptor = new StorageDescriptor(WADO_JSON_STORAGE_ID);
            wadoJsonStorageDescriptor.setStorageURIStr(WADO_JSON_STORAGE_URI);
            wadoJsonStorageDescriptor.setProperty("pathFormat", WADO_JSON_PATH_FORMAT);
            wadoJsonStorageDescriptor.setProperty("checkMountFile", "NO_MOUNT");
            ext.addStorageDescriptor(wadoJsonStorageDescriptor);

            StorageDescriptor nearlineStorageDescriptor = new StorageDescriptor(NEARLINE_STORAGE_ID);
            nearlineStorageDescriptor.setStorageURIStr(NEARLINE_STORAGE_URI);
            nearlineStorageDescriptor.setProperty("pathFormat", NEARLINE_PATH_FORMAT);
            nearlineStorageDescriptor.setProperty("checkMountFile", "NO_MOUNT");
            nearlineStorageDescriptor.setInstanceAvailability(Availability.NEARLINE);
            ext.addStorageDescriptor(nearlineStorageDescriptor);

            ExporterDescriptor nearlineExporter = new ExporterDescriptor(NEARLINE_STORAGE_EXPORTER_ID);
            nearlineExporter.setDescription(NEARLINE_STORAGE_EXPORTER_DESC);
            nearlineExporter.setExportURI(NEARLINE_STORAGE_EXPORTER_URI);
            nearlineExporter.setQueueName("Export5");
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
            dicomExporter.setQueueName("Export1");
            dicomExporter.setAETitle(AE_TITLE);
            ext.addExporterDescriptor(dicomExporter);

            ExportRule exportRule = new ExportRule("Forward to STORESCP");
            exportRule.getConditions().setSendingAETitle("FORWARD");
            exportRule.getConditions().setCondition("Modality", "CT|MR");
            exportRule.setEntity(Entity.Series);
            exportRule.setExportDelay(Duration.valueOf("PT1M"));
            exportRule.setExporterIDs(DICOM_EXPORTER_ID);
            ext.addExportRule(exportRule);

            ExporterDescriptor wadoExportDescriptor = new ExporterDescriptor(WADO_EXPORTER_ID);
            wadoExportDescriptor.setDescription(WADO_EXPORTER_DESC);
            wadoExportDescriptor.setExportURI(WADO_EXPORT_URI);
            wadoExportDescriptor.setQueueName("Export2");
            wadoExportDescriptor.setAETitle(AE_TITLE);
            wadoExportDescriptor.setProperty("Cache-Control", WADO_CACHE_CONTROL);
            wadoExportDescriptor.setProperty("StorageID", WADO_JPEG_STORAGE_ID);
            wadoExportDescriptor.setProperty("URL.1", WADO_JSON_EXPORT_URL);
            wadoExportDescriptor.setProperty("Accept.1", WADO_JSON_ACCEPT);
            wadoExportDescriptor.setProperty("StorageID.1", WADO_JSON_STORAGE_ID);
            ext.addExporterDescriptor(wadoExportDescriptor);

            ExportRule wadoExportRule = new ExportRule("Forward to WADO");
            wadoExportRule.getConditions().setSendingAETitle("WADO");
            wadoExportRule.setEntity(Entity.Series);
            wadoExportRule.setExportDelay(Duration.valueOf("PT1M"));
            wadoExportRule.setExporterIDs(WADO_EXPORTER_ID);
            ext.addExportRule(wadoExportRule);

            ExporterDescriptor xdsiExportDescriptor = new ExporterDescriptor(XDSI_EXPORTER_ID);
            xdsiExportDescriptor.setDescription(XDSI_EXPORTER_DESC);
            xdsiExportDescriptor.setExportURI(XDSI_EXPORT_URI);
            xdsiExportDescriptor.setQueueName("Export3");
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

            ext.addAttributeCoercion(new ArchiveAttributeCoercion()
                    .setCommonName("Ensure PID")
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCU)
                    .setAETitles("ENSURE_PID")
                    .setXSLTStylesheetURI(ENSURE_PID)
                    .setNoKeywords(true));

            ext.addAttributeCoercion(new ArchiveAttributeCoercion()
                    .setCommonName("Merge MWL")
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCU)
                    .setAETitles("MERGE_MWL")
                    .setMergeMWLMatchingKey(MergeMWLMatchingKey.StudyInstanceUID)
                    .setMergeMWLTemplateURI(MERGE_MWL)
                    .setNoKeywords(true));

            ext.addAttributeCoercion(new ArchiveAttributeCoercion()
                    .setCommonName("Nullify PN")
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCP)
                    .setAETitles("NULLIFY_PN")
                    .setXSLTStylesheetURI(NULLIFY_PN)
                    .setNoKeywords(true));

            ext.addAttributeCoercion(new ArchiveAttributeCoercion()
                    .setCommonName("Correct VR")
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCP)
                    .setAETitles("CORRECT_VR")
                    .setXSLTStylesheetURI(CORRECT_VR)
                    .setNoKeywords(true));

            ext.addAttributeCoercion(new ArchiveAttributeCoercion()
                    .setCommonName("Leading DCMQRSCP STORE")
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCP)
                    .setAETitles("LEADING_DCMQRSCP")
                    .setLeadingCFindSCP("DCMQRSCP"));

            ext.addAttributeCoercion(new ArchiveAttributeCoercion()
                    .setCommonName("Leading DCMQRSCP FIND")
                    .setDIMSE(Dimse.C_FIND_RSP)
                    .setRole(SCU)
                    .setAETitles("LEADING_DCMQRSCP")
                    .setLeadingCFindSCP("DCMQRSCP"));

            ext.addAttributeCoercion(new ArchiveAttributeCoercion()
                    .setCommonName("Supplement Composite")
                    .setDIMSE(Dimse.C_STORE_RQ)
                    .setRole(SCU)
                    .setAETitles("STORESCU")
                    .setSupplementFromDevice(storescu));

            ext.addAttributeCoercion(new ArchiveAttributeCoercion()
                    .setCommonName("Supplement MPPS")
                    .setDIMSE(Dimse.N_CREATE_RQ)
                    .setRole(SCU)
                    .setAETitles("MPPSSCU")
                    .setSOPClasses(UID.ModalityPerformedProcedureStepSOPClass)
                    .setSupplementFromDevice(mppsscu));

            StoreAccessControlIDRule storeAccessControlIDRule =
                    new StoreAccessControlIDRule("StoreAccessControlIDRule1");
            storeAccessControlIDRule.getConditions().setSendingAETitle("ACCESS_CONTROL");
            storeAccessControlIDRule.setStoreAccessControlID("ACCESS_CONTROL_ID");
            ext.addStoreAccessControlIDRule(storeAccessControlIDRule);
        }
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
                                              boolean storeSCP, boolean storeSCU, boolean mwlSCP, boolean upsSCP,
                                              ArchiveAttributeCoercion coercion, ConfigType configType,
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
        addTC(ae, null, SCP, UID.VerificationSOPClass, UID.ImplicitVRLittleEndian);
        addTC(ae, null, SCU, UID.VerificationSOPClass, UID.ImplicitVRLittleEndian);
        EnumSet<QueryOption> allQueryOpts = EnumSet.allOf(QueryOption.class);
        addTCs(ae, allQueryOpts, SCP, QUERY_CUIDS, UID.ImplicitVRLittleEndian);
        if (mwlSCP) {
            addTC(ae, allQueryOpts, SCP,
                    UID.ModalityWorklistInformationModelFIND,
                    UID.ImplicitVRLittleEndian);
        }
        if (upsSCP) {
            addTCs(ae, allQueryOpts, SCP, new String[] {
                            UID.UnifiedProcedureStepPullSOPClass,
                            UID.UnifiedProcedureStepWatchSOPClass
                    },
                    UID.ImplicitVRLittleEndian);
            addTCs(ae, null, SCP, new String[]{
                            UID.UnifiedProcedureStepPushSOPClass,
                            UID.UnifiedProcedureStepEventSOPClass
                    },
                    UID.ImplicitVRLittleEndian);
        }
        String[][] CUIDS = { IMAGE_CUIDS, VIDEO_CUIDS, SR_CUIDS, OTHER_CUIDS };
        String[][] TSUIDS = { IMAGE_TSUIDS, VIDEO_TSUIDS, SR_TSUIDS, OTHER_TSUIDS };
        if (storeSCU) {
            addTCs(ae, EnumSet.of(QueryOption.RELATIONAL), SCP, RETRIEVE_CUIDS, UID.ImplicitVRLittleEndian);
            for (int i = 0; i < CUIDS.length; i++)
                addTCs(ae, null, SCU, CUIDS[i], TSUIDS[i]);
            addTC(ae, null, SCU, UID.StorageCommitmentPushModelSOPClass, UID.ImplicitVRLittleEndian);
        }
        if (storeSCP) {
            for (int i = 0; i < CUIDS.length; i++)
                addTCs(ae, null, SCP, CUIDS[i], TSUIDS[i]);
            addTC(ae, null, SCP, UID.StorageCommitmentPushModelSOPClass, UID.ImplicitVRLittleEndian);
            addTC(ae, null, SCP, UID.ModalityPerformedProcedureStepSOPClass, UID.ImplicitVRLittleEndian);
            addTC(ae, null, SCU, UID.ModalityPerformedProcedureStepSOPClass, UID.ImplicitVRLittleEndian);
            addTC(ae, null, SCU, UID.InstanceAvailabilityNotificationSOPClass, UID.ImplicitVRLittleEndian);
            if (configType == ConfigType.SAMPLE)
                aeExt.setMetadataStorageIDs(METADATA_STORAGE_ID);
            aeExt.setObjectStorageIDs(STORAGE_ID);
        }
        aeExt.setQueryRetrieveViewID(qrView.getViewID());
        aeExt.setAcceptedUserRoles(acceptedUserRoles);
        if (coercion != null)
            aeExt.addAttributeCoercion(coercion);
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
