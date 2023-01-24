import { Component, OnInit } from '@angular/core';
import { MatDialogRef } from "@angular/material/dialog";
import * as _ from 'lodash-es';
import {AppService} from "../../../app.service";
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {StudiesService} from "../../../studies/studies.service";
import {UploadDicomService} from "../upload-dicom/upload-dicom.service";
import {KeycloakService} from "../../../helpers/keycloak-service/keycloak.service";
import {j4care} from "../../../helpers/j4care.service";
import {ComparewithiodPipe} from "../../../pipes/comparewithiod.pipe";
import {StudyDicom} from "../../../models/study-dicom";
import {StudyService} from "../../../study/study/study.service";
import {DcmWebApp} from "../../../models/dcm-web-app";
import {Observable, Subscriber} from "rxjs";
import {UploadFilesService} from "./upload-files.service";
// import {StudyWebService} from "../../../study/study/study-web-service.model";

// declare var uuidv4: any;

// import * as uuidv4 from  'uuid/v4';


@Component({
  selector: 'app-upload-files',
    templateUrl: './upload-files.component.html',
    styles:[`        
        .upload label{
            float: left;
            width: 100%;
        }
        .edit_attribute_button{
            margin-top: 15px;
        }
    `]
})
export class UploadFilesComponent implements OnInit {


    private _aes;
    private _selectedAe;
    private _dicomObject;
    private _fromExternalWebApp;
    private _preselectedWebApp:DcmWebApp;
    mode;
    file;
    fileList: File[];
    xmlHttpRequest;
    percentComplete: any;
    selectedSopClass;
    modality;
    description;
    showFileList = false;
    isImage = false;
    webApps;
    // studyWebService:StudyWebService;
    selectedWebApp;
    seriesNumber = 0;
    // instanceNumber = 1;
    moreAttributes = false;
    tempAttributes:any = [];
    dropdown;
    iod;
    tempIods;
    iodFileNameFromMode = {
        patient:"study",
        study:"series",
        mwl:"mwl"
    };
    imageType = [
        {
            title: $localize `:@@screenshots:Screenshots`,
            description:$localize `:@@upload-files.secondary_capture_image_storage:Secondary Capture Image Storage`,
            value:"1.2.840.10008.5.1.4.1.1.7",
            modality:"OT"
        },
        {
            title: $localize `:@@photographs:Photographs`,
            description:$localize `:@@upload-files.vl_photographic_image_storage:VL Photographic Image Storage`,
            value:"1.2.840.10008.5.1.4.1.1.77.1.4",
            modality:"XC"
        },
        {
            title: $localize `:@@animated_gif:Animated gif`,
            description:$localize `:@@upload-files.gif_with_multi_frames_in_it:Gif with multi frames in it`,
            value:"1.2.840.10008.5.1.4.1.1.77.1.4.1",
            modality:"XC"
        }
    ];
    sourceOfPreviousValues = "";
    sourceOfPreviousValuesBlock = false;
    isDicomCheckbox = false;
    isDicomModel;
    neededClassMissing = false;
    constructor(
        public dialogRef: MatDialogRef<UploadFilesComponent>,
        public mainservice:AppService,
        public $http:J4careHttpService,
        private studyService:StudyService,
        private uploadDicomService:UploadDicomService,
        private _keycloakService: KeycloakService,
        private service:UploadFilesService
    ) {
    }

    ngOnInit() {
        this.percentComplete = {};
        this.tempAttributes = undefined;
        this.tempIods = undefined;
        this.iod = undefined;
        this.selectedSopClass = this.imageType[0];
        if(!this._fromExternalWebApp){
            this.getWebApps();
        }else{
            this.selectedWebApp = this._fromExternalWebApp;
        }
    }
    fileChange(event){
        this.fileList = event.target.files;
        let file0 = this.fileList[0];
        this.service.fileTypeOrExt(file0).subscribe(( fileTypeOrExt:string )=>{
            if(fileTypeOrExt === "NO_TYPE_FOUND"){
                this.isDicomCheckbox = true;
            }else{
                this.setDicomObject(fileTypeOrExt,file0);
            }
        });
    }
    onDicomCheck(e){
        if(e != "application/dicom"){
            this.isDicomCheckbox = false;
            this.isDicomModel = "";
        }
        this.setDicomObject(this.isDicomModel,this.fileList[0]);
    }

    private setDicomObject(fileTypeOrExt, file0){
        if(fileTypeOrExt === "application/dicom" && this.selectedWebApp && this.selectedWebApp.dcmWebServiceClass && this.selectedWebApp.dcmWebServiceClass.indexOf("DCM4CHEE_ARC_AET") === -1){
            this.neededClassMissing = true;
            this.mainservice.showError($localize `:@@selected_webapp_doesent_have_the_webapp_class:The selected WebApp doesn't have the webapp class ${'DCM4CHEE_ARC_AET'}:@@webAppServiceClass:`);
        }else{
            if(file0 && (file0.type === "image/jpeg" || file0.type === "image/png" || file0.type === "image/gif" || file0.type === "image/tiff")){
                this.isImage = true;
            }
            if(fileTypeOrExt === "application/dicom"){
                this.sourceOfPreviousValuesBlock = true;
            }

            this.studyService.getIodFromContext(fileTypeOrExt, this.mode).subscribe(iods=>{
                console.log("iods",iods);
                if(!this._dicomObject){
                    this._dicomObject = {
                        attrs:[]
                    }
                }
                this._dicomObject.attrs["0008103E"] = {
                    "vr": "LO",
                    "Value": [""]
                };
                this._dicomObject.attrs["00080018"] = {
                    "vr": "UI",
                    "Value": [
                        j4care.generateOIDFromUUID()
                    ]
                };
                if (!_.hasIn(this._dicomObject.attrs, "00080020.Value[0]")) { // Study Date
                    this._dicomObject.attrs["00080020"] = {
                        "vr": "DA",
                        "Value": [""]
                    };
                }
                if (!_.hasIn(this._dicomObject.attrs, "00080030.Value[0]")) { // Study Time
                    this._dicomObject.attrs["00080030"] = {
                        "vr": "TM",
                        "Value": [""]
                    };
                }
                if (!_.hasIn(this._dicomObject.attrs, "00080090.Value[0]")) { // Referring Physician's Name
                    this._dicomObject.attrs["00080090"] = {
                        "vr": "PN",
                        "Value": [""]
                    };
                }
                if (!_.hasIn(this._dicomObject.attrs, "00200010.Value[0]")) { // Study ID
                    this._dicomObject.attrs["00200010"] = {
                        "vr": "SH",
                        "Value": [""]
                    };
                }
                if (!_.hasIn(this._dicomObject.attrs, "00080050.Value[0]")) { // Accession Number
                    this._dicomObject.attrs["00080050"] = {
                        "vr": "SH",
                        "Value": [""]
                    };
                }
                if (fileTypeOrExt === "application/pdf" || fileTypeOrExt === "pdf") {
                    this.supplementEncapsulatedDocumentAttrs();
                    this.supplementEncapsulatedPDFAttrs();
                }
                else if (fileTypeOrExt === "text/xml" || fileTypeOrExt === "xml") {
                    this.supplementEncapsulatedDocumentAttrs();
                    this.supplementEncapsulatedCDAAttrs();
                }
                else if (fileTypeOrExt === "mtl"
                    || fileTypeOrExt === "model/mtl") {
                    this.supplementEncapsulated3DAttrs();
                    this.supplementEncapsulatedMTLAttrs();
                }
                else if (fileTypeOrExt === "obj"
                    || fileTypeOrExt === "application/x-tgif"
                    || fileTypeOrExt === "model/obj") {
                    this.supplementEncapsulated3DAttrs();
                    this.supplementEncapsulatedOBJAttrs();
                }
                else if (fileTypeOrExt === "model/stl"
                    || fileTypeOrExt === "model/x.stl-binary"
                    || fileTypeOrExt === "application/sla"
                    || fileTypeOrExt === "stl") {
                    this.supplementEncapsulated3DAttrs();
                    this.supplementEncapsulatedSTLAttrs(file0.type);
                }
                else if (fileTypeOrExt === "genozip"
                    || fileTypeOrExt === "application/vnd.genozip")
                    this.supplementEncapsulatedGENOZIPAttrs();
                else {
                    if (file0.type.indexOf("video") > -1) {
                        this._dicomObject.attrs["00080016"] = {
                            "vr": "UI",
                            "Value": [
                                "1.2.840.10008.5.1.4.1.1.77.1.4.1"
                            ]
                        }
                    } else {
                        this._dicomObject.attrs["00080016"] = {
                            "vr": "UI",
                            "Value": [
                                this.selectedSopClass.value
                            ]
                        }
                    }
                    this._dicomObject.attrs["7FE00010"] = {
                        "vr": "OB",
                        "BulkDataURI": "file/" + file0.name
                    }
                }
                if (file0.type === "image/jpeg" || file0.type === "image/png" || file0.type === "image/gif"|| file0.type === "image/tiff") {
                    this._dicomObject.attrs["00080008"] = {
                        "vr": "CS",
                        "Value": [
                            "ORIGINAL",
                            "PRIMARY"
                        ]
                    };
                    if (this.selectedSopClass.value === '1.2.840.10008.5.1.4.1.1.7') {
                        this._dicomObject.attrs["00080064"] = {
                            "vr": "CS",
                            "Value": [
                                "WSD"
                            ]
                        };
                        this._dicomObject.attrs["00200020"] = {
                            "vr": "CS"
                        };
                    }
                }
                this.tempIods = iods;
                this.tempAttributes = _.cloneDeep(this._dicomObject);
                this.tempAttributes.attrs = _.pickBy(this._dicomObject.attrs, (o, i) => {
                    return (i.toString().indexOf("777") === -1);
                });
            })
        }

    }
    private supplementEncapsulatedDocumentAttrs() {
        this._dicomObject.attrs["00420011"] = {
            "vr": "OB",
            "BulkDataURI": "file/" + this.fileList[0].name
        };
        this._dicomObject.attrs["00280301"] = {
            "vr": "CS",
            "Value": [
                "YES"
            ]
        };
        this._dicomObject.attrs["00420010"] = {
            "vr": "ST",
            "Value": [
                ""
            ]
        };
        this._dicomObject.attrs["00080070"] = {
            "vr": "LO",
            "Value": [
                ""
            ]
        };
    }

    private supplementEncapsulatedPDFAttrs() {
        this._dicomObject.attrs["00080016"] = {
            "vr": "UI",
            "Value": [
                "1.2.840.10008.5.1.4.1.1.104.1"
            ]
        };
        this._dicomObject.attrs["00420012"] = {
            "vr": "LO",
            "Value": [
                "application/pdf"
            ]
        };
        this._dicomObject.attrs["00080064"] = {
            "vr": "CS",
            "Value": [
                "SD"
            ]
        };
    }

    private supplementEncapsulatedCDAAttrs() {
        this._dicomObject.attrs["00080016"] = {
            "vr": "UI",
            "Value": [
                "1.2.840.10008.5.1.4.1.1.104.2"
            ]
        };
        this._dicomObject.attrs["00420012"] = {
            "vr": "LO",
            "Value": [
                "text/XML"
            ]
        };
        this._dicomObject.attrs["00080064"] = {
            "vr": "CS",
            "Value": [
                "WSD"
            ]
        };
    }

    private supplementEncapsulated3DAttrs() {
        this._dicomObject.attrs["00420011"] = {
            "vr": "OB",
            "BulkDataURI": "file/" + this.fileList[0].name
        };
        this._dicomObject.attrs["00280301"] = {
            "vr": "CS",
            "Value": [
                "YES"
            ]
        };
        this._dicomObject.attrs["00420010"] = {
            "vr": "ST",
            "Value": [
                ""
            ]
        };
        this._dicomObject.attrs["00201040"] = {
            "vr": "LO",
            "Value": [
                ""
            ]
        };
        this._dicomObject.attrs["00080070"] = {
            "vr": "LO",
            "Value": [
                ""
            ]
        };
        let item = {
            attrs:[]
        }
        item.attrs["00080100"] = {
            "vr": "SH",
            "Value": [
                "mm"
            ]
        };
        item.attrs["00080102"] = {
            "vr": "SH",
            "Value": [
                "UCUM"
            ]
        };
        item.attrs["00080104"] = {
            "vr": "LO",
            "Value": [
                "mm"
            ]
        };
        // this._dicomObject.attrs["004008EA"] = {
        //     "vr": "SQ",
        //     "Value": [
        //         item.attrs
        //     ]
        // };
    }

    private supplementEncapsulatedMTLAttrs() {
        this._dicomObject.attrs["00080016"] = {
            "vr": "UI",
            "Value": [
                "1.2.840.10008.5.1.4.1.1.104.5"
            ]
        };
        this._dicomObject.attrs["00420012"] = {
            "vr": "LO",
            "Value": [
                "model/mtl"
            ]
        };
    }

    private supplementEncapsulatedOBJAttrs() {
        this._dicomObject.attrs["00080016"] = {
            "vr": "UI",
            "Value": [
                "1.2.840.10008.5.1.4.1.1.104.4"
            ]
        };
        this._dicomObject.attrs["00420012"] = {
            "vr": "LO",
            "Value": [
                "model/obj"
            ]
        };
    }

    private supplementEncapsulatedSTLAttrs(fileType: string) {
        this._dicomObject.attrs["00080016"] = {
            "vr": "UI",
            "Value": [
                "1.2.840.10008.5.1.4.1.1.104.3"
            ]
        };
        this._dicomObject.attrs["00420012"] = {
            "vr": "LO",
            "Value": [
                fileType
            ]
        };
    }

    private supplementEncapsulatedGENOZIPAttrs() {
        this._dicomObject.attrs["00420011"] = {
            "vr": "OB",
            "BulkDataURI": "file/" + this.fileList[0].name
        };
        this._dicomObject.attrs["00080016"] = {
            "vr": "UI",
            "Value": [
                "1.2.40.0.13.1.5.1.4.1.1.104.1"
            ]
        }
        this._dicomObject.attrs["00420012"] = {
            "vr": "LO",
            "Value": [
                "application/vnd.genozip"
            ]
        };
        this._dicomObject.attrs["00080070"] = {
            "vr": "LO",
            "Value": [
                ""
            ]
        };
    }

    showMoreAttributes(){
        console.log("this.dicomObject",this.dicomObject);
        if(!this._dicomObject){
            this._dicomObject = {
                attrs:[]
            }
        }
/*        if(this.mode === "study"){
            if(this.seriesNumber && this.seriesNumber != 0){
                this._dicomObject.attrs['00200011'] = {
                    "vr": "IS",
                    "Value": [
                        this.seriesNumber || 0
                    ]
                };
            }
            if(this.description && this.description != ""){
                this._dicomObject.attrs['0008103E'] = {
                    "vr": "LO",
                    "Value": [
                        this.description
                    ]
                };
            }
        }*/

        // this.studyService.getIod(this.iodFileNameFromMode[this.mode]).subscribe((iod) => {
        //     this._dicomObject.attrs = new ComparewithiodPipe().transform(this._dicomObject.attrs, this.tempIods);
            this.tempAttributes.attrs = new ComparewithiodPipe().transform(this.tempAttributes.attrs, this.tempIods);
            this.studyService.initEmptyValue(this.tempAttributes.attrs);
            this.iod = this.studyService.replaceKeyInJson(this.tempIods, 'items', 'Value');
            // console.log("iod",iod);
            console.log("dicomOjbect",this.dicomObject);
            this.dropdown = this.studyService.getArrayFromIod(this.tempIods);
            this.moreAttributes = !this.moreAttributes;
        // });
    }
    onStudyChange(e:StudyDicom){
        console.log("e",e);
        console.log("this._dicomObject.attrs",this._dicomObject.attrs);
        // this._dicomObject.attrs = e.attrs;
    }
    getToken(){
        if(this.selectedWebApp && _.hasIn(this.selectedWebApp, "dcmKeycloakClientID")){
            return this.$http.getRealm(this.selectedWebApp);
        }else{
            return this._keycloakService.getToken();
        }
    }



/*    private fileTypeFromExt(fileTypeOrExt:string) {
        switch (fileTypeOrExt) {
            case "mtl":
                return "model/mtl";
            case "stl":
                return "model/stl";
            case "obj":
                return "model/obj";
            case "genozip":
                return "application/vnd.genozip";
            default:
                return fileTypeOrExt;
        }
    }*/

    upload() {
        if (!this.neededClassMissing){
            let token;
            this.showFileList = true;
            let seriesInstanceUID;
            this.getToken().subscribe((response) => {
                if (!this.mainservice.global.notSecure) {
                    token = response.token;
                }
                if (!this.seriesNumber && this.seriesNumber != 0) {
                    this.seriesNumber = 0;
                }
                if (this.fileList) {
                    seriesInstanceUID = j4care.generateOIDFromUUID();
                    _.forEach(this.fileList, (file, i) => {
                        this.service.fileTypeOrExt(file).subscribe(fileTypeOrExt => {
                            if (fileTypeOrExt === "NO_TYPE_FOUND") {
                                if (this.isDicomModel) {
                                    this.triggerUpload(file, i, token, seriesInstanceUID, this.isDicomModel);
                                }
                            } else {
                                this.triggerUpload(file, i, token, seriesInstanceUID, fileTypeOrExt);
                            }
                        })

                    });
                }
            });
        }
    }
    triggerUpload(file,fileIndex, token, seriesInstanceUID, fileTypeOrExt){
        let $this = this;
        let descriptionPart;
        let boundary = Math.random().toString().substr(2);
        switch (fileTypeOrExt) {
            case "image/jpeg":
                $this.modality = $this.selectedSopClass.modality;
                descriptionPart = "Image";
                break;
            case "image/png":
                $this.modality = $this.selectedSopClass.modality;
                descriptionPart = "Image";
                break;
            case "image/tiff":
                $this.modality = $this.selectedSopClass.modality;
                descriptionPart = "Image";
                break;
            case "image/gif":
                $this.modality = $this.selectedSopClass.modality;
                descriptionPart = "Gif";
                break;
            case "video/mpeg":
                descriptionPart = "Video";
                $this.modality = "XC";
                break;
            case "video/mp4":
                descriptionPart = "Video";
                $this.modality = "XC";
                break;
            case "video/quicktime":
                descriptionPart = "Video";
                $this.modality = "XC";
                break;
            case "application/pdf":
                descriptionPart = "PDF";
                $this.modality = "DOC";
                break;
            case "text/xml":
                descriptionPart = "CDA";
                $this.modality = "SR";
                break;
            case "model/mtl":
            case "mtl":
                descriptionPart = "MTL";
                $this.modality = "M3D";
                break;
            case "model/obj":
            case "application/x-tgif":
            case "obj":
                descriptionPart = "OBJ";
                $this.modality = "M3D";
                break;
            case "stl":
            case "model/stl":
            case "model/x.stl-binary":
            case "application/sla":
                descriptionPart = "STL";
                $this.modality = "M3D";
                break;
            case "genozip":
            case "application/vnd.genozip":
                descriptionPart = "GENOZIP";
                $this.modality = "DNA";
                break;
        }

        let xmlHttpRequest = new XMLHttpRequest();
        let url = this.studyService.getDicomURL("study",this.selectedWebApp);
        console.log("url",url);
        //TODO check if the url is corerct for dicom  // POST /dcm4chee-arc/aets/{aet}/rs/study


        if (url) {

            this.percentComplete[file.name] = {};
            $this.percentComplete[file.name]['showTicker'] = false;
            $this.percentComplete[file.name]['showLoader'] = true;


            let dashes = '--';
            let crlf = '\r\n';
            if(j4care.is(this.tempAttributes,"attrs")){
                Object.keys(this.tempAttributes.attrs).forEach(attr=>{
                    this._dicomObject.attrs[attr] = this.tempAttributes.attrs[attr];
                });
            }
            let studyObject = _.pickBy(this._dicomObject.attrs, (o, i) => {
                return (i.toString().indexOf("777") === -1);
            });
            if(fileTypeOrExt === "application/dicom"){
                let queryParameters = {
                    irwf:"UNSCHEDULED",
                    sourceOfPreviousValues:this.sourceOfPreviousValues,
                    "00100020":_.get(studyObject,"00100020.Value[0]")
                }
                if(j4care.hasSet(studyObject, "00100021.Value[0]")){
                    queryParameters["00100021"] = _.get(studyObject, "00100021.Value[0]");
                }
                if(j4care.hasSet(studyObject, "00100024.Value[0]") &&  j4care.hasSet(studyObject, '["00100024"].Value[0]["00400032"].Value[0]')){
                    queryParameters["00100024.00400032"] = _.get(studyObject, '["00100024"].Value[0]["00400032"].Value[0]');
                }
                if(j4care.hasSet(studyObject, "00100024.Value[0]") &&  j4care.hasSet(studyObject, '["00100024"].Value[0]["00400033"].Value[0]')){
                    queryParameters["00100024.00400033"] = _.get(studyObject, '["00100024"].Value[0]["00400033"].Value[0]');
                }
                if(queryParameters && Object.keys(queryParameters).length > 0){
                    url = url + j4care.objToUrlParams(queryParameters,true);
                }
            }
            xmlHttpRequest.open('POST', url, true);
            if(!_.hasIn(studyObject, "0008103E.Value[0]") || _.get(studyObject, "0008103E.Value[0]") === ""){
                studyObject["0008103E"] = {
                    "vr": "LO",
                    "Value": [
                        $localize `:@@upload-files.imported_:Imported ` + descriptionPart
                    ]
                };
            }
            studyObject["00200013"] = { //"00200013":$localize `:@@instance_number:Instance Number`
                "vr": "IS",
                "Value": [
                    fileIndex + 1
                ]
            };
            if(this.mode === "series" && _.hasIn(studyObject, "00201209.Value[0]")){
                studyObject["00200011"] = studyObject["00200011"] || { // "00200011":$localize `:@@upload-files.series_number:Series Number`
                    "vr": "IS",
                    "Value": [
                        _.get(studyObject, "00201209.Value[0]")*1 + 1
                    ]
                };
                studyObject["00200013"] = studyObject["00200013"] || { //"00200013":$localize `:@@instance_number:Instance Number`
                    "vr": "IS",
                    "Value": [
                        _.get(studyObject, "00201209.Value[0]")*1 + fileIndex*1 + 1
                    ]
                };
            }else{
                studyObject["00200011"] = studyObject["00200011"] || { // "00200011":$localize `:@@upload-files.series_number:Series Number`
                    "vr": "IS",
                    "Value": [
                        this.seriesNumber || 0
                    ]
                };
                studyObject["00200013"] = { //"00200013":$localize `:@@instance_number:Instance Number`
                    "vr": "IS",
                    "Value": [
                        fileIndex + 1
                    ]
                };
            }
            if (_.hasIn(studyObject, "0020000D.Value[0]") && this.mode != "series") {
                studyObject["0020000E"] = studyObject["0020000E"] || { ///"0020000E":$localize `:@@upload-files.series_instance_uid:Series Instance UID` //Decides if the file in the same series appear
                    "vr": "UI",
                    "Value": [
                        seriesInstanceUID
                    ]
                };
            }else{
                if(!_.hasIn(studyObject, "0020000E.Value[0]")){
                    studyObject["0020000D"] = studyObject["0020000D"] || {
                        "vr": "UI",
                        "Value": [
                            seriesInstanceUID
                        ]
                    };
                }
            }

            studyObject["00080060"] = studyObject["00080060"] || {
                "vr": "CS",
                "Value": [
                    $this.modality
                ]
            };
            let object = [{}];
            this.service.fixFileSpecificEntries(file, studyObject);
            Object.keys(studyObject).forEach(key => {
                if (([
                    "00080054",
                    "00080056",
                    "00080061",
                    "00080062",
                    "00081190",
                    "00201200",
                    "00201206",
                    "00201208"
                ].indexOf(key) === -1))
                    object[0][key] = studyObject[key];
            });
            const jsonData = dashes + boundary + crlf + 'Content-Type: application/dicom+json' + crlf + crlf + JSON.stringify(j4care.removeKeyFromObject(object, ["required","enum", "multi"])) + crlf;

            const postDataStart = jsonData + dashes + boundary + crlf + 'Content-Type: ' + this.service.fileTypeFromExt(fileTypeOrExt) + crlf + 'Content-Location: file/' + file.name + crlf + crlf;
            const postDataEnd = crlf + dashes + boundary + dashes;
            if(fileTypeOrExt === "application/dicom"){
                xmlHttpRequest.setRequestHeader('Content-Type', 'multipart/related;type="application/dicom";boundary=' + boundary);
            }else{
                xmlHttpRequest.setRequestHeader('Content-Type', 'multipart/related;type="application/dicom+json";boundary=' + boundary);
            }
            xmlHttpRequest.setRequestHeader('Accept', 'application/dicom+json');
            if (!this.mainservice.global.notSecure) {
                xmlHttpRequest.setRequestHeader('Authorization', `Bearer ${token}`);
            }
            xmlHttpRequest.upload.onprogress = function (e) {
                if (e.lengthComputable) {
                    $this.percentComplete[file.name]['value'] = (e.loaded / e.total) * 100;
                }
            };
            xmlHttpRequest.onreadystatechange = () => {
                if (xmlHttpRequest.readyState === 4) {
                    if (xmlHttpRequest.status === 200) {
                        $this.percentComplete[file.name]['showLoader'] = false;
                        $this.percentComplete[file.name]['showTicker'] = true;
                        console.log(`in response`, JSON.parse(xmlHttpRequest.response));
                    } else {
                        $this.percentComplete[file.name]['showLoader'] = false;
                        console.log(`in response error`, xmlHttpRequest.status);
                        console.log('statusText', xmlHttpRequest.statusText);
                        $this.percentComplete[file.name]['value'] = 0;
                        $this.percentComplete[file.name]['status'] = xmlHttpRequest.status + ` ` + xmlHttpRequest.statusText;
                    }
                }
                // $this.percentComplete[file.name]['showLoader'] = true;
            };
            xmlHttpRequest.upload.onloadstart = function (e) {
                $this.percentComplete[file.name]['value'] = 1;
            };
            xmlHttpRequest.upload.onloadend = function (e) {
                if (xmlHttpRequest.status === 200) {
                    $this.percentComplete[file.name]['showLoader'] = false;
                    $this.percentComplete[file.name]['showTicker'] = true;
                    $this.percentComplete[file.name]['value'] = 100;
                }
            };
            xmlHttpRequest.send(new Blob([new Blob([postDataStart]), file, new Blob([postDataEnd])]));
        }else{
            this.mainservice.showError("A STOW-RS server is missing!")
        }
        /*                        },err=>{
                                    console.log("errwebApp",err);
                                });*/
    }

/*    fixFileSpecificEntries(file,object){
        if(_.hasIn(object,"00420011.BulkDataURI")){
            _.set(object,"00420011.BulkDataURI", `file/${file.name}`);
        }
        if(_.hasIn(object,"7FE00010.BulkDataURI")){
            _.set(object,"7FE00010.BulkDataURI", `file/${file.name}`);
        }
        if(_.hasIn(object,"00080018.Value[0]")){
            _.set(object,"00080018.Value[0]", j4care.generateOIDFromUUID());
        }
    }*/

    close(dialogRef){
        if (this.xmlHttpRequest){
            this.xmlHttpRequest.abort();
        }
        dialogRef.close(null);
    }
    onChange(newValue) {
        this._selectedAe = newValue.dicomAETitle;
    }

    get dicomObject() {
        return this._dicomObject;
    }

    set dicomObject(value) {
        this._dicomObject = value;
    }

    get selectedAe() {
        return this._selectedAe;
    }

    set selectedAe(value) {
        this._selectedAe = value;
    }

    get aes() {
        return this._aes;
    }

    set aes(value) {
        this._aes = value;
    }
    get fromExternalWebApp() {
        return this._fromExternalWebApp;
    }

    set fromExternalWebApp(value) {
        this._fromExternalWebApp = value;
    }

    get preselectedWebApp():DcmWebApp {
        return this._preselectedWebApp;
    }

    set preselectedWebApp(value:DcmWebApp) {
        this._preselectedWebApp = value;
    }

    getWebApps(){
        console.log("_preselectedWebApp",this._preselectedWebApp);
        let filters = {
            dcmWebServiceClass:"STOW_RS"
        };
        if(this._preselectedWebApp && _.hasIn(this._preselectedWebApp, "dicomDeviceName")){
            filters["dicomDeviceName"] = this._preselectedWebApp.dicomDeviceName;
        }
        this.studyService.getWebApps(filters).subscribe((res)=>{
            if(res && res.length > 0){
                this.webApps = res;
                this.webApps.forEach((webApp:DcmWebApp)=>{
                    if(this._preselectedWebApp){
                        if(webApp.dcmWebAppName === this._preselectedWebApp.dcmWebAppName){
                            this.selectedWebApp = webApp;
                        }
                    }else{
                        if(webApp.dicomAETitle === this._selectedAe)
                            this.selectedWebApp = webApp;
                    }
                });
            }else{
                this.mainservice.showError($localize `:@@upload-files.no_web_application_with_the_web_service_class_stow_rs_found_in_this_device:No Web Application with the Web Service Class "STOW_RS" found in this device`);
                this.dialogRef.close(null);
            }
        },(err)=>{

        });
    }


/*    let fileInput = document.getElementById('fileInput');
    fileInput.onchange  = function(){
        let file = fileInput.files[0];
        fileIsDicom(file).then(isDicom=>{
            console.log("isDicom=",isDicom);
        })
    };*/
/*    private fileTypeOrExt(file: File) {
        let fileType = file.type;
        let fileExt = file.name.substr(file.name.lastIndexOf(".") + 1);
        return fileType.length == 0
            ? fileExt : fileType;
    }*/
    //private
}
