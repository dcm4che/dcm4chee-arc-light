import {Component, OnInit} from '@angular/core';
import {MatDialogRef} from '@angular/material';
import * as _ from 'lodash';
import {AppService} from '../../../app.service';
import {Http} from '@angular/http';
import {WindowRefService} from "../../../helpers/window-ref.service";
import {HttpErrorHandler} from "../../../helpers/http-error-handler";
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {LoadingBarService} from '@ngx-loading-bar/core';
import {AeListService} from "../../../configuration/ae-list/ae-list.service";
import {j4care} from "../../../helpers/j4care.service";
import {SelectDropdown} from "../../../interfaces";

@Component({
    selector: 'app-create-ae',
    templateUrl: './create-ae.component.html',
    styles: [`
        .test_button button{
            background:rgba(6, 29, 47, 0.84);
            color: white;
            width: 130px;
            height: 26px;
            border: none;
            margin-top: 4px;
            margin-left: 5px;
        }
        .test_button button:hover{
            background: #061d2f;
        }
  `],
})
export class CreateAeComponent implements OnInit{
    private _dicomconn;
    private _newAetModel;
    private _netAEModel;
    showTestBlock = true;
    showdevice= false;
    showconn= true;
    showselectdevice= true;
    showconnselecteddevice= false;
    showae= true;
    activetab= 'createdevice';
    selctedDeviceObject;
    selectedDevice;
    showAetList = false;
    netConnModelDevice;
    private _aes;
    selectedCallingAet;
    private _devices;
    _ = _;
    configuredAetList = [];
    selectedForAcceptedCallingAET:string[] = [];
    dicomConnectionns = [];
    selectedDicomConnection:any =  {};
    constructor(
        public $http:J4careHttpService,
        public dialogRef: MatDialogRef<CreateAeComponent>,
        public mainservice: AppService,
        public cfpLoadingBar: LoadingBarService,
        public httpErrorHandler:HttpErrorHandler,
        private aeListService:AeListService
    ) {
    }
    ngOnInit(){
        this.cfpLoadingBar.complete();
        if(_.hasIn(this.mainservice.global,"uiConfig.dcmuiWidgetAets")){
            this.configuredAetList = (<string[]>_.get(this.mainservice.global,"uiConfig.dcmuiWidgetAets")).map(ae=>{
                // this.selectedForAcceptedCallingAET.push(ae);
                return new SelectDropdown(ae,ae);
            });
            if(_.hasIn(this.mainservice.global,"uiConfig.dcmuiDefaultWidgetAets")){
                this.selectedForAcceptedCallingAET = _.get(this.mainservice.global,"uiConfig.dcmuiDefaultWidgetAets");
            }
            //selectedForAcceptedCallingAET
        }else{
            this.aeListService.getAes().subscribe(aes=>{
                this.configuredAetList = aes.map(ae=>{
                    return new SelectDropdown(ae.dicomAETitle,ae.dicomAETitle);
                })
            },err=>{
                this.httpErrorHandler.handleError(err);
            })
        }
    }

    get dicomconn() {
        return this._dicomconn;
    }

    set dicomconn(value) {
        this._dicomconn = value;
    }

    get newAetModel() {
        return this._newAetModel;
    }

    set newAetModel(value) {
        this._newAetModel = value;
    }

    get netAEModel() {
        return this._netAEModel;
    }

    set netAEModel(value) {
        this._netAEModel = value;
    }
    onChange(newValue, model) {
        _.set(this, model, newValue);
    }

    get devices() {
        return this._devices;
    }

    set devices(value) {
        this._devices = value;
    };

    get aes() {
        return this._aes;
    }

    set aes(value) {
        this._aes = value;
    }

    checkClick(e){
        console.log('e', e);
        let code = (e.keyCode ? e.keyCode : e.which);
    };
    getDevice(e){
        this.selectedDevice = e;
        let $this = this;
        if (this.selectedDevice){
            if (this.selctedDeviceObject && this.selctedDeviceObject.dicomDeviceName === this.selectedDevice){
                $this.setReferencesFromDevice();
            }else{
                $this.cfpLoadingBar.start();
                $this.$http.get('../devices/' + this.selectedDevice)
                    .map(res => j4care.redirectOnAuthResponse(res))
                    .subscribe((response) => {
                        $this.selctedDeviceObject = response;
                        // $scope.selctedDeviceObject.dicomNetworkConnection;
                        // $scope.selctedDeviceObject.dicomNetworkConnection.push($scope.netConnModelDevice);
                        console.log('this.selctedDeviceObject', $this.selctedDeviceObject);
                        $this.setReferencesFromDevice();
                        $this.cfpLoadingBar.complete();
                    }, (err) => {
                        $this.httpErrorHandler.handleError(err);
                        $this.cfpLoadingBar.complete();
                    });
            }
        }
    };
    setReferencesFromDevice(){
        this.newAetModel.dicomNetworkAE[0].dicomNetworkConnectionReference = [];
    };

    getNameOfRefference(reference){
        let refIndex = _.parseInt(_.last(_.split(reference, '/')));
        console.log('refIndex', refIndex);
        if (this.selctedDeviceObject && _.hasIn(this.selctedDeviceObject, 'dicomNetworkConnection[' + refIndex + '].cn')){
            console.log('cn', this.selctedDeviceObject.dicomNetworkConnection[refIndex].cn);
            return this.selctedDeviceObject.dicomNetworkConnection[refIndex].cn;
        }else{
            return '';
        }
    }
    toggleReference(model, ref){
        if (this.inArray(ref, model)){
          _.remove(model, (i) => {
              return i === ref;
          });
        }else{
            model.push(ref);
        }
        this.dicomConnectionns = this.getDicomConnections();

    }
    inArray(element, array){
        for (let i of array){
            if (element === i){
                return true;
            }
        }
        return false;
    }

    changeTabAERegister(tabname){
        this.activetab = tabname;
        if (tabname === 'createdevice'){
            // this.getConn();
            this.dicomconn = [];
            this.newAetModel = {
                dicomNetworkConnection: [{
                    cn: 'dicom',
                    dicomHostname: 'localhost',
                    dicomPort: 104
                }],
                dicomNetworkAE: [{
                    dicomNetworkConnectionReference: ['/dicomNetworkConnection/0']
                }],
                dicomInstitutionName:['']
            };
        }else{
            this.getDevice(null);
            this.dicomconn = [];
            this.newAetModel = {
                dicomNetworkConnection: [{
                    cn: 'dicom',
                    dicomHostname: 'localhost',
                    dicomPort: 104
                }],
                dicomNetworkAE: [{
                    dicomNetworkConnectionReference: []
                }]

            };
        }
    };
    trackByFn(index, item) {
        return index; // or item.id
    }
    addArrayElement(model,key){
       if(_.hasIn(model,key) && _.isArray(model[key])){
            model[key].push("");
       }else{
           model[key] = [""];
       }
    }
    removeElemnt(model,i){
        model.splice(i,1);
    }

/*
    getConn(){
        if(this.newAetModel && this.activetab === "createdevice" && this.newAetModel.dicomNetworkConnection && this.newAetModel.dicomNetworkConnection[0] && this.newAetModel.dicomNetworkConnection[0].cn && this.newAetModel.dicomNetworkConnection[0].cn != ""){
            this.dicomconn = [];
            this.dicomconn.push({
                "value":"/dicomNetworkConnection/" + 0,
                "name":this.newAetModel.dicomNetworkConnection[0].cn
            });
        }
    }*/
    updateAetFromDevicename(e){
        let code = (e.keyCode ? e.keyCode : e.which);
        if (code === 8 && _.hasIn(this.newAetModel,"dicomDeviceName")){
            let aetUppercase = this.newAetModel.dicomDeviceName.toUpperCase();
            if (this.newAetModel.dicomNetworkAE[0].dicomAETitle.slice(0, -1) === aetUppercase){
                this.newAetModel.dicomNetworkAE[0].dicomAETitle = aetUppercase;
            }
        }else{
            if (_.hasIn(this.newAetModel, 'dicomNetworkAE[0].dicomAETitle') && _.hasIn(this.newAetModel, 'dicomDeviceName')){
                let aetUppercase = this.newAetModel.dicomDeviceName.toUpperCase();
                if (this.newAetModel.dicomNetworkAE[0].dicomAETitle === aetUppercase.slice(0, -1)){
                    this.newAetModel.dicomNetworkAE[0].dicomAETitle = aetUppercase;
                }
            }else{
                if (this.newAetModel.dicomDeviceName){
                    this.newAetModel.dicomNetworkAE[0].dicomAETitle = this.newAetModel.dicomDeviceName.toUpperCase();
                }
            }
        }
    }
    addNewConnectionToDevice(){
        if (!this.newAetModel.dicomNetworkConnection[0].cn || this.newAetModel.dicomNetworkConnection[0].cn === ''){
            this.mainservice.setMessage({
                'title': 'Error',
                'text': 'Name of the new connection is empty!',
                'status': 'error'
            });
        }else{

            let hasConnection = false;
            _.forEach(this.selctedDeviceObject.dicomNetworkConnection, (m, i) => {
                    if (m.cn === this.newAetModel.dicomNetworkConnection[0].cn){
                        hasConnection = true;
                    }
            });
            if (hasConnection){
                this.mainservice.setMessage({
                    'title': 'Error',
                    'text': 'Connection with that name exist!',
                    'status': 'error'
                });
            }else{
                if (_.hasIn(this.selctedDeviceObject, 'dicomNetworkConnection')){
                    this.selctedDeviceObject.dicomNetworkConnection.push(_.cloneDeep(this.newAetModel.dicomNetworkConnection[0]));
                }else{
                    this.selctedDeviceObject['dicomNetworkConnection'] = [];
                    this.selctedDeviceObject.dicomNetworkConnection.push(_.cloneDeep(this.newAetModel.dicomNetworkConnection[0]));
                }
            }
        }
    }
    removeNewConnectionFromDevice(){
        if (_.hasIn(this.newAetModel, 'dicomNetworkConnection[0].cn')){
            _.forEach(this.selctedDeviceObject.dicomNetworkConnection, (m, i) => {
                if (_.hasIn(this.newAetModel, 'dicomNetworkConnection[0].cn') && m.cn === this.newAetModel.dicomNetworkConnection[0].cn){
                    this.selctedDeviceObject.dicomNetworkConnection.splice(i, 1);
                }
                console.log('this.newAetModel.dicomNetworkConnection[0]', this.newAetModel.dicomNetworkConnection[0].cn);
            });
        }
        console.log('this.selctedDeviceObject=', this.selctedDeviceObject);
    }
    getDicomConnections(){
        try{
            if(_.hasIn(this.newAetModel,"dicomNetworkAE.0.dicomNetworkConnectionReference") && _.hasIn(this.newAetModel,"dicomNetworkAE.0.dicomNetworkConnectionReference")){
                return this.selctedDeviceObject.dicomNetworkConnection.filter((connection,i)=>{
                    return this.newAetModel.dicomNetworkAE["0"].dicomNetworkConnectionReference.indexOf(`/dicomNetworkConnection/${i}`) > -1 &&
                            (
                                !_.hasIn(connection, "dcmNetworkConnection.dcmProtocol") ||
                                !connection.dcmNetworkConnection.dcmProtocol ||
                                connection.dcmNetworkConnection.dcmProtocol === ""
                            )
                });
            }
        }catch (e) {
            return [];
        }
        return [];
    }

    testConnection(){
        if(this.selectedCallingAet && this.newAetModel.dicomNetworkAE[0].dicomAETitle && this.newAetModel.dicomNetworkConnection[0].dicomHostname && this.newAetModel.dicomNetworkConnection[0].dicomPort){
            this.cfpLoadingBar.start();
            let data;
            if(this.activetab === "selectdevice"){
                if(this.dicomConnectionns.length > 1){
                    console.log("this.selectedDicomConnection",this.selectedDicomConnection);
                    if(this.selectedDicomConnection){
                        data = {
                            host:this.selectedDicomConnection.dicomHostname,
                            port:this.selectedDicomConnection.dicomPort
                        };
                    }else{
                        this.mainservice.showError("Multiple DICOM connection selected!");
                        this.cfpLoadingBar.complete();
                        return;
                    }
                }else{
                    if(this.dicomConnectionns.length === 0){
                        this.mainservice.showError("No DICOM connection found!");
                        this.cfpLoadingBar.complete();
                        return;
                    }else{
                        data = {
                            host:this.dicomConnectionns[0].dicomHostname,
                            port:this.dicomConnectionns[0].dicomPort
                        };
                    }
                }
            }else{
                data = {
                    host: this.newAetModel.dicomNetworkConnection[0].dicomHostname,
                    port: this.newAetModel.dicomNetworkConnection[0].dicomPort
                }
            }
            if(data && data.host && data.port){
                this.aeListService.echoAe(this.selectedCallingAet, this.newAetModel.dicomNetworkAE[0].dicomAETitle,data).subscribe((response) => {
                    this.cfpLoadingBar.complete();
                    let msg = this.aeListService.generateEchoResponseText(response);
                    this.mainservice.setMessage({
                        'title': msg.title,
                        'text': msg.text,
                        'status': msg.status
                    });
                    this.dicomConnectionns = [];
                }, err => {
                    this.cfpLoadingBar.complete();
                    this.httpErrorHandler.handleError(err);
                });
            }else{
                this.mainservice.showError("No connection found");
            }
        }else{
            this.mainservice.setMessage({
                title:"Error",
                text:"Parameter is missing, check the parameters Host, port, AE Title and Calling AET!",
                status:"error"
            })
        }
    }
    validAeForm(){
        if (!_.hasIn(this.newAetModel, 'dicomNetworkAE[0].dicomAETitle') || this.newAetModel.dicomNetworkAE[0].dicomAETitle === ''){
            return false;
        }
        if (!_.hasIn(this.newAetModel, 'dicomNetworkAE[0].dicomNetworkConnectionReference[0]') || this.newAetModel.dicomNetworkAE[0].dicomNetworkConnectionReference[0] === ''){
            return false;
        }
        if (this.activetab === 'createdevice'){
            if (!_.hasIn(this.newAetModel, 'dicomNetworkConnection[0]') || (this.newAetModel.dicomNetworkConnection[0].cn && this.newAetModel.dicomNetworkConnection[0].cn === '')){
                return false;
            }
        }else{
            if (!this.selctedDeviceObject){
                return false;
            }
        }
        return true;
    }
}
