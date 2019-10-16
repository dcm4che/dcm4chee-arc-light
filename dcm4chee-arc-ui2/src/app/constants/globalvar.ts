import {FilterSchema, SelectDropdown} from "../interfaces";
import {DicomTableSchema, DynamicPipe} from "../helpers/dicom-studies-table/dicom-studies-table.interfaces";
import {ContentDescriptionPipe} from "../pipes/content-description.pipe";
import {TableSchemaElement} from "../models/dicom-table-schema-element";

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
    public static get ORDERBY(): Array<{value:string,label:any,mode:('patient'|'study'|'mwl'|'diff'),title:string}>{
        return [
            {
                value: 'PatientName',
                label: `<label class="order_label">Patient</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span>`,
                mode: 'patient',
                title:'Query Patients'
            },
            {
                value: '-PatientName',
                label: `<label class="order_label">Patient</label><span class=\"orderbynamedesc\"></span>`,
                mode: 'patient',
                title:'Query Patients'
            },
            {

                value: '-StudyDate,-StudyTime',
                label: `<label class="order_label">Study</label><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: 'StudyDate,StudyTime',
                label: `<label class="order_label">Study</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: 'PatientName,-StudyDate,-StudyTime',
                label: `<label class="order_label">Study</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: '-PatientName,-StudyDate,-StudyTime',
                label: `<label class="order_label">Study</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: 'PatientName,StudyDate,StudyTime',
                label: `<label class="order_label">Study</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: '-PatientName,StudyDate,StudyTime',
                label: `<label class="order_label">Study</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: '-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: `<label class="order_label">MWL</label></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: `<label class="order_label">MWL</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: 'PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: `<label class="order_label">MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: '-PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: `<label class="order_label">MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: 'PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: `<label class="order_label">MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: '-PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: `<label class="order_label">MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: '',
                label: `<label class="order_label">Diff </label><i class="material-icons">compare_arrows</i>`,
                mode: 'diff',
                title:'Make diff between two archives'
            }
        ];

    }
    public static get ORDERBY_NEW(): Array<{value:string,label:any,mode:('patient'|'study'|'mwl'|'diff'),title:string}>{
        return [
            {
                value: 'PatientName',
                label: `<label class="order_label">Order A-Z</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span>`,
                mode: 'patient',
                title:'Query Patients'
            },
            {
                value: '-PatientName',
                label: `<label class="order_label">Z-A</label><span class=\"orderbynamedesc\"></span>`,
                mode: 'patient',
                title:'Query Patients'
            },
            {

                value: '-StudyDate,-StudyTime',
                label: `<label class="order_label">Newest first</label><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: 'StudyDate,StudyTime',
                label: `<label class="order_label">Oldest first</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: 'PatientName,-StudyDate,-StudyTime',
                label: `<label class="order_label">A-Z, New to Old</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: '-PatientName,-StudyDate,-StudyTime',
                label: `<label class="order_label">Z-A, New to Old</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: 'PatientName,StudyDate,StudyTime',
                label: `<label class="order_label">A-Z, Old to New</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: '-PatientName,StudyDate,StudyTime',
                label: `<label class="order_label">Z-A, Old to New</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: '-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: `<label class="order_label">Newest first</label></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: `<label class="order_label">Oldest first</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: 'PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: `<label class="order_label">A-Z, New to Old</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: '-PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: `<label class="order_label">Z-A, New to Old</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: 'PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: `<label class="order_label">A-Z, Old to New</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: '-PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: `<label class="order_label">Z-A, Old to New</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:'Query MWL'
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

    public static get HL7_SPECIFIC_CHAR(): any{
        return [
            {
                groupName:"Single-Byte Character Sets",
                groupValues:[
                    {
                        title: "ASCII",
                        value: "ASCII"
                    },
                    {
                        title:"GB 18030-2000",
                        value:"GB 18030-2000"
                    },
                    {
                        title:"Latin alphabet No. 1",
                        value:"8859/1"
                    },

                    {
                        title:"Latin alphabet No. 2",
                        value:"8859/2"
                    },
                    {
                        title:"Thai",
                        value:"CNS 11643-1992"
                    },
                    {
                        title:"Latin alphabet No. 3",
                        value:"8859/3"
                    },
                    {
                        title:"Latin alphabet No. 4",
                        value:"8859/4"
                    },
                    {
                        title:"Japanese",
                        value:"ISO IR14"
                    },
                    {
                        title:"Cyrillic",
                        value:"8859/5"
                    },
                    {
                        title:"Arabic",
                        value:"8859/6"
                    },
                    {
                        title:"Greek",
                        value:"8859/7"
                    },

                    {
                        title:"Hebrew",
                        value:"8859/8"
                    },
                    {
                        title:"Latin alphabet No. 5",
                        value:"8859/9"
                    }
                ]
            },{
                groupName:"Multi-Byte Character Sets",
                groupValues:[
                    {
                        title:"Japanese (Kanji)",
                        value:"ISO IR87"
                    },{
                        title:"Japanese (Supplementary Kanji set)",
                        value:"ISO IR159"
                    },{
                        title:"Korean",
                        value:"KS X 1001"
                    },{
                        title:"Unicode",
                        value:"UNICODE"
                    },
                    {
                        title:"Unicode in UTF-8",
                        value:"UNICODE UTF-8"
                    }
                ]
            }
        ]
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
    public static get SUPER_ROOT(): string{
        return "root";
    }
    public static get TASK_NAMES(): any{
        return [
            "completed",
            "warning",
            "failed",
            "in-process",
            "scheduled",
            "canceled"
        ];;
    }
    public static get DYNAMIC_FORMATER(): any{
        return {
/*            dcmAETitle:{
                key:'dicomAETitle',
                labelKey:'{dicomAETitle}',
                msg:'Create first an AE Title!'
            },*/
            dcmArchiveAETitle:{
                key:'dicomAETitle',
                labelKey:'{dicomAETitle}',
                msg:'Create first an AE Title!',
                pathInDevice:'dicomNetworkAE'
            },
            dcmQueueName:{
                key:'dcmQueueName',
                labelKey:'{dicomDescription} ({dcmQueueName})',
                msg:'Configure first an Queue',
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmQueue'
            },
            dcmExporterID:{
                key:'dcmExporterID',
                labelKey:'{dcmExporterID}',
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
            dcmuiDeviceURLObject:{
                key:'dcmuiDeviceURLName',
                labelKey:'{dcmuiDeviceURLName}',
                msg:'Create first an UI Device URL!',
                pathInDevice:'dcmDevice.dcmuiConfig[0].dcmuiDeviceURLObject'
            },
            dcmuiDeviceClusterObject:{
                key:'dcmuiDeviceClusterName',
                labelKey:'{dcmuiDeviceClusterName}',
                msg:'Create first an UI Device Cluster!',
                pathInDevice:'dcmDevice.dcmuiConfig["0"].dcmuiDeviceClusterObject'
            },
            dcmuiElasticsearchConfig:{
                key:'dcmuiElasticsearchURLName',
                labelKey:'{dcmuiElasticsearchURLName}',
                msg:'Create first an UI Elasticsearch URL!',
                pathInDevice:'dcmDevice.dcmuiConfig[0].dcmuiElasticsearchConfig[0].dcmuiElasticsearchURLObjects'
            },
            dcmKeycloakServer:{
                key:'dcmKeycloakServerID',
                labelKey:'{dcmKeycloakServerID}',
                msg:'Create first an Keycloak Server!',
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmKeycloakServer'
            }
/*            dicomDeviceName:{
                key:'dicomDeviceName',
                labelKey:'{dicomDeviceName}',
                msg:'Create first any device first!'
            },*/
/*            hl7ApplicationName:{
                key:'hl7ApplicationName',
                labelKey:'{hl7ApplicationName}',
                msg:'Create first an hl7 Application!'
            }*/
        };
    }
    public static get HL7_LIST_LINK(): string{
        return "../hl7apps";
    }
    public static get QUEU_CONFIG_PATH(): string{
        return "dcmDevice.dcmArchiveDevice.dcmQueue";
    }
    public static get EXPORTER_CONFIG_PATH(): string{
        return "dcmDevice.dcmArchiveDevice.dcmExporter";
    }
    public static LINK_PERMISSION(url):any{
        const regex = /^(\/[\S\/]*)\*$/m;
        let m;
        let urlPermissions = {
            "/studies":{
                permissionsAction:"menu-studies"
            },
            "/device/edit/*":{
                permissionsAction:"action-devicelist-device_configuration"
            },
            "/monitoring/dashboard/*":{
                permissionsAction:"menu-dashboard",
                nextCheck:"/studies"
            },
            "/lifecycle-management":{
                permissionsAction:"menu-lifecycle_management",
                nextCheck:"/studies"
            },
            "/migration/retrieve":{
                permissionsAction:"tab-move_data->retrieve",
                nextCheck:"/migration/export"
            },
            "/migration/export":{
                permissionsAction:"tab-move_data->export"
            },
            "/audit-record-repository/*":{
                permissionsAction:"menu-audit_record_repository"
            },
            "/study/*":{
                permissionsAction:"menu-study"
            },
            "/study/study":{
                permissionsAction:"tab-study-study"
            },
            "/study/patient":{
                permissionsAction:"tab-study-patient"
            },
            "/study/mwl":{
                permissionsAction:"tab-study-mwl"
            },
            "/study/diff":{
                permissionsAction:"tab-study-diff"
            },
            "/device/devicelist":{
                permissionsAction:"tab-configuration->devices",
                nextCheck:"/device/aelist"
            },
            "/device/aelist":{
                permissionsAction:"tab-configuration->ae_list",
                nextCheck:"/device/webappslist"
            },
            "/device/webappslist":{
                permissionsAction:"tab-configuration->web_apps_list",
                nextCheck:"/device/hl7applications"
            },
            "/device/hl7applications":{
                permissionsAction:"tab-configuration->hl7_applications"
            },
            "/monitoring/queues":{
                permissionsAction:"tab-monitoring->queues",
                nextCheck:"/monitoring/export"
            },
            "/monitoring/export":{
                permissionsAction:"tab-monitoring->export",
                nextCheck:"/monitoring/external"
            },
            "/monitoring/external":{
                permissionsAction:"tab-monitoring->external_retrieve",
                nextCheck:"/monitoring/control"
            },
            "/monitoring/control":{
                permissionsAction:"tab-monitoring->control",
                nextCheck:"/monitoring/associations"
            },
            "/monitoring/associations":{
                permissionsAction:"tab-monitoring->associations",
                nextCheck:"/monitoring/storage-commitment"
            },
            "/monitoring/storage-commitment":{
                permissionsAction:"tab-monitoring->storage_commitments",
                nextCheck:"/monitoring/storage-systems"
            },
            "/monitoring/storage-systems":{
                permissionsAction:"tab-monitoring->storage_systems"
            },
            "/monitoring/metrics":{
                permissionsAction:"tab-monitoring->metrics"
            },
            "/statistics/all":{
                permissionsAction:"tab-statistics->statistics",
                nextCheck:"/statistics/studies-stored"
            },
            "/statistics/studies-stored":{
                permissionsAction:"tab-statistics->studies-stored"
            },
            "/correct-data/diff":{
                permissionsAction:"tab-correct_data->diff"
            },
            "/correct-data/patient-data":{
                permissionsAction:"tab-correct_data->patient_data"
            },
        };
        if(urlPermissions[url])
            return urlPermissions[url];
        else{
            let actionObject;
            Object.keys(urlPermissions).forEach(keys=>{
                if ((m = regex.exec(keys)) !== null && url.indexOf(m[1]) > -1)
                    actionObject = urlPermissions[keys];
            });
            return actionObject;
        }
    }

    static MWL_FILTER_SCHEMA(hidden?):FilterSchema{
        if(hidden){
            return [
                {
                    tag:"input",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.ScheduledProcedureStepStatus",
                    description:"SPS Status",
                    placeholder:"SPS Status"
                },{
                    tag:"checkbox",
                    filterKey:"onlyDefault",
                    text:"Only Default"
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.ScheduledPerformingPhysicianName",
                    description:"Scheduled Performing Physician's Name",
                    placeholder:"SP Physician's Name"
                }
            ]
        }else{
            return [
                {
                    tag:"input",
                    type:"text",
                    filterKey:"PatientName",
                    description:"Patient name",
                    placeholder:"Patient name"
                },
                {
                    tag:"checkbox",
                    filterKey:"fuzzymatching",
                    text:"Fuzzy Matching"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"PatientID",
                    description:"Patient ID",
                    placeholder:"Patient ID"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"IssuerOfPatientID",
                    description:"Issuer of patient",
                    placeholder:"Issuer of patient"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"AccessionNumber",
                    description:"Accession number",
                    placeholder:"Accession number"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"IssuerOfAccessionNumberSequence.LocalNamespaceEntityID",
                    description:"Issuer of accession number",
                    placeholder:"Issuer of accession number"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.ScheduledStationAETitle",
                    description:"Scheduled Station AE Title",
                    placeholder:"Scheduled Station AE Title"
                },{
                    tag:"modality",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.Modality",
                    placeholder:"Modality",
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StudyInstanceUID",
                    description:"Study Instance UID",
                    placeholder:"Study Instance UID"
                },{
                    tag:"input",
                    type:"number",
                    filterKey:"limit",
                    description:"Limit",
                    placeholder:"Limit of MWL"
                },{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate",
                    description:"SPS Start Date",
                    placeholder:"Scheduled Procedure Step Start Date"
                },{
                    tag:"range-picker-time",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
                    description:"SPS Start Time",
                    placeholder:"Scheduled Procedure Step Start Time"
                }
            ]
        }
    }

    static STUDY_FILTER_SCHEMA(aets,hidden?):FilterSchema{
        if(hidden){
            return [
                {
                    tag:"input",
                    type:"text",
                    filterKey:"InstitutionName",
                    description:"Institution name",
                    placeholder:"Institution name"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StationName",
                    description:"Station name",
                    placeholder:"Station name"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"SOPClassesInStudy",
                    description:"SOP classes in study",
                    placeholder:"SOP classes in study"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"SeriesDescription",
                    description:"Series description",
                    placeholder:"Series description"
                },
                {
                    tag:"checkbox",
                    filterKey:"incomplete",
                    text:"Only incomplete",
                    description:"Only incomplete studies"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StudyDescription",
                    description:"Study description",
                    placeholder:"Study description"
                },{
                    tag:"select",
                    options:aets,
                    showStar:true,
                    filterKey:"ExternalRetrieveAET",
                    description:"Retrievable from external retrieve AET",
                    placeholder:"External retrieve AET"
                },{
                    tag:"select",
                    options:aets,
                    showStar:true,
                    filterKey:"ExternalRetrieveAET!",
                    description:"Not retrievable from external retrieve AET",
                    placeholder:"Not retrievable from AET"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"BodyPartExamined",
                    description:"Body part examined",
                    placeholder:"Body part examined"
                },
                {
                    tag:"checkbox",
                    filterKey:"compressionfailed",
                    text:"Compression Failed"
                },
                {
                    tag:"size_range_picker",
                    filterKey:"StudySizeInKB"
                },
                {
                    tag:"select",
                    filterKey:"ExpirationState",
                    showStar:true,
                    options:[
                        new SelectDropdown("UPDATEABLE", "UPDATEABLE"),
                        new SelectDropdown("FROZEN", "FROZEN"),
                        new SelectDropdown("REJECTED", "REJECTED"),
                        new SelectDropdown("EXPORT_SCHEDULED", "EXPORT_SCHEDULED"),
                        new SelectDropdown("FAILED_TO_EXPORT", "FAILED_TO_EXPORT"),
                        new SelectDropdown("FAILED_TO_REJECT", "FAILED_TO_REJECT"),
                    ],
                    description:"Expiration State",
                    placeholder:"Expiration State",
                }
                ,{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"ExpirationDate",
                    description:"Expiration Date"
                },
                {
                    tag:"checkbox",
                    filterKey:"retrievefailed",
                    text:"Only failed retrieving",
                    description:"Only failed to be retrieved"
                },
                {
                    tag:"checkbox",
                    filterKey:"storageVerificationFailed",
                    text:"Verification Failed",
                    description:"Storage Verification Failed"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"ResponsiblePerson",
                    description:"Responsible Person",
                    placeholder:"Responsible Person"
                },{
                    tag:"p-calendar",
                    type:"text",
                    filterKey:"PatientsBirthDate",
                    description:"Patient's Birth Date",
                    placeholder:"Birth Date"
                }
            ];
        }
        return [
            {
                tag:"select",
                options:aets,
                showStar:true,
                filterKey:"aet",
                description:"AET",
                placeholder:"AET"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"PatientName",
                description:"Patient name",
                placeholder:"Patient name"
            },
            {
                tag:"checkbox",
                filterKey:"fuzzymatching",
                text:"Fuzzy Matching"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"PatientID",
                description:"Patient ID",
                placeholder:"Patient ID"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"StudyInstanceUID",
                description:"Study Instance UID",
                placeholder:"Study Instance UID"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"IssuerOfPatientID",
                description:"Issuer of patient",
                placeholder:"Issuer of patient"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"AccessionNumber",
                description:"Accession number",
                placeholder:"Accession number"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"IssuerOfAccessionNumberSequence.LocalNamespaceEntityID",
                description:"Issuer of accession number",
                placeholder:"Issuer of accession number"
            },
            {
                tag:"modality",
                type:"text",
                filterKey:"ModalitiesInStudy",
                placeholder:"Modality",
            },
            {
                tag:"input",
                type:"number",
                filterKey:"limit",
                description:"Limit",
                placeholder:"Limit of studies"
            },{
                tag:"select",
                filterKey:"includefield",
                options:[
                    new SelectDropdown("", "dicom","Search Response Payload according DICOM PS 3.18"),
                    new SelectDropdown("all", "all", "all available attributes")
                ],
                description:"Include field",
                placeholder:"Include field",
            },
            {
                tag:"input",
                type:"text",
                filterKey:"ReferringPhysicianName",
                description:"Referring physician name",
                placeholder:"Referring physician name"
            },{
                tag:"input",
                type:"text",
                filterKey:"InstitutionalDepartmentName",
                description:"Institutional Department Name",
                placeholder:"Institutional Department Name"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"SendingApplicationEntityTitleOfSeries",
                description:"Sending Application Entity Title of Series",
                placeholder:"Sending AET of Series"
            },{
                tag:"input",
                type:"text",
                filterKey:"StudyID",
                description:"Study ID",
                placeholder:"Study ID"
            },{
                tag:"range-picker-limit",
                type:"text",
                filterKey:"StudyDate",
                description:"Study date"
            },{
                tag:"range-picker-time",
                type:"text",
                filterKey:"StudyTime",
                description:"Study time"
            },{
                tag:"range-picker",
                type:"text",
                filterKey:"StudyReceiveDateTime",
                description:"Study Received"
            },{
                tag:"range-picker",
                type:"text",
                filterKey:"StudyAccessDateTime",
                description:"Study Access"
            }
        ];
    }
    static DIFF_FILTER_SCHEMA(aets, attributeSet, hidden?):FilterSchema{
        if(hidden){
            return [
                {
                    tag:"input",
                    type:"text",
                    filterKey:"InstitutionName",
                    description:"Institution name",
                    placeholder:"Institution name"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StationName",
                    description:"Station name",
                    placeholder:"Station name"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"SOPClassesInStudy",
                    description:"SOP classes in study",
                    placeholder:"SOP classes in study"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"SeriesDescription",
                    description:"Series description",
                    placeholder:"Series description"
                },
                {
                    tag:"checkbox",
                    filterKey:"incomplete",
                    text:"Only incomplete",
                    description:"Only incomplete studies"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StudyDescription",
                    description:"Study description",
                    placeholder:"Study description"
                },{
                    tag:"select",
                    options:aets,
                    showStar:true,
                    filterKey:"ExternalRetrieveAET",
                    description:"Retrievable from external retrieve AET",
                    placeholder:"External retrieve AET"
                },{
                    tag:"select",
                    options:aets,
                    showStar:true,
                    filterKey:"ExternalRetrieveAET!",
                    description:"Not retrievable from external retrieve AET",
                    placeholder:"Not retrievable from AET"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"BodyPartExamined",
                    description:"Body part examined",
                    placeholder:"Body part examined"
                }
                ,
                {
                    tag:"checkbox",
                    filterKey:"retrievefailed",
                    text:"Only failed retrieving",
                    description:"Only failed to be retrieved"
                },
                {
                    tag:"checkbox",
                    filterKey:"storageVerificationFailed",
                    text:"Verification Failed",
                    description:"Storage Verification Failed"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"ResponsiblePerson",
                    description:"Responsible Person",
                    placeholder:"Responsible Person"
                },{
                    tag:"p-calendar",
                    type:"text",
                    filterKey:"PatientsBirthDate",
                    description:"Patient's Birth Date",
                    placeholder:"Birth Date"
                },{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"StudyReceiveDateTime",
                    description:"Study Received"
                },{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"StudyAccessDateTime",
                    description:"Study Access"
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"StudyInstanceUID",
                    description:"Study Instance UID",
                    placeholder:"Study Instance UID"
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StudyID",
                    description:"Study ID",
                    placeholder:"Study ID"
                }
            ];
        }
        return [
            {
                tag:"input",
                type:"text",
                filterKey:"PatientName",
                description:"Patient name",
                placeholder:"Patient name"
            },
            {
                tag:"checkbox",
                filterKey:"fuzzymatching",
                text:"Fuzzy Matching"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"PatientID",
                description:"Patient ID",
                placeholder:"Patient ID"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"IssuerOfPatientID",
                description:"Issuer of patient",
                placeholder:"Issuer of patient"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"AccessionNumber",
                description:"Accession number",
                placeholder:"Accession number"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"IssuerOfAccessionNumberSequence.LocalNamespaceEntityID",
                description:"Issuer of accession number",
                placeholder:"Issuer of accession number"
            },
            {
                tag:"modality",
                type:"text",
                filterKey:"ModalitiesInStudy",
                placeholder:"Modality",
            },
            {
                tag:"input",
                type:"text",
                filterKey:"ReferringPhysicianName",
                description:"Referring physician name",
                placeholder:"Referring physician name"
            },{
                tag:"input",
                type:"text",
                filterKey:"InstitutionalDepartmentName",
                description:"Institutional Department Name",
                placeholder:"Institutional Department Name"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"SendingApplicationEntityTitleOfSeries",
                description:"Sending Application Entity Title of Series",
                placeholder:"Sending AET of Series"
            },{
                tag:"range-picker-limit",
                type:"text",
                filterKey:"StudyDate",
                description:"Study date"
            },{
                tag:"range-picker-time",
                type:"text",
                filterKey:"StudyTime",
                description:"Study time"
            },
            {
                tag:"input",
                type:"number",
                filterKey:"limit",
                description:"Limit",
                placeholder:"Limit of studies"
            },{
                tag:"select",
                filterKey:"includefield",
                options:[
                    new SelectDropdown("", "dicom","Search Response Payload according DICOM PS 3.18"),
                    new SelectDropdown("all", "all", "all available attributes")
                ],
                description:"Include field",
                placeholder:"Include field",
            },
            {
                tag:"checkbox",
                filterKey:"diffQueue",
                text:"Queued"
            },
            {
                tag:"checkbox",
                filterKey:"missing",
                text:"Missing Studies"
            },
            {
                tag:"checkbox",
                filterKey:"different",
                text:"Different Studies"
            },{
                tag:"select",
                filterKey:"comparefield",
                options:attributeSet,
                description:"Attribute Set",
                placeholder:"Attribute Set",
            },{
                tag:"input",
                type:"text",
                filterKey:"taskPK",
                description:"Pk of task",
                placeholder:"Pk of task"
            },{
                tag:"input",
                type:"text",
                filterKey:"batchID",
                description:"Bash ID",
                placeholder:"Bash ID"
            }
        ];
    }

    static STUDY_FILTER_ENTRY_SCHEMA(devices,webService):FilterSchema{
        return [
            {
                tag:"html-select",
                options:devices,
                filterKey:"device",
                description:"Select Device",
                placeholder:"Select Device"
            },{
                tag:"html-select",
                options:webService,
                filterKey:"webService",
                description:"Web Application Service",
                placeholder:"Web App Service"
            },
        ]
    }

    static PATIENT_FILTER_SCHEMA(aets,hidden?):FilterSchema{
        if(hidden){
            return [
                {
                    tag:"select",
                    options:[
                        new SelectDropdown("F","Female"),
                        new SelectDropdown("M","Male"),
                        new SelectDropdown("O","Other")
                    ],
                    showStar:true,
                    filterKey:"PatientSex",
                    description:"Patient's Sex",
                    placeholder:"Patient's Sex"
                },
                {
                    tag:"checkbox",
                    filterKey:"withoutstudies",
                    text:"only with studies"
                },{
                    tag:"p-calendar",
                    filterKey:"PatientBirthDate",
                    description:"Birth Date"
                },
                {
                    tag: "select",
                    options: [
                        new SelectDropdown("UNVERIFIED", "UNVERIFIED"),
                        new SelectDropdown("VERIFIED", "VERIFIED"),
                        new SelectDropdown("NOT_FOUND", "NOT_FOUND"),
                        new SelectDropdown("VERIFICATION_FAILED", "VERIFICATION_FAILED")
                    ],
                    showStar: true,
                    filterKey: "patientVerificationStatus",
                    description: "Verification Status",
                    placeholder: "Verification Status"
                }
            ]
        }
        return [
            {
                tag:"select",
                options:aets,
                showStar:true,
                filterKey:"aet",
                description:"AET",
                placeholder:"AET"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"PatientName",
                description:"Patient name",
                placeholder:"Patient name"
            },
            {
                tag:"checkbox",
                filterKey:"fuzzymatching",
                text:"Fuzzy Matching"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"PatientID",
                description:"Patient ID",
                placeholder:"Patient ID"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"IssuerOfPatientID",
                description:"Issuer of patient",
                placeholder:"Issuer of patient"
            },
            {
                tag:"input",
                type:"text",
                filterKey:"StudyInstanceUID",
                description:"Study Instance UID",
                placeholder:"Study Instance UID"
            },
            {
                tag:"input",
                type:"number",
                filterKey:"limit",
                description:"Limit",
                placeholder:"Limit of studies"
            }
        ]
    }

    static KEYCLOAK_OPTIONS():any{
        return {
            flow: 'standard',
            responseMode: 'fragment',
            checkLoginIframe: true,
            onLoad: 'login-required'
        };
    }

}
