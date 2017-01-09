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

package org.dcm4chee.arc.conf;

import org.dcm4che3.data.*;
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory;
import org.dcm4che3.net.*;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditLoggerDeviceExtension;
import org.dcm4che3.net.audit.AuditRecordRepository;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.imageio.ImageReaderExtension;
import org.dcm4che3.net.imageio.ImageWriterExtension;
import org.dcm4che3.util.Property;

import java.net.URI;
import java.time.LocalTime;
import java.time.Period;
import java.util.EnumSet;

import static org.dcm4che3.net.TransferCapability.Role.SCP;
import static org.dcm4che3.net.TransferCapability.Role.SCU;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
class ArchiveDeviceFactory {

    enum ConfigType {
        DEFAULT,
        SAMPLE,
        DOCKER,
        TEST
    }
    static final String[] OTHER_DEVICES = {
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
    static final String[] OTHER_AES = {
            "DCMQRSCP",
            "STGCMTSCU",
            "STORESCP",
            "MPPSSCP",
            "IANSCP",
            "STORESCU",
            "MPPSSCU",
            "FINDSCU",
            "GETSCU"
    };
    static final Issuer SITE_A =
            new Issuer("Site A", "1.2.40.0.13.1.1.999.111.1111", "ISO");
    static final Issuer SITE_B =
            new Issuer("Site B", "1.2.40.0.13.1.1.999.222.2222", "ISO");
    static final Code INST_B =
            new Code("222.2222", "99DCM4CHEE", null, "Site B");
    static final Issuer[] OTHER_ISSUER = {
            SITE_B, // DCMQRSCP
            null, // STGCMTSCU
            SITE_A, // STORESCP
            SITE_A, // MPPSSCP
            null, // IANSCP
            SITE_A, // STORESCU
            SITE_A, // MPPSSCU
            SITE_A, // FINDSCU
            SITE_A, // GETSCU
    };
    static final Code INST_A =
            new Code("111.1111", "99DCM4CHEE", null, "Site A");
    static final Code[] OTHER_INST_CODES = {
            INST_B, // DCMQRSCP
            null, // STGCMTSCU
            null, // STORESCP
            null, // MPPSSCP
            null, // IANSCP
            INST_A, // STORESCU
            null, // MPPSSCU
            null, // FINDSCU
            null, // GETSCU
    };
    static final int[] OTHER_PORTS = {
            11113, 2763, // DCMQRSCP
            11114, 2765, // STGCMTSCU
            11115, 2766, // STORESCP
            11116, 2767, // MPPSSCP
            11117, 2768, // IANSCP
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // STORESCU
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // MPPSSCU
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // FINDSCU
            Connection.NOT_LISTENING, Connection.NOT_LISTENING, // GETSCU
    };

    static final QueueDescriptor[] QUEUE_DESCRIPTORS = {
        newQueueDescriptor("MPPSSCU", "MPPS Forward Tasks"),
        newQueueDescriptor("IANSCU", "IAN Tasks"),
        newQueueDescriptor("StgCmtSCP", "Storage Commitment SCP Tasks"),
        newQueueDescriptor("StgCmtSCU", "Storage Commitment SCU Tasks"),
        newQueueDescriptor("Export1", "Dicom Export Tasks"),
        newQueueDescriptor("Export2", "WADO Export Tasks"),
        newQueueDescriptor("Export3", "XDS-I Export Tasks"),
        newQueueDescriptor("HL7Send", "HL7 Forward Tasks"),
        newQueueDescriptor("RSClient", "RESTful Forward Tasks")
    };

    static final HL7OrderSPSStatus[] HL7_ORDER_SPS_STATUSES = {
            newHL7OrderSPSStatus("SCHEDULED", "NW_SC", "NW_IP", "XO_SC"),
            newHL7OrderSPSStatus("CANCELLED", "CA_CA"),
            newHL7OrderSPSStatus("DISCONTINUED", "DC_CA"),
            newHL7OrderSPSStatus("COMPLETED", "XO_CM")
    };

    static QueueDescriptor newQueueDescriptor(String name, String description) {
        QueueDescriptor desc = new QueueDescriptor(name);
        desc.setDescription(description);
        desc.setJndiName("jms/queue/" + name);
        desc.setMaxRetries(10);
        desc.setRetryDelay(Duration.parse("PT30S"));
        desc.setRetryDelayMultiplier(200);
        desc.setMaxRetryDelay(Duration.parse("PT10M"));
        desc.setPurgeQueueMessageCompletedDelay(Duration.parse("P1D"));
        return desc;
    }

    static HL7OrderSPSStatus newHL7OrderSPSStatus(String spsStatus, String... orderStatuses) {
        HL7OrderSPSStatus hl7OrderSPSStatus = new HL7OrderSPSStatus();
        hl7OrderSPSStatus.setSPSStatus(SPSStatus.valueOf(spsStatus));
        hl7OrderSPSStatus.setOrderControlStatusCodes(orderStatuses);
        return hl7OrderSPSStatus;
    }

    static IDGenerator newIDGenerator(IDGenerator.Name name, String format) {
        IDGenerator gen = new IDGenerator();
        gen.setName(name);
        gen.setFormat(format);
        return gen;
    }

    static final int[] PATIENT_ATTRS = {
            Tag.SpecificCharacterSet,
            Tag.PatientName,
            Tag.PatientID,
            Tag.IssuerOfPatientID,
            Tag.IssuerOfPatientIDQualifiersSequence,
            Tag.PatientBirthDate,
            Tag.PatientBirthTime,
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
            Tag.MedicalAlerts,
            Tag.Allergies,
            Tag.CountryOfResidence,
            Tag.RegionOfResidence,
            Tag.PatientTelephoneNumbers,
            Tag.EthnicGroup,
            Tag.SmokingStatus,
            Tag.PregnancyStatus,
            Tag.LastMenstrualDate,
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
            Tag.PatientState,
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
            Tag.StudyDescription,
            Tag.ProcedureCodeSequence,
            Tag.PhysiciansOfRecord,
            Tag.PatientAge,
            Tag.PatientSize,
            Tag.PatientSizeCodeSequence,
            Tag.PatientWeight,
            Tag.Occupation,
            Tag.AdditionalPatientHistory,
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
            Tag.StationName,
            Tag.SeriesDescription,
            Tag.InstitutionalDepartmentName,
            Tag.PerformingPhysicianName,
            Tag.ManufacturerModelName,
            Tag.ReferencedPerformedProcedureStepSequence,
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
            Tag.SOPClassUID,
            Tag.SOPInstanceUID,
            Tag.ContentDate,
            Tag.ContentTime,
            Tag.ReferencedSeriesSequence,
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
            Tag.CurrentRequestedProcedureEvidenceSequence
    };
    static final int[] MPPS_ATTRS = {
            Tag.SpecificCharacterSet,
            Tag.Modality,
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
            Tag.AdmittingDiagnosesDescription,
            Tag.AdmittingDiagnosesCodeSequence,
            Tag.ReferencedStudySequence,
            Tag.ReferencedPatientSequence,
            Tag.PatientAge,
            Tag.PatientSize,
            Tag.PatientSizeCodeSequence,
            Tag.PatientWeight,
            Tag.Occupation,
            Tag.AdditionalPatientHistory,
            Tag.PatientSexNeutered,
            Tag.MedicalAlerts,
            Tag.Allergies,
            Tag.PregnancyStatus,
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
            UID.AcquisitionContextSRStorage
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
            UID.GrayscaleSoftcopyPresentationStateStorageSOPClass,
            UID.ColorSoftcopyPresentationStateStorageSOPClass,
            UID.PseudoColorSoftcopyPresentationStateStorageSOPClass,
            UID.BlendingSoftcopyPresentationStateStorageSOPClass,
            UID.XAXRFGrayscaleSoftcopyPresentationStateStorage,
            UID.GrayscalePlanarMPRVolumetricPresentationStateStorage,
            UID.CompositingPlanarMPRVolumetricPresentationStateStorage,
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
            UID.StandalonePETCurveStorageRetired,
            UID.TextSRStorageTrialRetired,
            UID.AudioSRStorageTrialRetired,
            UID.DetailSRStorageTrialRetired,
            UID.ComprehensiveSRStorageTrialRetired,
            UID.ContentAssessmentResultsStorage,
            UID.RTDoseStorage,
            UID.RTStructureSetStorage,
            UID.RTBeamsTreatmentRecordStorage,
            UID.RTPlanStorage,
            UID.RTBrachyTreatmentRecordStorage,
            UID.RTTreatmentSummaryRecordStorage,
            UID.RTIonPlanStorage,
            UID.RTIonBeamsTreatmentRecordStorage,
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

    static final String[][] CUIDS_TSUIDS = {
            IMAGE_CUIDS, IMAGE_TSUIDS,
            VIDEO_CUIDS, VIDEO_TSUIDS,
            SR_CUIDS, SR_TSUIDS,
            OTHER_CUIDS, OTHER_TSUIDS
    };

    static final String[] QUERY_CUIDS = {
            UID.PatientRootQueryRetrieveInformationModelFIND,
            UID.StudyRootQueryRetrieveInformationModelFIND,
            UID.PatientStudyOnlyQueryRetrieveInformationModelFINDRetired
    };
    static final String[] MWL_CUID = {
            UID.ModalityWorklistInformationModelFIND
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
    static final Code REJECT_FOR_PATIENT_SAFETY_REASONS =
            new Code("113037", "DCM", null, "Rejected for Patient Safety Reasons");
    static final Code INCORRECT_MODALITY_WORKLIST_ENTRY =
            new Code("113038", "DCM", null, "Incorrect Modality Worklist Entry");
    static final Code DATA_RETENTION_POLICY_EXPIRED =
            new Code("113039", "DCM", null, "Data Retention Policy Expired");
    static final Code REVOKE_REJECTION =
            new Code("REVOKE_REJECTION", "99DCM4CHEE", null, "Restore rejected Instances");
    static final Code[] REJECTION_CODES = {
            REJECTED_FOR_QUALITY_REASONS,
            REJECT_FOR_PATIENT_SAFETY_REASONS,
            INCORRECT_MODALITY_WORKLIST_ENTRY,
            DATA_RETENTION_POLICY_EXPIRED
    };
    static final QueryRetrieveView REGULAR_USE_VIEW =
            createQueryRetrieveView("regularUse",
                    new Code[]{REJECTED_FOR_QUALITY_REASONS},
                    new Code[]{DATA_RETENTION_POLICY_EXPIRED},
                    false);
    static final QueryRetrieveView HIDE_REJECTED_VIEW =
            createQueryRetrieveView("hideRejected",
                    new Code[0],
                    new Code[]{DATA_RETENTION_POLICY_EXPIRED},
                    false);
    static final QueryRetrieveView TRASH_VIEW =
            createQueryRetrieveView("trashView",
                    REJECTION_CODES,
                    new Code[0],
                    true);

    static final String[] MPPS_FORWARD_DESTINATIONS = {
            "MPPSCP",
            "OTHER_MPPSCP",
            "MPPSCP2"
    };

    static final String[] ACCESS_CONTROL_IDS = {
            "*",
            "*"
    };

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
            "ADT^A28",
            "ADT^A31",
            "ADT^A40",
            "ADT^A47",
            "ORM^O01",
            "OMI^O23",
            "OMG^O19",
            "ORU^R01"
    };

    static final String[] DEVICE_TYPES = {
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

    static final String DCM4CHEE_ARC_VERSION = "5.8.1";
    static final String DCM4CHEE_ARC_KEY_JKS =  "${jboss.server.config.url}/dcm4chee-arc/key.jks";
    static final String HL7_ADT2DCM_XSL = "${jboss.server.temp.url}/dcm4chee-arc/hl7-adt2dcm.xsl";
    static final String DSR2HTML_XSL = "${jboss.server.temp.url}/dcm4chee-arc/dsr2html.xsl";
    static final String DSR2TEXT_XSL = "${jboss.server.temp.url}/dcm4chee-arc/dsr2text.xsl";
    static final String HL7_ORU2DSR_XSL = "${jboss.server.temp.url}/dcm4chee-arc/hl7-oru2dsr.xsl";
    static final String HL7_ORDER2DCM_XSL = "${jboss.server.temp.url}/dcm4chee-arc/hl7-order2dcm.xsl";
    static final String UNZIP_VENDOR_DATA = "${jboss.server.temp.url}/dcm4chee-arc";
    static final String NULLIFY_PN = "${jboss.server.temp.url}/dcm4chee-arc/nullify-pn.xsl";
    static final String ENSURE_PID = "${jboss.server.temp.url}/dcm4chee-arc/ensure-pid.xsl";
    static final String MERGE_MWL = "${jboss.server.temp.url}/dcm4chee-arc/mwl2series.xsl";
    static final String PIX_CONSUMER = "DCM4CHEE|DCM4CHEE";

    static final String PIX_MANAGER = "HL7RCV|DCM4CHEE";
    static final String STORAGE_ID = "fs1";
    static final String STORAGE_URI = "${jboss.server.data.url}/fs1/";
    static final String PATH_FORMAT = "{now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}";
    static final String METADATA_STORAGE_ID = "metadata";
    static final String METADATA_STORAGE_URI = "${jboss.server.data.url}/metadata/";
    static final String METADATA_PATH_FORMAT = "{now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}.json";
    static final String SERIES_METADATA_STORAGE_ID = "series-metadata";
    static final String SERIES_METADATA_STORAGE_URI = "${jboss.server.data.url}/series-metadata/";
    static final String SERIES_METADATA_PATH_FORMAT = "{now,date,yyyy/MM/dd}/{0020000D}/{0020000E}/metadata.zip";
    static final Duration SERIES_METADATA_DELAY = Duration.parse("PT1M");
    static final Duration SERIES_METADATA_POLLING_INTERVAL = Duration.parse("PT1M");
    static final String WADO_JPEG_STORAGE_ID = "wado-jpeg";
    static final String WADO_JPEG_STORAGE_URI = "${jboss.server.data.url}/wado/";
    static final String WADO_JPEG_PATH_FORMAT = "{0020000D}/{0020000E}/{00080018}/{00081160}.jpeg";
    static final String WADO_JSON_STORAGE_ID = "wado-json";
    static final String WADO_JSON_STORAGE_URI = "${jboss.server.data.url}/wado/";
    static final String WADO_JSON_PATH_FORMAT = "{0020000D}.json";
    static final boolean SEND_PENDING_C_GET = true;
    static final Duration SEND_PENDING_C_MOVE_INTERVAL = Duration.parse("PT5S");
    static final int QIDO_MAX_NUMBER_OF_RESULTS = 1000;
    static final Duration IAN_TASK_POLLING_INTERVAL = Duration.parse("PT1M");
    static final Duration HL7PSU_TASK_POLLING_INTERVAL = Duration.parse("PT1M");
    static final Duration PURGE_QUEUE_MSG_POLLING_INTERVAL = Duration.parse("PT1H");
    static final String DICOM_EXPORTER_ID = "STORESCP";
    static final String DICOM_EXPORTER_DESC = "Export to STORESCP";
    static final URI DICOM_EXPORT_URI = URI.create("dicom:STORESCP");
    static final String WADO_EXPORTER_ID = "WADO";
    static final String WADO_EXPORTER_DESC = "Export to WADO";
    static final URI WADO_EXPORT_URI = URI.create("wado:http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/wado?requestType=WADO&studyUID=[0]&seriesUID=[1]&objectUID=[2]&frameNumber=[3]");
    static final String WADO_CACHE_CONTROL = "no-cache";
    static final String WADO_JSON_EXPORT_URL = "http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies/[0]/metadata";
    static final String WADO_JSON_ACCEPT = "application/json";
    static final Duration EXPORT_TASK_POLLING_INTERVAL = Duration.parse("PT1M");
    static final Duration PURGE_STORAGE_POLLING_INTERVAL = Duration.parse("PT5M");
    static final Duration DELETE_REJECTED_POLLING_INTERVAL = Duration.parse("PT5M");
    static final String AUDIT_SPOOL_DIR =  "${jboss.server.data.dir}/audit-spool";
    static final Duration AUDIT_POLLING_INTERVAL = Duration.parse("PT1M");
    static final Duration AUDIT_AGGREGATE_DURATION = Duration.parse("PT1M");
    static final Duration DELETE_REJECTED_INSTANCE_DELAY = Duration.parse("P1D");
    static final Duration MAX_ACCESS_TIME_STALENESS = Duration.parse("PT5M");
    static final Duration AE_CACHE_STALE_TIMEOUT = Duration.parse("PT5M");
    static final Duration LEADING_C_FIND_SCP_QUERY_CACHE_STALE_TIMEOUT = Duration.parse("PT5M");
    static final Duration REJECT_EXPIRED_STUDIES_POLLING_INTERVAL = Duration.parse("P1D");
    static final LocalTime REJECT_EXPIRED_STUDIES_START_TIME = LocalTime.parse("00:00:00");
    static final int REJECT_EXPIRED_STUDIES_SERIES_FETCH_SIZE = 10;
    static final String REJECT_EXPIRED_STUDIES_AE_TITLE = "DCM4CHEE";
    static final Duration PURGE_STGCMT_COMPLETED_DELAY = Duration.parse("P1D");
    static final Duration PURGE_STGCMT_POLLING_INTERVAL = Duration.parse("PT1H");

    static {
        System.setProperty("jboss.server.data.url", "file:///opt/wildfly/standalone/data");
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

    public static Device createUnknownDevice(String name, String aet, String host, int port) {
        Device device = new Device(name);
        ApplicationEntity ae = new ApplicationEntity(aet);
        ae.setAssociationAcceptor(true);
        device.addApplicationEntity(ae);
        Connection dicom = new Connection("dicom", host, port);
        device.addConnection(dicom);
        ae.addConnection(dicom);
        return device;
    }

    public static Device createDevice(String name, ConfigType configType) throws Exception {
        return init(new Device(name), null, null);
    }

    public static Device createDevice(String name, Issuer issuer, Code institutionCode) throws Exception {
        return init(new Device(name), issuer, institutionCode);
    }

    private static Device init(Device device, Issuer issuer, Code institutionCode) throws Exception {
        device.setIssuerOfPatientID(issuer);
        device.setIssuerOfAccessionNumber(issuer);
        if (institutionCode != null) {
            device.setInstitutionNames(institutionCode.getCodeMeaning());
            device.setInstitutionCodes(institutionCode);
        }
        return device;
    }

    public static Device createDevice(String name, String primaryDeviceType, Issuer issuer, Code institutionCode, String aet,
                               String host, int port, int tlsPort) throws Exception {
        Device device = init(new Device(name), issuer, institutionCode);
        ApplicationEntity ae = new ApplicationEntity(aet);
        ae.setAssociationAcceptor(true);
        device.addApplicationEntity(ae);
        Connection dicom = new Connection("dicom", host, port);
        device.addConnection(dicom);
        ae.addConnection(dicom);
        Connection dicomTLS = new Connection("dicom-tls", host, tlsPort);
        dicomTLS.setTlsCipherSuites(
                Connection.TLS_RSA_WITH_AES_128_CBC_SHA,
                Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
        device.addConnection(dicomTLS);
        device.setPrimaryDeviceTypes(primaryDeviceType);
        ae.addConnection(dicomTLS);
        return device;
    }

    public static Device createHL7Device(String name, Issuer issuer, Code institutionCode, String appName,
                                     String host, int port, int tlsPort) throws Exception {
        Device device = new Device(name);
        HL7DeviceExtension hl7Device = new HL7DeviceExtension();
        device.addDeviceExtension(hl7Device);
        init(device, issuer, institutionCode);
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
        device.setPrimaryDeviceTypes("DSS");
        return device;
    }

    public static Device createKeycloakDevice(String name, Device arrDevice, ConfigType configType) {
        Device device = new Device(name);
        String keycloakHost = configType == ConfigType.DOCKER ? "keycloak-host" : "localhost";
        device.setInstalled(true);
        device.setPrimaryDeviceTypes("AUTH");
        addAuditLoggerDeviceExtension(device, arrDevice, keycloakHost);
        return device;
    }

    public static Device createArchiveDevice(String name, Device arrDevice, Device unknown, ConfigType configType) throws Exception {
        Device device = new Device(name);
        String archiveHost = configType == ConfigType.DOCKER ? "archive-host" : "localhost";
        Connection dicom = new Connection("dicom", archiveHost, 11112);
        dicom.setBindAddress("0.0.0.0");
        dicom.setClientBindAddress("0.0.0.0");
        dicom.setMaxOpsInvoked(0);
        dicom.setMaxOpsPerformed(0);
        device.addConnection(dicom);

        Connection dicomTLS = null;
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
        }

        addArchiveDeviceExtension(device, unknown, configType);
        addHL7DeviceExtension(device, configType, archiveHost);
        addAuditLoggerDeviceExtension(device, arrDevice, archiveHost);
        device.addDeviceExtension(new ImageReaderExtension(ImageReaderFactory.getDefault()));
        device.addDeviceExtension(new ImageWriterExtension(ImageWriterFactory.getDefault()));

        device.setManufacturer("dcm4che.org");
        device.setManufacturerModelName("dcm4chee-arc");
        device.setSoftwareVersions(DCM4CHEE_ARC_VERSION);
        device.setKeyStoreURL(DCM4CHEE_ARC_KEY_JKS);
        device.setKeyStoreType("JKS");
        device.setKeyStorePin("secret");
        device.setPrimaryDeviceTypes("ARCHIVE");

        device.addApplicationEntity(createAE("DCM4CHEE", "Hide instances rejected for Quality Reasons",
                dicom, dicomTLS, HIDE_REJECTED_VIEW, true, true, true, configType, USER_AND_ADMIN));
        device.addApplicationEntity(createAE("DCM4CHEE_ADMIN", "Show instances rejected for Quality Reasons",
                dicom, dicomTLS, REGULAR_USE_VIEW, false, true, false, configType, ONLY_ADMIN));
        device.addApplicationEntity(createAE("DCM4CHEE_TRASH", "Show rejected instances only",
                dicom, dicomTLS, TRASH_VIEW, false, false, false, configType, ONLY_ADMIN));

        return device;
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

    private static ArchiveAttributeCoercion createAttributeCoercion(
            String cn, Dimse dimse, TransferCapability.Role role, String aet, String xsltURI, String leadingCFindSCP,
            MergeMWLMatchingKey mergeMWLMatchingKey, ConfigType configType) {
        ArchiveAttributeCoercion coercion = new ArchiveAttributeCoercion(cn);
        coercion.setAETitles(aet);
        coercion.setRole(role);
        coercion.setDIMSE(dimse);
        coercion.setXSLTStylesheetURI(xsltURI);
        coercion.setNoKeywords(xsltURI != null);
        coercion.setLeadingCFindSCP(leadingCFindSCP);
        coercion.setMergeMWLMatchingKey(mergeMWLMatchingKey);
        if (mergeMWLMatchingKey != null)
            coercion.setMergeMWLTemplateURI(MERGE_MWL);
        if (configType == configType.TEST) {
            coercion.setPriority(3);
            coercion.setHostNames("localhost", "testenv");
            coercion.setSOPClasses(UID.MPEG2, UID.JPEG2000);
        }
        return coercion;
    }

    private static void addAuditLoggerDeviceExtension(Device device, Device arrDevice, String archiveHost) {
        Connection syslog = new Connection("syslog", archiveHost);
        syslog.setClientBindAddress("0.0.0.0");
        syslog.setProtocol(Connection.Protocol.SYSLOG_UDP);
        device.addConnection(syslog);
        AuditLoggerDeviceExtension ext = new AuditLoggerDeviceExtension();
        AuditLogger auditLogger = new AuditLogger("Audit Logger");
        auditLogger.addConnection(syslog);
        auditLogger.setAuditSourceTypeCodes("4");
        auditLogger.setAuditRecordRepositoryDevice(arrDevice);
        ext.addAuditLogger(auditLogger);
        device.addDeviceExtension(ext);
    }

    static HL7OrderScheduledStation newScheduledStation(Device unknown) {
        HL7OrderScheduledStation ss = new HL7OrderScheduledStation();
        ss.setCommonName("Default Scheduled Station");
        ss.setDevice(unknown);
        return ss;
    }

    private static void addHL7DeviceExtension(Device device, ConfigType configType, String archiveHost) {
        HL7DeviceExtension ext = new HL7DeviceExtension();
        device.addDeviceExtension(ext);

        HL7Application hl7App = new HL7Application("*");
        ArchiveHL7ApplicationExtension hl7AppExt = new ArchiveHL7ApplicationExtension();
        hl7App.addHL7ApplicationExtension(hl7AppExt);
        hl7App.setAcceptedMessageTypes(HL7_MESSAGE_TYPES);
        hl7App.setHL7DefaultCharacterSet("8859/1");
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

    private static void addArchiveDeviceExtension(Device device, Device unknown, ConfigType configType) {
        ArchiveDeviceExtension ext = new ArchiveDeviceExtension();
        device.addDeviceExtension(ext);
        ext.setFuzzyAlgorithmClass("org.dcm4che3.soundex.ESoundex");
        ext.setStorageID(STORAGE_ID);
        ext.setOverwritePolicy(OverwritePolicy.SAME_SOURCE);
        ext.setQueryRetrieveViewID(HIDE_REJECTED_VIEW.getViewID());
        if (configType == configType.TEST) {
            ext.setPersonNameComponentOrderInsensitiveMatching(true);
            ext.setMppsForwardDestinations(MPPS_FORWARD_DESTINATIONS);
            ext.setFallbackCMoveSCP("QRSCP");
            ext.setFallbackCMoveSCPDestination("DCM4CHEE");
            ext.setAlternativeCMoveSCP("DCM4CHEE");
            ext.setDeleteStudyBatchSize(20);
            ext.setDeletePatientOnDeleteLastStudy(true);
            ext.setMaxAccessTimeStaleness(MAX_ACCESS_TIME_STALENESS);
        }
        ext.addQueryRetrieveView(HIDE_REJECTED_VIEW);
        ext.addQueryRetrieveView(REGULAR_USE_VIEW);
        ext.addQueryRetrieveView(TRASH_VIEW);

        ext.setSendPendingCGet(SEND_PENDING_C_GET);
        ext.setSendPendingCMoveInterval(SEND_PENDING_C_MOVE_INTERVAL);
        ext.setWadoSupportedSRClasses(SR_CUIDS);
        ext.setWadoSR2HtmlTemplateURI(DSR2HTML_XSL);
        ext.setWadoSR2TextTemplateURI(DSR2TEXT_XSL);
        ext.setPatientUpdateTemplateURI(HL7_ADT2DCM_XSL);
        ext.setUnzipVendorDataToURI(UNZIP_VENDOR_DATA);
        ext.setQidoMaxNumberOfResults(QIDO_MAX_NUMBER_OF_RESULTS);
        ext.setIanTaskPollingInterval(IAN_TASK_POLLING_INTERVAL);
        ext.setHl7PSUTaskPollingInterval(HL7PSU_TASK_POLLING_INTERVAL);
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

        ext.setRejectExpiredStudiesPollingInterval(REJECT_EXPIRED_STUDIES_POLLING_INTERVAL);
        ext.setRejectExpiredStudiesPollingStartTime(REJECT_EXPIRED_STUDIES_START_TIME);
        ext.setRejectExpiredStudiesAETitle(REJECT_EXPIRED_STUDIES_AE_TITLE);
        ext.setRejectExpiredStudiesFetchSize(REJECT_EXPIRED_STUDIES_SERIES_FETCH_SIZE);
        ext.setRejectExpiredSeriesFetchSize(REJECT_EXPIRED_STUDIES_SERIES_FETCH_SIZE);

        ext.setAttributeFilter(Entity.Patient, newAttributeFilter(PATIENT_ATTRS, Attributes.UpdatePolicy.SUPPLEMENT));
        ext.setAttributeFilter(Entity.Study, newAttributeFilter(STUDY_ATTRS, Attributes.UpdatePolicy.MERGE));
        ext.setAttributeFilter(Entity.Series, newAttributeFilter(SERIES_ATTRS, Attributes.UpdatePolicy.MERGE));
        ext.setAttributeFilter(Entity.Instance, new AttributeFilter(INSTANCE_ATTRS));
        ext.setAttributeFilter(Entity.MPPS, new AttributeFilter(MPPS_ATTRS));
        ext.setAttributeFilter(Entity.MWL, new AttributeFilter(MWL_ATTRS));


        ext.addHL7OrderScheduledStation(newScheduledStation(unknown));

        if (configType == configType.TEST) {
            ext.getAttributeFilter(Entity.Patient).setCustomAttribute1(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"1\"]"));
            ext.getAttributeFilter(Entity.Patient).setCustomAttribute2(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"2\"]"));
            ext.getAttributeFilter(Entity.Patient).setCustomAttribute3(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"3\"]"));
            ext.getAttributeFilter(Entity.Study).setCustomAttribute1(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"1\"]"));
            ext.getAttributeFilter(Entity.Study).setCustomAttribute2(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"2\"]"));
            ext.getAttributeFilter(Entity.Study).setCustomAttribute3(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"3\"]"));
            ext.getAttributeFilter(Entity.Series).setCustomAttribute1(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"1\"]"));
            ext.getAttributeFilter(Entity.Series).setCustomAttribute2(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"2\"]"));
            ext.getAttributeFilter(Entity.Series).setCustomAttribute3(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"3\"]"));
            ext.getAttributeFilter(Entity.Instance).setCustomAttribute1(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"1\"]"));
            ext.getAttributeFilter(Entity.Instance).setCustomAttribute2(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"2\"]"));
            ext.getAttributeFilter(Entity.Instance).setCustomAttribute3(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"3\"]"));
            ext.getAttributeFilter(Entity.MPPS).setCustomAttribute1(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"1\"]"));
            ext.getAttributeFilter(Entity.MPPS).setCustomAttribute2(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"2\"]"));
            ext.getAttributeFilter(Entity.MPPS).setCustomAttribute3(ValueSelector.valueOf("DicomAttribute[@tag=\"0020000D\"]/Value[@number=\"3\"]"));
        }

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
        if (configType == configType.TEST) {
            storageDescriptor.setDeleterThresholdsFromStrings("1GB", "1TB");
        }
        ext.addStorageDescriptor(storageDescriptor);

        for (QueueDescriptor descriptor : QUEUE_DESCRIPTORS)
            ext.addQueueDescriptor(descriptor);

        for (HL7OrderSPSStatus hl7OrderSPSStatus : HL7_ORDER_SPS_STATUSES)
            ext.addHL7OrderSPSStatus(hl7OrderSPSStatus);

        ext.addMetadataFilter(createMetadataFilter());

        ext.addRejectionNote(createRejectionNote("Quality",
                RejectionNote.Type.REJECTED_FOR_QUALITY_REASONS,
                REJECTED_FOR_QUALITY_REASONS,
                RejectionNote.AcceptPreviousRejectedInstance.IGNORE));
        ext.addRejectionNote(createRejectionNote("Patient Safety",
                RejectionNote.Type.REJECTED_FOR_PATIENT_SAFETY_REASONS,
                REJECT_FOR_PATIENT_SAFETY_REASONS,
                RejectionNote.AcceptPreviousRejectedInstance.REJECT,
                REJECTED_FOR_QUALITY_REASONS));
        ext.addRejectionNote(createRejectionNote("Incorrect MWL Entry",
                RejectionNote.Type.INCORRECT_MODALITY_WORKLIST_ENTRY,
                INCORRECT_MODALITY_WORKLIST_ENTRY,
                RejectionNote.AcceptPreviousRejectedInstance.REJECT,
                REJECTED_FOR_QUALITY_REASONS, REJECT_FOR_PATIENT_SAFETY_REASONS));
        RejectionNote retentionExpired = createRejectionNote("Retention Expired",
                RejectionNote.Type.DATA_RETENTION_POLICY_EXPIRED,
                DATA_RETENTION_POLICY_EXPIRED,
                RejectionNote.AcceptPreviousRejectedInstance.RESTORE,
                REJECTED_FOR_QUALITY_REASONS, REJECT_FOR_PATIENT_SAFETY_REASONS, INCORRECT_MODALITY_WORKLIST_ENTRY);
        retentionExpired.setDeleteRejectedInstanceDelay(DELETE_REJECTED_INSTANCE_DELAY);
        retentionExpired.setDeleteRejectionNoteDelay(DELETE_REJECTED_INSTANCE_DELAY);
        ext.addRejectionNote(retentionExpired);
        ext.addRejectionNote(createRejectionNote("Revoke Rejection",
                RejectionNote.Type.REVOKE_REJECTION,
                REVOKE_REJECTION, null,
                REJECTION_CODES));
        ext.setHideSPSWithStatusFrom(HIDE_SPS_WITH_STATUS_FROM_MWL);

        if (configType == configType.SAMPLE || configType == configType.TEST) {
            ext.setMetadataStorageID(METADATA_STORAGE_ID);
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
            ext.setSeriesMetadataStorageID(SERIES_METADATA_STORAGE_ID);
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

            ExporterDescriptor exportDescriptor = new ExporterDescriptor(DICOM_EXPORTER_ID);
            exportDescriptor.setDescription(DICOM_EXPORTER_DESC);
            exportDescriptor.setExportURI(DICOM_EXPORT_URI);
            exportDescriptor.setSchedules(
                    ScheduleExpression.valueOf("hour=18-6 dayOfWeek=*"),
                    ScheduleExpression.valueOf("hour=* dayOfWeek=0,6"));
            exportDescriptor.setQueueName("Export1");
            exportDescriptor.setAETitle("DCM4CHEE");
            ext.addExporterDescriptor(exportDescriptor);

            ExportRule exportRule = new ExportRule("Forward to STORESCP");
            exportRule.getConditions().setSendingAETitle("FORWARD");
            exportRule.getConditions().setCondition("Modality", "CT|MR");
            exportRule.setEntity(Entity.Series);
            exportRule.setExportDelay(Duration.parse("PT1M"));
            exportRule.setExporterIDs(DICOM_EXPORTER_ID);
            ext.addExportRule(exportRule);

            ExporterDescriptor wadoExportDescriptor = new ExporterDescriptor(WADO_EXPORTER_ID);
            wadoExportDescriptor.setDescription(WADO_EXPORTER_DESC);
            wadoExportDescriptor.setExportURI(WADO_EXPORT_URI);
            wadoExportDescriptor.setQueueName("Export2");
            wadoExportDescriptor.setAETitle("DCM4CHEE");
            wadoExportDescriptor.setProperty("Cache-Control", WADO_CACHE_CONTROL);
            wadoExportDescriptor.setProperty("StorageID", WADO_JPEG_STORAGE_ID);
            wadoExportDescriptor.setProperty("URL.1", WADO_JSON_EXPORT_URL);
            wadoExportDescriptor.setProperty("Accept.1", WADO_JSON_ACCEPT);
            wadoExportDescriptor.setProperty("StorageID.1", WADO_JSON_STORAGE_ID);
            ext.addExporterDescriptor(wadoExportDescriptor);

            ExportRule wadoExportRule = new ExportRule("Forward to WADO");
            wadoExportRule.getConditions().setSendingAETitle("WADO");
            wadoExportRule.setEntity(Entity.Series);
            wadoExportRule.setExportDelay(Duration.parse("PT1M"));
            wadoExportRule.setExporterIDs(WADO_EXPORTER_ID);
            ext.addExportRule(wadoExportRule);

            HL7ForwardRule hl7ForwardRule = new HL7ForwardRule("Forward to HL7RCV|DCM4CHEE");
            hl7ForwardRule.getConditions().setCondition("MSH-3", "FORWARD");
            hl7ForwardRule.setDestinations(PIX_MANAGER);
            ext.addHL7ForwardRule(hl7ForwardRule);

            ext.addCompressionRule(JPEG_BASELINE);
            ext.addCompressionRule(JPEG_EXTENDED);
            ext.addCompressionRule(JPEG_LOSSLESS);
            ext.addCompressionRule(JPEG_LS);
            ext.addCompressionRule(JPEG_2000);

            ext.addStudyRetentionPolicy(THICK_SLICE);
            ext.addStudyRetentionPolicy(THIN_SLICE);

            ext.addAttributeCoercion(createAttributeCoercion(
                    "Ensure PID", Dimse.C_STORE_RQ, SCU, "ENSURE_PID", ENSURE_PID, null, null, configType));
            ext.addAttributeCoercion(createAttributeCoercion(
                    "Merge MWL", Dimse.C_STORE_RQ, SCU, "MERGE_MWL", null, null,
                    MergeMWLMatchingKey.StudyInstanceUID, configType));
            ext.addAttributeCoercion(createAttributeCoercion(
                    "Nullify PN", Dimse.C_STORE_RQ, SCP, "NULLIFY_PN", NULLIFY_PN, null, null, configType));
            ext.addAttributeCoercion(createAttributeCoercion(
                    "Leading DCMQRSCP", Dimse.C_STORE_RQ, SCP, "LEADING_DCMQRSCP", null, "DCMQRSCP", null, configType));

            StoreAccessControlIDRule storeAccessControlIDRule =
                    new StoreAccessControlIDRule("StoreAccessControlIDRule1");
            storeAccessControlIDRule.getConditions().setSendingAETitle("ACCESS_CONTROL");
            storeAccessControlIDRule.setStoreAccessControlID("ACCESS_CONTROL_ID");
            ext.addStoreAccessControlIDRule(storeAccessControlIDRule);
        }
    }

    private static MetadataFilter createMetadataFilter() {
        MetadataFilter filter = new MetadataFilter("AttributeFilters");
        int[] tags = new int[PATIENT_ATTRS.length + STUDY_ATTRS.length + SERIES_ATTRS.length + INSTANCE_ATTRS.length - 3];
        int destPos = 0;
        System.arraycopy(PATIENT_ATTRS, 0, tags, destPos, PATIENT_ATTRS.length);
        System.arraycopy(STUDY_ATTRS, 1, tags, destPos += PATIENT_ATTRS.length, STUDY_ATTRS.length - 1);
        System.arraycopy(SERIES_ATTRS, 1, tags, destPos += STUDY_ATTRS.length - 1, SERIES_ATTRS.length - 1);
        System.arraycopy(INSTANCE_ATTRS, 1, tags, destPos += SERIES_ATTRS.length - 1, INSTANCE_ATTRS.length - 1);
        filter.setSelection(tags);
        return filter;
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
                                              boolean storeSCP, boolean storeSCU, boolean mwlSCP,
                                              ConfigType configType, String... acceptedUserRoles) {
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
        addTCs(ae, EnumSet.allOf(QueryOption.class), SCP, QUERY_CUIDS, UID.ImplicitVRLittleEndian);
        if (mwlSCP) {
            addTCs(ae, EnumSet.allOf(QueryOption.class), SCP, MWL_CUID, UID.ImplicitVRLittleEndian);
        }
        if (storeSCU) {
            addTCs(ae, EnumSet.of(QueryOption.RELATIONAL), SCP, RETRIEVE_CUIDS, UID.ImplicitVRLittleEndian);
            for (int i = 0; i < CUIDS_TSUIDS.length; i++, i++)
                addTCs(ae, null, SCU, CUIDS_TSUIDS[i], CUIDS_TSUIDS[i + 1]);
            addTC(ae, null, SCU, UID.StorageCommitmentPushModelSOPClass, UID.ImplicitVRLittleEndian);
        }
        if (storeSCP) {
            for (int i = 0; i < CUIDS_TSUIDS.length; i++, i++)
                addTCs(ae, null, SCP, CUIDS_TSUIDS[i], CUIDS_TSUIDS[i+1]);
            addTC(ae, null, SCP, UID.StorageCommitmentPushModelSOPClass, UID.ImplicitVRLittleEndian);
            addTC(ae, null, SCP, UID.ModalityPerformedProcedureStepSOPClass, UID.ImplicitVRLittleEndian);
            addTC(ae, null, SCU, UID.ModalityPerformedProcedureStepSOPClass, UID.ImplicitVRLittleEndian);
            addTC(ae, null, SCU, UID.InstanceAvailabilityNotificationSOPClass, UID.ImplicitVRLittleEndian);
        }
        aeExt.setQueryRetrieveViewID(qrView.getViewID());
        aeExt.setAcceptedUserRoles(acceptedUserRoles);
        if (configType == configType.TEST) {
            aeExt.setStorageID(STORAGE_ID);
            aeExt.setStoreAccessControlID("*");
            aeExt.setAccessControlIDs(ACCESS_CONTROL_IDS);
            aeExt.setOverwritePolicy(OverwritePolicy.SAME_SOURCE);
            aeExt.setPersonNameComponentOrderInsensitiveMatching(true);
            aeExt.setSendPendingCGet(SEND_PENDING_C_GET);
            aeExt.setSendPendingCMoveInterval(SEND_PENDING_C_MOVE_INTERVAL);
            aeExt.setWadoSR2HtmlTemplateURI(DSR2HTML_XSL);
            aeExt.setWadoSR2TextTemplateURI(DSR2TEXT_XSL);
            aeExt.setQidoMaxNumberOfResults(QIDO_MAX_NUMBER_OF_RESULTS);
            aeExt.setMppsForwardDestinations(MPPS_FORWARD_DESTINATIONS);
            aeExt.setFallbackCMoveSCPDestination("DCM4CHEE");
            aeExt.setAlternativeCMoveSCP("DCM4CHEE");
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
