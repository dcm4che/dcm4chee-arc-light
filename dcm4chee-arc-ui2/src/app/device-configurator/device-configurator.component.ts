import {Component, OnInit, OnDestroy} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {FormElement} from '../helpers/form/form-element';
import {DeviceConfiguratorService} from './device-configurator.service';
import {Http} from '@angular/http';
import * as _ from 'lodash';
import {Observable} from 'rxjs';
import {AppService} from '../app.service';
import {ControlService} from '../control/control.service';
import {SlimLoadingBarService} from 'ng2-slim-loading-bar';
import {WindowRefService} from "../helpers/window-ref.service";
import {HttpErrorHandler} from "../helpers/http-error-handler";
import {AeListService} from "../ae-list/ae-list.service";
import {Hl7ApplicationsService} from "../hl7-applications/hl7-applications.service";
import {DevicesService} from "../devices/devices.service";
import {J4careHttpService} from "../helpers/j4care-http.service";

@Component({
  selector: 'app-device-configurator',
  templateUrl: './device-configurator.component.html'
})
export class DeviceConfiguratorComponent implements OnInit, OnDestroy {
    formObj: FormElement<any>[];
    model;
    device;
    schema;
    showform;
    params = [];
    recentParams;
    inClone;
    pressedKey = [];
    submitValue;
    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private service: DeviceConfiguratorService,
        private $http:J4careHttpService,
        private mainservice: AppService,
        private controlService: ControlService,
        private cfpLoadingBar: SlimLoadingBarService,
        private httpErrorHandler:HttpErrorHandler,
        private aeService:AeListService,
        private hl7Service:Hl7ApplicationsService,
        private devicesService:DevicesService
    ) { }
    addModel(){
        let explod = this.params['device'].split('|');
        console.log('explod', explod);
        this.model = this.device[explod[1]];
        console.log('this.model', this.model);

    }
    submitFunction(value){
        let extensionAdded = false;
        let form;
        console.log('in submit');
        let $this = this;
        this.cfpLoadingBar.start();
        let deviceClone = _.cloneDeep(this.service.device);

        if(this.inClone){
            let clonePart =  _.cloneDeep(_.get(this.service.device, this.recentParams.clone));
            this.service.replaceOldAETitleWithTheNew(clonePart,value.dicomAETitle);
            _.set(this.service.device,  this.recentParams.devicereff,  clonePart);
        }
        this.service.addChangesToDevice(value, this.recentParams.devicereff);
        if (_.hasIn(this.recentParams, 'schema')){
            let newSchema = this.service.getSchemaFromPath(this.service.schema, this.recentParams['schema']);
            let title = this.service.getPaginationTitleFromModel(value, newSchema);
            this.service.pagination[this.service.pagination.length - 1].title = title;

            //Adding archive extension to the network ae if the device has archive extension
            if(
                _.hasIn(this.service.device,"dcmArchiveDevice") &&
                this.recentParams.schema === "properties.dicomNetworkAE" &&
                _.hasIn(this.service.device,"dicomNetworkAE") &&
                this.service.device.dicomNetworkAE.length > 0
            ){
                _.forEach(this.service.device.dicomNetworkAE, (m,i)=>{
                    if(!_.hasIn(m,"dcmArchiveNetworkAE")){
                        this.service.device.dicomNetworkAE[i]["dcmArchiveNetworkAE"] = {};
                        extensionAdded = true;
                    }
                });
            }
        }
        if (_.hasIn(this.service.pagination, '[1].title') && this.service.pagination[1].title === '[new_device]'){
            let creatDevice = this.service.createDevice();
            if (creatDevice){
                creatDevice
                    .subscribe(
                        (success) => {
                            console.log('succes', success);
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'Device created successfully!',
                                'status': 'info'
                            });
                            try {
                                $this.recentParams = {};
                                $this.service.pagination = $this.params = [
                                    {
                                        url: '/device/devicelist',
                                        title: 'devicelist',
                                        devicereff: undefined
                                    }
                                ];
                            }catch (e){
                                console.warn('error on chagning pagination', e);
                            }
                            $this.controlService.reloadArchive().subscribe((res) => {
                                console.log('res', res);
                                // $this.message = 'Reload successful';
                                $this.mainservice.setMessage({
                                    'title': 'Info',
                                    'text': 'Reload successful',
                                    'status': 'info'
                                });
                                    $this.cfpLoadingBar.complete();
                            }, (err) => {
                                $this.cfpLoadingBar.complete();
                                }
                            );
                            setTimeout(() => {
                                $this.router.navigateByUrl(`/device/edit/${value.dicomDeviceName}`);
                            }, 200);
                        },
                        (err) => {
                            _.assign($this.service.device, deviceClone);
                            console.log('error', err);
                            $this.httpErrorHandler.handleError(err);
                            $this.cfpLoadingBar.complete();
                        }

                    );
            }else{
                _.assign($this.service.device, deviceClone);
                console.warn('devicename is missing', this.service.device);
                $this.mainservice.setMessage({
                    'title': 'Error',
                    'text': 'Device name is missing!',
                    'status': 'error'
                });
            }
        }else{
            let updateDevice = this.service.updateDevice();
            if (updateDevice){
                updateDevice
                    .subscribe(
                        (success) => {
                            console.log('succes', success);
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'Device saved successfully!',
                                'status': 'info'
                            });
                            if(extensionAdded){
                                // $this.setFormFromParameters($this.recentParams, form);
                                $this.deleteForm();
                                $this.showform = false;
                                let url = window.location.hash.substr(1);
                                if(url){
                                    setTimeout(() => {
                                        $this.router.navigateByUrl('blank').then(() => {
                                            $this.router.navigateByUrl(url);
                                            $this.showform = true;
                                        });
                                    });
                                }
                            }
                            $this.controlService.reloadArchive().subscribe((res) => {
                                console.log('res', res);
                                // $this.message = 'Reload successful';
                                $this.mainservice.setMessage({
                                    'title': 'Info',
                                    'text': 'Reload successful',
                                    'status': 'info'
                                });
                                    $this.cfpLoadingBar.complete();
                            }, (err) => {

                                    $this.cfpLoadingBar.complete();
                                }
                            );
                            $this.refreshExternalRefferences();
                        },
                        (err) => {
                            _.assign($this.service.device, deviceClone);
                            console.log('error', err);
                            $this.httpErrorHandler.handleError(err);
                            $this.cfpLoadingBar.complete();
                        }

                    );
            }else{
                _.assign($this.service.device, deviceClone);
                $this.mainservice.setMessage({
                    'title': 'Error',
                    'text': 'Device name is missing!',
                    'status': 'error'
                });
                console.warn('devicename is missing', this.service.device);
                $this.cfpLoadingBar.complete();
            }
        }
    }
    refreshExternalRefferences(){
        this.getAes();
        this.getHl7ApplicationsList();
        this.getDevices();
    }
    getAes(){
        let $this = this;
        this.aeService.getAes()
            .subscribe((response) => {
                if ($this.mainservice.global && !$this.mainservice.global.aes){
                    let global = _.cloneDeep($this.mainservice.global);
                    global.aes = response;
                    $this.mainservice.setGlobal(global);
                }else{
                    if ($this.mainservice.global && $this.mainservice.global.aes){
                        $this.mainservice.global.aes = response;
                    }else{
                        $this.mainservice.setGlobal({aes: response});
                    }
                }
            }, (response) => {
                // vex.dialog.alert("Error loading aes, please reload the page and try again!");
            });
        // }
    }
    getHl7ApplicationsList(){
        let $this = this;
        this.hl7Service.getHl7ApplicationsList('').subscribe(
            (response)=>{
                if ($this.mainservice.global && !$this.mainservice.global.hl7){
                    let global = _.cloneDeep($this.mainservice.global); //,...[{hl7:response}]];
                    global.hl7 = response;
                    $this.mainservice.setGlobal(global);
                }else{
                    if ($this.mainservice.global && $this.mainservice.global.hl7){
                        $this.mainservice.global.hl7 = response;
                    }else{
                        $this.mainservice.setGlobal({hl7: response});
                    }
                }
            },
            (err)=>{
            }
        );
    }
    getDevices(){
        let $this = this;
        this.devicesService.getDevices().subscribe((response) => {
            if ($this.mainservice.global && !$this.mainservice.global.devices){
                let global = _.cloneDeep($this.mainservice.global); //,...[{devices:response}]];
                global.devices = response;
                $this.mainservice.setGlobal(global);
            }else{
                if ($this.mainservice.global && $this.mainservice.global.devices){
                    $this.mainservice.global.devices = response;
                }else{
                    $this.mainservice.setGlobal({devices: response});
                }
            }
        }, (err) => {
            // vex.dialog.alert("Error loading device names, please reload the page and try again!");
        });
        // }
    };
    ngOnInit() {
        let $this = this;
        let form;
        this.params = $this.service.pagination;
        this.inClone = false;
        $this.cfpLoadingBar.start();
        this.route.params
            .subscribe((params) => {
                if (
                    ($this.service.pagination.length < 3) // If the deepest pagination level is the device than go one
                        ||
                    (_.size(params.devicereff) < _.size($this.service.pagination[$this.service.pagination.length - 1].devicereff)) //If the user goes back allow it
                        ||
                    (
                        $this.service.pagination.length > 2 &&
                        _.hasIn($this.service.pagination, [$this.service.pagination.length - 1, 'devicereff']) &&
                        $this.service.pagination[$this.service.pagination.length - 1].devicereff &&
                        _.hasIn(this.service.device, $this.service.pagination[$this.service.pagination.length - 1].devicereff)
                    )
                ){
                $this.recentParams = params;
                // $this.service.getSchema('device.schema.json').subscribe(schema => {
                /*                $this.formObj = undefined;
                 $this.model = undefined;*/
                if (!(_.hasIn(params, 'devicereff') && _.hasIn(params, 'schema')) || !$this.service.schema) {
                    let newPaginationObject = {
                        url: '/device/edit/' + params['device'],
                        title: params['device'],
                        devicereff: '',
                    };
                    let newPaginationIndex = _.findIndex($this.service.pagination, (p) => {
                        return p.url === newPaginationObject.url;
                    });
                    if (newPaginationIndex > -1) {
                        let dropedPaginations = _.dropRight($this.service.pagination, $this.service.pagination.length - newPaginationIndex - 1);
                        $this.service.pagination = dropedPaginations;
                        $this.params = dropedPaginations;
                    } else {
                        $this.service.pagination.push(newPaginationObject);
                    }
                        if (params['device'] == '[new_device]') {
                            $this.$http.get('./assets/schema/device.schema.json').map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;}).subscribe((schema) => {
                                $this.showform = false;
                                $this.device = {};
                                $this.service.device = {};
                                $this.schema = schema;
                                $this.service.schema = schema;
                                let formObject = $this.service.convertSchemaToForm($this.device, $this.schema, params);
                                $this.formObj = formObject;
                                $this.model = {};
                                setTimeout(() => {
                                    $this.showform = true;
                                    $this.cfpLoadingBar.complete();
                                }, 1);
                            });
                        } else {

                            Observable.combineLatest(
                                $this.service.getDevice(params['device']),
                                $this.$http.get('./assets/schema/device.schema.json').map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
                            ).subscribe(deviceschema => {
                                $this.service.device = deviceschema[0];
                                $this.service.schema = deviceschema[1];
                                if (_.hasIn(params, 'devicereff') && _.hasIn(params, 'schema')){
                                    this.setFormFromParameters(params, form);
                                }else{
                                    $this.showform = false;
                                    console.log('deviceschema', deviceschema);
                                    $this.device = deviceschema[0];
                                    $this.schema = deviceschema[1];
                                    let formObject = $this.service.convertSchemaToForm($this.device, $this.schema, params);
                                    $this.formObj = formObject;
                                    $this.model = {};
                                    setTimeout(() => {
                                        $this.cfpLoadingBar.complete();
                                        $this.showform = true;
                                    }, 1);
                                }
                            },(err)=>{
                                console.log("error",err);
                                $this.cfpLoadingBar.complete();
                                $this.httpErrorHandler.handleError(err);
                            });
                        }
                    // }
                } else {
                    this.setFormFromParameters(params, form);
                }
            }else {
                //We assume that the user tryes to go one level deeper than allowed
                $this.mainservice.setMessage({
                    'title': 'Error',
                    'text': 'Parent didn\'t exist, save first the parent',
                    'status': 'error'
                });
                $this.router.navigateByUrl($this.service.pagination[$this.service.pagination.length - 1].url);
                $this.cfpLoadingBar.complete();
            }
            });

    }

    setFormFromParameters(params, form){
        let $this = this;
        let newModel: any = {};
        let newSchema = $this.service.getSchemaFromPath($this.service.schema, params['schema']);
        if (_.hasIn(params, 'clone')){
            newModel = _.get(this.service.device, params['clone']);
            //TODO
            if(params["schema"] === "properties.dicomNetworkAE"){

            }
            this.inClone = true;
        }else{
            newModel = _.get(this.service.device, params['devicereff']);
        }
        if (newSchema === null){
            if (_.hasIn(params, 'device')){
                this.router.navigateByUrl(`/device/edit/${params['device']}`);
            }else{
                this.router.navigateByUrl('/device/devicelist');
            }
        }
        let title = $this.service.getPaginationTitleFromModel(newModel, newSchema);

        let newPaginationObject = {
            url: '/device/edit/' + params['device'] + '/' + params['devicereff'] + '/' + params['schema'],
            // title:_.replace(newTitle,lastreff,''),
            title: title,
            devicereff: params['devicereff']
        };
        let newPaginationIndex = _.findIndex($this.service.pagination, (p) => {
            return p.url === newPaginationObject.url;
        });
        if (newPaginationIndex > -1) {
            let dropedPaginations = _.dropRight($this.service.pagination, $this.service.pagination.length - newPaginationIndex - 1);
            $this.service.pagination = dropedPaginations;
            $this.params = dropedPaginations;
        } else {
            $this.service.pagination.push(newPaginationObject);
        }
        $this.deleteForm();
        $this.showform = false;
        $this.model = newModel;
        if (_.hasIn(newSchema, '$ref') || _.hasIn(newSchema, 'items.$ref') || _.hasIn(newSchema, 'properties.$ref')) {
            let schemaName;
            let deleteRef;
            let refPath = '';
            if (_.hasIn(newSchema, 'properties.$ref')) {
                schemaName = newSchema.properties.$ref;
                refPath = 'properties';
                deleteRef = () => {
                    delete newSchema.properties.$ref;
                };
            }
            if (_.hasIn(newSchema, 'items.$ref')) {
                schemaName = newSchema.items.$ref;
                refPath = 'items';
                deleteRef = () => {
                    delete newSchema.items.$ref;
                };
            }
            if (_.hasIn(newSchema, '$ref')) {
                schemaName = newSchema.$ref;
                deleteRef = () => {
                    delete newSchema.$ref;
                };
            }
            $this.service.getSchema(schemaName).subscribe(subRefSchema => {
                deleteRef();
                if (refPath === '') {
                    _.merge(newSchema, subRefSchema);
                } else {
                    _.set(newSchema, refPath, subRefSchema);
                    refPath = '.' + refPath;
                }
                if(this.inClone){
                    //TODO
                }
                _.set($this.service.schema, params['schema'], newSchema);
                form = $this.service.convertSchemaToForm($this.model, newSchema, params);
                $this.formObj = form;
                setTimeout(() => {
                    $this.showform = true;
                    $this.cfpLoadingBar.complete();
                }, 1);
            }, (err) => {
                $this.cfpLoadingBar.complete();
            }
            );
        } else {
            // let newSchema = $this.service.getSchemaFromPath($this.service.schema,schemaparam);
            form = $this.service.convertSchemaToForm(newModel, newSchema, params);
            _.set($this.service.schema, params['schema'], newSchema);
            $this.formObj = form;
            setTimeout(() => {
                $this.showform = true;
                $this.cfpLoadingBar.complete();
            }, 1);
            // this._changeDetectionRef.detectChanges();

        }
        }
        deleteForm(){
            this.model = {};
            this.formObj = [];
        }
        fireBreadcrumb(breadcrumb){
            if (breadcrumb.url ===  '/device/devicelist'){ // for some reason when the user visited the device configurator and than comes back while trying to create new device, the old device is still in the pagination
                this.params = this.service.pagination = [
                     {
                         url: '/device/devicelist',
                         title: 'devicelist',
                         devicereff: undefined
                     }
                 ];
            }
            this.router.navigateByUrl(breadcrumb.url);
        }


        ngOnDestroy(){


        }
}
