import { Component, OnInit } from '@angular/core';
import {MatLegacyDialogRef as MatDialogRef} from "@angular/material/legacy-dialog";
import * as _ from 'lodash-es';
import {j4care} from "../../../helpers/j4care.service";
import {AppService} from "../../../app.service";

@Component({
  selector: 'app-device-clone',
  templateUrl: './device-clone.component.html',
  styleUrls: ['./device-clone.component.scss']
})
export class DeviceCloneComponent implements OnInit {
    validationDetailMessage:String = "";
    toggle:ToggleBlock = "name";
    device;
    clonedDevice;
    _ = _;
    j4care = j4care;
    suffixPrefix;
    previousAetState = {};
    texts = {
        use_this_prefix_suffix: $localize `:@@use_this_prefix_suffix:Use this Suffix ( You can use * to make more complex Prefix and Suffix )`,
        device_base_data: $localize `:@@device_base_data:Device base data`,
        current_device: $localize `:@@current_device:Current Device`,
        clone: $localize `:@@clone:Clone`,
        device_name: $localize `:@@device_name:Device Name`,
        dicom_network_connection: $localize `:@@dicom_network_connection:Dicom Network Connection`,
        network_connection_name: $localize `:@@network_connection_name:Network Connection Name`,
        hostname: $localize `:@@hostname:Hostname`,
        port: $localize `:@@port:Port`,
        network_aes: $localize `:@@network_aes:Network AEs`,
        ae_title: $localize `:@@ae_title:AE Title`,
        network_connection_reference: $localize `:@@network_connection_reference:Network Connection Reference`,
        web_apps: $localize `:@@web_apps:Web Apps`,
        web_app_name: $localize `:@@web_app_name:Web App Name`,
        web_service_path: $localize `:@@web_service_path:Web Service Path`,
        hl7_applications: $localize `:@@hl7_applications:Hl7 Applications`,
        hl7_application_name: $localize `:@@hl7_application_name:Hl7 Application Name`
    };

    keys = [
        {
            path:'dicomNetworkAE',
            name:'dicomAETitle'
        },
        {
            path:'dcmDevice.dcmWebApp',
            name:'dcmWebAppName'
        },
        {
            path:'dcmDevice.hl7Application',
                name:'hl7ApplicationName'
        }
    ];

    constructor(
        public dialogRef: MatDialogRef<DeviceCloneComponent>,
        private appService: AppService
    ) { }

    ngOnInit(): void {
        this.clonedDevice = _.cloneDeep(this.device);
    }

    networkReferenceMatchNetworkConnection(reff, connIndex){
        ///dicomNetworkConnection/1

    }


    clone(){
        if(this.valid()){
            this.dialogRef.close(this.clonedDevice);
        }else{
            this.appService.showError($localize `:@@make_sure_the_names_are_unique_on_clone:You have to make sure that the names of the new device, AETs, Web Applications and HL7 are not the same as the current device ( You can use the Prefix/Suffix input field ) ${this.validationDetailMessage}`)
        }
    }

    valid(){
        try{
            let valid:boolean = true;
            if(this.device.dicomDeviceName === this.clonedDevice.dicomDeviceName){
                valid = valid && false;
            }
            this.keys.forEach(key=>{
                if(_.get(this.device, key.path)){
                    _.get(this.device, key.path).forEach((part,i)=>{
                       if(_.get(this.clonedDevice, `${key.path}[${i}][${key.name}]`) === part[key.name]){
                            console.groupCollapsed("Following part was not changed in the clone object:");
                            console.log("In path:",key.path);
                            console.log("In original device:",_.get(this.clonedDevice, `${key.path}[${i}][${key.name}]`));
                            console.log("In clone device:",part[key.name]);
                            console.groupEnd();
                            this.validationDetailMessage = this.validationDetailMessage || $localize `:@@value_in_path_was_not_changed: <br/>(${_.get(this.clonedDevice, `${key.path}[${i}][${key.name}]`)}:@@value: in ${key.path}:@@path:, was not changed)`;
                            valid = valid && false;
                        }else{
                            valid = valid && true;
                        }
                    });
                }
            });
            return valid;
        }catch (e) {
            console.error(e);
            return false;
        }
    }

    onConnectionReffChange(aet, i, e){
        try{
            if(e.target.checked){
                aet.dicomNetworkConnectionReference.push(`/dicomNetworkConnection/${i}`);
            }else{
                const index = aet.dicomNetworkConnectionReference.indexOf(`/dicomNetworkConnection/${i}`);
                if(index > -1){
                    aet.dicomNetworkConnectionReference.splice(index,1);
                }

            }
        }catch (e){
            j4care.log("trying to toggle netwock reference",e);
        }
    }

    onSufficePrefixChange(){
        console.log("this.suffixPrefix", this.suffixPrefix);
        this.keys.forEach(key=>{
            _.get(this.device, key.path).forEach((part,i)=>{
                if(this.suffixPrefix && this.suffixPrefix.indexOf("*") > -1){
                    _.set(this.clonedDevice, `${key.path}[${i}][${key.name}]`, this.suffixPrefix.toUpperCase().replace(/\*/i, part[key.name]));
                }else{
                    _.set(this.clonedDevice, `${key.path}[${i}][${key.name}]`, _.get(this.device,`${key.path}[${i}][${key.name}]`) + this.suffixPrefix.toUpperCase());
                }
                if(key.path === "dicomNetworkAE"){
                    this.onAetChange( i, _.get(this.clonedDevice, `${key.path}[${i}]`));
                }
            });
        });
        if(this.suffixPrefix && this.suffixPrefix.indexOf("*") > -1){
            this.clonedDevice.dicomDeviceName = this.suffixPrefix.replace(/\*/i, this.device.dicomDeviceName);
        }else{
            this.clonedDevice.dicomDeviceName = this.device.dicomDeviceName + this.suffixPrefix;
        }
    }

    onAetChange( i, aet){
        try{
            const oldAet = _.get(this.device,`dicomNetworkAE[${i}].dicomAETitle`);
            const clonedOldAet = _.get(this.clonedDevice,`dicomNetworkAE[${i}].dicomAETitle`);
            const newAet = aet.dicomAETitle;
            const regex = new RegExp(`/${oldAet}/`, 'gm');
            const clonedRegex = new RegExp(`/${clonedOldAet}/`, 'gm');
            let prevRegex;
            let prevAet;
            if(this.previousAetState[i]){
                prevAet = this.previousAetState[i];
                prevRegex = new RegExp(`/${prevAet}/`, "gm");
            }
            this.clonedDevice.dcmDevice.dcmWebApp.forEach(webApp=>{
                if(webApp.dcmWebServicePath.indexOf(`/${oldAet}/`) > -1  || webApp.dcmWebServicePath.indexOf(`/${clonedOldAet}/`) > -1 || (prevAet && webApp.dcmWebServicePath.indexOf(`/${prevAet}/`) > -1)){
                    if(clonedRegex.test(webApp.dcmWebServicePath)){
                        webApp.dcmWebServicePath = webApp.dcmWebServicePath.replace(clonedRegex, `/${newAet}/`);
                        this.replaceOtherAetRef(clonedOldAet, newAet);
                    };
                    if(regex.test(webApp.dcmWebServicePath)){
                        webApp.dcmWebServicePath = webApp.dcmWebServicePath.replace(regex, `/${newAet}/`);
                        this.replaceOtherAetRef(oldAet, newAet);
                    };
                    if(this.previousAetState[i] && prevRegex && prevRegex.test(webApp.dcmWebServicePath)){
                        webApp.dcmWebServicePath = webApp.dcmWebServicePath.replace(prevRegex, `/${newAet}/`);
                        this.replaceOtherAetRef(prevAet, newAet);
                    };
                    this.previousAetState[i] = newAet;
                }
            });
        }catch (e) {
            
        }
    }

    replaceOtherAetRef(currentAet,newAet){
        j4care.traverse(this.clonedDevice, (value, key, object)=>{
            if(value === currentAet){
                object[key] = newAet;
            }
            return object[key];
        });
    }
}

export type ToggleBlock = "name" | "connections" | "aets" | "webapps" | "hl7";
