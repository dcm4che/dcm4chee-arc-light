import { Injectable } from '@angular/core';
import * as _ from 'lodash';

@Injectable()
export class DevicesService {

    constructor() { }

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
                    m.dicomAETitle = this.getNewAETitle(m.dicomAETitle, aes);
                }
            });
        }

    }
    getNewAETitle(dicomAETitle, aes){
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
        if(aes && _.findIndex(aes, function(o) { return (_.hasIn(o,"dicomAETitle") && o["dicomAETitle"] == newAeTitle); }) > -1){
            return this.getNewAETitle(newAeTitle,aes);
        }else{
            return newAeTitle;
        }
    }
}
