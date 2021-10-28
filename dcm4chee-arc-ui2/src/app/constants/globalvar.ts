import {FilterSchema, LanguageObject, SelectDropdown} from "../interfaces";
import {DicomTableSchema, DynamicPipe} from "../helpers/dicom-studies-table/dicom-studies-table.interfaces";
import {ContentDescriptionPipe} from "../pipes/content-description.pipe";
import {TableSchemaElement} from "../models/dicom-table-schema-element";
declare var DCM4CHE: any;
const sopObject = DCM4CHE.SOPClass.nameOf("all");
import * as _ from "lodash-es";

export const MY_FORMATS = {
    parse: {
        dateInput: 'YYYYMMDD',
    },
    display: {
        dateInput: 'YYYYMMDD',
        monthYearLabel: 'MM YYYY',
        dateA11yLabel: 'LL',
        monthYearA11yLabel: 'MM YYYY',
    },
};

export class Globalvar {
    public static get MODALITIES(): any {
        return {
            'common': {
                'CR': $localize `:@@modalities.computed_radiography:Computed Radiography`,
                'CT': $localize `:@@modalities.computed_tomography:Computed Tomography`,
                'DX': $localize `:@@modalities.digital_radiography:Digital Radiography`,
                'KO': $localize `:@@modalities.key_object_selection:Key Object Selection`,
                'MR': $localize `:@@modalities.magnetic_resonance:Magnetic Resonance`,
                'MG': $localize `:@@modalities.mammography:Mammography`,
                'NM': $localize `:@@modalities.nuclear_medicine:Nuclear Medicine`,
                'OT': $localize `:@@other:Other`,
                'PT': $localize `:@@modalities.pet:Positron emission tomography (PET)`,
                'PR': $localize `:@@modalities.presentation_state:Presentation State`,
                'US': $localize `:@@modalities.ultrasound:Ultrasound`,
                'XA': $localize `:@@modalities.xa:X-Ray Angiography`
            },
            'more': {
                'AR': $localize `:@@modalities.AR:Autorefraction`,
                'AU': $localize `:@@modalities.AU:Audio`,
                'BDUS': $localize `:@@modalities.BDUS:Bone Densitometry (ultrasound)`,
                'BI': $localize `:@@modalities.BI:Biomagnetic imaging`,
                'BMD': $localize `:@@modalities.BMD:Bone Densitometry (X-Ray)`,
                'DOC': $localize `:@@modalities.DOC:Document`,
                'DG': $localize `:@@modalities.DG:Diaphanography`,
                'ECG': $localize `:@@modalities.ECG:Electrocardiography`,
                'EPS': $localize `:@@modalities.EPS:Cardiac Electrophysiology`,
                'ES': $localize `:@@modalities.ES:Endoscopy`,
                'FID': $localize `:@@modalities.FID:Fiducials`,
                'GM': $localize `:@@modalities.GM:General Microscopy`,
                'HC': $localize `:@@modalities.HC:Hard Copy`,
                'HD': $localize `:@@modalities.HD:Hemodynamic Waveform`,
                'IO': $localize `:@@modalities.IO:Intra-Oral Radiography`,
                'IOL': $localize `:@@modalities.IOL:Intraocular Lens Data`,
                'IVOCT': $localize `:@@modalities.IVOCT:Intravascular Optical Coherence Tomography`,
                'IVUS': $localize `:@@modalities.IVUS:Intravascular Ultrasound`,
                'KER': $localize `:@@modalities.KER:Keratometry`,
                'LEN': $localize `:@@modalities.LEN:Lensometry`,
                'LS': $localize `:@@modalities.LS:Laser surface scan`,
                'OAM': $localize `:@@modalities.OAM:Ophthalmic Axial Measurements`,
                'OCT': $localize `:@@modalities.OCT:Optical Coherence Tomography (non-Ophthalmic)`,
                'OP': $localize `:@@modalities.OP:Ophthalmic Photography`,
                'OPM': $localize `:@@modalities.OPM:Ophthalmic Mapping`,
                'OPT': $localize `:@@modalities.OPT:Ophthalmic Tomography`,
                'OPV': $localize `:@@modalities.OPV:Ophthalmic Visual Field`,
                'OSS': $localize `:@@modalities.OSS:Optical Surface Scan`,
                'PLAN': $localize `:@@modalities.PLAN:Plan`,
                'PX': $localize `:@@modalities.PX:Panoramic X-Ray`,
                'REG': $localize `:@@modalities.REG:Registration`,
                'RESP': $localize `:@@modalities.RESP:Respiratory Waveform`,
                'RF': $localize `:@@modalities.RF:Radio Fluoroscopy`,
                'RG': $localize `:@@modalities.RG:Radiographic imaging (conventional film/screen)`,
                'RTDOSE': $localize `:@@modalities.RTDOSE:Radiotherapy Dose`,
                'RTIMAGE': $localize `:@@modalities.RTIMAGE:Radiotherapy Image`,
                'RTPLAN': $localize `:@@modalities.RTPLAN:Radiotherapy Plan`,
                'RTRECORD': $localize `:@@modalities.RTRECORD:RT Treatment Record`,
                'RTSTRUCT': $localize `:@@modalities.RTSTRUCT:Radiotherapy Structure Set`,
                'RWV': $localize `:@@modalities.RWV:Real World Value Map`,
                'SEG': $localize `:@@modalities.SEG:Segmentation`,
                'SM': $localize `:@@modalities.SM:Slide Microscopy`,
                'SMR': $localize `:@@modalities.SMR:Stereometric Relationship`,
                'SR': $localize `:@@modalities.SR:SR Document`,
                'SRF': $localize `:@@modalities.SRF:Subjective Refraction`,
                'STAIN': $localize `:@@modalities.STAIN:Automated Slide Stainer`,
                'TG': $localize `:@@modalities.TG:Thermography`,
                'VA': $localize `:@@modalities.VA:Visual Acuity`,
                'XC': $localize `:@@modalities.XC:External-camera Photograph`
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
                    'title': $localize `:@@female:Female`
                },
                {
                    obj: {
                        'vr': 'CS',
                        'Value': ['M']
                    },
                    'title': $localize `:@@male:Male`
                },
                {
                    obj: {
                        'vr': 'CS',
                        'Value': ['O']
                    },
                    'title': $localize `:@@other:Other`
                }
            ]
        };
    }
    public static get ORDERBY_EXTERNAL(): Array<any>{
        return [
            {
                value: '',
                label: $localize `:@@orderby.patient:<label>Patient </label>`,
                mode: 'patient',
                title:$localize `:@@orderby.query_patients_to_external_archive:Query Patients to external archive`
            },{
                value: '',
                label: $localize `:@@orderby.study:<label>Study </label>`,
                mode: 'study',
                title:$localize `:@@orderby.query_studies_to_external_archive:Query Studies to external archive`
            }
        ]
    }
    public static get ORDERBY(): Array<{value:string,label:any,mode:('patient'|'study'|'mwl'|'mpps'|'diff'),title:string}>{
        return [
            {
                value: 'PatientName',
                label: $localize `:@@orderby.patient_alph:<label class="order_label">Patient</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span>`,
                mode: 'patient',
                title:$localize `:@@query_patients:Query Patients`
            },
            {
                value: '-PatientName',
                label: $localize `:@@orderby.patient:name_desc:<label class="order_label">Patient</label><span class=\"orderbynamedesc\"></span>`,
                mode: 'patient',
                title:$localize `:@@query_patients:Query Patients`
            },
            {

                value: '-StudyDate,-StudyTime',
                label: $localize `:@@orderby.study_date_asc:<label class="order_label">Study</label><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies:Query Studies`
            },
            {
                value: 'StudyDate,StudyTime',
                label: $localize `:@@orderby.study_date_desc:<label class="order_label">Study</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies:Query Studies`
            },
            {
                value: 'PatientName,-StudyDate,-StudyTime',
                label: $localize `:@@orderby.study_alph_asc:<label class="order_label">Study</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies:Query Studies`
            },
            {
                value: '-PatientName,-StudyDate,-StudyTime',
                label: $localize `:@@orderby.study_name_asc:<label class="order_label">Study</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies:Query Studies`
            },
            {
                value: 'PatientName,StudyDate,StudyTime',
                label: $localize `:@@orderby.study_alph_desc:<label class="order_label">Study</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies:Query Studies`
            },
            {
                value: '-PatientName,StudyDate,StudyTime',
                label: $localize `:@@orderby.study_name_desc:<label class="order_label">Study</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies:Query Studies`
            },
            {
                value: '-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby.mwl_asc:<label class="order_label">MWL</label></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@query_mwl:Query MWL`
            },
            {
                value: 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby.mwl_desc:<label class="order_label">MWL</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@query_mwl:Query MWL`
            },
            {
                value: 'PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby.mwl_alph_asc:<label class="order_label">MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@query_mwl:Query MWL`
            },
            {
                value: '-PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby.mwl_desc_asc:<label class="order_label">MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@query_mwl:Query MWL`
            },
            {
                value: 'PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby.mwl_alph_desc:<label class="order_label">MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@query_mwl:Query MWL`
            },
            {
                value: '-PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby.mwl_namedsc_date_dsc:<label class="order_label">MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@query_mwl:Query MWL`
            },
            {
                value: '',
                label: $localize `:@@orderby.diff:<label class="order_label">Diff </label><i class="material-icons">compare_arrows</i>`,
                mode: 'diff',
                title:$localize `:@@globalvar.make_diff_between_two_archives:Make diff between two archives`
            },
            {
                value: '-PerformedProcedureStepStartDate,-PerformedProcedureStepStartTime',
                label: $localize `:@@orderby.mpps_asc:<label class="order_label">MPPS</label></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mpps',
                title:$localize `:@@query_mpps:Query MPPS`
            },
            {
                value: 'PerformedProcedureStepStartDate,PerformedProcedureStepStartTime',
                label: $localize `:@@orderby.mpps_desc:<label class="order_label">MPPS</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'mpps',
                title:$localize `:@@query_mpps:Query MPPS`
            },
            {
                value: 'PatientName,-PerformedProcedureStepStartDate,-PerformedProcedureStepStartTime',
                label: $localize `:@@orderby.mpps_alph_asc:<label class="order_label">MPPS</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mpps',
                title:$localize `:@@query_mpps:Query MPPS`
            },
            {
                value: '-PatientName,-PerformedProcedureStepStartDate,-PerformedProcedureStepStartTime',
                label: $localize `:@@orderby.mpps_desc_asc:<label class="order_label">MPPS</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mpps',
                title:$localize `:@@query_mpps:Query MPPS`
            },
            {
                value: 'PatientName,PerformedProcedureStepStartDate,PerformedProcedureStepStartTime',
                label: $localize `:@@orderby.mpps_alph_desc:<label class="order_label">MPPS</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mpps',
                title:$localize `:@@query_mpps:Query MPPS`
            },
            {
                value: '-PatientName,PerformedProcedureStepStartDate,PerformedProcedureStepStartTime',
                label: $localize `:@@orderby.mpps_namedsc_date_dsc:<label class="order_label">MPPS</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mpps',
                title:$localize `:@@query_mpps:Query MPPS`
            }
        ];

    }
    public static get ORDERBY_NEW(): Array<{value:string,label:any,mode:('patient'|'study'|'series'|'mwl'|'mpps'|'diff'),title:string}>{
        return [
            {
                value: 'PatientName',
                label: $localize `:@@orderby_new.order_a_z:<label class="order_label">A-Z</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span>`,
                mode: 'patient',
                title:$localize `:@@query_patients_pname_asc:Patient Name descending`
            },
            {
                value: '-PatientName',
                label: $localize `:@@orderby_new.z_a:<label class="order_label">Z-A</label><span class=\"orderbynamedesc\"></span>`,
                mode: 'patient',
                title:$localize `:@@query_patients_pname_desc:Patient Name ascending`
            },
            {

                value: '-StudyDate,-StudyTime',
                label: $localize `:@@orderby_new.newest_first:<label class="order_label">Newest first</label><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies_study_datetime_newest_first:Newest StudyDateTime first`
            },
            {
                value: 'StudyDate,StudyTime',
                label: $localize `:@@orderby_new.oldest_first:<label class="order_label">Oldest first</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies_study_datetime_oldest_first:Oldest StudyDateTime first`
            },
            {
                value: 'PatientName,-StudyDate,-StudyTime',
                label: $localize `:@@orderby_new.a_z_new_old:<label class="order_label">A-Z, New to Old</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies_pname_asc_study_datetime_new_old:Patient Name ascending, New to Old - StudyDateTime`
            },
            {
                value: '-PatientName,-StudyDate,-StudyTime',
                label: $localize `:@@orderby_new.z_a_new_old:<label class="order_label">Z-A, New to Old</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies_pname_desc_study_datetime_new_old:Patient Name descending, New to Old - StudyDateTime`
            },
            {
                value: 'PatientName,StudyDate,StudyTime',
                label: $localize `:@@orderby_new.a_z_old_new:<label class="order_label">A-Z, Old to New</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies_pname_asc_study_datetime_old_new:Patient Name ascending, Old to New - StudyDateTime`
            },
            {
                value: '-PatientName,StudyDate,StudyTime',
                label: $localize `:@@orderby_new.z_a_old_new:<label class="order_label">Z-A, Old to New</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies_pname_desc_study_datetime_old_new:Patient Name descending, Old to New - StudyDateTime`
            },
            {
                value: '-PerformedProcedureStepStartDate,-PerformedProcedureStepStartTime',
                label: $localize `:@@orderby_new.newest_first:<label class="order_label">Newest first</label><span class=\"orderbydateasc\"></span>`,
                mode: 'series',
                title:$localize `:@@query_series_mpps_pps_newest_first:Newest PerformedProcedureStepStartDateTime first`
            },
            {
                value: 'PerformedProcedureStepStartDate,PerformedProcedureStepStartTime',
                label: $localize `:@@orderby_new.oldest_first:<label class="order_label">Oldest first</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'series',
                title:$localize `:@@query_series_mpps_pps_oldest_first:Oldest PerformedProcedureStepStartDateTime first`
            },
            {
                value: 'PatientName,-PerformedProcedureStepStartDate,-PerformedProcedureStepStartTime',
                label: $localize `:@@orderby_new.a_z_new_old:<label class="order_label">A-Z, New to Old</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'series',
                title:$localize `:@@query_series_mpps_pname_asc_pps_new_old:Patient Name ascending, New to Old - PerformedProcedureStepStartDateTime`
            },
            {
                value: '-PatientName,-PerformedProcedureStepStartDate,-PerformedProcedureStepStartTime',
                label: $localize `:@@orderby_new.z_a_new_old:<label class="order_label">Z-A, New to Old</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'series',
                title:$localize `:@@query_series_mpps_pname_desc_pps_new_old:Patient Name descending, New to Old - PerformedProcedureStepStartDateTime`
            },
            {
                value: 'PatientName,PerformedProcedureStepStartDate,PerformedProcedureStepStartTime',
                label: $localize `:@@orderby_new.a_z_old_new:<label class="order_label">A-Z, Old to New</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'series',
                title:$localize `:@@query_series_mpps_pname_asc_pps_old_new:Patient Name ascending, Old to New - PerformedProcedureStepStartDateTime`
            },
            {
                value: '-PatientName,PerformedProcedureStepStartDate,PerformedProcedureStepStartTime',
                label: $localize `:@@orderby_new.z_a_old_new:<label class="order_label">Z-A, Old to New</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'series',
                title:$localize `:@@query_series_mpps_pname_desc_pps_old_new:Patient Name descending, Old to New - PerformedProcedureStepStartDateTime`
            },
            {
                value: '-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby_new.newest_first:<label class="order_label">Newest first</label></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@query_mwls_sps_newest_first:Newest ScheduledProcedureStepStartDateTime first`
            },
            {
                value: 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby_new.oldest_first:<label class="order_label">Oldest first</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@query_mwls_sps_oldest_first:Oldest ScheduledProcedureStepStartDateTime first`
            },
            {
                value: 'PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby_new.a_z_new_old:<label class="order_label">A-Z, New to Old</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@query_mwls_pname_asc_sps_new_old:Patient Name ascending, New to Old - ScheduledProcedureStepStartDateTime`
            },
            {
                value: '-PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby_new.z_a_new_old:<label class="order_label">Z-A, New to Old</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@query_mwls_pname_desc_sps_new_old:Patient Name descending, New to Old - ScheduledProcedureStepStartDateTime`
            },
            {
                value: 'PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby_new.a_z_old_new:<label class="order_label">A-Z, Old to New</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@query_mwls_pname_asc_sps_old_new:Patient Name ascending, Old to New - ScheduledProcedureStepStartDateTime`
            },
            {
                value: '-PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby_new.z_a_old_new:<label class="order_label">Z-A, Old to New</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@query_mwls_pname_desc_sps_old_new:Patient Name descending, Old to New - ScheduledProcedureStepStartDateTime`
            },
            {
                value: '-PerformedProcedureStepStartDate,-PerformedProcedureStepStartTime',
                label: $localize `:@@orderby_new.newest_first:<label class="order_label">Newest first</label></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mpps',
                title:$localize `:@@query_series_mpps_pps_newest_first:Newest PerformedProcedureStepStartDateTime first`
            },
            {
                value: 'PerformedProcedureStepStartDate,PerformedProcedureStepStartTime',
                label: $localize `:@@orderby_new.oldest_first:<label class="order_label">Oldest first</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'mpps',
                title:$localize `:@@query_series_mpps_pps_oldest_first:Oldest PerformedProcedureStepStartDateTime first`
            },
            {
                value: 'PatientName,-PerformedProcedureStepStartDate,-PerformedProcedureStepStartTime',
                label: $localize `:@@orderby_new.a_z_new_old:<label class="order_label">A-Z, New to Old</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mpps',
                title:$localize `:@@query_series_mpps_pname_asc_pps_new_old:Patient Name ascending, New to Old - PerformedProcedureStepStartDateTime`
            },
            {
                value: '-PatientName,-PerformedProcedureStepStartDate,-PerformedProcedureStepStartTime',
                label: $localize `:@@orderby_new.z_a_new_old:<label class="order_label">Z-A, New to Old</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mpps',
                title:$localize `:@@query_series_mpps_pname_desc_pps_new_old:Patient Name descending, New to Old - PerformedProcedureStepStartDateTime`
            },
            {
                value: 'PatientName,PerformedProcedureStepStartDate,PerformedProcedureStepStartTime',
                label: $localize `:@@orderby_new.a_z_old_new:<label class="order_label">A-Z, Old to New</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mpps',
                title:$localize `:@@query_series_mpps_pname_asc_pps_old_new:Patient Name ascending, Old to New - PerformedProcedureStepStartDateTime`
            },
            {
                value: '-PatientName,PerformedProcedureStepStartDate,PerformedProcedureStepStartTime',
                label: $localize `:@@orderby_new.z_a_old_new:<label class="order_label">Z-A, Old to New</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mpps',
                title:$localize `:@@query_series_mpps_pname_desc_pps_old_new:Patient Name descending, Old to New - PerformedProcedureStepStartDateTime`
            }
        ];

    }

    public static get LANGUAGES(){
        const languages = {
            en: {
                code: 'en',
                name: 'English',
                nativeName: 'English',
                flag:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAAPCAYAAAARZmTlAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAuJAAALiQE3ycutAAAAB3RJTUUH5AIUEDUJI3G5/gAABAlJREFUOMudkn1MlWUYxn/P+77nPR/JIWx+QJG2GVkMj/QhmjrSNbQ05CMtAQGRUmG0FRM/ScsvLEydnmFOJipamc5kHFYhomszwZxpWIpurtTEqQdBD0fPOe/79Aebhtrmuv577l177vva7xLeGbNleOk8lAHRrNnaSPnmH7nlu82/ZRgmgwdF8Uv6AC5mZhO1cgVT2iLwNJxAt6g9vA6HlVnJL1HcN4C/YhPhixegNA1+xehcvAz/mg18OP4Fmj2fkJGcgE23EAwaPIqCIQNNt5CWFE99tou5Z/ZjNh+lzw81HB8Yhzaj9pw5aViCOr+XzhPzSukdPxT3kkKyUkew0u3B03gSu11/6OdSSjpv+hmT6GJBylDivtqM45oVW8FMjNghzHXXsau2GeVWp48d+39jxLZTfPdmNlYhaR+XzPDeGrsrCqhalYvDpiOlvG8B6BaV9WV57BoZTlxRPs4J4wjbXMH3fgeuiZ+ycUcj3g4fmmGaANzo7GJa6U6qX49n5fuFBCZnEZYxhZz8bMYMi6F86wGkEehmZJoMebY/H4+L4bm9W/BdaqN30yEuCDuLPqrka89R7DYdIboPEkXl+0ICcZfenUCIPv0imP3uKCL2fIsMBnHkZQPgq6qmfXouzqXLcS4q4c46N+ZT0djTJ3E9BBXbG7lwuR2b1dIjtbg+MU0KU4K4NzRNiWFKVKuODAQAiRAKxuU2gmda0QYOQH06GqGqoKoQCnX7FYGiiAfYiUsRURLj0Vr0f6UpsbEIaT7QGinBoqmgCGQwBKaJ2dGB8edfKFGRaJGRIE2kYSCsVkLBEEiJEA8m0VZnzDGEco9JIBAiItxBTtpIInup3DlwEPP309jfy6Frdw3erEzCcksIW1jS7T98hNCpP2hPTmX7z+dpu3AFXdd6LnFX1JiACuD3B0hMiOGz0kz6+2/gzf0Amya4Oi2XjWs9LI8KAgIrks+3NJA6No5B8XEYFy+hvDOV195IoazNwr7aJuw2/S5nxaKpaKqKw65TWZbDwepi4lqO0D50GNroV/HklzB8RT31zWdR1O7AmqrQ9Ot5Et4uo7L2OJb0VPrv+4aYYz+xM9TC3i+mE/G4A01VsGgqit1mISUpnpa6JeS5+tI5q4iblVu5UrWD/L+dTJntpuNmFz3qR/fT5w+Qv3A7E2e6OXbxBuG7qlFefpGx65dxLHMwUxOfxxn+GNqmVXlKsutJfKvK6Wo9y/WRiXz5TCLbltbg9XbiDLNjGOZDWyOEwNnLzqGmVsbnrSMrOYHiwnSiUyagbthEubeVt0a50JJuX1auTSjCMTmNqn4u1tad5MrV00jTxHofwP+SblEJBAy27DlMTcMJCrLGUDx/DsbZc4wuW80/B52SmC5kfUQAAAAASUVORK5CYII='
            },
            ru: {
                code: 'ru',
                name: 'Russian',
                nativeName: 'Русский',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAARBAMAAADalBo9AAAAD1BMVEX///+qveEAOaaOMEvVKx7/j+iDAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIVCAgR8Hz8uwAAABdJREFUCNdjYKABUEICCmTzXJCAA3V4AGMLHTkaNKlFAAAAAElFTkSuQmCC'
            },
            de: {
                code: 'de',
                name: 'German',
                nativeName: 'Deutsch',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAASCAYAAACuLnWgAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIVDRIfoSLpjAAAADVJREFUOMtjZGBg+M9AY8DEQAcwasngs4Rlx2hwDbo4YWKkgyX//tPeEsb/FyRGy65RS2gDAGpJBsZkRqwRAAAAAElFTkSuQmCC'
            },
            hi: {
                code: 'hi',
                name: 'Hindi',
                nativeName: 'हिन्दी',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAAUBAMAAACKWYuOAAAAMFBMVEX///8AZjP/mTMAPGi7y9dmiqWAnrQwYYTv8/YVTHSftsbP2uNAbY7f5+yPqr1QeZcEz6ptAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIUEQYIod+GbwAAADFJREFUGNNjUEICCgy04EGAAwMSYElH4jCzHGBG4jGfZEZWmo/MYXC4AKEFkYAALXgA9uIV01T/BOMAAAAASUVORK5CYII='
            },
            it: {
                code: 'it',
                name: 'Italian',
                nativeName: 'Italiano',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAARCAYAAAAougcOAAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAAAPwAAAD8BR5eJ4AAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAAA3SURBVDiNY2SY5PafAQ9Y7VnDEKJii1P+w869DPeLq/EZwcCEV5ZKYNSSUUtGLRm1ZNSSYW8JAPHjCFF7xQeaAAAAAElFTkSuQmCC'
            },
            ja: {
                code: 'ja',
                name: 'Japanese',
                nativeName: '日本語 (にほんご／にっぽんご)',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAARCAYAAAAougcOAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AobDjswnFzJlQAAATJJREFUOMvtlD1LA0EURc/MbszKSgIBCUIMNoJdfoo2aqGNIEg6sRBBELFQG0ELLYRAGhURLPwpEhAslBQh2Ciarw1m51kEsdq4bGIqbz3zztz73hslIsIfSzMEDQVihz0oIninBToPj9gz0zj5FZQd7roK05PG1h7ty1vwfVAKRMCyiC/P4+5v9x9X8+gMr3jdLax1F6I1iOAVLmgeHPfnRBoNXjM51JgbHONnh1TlHmVZ0Zy0b+7AcX55psIrXkWPy1SqKKt3okorzFM5OkSnxxFjek+dEXQ2E70n5v2Dt8kcKpkIhrQ8UtUSKhaL6CSZwNnIQ4Ab8X2c1aWegFAj7O5uMrIwi9TqPzBjkFqd+OIc7uHOYJYRwH8u0zo5x1Rf0BNpRtfXsKayg9v4/1/4W1+o7nc/qdSJmAAAAABJRU5ErkJggg=='
            },
            mr: {
                code: 'mr',
                name: 'Marathi',
                nativeName: 'मराठी',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAAUBAMAAACKWYuOAAAAMFBMVEX///8AZjP/mTMAPGi7y9dmiqWAnrQwYYTv8/YVTHSftsbP2uNAbY7f5+yPqr1QeZcEz6ptAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIUEQYIod+GbwAAADFJREFUGNNjUEICCgy04EGAAwMSYElH4jCzHGBG4jGfZEZWmo/MYXC4AKEFkYAALXgA9uIV01T/BOMAAAAASUVORK5CYII='
            },
            th: {
                code: 'th',
                name: 'Thai',
                nativeName: 'ไทย',
                flag: 'data:image/png;base64,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5Spr3pO/MJzTV5a5TnMYCSxiCRJEKKiihDJsxGjVSbGQov24j3/I9UvkUshVAiPHAirQILt+8D/43a2Vn5zwksJxoPPFcT5GgK5doFFznO9jx2mcAMFn4Epv+St1YOaT9FpLix4BvdvAxXVLU/aAyx1g4MmQTdmVgjSFfB54P6NvygL9t0D3mtdbcx+nD0CaukreAAeHwGiBstd93h1q7+3fM83+fgD2CHJ1hFptUQAAD4tpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADw/eHBhY2tldCBiZWdpbj0i77u/IiBpZD0iVzVNME1wQ2VoaUh6cmVTek5UY3prYzlkIj8+Cjx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IlhNUCBDb3JlIDQuNC4wLUV4aXYyIj4KIDxyZGY6UkRGIHhtbG5zOnJkZj0iaHR0cDovL3d3dy53My5vcmcvMTk5OS8wMi8yMi1yZGYtc3ludGF4LW5zIyI+CiAgPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIKICAgIHhtbG5zOmlwdGNFeHQ9Imh0dHA6Ly9pcHRjLm9yZy9zdGQvSXB0YzR4bXBFeHQvMjAwOC0wMi0yOS8iCiAgICB4bWxuczp4bXBNTT0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wL21tLyIKICAgIHhtbG5zOnN0RXZ0PSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvc1R5cGUvUmVzb3VyY2VFdmVudCMiCiAgICB4bWxuczpwbHVzPSJodHRwOi8vbnMudXNlcGx1cy5vcmcvbGRmL3htcC8xLjAvIgogICAgeG1sbnM6R0lNUD0iaHR0cDovL3d3dy5naW1wLm9yZy94bXAvIgogICAgeG1sbnM6ZGM9Imh0dHA6Ly9wdXJsLm9yZy9kYy9lbGVtZW50cy8xLjEvIgogICAgeG1sbnM6dGlmZj0iaHR0cDovL25zLmFkb2JlLmNvbS90aWZmLzEuMC8iCiAgICB4bWxuczp4bXA9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC8iCiAgIHhtcE1NOkRvY3VtZW50SUQ9ImdpbXA6ZG9jaWQ6Z2ltcDoyNmQxOGFiOS1hYjI2LTQwNzktYjViMy1hNmJlNTIyYjdkOGQiCiAgIHhtcE1NOkluc3RhbmNlSUQ9InhtcC5paWQ6M2NkMjQ4NTktMGFkOC00NTUzLWE0MTYtNzNhMzUzYzNhOGFiIgogICB4bXBNTTpPcmlnaW5hbERvY3VtZW50SUQ9InhtcC5kaWQ6ODU3NGVhZTMtYzYxYi00NjZhLWJlNmEtMzg0ZmYxZTFlYjAzIgogICBHSU1QOkFQST0iMi4wIgogICBHSU1QOlBsYXRmb3JtPSJMaW51eCIKICAgR0lNUDpUaW1lU3RhbXA9IjE2Mjc5MjM4Mjg5NTQwNTUiCiAgIEdJTVA6VmVyc2lvbj0iMi4xMC4yMiIKICAgZGM6Rm9ybWF0PSJpbWFnZS9wbmciCiAgIHRpZmY6T3JpZW50YXRpb249IjEiCiAgIHhtcDpDcmVhdG9yVG9vbD0iR0lNUCAyLjEwIj4KICAgPGlwdGNFeHQ6TG9jYXRpb25DcmVhdGVkPgogICAgPHJkZjpCYWcvPgogICA8L2lwdGNFeHQ6TG9jYXRpb25DcmVhdGVkPgogICA8aXB0Y0V4dDpMb2NhdGlvblNob3duPgogICAgPHJkZjpCYWcvPgogICA8L2lwdGNFeHQ6TG9jYXRpb25TaG93bj4KICAgPGlwdGNFeHQ6QXJ0d29ya09yT2JqZWN0PgogICAgPHJkZjpCYWcvPgogICA8L2lwdGNFeHQ6QXJ0d29ya09yT2JqZWN0PgogICA8aXB0Y0V4dDpSZWdpc3RyeUlkPgogICAgPHJkZjpCYWcvPgogICA8L2lwdGNFeHQ6UmVnaXN0cnlJZD4KICAgPHhtcE1NOkhpc3Rvcnk+CiAgICA8cmRmOlNlcT4KICAgICA8cmRmOmxpCiAgICAgIHN0RXZ0OmFjdGlvbj0ic2F2ZWQiCiAgICAgIHN0RXZ0OmNoYW5nZWQ9Ii8iCiAgICAgIHN0RXZ0Omluc3RhbmNlSUQ9InhtcC5paWQ6ZDhkYTU5MzUtZjA3MS00NzViLWIwZjEtOTc0Njc0ZmQ0NmJhIgogICAgICBzdEV2dDpzb2Z0d2FyZUFnZW50PSJHaW1wIDIuMTAgKExpbnV4KSIKICAgICAgc3RFdnQ6d2hlbj0iKzAyOjAwIi8+CiAgICA8L3JkZjpTZXE+CiAgIDwveG1wTU06SGlzdG9yeT4KICAgPHBsdXM6SW1hZ2VTdXBwbGllcj4KICAgIDxyZGY6U2VxLz4KICAgPC9wbHVzOkltYWdlU3VwcGxpZXI+CiAgIDxwbHVzOkltYWdlQ3JlYXRvcj4KICAgIDxyZGY6U2VxLz4KICAgPC9wbHVzOkltYWdlQ3JlYXRvcj4KICAgPHBsdXM6Q29weXJpZ2h0T3duZXI+CiAgICA8cmRmOlNlcS8+CiAgIDwvcGx1czpDb3B5cmlnaHRPd25lcj4KICAgPHBsdXM6TGljZW5zb3I+CiAgICA8cmRmOlNlcS8+CiAgIDwvcGx1czpMaWNlbnNvcj4KICA8L3JkZjpEZXNjcmlwdGlvbj4KIDwvcmRmOlJERj4KPC94OnhtcG1ldGE+CiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAKPD94cGFja2V0IGVuZD0idyI/PieYdx4AAAAJcEhZcwAADsQAAA7EAZUrDhsAAAAHdElNRQflCAIRAzAANAnzAAAAx0lEQVQ4y61U2w3CMAy8SyyxBiuwBbuxDfxBNkBMgfiAEaCN+UgfURsqlDpfuXPk+HKxqaowWgLAJBkBeYXgE9Keq4Itwcd2t/m06+t6i5MIRKY7mIW0JCLxk2MdjHSMGmd85XvR0sdTuOX1sNZWIlL2h4beoChtpBwZhJMjk/b5m5AjVEhZE/mTmYQy6JbM+9OW/hiPl6uW0k1MWPYkiTf9EwqlUS7eu37k6n704gAHg8HjoHyeg7cQ2KZ+VItpSFBm06Z+fQFqCk2cuEva4gAAAABJRU5ErkJggg=='
            },
            zh: {
                code: 'zh',
                name: 'Chinese',
                nativeName: '中国人',
                flag: 'data:image/png;base64,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AZc7wNBTXTZkR/LTFIpF4P2MvikPDN4CoTW3t84+Th+ALHWVvgEODoHREmWve7w72Nvbv2c6/f0ABatye77yK0MAAA+LaVRYdFhNTDpjb20uYWRvYmUueG1wAAAAAAA8P3hwYWNrZXQgYmVnaW49Iu+7vyIgaWQ9Ilc1TTBNcENlaGlIenJlU3pOVGN6a2M5ZCI/Pgo8eDp4bXBtZXRhIHhtbG5zOng9ImFkb2JlOm5zOm1ldGEvIiB4OnhtcHRrPSJYTVAgQ29yZSA0LjQuMC1FeGl2MiI+CiA8cmRmOlJERiB4bWxuczpyZGY9Imh0dHA6Ly93d3cudzMub3JnLzE5OTkvMDIvMjItcmRmLXN5bnRheC1ucyMiPgogIDxyZGY6RGVzY3JpcHRpb24gcmRmOmFib3V0PSIiCiAgICB4bWxuczppcHRjRXh0PSJodHRwOi8vaXB0Yy5vcmcvc3RkL0lwdGM0eG1wRXh0LzIwMDgtMDItMjkvIgogICAgeG1sbnM6eG1wTU09Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9tbS8iCiAgICB4bWxuczpzdEV2dD0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wL3NUeXBlL1Jlc291cmNlRXZlbnQjIgogICAgeG1sbnM6cGx1cz0iaHR0cDovL25zLnVzZXBsdXMub3JnL2xkZi94bXAvMS4wLyIKICAgIHhtbG5zOkdJTVA9Imh0dHA6Ly93d3cuZ2ltcC5vcmcveG1wLyIKICAgIHhtbG5zOmRjPSJodHRwOi8vcHVybC5vcmcvZGMvZWxlbWVudHMvMS4xLyIKICAgIHhtbG5zOnRpZmY9Imh0dHA6Ly9ucy5hZG9iZS5jb20vdGlmZi8xLjAvIgogICAgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIgogICB4bXBNTTpEb2N1bWVudElEPSJnaW1wOmRvY2lkOmdpbXA6OTliZGNlNDUtNjhmMC00YzBlLWI5MGQtOTdmZTIzZTk3MDE4IgogICB4bXBNTTpJbnN0YW5jZUlEPSJ4bXAuaWlkOmM2ZTI5YTMzLTZjMGYtNDFkOS1iMTMzLTkzMDk4NzYyNTJmZiIKICAgeG1wTU06T3JpZ2luYWxEb2N1bWVudElEPSJ4bXAuZGlkOjYyMDhmOTY3LTYyZjEtNDE5MC1iMjdmLTQ2YjliYWJlZmY4YSIKICAgR0lNUDpBUEk9IjIuMCIKICAgR0lNUDpQbGF0Zm9ybT0iTGludXgiCiAgIEdJTVA6VGltZVN0YW1wPSIxNjM1NDI2Njg3Nzk1MzA1IgogICBHSU1QOlZlcnNpb249IjIuMTAuMjIiCiAgIGRjOkZvcm1hdD0iaW1hZ2UvcG5nIgogICB0aWZmOk9yaWVudGF0aW9uPSIxIgogICB4bXA6Q3JlYXRvclRvb2w9IkdJTVAgMi4xMCI+CiAgIDxpcHRjRXh0OkxvY2F0aW9uQ3JlYXRlZD4KICAgIDxyZGY6QmFnLz4KICAgPC9pcHRjRXh0OkxvY2F0aW9uQ3JlYXRlZD4KICAgPGlwdGNFeHQ6TG9jYXRpb25TaG93bj4KICAgIDxyZGY6QmFnLz4KICAgPC9pcHRjRXh0OkxvY2F0aW9uU2hvd24+CiAgIDxpcHRjRXh0OkFydHdvcmtPck9iamVjdD4KICAgIDxyZGY6QmFnLz4KICAgPC9pcHRjRXh0OkFydHdvcmtPck9iamVjdD4KICAgPGlwdGNFeHQ6UmVnaXN0cnlJZD4KICAgIDxyZGY6QmFnLz4KICAgPC9pcHRjRXh0OlJlZ2lzdHJ5SWQ+CiAgIDx4bXBNTTpIaXN0b3J5PgogICAgPHJkZjpTZXE+CiAgICAgPHJkZjpsaQogICAgICBzdEV2dDphY3Rpb249InNhdmVkIgogICAgICBzdEV2dDpjaGFuZ2VkPSIvIgogICAgICBzdEV2dDppbnN0YW5jZUlEPSJ4bXAuaWlkOmE5NjIzMGNmLWJhZjYtNDJkNi04ZmMzLWNmY2YwNjQ0MGFjYyIKICAgICAgc3RFdnQ6c29mdHdhcmVBZ2VudD0iR2ltcCAyLjEwIChMaW51eCkiCiAgICAgIHN0RXZ0OndoZW49IiswMjowMCIvPgogICAgPC9yZGY6U2VxPgogICA8L3htcE1NOkhpc3Rvcnk+CiAgIDxwbHVzOkltYWdlU3VwcGxpZXI+CiAgICA8cmRmOlNlcS8+CiAgIDwvcGx1czpJbWFnZVN1cHBsaWVyPgogICA8cGx1czpJbWFnZUNyZWF0b3I+CiAgICA8cmRmOlNlcS8+CiAgIDwvcGx1czpJbWFnZUNyZWF0b3I+CiAgIDxwbHVzOkNvcHlyaWdodE93bmVyPgogICAgPHJkZjpTZXEvPgogICA8L3BsdXM6Q29weXJpZ2h0T3duZXI+CiAgIDxwbHVzOkxpY2Vuc29yPgogICAgPHJkZjpTZXEvPgogICA8L3BsdXM6TGljZW5zb3I+CiAgPC9yZGY6RGVzY3JpcHRpb24+CiA8L3JkZjpSREY+CjwveDp4bXBtZXRhPgogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgCjw/eHBhY2tldCBlbmQ9InciPz5B0N2VAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5QocDQsbu2tTYwAAANdJREFUOMtjvKspwIAJ2BgZfv1nIBEwIVhSCFHRjh/MGgzkm8UX85vVHOqWj5vZ/t5kYNYm3SwWA07hlh+8hj9F074KVv1mYGL5tZ+Rxfg/X9AfksxiYWBg+HPh+w8dAT7DF4ysTB+X8TH8+87AwPDnDOP7Myzk+JGJ98u7fdI/HksyCXyDWmL6X6CcdHcxMDB8Wc37/81XBgYGRglGiMi/54w/7zCT467/b75DGS8YGBgYRLu/M0n8/76WkRx3oYF3czj/3qQgTSADMgzCaRZ5YNSs4WEWALSgQT3EmxTDAAAAAElFTkSuQmCC'
            }
        };
        return {
            getNativeNameFromCode:(code:string)=>{
                if(_.hasIn(languages,code)){
                    return languages[code].nativeName;
                }
                return languages.en.nativeName;
            },
            getAllLanguages:languages,
            getLanguageObjectFromCode:(code:string):LanguageObject=>{
                if(_.hasIn(languages,code)){
                    return languages[code];
                }
                return languages.en;
            }
        }
    }
    /*
    * Defines action for replacing placehoders/title or disabling elements when you edit or create patient,mwl or study
    * Used in helpers/placeholderchanger.directive.ts
    * */
    public static get IODPLACEHOLDERS(): any{
        return {
            '00100020': {
                'create': {
                    placeholder: $localize `:@@leave_it_blank_to_generate_it_automatically:Leave it blank to generate it automatically!`,
                    action: 'replace'
                }
            },
            '0020000D': {
                'create': {
                    placeholder: $localize `:@@leave_it_blank_to_generate_it_automatically:Leave it blank to generate it automatically!`,
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
                groupName:$localize `:@@hl7_specific_char.single_byte_groupe_name:Single-Byte Character Sets`,
                groupValues:[
                    {
                        title: $localize `:@@hl7_specific_char.ASCII:ASCII`,
                        value: "ASCII"
                    },
                    {
                        title: $localize `:@@hl7_specific_char.gb_18030_2000:GB 18030-2000`,
                        value:"GB 18030-2000"
                    },
                    {
                        title:$localize `:@@hl7_specific_char.latin_alphabet_no._1:Latin alphabet No. 1`,
                        value:"8859/1"
                    },

                    {
                        title:$localize `:@@hl7_specific_char.latin_alphabet_no._2:Latin alphabet No. 2`,
                        value:"8859/2"
                    },
                    {
                        title:$localize `:@@hl7_specific_char.thai:Thai`,
                        value:"CNS 11643-1992"
                    },
                    {
                        title:$localize `:@@hl7_specific_char.latin_alphabet_no._3:Latin alphabet No. 3`,
                        value:"8859/3"
                    },
                    {
                        title:$localize `:@@hl7_specific_char.latin_alphabet_no._4:Latin alphabet No. 4`,
                        value:"8859/4"
                    },
                    {
                        title:$localize `:@@japanese:Japanese`,
                        value:"ISO IR14"
                    },
                    {
                        title: $localize `:@@hl7_specific_char.cyrillic:Cyrillic`,
                        value:"8859/5"
                    },
                    {
                        title: $localize `:@@hl7_specific_char.arabic:Arabic`,
                        value:"8859/6"
                    },
                    {
                        title: $localize `:@@hl7_specific_char.greek:Greek`,
                        value:"8859/7"
                    },

                    {
                        title: $localize `:@@hl7_specific_char.hebrew:Hebrew`,
                        value:"8859/8"
                    },
                    {
                        title:$localize `:@@hl7_specific_char.latin_alphabet_no._5:Latin alphabet No. 5`,
                        value:"8859/9"
                    }
                ]
            },{
                groupName:$localize `:@@hl7_specific_char.multi_byte_set:Multi-Byte Character Sets`,
                groupValues:[
                    {
                        title:$localize `:@@hl7_specific_char.japanese:Japanese (Kanji)`,
                        value:"ISO IR87"
                    },{
                        title:$localize `:@@hl7_specific_char.japanese_supplementary:Japanese (Supplementary Kanji set)`,
                        value:"ISO IR159"
                    },{
                        title:$localize `:@@hl7_specific_char.korean:Korean`,
                        value:"KS X 1001"
                    },{
                        title:$localize `:@@hl7_specific_char.unicode:Unicode`,
                        value:"UNICODE"
                    },
                    {
                        title:$localize `:@@hl7_specific_char.unicode_utf8:Unicode in UTF-8`,
                        value:"UNICODE UTF-8"
                    }
                ]
            }
        ]
    }
    public static get DICOM_SPECIFIC_CHAR(): any{
        return [
            {
                groupName:$localize `:@@dicom_specific_char.single_byte_character:Single-Byte Character Sets`,
                groupValues:[
                    {
                        title:$localize `:@@dicom_specific_char.latin_alphabet_no._1:Latin alphabet No. 1`,
                        value:"ISO_IR 100"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.latin_alphabet_no._2:Latin alphabet No. 2`,
                        value:"ISO_IR 101"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.latin_alphabet_no._3:Latin alphabet No. 3`,
                        value:"ISO_IR 101"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.latin_alphabet_no._4:Latin alphabet No. 4`,
                        value:"ISO_IR 101"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.cyrillic:Cyrillic`,
                        value:"ISO_IR 144"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.arabic:Arabic`,
                        value:"ISO_IR 127"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.greek:Greek`,
                        value:"ISO_IR 126"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.hebrew:Hebrew`,
                        value:"ISO_IR 138"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.latin_alphabet_no_5:Latin alphabet No. 5`,
                        value:"ISO_IR 148"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.japanese:Japanese`,
                        value:"ISO_IR 13"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.thai:Thai`,
                        value:"ISO_IR 166"
                    }
                ]
            },{
                groupName: $localize `:@@dicom_specific_char.multi_byte_character_without_extension_group_name:Multi-Byte Character Sets Without Code Extensions`,
                groupValues:[
                    {
                        title: $localize `:@@dicom_specific_char.unicode:Unicode in UTF-8`,
                        value:"ISO_IR 192"
                    },{
                        title: $localize `:@@dicom_specific_char.gb18030:GB18030`,
                        value:"GB18030"
                    },{
                        title: $localize `:@@dicom_specific_char.gbk:GBK`,
                        value:"GBK"
                    }
                ]
            },
            {
                groupName:$localize `:@@dicom_specific_char.single_byte_character_set_with_extension_group_name:Single-Byte Character Sets with Code Extensions`,
                groupValues:[
                    {
                        title:$localize `:@@dicom_specific_char.default_repertoire:Default repertoire`,
                        value:"ISO 2022 IR 6"
                    },{
                        title:$localize `:@@dicom_specific_char.latin_alphabet_no._1:Latin alphabet No. 1`,
                        value:"ISO 2022 IR 100"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.latin_alphabet_no._2:Latin alphabet No. 2`,
                        value:"ISO 2022 IR 101"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.latin_alphabet_no._3:Latin alphabet No. 3`,
                        value:"ISO 2022 IR 109"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.latin_alphabet_no._4:Latin alphabet No. 4`,
                        value:"ISO 2022 IR 110"
                    },
                    {
                        title:$localize`:@@dicom_specific_char.cyrillic:Cyrillic`,
                        value:"ISO 2022 IR 144"
                    },
                    {
                        title:$localize`:@@dicom_specific_char.arabic:Arabic`,
                        value:"ISO 2022 IR 127"
                    },
                    {
                        title:$localize`:@@dicom_specific_char.greek:Greek`,
                        value:"ISO 2022 IR 126"
                    },
                    {
                        title:$localize`:@@dicom_specific_char.hebrew:Hebrew`,
                        value:"ISO 2022 IR 138"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.latin_alphabet_no_5:Latin alphabet No. 5`,
                        value:"ISO 2022 IR 148"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.japanese:Japanese`,
                        value:"ISO 2022 IR 13"
                    },
                    {
                        title:$localize `:@@dicom_specific_char.thai:Thai`,
                        value:"ISO 2022 IR 166"
                    }
                ]
            },{
                groupName:$localize `:@@dicom_specific_char.multi_byte_character_sets_group_name:Multi-Byte Character Sets`,
                groupValues:[
                    {
                        title:$localize `:@@dicom_specific_char.japanese_kanji:Japanese (Kanji)`,
                        value:"ISO 2022 IR 87"
                    },{
                        title:$localize `:@@dicom_specific_char.japanese_supplementary_kanji:Japanese (Supplementary Kanji set)`,
                        value:"ISO 2022 IR 159"
                    },{
                        title:$localize `:@@dicom_specific_char.korean:Korean`,
                        value:"ISO 2022 IR 149"
                    },{
                        title:$localize `:@@dicom_specific_char.simplified_chinese:Simplified Chinese`,
                        value:"ISO 2022 IR 58"
                    }
                ]
            }
        ]

    }
    static getActionText(action){
        switch (action){
            case 'cancel':
                return $localize `:@@cancel:cancel`;
            case 'reschedule':
                return $localize `:@@reschedule:reschedule`;
            case 'delete':
                return $localize `:@@delete:delete`;
        }
        return '';
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
        ];
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
                msg:$localize `:@@dynamic_formatter.create_first_aet:Create first an AE Title!`,
                pathInDevice:'dicomNetworkAE'
            },
            dcmQueueName:{
                key:'dcmQueueName',
                labelKey:'{dicomDescription} ({dcmQueueName})',
                msg:$localize `:@@dynamic_formatter.configure_first_a_queue:Configure first an Queue`,
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmQueue'
            },
            dcmExporterID:{
                key:'dcmExporterID',
                labelKey:'{dcmExporterID}',
                msg:$localize `:@@dynamic_formatter.exporter:Create first an Exporter!`,
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmExporter'
            },
            dcmStorageID:{
                key:'dcmStorageID',
                labelKey:'{dcmStorageID}',
                msg:$localize `:@@dynamic_formatter.storage:Create first an Storage!`,
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmStorage'
            },
            dcmQueryRetrieveViewID:{
                key:'dcmQueryRetrieveViewID',
                labelKey:'{dcmQueryRetrieveViewID}',
                msg:$localize `:@@dynamic_formatter.query_retrieve_view:Create first an Query Retrieve View!`,
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmQueryRetrieveView'
            },
            dcmRejectionNoteCode:{
                key:'dcmRejectionNoteCode',
                labelKey:'{dcmRejectionNoteLabel}',
                msg:$localize `:@@dynamic_formatter.rejection_note:Create first an Rejection Note!`,
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmRejectionNote'
            },
            dcmuiDeviceURLObject:{
                key:'dcmuiDeviceURLName',
                labelKey:'{dcmuiDeviceURLName}',
                msg:$localize `:@@dynamic_formatter.ui _device_url:Create first an UI Device URL!`,
                pathInDevice:'dcmDevice.dcmuiConfig[0].dcmuiDeviceURLObject'
            },
            dcmuiDeviceClusterObject:{
                key:'dcmuiDeviceClusterName',
                labelKey:'{dcmuiDeviceClusterName}',
                msg:$localize `:@@dynamic_formatter.ui_device_cluster:Create first an UI Device Cluster!`,
                pathInDevice:'dcmDevice.dcmuiConfig["0"].dcmuiDeviceClusterObject'
            },
            dcmuiElasticsearchConfig:{
                key:'dcmuiElasticsearchURLName',
                labelKey:'{dcmuiElasticsearchURLName}',
                msg:$localize `:@@dynamic_formatter.ui_elasticsearch_url:Create first an UI Elasticsearch URL!`,
                pathInDevice:'dcmDevice.dcmuiConfig[0].dcmuiElasticsearchConfig[0].dcmuiElasticsearchURLObjects'
            },
            dcmKeycloakClient:{
                key:'dcmKeycloakClientID',
                labelKey:'{dcmKeycloakClientID}',
                msg:$localize `:@@dynamic_formatter.keycloak_client:Create first a Keycloak Client!`,
                pathInDevice:'dcmDevice.dcmKeycloakClient'
            },
            dcmIDGenerator:{
                key:'dcmIDGeneratorName',
                labelKey:'{dcmIDGeneratorName}',
                msg:$localize `:@@dynamic_formatter.id_generator:Create first an ID Generator!`,
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmIDGenerator'
            },
            dcmPDQServiceID:{
                key:'dcmPDQServiceID',
                labelKey:'{dcmPDQServiceID}',
                msg:$localize `:@@dynamic_formatter.pdq_service:Create a PDQ Service Descriptor first!`,
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmPDQService'
            },
            dcmWebApp:{
                key:'dcmWebAppName',
                labelKey:'{dcmWebAppName}',
                msg:$localize `:@@dynamic_formatter.web_app:Create first a Web Application!`,
                pathInDevice:'dcmDevice.dcmWebApp'
            },
            dcmBulkDataDescriptorID:{
                key:'dcmBulkDataDescriptorID',
                labelKey:'{dcmBulkDataDescriptorID}',
                msg:$localize `:@@dynamic_formatter.web_app:Create first a Bulkdata Descriptor!`,
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmBulkDataDescriptor'
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
        return "hl7apps";
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
                nextCheck:"/study/study"
            },
            "/lifecycle-management":{
                permissionsAction:"menu-lifecycle_management",
                nextCheck:"/study/study"
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
            "/study/series":{
                permissionsAction:"tab-study-series"
            },
            "/study/mwl":{
                permissionsAction:"tab-study-mwl"
            },
            "/study/mpps":{
                permissionsAction:"tab-study-mpps"
            },
            "/study/uwl":{
                permissionsAction:"tab-study-uwl"
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
            "/statistics/studies-stored/simple":{
                permissionsAction:"tab-statistics->studies-stored-simple"
            },
            "/statistics/studies-stored/detailed":{
                permissionsAction:"tab-statistics->studies-stored-detailed"
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

            ]
        }else{
            return [
                {
                    tag:"input",
                    type:"text",
                    filterKey:"PatientName",
                    placeholder:$localize `:@@patient_family_name:Patient family name`,
                    description:$localize `:@@patient_family_name_tooltip:Order of name components in the search field differs from the rendered person names in the list`
                },
                {
                    tag:"checkbox",
                    filterKey:"fuzzymatching",
                    text:$localize `:@@fuzzy_matching:Fuzzy Matching`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"PatientID",
                    description:$localize `:@@patient_id:Patient ID`,
                    placeholder:$localize `:@@patient_id:Patient ID`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"IssuerOfPatientID",
                    description:$localize `:@@issuer_of_patient:Issuer of Patient`,
                    placeholder:$localize `:@@issuer_of_patient:Issuer of Patient`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"AccessionNumber",
                    description:$localize `:@@accession_number:Accession number`,
                    placeholder:$localize `:@@accession_number:Accession number`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"IssuerOfAccessionNumberSequence.LocalNamespaceEntityID",
                    description:$localize `:@@issuer_of_accession_number:Issuer of accession number`,
                    placeholder:$localize `:@@issuer_of_accession_number:Issuer of accession number`
                },{
                    tag:"select",
                    filterKey:"includefield",
                    options:[
                        new SelectDropdown("", $localize `:@@dicom:dicom`,$localize `:@@search_response_payload_according_dicom_ps_3.18:Search Response Payload according DICOM PS 3.18`),
                        new SelectDropdown("all", $localize `:@@all:all`, $localize `:@@all_available_attributes:all available attributes`)
                    ],
                    description:$localize `:@@include_field:Include field`,
                    placeholder:$localize `:@@include_field:Include field`,
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"AdmissionID",
                    description:$localize `:@@admission_id:Admission ID`,
                    placeholder:$localize `:@@admission_id:Admission ID`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"IssuerOfAdmissionIDSequence.LocalNamespaceEntityID",
                    description:$localize `:@@issuer_of_admission_id_sequence:Issuer of Admission ID Sequence`,
                    placeholder:$localize `:@@issuer_of_admission_id_sequence:Issuer of Admission ID Sequence`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.ScheduledStationAETitle",
                    description:$localize `:@@scheduled_station_ae_title:Scheduled Station AE Title`,
                    placeholder:$localize `:@@scheduled_station_ae_title:Scheduled Station AE Title`
                },{
                    tag:"modality",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.Modality",
                    placeholder:$localize `:@@modality:Modality`,
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StudyInstanceUID",
                    description:$localize `:@@study_instance_uid:Study Instance UID`,
                    placeholder:$localize `:@@study_instance_uid:Study Instance UID`
                },{
                    tag:"input",
                    type:"number",
                    filterKey:"limit",
                    description:$localize `:@@limit:Limit`,
                    placeholder:$localize `:@@limit_of_mwl:Limit of MWL`
                },{
                    tag:"select",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.ScheduledProcedureStepStatus",
                    options:[
                        new SelectDropdown("STARTED", $localize `:@@STARTED:STARTED`),
                        new SelectDropdown("ARRIVED", $localize `:@@ARRIVED:ARRIVED`),
                        new SelectDropdown("READY", $localize `:@@READY:READY`),
                        new SelectDropdown("DEPARTED", $localize `:@@DEPARTED:DEPARTED`),
                        new SelectDropdown("SCHEDULED", $localize `:@@SCHEDULED:SCHEDULED`),
                        new SelectDropdown("COMPLETED", $localize `:@@COMPLETED:COMPLETED`),
                        new SelectDropdown("CANCELED", $localize `:@@CANCELED:CANCELED`),
                        new SelectDropdown("DISCONTINUED", $localize `:@@DISCONTINUED:DISCONTINUED`)
                    ],
                    showStar:true,
                    description:$localize `:@@sps_status:SPS Status`,
                    placeholder:$localize `:@@sps_status:SPS Status`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.ScheduledPerformingPhysicianName",
                    description:$localize `:@@person_family_name_tooltip:Order of name components in the search field differs from the rendered person names in the list`,
                    placeholder:$localize `:@@sp_physicians_family_name:SP Physician's family name`
                }
                ,{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate",
                    description:$localize `:@@sps_start_date:SPS Start Date`,
                    placeholder:$localize `:@@scheduled_procedure_step_start_date:Scheduled Procedure Step Start Date`,
                    onlyDate:true
                },{
                    tag:"range-picker-time",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
                    description:$localize `:@@sps_start_time:SPS Start Time`,
                    placeholder:$localize `:@@scheduled_procedure_step_start_time:Scheduled Procedure Step Start Time`
                },
                {
                    tag:"select",
                    type:"text",
                    filterKey:"StudyStatusID",
                    options:[
                        new SelectDropdown("NONE", $localize `:@@NONE:NONE`),
                        new SelectDropdown("CREATED", $localize `:@@CREATED:CREATED`),
                        new SelectDropdown("SCHEDULED", $localize `:@@SCHEDULED:SCHEDULED`),
                        new SelectDropdown("ARRIVED", $localize `:@@ARRIVED:ARRIVED`),
                        new SelectDropdown("STARTED", $localize `:@@STARTED:STARTED`),
                        new SelectDropdown("COMPLETED", $localize `:@@COMPLETED:COMPLETED`),
                        new SelectDropdown("VERIFIED", $localize `:@@VERIFIED:VERIFIED`),
                        new SelectDropdown("READ", $localize `:@@READ:READ`),
                        new SelectDropdown("CANCELLED", $localize `:@@CANCELLED:CANCELLED`)
                    ],
                    showStar:true,
                    description:$localize `:@@study_status_id_agfa:Study Status ID - AGFA`,
                    placeholder:$localize `:@@study_status_id_agfa:Study Status ID - AGFA`
                }
            ]
        }
    }

    static MPPS_FILTER_SCHEMA(hidden?):FilterSchema{
        if(hidden){
            return [

            ]
        }else{
            return [
                {
                    tag:"input",
                    type:"text",
                    filterKey:"PatientName",
                    placeholder:$localize `:@@patient_family_name:Patient family name`,
                    description:$localize `:@@patient_family_name_tooltip:Order of name components in the search field differs from the rendered person names in the list`
                }, {
                    tag:"checkbox",
                    filterKey:"fuzzymatching",
                    text:$localize `:@@fuzzy_matching:Fuzzy Matching`
                }, {
                    tag:"input",
                    type:"text",
                    filterKey:"PatientID",
                    description:$localize `:@@patient_id:Patient ID`,
                    placeholder:$localize `:@@patient_id:Patient ID`
                }, {
                    tag:"input",
                    type:"text",
                    filterKey:"IssuerOfPatientID",
                    description:$localize `:@@issuer_of_patient:Issuer of Patient`,
                    placeholder:$localize `:@@issuer_of_patient:Issuer of Patient`
                }, {
                    tag:"select",
                    options:[
                        new SelectDropdown("F",$localize `:@@female:Female`),
                        new SelectDropdown("M",$localize `:@@male:Male`),
                        new SelectDropdown("O",$localize `:@@other:Other`)
                    ],
                    showStar:true,
                    filterKey:"PatientSex",
                    description:$localize `:@@patients_sex:Patient's Sex`,
                    placeholder:$localize `:@@patients_sex:Patient's Sex`
                }, {
                    tag:"p-calendar",
                    filterKey:"PatientBirthDate",
                    description:$localize `:@@birth_date:Birth Date`
                }, {
                    tag: "select",
                    options: [
                        new SelectDropdown("UNVERIFIED", $localize `:@@UNVERIFIED:UNVERIFIED`),
                        new SelectDropdown("VERIFIED", $localize `:@@VERIFIED:VERIFIED`),
                        new SelectDropdown("NOT_FOUND", $localize `:@@NOT_FOUND:NOT_FOUND`),
                        new SelectDropdown("VERIFICATION_FAILED", $localize `:@@VERIFICATION_FAILED:VERIFICATION_FAILED`)
                    ],
                    showStar: true,
                    filterKey: "patientVerificationStatus",
                    description: $localize `:@@verification_status:Verification Status`,
                    placeholder: $localize `:@@verification_status:Verification Status`
                }, {
                    tag:"input",
                    type:"text",
                    filterKey:"ScheduledStepAttributesSequence.AccessionNumber",
                    description:$localize `:@@accession_number:Accession number`,
                    placeholder:$localize `:@@accession_number:Accession number`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"ScheduledStepAttributesSequence.IssuerOfAccessionNumberSequence.LocalNamespaceEntityID",
                    description:$localize `:@@issuer_of_accession_number:Issuer of accession number`,
                    placeholder:$localize `:@@issuer_of_accession_number:Issuer of accession number`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"ScheduledStepAttributesSequence.StudyInstanceUID",
                    description:$localize `:@@study_instance_uid:Study Instance UID`,
                    placeholder:$localize `:@@study_instance_uid:Study Instance UID`
                }, {
                    tag:"input",
                    type:"number",
                    filterKey:"limit",
                    description:$localize `:@@limit:Limit`,
                    placeholder:$localize `:@@limit_of_mpps:Limit of MPPS`
                },{
                    tag:"select",
                    type:"text",
                    filterKey:"ScheduledProcedureStepSequence.ScheduledProcedureStepStatus",
                    options:[
                        new SelectDropdown("IN PROGRESS",$localize `:@@IN_PROGRESS:IN PROGRESS`),
                        new SelectDropdown("COMPLETED", $localize `:@@COMPLETED:COMPLETED`),
                        new SelectDropdown("DISCONTINUED", $localize `:@@DISCONTINUED:DISCONTINUED`)
                    ],
                    showStar:true,
                    description:$localize `:@@pps_status:Performed Procedure Step Status`,
                    placeholder:$localize `:@@pps_status:Performed Procedure Step Status`
                }, {
                    tag:"range-picker",
                    type:"text",
                    filterKey:"PerformedProcedureStepStartDate",
                    description:$localize `:@@study.performed_procedure_step_start_date:Performed Procedure Step Start Date`,
                    placeholder:$localize `:@@study.pps_start_date:PPS Start Date`,
                    onlyDate:true
                }, {
                    tag:"range-picker-time",
                    type:"text",
                    filterKey:"PerformedProcedureStepStartTime",
                    description:$localize `:@@study.performed_procedure_step_start_time:Performed Procedure Step Start Time`,
                    placeholder:$localize `:@@study.pps_start_time:PPS Start Time`,
                },{
                    tag:"select",
                    filterKey:"includefield",
                    options:[
                        new SelectDropdown("", $localize `:@@dicom:dicom`,$localize `:@@search_response_payload_according_dicom_ps_3.18:Search Response Payload according DICOM PS 3.18`),
                        new SelectDropdown("all", $localize `:@@all:all`, $localize `:@@all_available_attributes:all available attributes`)
                    ],
                    description:$localize `:@@include_field:Include field`,
                    placeholder:$localize `:@@include_field:Include field`,
                }
            ]
        }
    }

    static UWL_FILTER_SCHEMA(hidden?):FilterSchema{
        if(hidden){
            return [{
                tag:"code-selector",
                codes:[
                    {
                        key:"00404025.00080100",
                        label:$localize `:@@code_value_00080100:Code Value (0008,0100)`
                    },{
                        key:"00404025.00080102",
                        label:$localize `:@@coding_scheme_designator_00080102:Coding scheme designator (0008,0102)`
                    }
                ],
                description:$localize `:@@scheduled_station_name_code_sequence_00404025:Scheduled Station Name Code Sequence (0040,4025)`,
                placeholder:$localize `:@@station_name_code:Station Name Code`
            },{
                tag:"code-selector",
                codes:[
                    {
                        key:"00404026.00080100",
                        label:$localize `:@@code_value_00080100:Code Value (0008,0100)`
                    },{
                        key:"00404026.00080102",
                        label:$localize `:@@coding_scheme_designator_00080102:Coding scheme designator (0008,0102)`
                    }
                ],
                description:$localize `:@@scheduled_station_class_code_sequence_00404026:Scheduled Station Class Code Sequence (0040,4026)`,
                placeholder:$localize `:@@station_class_code:Station Class Code`
            },{
                tag:"code-selector",
                codes:[
                    {
                        key:"00404027.00080100",
                        label:$localize `:@@code_value_00080100:Code Value (0008,0100)`
                    },{
                        key:"00404027.00080102",
                        label:$localize `:@@coding_scheme_designator_00080102:Coding scheme designator (0008,0102)`
                    }
                ],
                description: $localize `:@@scheduled_station_geographic_location_code_sequence_00404027:Scheduled Station Geographic Location Code Sequence (0040,4027)`,
                placeholder: $localize `:@@geographic_location_code:Geographic Location Code`
            },{
                tag:"code-selector",
                codes:[
                    {
                        key:"00404034.00404009.00080100",
                        label: $localize `:@@code_value_00080100:Code Value (0008,0100)`
                    },{
                        key:"00404034.00404009.00080102",
                        label: $localize `:@@coding_scheme_designator_00080102:Coding scheme designator (0008,0102)`
                    }
                ],
                description:$localize `:@@scheduled_human_performers_sequence_00404034:Scheduled Human Performers Sequence (0040,4034)`,
                placeholder:$localize `:@@human_performers:Human Performers`
            },{
                tag:"range-picker",
                type:"text",
                filterKey:"00404010",
                description:$localize `:@@scheduled_procedure_step_modification_date_and_time:Scheduled Procedure Step Modification Date and Time`,
                placeholder:$localize `:@@step_modification_time:Step Modification Time`
            }
            ]
        }else{
            return [
                {
                    tag:"input",
                    type:"text",
                    filterKey:"00100010",
                    description:$localize `:@@patient_family_name:Patient family name`,
                    placeholder:$localize `:@@patient_family_name:Patient family name`
                },
                {
                    tag:"checkbox",
                    filterKey:"fuzzymatching",
                    text:$localize `:@@fuzzy_matching:Fuzzy Matching`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"00100020",
                    description:$localize `:@@patient_id:Patient ID`,
                    placeholder:$localize `:@@patient_id:Patient ID`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"00100021",
                    description:$localize `:@@issuer_of_patient_id:Issuer of Patient ID`,
                    placeholder:$localize `:@@issuer_of_patient_id:Issuer of Patient ID`
                },
                {
                    tag:"p-calendar",
                    filterKey:"PatientBirthDate",
                    description:$localize `:@@birth_date:Birth Date`
                },
                {
                    tag:"select",
                    options:[
                        new SelectDropdown("F",$localize `:@@f:F`),
                        new SelectDropdown("M",$localize `:@@m:M`),
                        new SelectDropdown("O",$localize `:@@o:O`)
                    ],
                    showStar:true,
                    filterKey:"00100040",
                    description:$localize `:@@patients_sex:Patient's Sex`,
                    placeholder:$localize `:@@patients_sex:Patient's Sex`
                },
                {
                    tag:"select",
                    filterKey:"includefield",
                    options:[
                        new SelectDropdown("", $localize `:@@dicom:dicom`,$localize `:@@search_response_payload_according_dicom_ps_3.18:Search Response Payload according DICOM PS 3.18`),
                        new SelectDropdown("all", $localize `:@@all:all`, $localize `:@@all_available_attributes:all available attributes`)
                    ],
                    description:$localize `:@@include_field:Include field`,
                    placeholder:$localize `:@@include_field:Include field`,
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"00741202",
                    description:$localize `:@@worklist_label:Worklist Label`,
                    placeholder:$localize `:@@worklist_label:Worklist Label`
                },{
                    tag:"select",
                    options:[
                        new SelectDropdown("INCOMPLETE",$localize `:@@INCOMPLETE:INCOMPLETE`),
                        new SelectDropdown("UNAVAILABLE",$localize `:@@UNAVAILABLE:UNAVAILABLE`),
                        new SelectDropdown("READY",$localize `:@@READY:READY`)
                    ],
                    showStar:true,
                    filterKey:"00404041",
                    description:$localize `:@@input_readiness_state:Input Readiness State`,
                    placeholder:$localize `:@@input_readiness_state:Input Readiness State`
                },{
                    tag:"input",
                    type:"number",
                    filterKey:"limit",
                    description:$localize `:@@limit:Limit`,
                    placeholder:$localize `:@@limit_of_uwl:Limit of UWL`
                },{
                    tag:"select",
                    options:[
                        new SelectDropdown("SCHEDULED",$localize `:@@SCHEDULED:SCHEDULED`),
                        new SelectDropdown("IN PROGRESS",$localize `:@@IN_PROGRESS:IN PROGRESS`),
                        new SelectDropdown("CANCELED",$localize `:@@CANCELED:CANCELED`),
                        new SelectDropdown("COMPLETED",$localize `:@@COMPLETED:COMPLETED`)
                    ],
                    showStar:true,
                    filterKey:"00741000",
                    description:$localize `:@@procedure_step_state:Procedure Step State`,
                    placeholder:$localize `:@@procedure_step_state:Procedure Step State`
                },{
                    tag:"select",
                    options:[
                        new SelectDropdown("LOW",$localize `:@@LOW:LOW`),
                        new SelectDropdown("MEDIUM",$localize `:@@MEDIUM:MEDIUM`),
                        new SelectDropdown("HIGH",$localize `:@@HIGH:HIGH`)
                    ],
                    showStar:true,
                    filterKey:"00741200",
                    description:$localize `:@@scheduled_procedure_step_priority:Scheduled Procedure Step Priority`,
                    placeholder:$localize `:@@s._p._step_priority:S. P. Step Priority`
                },{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"00404005",
                    description:$localize `:@@scheduled_procedure_step_start_date_and_time:Scheduled Procedure Step Start Date and Time`,
                    placeholder:$localize `:@@s._procedure_step_date:S. Procedure Step Date`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"00741204",
                    description:$localize `:@@procedure_step_label:Procedure Step Label`,
                    placeholder:$localize `:@@procedure_step_label:Procedure Step Label`
                },{
                    tag:"code-selector",
                    codes:[
                        {
                            key:"00404018.00080100",
                            label:$localize `:@@code_value_00080100:Code Value (0008,0100)`
                        },{
                            key:"00404018.00080102",
                            label:$localize `:@@coding_scheme_designator_00080102:Coding scheme designator (0008,0102)`
                        }
                    ],
                    description:$localize `:@@Scheduled workitem_code_sequence_00404018:Scheduled Workitem Code Sequence (0040,4018)`,
                    placeholder:$localize `:@@scheduled_workitem:Scheduled Workitem`
                },{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"00404011",
                    description:$localize `:@@expected_completion_date_and_time:Expected Completion Date and Time`,
                    placeholder:$localize `:@@e._completion_date:E. Completion Date`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"00380010",
                    description:$localize `:@@admission_id:Admission ID`,
                    placeholder:$localize `:@@admission_id:Admission ID`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"00380014.00400031",
                    description:$localize `:@@issuer_of_admission_id_sequence:Issuer of Admission ID Sequence`,
                    placeholder:$localize `:@@issuer_of_admission_id_sequence:Issuer of Admission ID Sequence`
                },{
                    tag:"checkbox",
                    filterKey:"template",
                    text:$localize `:@@template:Template`,
                    description:$localize `:@@template:Template`
                },{
                    tag:"code-selector",
                    codes:[
                        {
                            key:"0040A370.0020000D",
                            label:$localize `:@@study_instance_uid_0020000d:Study Instance UID (0020,000D)`
                        },{
                            key:"0040A370.00080050",
                            label:$localize `:@@accession_number_00080050:Accession Number (0008,0050)`
                        },{
                            key:"0040A370.00080051.00400031",
                            label:$localize `:@@Issuer_of_Accession_number_sequence_00080051:Issuer of Accession Number Sequence (0008,0051)`
                        },{
                            key:"0040A370.00401001",
                            label:$localize `:@@requested_procedure_id_00401001:Requested Procedure ID (0040,1001)`
                        },{
                            key:"0040A370.00321032",
                            label:$localize `:@@requesting_physician_00321032:Requesting Physician (0032,1032)`
                        },{
                            key:"0040A370.00321033",
                            label:$localize `:@@requesting_service_00321033:Requesting Service  (0032,1033)`
                        }
                    ],
                    description:$localize `:@@referenced_request_sequence_0040A370:Referenced Request Sequence (0040,A370)`,
                    placeholder:$localize `:@@request_sequence:Request Sequence`
                }
            ]
        }
    }

    static STUDY_FILTER_SCHEMA(aets, storages, hidden?):FilterSchema{
        if(hidden){
            return [
                {
                    tag:"input",
                    type:"text",
                    filterKey:"InstitutionName",
                    description:$localize `:@@institution_name:Institution Name`,
                    placeholder:$localize `:@@institution_name:Institution Name`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StationName",
                    description:$localize `:@@station_name:Station Name`,
                    placeholder:$localize `:@@station_name:Station Name`
                },{
                    tag:"multi-select",
                    filterKey:"SOPClassesInStudy",
                    options:Object.keys(sopObject).map(sopKey=>{
                        return new SelectDropdown(sopKey, sopObject[sopKey], sopKey)
                    }),
                    showSearchField:true,
                    description:$localize `:@@sop_classes_in_study:SOP classes in study`,
                    placeholder:$localize `:@@sop_classes_in_study:SOP classes in study`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"SeriesDescription",
                    description:$localize `:@@series_description:Series Description`,
                    placeholder:$localize `:@@series_description:Series Description`
                },
                {
                    tag:"checkbox",
                    filterKey:"incomplete",
                    text:$localize `:@@only_incomplete:Only incomplete`,
                    description:$localize `:@@only_incomplete_studies:Only incomplete studies`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StudyID",
                    description:$localize `:@@study_id:Study ID`,
                    placeholder:$localize `:@@study_id:Study ID`
                },{
                    tag:"select",
                    options:aets,
                    showStar:true,
                    filterKey:"ExternalRetrieveAET",
                    description:$localize `:@@retrievable_from_external_retrieve_aet:Retrievable from external retrieve AET`,
                    placeholder:$localize `:@@external_retrieve_aet:External retrieve AET`
                },{
                    tag:"select",
                    options:aets,
                    showStar:true,
                    filterKey:"ExternalRetrieveAET!",
                    description:$localize `:@@not_retrievable_from_external_retrieve_aet:Not retrievable from external retrieve AET`,
                    placeholder:$localize `:@@not_retrievable_from_aet:Not retrievable from AET`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"BodyPartExamined",
                    description:$localize `:@@body_part_examined:Body part examined`,
                    placeholder:$localize `:@@body_part_examined:Body part examined`
                },
                {
                    tag:"checkbox",
                    filterKey:"compressionfailed",
                    text:$localize `:@@compression_failed:Compression Failed`
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
                        new SelectDropdown("UPDATABLE", $localize `:@@UPDATABLE:UPDATABLE`),
                        new SelectDropdown("FROZEN", $localize `:@@FROZEN:FROZEN`),
                        new SelectDropdown("REJECTED", $localize `:@@REJECTED:REJECTED`),
                        new SelectDropdown("EXPORT_SCHEDULED", $localize `:@@EXPORT_SCHEDULED:EXPORT_SCHEDULED`),
                        new SelectDropdown("FAILED_TO_EXPORT", $localize `:@@FAILED_TO_EXPORT:FAILED_TO_EXPORT`),
                        new SelectDropdown("FAILED_TO_REJECT", $localize `:@@FAILED_TO_REJECT:FAILED_TO_REJECT`),
                    ],
                    description:$localize `:@@expiration_state:Expiration State`,
                    placeholder:$localize `:@@expiration_state:Expiration State`,
                }
                ,{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"ExpirationDate",
                    description:$localize `:@@expiration_date:Expiration Date`
                },
                {
                    tag:"checkbox",
                    filterKey:"retrievefailed",
                    text:$localize `:@@only_failed_retrieving:Only failed retrieving`,
                    description:$localize `:@@only_failed_to_be_retrieved:Only failed to be retrieved`
                },
                {
                    tag:"checkbox",
                    filterKey:"storageVerificationFailed",
                    text:$localize `:@@verification_failed:Verification Failed`,
                    description:$localize `:@@storage_verification_failed:Storage Verification Failed`
                },
                {
                    tag:"checkbox",
                    filterKey:"metadataUpdateFailed",
                    text:$localize `:@@metadata_update_failed:Metadata Update Failed`,
                    description:$localize `:@@series_metadata_update_failed:Series Metadata Update Failed`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"ResponsiblePerson",
                    description:$localize `:@@responsible_person:Responsible Person`,
                    placeholder:$localize `:@@responsible_person:Responsible Person`
                },
                {
                    tag:"p-calendar",
                    type:"text",
                    filterKey:"PatientBirthDate",
                    description:$localize `:@@patients_birth_date:Patient's Birth Date`,
                    placeholder:$localize `:@@birth_date:Birth Date`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"ReceivingApplicationEntityTitleOfSeries",
                    description:$localize `:@@receiving_application_entity_title_of_series:Receiving Application Entity Title of Series`,
                    placeholder:$localize `:@@receiving_aet_of_series:Receiving AET of Series`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"SendingPresentationAddressOfSeries",
                    description:$localize `:@@sending_presentation_addr_of_series:Sending Presentation Address of Series`,
                    placeholder:$localize `:@@sending_presentation_addr_of_series:Sending Presentation Address of Series`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"ReceivingPresentationAddressOfSeries",
                    description:$localize `:@@receiving_presentation_addr_of_series:Receiving Presentation Address of Series`,
                    placeholder:$localize `:@@receiving_presentation_addr_of_series:Receiving Presentation Address of Series`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"AdmissionID",
                    description:$localize `:@@admission_id:Admission ID`,
                    placeholder:$localize `:@@admission_id:Admission ID`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"IssuerOfAdmissionIDSequence.LocalNamespaceEntityID",
                    description:$localize `:@@issuer_of_admission_id_sequence:Issuer of Admission ID Sequence`,
                    placeholder:$localize `:@@issuer_of_admission_id_sequence:Issuer of Admission ID Sequence`
                },
                {
                    tag:"select",
                    options:storages,
                    showStar:true,
                    filterKey:"storageID",
                    placeholder:$localize `:@@storage_id:Storage ID`,
                    description:$localize `:@@storage_id_tooltip:Only query studies whose objects are on a particular storage system`
                },
                {
                    tag:"checkbox",
                    filterKey:"storageClustered",
                    text:$localize `:@@storage_clustered:Storage Clustered`,
                    description:$localize `:@@storage_clustered_tooltip:Only query studies whose objects is on storage system selected by 'Storage ID' and also on other storage systems of the 'Storage Cluster' to which selected 'Storage ID' belongs`
                },
                {
                    tag:"checkbox",
                    filterKey:"storageExported",
                    text:$localize `:@@storage_exported:Storage Exported`,
                    description:$localize `:@@storage_exported_tooltip:Only query studies whose objects are on storage systems selected by 'Storage ID' and its corresponding configured 'Export Storage ID'`
                },
                {
                    tag:"select",
                    filterKey:"requested",
                    showStar:true,
                    options:[
                        new SelectDropdown("false", $localize `:@@unscheduled:Unscheduled`),
                        new SelectDropdown("true", $localize `:@@requested:Requested`)
                    ],
                    description:$localize `:@@unscheduled_studies:Unscheduled Studies`,
                    placeholder:$localize `:@@unscheduled_studies:Unscheduled Studies`,
                }
            ];
        }
        return [
            {
                tag:"select",
                options:aets,
                showStar:true,
                filterKey:"aet",
                description:$localize `:@@AET:AET`,
                placeholder:$localize `:@@AET:AET`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"PatientName",
                placeholder:$localize `:@@patient_family_name:Patient family name`,
                description:$localize `:@@patient_family_name_tooltip:Order of name components in the search field differs from the rendered person names in the list`
            },
            {
                tag:"checkbox",
                filterKey:"fuzzymatching",
                text:$localize `:@@fuzzy_matching:Fuzzy Matching`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"PatientID",
                description:$localize `:@@patient_id:Patient ID`,
                placeholder:$localize `:@@patient_id:Patient ID`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"IssuerOfPatientID",
                description:$localize `:@@issuer_of_patient:Issuer of patient`,
                placeholder:$localize `:@@issuer_of_patient:Issuer of patient`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"AccessionNumber",
                description:$localize `:@@accession_number:Accession number`,
                placeholder:$localize `:@@accession_number:Accession number`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"IssuerOfAccessionNumberSequence.LocalNamespaceEntityID",
                description:$localize `:@@issuer_of_accession_number:Issuer of accession number`,
                placeholder:$localize `:@@issuer_of_accession_number:Issuer of accession number`
            },{
                tag:"input",
                type:"text",
                filterKey:"StudyDescription",
                description:$localize `:@@study_description:Study Description`,
                placeholder:$localize `:@@study_description:Study Description`
            },
            {
                tag:"modality",
                type:"text",
                filterKey:"ModalitiesInStudy",
                placeholder:$localize `:@@modality:Modality`,
            },
            {
                tag:"input",
                type:"number",
                filterKey:"limit",
                description:$localize `:@@limit:Limit`,
                placeholder:$localize `:@@limit_of_studies:Limit of studies`
            },{
                tag:"select",
                filterKey:"includefield",
                options:[
                    new SelectDropdown("", $localize `:@@dicom:dicom`,$localize `:@@search_response_payload_according_dicom_ps_3.18:Search Response Payload according DICOM PS 3.18`),
                    new SelectDropdown("all", $localize `:@@all:all`, $localize `:@@all_available_attributes:all available attributes`)
                ],
                description:$localize `:@@include_field:Include field`,
                placeholder:$localize `:@@include_field:Include field`,
            },
            {
                tag:"input",
                type:"text",
                filterKey:"ReferringPhysicianName",
                placeholder:$localize `:@@referring_physician_family_name:Referring physician family name`,
                description:$localize `:@@person_family_name_tooltip:Order of name components in the search field differs from the rendered person names in the list`
            },{
                tag:"input",
                type:"text",
                filterKey:"InstitutionalDepartmentName",
                description:$localize `:@@institutional_department_name:Institutional Department Name`,
                placeholder:$localize `:@@institutional_department_name:Institutional Department Name`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"SendingApplicationEntityTitleOfSeries",
                description:$localize `:@@sending_application_entity_title_of_series:Sending Application Entity Title of Series`,
                placeholder:$localize `:@@sending_aet_of_series:Sending AET of Series`
            },{
                tag:"input",
                type:"text",
                filterKey:"StudyInstanceUID",
                description:$localize `:@@study_instance_uid:Study Instance UID`,
                placeholder:$localize `:@@study_instance_uid:Study Instance UID`
            },{
                tag:"range-picker-limit",
                type:"text",
                filterKey:"StudyDate",
                description:$localize `:@@study_date:Study date`,
                onlyDate:true
            },{
                tag:"range-picker-time",
                type:"text",
                filterKey:"StudyTime",
                description:$localize `:@@study_time:Study time`
            },{
                tag:"range-picker",
                type:"text",
                filterKey:"StudyReceiveDateTime",
                description:$localize `:@@study_received:Study Received`
            },{
                tag:"range-picker",
                type:"text",
                filterKey:"StudyAccessDateTime",
                description:$localize `:@@study_access:Study Access`
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
                    description:$localize `:@@institution_name:Institution Name`,
                    placeholder:$localize `:@@institution_name:Institution Name`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StationName",
                    description:$localize `:@@station_name:Station Name`,
                    placeholder:$localize `:@@station_name:Station Name`
                },
                {
                    tag:"multi-select",
                    filterKey:"SOPClassesInStudy",
                    options:Object.keys(sopObject).map(sopKey=>{
                        return new SelectDropdown(sopKey, sopObject[sopKey], sopKey)
                    }),
                    showSearchField:true,
                    description:$localize `:@@sop_classes_in_study:SOP classes in study`,
                    placeholder:$localize `:@@sop_classes_in_study:SOP classes in study`
                }
                ,{
                    tag:"input",
                    type:"text",
                    filterKey:"SeriesDescription",
                    description:$localize `:@@series_description:Series description`,
                    placeholder:$localize `:@@series_description:Series description`
                },
                {
                    tag:"checkbox",
                    filterKey:"incomplete",
                    text:$localize `:@@only_incomplete:Only incomplete`,
                    description:$localize `:@@only_incomplete_studies:Only incomplete studies`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StudyDescription",
                    description:$localize `:@@study_description:Study Description`,
                    placeholder:$localize `:@@study_description:Study Description`
                },{
                    tag:"select",
                    options:aets,
                    showStar:true,
                    filterKey:"ExternalRetrieveAET",
                    description:$localize `:@@retrievable_from_external_retrieve_aet:Retrievable from external retrieve AET`,
                    placeholder:$localize `:@@external_retrieve_aet:External retrieve AET`
                },{
                    tag:"select",
                    options:aets,
                    showStar:true,
                    filterKey:"ExternalRetrieveAET!",
                    description:$localize `:@@not_retrievable_from_external_retrieve_aet:Not retrievable from external retrieve AET`,
                    placeholder:$localize `:@@not_retrievable_from_aet:Not retrievable from AET`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"BodyPartExamined",
                    description:$localize `:@@body_part_examined:Body part examined`,
                    placeholder:$localize `:@@body_part_examined:Body part examined`
                }
                ,
                {
                    tag:"checkbox",
                    filterKey:"retrievefailed",
                    text:$localize `:@@only_failed_retrieving:Only failed retrieving`,
                    description:$localize `:@@only_failed_to_be_retrieved:Only failed to be retrieved`
                },
                {
                    tag:"checkbox",
                    filterKey:"storageVerificationFailed",
                    text:$localize `:@@verification_failed:Verification Failed`,
                    description:$localize `:@@storage_verification_failed:Storage Verification Failed`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"ResponsiblePerson",
                    description:$localize `:@@responsible_person:Responsible Person`,
                    placeholder:$localize `:@@responsible_person:Responsible Person`
                },{
                    tag:"p-calendar",
                    type:"text",
                    filterKey:"PatientsBirthDate",
                    description:$localize `:@@patients_birth_date:Patient's Birth Date`,
                    placeholder:$localize `:@@birth_date:Birth Date`
                },{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"StudyReceiveDateTime",
                    description:$localize `:@@study_received:Study Received`
                },{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"StudyAccessDateTime",
                    description:$localize `:@@study_access:Study Access`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"StudyInstanceUID",
                    description:$localize `:@@study_instance_uid:Study Instance UID`,
                    placeholder:$localize `:@@study_instance_uid:Study Instance UID`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StudyID",
                    description:$localize `:@@study_id:Study ID`,
                    placeholder:$localize `:@@study_id:Study ID`
                }
            ];
        }
        return [
            {
                tag:"input",
                type:"text",
                filterKey:"PatientName",
                description:$localize `:@@patient_family_name:Patient family name`,
                placeholder:$localize `:@@patient_family_name:Patient family name`
            },
            {
                tag:"checkbox",
                filterKey:"fuzzymatching",
                text:$localize `:@@fuzzy_matching:Fuzzy Matching`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"PatientID",
                description:$localize `:@@patient_id:Patient ID`,
                placeholder:$localize `:@@patient_id:Patient ID`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"IssuerOfPatientID",
                description:$localize `:@@issuer_of_patient:Issuer of patient`,
                placeholder:$localize `:@@issuer_of_patient:Issuer of patient`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"AccessionNumber",
                description:$localize `:@@accession_number:Accession number`,
                placeholder:$localize `:@@accession_number:Accession number`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"IssuerOfAccessionNumberSequence.LocalNamespaceEntityID",
                description:$localize `:@@issuer_of_accession_number:Issuer of accession number`,
                placeholder:$localize `:@@issuer_of_accession_number:Issuer of accession number`
            },
            {
                tag:"modality",
                type:"text",
                filterKey:"ModalitiesInStudy",
                placeholder:$localize `:@@modality:Modality`,
            },
            {
                tag:"input",
                type:"text",
                filterKey:"ReferringPhysicianName",
                placeholder:$localize `:@@referring_physician_family_name:Referring physician family name`,
                description:$localize `:@@person_family_name_tooltip:Order of name components in the search field differs from the rendered person names in the list`
            },{
                tag:"input",
                type:"text",
                filterKey:"InstitutionalDepartmentName",
                description:$localize `:@@institutional_department_name:Institutional Department Name`,
                placeholder:$localize `:@@institutional_department_name:Institutional Department Name`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"SendingApplicationEntityTitleOfSeries",
                description:$localize `:@@sending_application_entity_title_of_series:Sending Application Entity Title of Series`,
                placeholder:$localize `:@@sending_aet_of_series:Sending AET of Series`
            },{
                tag:"range-picker-limit",
                type:"text",
                filterKey:"StudyDate",
                description:$localize `:@@study_date:Study date`
            },{
                tag:"range-picker-time",
                type:"text",
                filterKey:"StudyTime",
                description:$localize `:@@study_time:Study time`
            },
            {
                tag:"input",
                type:"number",
                filterKey:"limit",
                description:$localize `:@@limit:Limit`,
                placeholder:$localize `:@@limit_of_studies:Limit of studies`
            },{
                tag:"select",
                filterKey:"includefield",
                options:[
                    new SelectDropdown("", $localize `:@@dicom:dicom`,$localize `:@@search_response_payload_according_dicom_ps_3.18:Search Response Payload according DICOM PS 3.18`),
                    new SelectDropdown("all", $localize `:@@all:all`, $localize `:@@all_available_attributes:all available attributes`)
                ],
                description:$localize `:@@include_field:Include field`,
                placeholder:$localize `:@@include_field:Include field`,
            },
            {
                tag:"checkbox",
                filterKey:"queue",
                text:$localize `:@@queued:Queued`
            },
            {
                tag:"checkbox",
                filterKey:"missing",
                text:$localize `:@@missing_studies:Missing Studies`
            },
            {
                tag:"checkbox",
                filterKey:"different",
                text:$localize `:@@different_studies:Different Studies`
            },{
                tag:"select",
                filterKey:"comparefield",
                options:attributeSet,
                description:$localize `:@@attribute_set:Attribute Set`,
                placeholder:$localize `:@@attribute_set:Attribute Set`,
            },{
                tag:"input",
                type:"text",
                filterKey:"taskID",
                description:$localize `:@@task_id:Task ID`,
                placeholder:$localize `:@@task_id:Task ID`
            },{
                tag:"input",
                type:"text",
                filterKey:"batchID",
                description:$localize `:@@batch_id:Batch ID`,
                placeholder:$localize `:@@batch_id:Batch ID`
            }
        ];
    }

    static SERIES_FILTER_SCHEMA(aets, storages, hidden?):FilterSchema{
        if(hidden){
            return [
                {
                    tag:"multi-select",
                    filterKey:"SOPClassesInStudy",
                    options:Object.keys(sopObject).map(sopKey=>{
                        return new SelectDropdown(sopKey, sopObject[sopKey], sopKey)
                    }),
                    showSearchField:true,
                    description:$localize `:@@sop_classes_in_study:SOP classes in study`,
                    placeholder:$localize `:@@sop_classes_in_study:SOP classes in study`
                },
                {
                    tag:"checkbox",
                    filterKey:"incomplete",
                    text:$localize `:@@only_incomplete:Only incomplete`,
                    description:$localize `:@@only_incomplete_studies:Only incomplete studies`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"StudyID",
                    description:$localize `:@@study_id:Study ID`,
                    placeholder:$localize `:@@study_id:Study ID`
                },{
                    tag:"select",
                    options:aets,
                    showStar:true,
                    filterKey:"ExternalRetrieveAET",
                    description:$localize `:@@retrievable_from_external_retrieve_aet:Retrievable from external retrieve AET`,
                    placeholder:$localize `:@@external_retrieve_aet:External retrieve AET`
                },{
                    tag:"select",
                    options:aets,
                    showStar:true,
                    filterKey:"ExternalRetrieveAET!",
                    description:$localize `:@@not_retrievable_from_external_retrieve_aet:Not retrievable from external retrieve AET`,
                    placeholder:$localize `:@@not_retrievable_from_aet:Not retrievable from AET`
                },
                {
                    tag:"checkbox",
                    filterKey:"compressionfailed",
                    text:$localize `:@@compression_failed:Compression Failed`
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
                        new SelectDropdown("UPDATABLE", $localize `:@@UPDATABLE:UPDATABLE`),
                        new SelectDropdown("FROZEN", $localize `:@@FROZEN:FROZEN`),
                        new SelectDropdown("REJECTED", $localize `:@@REJECTED:REJECTED`),
                        new SelectDropdown("EXPORT_SCHEDULED", $localize `:@@EXPORT_SCHEDULED:EXPORT_SCHEDULED`),
                        new SelectDropdown("FAILED_TO_EXPORT", $localize `:@@FAILED_TO_EXPORT:FAILED_TO_EXPORT`),
                        new SelectDropdown("FAILED_TO_REJECT", $localize `:@@FAILED_TO_REJECT:FAILED_TO_REJECT`),
                    ],
                    description:$localize `:@@expiration_state:Expiration State`,
                    placeholder:$localize `:@@expiration_state:Expiration State`,
                }
                ,{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"ExpirationDate",
                    description:$localize `:@@expiration_date:Expiration Date`
                },
                {
                    tag:"checkbox",
                    filterKey:"retrievefailed",
                    text:$localize `:@@only_failed_retrieving:Only failed retrieving`,
                    description:$localize `:@@only_failed_to_be_retrieved:Only failed to be retrieved`
                },
                {
                    tag:"checkbox",
                    filterKey:"storageVerificationFailed",
                    text:$localize `:@@verification_failed:Verification Failed`,
                    description:$localize `:@@storage_verification_failed:Storage Verification Failed`
                },
                {
                    tag:"checkbox",
                    filterKey:"metadataUpdateFailed",
                    text:$localize `:@@metadata_update_failed:Metadata Update Failed`,
                    description:$localize `:@@series_metadata_update_failed:Series Metadata Update Failed`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"ResponsiblePerson",
                    description:$localize `:@@responsible_person:Responsible Person`,
                    placeholder:$localize `:@@responsible_person:Responsible Person`
                },
                {
                    tag:"p-calendar",
                    type:"text",
                    filterKey:"PatientBirthDate",
                    description:$localize `:@@patients_birth_date:Patient's Birth Date`,
                    placeholder:$localize `:@@birth_date:Birth Date`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"ReceivingApplicationEntityTitleOfSeries",
                    description:$localize `:@@receiving_application_entity_title_of_series:Receiving Application Entity Title of Series`,
                    placeholder:$localize `:@@receiving_aet_of_series:Receiving AET of Series`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"SendingPresentationAddressOfSeries",
                    description:$localize `:@@sending_presentation_addr_of_series:Sending Presentation Address of Series`,
                    placeholder:$localize `:@@sending_presentation_addr_of_series:Sending Presentation Address of Series`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"ReceivingPresentationAddressOfSeries",
                    description:$localize `:@@receiving_presentation_addr_of_series:Receiving Presentation Address of Series`,
                    placeholder:$localize `:@@receiving_presentation_addr_of_series:Receiving Presentation Address of Series`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"AdmissionID",
                    description:$localize `:@@admission_id:Admission ID`,
                    placeholder:$localize `:@@admission_id:Admission ID`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"IssuerOfAdmissionIDSequence.LocalNamespaceEntityID",
                    description:$localize `:@@issuer_of_admission_id_sequence:Issuer of Admission ID Sequence`,
                    placeholder:$localize `:@@issuer_of_admission_id_sequence:Issuer of Admission ID Sequence`
                },
                {
                    tag:"select",
                    options:storages,
                    showStar:true,
                    filterKey:"storageID",
                    placeholder:$localize `:@@storage_id:Storage ID`,
                    description:$localize `:@@storage_id_tooltip:Only query studies whose objects are on a particular storage system`
                },
                {
                    tag:"checkbox",
                    filterKey:"storageClustered",
                    text:$localize `:@@storage_clustered:Storage Clustered`,
                    description:$localize `:@@storage_clustered_tooltip:Only query studies whose objects is on storage system selected by 'Storage ID' and also on other storage systems of the 'Storage Cluster' to which selected 'Storage ID' belongs`
                },
                {
                    tag:"checkbox",
                    filterKey:"storageExported",
                    text:$localize `:@@storage_exported:Storage Exported`,
                    description:$localize `:@@storage_exported_tooltip:Only query studies whose objects are on storage systems selected by 'Storage ID' and its corresponding configured 'Export Storage ID'`
                },
                {
                    tag:"select",
                    filterKey:"requested",
                    showStar:true,
                    options:[
                        new SelectDropdown("false", $localize `:@@unscheduled:Unscheduled`),
                        new SelectDropdown("true", $localize `:@@requested:Requested`)
                    ],
                    description:$localize `:@@unscheduled_studies:Unscheduled Studies`,
                    placeholder:$localize `:@@unscheduled_studies:Unscheduled Studies`,
                },{
                    tag:"range-picker-limit",
                    type:"text",
                    filterKey:"StudyDate",
                    description:$localize `:@@study_date:Study date`,
                    onlyDate:true
                },{
                    tag:"range-picker-time",
                    type:"text",
                    filterKey:"StudyTime",
                    description:$localize `:@@study_time:Study time`
                },{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"StudyReceiveDateTime",
                    description:$localize `:@@study_received:Study Received`
                },{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"StudyAccessDateTime",
                    description:$localize `:@@study_access:Study Access`
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"StudyDescription",
                    description:$localize `:@@study_description:Study Description`,
                    placeholder:$localize `:@@study_description:Study Description`
                },{
                    tag:"range-picker-limit",
                    type:"text",
                    filterKey:"PerformedProcedureStepStartDate",
                    description:$localize `:@@study.performed_procedure_step_start_date:Performed Procedure Step Start Date`,
                    onlyDate:true
                },{
                    tag:"range-picker-time",
                    type:"text",
                    filterKey:"PerformedProcedureStepStartTime",
                    description:$localize `:@@study.performed_procedure_step_start_time:Performed Procedure Step Start Time`
                },
            ];
        }
        return [
            {
                tag:"select",
                options:aets,
                showStar:true,
                filterKey:"aet",
                description:$localize `:@@AET:AET`,
                placeholder:$localize `:@@AET:AET`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"PatientName",
                placeholder:$localize `:@@patient_family_name:Patient family name`,
                description:$localize `:@@patient_family_name_tooltip:Order of name components in the search field differs from the rendered person names in the list`
            },
            {
                tag:"checkbox",
                filterKey:"fuzzymatching",
                text:$localize `:@@fuzzy_matching:Fuzzy Matching`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"PatientID",
                description:$localize `:@@patient_id:Patient ID`,
                placeholder:$localize `:@@patient_id:Patient ID`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"IssuerOfPatientID",
                description:$localize `:@@issuer_of_patient:Issuer of patient`,
                placeholder:$localize `:@@issuer_of_patient:Issuer of patient`
            },
            {
                tag:"input",
                type:"number",
                filterKey:"limit",
                description:$localize `:@@limit:Limit`,
                placeholder:$localize `:@@limit_of_studies:Limit of studies`
            },{
                tag:"select",
                filterKey:"includefield",
                options:[
                    new SelectDropdown("", $localize `:@@dicom:dicom`,$localize `:@@search_response_payload_according_dicom_ps_3.18:Search Response Payload according DICOM PS 3.18`),
                    new SelectDropdown("all", $localize `:@@all:all`, $localize `:@@all_available_attributes:all available attributes`)
                ],
                description:$localize `:@@include_field:Include field`,
                placeholder:$localize `:@@include_field:Include field`,
            },{
                tag:"input",
                type:"text",
                filterKey:"SeriesDescription",
                description:$localize `:@@series_description:Series Description`,
                placeholder:$localize `:@@series_description:Series Description`
            },{
                tag:"input",
                type:"text",
                filterKey:"SeriesInstanceUID",
                description:$localize `:@@series_instance_uid:Series Instance UID`,
                placeholder:$localize `:@@series_instance_uid:Series Instance UID`
            },{
                tag:"modality",
                type:"text",
                filterKey:"ModalitiesInStudy",
                placeholder:$localize `:@@modality:Modality`,
            },
            {
                tag:"input",
                type:"text",
                filterKey:"BodyPartExamined",
                description:$localize `:@@body_part_examined:Body part examined`,
                placeholder:$localize `:@@body_part_examined:Body part examined`
            },{
                tag:"input",
                type:"text",
                filterKey:"InstitutionalDepartmentName",
                description:$localize `:@@institutional_department_name:Institutional Department Name`,
                placeholder:$localize `:@@institutional_department_name:Institutional Department Name`
            },{
                tag:"input",
                type:"text",
                filterKey:"InstitutionName",
                description:$localize `:@@institution_name:Institution Name`,
                placeholder:$localize `:@@institution_name:Institution Name`
            },{
                tag:"input",
                type:"text",
                filterKey:"StationName",
                description:$localize `:@@station_name:Station Name`,
                placeholder:$localize `:@@station_name:Station Name`
            },{
                tag:"input",
                type:"text",
                filterKey:"PerformingPhysicianName",
                placeholder:$localize `:@@Performing_physician_family_name:Performing physician family name`,
                description:$localize `:@@person_family_name_tooltip:Order of name components in the search field differs from the rendered person names in the list`
            },{
                tag:"input",
                type:"text",
                filterKey:"StudyInstanceUID",
                description:$localize `:@@study_instance_uid:Study Instance UID`,
                placeholder:$localize `:@@study_instance_uid:Study Instance UID`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"AccessionNumber",
                description:$localize `:@@accession_number:Accession number`,
                placeholder:$localize `:@@accession_number:Accession number`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"IssuerOfAccessionNumberSequence.LocalNamespaceEntityID",
                description:$localize `:@@issuer_of_accession_number:Issuer of accession number`,
                placeholder:$localize `:@@issuer_of_accession_number:Issuer of accession number`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"ReferringPhysicianName",
                placeholder:$localize `:@@referring_physician_family_name:Referring physician family name`,
                description:$localize `:@@person_family_name_tooltip:Order of name components in the search field differs from the rendered person names in the list`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"SendingApplicationEntityTitleOfSeries",
                description:$localize `:@@sending_application_entity_title_of_series:Sending Application Entity Title of Series`,
                placeholder:$localize `:@@sending_aet_of_series:Sending AET of Series`
            }
        ];
    }

    static STUDY_FILTER_ENTRY_SCHEMA(devices,webService):FilterSchema{
        return [
            {
                tag:"html-select",
                options:devices,
                filterKey:"device",
                description:$localize `:@@select_device:Select Device`,
                placeholder:$localize `:@@select_device:Select Device`
            },{
                tag:"html-select",
                options:webService,
                filterKey:"webService",
                description:$localize `:@@web_application_service:Web Application Service`,
                placeholder:$localize `:@@web_app_service:Web App Service`
            },
        ]
    }

    static PATIENT_FILTER_SCHEMA(aets,hidden?):FilterSchema{
        if(hidden){
            return [
                {
                    tag:"select",
                    options:[
                        new SelectDropdown("F",$localize `:@@female:Female`),
                        new SelectDropdown("M",$localize `:@@male:Male`),
                        new SelectDropdown("O",$localize `:@@other:Other`)
                    ],
                    showStar:true,
                    filterKey:"PatientSex",
                    description:$localize `:@@patients_sex:Patient's Sex`,
                    placeholder:$localize `:@@patients_sex:Patient's Sex`
                },
                {
                    tag:"checkbox",
                    filterKey:"onlyWithStudies",
                    text:$localize `:@@only_with_studies:only with studies`
                },{
                    tag:"p-calendar",
                    filterKey:"PatientBirthDate",
                    description:$localize `:@@birth_date:Birth Date`
                },
                {
                    tag: "select",
                    options: [
                        new SelectDropdown("UNVERIFIED", $localize `:@@UNVERIFIED:UNVERIFIED`),
                        new SelectDropdown("VERIFIED", $localize `:@@VERIFIED:VERIFIED`),
                        new SelectDropdown("NOT_FOUND", $localize `:@@NOT_FOUND:NOT_FOUND`),
                        new SelectDropdown("VERIFICATION_FAILED", $localize `:@@VERIFICATION_FAILED:VERIFICATION_FAILED`)
                    ],
                    showStar: true,
                    filterKey: "patientVerificationStatus",
                    description: $localize `:@@verification_status:Verification Status`,
                    placeholder: $localize `:@@verification_status:Verification Status`
                },{
                    tag:"select",
                    filterKey:"includefield",
                    options:[
                        new SelectDropdown("", $localize `:@@dicom:dicom`,$localize `:@@search_response_payload_according_dicom_ps_3.18:Search Response Payload according DICOM PS 3.18`),
                        new SelectDropdown("all", $localize `:@@all:all`, $localize `:@@all_available_attributes:all available attributes`)
                    ],
                    description:$localize `:@@include_field:Include field`,
                    placeholder:$localize `:@@include_field:Include field`,
                }, {
                    tag:"checkbox",
                    filterKey:"merged",
                    text:$localize `:@@merged_patients:Merged Patients`,
                    description:$localize `:@@merged_patients_tooltip:Indicates to return merged patients`
                }
            ]
        }
        return [
            {
                tag:"select",
                options:aets,
                showStar:true,
                filterKey:"aet",
                description:$localize `:@@AET:AET`,
                placeholder:$localize `:@@AET:AET`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"PatientName",
                placeholder:$localize `:@@patient_family_name:Patient family name`,
                description:$localize `:@@person_family_name_tooltip:Order of name components in the search field differs from the rendered person names in the list`
            },
            {
                tag:"checkbox",
                filterKey:"fuzzymatching",
                text:$localize `:@@fuzzy_matching:Fuzzy Matching`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"PatientID",
                description:$localize `:@@patient_id:Patient ID`,
                placeholder:$localize `:@@patient_id:Patient ID`
            },
            {
                tag:"input",
                type:"text",
                filterKey:"IssuerOfPatientID",
                description:$localize `:@@issuer_of_patient:Issuer of patient`,
                placeholder:$localize `:@@issuer_of_patient:Issuer of patient`
            },
            {
                tag:"input",
                type:"number",
                filterKey:"limit",
                description:$localize `:@@limit:limit`,
                placeholder:$localize `:@@limit_of_patients:Limit of patients`
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