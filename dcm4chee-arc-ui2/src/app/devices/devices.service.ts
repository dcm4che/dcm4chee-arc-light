import { Injectable } from '@angular/core';
import * as _ from 'lodash';
import {WindowRefService} from "../helpers/window-ref.service";
import {Http} from "@angular/http";
import {J4careHttpService} from "../helpers/j4care-http.service";

@Injectable()
export class DevicesService {

    constructor(private $http:J4careHttpService) { }

    appendExporterToDevice(device, exporter){
        device.dcmDevice = device.dcmDevice || {};
        device.dcmDevice.dcmArchiveDevice = device.dcmDevice.dcmArchiveDevice || {};
        device.dcmDevice.dcmArchiveDevice.dcmExporter = device.dcmDevice.dcmArchiveDevice.dcmExporter || [];
        device.dcmDevice.dcmArchiveDevice.dcmExporter.push(this.removeEmptyFieldsFromExporter(exporter));
        return device;
    }
    removeEmptyFieldsFromExporter(exporter){
        _.forEach(exporter,(m,i)=>{
            if(m === "" || m === undefined){
                delete exporter[i];
            }
        });
        return exporter;
    }
    changeAetOnClone(device,aes){
        if (_.hasIn(device, 'dicomNetworkAE') && _.size(device.dicomNetworkAE) > 0){
            _.forEach(device.dicomNetworkAE, (m, i) => {
                if (_.hasIn(m, 'dicomAETitle')){
                    m.dicomAETitle = this.generateNewTitle(m.dicomAETitle, aes, "dicomAETitle");
                }
            });
        }
    }
    changeHl7ApplicationNameOnClone(device,hl7){
        if (_.hasIn(device, 'dcmDevice.hl7Application') && _.size(device.dcmDevice.hl7Application) > 0){
            _.forEach(device.dcmDevice.hl7Application, (m, i) => {
                if (_.hasIn(m, 'hl7ApplicationName')){
                    m.hl7ApplicationName = this.generateNewTitle(m.hl7ApplicationName, hl7, "hl7ApplicationName");
                }
            });
        }
    }
    getDevices(){
       return this.$http.get(
            '../devices'
        ).map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});
    }
    generateNewTitle(oldTitle, aes, titleName){
        let newTitle;
        if (_.endsWith(oldTitle, '_CLONE')){
            newTitle = oldTitle + '(1)';
        }else{
            if (_.endsWith(oldTitle, ')')){
                let split = _.split(oldTitle,  '(');
                let index = _.last(split);
                split.pop();
                index = _.replace(index, ')', '');
                let indexInt = _.parseInt(index);
                newTitle = split + '(' + _.add(indexInt, 1) + ')';
            }else{
                newTitle = oldTitle + '_CLONE';
            }
        }
        if(aes && _.findIndex(aes, function(o) { return (_.hasIn(o,titleName) && o[titleName] == newTitle); }) > -1){
            return this.generateNewTitle(newTitle, aes, titleName);
        }else{
            return newTitle;
        }
    }
}
