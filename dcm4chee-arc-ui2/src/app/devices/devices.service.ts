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
    changeAetOnClone(device){
        if (_.hasIn(device, 'dicomNetworkAE') && _.size(device.dicomNetworkAE) > 0){
            _.forEach(device.dicomNetworkAE, (m, i) => {
                console.log('m', m);
                console.log('i', i);
                if (_.hasIn(m, 'dicomAETitle')){
                    if (_.endsWith(m.dicomAETitle, '_CLONE')){
                        m.dicomAETitle = m.dicomAETitle + '(1)';
                    }else{
                        if (_.endsWith(m.dicomAETitle, ')')){

                            let split = _.split(m.dicomAETitle,  '(');
                            let index = _.last(split);
                            split.pop();
                            index = _.replace(index, ')', '');
                            let indexInt = _.parseInt(index);
                            console.log('index', index);
                            console.log('index', indexInt);
                            m.dicomAETitle = split + '(' + _.add(indexInt, 1) + ')';
                        }else{

                            m.dicomAETitle = m.dicomAETitle + '_CLONE';
                        }
                    }

                }
            });
        }

    }
}
