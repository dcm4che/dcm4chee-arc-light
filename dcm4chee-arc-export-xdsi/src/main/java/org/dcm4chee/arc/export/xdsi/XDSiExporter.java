/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2019
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.export.xdsi;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.dcm4che3.data.*;
import org.dcm4che3.dcmr.AcquisitionModality;
import org.dcm4che3.dcmr.AnatomicRegion;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4che3.xdsi.*;

import javax.xml.bind.JAXBElement;
import javax.xml.ws.soap.AddressingFeature;
import javax.xml.ws.soap.MTOMFeature;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

import static org.dcm4che3.xdsi.XDSConstants.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2017
 */
public class XDSiExporter extends AbstractExporter {

    private static final String SUBMISSION_SET_ID = "SubmissionSet01";
    private static final String DOCUMENT_ID = "Document01";
    private static final ObjectFactory rimFactory = new ObjectFactory();
    private static final String DEFAULT_SOURCE_ID = "1.3.6.1.4.1.21367.2011.2.1.331";
    private static final String DEFAULT_LANGUAGE_CODE = "en-us";
    private static final Code DEFAULT_CONTENT_TYPE = new Code(
            "UNSPECIFIED-CONTENT-TYPE",
            "1.3.6.1.4.1.21367.2017.3",
            null,
            "Unspecified Clinical Activity");
    private static final Code DEFAULT_TYPE_CODE = new Code(
            "18748-4",
            "2.16.840.1.113883.6.1",
            null,
            "Diagnostic imaging study");
    private static final Code DEFAULT_CLASS_CODE = new Code(
            "IMAGES",
            "1.3.6.1.4.1.19376.1.2.6.1",
            null,
            "Images");
    private static final Code DEFAULT_CONFIDENTIALITY_CODE = new Code(
            "N", 
            "2.16.840.1.113883.5.25",
            null,
            "Normal");
    private static final Code DEFAULT_MANIFEST_TITLE = new Code(
            "113030",
            "DCM",
            null,
            "Manifest");
    private static final Code DEFAULT_HEALTH_CARE_FACILITY_TYPE_CODE = new Code(
            "22232009",
            "2.16.840.1.113883.6.96",
            null,
            "Hospital");
    private static final Code DEFAULT_PRACTICE_SETTING_CODE = new Code(
            "Practice-A",
            "1.3.6.1.4.1.21367.2017.3",
            null,
            "Radiology");
    private static final Code MANIFEST_FORMAT_CODE = new Code(
            UID.KeyObjectSelectionDocumentStorage,
            UID.DICOMUIDRegistry, null,
            UID.KeyObjectSelectionDocumentStorage);

    private final DocumentRepositoryService service;
    private final QueryService queryService;
    private final Device device;
    private final String tlsProtocol;
    private final String[] cipherSuites;
    private final boolean disableCNCheck;
    private final boolean includeModalityCodes;
    private final boolean includeAnatomicRegionCodes;
    private final boolean useProcedureCodeAsTypeCode;
    private final String manifestLogDir;
    private final String sourceId;
    private final String assigningAuthorityOfPatientID;
    private final String assigningAuthorityOfAccessionNumber;
    private final Code manifestContentType;
    private final Code manifestTitle;
    private final int manifestSeriesNumber;
    private final int manifestInstanceNumber;
    private final Date now = new Date();
    private final String repositoryURL;
    private final String languageCode;
    private final Code classCode;
    private final Code confidentialityCode;
    private final Code healthCareFacilityTypeCode;
    private final Code practiceSettingCode;
    private final Code defTypeCode;
    private final List<String> sourcePatientInfo = new ArrayList<>();
    private final Set<String> referenceIdList = new HashSet<>();
    private final Map<Code.Key, Code> modalityCodes = new HashMap<>();
    private final Map<Code.Key, Code> anatomicRegionCodes = new HashMap<>();

    private String submissionSetUID;
    private Attributes manifest;
    private String documentUID;
    private String patientId;
    private String sourcePatientId;
    private Code typeCode;
    private int id;

    public XDSiExporter(ExporterDescriptor descriptor, DocumentRepositoryService service, QueryService queryService,
                        Device device) {
        super(descriptor);
        this.service = service;
        this.queryService = queryService;
        this.device = device;
        this.repositoryURL = descriptor.getExportURI().getSchemeSpecificPart();
        this.disableCNCheck = Boolean.parseBoolean(descriptor.getProperty("TLS.disableCNCheck", null));
        this.tlsProtocol = descriptor.getProperty("TLS.protocol", null);
        this.cipherSuites = StringUtils.split(descriptor.getProperty("TLS.cipherSuites", null), ',');
        this.manifestTitle = getCodeProperty("Manifest.title", DEFAULT_MANIFEST_TITLE);
        this.manifestSeriesNumber = Integer.parseInt(descriptor.getProperty("Manifest.seriesNumber", "0"));
        this.manifestInstanceNumber = Integer.parseInt(descriptor.getProperty("Manifest.instanceNumber", "0"));
        this.manifestLogDir = descriptor.getProperty("Manifest.logDir", null);
        this.patientId = descriptor.getProperty("XDSSubmissionSet.patientId", null);
        this.assigningAuthorityOfPatientID = descriptor.getProperty("AssigningAuthority.patientId", null);
        this.assigningAuthorityOfAccessionNumber = descriptor.getProperty("AssigningAuthority.accessionNumber", null);
        this.sourceId = descriptor.getProperty("XDSSubmissionSet.sourceId", DEFAULT_SOURCE_ID);
        this.manifestContentType = getCodeProperty("XDSSubmissionSet.contentType", DEFAULT_CONTENT_TYPE);
        this.defTypeCode = getCodeProperty("DocumentEntry.typeCode", DEFAULT_TYPE_CODE);
        this.languageCode = descriptor.getProperty("DocumentEntry.languageCode", DEFAULT_LANGUAGE_CODE);
        this.classCode = getCodeProperty("DocumentEntry.classCode", DEFAULT_CLASS_CODE);
        this.confidentialityCode = getCodeProperty("DocumentEntry.confidentialityCode", DEFAULT_CONFIDENTIALITY_CODE);
        this.healthCareFacilityTypeCode = getCodeProperty("DocumentEntry.healthCareFacilityTypeCode", DEFAULT_HEALTH_CARE_FACILITY_TYPE_CODE);
        this.practiceSettingCode = getCodeProperty("DocumentEntry.practiceSettingCode", DEFAULT_PRACTICE_SETTING_CODE);
        this.includeModalityCodes = Boolean.parseBoolean(descriptor.getProperty("DocumentEntry.includeModalityCodes", null));
        this.includeAnatomicRegionCodes = Boolean.parseBoolean(descriptor.getProperty("DocumentEntry.includeAnatomicRegionCodes", null));
        this.useProcedureCodeAsTypeCode = Boolean.parseBoolean(descriptor.getProperty("DocumentEntry.useProcedureCodeAsTypeCode", null));
    }

    private Code getCodeProperty(String name, Code defValue) {
        String value = descriptor.getProperty(name, null);
        return value != null ? new Code(value) : defValue;
    }

    @Override
    public Outcome export(ExportContext ctx) throws Exception {
        ApplicationEntity ae = device.getApplicationEntity(ctx.getAETitle(), true);
        Collection<Attributes> seriesAttrs = new ArrayList<>();
        this.manifest = queryService.createXDSiManifest(ae, ctx.getStudyInstanceUID(),
                descriptor.getRetrieveAETitles(), descriptor.getRetrieveLocationUID(),
                manifestTitle, manifestSeriesNumber, manifestInstanceNumber, seriesAttrs);
        this.documentUID = manifest.getString(Tag.SOPInstanceUID);
        this.submissionSetUID = UIDUtils.createUID();
        this.sourcePatientId = adjustSourcePatientId();
        if (patientId == null)
            patientId = sourcePatientId;
        this.typeCode = typeCodeOf(manifest);
        initSourcePatientInfo();
        referenceIdList.add(manifest.getString(Tag.StudyInstanceUID) + "^^^^" + CXI_TYPE_STUDY_INSTANCE_UID);
        addAccessionNumber(manifest);
        processSeriesAttrs(seriesAttrs);
        ctx.setXDSiManifest(manifest);
        ctx.setSubmissionSetUID(submissionSetUID);
        try {
            if (manifestLogDir != null) {
                File logDir = new File(manifestLogDir);
                logDir.mkdirs();
                try (DicomOutputStream out = new DicomOutputStream(new File(logDir, documentUID))) {
                    out.writeDataset(manifest.createFileMetaInformation(UID.ExplicitVRLittleEndian), manifest);
                }
            }
            RegistryResponseType rsp = port().documentRepositoryProvideAndRegisterDocumentSetB(createRequest());
            ctx.setXDSiRegistryResponse(rsp);
            switch (rsp.getStatus()) {
                case XDS_STATUS_SUCCESS:
                    return new Outcome(QueueMessage.Status.COMPLETED,
                            "Provide and Register Study[" + ctx.getStudyInstanceUID()
                                    + "] in SubmissionSet[" + submissionSetUID
                                    + "] @ " + repositoryURL + " successful");
                case XDS_STATUS_PARTIAL_SUCCESS:
                    return new Outcome(QueueMessage.Status.WARNING,
                            "Provide and Register Study[" + ctx.getStudyInstanceUID()
                                    + "] in SubmissionSet[" + submissionSetUID
                                    + "] @ " + repositoryURL + " partial successful - "
                                    + getRegistryErrorMessage(rsp));
            }
            throw new Exception("Provide and Register Study[" + ctx.getStudyInstanceUID()
                    + "] @ " + repositoryURL + " failed - " + getRegistryErrorMessage(rsp));
        } catch (Exception e) {
            ctx.setException(e);
            throw e;
        }
    }

    private Code typeCodeOf(Attributes manifest) {
        if (useProcedureCodeAsTypeCode) {
            Attributes codeItem = manifest.getNestedDataset(Tag.ProcedureCodeSequence);
            if (codeItem != null)
                return new Code(codeItem);
        }
        return defTypeCode;
    }

    private void addAccessionNumber(Attributes attrs) {
        IDWithIssuer accno = IDWithIssuer.valueOf(attrs, Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence);
        if (accno != null) {
            Issuer issuer = accno.getIssuer();
            String uid = issuer != null && "ISO".equals(issuer.getUniversalEntityIDType())
                    ? issuer.getUniversalEntityID()
                    : assigningAuthorityOfAccessionNumber;
            referenceIdList.add(uid == null
                    ? accno.getID() + "^^^^" + CXI_TYPE_ACCESSION
                    : accno.getID() + "^^^&" + uid + "&ISO^" + CXI_TYPE_ACCESSION);
        }
    }

    private void processSeriesAttrs(Collection<Attributes> seriesAttrs) {
        for (Attributes attrs : seriesAttrs) {
            if (includeModalityCodes) {
                Code modalityCode = AcquisitionModality.codeOf(attrs.getString(Tag.Modality));
                if (modalityCode != null)
                    modalityCodes.put(modalityCode.key(), modalityCode);
            }
            if (includeAnatomicRegionCodes) {
                Attributes anatomicRegionCodeItem = attrs.getNestedDataset(Tag.AnatomicRegionSequence);
                Code anatomicRegionCode = anatomicRegionCodeItem != null
                        ? new Code(anatomicRegionCodeItem)
                        : AnatomicRegion.codeOf(attrs.getString(Tag.BodyPartExamined));
                if (anatomicRegionCode != null)
                    anatomicRegionCodes.put(anatomicRegionCode.key(), anatomicRegionCode);
            }
            Sequence reqAttrsSeq = attrs.getSequence(Tag.RequestAttributesSequence);
            if (reqAttrsSeq != null)
                for (Attributes reqAttrs : reqAttrsSeq) {
                    addAccessionNumber(reqAttrs);
                }
        }
    }

    private String getRegistryErrorMessage(RegistryResponseType rsp) {
        StringBuilder sb = null;
        for (RegistryError registryError : rsp.getRegistryErrorList().getRegistryError()) {
            if (sb == null)
                sb = new StringBuilder(registryError.getCodeContext());
            else
                sb.append(", ").append(registryError.getCodeContext());
        }
        return sb != null ? sb.toString() : "";
    }

    private String adjustSourcePatientId() {
        IDWithIssuer pid = IDWithIssuer.pidOf(manifest);
        Issuer issuer = pid.getIssuer();
        String iuid = issuer != null && "ISO".equals(issuer.getUniversalEntityIDType())
                ? issuer.getUniversalEntityID()
                : assigningAuthorityOfPatientID;
        if (iuid != null) {
             pid.setIssuer(new Issuer(null, iuid, "ISO"));
             pid.exportPatientIDWithIssuer(manifest);
        }
        return pid.toString();
    }

    private DocumentRepositoryPortType port() throws Exception {
        DocumentRepositoryPortType port = service.getDocumentRepositoryPortSoap12(
                new AddressingFeature(true, true),
                new MTOMFeature());
        XDSUtils.ensureMustUnderstandHandler(port);
        XDSUtils.setEndpointAddress(port, repositoryURL);
        if (repositoryURL.startsWith("https"))
            XDSUtils.setTlsClientParameters(port, tlsClientParams());
        return port;
    }

    private TLSClientParameters tlsClientParams() throws GeneralSecurityException, IOException {
        TLSClientParameters params = new TLSClientParameters();
        params.setKeyManagers(device.keyManagers());
        params.setTrustManagers(device.trustManagers());
        params.setSecureSocketProtocol(tlsProtocol);
        for (String cipherSuite : cipherSuites)
            params.getCipherSuites().add(cipherSuite.trim());
        params.setDisableCNCheck(disableCNCheck);
        return params;
    }

    private ProvideAndRegisterDocumentSetRequestType createRequest() {
        ProvideAndRegisterDocumentSetRequestType pnrReq = new ProvideAndRegisterDocumentSetRequestType();
        pnrReq.setSubmitObjectsRequest(createSubmitObjectsRequest());
        pnrReq.getDocument().add(createDocument());
        return pnrReq;
    }

    private SubmitObjectsRequest createSubmitObjectsRequest() {
        SubmitObjectsRequest sor = new SubmitObjectsRequest();
        sor.setRegistryObjectList(createRegistryObjectList());
        return sor;
    }

    private RegistryObjectListType createRegistryObjectList() {
        RegistryObjectListType registryObjectList = new RegistryObjectListType();
        List<JAXBElement<? extends IdentifiableType>> identifiable = registryObjectList.getIdentifiable();
        identifiable.add(rimFactory.createExtrinsicObject(createDocumentEntry()));
        identifiable.add(rimFactory.createRegistryPackage(createSubmissionSet()));
        identifiable.add(rimFactory.createClassification(createSubmissionSetClassification()));
        identifiable.add(rimFactory.createAssociation(createAssociation()));
        return registryObjectList;
    }

    private String nextId() {
        return "id_" + ++id;
    }

    private ExtrinsicObjectType createDocumentEntry() {
        ExtrinsicObjectType docEntry = new ExtrinsicObjectType();
        docEntry.setId(DOCUMENT_ID);
        docEntry.setObjectType(UUID_XDSDocumentEntry);
        docEntry.setMimeType(MediaTypes.APPLICATION_DICOM);
        createDocumentEntrySlots(docEntry.getSlot());
        createDocumentEntryClassifications(docEntry.getClassification());
        createDocumentEntryExternalIdentifiers(docEntry.getExternalIdentifier());
        return docEntry;
    }

    private void createDocumentEntrySlots(List<SlotType1> list) {
        list.add(new SlotBuilder(SLOT_NAME_CREATION_TIME)
                .valueDTM(now).build());
        list.add(new SlotBuilder(SLOT_NAME_SERVICE_START_TIME)
                .valueDTM(manifest.getDate(Tag.StudyDateAndTime, now)).build());
        list.add(new SlotBuilder(SLOT_NAME_LANGUAGE_CODE)
                .valueList(languageCode).build());
        list.add(new SlotBuilder(SLOT_NAME_SOURCE_PATIENT_ID)
                .valueList(sourcePatientId).build());
        list.add(new SlotBuilder(SLOT_NAME_SOURCE_PATIENT_INFO)
                .valueList(sourcePatientInfo).build());
        list.add(new SlotBuilder(SLOT_NAME_REFERENCE_ID_LIST)
                .valueList(referenceIdList).build());
    }

    private void createDocumentEntryClassifications(List<ClassificationType> list) {
        list.add(new ClassificationBuilder(nextId())
                .classificationScheme(UUID_XDSDocumentEntry_classCode)
                .classifiedObject(DOCUMENT_ID)
                .code(classCode)
                .build());
        list.add(new ClassificationBuilder(nextId())
                .classificationScheme(UUID_XDSDocumentEntry_confidentialityCode)
                .classifiedObject(DOCUMENT_ID)
                .code(confidentialityCode)
                .build());
        list.add(new ClassificationBuilder(nextId())
                .classificationScheme(UUID_XDSDocumentEntry_formatCode)
                .classifiedObject(DOCUMENT_ID)
                .code(MANIFEST_FORMAT_CODE)
                .build());
        list.add(new ClassificationBuilder(nextId())
                .classificationScheme(UUID_XDSDocumentEntry_healthCareFacilityTypeCode)
                .classifiedObject(DOCUMENT_ID)
                .code(healthCareFacilityTypeCode)
                .build());
        list.add(new ClassificationBuilder(nextId())
                .classificationScheme(UUID_XDSDocumentEntry_practiceSettingCode)
                .classifiedObject(DOCUMENT_ID)
                .code(practiceSettingCode)
                .build());
        list.add(new ClassificationBuilder(nextId())
                .classificationScheme(UUID_XDSDocumentEntry_typeCode)
                .classifiedObject(DOCUMENT_ID)
                .code(typeCode)
                .build());
        for (Code code : modalityCodes.values()) {
            list.add(new ClassificationBuilder(nextId())
                    .classificationScheme(UUID_XDSDocumentEntry_eventCodeList)
                    .classifiedObject(DOCUMENT_ID)
                    .code(code)
                    .build());
        }
        for (Code code : anatomicRegionCodes.values()) {
            list.add(new ClassificationBuilder(nextId())
                    .classificationScheme(UUID_XDSDocumentEntry_eventCodeList)
                    .classifiedObject(DOCUMENT_ID)
                    .code(code)
                    .build());
        }
    }

    private void createDocumentEntryExternalIdentifiers(List<ExternalIdentifierType> list) {
        list.add(new ExternalIdentifierBuilder(nextId())
                .identificationScheme(UUID_XDSDocumentEntry_uniqueId)
                .registryObject(DOCUMENT_ID)
                .name("XDSDocumentEntry.uniqueId")
                .value(documentUID)
                .build());
        list.add(new ExternalIdentifierBuilder(nextId())
                .identificationScheme(UUID_XDSDocumentEntry_patientId)
                .registryObject(DOCUMENT_ID)
                .name("XDSDocumentEntry.patientId")
                .value(patientId)
                .build());
    }

    private void initSourcePatientInfo() {
        sourcePatientInfo.add("PID-3|" + sourcePatientId);
        addIfNotNullTo("PID-5|", manifest.getString(Tag.PatientName), sourcePatientInfo);
        addIfNotNullTo("PID-7|", manifest.getString(Tag.PatientBirthDate), sourcePatientInfo);
        addIfNotNullTo("PID-8|", manifest.getString(Tag.PatientSex), sourcePatientInfo);
    }

    private static void addIfNotNullTo(String prefix, String value, Collection<String> list) {
        if (value != null)
            list.add(prefix + value);
    }

    private RegistryPackageType createSubmissionSet() {
        RegistryPackageType submissionSet = new RegistryPackageType();
        submissionSet.setId(SUBMISSION_SET_ID);
        createSubmissionSetSlots(submissionSet.getSlot());
        createSubmissionSetClassifications(submissionSet.getClassification());
        createSubmissionSetExternalIdentifiers(submissionSet.getExternalIdentifier());
        return submissionSet;
    }

    private void createSubmissionSetSlots(List<SlotType1> list) {
        list.add(new SlotBuilder(SLOT_NAME_SUBMISSION_TIME)
                .valueDTM(now).build());
    }

    private void createSubmissionSetClassifications(List<ClassificationType> list) {
        list.add(new ClassificationBuilder(nextId())
                .classificationScheme(UUID_XDSSubmissionSet_contentTypeCode)
                .classifiedObject(SUBMISSION_SET_ID)
                .code(manifestContentType)
                .build());
    }

    private void createSubmissionSetExternalIdentifiers(List<ExternalIdentifierType> list) {
        list.add(new ExternalIdentifierBuilder(nextId())
                .identificationScheme(UUID_XDSSubmissionSet_uniqueId)
                .registryObject(SUBMISSION_SET_ID)
                .name("XDSSubmissionSet.uniqueId")
                .value(submissionSetUID)
                .build());
        list.add(new ExternalIdentifierBuilder(nextId())
                .identificationScheme(UUID_XDSSubmissionSet_patientId)
                .registryObject(SUBMISSION_SET_ID)
                .name("XDSSubmissionSet.patientId")
                .value(patientId)
                .build());
        list.add(new ExternalIdentifierBuilder(nextId())
                .identificationScheme(UUID_XDSSubmissionSet_sourceId)
                .registryObject(SUBMISSION_SET_ID)
                .name("XDSSubmissionSet.sourceId")
                .value(sourceId)
                .build());
    }

    private ClassificationType createSubmissionSetClassification() {
        return new ClassificationBuilder(nextId())
                .classificationNode(UUID_XDSSubmissionSet)
                .classifiedObject(SUBMISSION_SET_ID)
                .build();
    }

    private AssociationType1 createAssociation() {
        return new AssociationBuilder(nextId())
                .associationType(HAS_MEMBER)
                .sourceObject(SUBMISSION_SET_ID)
                .targetObject(DOCUMENT_ID)
                .submissionSetStatus("Original")
                .build();
    }

    private ProvideAndRegisterDocumentSetRequestType.Document createDocument() {
        ProvideAndRegisterDocumentSetRequestType.Document doc = new ProvideAndRegisterDocumentSetRequestType.Document();
        doc.setId(DOCUMENT_ID);
        doc.setValue(new DicomDataHandler(manifest));
        return doc;
    }

}
