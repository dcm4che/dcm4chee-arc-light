myApp.constant("$modalities",{
"common":{
  "CR":"Computed Radiography",
  "CT":"Computed Tomography",
  "DX":"Digital Radiography",
  "KO":"Key Object Selection",
  "MR":"Magnetic Resonance",
  "MG":"Mammography",
  "NM":"Nuclear Medicine",
  "OT":"Other",
  "PT":"Positron emission tomography (PET)",
  "PR":"Presentation State",
  "US":"Ultrasound",
  "XA":"X-Ray Angiography"
},
"more":{
  "AR":"Autorefraction",
  "AU":"Audio",
  "BDUS":"Bone Densitometry (ultrasound)",
  "BI":"Biomagnetic imaging",
  "BMD":"Bone Densitometry (X-Ray)",
  "DOC":"Document",
  "DG":"Diaphanography",
  "ECG":"Electrocardiography",
  "EPS":"Cardiac Electrophysiology",
  "ES":"Endoscopy",
  "FID":"Fiducials",
  "GM":"General Microscopy",
  "HC":"Hard Copy",
  "HD":"Hemodynamic Waveform",
  "IO":"Intra-Oral Radiography",
  "IOL":"Intraocular Lens Data",
  "IVOCT":"Intravascular Optical Coherence Tomography",
  "IVUS":"Intravascular Ultrasound",
  "KER":"Keratometry",
  "LEN":"Lensometry",
  "LS":"Laser surface scan",
  "OAM":"Ophthalmic Axial Measurements",
  "OCT":"Optical Coherence Tomography (non-Ophthalmic)",
  "OP":"Ophthalmic Photography",
  "OPM":"Ophthalmic Mapping",
  "OPT":"Ophthalmic Tomography",
  "OPV":"Ophthalmic Visual Field",
  "OSS":"Optical Surface Scan",
  "PLAN":"Plan",
  "PX":"Panoramic X-Ray",
  "REG":"Registration",
  "RESP":"Respiratory Waveform",
  "RF":"Radio Fluoroscopy",
  "RG":"Radiographic imaging (conventional film/screen)",
  "RTDOSE":"Radiotherapy Dose",
  "RTIMAGE":"Radiotherapy Image",
  "RTPLAN":"Radiotherapy Plan",
  "RTRECORD":"RT Treatment Record",
  "RTSTRUCT":"Radiotherapy Structure Set",
  "RWV":"Real World Value Map",
  "SEG":"Segmentation",
  "SM":"Slide Microscopy",
  "SMR":"Stereometric Relationship",
  "SR":"SR Document",
  "SRF":"Subjective Refraction",
  "STAIN":"Automated Slide Stainer",
  "TG":"Thermography",
  "VA":"Visual Acuity",
  "XC":"External-camera Photography"
}
});
myApp.constant("$select", 
  {
      "dicomNetworkConnection":{
        "title" : "Network Connection",
        "optionRef" : ["dicomNetworkConnection"],
        "optionValue": "cn",
        "type": "array",
        "parentOf" : ["dcmNetworkConnection"],
        "required":{
          "cn": "Conneciton name",
          "dicomHostname":"Hostname"
        }
      },
      "dcmNetworkConnection":{
        "title" : "dcm4che Network Connection Attributes",
        "optionRef" : ["dicomNetworkConnection","dcmNetworkConnection"],
        "optionValue": "dcmProtocol",
        "type": "object"
      },    
      "dicomNetworkAE":{
        "title" : "Network AE",
        "optionRef" : ["dicomNetworkAE"],
        "optionValue" : "dicomAETitle",
        "type": "array",
        "parentOf" : [
          "dicomTransferCapability",
          "dcmArchiveNetworkAE",
          "dcmNetworkAE",
          // "dcmExportRule",
          "dcmArchiveCompressionRule",
          "dcmArchiveAttributeCoercion",
          "dcmStudyRetentionPolicy"
        ],
        "required":{
          "dicomAETitle": "AE Title",
          "dicomNetworkConnectionReference": "Network Connection Reference",
          "dicomAssociationInitiator": "Association Initiator",
          "dicomAssociationAcceptor": "Association Acceptor"
        },
        "requiredPart":[
          "dicomNetworkConnection"
        ]
      },
      "dicomTransferCapability":{
        "title" : "Transfer Capability",
        "optionRef" : ["dicomNetworkAE","dicomTransferCapability"],
        "optionValue": "cn",
        "type": "array",
        "required":{
          "cn": "Transfare Capability name",
          "dicomSOPClass": "SOP Class",
          "dicomTransferRole": "Transfer Role",
          "dicomTransferSyntax": "Transfer Syntax"
        }
      },
      "dcmArchiveNetworkAE":{
        "title" : "Archive Network AE",
        "optionRef" : ["dicomNetworkAE","dcmArchiveNetworkAE"],
        "optionValue" : "dcmStorageID",
        "type": "object",
        "parentOf" : [
          "dcmExportRule",
          "dcmArchiveCompressionRule",
          "dcmArchiveAttributeCoercion",
          "dcmStudyRetentionPolicy"
        ],
      },
      // "dcmExportRule":{
      //   "title" : "Export Rule",
      //   "optionRef" : ["dicomNetworkAE","dcmArchiveNetworkAE","dcmExportRule"],
      //   "optionValue" : "cn",
      //   "type": "array"
      // },
      // "dcmArchiveCompressionRule":{
      //   "title" : "Archive Compression rule",
      //   "optionRef" : ["dicomNetworkAE","dcmArchiveNetworkAE","dcmArchiveCompressionRule"],
      //   "optionValue" : "cn",
      //   "type": "array"
      // },
      // "dcmArchiveAttributeCoercion":{
      //   "title" : "Archive Attribute Coercion",
      //   "optionRef" : ["dicomNetworkAE","dcmArchiveNetworkAE","dcmArchiveAttributeCoercion"],
      //   "optionValue" : "cn",
      //   "type": "array"
      // },
      // "dcmStudyRetentionPolicy":{
      //   "title" : "Study Retention Policy",
      //   "optionRef" : ["dicomNetworkAE","dcmArchiveNetworkAE","dcmStudyRetentionPolicy"],
      //   "optionValue" : "cn",
      //   "type": "array"
      // },
      "dcmNetworkAE":{
        "title" : "dcm4che Network AE Attributes",
        "optionRef" : ["dicomNetworkAE","dcmNetworkAE"],
        "optionValue" : "dcmAcceptedCallingAETitle",
        "type": "object"
      },
      "hl7Application":{
        "title" : "HL7 Applications",
        "optionRef" : ["hl7Application"],
        "optionValue": "hl7ApplicationName",
        "type": "array",
        "parentOf" : ["dcmArchiveHL7Application"],
        "required":{
          "hl7ApplicationName": "HL7 Application name",
          "dicomNetworkConnectionReference": "Network Connection Reference"
        },
        "requiredPart":[
          "dicomNetworkConnection"
        ]
      
      },
      "dcmArchiveHL7Application":{
        "title" : "Archive HL7 Application",
        "optionRef" : ["hl7Application","dcmArchiveHL7Application"],
        "optionValue": "hl7ApplicationName",
        "parentOf" : ["hl7ForwardRule"],
        "type": "object"
      
      },
      "dcmImageWriter":{
        "title" : "Image Writers",
        "optionRef" : ["dcmImageWriter"],
        "optionValue": "dicomTransferSyntax",
        "type": "array",
        "required":{
          "dicomTransferSyntax": "Transfer Syntax",
          "dcmIIOFormatName": "Image IO Writer Format Name"
        }
      
      },
      "dcmImageReader":{
        "title" : "Image Readers",
        "optionRef" : ["dcmImageReader"],
        "optionValue": "dicomTransferSyntax",
        "type": "array",
        "required":{
          "dicomTransferSyntax": "Transfer Syntax",
          "dcmIIOFormatName": "Image IO Reader Format Name"
        }
      
      },
      "dcmAuditLogger":{
        "title" : "Audit Logger",
        "optionRef" : ["dcmAuditLogger"],
        "optionValue": "dicomNetworkConnectionReference",
        "type": "object",
        "parentOf": [
          "dcmAuditSuppressCriteria"
        ],
        "requiredPart":[
          "dicomNetworkConnection"
        ]
      },
      "dcmAuditSuppressCriteria":{
        "title" : "Audit Suppress Criteria",
        "optionRef" : ["dcmAuditLogger","dcmAuditSuppressCriteria"],
        "optionValue": "cn",
        "type": "array",        
        "required":{
          "cn": "Name"
        }
      },
      "dcmAuditRecordRepository":{
        "title" : "Audit Record Repository",
        "optionRef" : ["dcmAuditRecordRepository"],
        "optionValue": "dicomNetworkConnectionReference",
        "type": "object",
        "requiredPart":[
          "dicomNetworkConnection"
        ]
      
      },
      "dcmArchiveDevice":{
        "title" : "Archive Device",
        "optionRef" : ["dcmArchiveDevice"],
        "optionValue": "dcmFuzzyAlgorithmClass",
        "type": "object",
        "parentOf": [
          "dcmAttributeFilter",
          "dcmStorage",
          "dcmQueryRetrieveView",
          "dcmQueue",
          "dcmExporter",
          "dcmExportRule",
          "dcmArchiveCompressionRule",
          "dcmArchiveAttributeCoercion",
          "dcmRejectionNote",
          "dcmStudyRetentionPolicy",
          "dcmIDGenerator",
          "hl7ForwardRule"
        ]
      
      },
      "dcmAttributeFilter":{
        "title" : "Attribute List",
        "optionRef" : ["dcmArchiveDevice","dcmAttributeFilter"],
        "optionValue": "dcmEntity",
        "type": "array",
        "required":{
          "dcmEntity": "Attribute Entity",
          "dcmTag": "Attribute Tag"
        }
      
      },
      "dcmStorage":{
        "title" : "Storage Descriptor",
        "optionRef" : ["dcmArchiveDevice","dcmStorage"],
        "optionValue": "dcmStorageID",
        "type": "array",
        "required":{
          "dcmStorageID": "Storage ID",
          "dcmURI": "Storage URI"
        }
      
      },
      "dcmQueryRetrieveView":{
        "title" : "Query Retrieve View",
        "optionRef" : ["dcmArchiveDevice","dcmQueryRetrieveView"],
        "optionValue": "dcmQueryRetrieveViewID",
        "type": "array",
        "required":{
          "dcmQueryRetrieveViewID": "Query/Retrieve View ID"
        }
      
      },
      "dcmQueue":{
        "title" : "Managed JMS Queue",
        "optionRef" : ["dcmArchiveDevice","dcmQueue"],
        "optionValue": "dcmQueueName",
        "type": "array",
        "required":{
          "dcmQueueName": "Queue Name",
          "dcmJndiName": "JNDI Name"
        }
      },
      "dcmExporter":{
        "title" : "Exporter Descriptor",
        "optionRef" : ["dcmArchiveDevice","dcmExporter"],
        "optionValue": "dcmExporterID",
        "type": "array",
        "required":{
          "dcmExporterID": "Exporter ID",
          "dcmURI": "URI",
          "dcmQueueName": "dcmQueueName"
        }
      
      },
      "dcmExportRule":{
        "title" : "Export Rule",
        "optionRef" : ["dcmArchiveDevice","dcmExportRule"],
        "optionValue": "cn",
        "type": "array",
        "required":{
          "cn": "Name",
          "dcmEntity": "Attribute Entity",
          "dcmExporterID": "Exporter ID"
        }
      
      },
      "dcmArchiveCompressionRule":{
        "title" : "Archive Compression rule",
        "optionRef" : ["dcmArchiveDevice","dcmArchiveCompressionRule"],
        "optionValue": "cn",
        "type": "array",
        "required":{
          "cn": "Name",
          "dicomTransferSyntax": "DICOM Transfer Syntax UID"
        }
      
      },
      "dcmArchiveAttributeCoercion":{
        "title" : "Archive Attribute Coercion",
        "optionRef" : ["dcmArchiveDevice","dcmArchiveAttributeCoercion"],
        "optionValue": "cn",
        "type": "array",
        "required":{
          "cn": "Name",
          "dcmDIMSE": "DIMSE",
          "dicomTransferRole": "DICOM Transfer Role"
        }
      },
      "dcmRejectionNote":{
        "title" : "Rejection Note",
        "optionRef" : ["dcmArchiveDevice", "dcmRejectionNote"],
        "optionValue": "dcmRejectionNoteLabel",
        "type": "array",
        "required":{
          "dcmRejectionNoteLabel": "Rejection Note Label",
          "dcmRejectionNoteCode": "Rejection Note Code"
        }
      
      },
      "dcmStudyRetentionPolicy":{
        "title" : "Study Retention Policy",
        "optionRef" : ["dcmArchiveDevice", "dcmStudyRetentionPolicy"],
        "optionValue": "cn",
        "type": "array",
        "required":{
          "cn": "Name",
          "dcmRetentionPeriod": "Study Retention Period"
        }
      
      },
      "dcmIDGenerator":{
        "title" : "ID Generator",
        "optionRef" : ["dcmArchiveDevice", "dcmIDGenerator"],
        "optionValue": "dcmIDGeneratorName",
        "type": "array",
        "required":{
          "dcmIDGeneratorName": "ID Generator Name",
          "dcmIDGeneratorFormat": "ID Generator Format"
        }
      
      },
      "hl7ForwardRule":{
        "title" : "HL7 Forward Rule",
        "optionRef" : ["dcmArchiveDevice", "hl7ForwardRule"],
        "optionValue": "cn",
        "type": "array",
        "required":{
          "cn": "Name",
          "hl7FwdApplicationName": "HL7 Forward Application Name"
        }
      
      }
    }
);