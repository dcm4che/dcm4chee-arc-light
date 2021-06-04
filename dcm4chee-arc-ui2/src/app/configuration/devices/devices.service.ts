import { Injectable } from '@angular/core';
import * as _ from 'lodash-es';
import {WindowRefService} from "../../helpers/window-ref.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {ConfirmComponent} from "../../widgets/dialogs/confirm/confirm.component";
import { MatDialog, MatDialogConfig, MatDialogRef } from "@angular/material/dialog";
import {HttpHeaders} from "@angular/common/http";
import {SelectDropdown} from "../../interfaces";
import { loadTranslations } from '@angular/localize';
import {AppService} from "../../app.service";
import {map, switchMap} from "rxjs/operators";
import {AeListService} from "../ae-list/ae-list.service";

@Injectable()
export class DevicesService {
    headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    dialogRef: MatDialogRef<any>;

    constructor(
        private $http:J4careHttpService,
        public dialog: MatDialog,
        public config: MatDialogConfig,
        private appService: AppService
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
                    m.dicomAETitle = this.generateNewAETitle(m.dicomAETitle, 1, 0, aes, "dicomAETitle");
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
        return  this.$http.post(`${j4care.addLastSlash(this.appService.baseUrl)}devices/${deviceName}`, object, this.headers)
    }
    saveDeviceChanges(deviceName, object){
        return  this.$http.put(`${j4care.addLastSlash(this.appService.baseUrl)}devices/${deviceName}`, object, this.headers)
    }
    getDevices(){
       return this.$http.get(
            `${j4care.addLastSlash(this.appService.baseUrl)}devices`
        );
    }
    getDevice(deviceName){
       return this.$http.get(
            `${j4care.addLastSlash(this.appService.baseUrl)}devices/${deviceName}`
        );
    }
    cloneVendorData(oldDeviceName, newDeviceName){
        return this.$http.get(`${j4care.addLastSlash(this.appService.baseUrl)}devices/${oldDeviceName.trim()}/vendordata`,{
            responseType: "blob"
        }).pipe(switchMap((vendorData:Blob)=>this.$http.put(`${j4care.addLastSlash(this.appService.baseUrl)}devices/${newDeviceName.trim()}/vendordata`, vendorData)));
    }
    getAes(){
        return this.$http.get(`${j4care.addLastSlash(this.appService.baseUrl)}aes`);
    }
    generateNewTitle(oldTitle, nodes, titleName){
        let newTitle;
        if (_.endsWith(oldTitle, '_CLONE'))
            newTitle = oldTitle + '(1)';
        else {
            if (_.endsWith(oldTitle, ')')) {
                let split = _.split(oldTitle,  '(');
                let index = _.last(split);
                split.pop();
                index = _.replace(index, ')', '');
                let indexInt = _.parseInt(index);
                newTitle = split + '(' + _.add(indexInt, 1) + ')';
            } else
                newTitle = oldTitle + '_CLONE';
        }
        return this.nodeExists(newTitle, nodes, titleName)
                ? this.generateNewTitle(newTitle, nodes, titleName)
                : newTitle;
    }

    generateNewAETitle(oldAETitle, sliceUpto, existsCounter, aes, titleName){
        let newAETitle;
        if (oldAETitle.length > 16)
            newAETitle = this.newAETitleFrom(oldAETitle, 15);
        else {
            let lastChar = _.parseInt(oldAETitle.slice(-1));
            let lastCharIsNotDigit = _.isNaN(lastChar);
            newAETitle = oldAETitle.length < 16
                        ? lastCharIsNotDigit
                            ? oldAETitle + 1
                            : oldAETitle.slice(0, -1) + (lastChar + 1)
                        : lastCharIsNotDigit
                            ? oldAETitle.slice(0, -1) + 1
                            : lastChar < 9
                                ? oldAETitle.slice(0, -1) + (lastChar + 1)
                                : this.newAETitleFrom(oldAETitle, 15);
        }

        let aeExists = this.nodeExists(newAETitle, aes, titleName);
        if (aeExists && newAETitle.length == 16 && existsCounter == 9) {
            sliceUpto = sliceUpto + 1;
            newAETitle = this.newAETitleFrom(oldAETitle, 16 - sliceUpto);
        }
        return aeExists
                    ? this.generateNewAETitle(newAETitle, sliceUpto, existsCounter, aes, titleName)
                    : newAETitle;
    }

    newAETitleFrom(oldAETitle, substringUpto) {
        let trimmedOldAETitle = oldAETitle.substring(0, substringUpto);
        let lastChar = _.parseInt(trimmedOldAETitle.slice(-1));
        let lastCharIsNotDigit = _.isNaN(lastChar);
        return lastCharIsNotDigit
                ? trimmedOldAETitle + 1
                : trimmedOldAETitle.slice(0, -1) + (lastChar + 1);
    }

    nodeExists(nodeTitle, nodes, titleName) {
        return nodes &&
                    _.findIndex(nodes, function(o)
                    {
                        return (_.hasIn(o,titleName) && o[titleName] == nodeTitle);
                    })
                    > -1;
    }

    selectParameters(callBack, devices? , addScheduleTime?:boolean, addQueueName?:boolean, queueNames?:SelectDropdown<string>[], title?:string){
        let setParams = function(tempDevices){
            let schema:any = {
                content: title || $localize `:@@title.reschedule:Reschedule`,
                doNotSave:true,
                form_schema:[
                    [
                        [
                            {
                                tag:"label",
                                text:$localize `:@@device:Device`
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
                saveButton: $localize `:@@SUBMIT:SUBMIT`
            };
            if(addScheduleTime){
                schema.form_schema[0].push([
                    {
                        tag:"label",
                        text:$localize `:@@scheduled_time:Scheduled Time`
                    }
                    ,{
                        tag:"single-date-time-picker",
                        type:"text",
                        filterKey:"scheduledTime",
                        description:$localize `:@@scheduled_time:Scheduled Time`
                    }
                ]);
            }
            if(addQueueName){
                const options:SelectDropdown<string>[] = queueNames || <SelectDropdown<string>[]> Array.from(Array(13).keys()).map(i=>{
                    const val = `Retrieve${i+1}`;
                    return new SelectDropdown(val,val);
                });
                schema.form_schema[0].push([
                    {
                        tag:"label",
                        text:$localize `:@@queue_name:Queue Name`
                    }
                    ,{
                        tag:"select",
                        options:options,
                        filterKey:"newQueueName",
                        description:$localize `:@@queue_name:Queue Name`,
                        placeholder:$localize `:@@queue_name:Queue Name`

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
                description:$localize `:@@manufacturer:Manufacturer`,
                placeholder:$localize `:@@manufacturer:Manufacturer`
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
                description:$localize `:@@station_name:Station Name`,
                placeholder:$localize `:@@station_name:Station Name`
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
                description:$localize `:@@institution_name:Institution Name`,
                placeholder:$localize `:@@institution_name:Institution Name`
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
                description: $localize `:@@query_devices:Query Devices`
            }
        ],2)
    }
}
