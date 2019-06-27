import { Component, OnInit } from '@angular/core';
// import {FileUploader} from 'ng2-file-upload';
import {MatDialogRef} from '@angular/material';
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {AppService} from "../../../app.service";
import {KeycloakService} from "../../../helpers/keycloak-service/keycloak.service";

@Component({
  selector: 'app-upload-files',
  templateUrl: './upload-vendor.component.html'
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
            xmlHttpRequest.open('PUT', `../devices/${this._deviceName}/vendordata`, true);
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
