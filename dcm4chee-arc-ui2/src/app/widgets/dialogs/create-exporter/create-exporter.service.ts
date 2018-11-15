import { Injectable } from '@angular/core';
import {Http} from '@angular/http';
import {WindowRefService} from "../../../helpers/window-ref.service";
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {j4care} from "../../../helpers/j4care.service";
import {DeviceConfiguratorService} from "../../../configuration/device-configurator/device-configurator.service";

@Injectable()
export class CreateExporterService {

    constructor(
        private $http:J4careHttpService,
        private deviceConfiguratorService:DeviceConfiguratorService
    ) { }

    getDevice = (deviceName) => this.deviceConfiguratorService.getDevice(deviceName);

    getQueue = () => this.$http.get('../queue').map(res => j4care.redirectOnAuthResponse(res));

    getExporterDescriptorSchema = () => this.$http.get('./assets/schema/exporter.schema.json').map(res => j4care.redirectOnAuthResponse(res));

}
