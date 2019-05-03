import {Component, HostListener, OnInit} from '@angular/core';
import {ActivatedRoute} from "@angular/router";
import {
    AccessLocation,
    FilterSchema,
    StudyFilterConfig,
    StudyPageConfig,
    DicomMode,
    SelectDropdown
} from "../../interfaces";
import {StudyService} from "./study.service";
import {Observable} from "rxjs/Observable";
import {j4care} from "../../helpers/j4care.service";
import {Aet} from "../../models/aet";
import {PermissionService} from "../../helpers/permissions/permission.service";
import {AppService} from "../../app.service";
import { retry } from 'rxjs/operators';
import {Globalvar} from "../../constants/globalvar";
import {unescape} from "querystring";
import {animate, state, style, transition, trigger} from "@angular/animations";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {PatientDicom} from "../../models/patient-dicom";
import {StudyDicom} from "../../models/study-dicom";
import * as _  from "lodash";
import {LoadingBarService} from "@ngx-loading-bar/core";
import {DicomTableSchema, TableSchemaConfig} from "../../helpers/dicom-studies-table/dicom-studies-table.interfaces";
import {SeriesDicom} from "../../models/series-dicom";
import {InstanceDicom} from "../../models/instance-dicom";
import {WadoQueryParams} from "./wado-wuery-params";
import {GSPSQueryParams} from "../../models/gsps-query-params";
import {DropdownList} from "../../helpers/form/dropdown-list";
import {DropdownComponent} from "../../widgets/dropdown/dropdown.component";
import {StudyDeviceWebservice} from "../../models/StudyDeviceWebservice";
import {DeviceConfiguratorService} from "../../configuration/device-configurator/device-configurator.service";


@Component({
    selector: 'app-study',
    templateUrl: './study.component.html',
    styleUrls: ['./study.component.scss'],
    animations:[
        trigger("showHide",[
            state("show",style({
                padding:"*",
                height:'*',
                opacity:1
            })),
            state("hide",style({
                padding:"0",
                opacity:0,
                height:'0px',
                margin:"0"
            })),
            transition("show => hide",[
                animate('0.4s')
            ]),
            transition("hide => show",[
                animate('0.3s')
            ])
        ])
    ]
})
export class StudyComponent implements OnInit {

    test = Globalvar.ORDERBY;
    // model = new SelectDropdown('StudyDate,StudyTime','','', '', `<label>Study</label><span class="orderbydatedesc"></span>`);
    isOpen = true;
    testToggle(){
        this.isOpen = !this.isOpen;
    }
    studyConfig:StudyPageConfig = {
        tab:"study"
    };

    patientAttributes;

    private _filter:StudyFilterConfig = {
        filterSchemaEntry:{
            lineLength:undefined,
            schema:[]
        },
        filterSchemaMain:{
            lineLength:undefined,
            schema:[]
        },
        filterSchemaExpand:{
            lineLength:2,
            schema:[]
        },
        filterEntryModel:{
        },
        filterModel:{
            limit:20,
            offset:0
        },
        expand:false,
        quantityText:{
            count:"COUNT",
            size:"SIZE"
        }
    };

    get filter(): StudyFilterConfig {
        return this._filter;
    }

    set filter(value: StudyFilterConfig) {
        this.tableParam.config.offset = value.filterModel.offset;
        this._filter = value;
    }

    applicationEntities = {
        aes:[],
        aets:[],
        aetsAreSet:false
    };

    tableParam:{tableSchema:DicomTableSchema,config:TableSchemaConfig} = {
        tableSchema:Globalvar.PATIENT_STUDIES_TABLE_SCHEMA(this, this.actions),
        config:{
            offset:0
        }
    };
    // studyDevice:StudyDevice;
    testModel;
    deviceWebservice:StudyDeviceWebservice;
    constructor(
        private route:ActivatedRoute,
        private service:StudyService,
        private permissionService:PermissionService,
        private appService:AppService,
        private httpErrorHandler:HttpErrorHandler,
        private cfpLoadingBar:LoadingBarService,
        private deviceConfigurator:DeviceConfiguratorService
    ) { }

    ngOnInit() {
        console.log("this.service",this.appService);
        if(_.hasIn(this.appService,"global.myDevice") && this.appService.deviceName && this.appService.deviceName === this.appService.global.myDevice.dicomDeviceName){
            this.filter.filterEntryModel["device"] = this.appService.deviceName;
            this.deviceWebservice = new StudyDeviceWebservice({
                selectedDeviceObject:this.appService.global.myDevice
            })
        }
        this.getPatientAttributeFilters();
        this.route.params.subscribe(params => {
          this.studyConfig.tab = params.tab;
          this.getDevices();
        });
    }
    testShow = true;
    fixedHeader = false;
    patients:PatientDicom[] = [];
    moreStudies:boolean = false;

    @HostListener("window:scroll", [])
    onWindowScroll(e) {
        let html = document.documentElement;
        if(html.scrollTop > 63){
            this.fixedHeader = true;
            this.testShow = false;
            this.filter.expand = false;
        }else{
            this.fixedHeader = false;
            this.testShow = true;
        }

    }


    actions(id, model){
        console.log("id",id);
        console.log("model",model);
        if(id.action === "toggle_studies"){
            if(!model.studies){
                // this.getStudies(model);
                //TODO getStudies
            }else{
                model.showStudies = !model.showStudies;
            }

        }
        if(id.action === "toggle_series"){
            if(!model.series){
                this.getSeries(model);
            }else{
                model.showSeries = !model.showSeries;
            }

        }
        if(id.action === "toggle_instances"){
            if(!model.instances){
                this.getInstances(model);
            }else{
                model.showInstances = !model.showInstances;
            }

        }
    }
    search(mode?:('next'|'prev'|'current')){
        // if (this._filter.filterModel.aet){
        // let callingAet = new Aet(this._filter.filterModel.aet);
        console.log("this",this.filter);
        console.log("deviceWebservice",this.deviceWebservice);
        let filterModel =  _.clone(this._filter.filterModel);
        if(filterModel.limit){
            filterModel.limit++;
        }
        if(!mode || mode === "current"){
            filterModel.offset = 0;
            this.getStudies(filterModel);
        }else{
            if(mode === "next" && this.moreStudies){
                filterModel.offset = filterModel.offset + this._filter.filterModel.limit;
                this.getStudies(filterModel);
            }
            if(mode === "prev" && filterModel.offset > 0){
                filterModel.offset = filterModel.filterModel.offset - this._filter.filterModel.offset;
                this.getStudies(filterModel);
            }
        }
/*        }else{
            this.appService.showError("Calling AET is missing!");
        }*/
    }

    getStudies(filterModel){
        this.cfpLoadingBar.start();
        filterModel['includefield'] = 'all';
        this.service.getStudies(filterModel, this.deviceWebservice)
            .subscribe(res => {
                this.patients = [];
                if(res){
                    let index = 0;
                    let patient: PatientDicom;
                    let study: StudyDicom;
                    let patAttrs;
                    let tags = this.patientAttributes.dcmTag;

                    while (tags && (tags[index] < '00201200')) {
                        index++;
                    }
                    tags.splice(index, 0, '00201200');
                    tags.push('77770010', '77771010', '77771011', '77771012', '77771013', '77771014');

                    res.forEach((studyAttrs, index) => {
                        patAttrs = {};
                        this.service.extractAttrs(studyAttrs, tags, patAttrs);
                        if (!(patient && this.service.equalsIgnoreSpecificCharacterSet(patient.attrs, patAttrs))) {
                            patient = new PatientDicom(patAttrs, [], false, true);
                            this.patients.push(patient);
                        }
                        study = new StudyDicom(studyAttrs, patient, this._filter.filterModel.offset + index);
                        patient.studies.push(study);
                    });
                    if (this.moreStudies = (res.length > this._filter.filterModel.limit)) {
                        patient.studies.pop();
                        if (patient.studies.length === 0) {
                            this.patients.pop();
                        }
                        // this.studies.pop();
                    }
                }else{
                    this.appService.showMsg("No Studies found!");
                }
                this._filter.filterModel.offset = filterModel.offset;
                this.cfpLoadingBar.complete();
                console.log("this.patients", this.patients);
            }, err => {
                j4care.log("Something went wrong on search", err);
                this.httpErrorHandler.handleError(err);
                this.cfpLoadingBar.complete();
            });
    }
    getSeries(study:StudyDicom){
        console.log('in query sersies study=', study);
        this.cfpLoadingBar.start();
        if (study.offset < 0) study.offset = 0;
        // let callingAet = new Aet(this._filter.filterModel.aet);
        let filters = _.clone(this._filter.filterModel);
        if(filters.limit){
            filters.limit++;
        }
        filters['includefield'] = 'all';
        delete filters.aet;
        filters["orderby"] = 'SeriesNumber';
        this.service.getSeries(study.attrs['0020000D'].Value[0], filters, this.deviceWebservice)
            .subscribe((res)=>{
            if (res){
                if (res.length === 0){
                    this.appService.setMessage( {
                        'title': 'Info',
                        'text': 'No matching series found!',
                        'status': 'info'
                    });
                    console.log('in reslength 0');
                }else{

                    study.series = res.map((attrs, index) =>{
                        return new SeriesDicom(study, attrs, study.offset + index);
                    });
                    if (study.moreSeries = (study.series.length > this._filter.filterModel.limit)) {
                        study.series.pop();
                    }
                    console.log("study",study);
                    console.log("patients",this.patients);
                    // StudiesService.trim(this);
                    study.showSeries = true;
                }
                this.cfpLoadingBar.complete();
            }else{
                this.appService.setMessage( {
                    'title': 'Info',
                    'text': 'No matching series found!',
                    'status': 'info'
                });
            }
        },(err)=>{
                j4care.log("Something went wrong on search", err);
                this.httpErrorHandler.handleError(err);
                this.cfpLoadingBar.complete();
        });
    }

    getInstances(series:SeriesDicom){
        console.log('in query Instances serie=', series);
        this.cfpLoadingBar.start();
        if (series.offset < 0) series.offset = 0;
        // let callingAet = new Aet(this._filter.filterModel.aet);
        let filters = _.clone(this._filter.filterModel);
        if(filters.limit){
            filters.limit++;
        }
        filters['includefield'] = 'all';
        delete filters.aet;
        filters["orderby"] = 'InstanceNumber';
        this.service.getInstances(series.attrs['0020000D'].Value[0], series.attrs['0020000E'].Value[0], filters, this.deviceWebservice)
            .subscribe((res)=>{
            if (res){
                series.instances = res.map((attrs, index) => {
                    let numberOfFrames = j4care.valueOf(attrs['00280008']),
                        gspsQueryParams:GSPSQueryParams[] = this.service.createGSPSQueryParams(attrs),
                        video = this.service.isVideo(attrs),
                        image = this.service.isImage(attrs);
                    return new InstanceDicom(
                        series,
                        series.offset + index,
                        attrs,
                        new WadoQueryParams(attrs['0020000D'].Value[0],attrs['0020000E'].Value[0], attrs['00080018'].Value[0]),
                        video,
                        image,
                        numberOfFrames,
                        gspsQueryParams,
                        this.service.createArray(video || numberOfFrames || gspsQueryParams.length || 1),
                        1
                    )
                });
                console.log(series);
                console.log(this.patients);
                series.showInstances = true;
            }else{
                series.instances = [];
                if (series.moreInstances = (series.instances.length > this._filter.filterModel.limit)) {
                    series.instances.pop();
                    this.appService.setMessage( {
                        'title': 'Info',
                        'text': 'No matching Instancess found!',
                        'status': 'info'
                    });
                }
            }
            this.cfpLoadingBar.complete();
        },(err)=>{
                j4care.log("Something went wrong on search", err);
                this.httpErrorHandler.handleError(err);
                this.cfpLoadingBar.complete();
        });
    }
    entryFilterChanged(e){
        console.log("e",e);
        console.log("this.deviceWebservice",this.deviceWebservice);
        if(this.deviceWebservice.selectedDevice != this.filter.filterEntryModel["device"]){
            this.deviceWebservice.selectedDevice = this.filter.filterEntryModel["device"];
            this.deviceConfigurator.getDevice(this.deviceWebservice.selectedDevice).subscribe(device=>{
                this.deviceWebservice.selectedDeviceObject = device;
                this._filter.filterSchemaEntry = this.service.getEntrySchema(this.deviceWebservice.devicesDropdown, this.deviceWebservice.getDcmWebAppServicesDropdown(["QIDO_RS"]));
            });
            this._filter.filterEntryModel["webService"] = undefined;
            this.deviceWebservice.selectedWebApp = undefined;
        }
        if(!this.deviceWebservice.selectedWebApp || this.deviceWebservice.selectedWebApp.dcmWebAppName != this.filter.filterEntryModel["webService"]){
            this.deviceWebservice.setSelectedWebAppByString(this.filter.filterEntryModel["webService"]);
        }
    }

    filterChanged(){

    }

    setSchema(){
        this._filter.filterSchemaMain.lineLength = undefined;
        this._filter.filterSchemaExpand.lineLength = undefined;
        this._filter.filterSchemaEntry.lineLength = undefined;
        // this._filter.filterSchemaEntry  = this.service.getEntrySchema(this.devices,this.selectedDeviceWebserviceAet);
        this._filter.filterSchemaMain  = this.service.getFilterSchema(this.studyConfig.tab,  this.applicationEntities.aes, this._filter.quantityText,'main');
        this._filter.filterSchemaExpand  = this.service.getFilterSchema(this.studyConfig.tab, this.applicationEntities.aes,this._filter.quantityText,'expand');
        this._filter.filterSchemaEntry = this.service.getEntrySchema(this.deviceWebservice.devicesDropdown, this.deviceWebservice.getDcmWebAppServicesDropdown(["QIDO_RS"]));
    }

    accessLocationChange(e){
        console.log("e",e.value);
        this.setSchema();
    }

    getPatientAttributeFilters(){
        this.service.getAttributeFilter().subscribe(patientAttributes=>{
            this.patientAttributes = patientAttributes;
        },err=>{
            j4care.log("Something went wrong on getting Patient Attributes",err);
            this.httpErrorHandler.handleError(err);
        });
    }
    getApplicationEntities(){
        if(!this.applicationEntities.aetsAreSet){
            Observable.forkJoin(
                this.service.getAes().map(aes=> aes.map(aet=> new Aet(aet))),
                this.service.getAets().map(aets=> aets.map(aet => new Aet(aet))),
            )
            .subscribe((res)=>{
/*                [0,1].forEach(i=>{
                    res[i] = j4care.extendAetObjectWithAlias(res[i]);
                    ["external","internal"].forEach(location=>{
                      this.applicationEntities.aes[location] = this.permissionService.filterAetDependingOnUiConfig(res[i],location);
                      this.applicationEntities.aets[location] = this.permissionService.filterAetDependingOnUiConfig(res[i],location);
                      this.applicationEntities.aetsAreSet = true;
                    })
                });*/
                console.log("filter",this.filter);
                this.setSchema();
            },(err)=>{
                this.appService.showError("Error getting AETs!");
                j4care.log("error getting aets in Study page",err);
            });
        }else{
            this.setSchema();
        }
    }

    getDevices(){
        this.service.getDevices()
            .subscribe(devices=>{
            // this.studyDevice = new StudyDevice(devices.map(device=> new SelectDropdown(device.dicomDeviceName, device.dicomDeviceName)));
                if(this.deviceWebservice){
                    this.deviceWebservice.devices = devices;
                    this.deviceWebservice.selectedDevice
                }else{
                    this.deviceWebservice = new StudyDeviceWebservice({devices:devices});
                }
            this.getApplicationEntities();
        },err=>{
            j4care.log("Something went wrong on getting Devices",err);
            this.httpErrorHandler.handleError(err);
        })
    }

}
