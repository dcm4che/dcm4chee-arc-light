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
    submitValue;
    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private service: DeviceConfiguratorService,
        private $http: Http,
        private mainservice: AppService,
        private controlService: ControlService,
        public cfpLoadingBar: SlimLoadingBarService
    ) { }
    addModel(){
        let explod = this.params['device'].split('|');
        console.log('explod', explod);
        this.model = this.device[explod[1]];
        console.log('this.model', this.model);

    }
    submitFunction(value){
        console.log('in submit');
        let $this = this;
        this.cfpLoadingBar.start();
        let deviceClone = _.cloneDeep(this.service.device);
        this.service.addChangesToDevice(value, this.recentParams.devicereff);
        if (_.hasIn(this.recentParams, 'schema')){
            let newSchema = this.service.getSchemaFromPath(this.service.schema, this.recentParams['schema']);
            let title = this.service.getPaginationTitleFromModel(value, newSchema);
            this.service.pagination[this.service.pagination.length - 1].title = title;
        }
        if (_.hasIn(this.service.pagination, '[1].title') && this.service.pagination[1].title === '[new_device]'){
            if (this.service.createDevice()){
                this.service.createDevice()
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
/*                                $this.service.pagination[this.service.pagination.length-1].title = value.dicomDeviceName;
                                $this.service.pagination[this.service.pagination.length-1].url = `/device/edit/${value.dicomDeviceName}`;*/
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
                            $this.mainservice.setMessage({
                                'title': 'Error ' + err.status,
                                'text': err.statusText + '!',
                                'status': 'error',
                                'detailError': err._body
                            });
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
            if (this.service.updateDevice()){
                this.service.updateDevice()
                    .subscribe(
                        (success) => {
                            console.log('succes', success);
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'Device saved successfully!',
                                'status': 'info'
                            });
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
                        },
                        (err) => {
                            _.assign($this.service.device, deviceClone);
                            console.log('error', err);
                            $this.mainservice.setMessage({
                                'title': 'Error ' + err.status,
                                'text': err.statusText + '!',
                                'status': 'error',
                                'detailError': err._body
                            });
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
    ngOnInit() {
        let $this = this;
        let form;
        this.params = $this.service.pagination;
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
/*                    if ($this.service.device && params['device'] === $this.service.device.dicomDeviceName && $this.service.schema) {
                        $this.deleteForm();
                        $this.showform = false;
                        $this.model = $this.service.device;
                        form = $this.service.convertSchemaToForm($this.service.device, $this.service.schema, params);
                        $this.formObj = form;
                        setTimeout(()=> {
                            $this.showform = true;
                            $this.cfpLoadingBar.complete();
                        }, 1);
                    } else {*/
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
        // this.model = {};
        // this.model = {
        //     brave:"greatTEST",
        //     firstName:"SelamTEST",
        //     emailAddress:"testemail@htall.deEST",
        //     arraytest:[{testkey:"testkeyfrommodelTSETS"}]
        // };
        // this.formObj = [];
/*        this.formObj = [
            new DropdownList({
                key: 'brave',
                label: 'Bravery Rating',
                options: [
                    {key: 'solid',  value: 'Solid'},
                    {key: 'great',  value: 'Great'},
                    {key: 'good',   value: 'Good'},
                    {key: 'unproven', value: 'Unproven'}
                ],
                order: 3
            }),
            new InputText({
                key: 'firstName',
                label: 'First name',
                description:'Testdescriptionfirstname',
                required: true,
                order: 1
            }),
            new InputText({
                key: 'emailAddress',
                label: 'Email',
                type: 'email',
                order: 2
            }),
            new Checkbox({
                key: 'testcheckbox2',
                label: 'Testcheckbox2',
                options: [
                    {key: '1Test1',  value: '1test1', active:false},
                    {key: '1Test2',  value: '1test2', active:true},
                    {key: '1Test3',  value: '1test3', active:true},
                ]
            }),
            new ArrayElement({
                key: 'arrsingleelement',
                label: 'TestArray',
                type: 'text',
                value: []
            }),
            new RadioButtons({
                key: 'testradio',
                label: 'Testradi label',
                value:'installed',
                options: [
                    {key: 'True',  value: true},
                    {key: 'False',  value: false},
                ]
            }),
            new Checkbox({
                key: 'testcheckbox',
                label: 'Testcheckbox',
                options: [
                    {key: 'Test1',  value: 'test1', active:true},
                    {key: 'Test2',  value: 'test2', active:false},
                    {key: 'Test3',  value: 'test3', active:true},
                ]
            }),
            new ArrayElement({
                key: 'arrsingleelement2',
                label: 'TestArray2',
                type: 'number',
                value: []
            }),
            new ArrayObject({
                key: 'arraytest',
                label: 'Array test',
                order:4,
                options: [{
                    element:[
                        new InputText({
                            key:'testkey',
                            label:'Test label',
                            description:"TestDescription",
                            type:'text'
                        }),
                        new InputText({
                            key:'testke2y',
                            label:'Test label2',
                            type:'text',
                            value:"testval"
                        })
                        ,
                        new ArrayElement({
                            key: 'arrsingleelement3',
                            label: 'TestArray2',
                            type: 'number',
                            value: [1]
                        })
                        ,
                        new DropdownList({
                            key: 'brave2',
                            label: 'Bravery Rating2',
                            value:'great',
                            options: [
                                {key: 'solid',  value: 'Solid'},
                                {key: 'great',  value: 'Great'},
                                {key: 'good',   value: 'Good'},
                                {key: 'unproven', value: 'Unproven'}
                            ]
                        }),
                        new ArrayObject({
                            key: 'arraytestsub',
                            label: 'Array test',
                            order:4,
                            options: [{
                                element:[
                                    new InputText({
                                        key:'testkeysub',
                                        label:'Test labelsub',
                                        type:'text'
                                    }),
                                    new InputText({
                                        key:'testke2ysub',
                                        label:'Test label2sub',
                                        type:'text',
                                        value:"testval"
                                    })
                                    ,
                                    new DropdownList({
                                        key: 'brave2',
                                        label: 'Bravery Rating2',
                                        value:'great',
                                        options: [
                                            {key: 'solid',  value: 'Solid'},
                                            {key: 'great',  value: 'Great'},
                                            {key: 'good',   value: 'Good'},
                                            {key: 'unproven', value: 'Unproven'}
                                        ]
                                    })
                                ]
                            }]
                        })
                    ]
                }
                ,{
                    element:[
                        new InputText({
                            key:'testkey',
                            label:'Test labelOBJECT',
                            type:'text'
                        }),
                        new InputText({
                            key:'testke2y',
                            label:'Test label2',
                            type:'text'
                        })
                        ,
                        new DropdownList({
                            key: 'brave2',
                            label: 'Bravery Rating2',
                            options: [
                                {key: 'solid',  value: 'Solid'},
                                {key: 'great',  value: 'Great'},
                                {key: 'good',   value: 'Good'},
                                {key: 'unproven', value: 'Unproven'}
                            ]
                        })
                    ]
                }
                ]
            })
        ]*/
    }

    setFormFromParameters(params, form){
        let $this = this;
        let newModel: any = {};
        let newSchema = $this.service.getSchemaFromPath($this.service.schema, params['schema']);
        if (_.hasIn(params, 'clone')){
            newModel = _.get(this.service.device, params['clone']);
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
