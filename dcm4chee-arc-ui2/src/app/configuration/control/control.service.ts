import { Injectable } from '@angular/core';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {AppService} from "../../app.service";
import * as _ from 'lodash'

@Injectable()
export class ControlService {

    constructor(
        private $http:J4careHttpService,
        private appservices:AppService
    ) { }

    fetchStatus = (url?) => this.$http.get(`${this.removeSlashOnTheEndOfUrl(url) || ''}/dcm4chee-arc/ctrl/status`);
    startArchive = (url?) => this.$http.post(`${this.removeSlashOnTheEndOfUrl(url) || ''}/dcm4chee-arc/ctrl/start`, {});
    stopArchive = (url?) => this.$http.post(`${this.removeSlashOnTheEndOfUrl(url) || ''}/dcm4chee-arc/ctrl/stop`, {});
    reloadArchive = (url?) => this.$http.post(`${this.removeSlashOnTheEndOfUrl(url) || ''}/dcm4chee-arc/ctrl/reload`, {});


    removeSlashOnTheEndOfUrl(url:string){
        if(url && url != "" && url.slice(-1) === "/"){
            return url.slice(0, -1);
        }
        return url;
    }
    getTableSchema(){
        return [
            {
                title:"&nbsp;",
                code:"actions",
                widthWeight:0.6,
                calculatedWidth:"20%"
            },
            {
                title:"Device name",
                code:"dcmuiDeviceURLName",
                description:"Archive device name",
                widthWeight:1,
                calculatedWidth:"20%"
            },
            {
                title:"Device description",
                code:"dicomDescription",
                description:"Archive device description",
                widthWeight:3,
                calculatedWidth:"20%"
            },
            {
                title:"Manufacturer",
                code:"dicomManufacturer",
                description:"Manufacturer",
                widthWeight:1,
                calculatedWidth:"20%"
            },
            {
                title:"Model name",
                code:"dicomManufacturerModelName",
                description:"Manufacturer model name",
                widthWeight:1,
                calculatedWidth:"20%"
            },
            {
                title:"Primary device type",
                code:"dicomPrimaryDeviceType",
                widthWeight:1,
                calculatedWidth:"20%"
            },
            {
                title:"Software version",
                code:"dicomSoftwareVersion",
                widthWeight:1,
                calculatedWidth:"20%"
            }
        ]
    }
    getMyArchivesFromConfig($this, allDevices, callBack){
        let devices = {};
        try{
            let config = this.appservices.global.uiConfig.dcmuiDeviceClusterObject.filter(cluster=>{
                let check = false;
                cluster.dcmuiDeviceClusterDevices.forEach(device=>{
                    if(device === this.appservices.archiveDeviceName || device.dcmuiDeviceURLName === this.appservices.archiveDeviceName)
                        check = true;
                });
                return check;
            })[0];
            config.dcmuiDeviceClusterDevices.forEach((deviceName,i)=>{
                this.appservices.global.uiConfig.dcmuiDeviceURLObject.forEach(deviceObject=>{
                    if(deviceObject.dcmuiDeviceURLName === deviceName || deviceObject.dcmuiDeviceURLName === deviceName.dcmuiDeviceURLName){
                        devices[deviceObject.dcmuiDeviceURLName] = deviceObject;
                    }
                });
            });
        }catch (e) {
            if(this.appservices.archiveDeviceName){
                devices[this.appservices.archiveDeviceName] = {
                    dcmuiDeviceURLName:this.appservices.archiveDeviceName
                }
            }
        }
        allDevices.forEach(device=>{
            if(_.hasIn(devices,device.dicomDeviceName)){
                devices[device.dicomDeviceName] = devices[device.dicomDeviceName] || {};
                Object.assign(devices[device.dicomDeviceName], device);
            }
        });
        callBack.call($this, devices);
    }
}
