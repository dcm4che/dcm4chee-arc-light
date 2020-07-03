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
    constructor(
        public dialogRef: MatDialogRef<UploadFilesComponent>,
        public mainservice:AppService,
        public $http:J4careHttpService,
        private studyService:StudyService,
        private uploadDicomService:UploadDicomService,
        private _keycloakService: KeycloakService
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
        if(this.fileList[0] && (this.fileList[0].type === "image/jpeg" || this.fileList[0].type === "image/png" || this.fileList[0].type === "image/gif" || this.fileList[0].type === "image/tiff")){
            this.isImage = true;
        }

        this.studyService.getIodFromContext(this.fileList[0].type, this.mode).subscribe(iods=>{
            console.log("iods",iods);
            if(!this._dicomObject){
                this._dicomObject = {
                    attrs:[]
                }
            }
/*            [
                "0008103E",
                "00080018",
                "00420011",
            ]
            [
                "0008103E"
                "00080018"
                "00080020"
                "00080030"
                "00080090"
                "00200010"
                "00080050"
                "00420011"
                "00080016"
                "00280301"
                "00420012"
                "00420010"
                "00080016"
                "00080016"
                "7FE00010"
                "00080008"
                "00080064"
                "00200020"
            ].forEach(k=>{
                delete this._dicomObject
            })*/
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
            if (this.fileList[0].type === "application/pdf") {
                this._dicomObject.attrs["00420011"] = {
                    "vr": "OB",
                    "BulkDataURI": "file/" + this.fileList[0].name
                };
                this._dicomObject.attrs["00080016"] = {
                    "vr": "UI",
                    "Value": [
                        "1.2.840.10008.5.1.4.1.1.104.1"
                    ]
                }
                this._dicomObject.attrs["00280301"] = {
                    "vr": "CS",
                    "Value": [
                        "YES"
                    ]
                };
                this._dicomObject.attrs["00420012"] = {
                    "vr": "LO",
                    "Value": [
                        "application/pdf"
                    ]
                };
                this._dicomObject.attrs["00420010"] = {
                    "vr": "ST",
                    "Value": [
                        ""
                    ]
                };
            } else {
                if (this.fileList[0].type.indexOf("video") > -1) {
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
                    "BulkDataURI": "file/" + this.fileList[0].name
                }
                // transfareSyntax = ';transfer-syntax=' + transfareSyntax;
            }
            if (this.fileList[0].type === "image/jpeg" || this.fileList[0].type === "image/png" || this.fileList[0].type === "image/gif"|| this.fileList[0].type === "image/tiff") {
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

/*        console.log("filtetypes",this.fileList);
        let type;
        let i = 0;
        _.forEach(this.fileList,file=>{
            if(!type){
                type = file.type;
                i++;
            }else{
                if(type != file.type){
                   i++;
                }
            }
        });
        if(i > 1){
            this.mainservice.showError("Mixed file types at once is not supported!");
            this.fileList = undefined;
            this.file = undefined;
            this.isImage = undefined;
        }
        //video / video.iod.json
        //image / sc.iod.json == screenshot or photograph vlPhotographic.iod.json
        //pdf   /encapsulatedPDF.iod.json*/

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

    upload() {
        let $this = this;
        let boundary = Math.random().toString().substr(2);
        let descriptionPart;
        let token;
        this.showFileList = true;
/*        $this.studyService.clearPatientObject(this.dicomObject.attrs);
        $this.studyService.convertStringToNumber(this.dicomObject.attrs);
        // StudiesService.convertDateToString($scope, "editstudyFiltered");

        //Add patient attributs again
        // angular.extend($scope.editstudyFiltered.attrs, patient.attrs);
        // $scope.editstudyFiltered.attrs.concat(patient.attrs);
        let local = {};
        // $this.studyService.appendPatientIdTo(patient.attrs, local);
        // local["00100020"] = patient.attrs["00100020"];
        _.forEach(this.dicomObject.attrs, (m, i)=>{
            if (this.iod[i]){
                local[i] = m;
            }
        });*/
        let seriesInstanceUID;
        this.getToken().subscribe((response) => {
            if(!this.mainservice.global.notSecure){
                token = response.token;
            }
            if(!this.seriesNumber && this.seriesNumber != 0){
                this.seriesNumber = 0;
            }
            if (this.fileList) {
                seriesInstanceUID = j4care.generateOIDFromUUID();
                _.forEach(this.fileList, (file, i) => {
                    switch (file.type) {
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
                    }

                        let xmlHttpRequest = new XMLHttpRequest();
                        let url = this.studyService.getDicomURL("study",this.selectedWebApp);
                        // this.studyService.getWebAppFromWebServiceClassAndSelectedWebApp(this.studyWebService,"DCM4CHEE_ARC_AET","STOW_RS").subscribe(webApp=>{
                        //     console.log("webApp",webApp);
                            // let url = this.studyService.getDicomURL('study', webApp);
                            // let url = this.uploadDicomService.getUrlFromWebApp(this.studyWebService.selectedWebService);
                            if (url) {
                                this.percentComplete[file.name] = {};
                                $this.percentComplete[file.name]['showTicker'] = false;
                                $this.percentComplete[file.name]['showLoader'] = true;
                                xmlHttpRequest.open('POST', url, true);
                                let dashes = '--';
                                let crlf = '\r\n';
                                //Post with the correct MIME type (If the OS can identify one)
                                /*                        let studyObject = _.pickBy(local, (o, i) => {
                                                            return (i.toString().indexOf("777") === -1);
                                                        });          */
                                // _.assign(this._dicomObject,this.tempAttributes);
                                Object.keys(this.tempAttributes.attrs).forEach(attr=>{
                                    this._dicomObject.attrs[attr] = this.tempAttributes.attrs[attr];
                                });
                                let studyObject = _.pickBy(this._dicomObject.attrs, (o, i) => {
                                    return (i.toString().indexOf("777") === -1);
                                });
/*                                if (!$this.description || $this.description === "") {
                                    $this.description = $localize `:@@upload-files.imported_:Imported ` + descriptionPart;
                                }*/
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
                                        i + 1
                                    ]
                                };
                                if(this.mode === "series" && _.hasIn(studyObject, "00201209.Value[0]")){
                                    studyObject["00200011"] = { // "00200011":$localize `:@@upload-files.series_number:Series Number`
                                        "vr": "IS",
                                        "Value": [
                                            _.get(studyObject, "00201209.Value[0]")*1 + 1
                                        ]
                                    };
                                    studyObject["00200013"] = { //"00200013":$localize `:@@instance_number:Instance Number`
                                        "vr": "IS",
                                        "Value": [
                                            _.get(studyObject, "00201209.Value[0]")*1 + i*1 + 1
                                        ]
                                    };
                                }else{
                                    studyObject["00200011"] = { // "00200011":$localize `:@@upload-files.series_number:Series Number`
                                        "vr": "IS",
                                        "Value": [
                                            this.seriesNumber || 0
                                        ]
                                    };
                                    studyObject["00200013"] = { //"00200013":$localize `:@@instance_number:Instance Number`
                                        "vr": "IS",
                                        "Value": [
                                            i + 1
                                        ]
                                    };
                                }
                                if (_.hasIn(studyObject, "0020000D.Value[0]") && this.mode != "series") {
                                    studyObject["0020000E"] = { ///"0020000E":$localize `:@@upload-files.series_instance_uid:Series Instance UID` //Decides if the file in the same series appear
                                        "vr": "UI",
                                        "Value": [
                                            seriesInstanceUID
                                        ]
                                    };
                                }else{
                                    if(!_.hasIn(studyObject, "0020000E.Value[0]")){
                                        studyObject["0020000D"] = {
                                            "vr": "UI",
                                            "Value": [
                                                seriesInstanceUID
                                            ]
                                        };
                                    }
                                }
/*                                if (!_.hasIn(studyObject, "00080020.Value[0]")) { // Study Date
                                    studyObject["00080020"] = {
                                        "vr": "DA",
                                        "Value": [
                                        ]
                                    };
                                }
                                if (!_.hasIn(studyObject, "00080030.Value[0]")) { // Study Time
                                    studyObject["00080030"] = {
                                        "vr": "TM",
                                        "Value": [
                                        ]
                                    };
                                }
                                if (!_.hasIn(studyObject, "00080090.Value[0]")) { // Referring Physician's Name
                                    studyObject["00080090"] = {
                                        "vr": "PN",
                                        "Value": [
                                        ]
                                    };
                                }
                                if (!_.hasIn(studyObject, "00200010.Value[0]")) { // Study ID
                                    studyObject["00200010"] = {
                                        "vr": "SH",
                                        "Value": [
                                        ]
                                    };
                                }
                                if (!_.hasIn(studyObject, "00080050.Value[0]")) { // Accession Number
                                    studyObject["00080050"] = {
                                        "vr": "SH",
                                        "Value": [
                                        ]
                                    };
                                }*/
/*                                studyObject["00080018"] = {
                                    "vr": "UI",
                                    "Value": [
                                        j4care.generateOIDFromUUID()
                                    ]
                                };*/

/*                                if (file.type === "application/pdf") {
                                    studyObject["00420011"] = {
                                        "vr": "OB",
                                        "BulkDataURI": "file/" + file.name
                                    };
                                    studyObject["00080016"] = {
                                        "vr": "UI",
                                        "Value": [
                                            "1.2.840.10008.5.1.4.1.1.104.1"
                                        ]
                                    }
                                    studyObject["00280301"] = {
                                        "vr": "CS",
                                        "Value": [
                                            "YES"
                                        ]
                                    };
                                    studyObject["00420012"] = {
                                        "vr": "LO",
                                        "Value": [
                                            "application/pdf"
                                        ]
                                    };
                                    studyObject["00420010"] = {
                                        "vr": "ST",
                                        "Value": [
                                            $this.description
                                        ]
                                    };
                                } else {
                                    if (file.type.indexOf("video") > -1) {
                                        studyObject["00080016"] = {
                                            "vr": "UI",
                                            "Value": [
                                                "1.2.840.10008.5.1.4.1.1.77.1.4.1"
                                            ]
                                        }
                                    } else {
                                        studyObject["00080016"] = {
                                            "vr": "UI",
                                            "Value": [
                                                $this.selectedSopClass.value
                                            ]
                                        }
                                    }
                                    studyObject["7FE00010"] = {
                                        "vr": "OB",
                                        "BulkDataURI": "file/" + file.name
                                    }
                                    // transfareSyntax = ';transfer-syntax=' + transfareSyntax;
                                }
                                if (file.type === "image/jpeg") {
                                    studyObject["00080008"] = {
                                        "vr": "CS",
                                        "Value": [
                                            "ORIGINAL",
                                            "PRIMARY"
                                        ]
                                    };
                                    if (this.selectedSopClass.value === '1.2.840.10008.5.1.4.1.1.7') {
                                        studyObject["00080064"] = {
                                            "vr": "CS",
                                            "Value": [
                                                "WSD"
                                            ]
                                        };
                                        studyObject["00200020"] = {
                                            "vr": "CS"
                                        };
                                    }
                                }*/
                                studyObject["00080060"] = {
                                    "vr": "CS",
                                    "Value": [
                                        $this.modality
                                    ]
                                };
                                let object = [{}];
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
                                const jsonData = dashes + boundary + crlf + 'Content-Type: application/dicom+json' + crlf + crlf + JSON.stringify(object) + crlf;

                                const postDataStart = jsonData + dashes + boundary + crlf + 'Content-Type: ' + file.type + crlf + 'Content-Location: file/' + file.name + crlf + crlf;
                                const postDataEnd = crlf + dashes + boundary + dashes;

                                xmlHttpRequest.setRequestHeader('Content-Type', 'multipart/related;type="application/dicom+json";boundary=' + boundary);
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
                });
            }
        });
    }
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
}
