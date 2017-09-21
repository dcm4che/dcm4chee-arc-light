import { Injectable } from '@angular/core';
import * as _ from 'lodash';
import {WindowRefService} from "../helpers/window-ref.service";
import {Http} from "@angular/http";

@Injectable()
export class DevicesService {

    constructor(private $http:Http) { }

    appendExporterToDevice(device, exporter){
        device.dcmArchiveDevice = device.dcmArchiveDevice || {};
        device.dcmArchiveDevice.dcmExporter = device.dcmArchiveDevice.dcmExporter || [];
        device.dcmArchiveDevice.dcmExporter.push(exporter);
        return device;
    }
    changeAetOnClone(device,aes){
        if (_.hasIn(device, 'dicomNetworkAE') && _.size(device.dicomNetworkAE) > 0){
            _.forEach(device.dicomNetworkAE, (m, i) => {
                if (_.hasIn(m, 'dicomAETitle')){
                    m.dicomAETitle = this.getNewTitle(m.dicomAETitle, aes, "dicomAETitle");
                }
            });
        }
    }
    changeHl7ApplicationNameOnClone(device,hl7){
        if (_.hasIn(device, 'dcmDevice.hl7Application') && _.size(device.dcmDevice.hl7Application) > 0){
            _.forEach(device.dcmDevice.hl7Application, (m, i) => {
                if (_.hasIn(m, 'hl7ApplicationName')){
                    m.hl7ApplicationName = this.getNewTitle(m.hl7ApplicationName, hl7, "hl7ApplicationName");
                }
            });
        }
    }
    getDevices(){
       return this.$http.get(
            '../devices'
        ).map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});

    }
    getNewTitle(dicomAETitle, aes, titleName){
        let newAeTitle;
        if (_.endsWith(dicomAETitle, '_CLONE')){
            newAeTitle = dicomAETitle + '(1)';
        }else{
            if (_.endsWith(dicomAETitle, ')')){

                let split = _.split(dicomAETitle,  '(');
                let index = _.last(split);
                split.pop();
                index = _.replace(index, ')', '');
                let indexInt = _.parseInt(index);
                newAeTitle = split + '(' + _.add(indexInt, 1) + ')';
            }else{

                newAeTitle = dicomAETitle + '_CLONE';
            }
        }
        if(aes && _.findIndex(aes, function(o) { return (_.hasIn(o,titleName) && o[titleName] == newAeTitle); }) > -1){
            return this.getNewTitle(newAeTitle, aes, titleName);
        }else{
            return newAeTitle;
        }
    }
}
