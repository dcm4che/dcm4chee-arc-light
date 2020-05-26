import {FilterSchema, LanguageObject, SelectDropdown} from "../interfaces";
import {DicomTableSchema, DynamicPipe} from "../helpers/dicom-studies-table/dicom-studies-table.interfaces";
import {ContentDescriptionPipe} from "../pipes/content-description.pipe";
import {TableSchemaElement} from "../models/dicom-table-schema-element";
declare var DCM4CHE: any;
const sopObject = DCM4CHE.SOPClass.nameOf("all");
import * as _ from "lodash";

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
    public static get ORDERBY(): Array<{value:string,label:any,mode:('patient'|'study'|'mwl'|'diff'),title:string}>{
        return [
            {
                value: 'PatientName',
                label: $localize `:@@orderby.patient_alph:<label class="order_label">Patient</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span>`,
                mode: 'patient',
                title:$localize `:@@globalvar.query_patients:Query Patients`
            },
            {
                value: '-PatientName',
                label: $localize `:@@orderby.patient:name_desc:<label class="order_label">Patient</label><span class=\"orderbynamedesc\"></span>`,
                mode: 'patient',
                title:$localize `:@@globalvar.query_patients:Query Patients`
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
                title:$localize `:@@globalvar.query_mwl:Query MWL`
            },
            {
                value: 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby.mwl_desc:<label class="order_label">MWL</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@globalvar.query_mwl:Query MWL`
            },
            {
                value: 'PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby.mwl_alph_asc:<label class="order_label">MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@globalvar.query_mwl:Query MWL`
            },
            {
                value: '-PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby.mwl_desc_asc:<label class="order_label">MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@globalvar.query_mwl:Query MWL`
            },
            {
                value: 'PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby.mwl_alph_desc:<label class="order_label">MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@globalvar.query_mwl:Query MWL`
            },
            {
                value: '-PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby.mlw_namedsc_date_dsc:<label class="order_label">MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@globalvar.query_mwl:Query MWL`
            },
            {
                value: '',
                label: $localize `:@@orderby.diff:<label class="order_label">Diff </label><i class="material-icons">compare_arrows</i>`,
                mode: 'diff',
                title:$localize `:@@globalvar.make_diff_between_two_archives:Make diff between two archives`
            }
        ];

    }
    public static get ORDERBY_NEW(): Array<{value:string,label:any,mode:('patient'|'study'|'mwl'|'diff'),title:string}>{
        return [
            {
                value: 'PatientName',
                label: $localize `:@@orderby_new.order_a_z:<label class="order_label">Order A-Z</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span>`,
                mode: 'patient',
                title:$localize `:@@globalvar.query_patients:Query Patients`
            },
            {
                value: '-PatientName',
                label: $localize `:@@orderby_new.z_a:<label class="order_label">Z-A</label><span class=\"orderbynamedesc\"></span>`,
                mode: 'patient',
                title:$localize `:@@globalvar.query_patients:Query Patients`
            },
            {

                value: '-StudyDate,-StudyTime',
                label: $localize `:@@orderby_new.newest_first:<label class="order_label">Newest first</label><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies:Query Studies`
            },
            {
                value: 'StudyDate,StudyTime',
                label: $localize `:@@orderby_new.oldest_first:<label class="order_label">Oldest first</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies:Query Studies`
            },
            {
                value: 'PatientName,-StudyDate,-StudyTime',
                label: $localize `:@@orderby_new.a_z_new_old:<label class="order_label">A-Z, New to Old</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies:Query Studies`
            },
            {
                value: '-PatientName,-StudyDate,-StudyTime',
                label: $localize `:@@orderby_new.z_a_new_old:<label class="order_label">Z-A, New to Old</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies:Query Studies`
            },
            {
                value: 'PatientName,StudyDate,StudyTime',
                label: $localize `:@@orderby_new.a_z_old_new:<label class="order_label">A-Z, Old to New</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies:Query Studies`
            },
            {
                value: '-PatientName,StudyDate,StudyTime',
                label: $localize `:@@orderby_new.z_a_old_new:<label class="order_label">Z-A, Old to New</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'study',
                title:$localize `:@@query_studies:Query Studies`
            },
            {
                value: '-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby_new.newest_first:<label class="order_label">Newest first</label></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@globalvar.query_mwl:Query MWL`
            },
            {
                value: 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby_new.oldest_first:<label class="order_label">Oldest first</label><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@globalvar.query_mwl:Query MWL`
            },
            {
                value: 'PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby_new.a_z_new_old:<label class="order_label">A-Z, New to Old</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@globalvar.query_mwl:Query MWL`
            },
            {
                value: '-PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby_new.z_a_new_old:<label class="order_label">Z-A, New to Old</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@globalvar.query_mwl:Query MWL`
            },
            {
                value: 'PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby_new.a_z_old_new:<label class="order_label">A-Z, Old to New</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@globalvar.query_mwl:Query MWL`
            },
            {
                value: '-PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: $localize `:@@orderby_new.z_a_old_new:<label class="order_label">Z-A, Old to New</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>`,
                mode: 'mwl',
                title:$localize `:@@globalvar.query_mwl:Query MWL`
            }
        ];

    }

    public static get LANGUAGES(){
        const languages = {
            sq: {
                code: 'sq',
                name: 'Albanian',
                nativeName: 'Shqip',
                flag:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAAPCAMAAAAmuJTXAAAAk1BMVEXkHiAAAADyICLpHiHtHyHwHyLjHiAHAQH1ICIMAQERAgIXAwPbHR87CAgdBAR5EBFyDxBDCQkjBAVrDg+5GBqRExSAERLHGhxZDAxRCwvfHR8zBwfTHB6pFhguBgaLEhPMGx2uFxjXHB4oBQW+GRtlDQ5KCgqFERKfFRa0GBmaFBbQGx1gDA2WFBWkFhfDGhv7ISOYvxptAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIUEDohkVwNywAAAGFJREFUGNNjYCACiAKxGKYwI4M4IyMDIyMTkIUmI8vIzQgCehgyCIAuIw4V58eQgenCtAdiIC8DFhlWDqAMswqGDAsDF8ggRmkGZqymSQIpdiyBwM4ojyt8GHGGHCcDUQAAgI8BvQ5YgHoAAAAASUVORK5CYII='
            },
            el: {
                code: 'el',
                name: 'Greek',
                nativeName: 'Ελληνικά',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAARCAMAAAAfZPc8AAAATlBMVEUNXq////8AVaoKXK4GWa2VuNz5+/4DV6zG2ewbZ7To8PcvdbpwoNAVY7HS4fBGhMLd6PQ1ebx3pdJAgMDB1epTjMYATaemxOJdlMrd6fTIjGVKAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIVCAYi0S+wIwAAADhJREFUGNNjYAACRiEGHICADCMawCODqgbFJJwyRNjDjwbwuJobCBhZuTEBhvH4XUQI4DaNqvYAAMxlAwaS/oOXAAAAAElFTkSuQmCC'
            },
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
            fr: {
                code: 'fr',
                name: 'French',
                nativeName: 'Français',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAAkklEQVRYR2NkGGDAOMD2M4w6YDQEGA0dpoYQkxDdHFV+1hRZsROj9nVm2a9fh06zEaOW0chh6n9iFAb6qB8vzLCwJEbtc4/oE38fPbUgQu3/UQeMhsBoCIyGwGgIjIbAaAiMhsAgCAHHKWFEtFwYXG0Vf9aU2hHVJHuTWfnr56GTxDXJiLGclmpGm+WjITDgIQAAGofb8ADVqusAAAAASUVORK5CYII='
            },
            hi: {
                code: 'hi',
                name: 'Hindi',
                nativeName: 'हिन्दी',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAAUBAMAAACKWYuOAAAAMFBMVEX///8AZjP/mTMAPGi7y9dmiqWAnrQwYYTv8/YVTHSftsbP2uNAbY7f5+yPqr1QeZcEz6ptAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIUEQYIod+GbwAAADFJREFUGNNjUEICCgy04EGAAwMSYElH4jCzHGBG4jGfZEZWmo/MYXC4AKEFkYAALXgA9uIV01T/BOMAAAAASUVORK5CYII='
            },
            fi: {
                code: 'fi',
                name: 'Finnish',
                nativeName: 'suomi',
                flag: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD//gA7Q1JFQVRPUjogZ2QtanBlZyB2MS4wICh1c2luZyBJSkcgSlBFRyB2ODApLCBxdWFsaXR5ID0gODAK/9sAQwABAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEB/9sAQwEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEB/8IAEQgADwAZAwERAAIRAQMRAf/EABcAAQEBAQAAAAAAAAAAAAAAAAgABAn/xAAYAQADAQEAAAAAAAAAAAAAAAAABQYCB//aAAwDAQACEAMQAAABUvS4o5P1Dbj6ODZjRYpUnQOFqoP/xAAaEAACAgMAAAAAAAAAAAAAAAAGBwAFCBcY/9oACAEBAAEFAjXIZc3ogh3AGr4U6cVs6cVsP1Kt6wIxxXwYWBmllZNLKyf/xAAoEQABAwMCBAcBAAAAAAAAAAADAQIEAAUREiEGFRZSIkFVk6LS4oH/2gAIAQMBAT8BuPFNpkwJkcbzKQ0YwmZFhNT2K1Mrq2TK1wzfYFrgmBKcRCPlvKmgepNChCxN8pvljq6ws3ef2f1XWFm7z+z+qulktQbdOKOEFhBxTvY5NWWuaNyoqeLyWuErZAm285ZUYZiNmPGjn5yjEBHcjdlTbLnL/a5BZvTwfP7VyCzeng+f2r//xAApEQABAwMDAQgDAAAAAAAAAAACAQMEAAUGERIh0hMWF1FTVZKUIjFx/9oACAECAQE/AbRgl/hXW3S3wiozGmR33VGShFsbdEy0HbyuicJWa4rdr7c48qALCtNQQjl2ryNl2gyJDi6IqLxtcHnz18q8O8k9OH9oemvDvJPTh/aHpqx5Pf5F5tbD90lOMvT4rbrZEO0wN4RIV/H9Ki6LWf3y7Wy7RWYE9+K0dubdIGlRBVxZMoFNdRXlRAU/gpXe3JPeJfyHprvbknvEv5D01//EACgQAAEEAQIEBgMAAAAAAAAAAAMBAgQFBgASERMh0zE0NkGTlRQiUf/aAAgBAQAGPwLJqaBIuFm2lHZQIiFqiDGsiVFIESPIpFRjd7k3O9k66s6jIi2LJkrIJFiJIkB8of4xK6sjNVXte3g/mxS8WfzavvrzN39OXu68zd/Tl7ustsYGIVMWbCx62lRJIxl5gJAYZSCKziVU3Me1HJ01bWGR49X20wGTSoYpEpj3EZGZV1BmhTa9v6IU5X+Hi9deiab4y93Xomm+Mvd1/8QAGhABAQADAQEAAAAAAAAAAAAAAREAECExQf/aAAgBAQABPyFYjpONQNaJaGYnd/wP2JOFDX50MGCyMEV3AcqinRxJLSprTxIU+/oGt+//2gAMAwEAAgADAAAAEF7Xdv/EABoRAQEBAQEBAQAAAAAAAAAAAAERIQAx8RD/2gAIAQMBAT8QEFI3N9GBWMNnHG09JRxaxPAbv666FsN9lMigJRKecfvNpAEUbgtXfI5+Rz//xAAaEQEBAAMBAQAAAAAAAAAAAAABEQAhMRBB/9oACAECAQE/ECCxijiQbcErDXcQT2igZU3y9oT68ixUyoQjLhVaCMdOBeJbJgi1tnB2+a9f/8QAGBABAAMBAAAAAAAAAAAAAAAAAQAQESH/2gAIAQEAAT8QLcLMfEm2mZwKwcp8p9VhrjCWECBeXab4hoqPFpAYNGATiLwrWWB8/wD/2Q=='
            },
            ar: {
                code: 'ar',
                name: 'Arabic',
                nativeName: 'اللغة العربية',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAAQCAYAAADj5tSrAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIVCAsV3DxrYQAAAgZJREFUOMvtk99LU3EYxj/f/Ti6NnQ/ajY1cxiLFVRERYwwKNpF3UR3QVAUlEVFdB9YFEEXQTeCUXTTfxARjDJXFP2E1NApSRfWZLNotnnOzs7O99tN3qTJsPKq5/J54X143+d5xM6ePYp/DAdLgCURcc1HNtY34Ha60SsGpm3i9/gpW2VaGpsRgG4ZzFR0LNvCsHQiDRFyxTxSSaqyOmefs31fR/evZFugldOJLg5s2M/A5Hu2rtrM4S2HqHNpSBTdyQsMTg5xfPtRHo+nuXvwDusjcbx1PkbyGQRi4UuUUpxKdBENruZL6Su6OcNwLoMtbarSJt4U52rfNYLL/HwvF0nGdtP74hZrwzFW+ppq9ETAw7E++j88QaG4svcSPs1LvbuesG8FydgubCWJBqJoLjefCllC3hDrwnGWe0OgahARCEbyGVr9LRTNIhdTlznfeRapFDOWzmB2iKAnwKuJNxTLJTa1bMTpcPL04zMMy6jNeIFgTaiDb3qBB5kUQW+Q1NgjXk+8JTud5d3nAfKlKY5tO0LP8152RBP0j6c5mTjBzZe3EULMfc7/Mv5xGWdxrvMMPreXil1BKomtJFJJlFIoJdFcGgVjmnxpinvD9xcncj19A4/b87Nagtl8zpqoOTWaGyO0B9oWf4lDODCr5m/nZtVkNF8kkxudN1V/1ZOFBAB+AF1NxkfPmjTZAAAAAElFTkSuQmCC'
            },
            es: {
                code: 'es',
                name: 'Spanish',
                nativeName: 'Español',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAALCAYAAAB24g05AAABgElEQVQoU4WSvUtcQRTFf/etrgbUSrQyomxnp+A/Ym/QYosQSCFEdCurFFbaWNiI+heIYGNjoYEUgh+lbEISEfxg12VX1zcz78qMb3WziA4c7r0M98yde44oKE0naS4AX78FCQSFQmjTJAEP51DrwFnUWrAWNSbkzbG6tUVKMI/+Ow+NodmY5xgaPGIfYzSOQy0DA1R2dhB1qMoc8Bdw3JUT7suW7r46mQ6LqkE1TpHmiUHkI7eVXcQ/KPIN+EO15tDFiPgKotlxsr3bZNtfIVBPMEilstcgmEH1N/WDSeqb6+iXBbKXZ8S5Zbr6q/+/HqYxSDTEbXn/iQC+BgJX+87FaoGuH/BhZQlzl6ej/6LpG41pDFE0TKn0E/H7EfmM6i/2Tz4xYjZ4KCrHvROMjq3R03nTMoEBLCLDXF8fIg+gmXyepFiE542/bLux9SclYtQasA7J5bg5OkLuQdumpsNFkM96D6R5q/beB6nUXu6r01Ok5v3T4jZvqvcc2DDsI1q3WFQmn/TUAAAAAElFTkSuQmCC'
            },
            et: {
                code: 'et',
                name: 'Estonian',
                nativeName: 'eesti',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAAQBAMAAAARyMmYAAAAD1BMVEUAcs4ATIoAAACqqqr///8uA1BFAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIVCA809Dm+OwAAABdJREFUCNdjYKAKUEICClTiuSABB3J5AN6KG4HII55eAAAAAElFTkSuQmCC'
            },
            bs: {
                code: 'bs',
                name: 'Bosnian',
                nativeName: 'bosanski jezik',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAANCAMAAABrcDXcAAACuFBMVEUAI5Wwu97///+vut53ck/+ywAMLZr29/v3+PwNLppWbblVbLiptduotNsIKpjy9Prz9foJK5lQaLZPZ7ajsNmir9gFJ5fu8Pjv8fgGKJdKY7RJYrOdqtacqtYDJpbq7fbs7/cEJpdDXbFCXLCWpNOVpNMCJZbm6fXn6vU9WK48V66Qn9GPntABJJXh5fPj5/M2Uqs1UauJmc6ImM7b4PDd4vE/Wa8+WK8wTKkvTKmSodKRoNGDlMuCk8vi5vPk6PTW3O7V2+45VK04U6wnRaUpRqaMnM+Lm898jsl7jcjf4/LO1ev19vtme78yTqoxTakhP6MuS6goRqbP1usjQaSFlsyElcx2icYKLJn+/v91iMbX3O7Z3u/J0OlOZrXI0OgsSacrSKccO6G/yOQbOqAXN5+4wuEePaF/kcp+kMlwhMT7/P2Akcpvg8P6+/3S2OzR1+zDy+bc4fDCyuYlQ6T8/P6tuN0RMpwOL5ulsdr9/f4ZOZ94i8d3isZpfsFedLz09vpofcDL0unK0em8xePU2u0tSqi7xOMgP6ITM52Zp9UHKZgUNJ1yhcRxhMRjeL7r7vdNZbVGX7JieL7FzefEzOa2wOG+x+S3weG1v+AYOJ8aOaAPMJv5+v1rf8FqfsFccrvg5PI7Vq5bcru9xuSst9yksNmuud0WNp4LLJpler9keb8QMZxMZbVFX7Jdc7yxvN+hrtj4+fw0UKtYb7pXbrmrt9xIYbOqttybqdXN1OomRKXHz+hSardRabdBW7BLZLREXrGUo9Pw8vnl6fSeq9c6Va3a3/COntCYptSXpdTM0+rGzueHl83o6/UVNZ60vuCBkss3UqwzT6qKms56jMje4/HY3e8qR6Z9j8nQ1uskQqQdPKEfPqLp7PZUa7izvd/T2e1Ta7fx8/myvd+6w+Jacbp/d0qdQNArAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIVCBEe+8NIMgAAADpJREFUGNN1jkEKACAMw8LA/39Z5zwUbHMNDYVhCQhVBHMc2RCMBn+DNxLEkcxbucncIJBMB8Ok3182b0sBpfJQQqwAAAAASUVORK5CYII='
            },
            tr: {
                code: 'tr',
                name: 'Turkish',
                nativeName: 'Türkçe',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAARBAMAAADalBo9AAAAIVBMVEXLICf//////PzJFBvNJizVSlDda2/kjI/54+Tyxcfsq67aSJWlAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIVCBMQLk0HtwAAADhJREFUCNdjYCAJOApKITiMgqJIUo2CyAoFJYGEAJwHZAmKQDksIB6joAA2OTR9qGai2ofqFuIAAK8WA5IgO9OVAAAAAElFTkSuQmCC'
            },
            ug: {
                code: 'ug',
                name: 'Uyghur',
                nativeName: 'ئۇيغۇرچە‎',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAARCAYAAAAougcOAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIVCBUEYs10TAAAAW1JREFUOMvtlD0vQ2EUx3/PvbftpRqlNFokIlGJSCQSg4GkLCQGi9UXkEiYxWIxSGxG4jMwSCzEQI0lsTRpS7ykFOm76n0eA4s0RL3F4Kzn5Pxyzvmfv5hYPVX8cGj8QvwKxHgvWZIKpUDXBJr4ZoglFV1NJmPdLvy1BseXD2wcpbjOWAjxDRBLKqYGPAwFnESuiyxu3xBNFhkOODH0R65Spa9D+tuqGQo4CV8UmNtMYNoENQ6NUDyPAlDgMjXSDxIUH5rs1eEfLcVIlwuAtYM7TNvrDuKlZjbYQKfXQU+zWbm6pAKvSwfg7L58LVLB/KiXbp+DmaCHw3i+cogmIJF+bt7iLteEJmBhK0H4vMB66J5gh7NyiE0XbJ1kAJjsc2NJVbYuQxMs7yQ5iOXYj+U+94z70Ry7kSy9rVUsjfuw688/4q81aG+0IwRki7IidYm3vKu1zs70YD1+t0Eolmdl75aS/JzNiX+D/HOQJ5oseRe/oPsNAAAAAElFTkSuQmCC'
            },
            ur: {
                code: 'ur',
                name: 'Urdu',
                nativeName: 'اردو',
                flag: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAARCAIAAACn2JBZAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH5AIVCBYtC1K/4wAAAb1JREFUOMtj/P//PwMS+Pn7l1914q7TBxkZGRlIBEwM1APEmvX//7//DP+pYNb/P3+E+YU42TgoNev/379p/nG+lm7fv35mYGBgZGBkY2FlJMOs/79+ZgYklIRnLFg7h5GVjYGBgY2VNdYteGJuMz83Lwlm/f/3T0xUsierdtrGRQwsrBBBTnYOKx0TCy1DDjZ2tPBjweeqv39cTe252Dlfvn/NwAS19f3nj82LJkoIiX769oWRBLP+/xcXEGFgYNBRVGP49xcixsjI+PDFkwcvHpMYXoyMrz68+fn7V4xrCDMXz////6BWMPyHk0Sbxcyy99yRz9++yIhK9GXVs7Gy/f/9+/+/v////v3/84ekkBha3mBuaGhACaJ/f5fv23j32UNGRkZGRsbP799+/fPbx8LZRF1PR1Hj99+/f//9ExcWi/UME+EXvnzvOrJxLPjTBCMH54xNi37/+T2ruDPI1tPL3OnHr5/MTEyzty6bv2MVA6q7WIjIPf9nb1m65fjuePcwLQXVF+9erdy36eyNi4zMzKTEIyIOGJ+/edmxoJfh318GRiYGVjZGZmZMZSxE5m1GJiYGdg76lTkAwZOktr73K2oAAAAASUVORK5CYII='
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
                break;
            case 'reschedule':
                return $localize `:@@reschedule:reschedule`;
                break;
            case 'delete':
                return $localize `:@@delete:delete`;
                break;
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
            dcmKeycloakServer:{
                key:'dcmKeycloakServerID',
                labelKey:'{dcmKeycloakServerID}',
                msg:$localize `:@@dynamic_formatter.keycloak_server:Create first an Keycloak Server!`,
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
                {
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
                        new SelectDropdown("CANCELLED", $localize `:@@CANCELLED:CANCELLED`),
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
                    description:$localize `:@@scheduled_performing_physicians_name:Scheduled Performing Physician's Name`,
                    placeholder:$localize `:@@sp_physicians_name:SP Physician's Name`
                }
            ]
        }else{
            return [
                {
                    tag:"input",
                    type:"text",
                    filterKey:"PatientName",
                    description:$localize `:@@patient_name:Patient name`,
                    placeholder:$localize `:@@patient_name:Patient name`
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
                description:$localize `:@@scheduled_station_name_code_sequence_00404026:Scheduled Station Class Code Sequence (0040,4026)`,
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
                description: $localize `:@@scheduled_station_name_code_sequence_00404027:Scheduled Station Geographic Location Code Sequence (0040,4027)`,
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
                    description:$localize `:@@patients_name:Patient's Name`,
                    placeholder:$localize `:@@patients_name:Patient's Name`
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
                },{
                    tag:"range-picker",
                    type:"text",
                    filterKey:"00100030",
                    onlyDate:true,
                    description:$localize `:@@patients_birth_date:Patient's Birth Date`
                },{
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

    static STUDY_FILTER_SCHEMA(aets,hidden?):FilterSchema{
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
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"ResponsiblePerson",
                    description:$localize `:@@responsible_person:Responsible Person`,
                    placeholder:$localize `:@@responsible_person:Responsible Person`
                },{
                    tag:"p-calendar",
                    type:"text",
                    filterKey:"PatientBirthDate",
                    description:$localize `:@@patients_birth_date:Patient's Birth Date`,
                    placeholder:$localize `:@@birth_date:Birth Date`
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
                description:$localize `:@@patient_name:Patient name`,
                placeholder:$localize `:@@patient_name:Patient name`
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
                description:$localize `:@@referring_physician_name:Referring physician name`,
                placeholder:$localize `:@@referring_physician_name:Referring physician name`
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
                description:$localize `:@@patient_name:Patient name`,
                placeholder:$localize `:@@patient_name:Patient name`
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
                description:$localize `:@@referring_physician_name:Referring physician name`,
                placeholder:$localize `:@@referring_physician_name:Referring physician name`
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
                filterKey:"diffQueue",
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
                filterKey:"taskPK",
                description:$localize `:@@pk_of_task:Pk of task`,
                placeholder:$localize `:@@pk_of_task:Pk of task`
            },{
                tag:"input",
                type:"text",
                filterKey:"batchID",
                description:$localize `:@@batch_id:Batch ID`,
                placeholder:$localize `:@@batch_id:Batch ID`
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
                description:$localize `:@@patient_name:Patient name`,
                placeholder:$localize `:@@patient_name:Patient name`
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
