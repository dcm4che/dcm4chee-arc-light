import { Injectable } from '@angular/core';
import * as _ from 'lodash';
import {WindowRefService} from "../../helpers/window-ref.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {ConfirmComponent} from "../../widgets/dialogs/confirm/confirm.component";
import { MatDialog, MatDialogConfig, MatDialogRef } from "@angular/material/dialog";
import {HttpHeaders} from "@angular/common/http";
import {SelectDropdown} from "../../interfaces";
import { loadTranslations } from '@angular/localize';

@Injectable()
export class DevicesService {
    headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    dialogRef: MatDialogRef<any>;

    constructor(
        private $http:J4careHttpService,
        public dialog: MatDialog,
        public config: MatDialogConfig
    ) { }

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
    changeWebAppOnClone(device,aes){
        if (_.hasIn(device, 'dcmDevice.dcmWebApp') && _.size(device.dcmDevice.dcmWebApp) > 0){
            _.forEach(device.dcmDevice.dcmWebApp, (m, i) => {
                if (_.hasIn(m, 'dcmWebAppName')){
                    m.dcmWebAppName = this.generateNewTitle(m.dcmWebAppName, aes, "dcmWebAppName");
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
    createDevice(deviceName, object){
        return  this.$http.post(`../devices/${deviceName}`, object, this.headers)
    }
    saveDeviceChanges(deviceName, object){
        return  this.$http.put(`../devices/${deviceName}`, object, this.headers)
    }
    getDevices(){
       return this.$http.get(
            '../devices'
        );
    }
    getDevice(deviceName){
       return this.$http.get(
            `../devices/${deviceName}`
        );
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

    selectDevice(callBack, devices? ,addScheduleTime?:boolean){
        let setParams = function(tempDevices){
            let schema:any = {
                content: $localize `:@@devices.select_device_to_reschedule:Select device if you want to reschedule to an other device`,
                doNotSave:true,
                form_schema:[
                    [
                        [
                            {
                                tag:"label",
                                text:"Device"
                            },
                            {
                                tag:"select",
                                options:tempDevices,
                                showStar:true,
                                filterKey:"newDeviceName",
                                description:$localize `:@@device:Device`,
                                placeholder:$localize `:@@device:Device`
                            }
                        ]
                    ]
                ],
                result: {
                    schema_model: {
                        newDeviceName:''
                    }
                },
                saveButton: 'SUBMIT'
            };
            if(addScheduleTime){
                schema.form_schema[0][0].push([
                    {
                        tag:"label",
                        text:$localize `:@@devices.scheduled_time:Scheduled Time`
                    }
                    ,{
                        tag:"range-picker-time",
                        type:"text",
                        filterKey:"scheduledTime",
                        description:$localize `:@@devices.scheduled_time:Scheduled Time`
                    }
                ]);
            }
            return schema;
        };

        if(devices){
            this.openDialog(setParams(devices)).subscribe(callBack);
        }else{
            this.getDevices().subscribe((devices)=>{
                devices = devices.map(device=>{
                    return {
                        text:device.dicomDeviceName,
                        value:device.dicomDeviceName
                    }
                });
                this.openDialog(setParams(devices)).subscribe(callBack);
            },(err)=>{

            });
        }

    }

    openDialog(parameters, width?, height?){
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: height || 'auto',
            width: width || '500px'
        });
        this.dialogRef.componentInstance.parameters = parameters;
        return this.dialogRef.afterClosed();
    };

    getFiltersSchema(){
        return j4care.prepareFlatFilterObject([
            {
                tag:"input",
                type:"text",
                filterKey:"dicomDeviceName",
                description:$localize `:@@device_name:Device name`,
                placeholder:$localize `:@@device_name:Device name`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomDeviceDescription",
                description:$localize `:@@device_description:Device description`,
                placeholder:$localize `:@@device_description:Device description`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomManufacturer",
                description:$localize `:@@devices.manufacturer:Manufacturer`,
                placeholder:$localize `:@@devices.manufacturer:Manufacturer`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomManufacturerModelName",
                description:$localize `:@@manufacturer_model_name:Manufacturer model name`,
                placeholder:$localize `:@@manufacturer_model_name:Manufacturer model name`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomSoftwareVersion",
                description:$localize `:@@software_version:Software version`,
                placeholder:$localize `:@@software_version:Software version`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomStationName",
                description:$localize `:@@devices.station_name:Station name`,
                placeholder:$localize `:@@devices.station_name:Station name`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomPrimaryDeviceType",
                description:$localize `:@@primary_device_type:Primary device type`,
                placeholder:$localize `:@@primary_device_type:Primary device type`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomInstitutionName",
                description:$localize `:@@devices.institution_name:Institution name`,
                placeholder:$localize `:@@devices.institution_name:Institution name`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomInstitutionDepartmentName",
                description:$localize `:@@devices.institution_department_name:Institution department name`,
                placeholder:$localize `:@@devices.institution_department_name:Institution department name`
            },{
                tag:"select",
                options:[
                    new SelectDropdown("true",$localize `:@@installed:Installed`),
                    new SelectDropdown("false",$localize `:@@not_installed:Not installed`),
                ],
                showStar:true,
                filterKey:"dicomInstalled",
                description:$localize `:@@devices.device_installed:Device installed`,
                placeholder:$localize `:@@installed:Installed`
            },
            {
                tag: "button",
                id: "submit",
                text: $localize `:@@SUBMIT:SUBMIT`,
                description: $localize `:@@devices.query_devices:Query Devices`
            }
        ],2)
    }
}
