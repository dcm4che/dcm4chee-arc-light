export class Globalvar {
    public static get MODALITIES(): any {
        return {
            'common': {
                'CR': 'Computed Radiography',
                'CT': 'Computed Tomography',
                'DX': 'Digital Radiography',
                'KO': 'Key Object Selection',
                'MR': 'Magnetic Resonance',
                'MG': 'Mammography',
                'NM': 'Nuclear Medicine',
                'OT': 'Other',
                'PT': 'Positron emission tomography (PET)',
                'PR': 'Presentation State',
                'US': 'Ultrasound',
                'XA': 'X-Ray Angiography'
            },
            'more': {
                'AR': 'Autorefraction',
                'AU': 'Audio',
                'BDUS': 'Bone Densitometry (ultrasound)',
                'BI': 'Biomagnetic imaging',
                'BMD': 'Bone Densitometry (X-Ray)',
                'DOC': 'Document',
                'DG': 'Diaphanography',
                'ECG': 'Electrocardiography',
                'EPS': 'Cardiac Electrophysiology',
                'ES': 'Endoscopy',
                'FID': 'Fiducials',
                'GM': 'General Microscopy',
                'HC': 'Hard Copy',
                'HD': 'Hemodynamic Waveform',
                'IO': 'Intra-Oral Radiography',
                'IOL': 'Intraocular Lens Data',
                'IVOCT': 'Intravascular Optical Coherence Tomography',
                'IVUS': 'Intravascular Ultrasound',
                'KER': 'Keratometry',
                'LEN': 'Lensometry',
                'LS': 'Laser surface scan',
                'OAM': 'Ophthalmic Axial Measurements',
                'OCT': 'Optical Coherence Tomography (non-Ophthalmic)',
                'OP': 'Ophthalmic Photography',
                'OPM': 'Ophthalmic Mapping',
                'OPT': 'Ophthalmic Tomography',
                'OPV': 'Ophthalmic Visual Field',
                'OSS': 'Optical Surface Scan',
                'PLAN': 'Plan',
                'PX': 'Panoramic X-Ray',
                'REG': 'Registration',
                'RESP': 'Respiratory Waveform',
                'RF': 'Radio Fluoroscopy',
                'RG': 'Radiographic imaging (conventional film/screen)',
                'RTDOSE': 'Radiotherapy Dose',
                'RTIMAGE': 'Radiotherapy Image',
                'RTPLAN': 'Radiotherapy Plan',
                'RTRECORD': 'RT Treatment Record',
                'RTSTRUCT': 'Radiotherapy Structure Set',
                'RWV': 'Real World Value Map',
                'SEG': 'Segmentation',
                'SM': 'Slide Microscopy',
                'SMR': 'Stereometric Relationship',
                'SR': 'SR Document',
                'SRF': 'Subjective Refraction',
                'STAIN': 'Automated Slide Stainer',
                'TG': 'Thermography',
                'VA': 'Visual Acuity',
                'XC': 'External-camera Photography'
            }
        };
    }
    public static get OPTIONS(): any{
        return  {genders:
            [
                {
                    obj: {
                        'vr': 'CS',
                        'Value': ['F']
                    },
                    'title': 'Female'
                },
                {
                    obj: {
                        'vr': 'CS',
                        'Value': ['M']
                    },
                    'title': 'Male'
                },
                {
                    obj: {
                        'vr': 'CS',
                        'Value': ['O']
                    },
                    'title': 'Other'
                }
            ]
        };
    }
    public static get ORDERBY_EXTERNAL(): Array<any>{
        return [
            {
                value: '',
                label: '<label>Patient </label>',
                mode: 'patient',
                title:'Query Patients to external archive'
            },{
                value: '',
                label: '<label>Study </label>',
                mode: 'study',
                title:'Query Studies to external archive'
            }
        ]
    }
    public static get ORDERBY(): Array<any>{
        return [
            {
                value: 'PatientName',
                label: '<label>Patient</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span>',
                mode: 'patient',
                title:'Query Patients'
            },
            {
                value: '-PatientName',
                label: '<label>Patient</label><span class=\"orderbynamedesc\"></span>',
                mode: 'patient',
                title:'Query Patients'
            },
            {

                value: '-StudyDate,-StudyTime',
                label: '<label>Study</label><span class=\"orderbydateasc\"></span>',
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: 'StudyDate,StudyTime',
                label: '<label>Study</label><span class=\"orderbydatedesc\"></span>',
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: 'PatientName,-StudyDate,-StudyTime',
                label: '<label>Study</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>',
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: '-PatientName,-StudyDate,-StudyTime',
                label: '<label>Study</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>',
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: 'PatientName,StudyDate,StudyTime',
                label: '<label>Study</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>',
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: '-PatientName,StudyDate,StudyTime',
                label: '<label>Study</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>',
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: '-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: '<label>MWL</label></span><span class=\"orderbydateasc\"></span>',
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: '<label>MWL</label><span class=\"orderbydatedesc\"></span>',
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: 'PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: '<label>MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>',
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: '-PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: '<label>MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>',
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: 'PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: '<label>MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>',
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: '-PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: '<label>MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>',
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: '',
                label: '<label>Diff </label><i class="material-icons">compare_arrows</i>',
                mode: 'diff',
                title:'Make diff between two archives'
            }
        ];

    }
    /*
    * Defines action for replacing placehoders/title or disabling elements when you edit or create patient,mwl or study
    * Used in helpers/placeholderchanger.directive.ts
    * */
    public static get IODPLACEHOLDERS(): any{
        return {
            '00100020': {
                'create': {
                    placeholder: 'To generate it automatically leave it blank',
                    action: 'replace'
                }
            },
            '0020000D': {
                'create': {
                    placeholder: 'To generate it automatically leave it blank',
                    action: 'replace'
                },
                'edit': {
                    action: 'disable'
                }
            },
            '00400009': {
                'edit': {
                    action: 'disable'
                }
            }
        };
    }
    public static get DICOM_SPECIFIC_CHAR(): any{
        return [
            {
                groupName:"Single-Byte Character Sets",
                groupValues:[
                    {
                        title:"Latin alphabet No. 1",
                        value:"ISO_IR 100"
                    },
                    {
                        title:"Latin alphabet No. 2",
                        value:"ISO_IR 101"
                    },
                    {
                        title:"Latin alphabet No. 3",
                        value:"ISO_IR 109"
                    },
                    {
                        title:"Latin alphabet No. 4",
                        value:"ISO_IR 110"
                    },
                    {
                        title:"Cyrillic",
                        value:"ISO_IR 144"
                    },
                    {
                        title:"Arabic",
                        value:"ISO_IR 127"
                    },
                    {
                        title:"Greek",
                        value:"ISO_IR 126"
                    },
                    {
                        title:"Hebrew",
                        value:"ISO_IR 138"
                    },
                    {
                        title:"Latin alphabet No. 5",
                        value:"ISO_IR 148"
                    },
                    {
                        title:"Japanese",
                        value:"ISO_IR 13"
                    },
                    {
                        title:"Thai",
                        value:"ISO_IR 166"
                    }
                ]
            },{
                groupName:"Multi-Byte Character Sets Without Code Extensions",
                groupValues:[
                    {
                        title:"Unicode in UTF-8",
                        value:"ISO_IR 192"
                    },{
                        title:"GB18030",
                        value:"GB18030"
                    },{
                        title:"GBK",
                        value:"GBK"
                    }
                ]
            },
            {
                groupName:"Single-Byte Character Sets with Code Extensions",
                groupValues:[
                    {
                        title:"Default repertoire",
                        value:"ISO 2022 IR 6"
                    },{
                        title:"Latin alphabet No. 1",
                        value:"ISO 2022 IR 100"
                    },
                    {
                        title:"Latin alphabet No. 2",
                        value:"ISO 2022 IR 101"
                    },
                    {
                        title:"Latin alphabet No. 3",
                        value:"ISO 2022 IR 109"
                    },
                    {
                        title:"Latin alphabet No. 4",
                        value:"ISO 2022 IR 110"
                    },
                    {
                        title:"Cyrillic",
                        value:"ISO 2022 IR 144"
                    },
                    {
                        title:"Arabic",
                        value:"ISO 2022 IR 127"
                    },
                    {
                        title:"Greek",
                        value:"ISO 2022 IR 126"
                    },
                    {
                        title:"Hebrew",
                        value:"ISO 2022 IR 138"
                    },
                    {
                        title:"Latin alphabet No. 5",
                        value:"ISO 2022 IR 148"
                    },
                    {
                        title:"Japanese",
                        value:"ISO 2022 IR 13"
                    },
                    {
                        title:"Thai",
                        value:"ISO 2022 IR 166"
                    }
                ]
            },{
                groupName:"Multi-Byte Character Sets",
                groupValues:[
                    {
                        title:"Japanese (Kanji)",
                        value:"ISO 2022 IR 87"
                    },{
                        title:"Japanese (Supplementary Kanji set)",
                        value:"ISO 2022 IR 159"
                    },{
                        title:"Korean",
                        value:"ISO 2022 IR 149"
                    },{
                        title:"Simplified Chinese",
                        value:"ISO 2022 IR 58"
                    }
                ]
            }
        ]

    }
    public static get DYNAMIC_FORMATER(): any{
        return {
            dcmAETitle:{
                key:'dicomAETitle',
                labelKey:'{dicomAETitle}',
                msg:'Create first an AE Title!'
            },
            dcmArchiveAETitle:{
                key:'dicomAETitle',
                labelKey:'{dicomAETitle}',
                msg:'Create first an AE Title!',
                pathInDevice:'dicomNetworkAE'
            },
            dcmQueueName:{
                key:'dcmQueueName',
                labelKey:'{dcmQueueName}',
                msg:'Configure first an Queue',
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmQueue'
            },
            dcmExporterID:{
                key:'dcmExporterID',
                labelKey:'{dcmQueueName}',
                msg:'Create first an Exporter!',
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmExporter'
            },
            dcmStorageID:{
                key:'dcmStorageID',
                labelKey:'{dcmStorageID}',
                msg:'Create first an Storage!',
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmStorage'
            },
            dcmQueryRetrieveViewID:{
                key:'dcmQueryRetrieveViewID',
                labelKey:'{dcmQueryRetrieveViewID}',
                msg:'Create first an Query Retrieve View!',
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmQueryRetrieveView'
            },
            dcmRejectionNoteCode:{
                key:'dcmRejectionNoteCode',
                labelKey:'{dcmRejectionNoteLabel}',
                msg:'Create first an Rejection Note!',
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmRejectionNote'
            },
            dicomDeviceName:{
                key:'dicomDeviceName',
                labelKey:'{dicomDeviceName}',
                msg:'Create first any device first!'
            },
            hl7ApplicationName:{
                key:'hl7ApplicationName',
                labelKey:'{hl7ApplicationName}',
                msg:'Create first an hl7 Application!'
            }
        };
    }
    public static get HL7_LIST_LINK(): string{
        return "../hl7apps";
    }
}
