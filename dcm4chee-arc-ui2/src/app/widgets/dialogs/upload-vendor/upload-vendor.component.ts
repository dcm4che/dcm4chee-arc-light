import { Component, OnInit } from '@angular/core';
// import {FileUploader} from 'ng2-file-upload';
//import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {AppService} from "../../../app.service";
import {KeycloakService} from "../../../helpers/keycloak-service/keycloak.service";
import {j4care} from "../../../helpers/j4care.service";
import {MatDialogRef} from "@angular/material/dialog";

@Component({
    selector: 'app-upload-files',
    templateUrl: './upload-vendor.component.html',
    standalone: true
})
export class UploadVendorComponent implements OnInit {

    private _deviceName;
    selectedFile;
    constructor(
        public dialogRef: MatDialogRef<UploadVendorComponent>,
        public $http:J4careHttpService,
        public mainservice:AppService,
        private _keycloakService: KeycloakService
    ) { }

    ngOnInit() {
    }
    setFile(event){
        this.selectedFile = event.target.files[0];
    }
    uploadFile(dialogRef){
        let $this = this;
        let token;
        this._keycloakService.getToken().subscribe((response) => {
            if(!this.mainservice.global.notSecure){
                token = response.token;
            }
            let xmlHttpRequest = new XMLHttpRequest();
            xmlHttpRequest.open('PUT', `${j4care.addLastSlash(this.mainservice.baseUrl)}devices/${this._deviceName}/vendordata`, true);
            xmlHttpRequest.setRequestHeader("Content-Type","application/zip");
            if(!this.mainservice.global.notSecure) {
                xmlHttpRequest.setRequestHeader('Authorization', `Bearer ${token}`);
            }
            xmlHttpRequest.upload.onloadend = (e)=>{
                dialogRef.close('ok');
            }
            xmlHttpRequest.send($this.selectedFile);
        });
    }

    get deviceName() {
        return this._deviceName;
    }

    set deviceName(value) {
        this._deviceName = value;
    }
}
