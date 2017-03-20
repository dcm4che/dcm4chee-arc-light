import { Component } from '@angular/core';
import {MdDialogRef} from "@angular/material";
import * as _ from "lodash";
import {AppService} from "../../../app.service";
import {Http} from "@angular/http";

@Component({
  selector: 'app-create-ae',
  templateUrl: './create-ae.component.html'
})
export class CreateAeComponent {
    private _dicomconn;
    private _newAetModel;
    private _netAEModel;
    showdevice=false;
    showconn=true;
    showselectdevice=true;
    showconnselecteddevice=false;
    showae=true;
    activetab='createdevice';
    selctedDeviceObject;
    selectedDevice;
    netConnModelDevice;
    private _devices;
    _ = _;
    constructor(public $http:Http, public dialogRef: MdDialogRef<CreateAeComponent>, public mainservice:AppService) { }

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
        _.set(this, model,newValue);
    }

    get devices() {
        return this._devices;
    }

    set devices(value) {
        this._devices = value;
    };

    checkClick(e){
        console.log("e",e);
        var code = (e.keyCode ? e.keyCode : e.which);
/*        console.log("code in checkclick");
        if(!(e.target.id === "dropdown" || e.target.id === 'addPatientAttribut')){
            this.opendropdown = false;
        }*/
    };
    getDevice(e){
        console.log("e",e);
        console.log("selectedDevice",this.selectedDevice);
        this.selectedDevice = e;
        let $this = this;
        if(this.selectedDevice){
            if(this.selctedDeviceObject && this.selctedDeviceObject.dicomDeviceName === this.selectedDevice){
                $this.setReferencesFromDevice();
            }else{
                $this.$http.get('../devices/'+this.selectedDevice)
                    .map(res => res.json())
                    .subscribe((response) => {
                        $this.selctedDeviceObject = response;
                        // $scope.selctedDeviceObject.dicomNetworkConnection;
                        // $scope.selctedDeviceObject.dicomNetworkConnection.push($scope.netConnModelDevice);
                        console.log("this.selctedDeviceObject",$this.selctedDeviceObject);
                        $this.setReferencesFromDevice();

                    },(err) => {
                        $this.mainservice.setMessage({
                            "title": "Error " + err.status,
                            "text": err.statusText,
                            "status": "error"
                        });
                    });
            }
        }
    };
    setReferencesFromDevice(){
/*        this.dicomconn = [];
        let $this = this;
        _.forEach(this.selctedDeviceObject.dicomNetworkConnection, function(l, i) {
            $this.dicomconn.push({
                "value":"/dicomNetworkConnection/" + i,
                "name":l.cn
            });
        });*/
        // this.newAetModel.dicomNetworkAE = this.selctedDeviceObject.dicomNetworkAE;
        this.newAetModel.dicomNetworkAE[0].dicomNetworkConnectionReference = [];
        console.log("dicomNetworkConnectionReference",this.selctedDeviceObject);
/*        _.forEach(this.selctedDeviceObject.dicomNetworkConnection,(m,i)=>{
            console.log("m",m);
            console.log("i",i);
            if(!_.hasIn(this.newAetModel.dicomNetworkAE[0].dicomNetworkConnectionReference,'/dicomNetworkConnection/'+i )){
                this.newAetModel.dicomNetworkAE[0].dicomNetworkConnectionReference.push('/dicomNetworkConnection/'+i);
            }
        });*/
        console.log("this.newAetModel",this.newAetModel);

    };

    getNameOfRefference(reference){
        console.log("reference",reference);
        console.log("reference",this.selctedDeviceObject);
        let refIndex = _.parseInt(_.last(_.split(reference, '/')));
        console.log("refIndex",refIndex);
        if(this.selctedDeviceObject && _.hasIn(this.selctedDeviceObject,'dicomNetworkConnection['+refIndex+'].cn')){
            console.log("cn",this.selctedDeviceObject.dicomNetworkConnection[refIndex].cn);
            return this.selctedDeviceObject.dicomNetworkConnection[refIndex].cn;
        }else{
            return '';
        }
    }
    toggleReference(model,ref){
        console.log("model",model);
        console.log("ref",ref);
        if(this.inArray(ref, model)){
            console.log("in if");
          _.remove(model,(i)=>{
              console.log("i",i);
              return i === ref
          });
            console.log("in hasin",model);
        }else{
            console.log("in else");
            model.push(ref)
        }
        console.log("model",model)
        console.log("ref",ref)
    }
    inArray(element, array){
        for(let i of array){
            if(element === i){
                return true;
            }
        }
        return false;
    }

    changeTabAERegister(tabname){
        this.activetab = tabname;
        if(tabname ==='createdevice'){
            // this.getConn();
            this.dicomconn = [];
            this.newAetModel = {
                dicomNetworkConnection:[{
                    cn:'dicom',
                    dicomHostname:'localhost',
                    dicomPort:104
                }],
                dicomNetworkAE:[{
                    dicomNetworkConnectionReference:["/dicomNetworkConnection/0"]
                }]

            };
        }else{
            this.getDevice(null);
            this.dicomconn = [];
            this.newAetModel = {
                dicomNetworkConnection:[{
                    cn:'dicom',
                    dicomHostname:'localhost',
                    dicomPort:104
                }],
                dicomNetworkAE:[{
                    dicomNetworkConnectionReference:[]
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
        if(code === 8){
            let aetUppercase = this.newAetModel.dicomDeviceName.toUpperCase();
            if(this.newAetModel.dicomNetworkAE[0].dicomAETitle.slice(0, -1) === aetUppercase){
                this.newAetModel.dicomNetworkAE[0].dicomAETitle = aetUppercase;
            }
        }else{
            if(_.hasIn(this.newAetModel,'dicomNetworkAE[0].dicomAETitle') && _.hasIn(this.newAetModel,'dicomDeviceName')){
                let aetUppercase = this.newAetModel.dicomDeviceName.toUpperCase();
                if(this.newAetModel.dicomNetworkAE[0].dicomAETitle === aetUppercase.slice(0, -1)){
                    this.newAetModel.dicomNetworkAE[0].dicomAETitle = aetUppercase;
                }
            }else{
                if(this.newAetModel.dicomDeviceName){
                    this.newAetModel.dicomNetworkAE[0].dicomAETitle = this.newAetModel.dicomDeviceName.toUpperCase();
                }
            }
        }
    }
    addNewConnectionToDevice(){
        console.log("in addnewconnectiontodevice",this.selctedDeviceObject);
        this.selctedDeviceObject.dicomNetworkConnection.push(_.cloneDeep(this.newAetModel.dicomNetworkConnection[0]));
        // this.setReferencesFromDevice();
        console.log("in addnewconnectiontodevice",this.selctedDeviceObject);
    }
    removeNewConnectionFromDevice(){
        if(_.hasIn(this.newAetModel,'dicomNetworkConnection[0].cn')){
            _.forEach(this.selctedDeviceObject.dicomNetworkConnection, (m, i)=>{
                console.log("m",m);
                console.log("i",i);
                if(_.hasIn(this.newAetModel,'dicomNetworkConnection[0].cn') && m.cn === this.newAetModel.dicomNetworkConnection[0].cn){
                    this.selctedDeviceObject.dicomNetworkConnection.splice(i, 1);
                }
                console.log("this.newAetModel.dicomNetworkConnection[0]",this.newAetModel.dicomNetworkConnection[0].cn);
            })
        }
        console.log("this.selctedDeviceObject=",this.selctedDeviceObject);
    }
    validAeForm(){
        if(!_.hasIn(this.newAetModel,'dicomNetworkAE[0].dicomAETitle') || this.newAetModel.dicomNetworkAE[0].dicomAETitle === ''){
            return false;
        }
        if(!_.hasIn(this.newAetModel,'dicomNetworkAE[0].dicomNetworkConnectionReference[0]') || this.newAetModel.dicomNetworkAE[0].dicomNetworkConnectionReference[0] === ''){
            return false;
        }
        if(this.activetab === 'createdevice'){
            if(!_.hasIn(this.newAetModel,'dicomNetworkConnection[0]') || (this.newAetModel.dicomNetworkConnection[0].cn && this.newAetModel.dicomNetworkConnection[0].cn === "")){
                return false;
            }
        }else{
            if(!this.selctedDeviceObject){
                return false;
            }
        }
        return true;
    }
}
