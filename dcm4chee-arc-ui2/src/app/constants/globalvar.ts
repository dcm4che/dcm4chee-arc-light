export class Globalvar {
    public static get MODALITIES(): any {
        return {
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
        }
    }
    public static get OPTIONS(): any{
        return  {genders:
            [
                {
                    obj:{
                        "vr": "CS",
                        "Value":["F"]
                    },
                    "title":"Female"
                },
                {
                    obj: {
                        "vr": "CS",
                        "Value": ["M"]
                    },
                    "title":"Male"
                },
                {
                    obj: {
                        "vr": "CS",
                        "Value": ["O"]
                    },
                    "title":"Other"
                }
            ]
        };
    }
    public static get ORDERBY(): Array<any>{
        return [
            {
                value:"PatientName",
                label:"<label title=\"Patient\">Patient</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span>",
                mode:"patient"
            },
            {
                value:"-PatientName",
                label:"<label title=\"Patient\">Patient</label><span class=\"orderbynamedesc\"></span>",
                mode:"patient"
            },
            {

                value:"-StudyDate,-StudyTime",
                label:"<label title=\"Study\">Study</label><span class=\"orderbydateasc\"></span>",
                mode:"study"
            },
            {
                value:"StudyDate,StudyTime",
                label:"<label title=\"Study\">Study</label><span class=\"orderbydatedesc\"></span>",
                mode:"study"
            },
            {
                value:"PatientName,-StudyDate,-StudyTime",
                label:"<label title=\"Study\">Study</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>",
                mode:"study"
            },
            {
                value:"-PatientName,-StudyDate,-StudyTime",
                label:"<label title=\"Study\">Study</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>",
                mode:"study"
            },
            {
                value:"PatientName,StudyDate,StudyTime",
                label:"<label title=\"Study\">Study</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>",
                mode:"study"
            },
            {
                value:"-PatientName,StudyDate,StudyTime",
                label:"<label title=\"Study\">Study</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>",
                mode:"study"
            },
            {
                value:"-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
                label:"<label title=\"Modality worklist\">MWL</label></span><span class=\"orderbydateasc\"></span>",
                mode:"mwl"
            },
            {
                value:"ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
                label:"<label title=\"Modality worklist\">MWL</label><span class=\"orderbydatedesc\"></span>",
                mode:"mwl"
            },
            {
                value:"PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
                label:"<label title=\"Modality worklist\">MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>",
                mode:"mwl"
            },
            {
                value:"-PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
                label:"<label title=\"Modality worklist\">MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>",
                mode:"mwl"
            },
            {
                value:"PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
                label:"<label title=\"Modality worklist\">MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>",
                mode:"mwl"
            },
            {
                value:"-PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
                label:"<label title=\"Modality worklist\">MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>",
                mode:"mwl"
            }
        ];

    }
}
