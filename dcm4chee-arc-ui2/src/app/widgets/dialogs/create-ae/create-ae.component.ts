import { Component } from '@angular/core';
import {MdDialogRef} from '@angular/material';
import * as _ from 'lodash';
import {AppService} from '../../../app.service';
import {Http} from '@angular/http';
import {SlimLoadingBarService} from 'ng2-slim-loading-bar';
import {WindowRefService} from "../../../helpers/window-ref.service";
import {HttpErrorHandler} from "../../../helpers/http-error-handler";
import {J4careHttpService} from "../../../helpers/j4care-http.service";

@Component({
  selector: 'app-create-ae',
  templateUrl: './create-ae.component.html'
})
export class CreateAeComponent {
    private _dicomconn;
    private _newAetModel;
    private _netAEModel;
    showdevice= false;
    showconn= true;
    showselectdevice= true;
    showconnselecteddevice= false;
    showae= true;
    activetab= 'createdevice';
    selctedDeviceObject;
    selectedDevice;
    netConnModelDevice;
    private _devices;
    _ = _;
    constructor(
        public $http:J4careHttpService,
        public dialogRef: MdDialogRef<CreateAeComponent>,
        public mainservice: AppService,
        public cfpLoadingBar: SlimLoadingBarService,
        public httpErrorHandler:HttpErrorHandler
    ) {
        this.cfpLoadingBar.complete();
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
                    .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
                    .subscribe((response) => {
                        $this.selctedDeviceObject = response;
                        // $scope.selctedDeviceObject.dicomNetworkConnection;
                        // $scope.selctedDeviceObject.dicomNetworkConnection.push($scope.netConnModelDevice);
                        console.log('this.selctedDeviceObject', $this.selctedDeviceObject);
                        $this.setReferencesFromDevice();
                        $this.cfpLoadingBar.stop();
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
                }]

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
        if (code === 8){
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
