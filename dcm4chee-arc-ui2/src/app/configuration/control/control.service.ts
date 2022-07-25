import { Injectable } from '@angular/core';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {AppService} from "../../app.service";
import * as _ from 'lodash-es'

@Injectable()
export class ControlService {

    constructor(
        private $http:J4careHttpService,
        private appService:AppService
    ) { }

    fetchStatus = (url?) => this.$http.get(this.getUrl(url, "status"));
    startArchive = (url?) => this.$http.post(this.getUrl(url, "start"), {});
    stopArchive = (url?) => this.$http.post(this.getUrl(url, "stop"), {});
    reloadArchive = (url?) => this.$http.post(this.getUrl(url, "reload"), {});


    removeSlashOnTheEndOfUrl(url:string){
        if(url && url != "" && url.slice(-1) === "/"){
            return url.slice(0, -1);
        }
        return url;
    }

    getUrl(url, mode){
        if(url){
            return `${this.removeSlashOnTheEndOfUrl(url)}/dcm4chee-arc/ctrl/${mode}`.replace("/dcm4chee-arc/dcm4chee-arc","/dcm4chee-arc");
        }else{
            return `${j4care.addLastSlash(this.appService.baseUrl)}ctrl/${mode}`;
        }
    }
    getTableSchema(){
        return [
            {
                title:"&nbsp;",
                code:"actions",
                pxWidth:123,
                calculatedWidth:"20%"
            },
            {
                title:$localize `:@@device_name:Device name`,
                code:"dcmuiDeviceURLName",
                description:$localize `:@@control.archive_device_name:Archive device name`,
                widthWeight:1,
                calculatedWidth:"20%"
            },
            {
                title:$localize `:@@device_description:Device description`,
                code:"dicomDescription",
                description:$localize `:@@control.archive_device_description:Archive device description`,
                widthWeight:3,
                calculatedWidth:"20%"
            },
            {
                title:$localize `:@@manufacturer:Manufacturer`,
                code:"dicomManufacturer",
                description:$localize `:@@manufacturer:Manufacturer`,
                widthWeight:1,
                calculatedWidth:"20%"
            },
            {
                title:$localize `:@@model_name:Model name`,
                code:"dicomManufacturerModelName",
                description:$localize `:@@manufacturer_model_name:Manufacturer model name`,
                widthWeight:1,
                calculatedWidth:"20%"
            },
            {
                title:$localize `:@@primary_device_type:Primary device type`,
                code:"dicomPrimaryDeviceType",
                widthWeight:1,
                calculatedWidth:"20%"
            },
            {
                title:$localize `:@@software_version:Software version`,
                code:"dicomSoftwareVersion",
                widthWeight:1,
                calculatedWidth:"20%"
            }
        ]
    }
    getMyArchivesFromConfig($this, allDevices, callBack){
        let devices = {};
        try{
            let config = this.appService.global.uiConfig.dcmuiDeviceClusterObject.filter(cluster=>{
                let check = false;
                cluster.dcmuiDeviceClusterDevices.forEach(device=>{
                    if(device === this.appService.archiveDeviceName || device.dcmuiDeviceURLName === this.appService.archiveDeviceName)
                        check = true;
                });
                return check;
            })[0];
            config.dcmuiDeviceClusterDevices.forEach((deviceName,i)=>{
                this.appService.global.uiConfig.dcmuiDeviceURLObject.forEach(deviceObject=>{
                    if(deviceObject.dcmuiDeviceURLName === deviceName || deviceObject.dcmuiDeviceURLName === deviceName.dcmuiDeviceURLName){
                        devices[deviceObject.dcmuiDeviceURLName] = deviceObject;
                    }
                });
            });
        }catch (e) {
            devices = this.getArchiveDevices(devices);
        }
        allDevices.forEach(device=>{
            if(_.hasIn(devices,device.dicomDeviceName)){
                devices[device.dicomDeviceName] = devices[device.dicomDeviceName] || {};
                Object.assign(devices[device.dicomDeviceName], device);
            }
        });
        callBack.call($this, devices);
    }
    getArchiveDevices(devices){
        try{
            if(j4care.is(this.appService,"dcm4cheeArcConfig.hasMoreThanOneBaseUrl",true)){
                const dcm4cheeArcConfig = _.get(this.appService,"dcm4cheeArcConfig");
                _.get(dcm4cheeArcConfig,"dcm4chee-arc-urls").forEach(deviceUrl=>{
                    const deviceName = dcm4cheeArcConfig.deviceNameUrlMap[deviceUrl];
                    devices[deviceName] = {
                        dcmuiDeviceURLName:deviceName,
                        dcmuiDeviceURL: deviceUrl
                    }
                });
            }else if(this.appService && (this.appService.archiveDeviceName || _.hasIn(this.appService,"archiveDevice.dicomDeviceName"))){
                const deviceName = this.appService.archiveDeviceName || _.get(this.appService,"archiveDevice.dicomDeviceName");
                devices[deviceName] = {
                    dcmuiDeviceURLName:deviceName
                }
            }
        }catch (e){
            console.error(e);
        }
        return devices;
    }
}
