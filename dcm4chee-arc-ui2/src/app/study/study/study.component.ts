import {
    AfterViewChecked,
    AfterViewInit,
    Component,
    ElementRef,
    HostListener,
    OnInit,
    ViewChild,
    ViewContainerRef
} from '@angular/core';
import {ActivatedRoute} from "@angular/router";
import {
    AccessLocation,
    FilterSchema,
    StudyFilterConfig,
    StudyPageConfig,
    DicomMode,
    SelectDropdown, DicomLevel, UniqueSelectIdObject, DicomSelectObject, Quantity, DicomResponseType
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
import {
    DicomTableSchema,
    StudyTrash, TableParam,
    TableSchemaConfig
} from "../../helpers/dicom-studies-table/dicom-studies-table.interfaces";
import {SeriesDicom} from "../../models/series-dicom";
import {InstanceDicom} from "../../models/instance-dicom";
import {WadoQueryParams} from "./wado-wuery-params";
import {GSPSQueryParams} from "../../models/gsps-query-params";
import {DropdownList} from "../../helpers/form/dropdown-list";
import {DropdownComponent} from "../../widgets/dropdown/dropdown.component";
import {DeviceConfiguratorService} from "../../configuration/device-configurator/device-configurator.service";
import {EditPatientComponent} from "../../widgets/dialogs/edit-patient/edit-patient.component";
import {MatDialog, MatDialogConfig, MatDialogRef} from "@angular/material";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";
import {HttpHeaders} from "@angular/common/http";
import {EditMwlComponent} from "../../widgets/dialogs/edit-mwl/edit-mwl.component";
import {ComparewithiodPipe} from "../../pipes/comparewithiod.pipe";
import {ConfirmComponent} from "../../widgets/dialogs/confirm/confirm.component";
import {EditStudyComponent} from "../../widgets/dialogs/edit-study/edit-study.component";
import {ExportDialogComponent} from "../../widgets/dialogs/export/export.component";
import {UploadFilesComponent} from "../../widgets/dialogs/upload-files/upload-files.component";
import {DcmWebApp} from "../../models/dcm-web-app";
import {StudyWebService} from "./study-web-service.model";
import {RequestOptionsArgs} from "@angular/http";
import {DeleteRejectedInstancesComponent} from "../../widgets/dialogs/delete-rejected-instances/delete-rejected-instances.component";
import {ViewerComponent} from "../../widgets/dialogs/viewer/viewer.component";
import {WindowRefService} from "../../helpers/window-ref.service";
import {SelectionsDicomObjects} from "./selections-dicom-objects.model";
import {LargeIntFormatPipe} from "../../pipes/large-int-format.pipe";
import {UploadDicomComponent} from "../../widgets/dialogs/upload-dicom/upload-dicom.component";


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
                animate('0.2s')
            ]),
            transition("hide => show",[
                animate('0.3s')
            ])
        ])
    ]
})
export class StudyComponent implements OnInit{

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
    trash:StudyTrash = {
        reject:undefined,
        rjnotes:undefined,
        rjcode:undefined,
        active:false
    };
    studyWebService:StudyWebService;
    selectedElements:SelectionsDicomObjects;
    tableParam:TableParam = {
        tableSchema:this.getSchema(),
        config:{
            offset:0,
            showCheckboxes:false
        }
    };
    // studyDevice:StudyDevice;
    testModel;
    // studyWebService:StudystudyWebServiceModel;
/*    private _selectedWebAppService:DcmWebApp;
    webApps:DcmWebApp[];*/


    dialogRef: MatDialogRef<any>;
    lastPressedCode;
    moreFunctionConfig = {
        placeholder: "More functions",
        options:[
            new SelectDropdown("create_patient","Create patient"),
            new SelectDropdown("upload_dicom","Upload DICOM Object"),
            new SelectDropdown("permanent_delete","Permanent delete", "Delete Rejected Instances permanently"),
            new SelectDropdown("export_multiple","Export multiple studies"),
            new SelectDropdown("retrieve_multiple","Retrieve multiple studies"),
            new SelectDropdown("storage_verification","Storage Verification"),
            new SelectDropdown("download_studies","Download Studies as CSV"),
        ],
        model:undefined
    };
    actionsSelections = {
        placeholder: "Actions for selections",
        options:[
            new SelectDropdown("export_object","Export selections", "Export selected studies, series or instances"),
            new SelectDropdown("reject_object","Reject selections", "Reject selected studies, series or instances"),
            new SelectDropdown("restore_object","Restore selections", "Restore selected studies, series or instances"),
            new SelectDropdown("delete_object","Delete selections", "Delete selected studies, series or instances permanently"),
            new SelectDropdown("toggle_checkboxes","Toggle checkboxes", "Toggle checkboxes for selection")
        ],
        model:undefined
    };
    exporters;
    testShow = true;
    fixedHeader = false;
    patients:PatientDicom[] = [];
    moreStudies:boolean = false;
    queues;

    searchCurrentList = '';
    @ViewChild('stickyHeader') stickyHeaderView: ElementRef;
    largeIntFormat;
    filterButtonPath = {
        count:[],
        size:[]
    };
    internal = true;
    constructor(
        private route:ActivatedRoute,
        private service:StudyService,
        private permissionService:PermissionService,
        private appService:AppService,
        private httpErrorHandler:HttpErrorHandler,
        private cfpLoadingBar:LoadingBarService,
        private deviceConfigurator:DeviceConfiguratorService,
        private viewContainerRef: ViewContainerRef,
        private dialog: MatDialog,
        private config: MatDialogConfig,
        private _keycloakService:KeycloakService
    ) {}

    ngOnInit() {
        console.log("this.service",this.appService);
        this.largeIntFormat = new LargeIntFormatPipe();
        this.resetSelectionObject();
        this.getPatientAttributeFilters();
        this.route.params.subscribe(params => {
          this.studyConfig.tab = params.tab;
          this.initWebApps();
        });
        this.moreFunctionConfig.options.filter(option=>{
            console.log("option",option);
            if(option.value === "retrieve_multiple"){
                return !this.internal;
            }else{
                return true;
            }
        })
    }

    resetSelectionObject(){
        this.selectedElements = new SelectionsDicomObjects({
            instance:{},
            series:{},
            patient:{},
            study:{}
        });
    }

    setTopToTableHeder(){
        if(this.stickyHeaderView.nativeElement.scrollHeight && this.stickyHeaderView.nativeElement.scrollHeight > 0 && this.tableParam.config.headerTop != `${this.stickyHeaderView.nativeElement.scrollHeight}px`){
            this.tableParam.config.headerTop = `${this.stickyHeaderView.nativeElement.scrollHeight}px`;
        }
    }

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


    moreFunctionChanged(e){
        console.log("e",e);
        switch (e){
            case "create_patient":
                this.createPatient();
                break;
            case "permanent_delete":
                this.deleteRejectedInstances();
                break;
            case "upload_dicom":
                this.uploadDicom();
               break;
            case "export_multiple":
                this.exportMultipleStudies();
               break;
            case "retrieve_multiple":
                this.retrieveMultipleStudies();
               break;
            case "storage_verification":
                this.storageVerification();
               break;
            case "download_studies":
                this.downloadCSV();
               break;
        }
        setTimeout(()=>{
            this.moreFunctionConfig.model = undefined;
        },1);
    }
    actionsSelectionsChanged(e){
        if(e === "toggle_checkboxes"){
            this.tableParam.config.showCheckboxes = !this.tableParam.config.showCheckboxes;
            this.tableParam.tableSchema  = this.getSchema();
        }
        //TODO
        // if(_.size(this.selectedElements) > 0){
            if(e === "export_object"){
                // Object.keys(this.selectedElements).forEach((id)=>{
                //     let object:DicomSelectObject = <DicomSelectObject>this.selectedElements[e.dicomLevel];
/*                    if(object.dicomLevel === "instance"){
                        this.exportInstance(object.object);
                    }
                    if(object.dicomLevel === "study"){
                        this.exportStudy(object.object);
                    }
                    if(object.dicomLevel === "series"){
                        this.exportSeries(object.object);
                    }*/
                // });
            }
        // }

        setTimeout(()=>{
            this.actionsSelections.model = undefined;
        },1);
    }


    select(object, dicomLevel:DicomLevel){
        this.selectedElements.toggle(dicomLevel, this.service.getObjectUniqueId(object.attrs, dicomLevel), object);
/*        let idObject:UniqueSelectIdObject = this.service.getObjectUniqueId(object.attrs, dicomLevel);
        if(_.hasIn(this.selectedElements,`${dicomLevel}["${idObject.id}"]`)){
            delete this.selectedElements[dicomLevel][idObject.id];
            object.selected = false;
        }else{
            this.selectedElements[dicomLevel][idObject.id] = {
                idObject:idObject,
                object:object,
                dicomLevel:dicomLevel
            };
            object.selected = true;
        }*/
    }
    actions(id, model){
        console.log("id",id);
        console.log("model",model);
        if(this.studyWebService.selectedWebService){ //selectedWebAppService

            if(id.action === "select"){
                // console.log("getid",this.service.getObjectUniqueId(model.attrs, id.level));
                this.select(model, id.level);
                console.log("this.selectedElement",this.selectedElements);
            }

            if(id.action === "toggle_studies"){
                if(!model.studies){
                    // this.getStudies(model);
                    //TODO getStudies (needed for patient mode)
                }else{
                    model.showStudies = !model.showStudies;
                }

            }
            if(id.action === "toggle_series"){
                if(!model.series){
                    this.getSeries(model, 0);
                }else{
                    model.showSeries = !model.showSeries;
                }

            }
            if(id.action === "toggle_instances"){
                if(!model.instances){
                    this.getInstances(model, 0);
                }else{
                    model.showInstances = !model.showInstances;
                }

            }
            if(id.action === "edit_patient"){
                this.editPatient(model);
            }
            if(id.action === "download"){
                if(id.level === "instance"){
                    if(id.mode === "uncompressed"){
                        this.downloadURL(model);
                    }else{
                        this.downloadURL(model, "*");
                    }
                }else{
                    this.downloadZip(model,id.level,id.mode);
                }
            }
            if(id.action === "reject"){
                if(id.level === "study"){
                    this.rejectStudy(model);
                }
                if(id.level === "series"){
                    this.rejectSeries(model);
                }
                if(id.level === "instance"){
                    this.rejectInstance(model);
                }
            }
            if(id.action === "delete"){
                if(id.level === "study"){
                    this.deleteStudy(model);
                }
            }
            if(id.action === "verify_storage"){
                this.storageCommitmen(id.level, model);
            }
            if(id.action === "export"){
                if(id.level === "study"){
                    this.exportStudy(model);
                }
                if(id.level === "instance"){
                    this.exportInstance(model);
                }
                if(id.level === "series"){
                    this.exportSeries(model);
                }
            }
            if(id.action === "edit_study"){
                this.editStudy(model);
            }
            if(id.action === "modify_expired_date"){
                this.setExpiredDate(model);
            }
            if(id.action === "create_mwl"){
                this.createMWL(model);
            }
            if(id.action === "download_csv"){
                this.downloadCSV(model.attrs, id.level);
            }
            if(id.action === "upload_file"){
                this.uploadFile(model, id.level);
            }
            if(id.action === "view"){
                this.viewInstance(model);
            }
        }else{
            this.appService.showError("No Web Application Service was selected!");
        }
    }

    uploadDicom(){
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(UploadDicomComponent, {
            height: 'auto',
            width: '500px'
        });
/*        this.dialogRef.componentInstance.aes = this.aes;
        this.dialogRef.componentInstance.selectedAe = this.aetmodel.dicomAETitle;*/
        this.dialogRef.componentInstance.selectedWebApp = this.studyWebService.selectedWebService;
        this.dialogRef.afterClosed().subscribe((result) => {
            console.log('result', result);
            if (result){
            }
        });
    };
    uploadFile(object,mode){
        if(mode === "mwl"){
            //perpare mwl object for study upload
            let newObject = {
                "00400275":{
                    "Value":[{}],
                    "vr":"SQ"
                }
            };
            let inSequenceCodes = [
                "00401001",
                "00321060",
                "00400009",
                "00400007",
                "00400008"
            ];
            let mapCodes = {
                "00401001":{
                    "code":"00200010",
                    "vr":"SH"
                },
                "00321060":{
                    "code":"00081030",
                    "vr":"LO"
                },
                "00400100":[
                    {
                        "map":"Value[0]['00400002']",
                        "code":"00080020",
                        "vr":"DA"
                    },
                    {
                        "map":"Value[0]['00400003']",
                        "code":"00080030",
                        "vr":"TM"
                    }
                ]
            };
            let removeCode = [
                "00401001",
                "00400100",
                "00321060",
                "00321064"
            ];
            _.forEach(object.attrs,(m,i)=>{
                if(_.indexOf(inSequenceCodes,i) > -1){
                    newObject["00400275"].Value[0][i] = m;
                    this.service.mapCode(m,i,newObject,mapCodes);
                }else{
                    if(_.indexOf(removeCode,i) === -1){
                        newObject[i] = m;
                    }else{
                        this.service.mapCode(m,i,newObject,mapCodes);
                    }
                }
            });

            object.attrs = newObject;
        }
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(UploadFilesComponent, {
            height: 'auto',
            width: '500px'
        });
/*        this.dialogRef.componentInstance.aes = this.aes;
        this.dialogRef.componentInstance.selectedAe = this.aetmodel.dicomAETitle;*/
        this.dialogRef.componentInstance.fromExternalWebApp = this.service.getUploadFileWebApp(this.studyWebService);
        this.dialogRef.componentInstance.dicomObject = object;
        this.dialogRef.afterClosed().subscribe((result) => {
            console.log('result', result);
            if (result){
            }
        });
    }
    editMWL(patient, patientkey, mwlkey, mwl){
        let config = {
            saveLabel:'SAVE',
            titleLabel:'Edit MWL of patient '
        };
        config.titleLabel = 'Edit MWL of patient ';
        config.titleLabel += ((_.hasIn(patient, 'attrs.00100010.Value.0.Alphabetic')) ? '<b>' + patient.attrs['00100010'].Value[0]['Alphabetic'] + '</b>' : ' ');
        config.titleLabel += ((_.hasIn(patient, 'attrs.00100020.Value.0')) ? ' with ID: <b>' + patient.attrs['00100020'].Value[0] + '</b>' : '');
        this.modifyMWL(patient, 'edit', patientkey, mwlkey, mwl, config);
    };
    createMWL(patient){
        let mwl: any = {
            'attrs': {
                '00400100': {
                    'vr': 'SQ',
                    'Value': [{
                        '00400001': { 'vr': 'AE', 'Value': ['']}
                    }]
                },
                '0020000D': { 'vr': 'UI', 'Value': ['']},
                '00400009': { 'vr': 'SH', 'Value': ['']},
                '00080050': { 'vr': 'SH', 'Value': ['']},
                '00401001': { 'vr': 'SH', 'Value': ['']}
            }
        };
        let config = {
            saveLabel:'CREATE',
            titleLabel:'Create new MWL'
        };
        this.modifyMWL(patient, 'create', '', '', mwl, config);
    };
    modifyMWL(patient, mode, patientkey, mwlkey, mwl, config:{saveLabel:string, titleLabel:string}){
        let originalMwlObject = _.cloneDeep(mwl);
        this.config.viewContainerRef = this.viewContainerRef;

        this.lastPressedCode = 0;
        if (mode === 'edit'){
            _.forEach(mwl.attrs, function(value, index) {
                let checkValue = '';
                if (value.Value && value.Value.length){
                    checkValue = value.Value.join('');
                }
                if (!(value.Value && checkValue != '')){
                    delete mwl.attrs[index];
                }
            });
            if (mwl.attrs['00400100'].Value[0]['00400002'] && !mwl.attrs['00400100'].Value[0]['00400002'].Value){
                mwl.attrs['00400100'].Value[0]['00400002']['Value'] = [''];
            }
        }

        // this.config.width = "800";

        let $this = this;
        this.service.getMwlIod().subscribe((res) => {
            $this.service.patientIod = res;
            let iod = $this.service.replaceKeyInJson(res, 'items', 'Value');
            let mwlFiltered = _.cloneDeep(mwl);
            mwlFiltered.attrs = new ComparewithiodPipe().transform(mwl.attrs, iod);
            $this.service.initEmptyValue(mwlFiltered.attrs);
            $this.dialogRef = $this.dialog.open(EditMwlComponent, {
                height: 'auto',
                width: '90%'
            });
            $this.dialogRef.componentInstance.iod = iod;
            $this.dialogRef.componentInstance.mode = mode;
            $this.dialogRef.componentInstance.dropdown = $this.service.getArrayFromIod(res);
            $this.dialogRef.componentInstance.mwl = mwlFiltered;
            $this.dialogRef.componentInstance.mwlkey = mwlkey;
            $this.dialogRef.componentInstance.saveLabel = config.saveLabel;
            $this.dialogRef.componentInstance.titleLabel = config.titleLabel;
            $this.dialogRef.afterClosed().subscribe(result => {
                //If user clicked save
                console.log('result', result);
                if (result){
                    $this.service.clearPatientObject(mwlFiltered.attrs);
                    $this.service.convertStringToNumber(mwlFiltered.attrs);
                    // StudiesService.convertDateToString($scope, "mwl");
                    let local = {};

                    $this.service.appendPatientIdTo(patient.attrs, local);

                    _.forEach(mwlFiltered.attrs, function(m, i){
                        if (res[i]){
                            local[i] = m;
                        }
                    });
                    _.forEach(mwlFiltered.attrs, function(m, i){
                        if ((res && res[i] && res[i].vr != 'SQ') && m.Value && m.Value.length === 1 && m.Value[0] === ''){
                            delete mwlFiltered.attrs[i];
                        }
                    });
                    console.log('on post', local);
                    this.service.modifyMWL(local,this.studyWebService, new HttpHeaders({ 'Content-Type': 'application/dicom+json' })).subscribe((response) => {
                        if (mode === 'edit'){
                            // _.assign(mwl, mwlFiltered);
                            $this.appService.setMessage('MWL saved successfully!');
                        }else{
                            $this.appService.showMsg('MWL created successfully!');
                        }
                    }, (response) => {
                        $this.httpErrorHandler.handleError(response);
                    });
                }else{
                    _.assign(mwl, originalMwlObject);
                }
                $this.dialogRef = null;
            });
        }, (err) => {
            console.log('error', err);
        });
    }

    /*
    * @confirmparameters is an object that can contain title, content
    * */
    confirm(confirmparameters){
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
    viewInstance(inst) {
        let token;
        let url;
        let contentType;
        this._keycloakService.getToken().subscribe((response)=>{
            if(!this.appService.global.notSecure){
                token = response.token;
            }
            // this.select_show = false;
            if(inst.video || inst.image || inst.numberOfFrames || inst.gspsQueryParams.length){
                if (inst.gspsQueryParams.length){
                    url =  this.service.wadoURL(this.studyWebService,inst.gspsQueryParams[inst.view - 1]);
                }
                if (inst.numberOfFrames || (inst.image && !inst.video)){
                    contentType = 'image/jpeg';
                    url =  this.service.wadoURL(this.studyWebService,inst.wadoQueryParams, { contentType: 'image/jpeg'});
                }
                if (inst.video){
                    contentType = 'video/*';
                    url =  this.service.wadoURL(this.studyWebService,inst.wadoQueryParams, { contentType: 'video/*' });
                }
            }else{
                url = this.service.wadoURL(this.studyWebService,inst.wadoQueryParams);
            }
            if(!contentType){
                if(_.hasIn(inst,"attrs.00420012.Value.0") && inst.attrs['00420012'].Value[0] != ''){
                    contentType = inst.attrs['00420012'].Value[0];
                }
            }
            if(!contentType || contentType.toLowerCase() === 'application/pdf' || contentType.toLowerCase().indexOf("video") > -1 || contentType.toLowerCase() === 'text/xml'){
                // this.j4care.download(url);
                if(!this.appService.global.notSecure){
                    WindowRefService.nativeWindow.open(this.service.renderURL(inst) + `&access_token=${token}`);
                }else{
                    WindowRefService.nativeWindow.open(this.service.renderURL(inst));
                }
            }else{
                this.config.viewContainerRef = this.viewContainerRef;
                this.dialogRef = this.dialog.open(ViewerComponent, {
                    height: 'auto',
                    width: 'auto',
                    panelClass:"viewer_dialog"
                });
                this.dialogRef.componentInstance.views = inst.views;
                this.dialogRef.componentInstance.view = inst.view;
                this.dialogRef.componentInstance.contentType = contentType;
                this.dialogRef.componentInstance.url = url;
                this.dialogRef.afterClosed().subscribe();
            }
            // window.open(this.renderURL(inst));
        });
    };
    downloadCSV(attr?, mode?){
        let queryParameters = this.createQueryParams(0, 1000, this.createStudyFilterParams());
        this.confirm({
            content:"Do you want to use semicolon as delimiter?",
            cancelButton:"No",
            saveButton:"Yes",
            result:"yes"
        }).subscribe((ok)=>{
            let semicolon = false;
            if(ok)
                semicolon = true;
            let token;
            let url = this.service.getDicomURL("study",this.studyWebService.selectedWebService);
            this._keycloakService.getToken().subscribe((response)=>{
                if(!this.appService.global.notSecure){
                    token = response.token;
                }
                let filterClone = _.cloneDeep(queryParameters);
                delete filterClone['offset'];
                delete filterClone['limit'];
                filterClone["accept"] = `text/csv${(semicolon?';delimiter=semicolon':'')}`;
                let fileName = "dcm4chee.csv";
                if(attr && mode){
                    filterClone["PatientID"] =  j4care.valueOf(attr['00100020']);
                    filterClone["IssuerOfPatientID"] = j4care.valueOf(attr['00100021']);
                    if(mode === "series" && _.hasIn(attr,'0020000D')){
                        url =`${url}/${j4care.valueOf(attr['0020000D'])}/series`;
                        fileName = `${j4care.valueOf(attr['0020000D'])}.csv`;
                    }
                    if(mode === "instance"){
                        url =`${url}/${j4care.valueOf(attr['0020000D'])}/series/${j4care.valueOf(attr['0020000E'])}/instances`;
                        fileName = `${j4care.valueOf(attr['0020000D'])}_${j4care.valueOf(attr['0020000E'])}.csv`;
                    }
                }
                if(!this.appService.global.notSecure){
                    filterClone["access_token"] = token;
                }
                j4care.downloadFile(`${url}?${this.appService.param(filterClone)}`,fileName);
                // WindowRefService.nativeWindow.open(`${url}?${this.appService.param(filterClone)}`);
            });
        })
    }

    downloadZip(object, level, mode){
        let token;
        let param = 'accept=application/zip';
        console.log("url",this.service.getDicomURL(mode, this.studyWebService.selectedWebService));
        let url = this.service.studyURL(object.attrs, this.studyWebService.selectedWebService);
        let fileName = this.service.studyFileName(object.attrs);
        if(mode === 'compressed'){
            param += ';transfer-syntax=*';
        }
        if(level === 'serie'){
            url = this.service.seriesURL(object.attrs, this.studyWebService.selectedWebService);
            fileName = this.service.seriesFileName(object.attrs);
        }
        this._keycloakService.getToken().subscribe((response)=>{
            if(!this.appService.global.notSecure){
                token = response.token;
            }
            if(!this.appService.global.notSecure){
                j4care.downloadFile(`${url}?${param}&access_token=${token}`,`${fileName}.zip`)
            }else{
                j4care.downloadFile(`${url}?${param}`,`${fileName}.zip`)
            }
        });
    };
    downloadURL(inst, transferSyntax?:string) {
        let token;
        let url = "";
        let fileName = "dcm4che.dcm";
        this._keycloakService.getToken().subscribe((response)=>{
            if(!this.appService.global.notSecure){
                token = response.token;
            }
            let exQueryParams = { contentType: 'application/dicom'};
            if (transferSyntax){
                exQueryParams["transferSyntax"] = {};
                exQueryParams["transferSyntax"] = transferSyntax;
            }
            console.log("keys",Object.keys(inst.wadoQueryParams));
            console.log("keys",Object.getOwnPropertyNames(inst.wadoQueryParams));
            console.log("keys",inst.wadoQueryParams);
            if(!this.appService.global.notSecure){
                // WindowRefService.nativeWindow.open(this.wadoURL(inst.wadoQueryParams, exQueryParams) + `&access_token=${token}`);
                url = this.service.wadoURL(this.studyWebService, inst.wadoQueryParams, exQueryParams) + `&access_token=${token}`;
            }else{
                // WindowRefService.nativeWindow.open(this.service.wadoURL(this.studyWebService.selectedWebService, inst.wadoQueryParams, exQueryParams));
                url = this.service.wadoURL(this.studyWebService, inst.wadoQueryParams, exQueryParams);
            }
            if(j4care.hasSet(inst, "attrs[00080018].Value[0]")){
                fileName = `${_.get(inst, "attrs[00080018].Value[0]")}.dcm`
            }
            j4care.downloadFile(url,fileName);
        });
    };

    createQueryParams(offset, limit, filter) {
        let params = {
            offset: offset,
            limit: limit
        };
        if(!this._filter.filterModel["onlyDefault"]){
            params["includefield"] = 'all';
        }

        for (let key in filter){
            if ((filter[key] || filter[key] === false) && key != "onlyDefault" && key != "webApp"){
                params[key] = filter[key];
            }
        }
        return params;
    }
    createStudyFilterParams(withoutPagination?:boolean) {
        let filter = this.getFilterClone();
        delete filter["onlyDefault"];
        delete filter['ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate'];
        delete filter['ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime'];
        if(!this._filter.filterModel["onlyDefault"]){
            filter["includefield"] = 'all';
        }
        if(withoutPagination){
            delete filter["size"];
            delete filter["offset"];
        }
        return filter;
    }

    onSubPaginationClick(e){
        console.log("e",e);
        if(e.level === "instance"){
            console.log("e.object",e.object);
            if(e.direction === "next"){
                this.getInstances(e.object,e.object.instances[0].offset + this._filter.filterModel.limit);
            }
            if(e.direction === "prev"){
                this.getInstances(e.object,e.object.instances[0].offset - this._filter.filterModel.limit);
            }

        }
        if(e.level === "series"){
            console.log("e.object",e.object);
            if(e.direction === "next"){
                this.getSeries(e.object,e.object.series[0].offset + this._filter.filterModel.limit);
            }
            if(e.direction === "prev"){
                this.getSeries(e.object,e.object.series[0].offset - this._filter.filterModel.limit);
            }

        }
    }

    getFilterClone():any{
        let filterModel =  _.clone(this._filter.filterModel);
        delete filterModel.webApp;
        return filterModel;
    }
    search(mode:('next'|'prev'|'current'), e){
        console.log("e",e);
        console.log("this",this.filter);
        if(this.studyWebService.selectedWebService){

            let filterModel =  this.getFilterClone();
            if(filterModel.limit){
                filterModel.limit++;
            }
            if(e.id === "submit"){
                if(!mode || mode === "current"){
                    filterModel.offset = 0;
                    this.getStudies(filterModel);
                }else{
                    if(mode === "next" && this.moreStudies){
                        filterModel.offset = filterModel.offset + this._filter.filterModel.limit;
                        this.getStudies(filterModel);
                    }
                    if(mode === "prev" && filterModel.offset > 0){
                        filterModel.offset = filterModel.offset - this._filter.filterModel.offset;
                        this.getStudies(filterModel);
                    }
                }
            }
            if(e.id === "count"){
                console.log("filter",this._filter.filterSchemaMain);
                this.getQuantity("count")
            }
            if(e.id === "size"){
                console.log("filter",this._filter.filterSchemaMain);
                this.getQuantity("size")
            }
    /*        }else{
                this.appService.showError("Calling AET is missing!");
            }*/
        }else{
            this.appService.showError("No web app service was selected!");
        }
    }
    showNoFilterWarning(queryParameters){
        let param =  _.clone(queryParameters);
        if (param['orderby'] == '-StudyDate,-StudyTime'){
            if (_.hasIn(param, ['ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate'])){
                delete param['ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate'];
            }
            if (_.hasIn(param, 'includefield')){
                delete param['includefield'];
            }
            if (_.hasIn(param, 'limit')){
                delete param['limit'];
            }
            if (_.hasIn(param, 'offset')){
                delete param['offset'];
            }
            if (_.hasIn(param, 'orderby')){
                delete param['orderby'];
            }
            if(_.size(param) < 1){
                return true
            }else{
                for(let p in param){
                    if(param[p] == "")
                        delete param[p];
                }
            }
            return (_.size(param) < 1) ? true : false;
        }else{
            return false;
        }
    }
    getQuantity(quantity:Quantity){
        let filterModel =  this.getFilterClone();
        if (this.showNoFilterWarning(filterModel)) {
            this.confirm({
                content: 'No filter are set, are you sure you want to continue?'
            }).subscribe(result => {
                if (result){
                    this.getQuantityService(filterModel, quantity);
                }
            });
        }else{
            this.getQuantityService(filterModel, quantity);
        }
    }
    getQuantityService(filterModel, quantity:Quantity){
        // let clonedFilters = _.cloneDeep(filters);
        delete filterModel.orderby;
        delete filterModel.limit;
        delete filterModel.offset;
        let quantityText = quantity === "count" ? "COUNT": "SIZE";

        _.set(this._filter.filterSchemaMain.schema,[...this.filterButtonPath[quantity],...["quantityText"]], false);
        _.set(this._filter.filterSchemaMain.schema,[...this.filterButtonPath[quantity],...["text"]], quantityText);
        _.set(this._filter.filterSchemaMain.schema,[...this.filterButtonPath[quantity],...["showDynamicLoader"]], true);
        this.service.getStudies(filterModel, this.studyWebService.selectedWebService, <DicomResponseType>quantity).subscribe(studyCount=>{
            console.log("studyCount",studyCount);
            let value = studyCount[quantity];
            if(quantity === "size"){
                value = j4care.convertBtoHumanReadable(value,1);
            }
            _.set(this._filter.filterSchemaMain.schema,[...this.filterButtonPath[quantity],...["showRefreshIcon"]], false);
            _.set(this._filter.filterSchemaMain.schema,[...this.filterButtonPath[quantity],...["showDynamicLoader"]], false);
            _.set(this._filter.filterSchemaMain.schema,[...this.filterButtonPath[quantity],...["text"]], `( ${this.largeIntFormat.transform(value)} ) ${quantityText}`);
        },err=>{
            j4care.log("Something went wrong on search", err);
            _.set(this._filter.filterSchemaMain.schema,[...this.filterButtonPath[quantity],...["showRefreshIcon"]], true);
            _.set(this._filter.filterSchemaMain.schema,[...this.filterButtonPath[quantity],...["showDynamicLoader"]], false);
            _.set(this._filter.filterSchemaMain.schema,[...this.filterButtonPath[quantity],...["text"]], quantityText);
            this.httpErrorHandler.handleError(err);
        })
    }


    getStudies(filterModel){
        this.cfpLoadingBar.start();
        this.searchCurrentList = "";
        filterModel['includefield'] = 'all';
        this.service.getStudies(filterModel, this.studyWebService.selectedWebService)
            .subscribe(res => {
                this.patients = [];
                this._filter.filterModel.offset = filterModel.offset;
                if(res){
                    this.setTopToTableHeder();
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
                        study = new StudyDicom(
                            studyAttrs,
                            patient,
                            this._filter.filterModel.offset + index
                        );
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
                this.cfpLoadingBar.complete();
                console.log("this.patients", this.patients);
            }, err => {
                j4care.log("Something went wrong on search", err);
                this.httpErrorHandler.handleError(err);
                this.cfpLoadingBar.complete();
            });
    }
    getSeries(study:StudyDicom, offset){
        console.log('in query sersies study=', study);
        this.cfpLoadingBar.start();
        let filters = this.getFilterClone();

        if (offset < 0) offset = 0;
        filters["offset"] = offset;

        if(filters.limit){
            filters.limit++;
        }
        filters['includefield'] = 'all';
        delete filters.aet;
        filters["orderby"] = 'SeriesNumber';
        this.service.getSeries(study.attrs['0020000D'].Value[0], filters, this.studyWebService.selectedWebService)
            .subscribe((res)=>{
            if (res){
                let hasMore = res.length > this._filter.filterModel.limit;
                if (res.length === 0){
                    this.appService.setMessage( {
                        'title': 'Info',
                        'text': 'No matching series found!',
                        'status': 'info'
                    });
                    console.log('in reslength 0');
                }else{

                    study.series = res.map((attrs, index) =>{
                        return new SeriesDicom(
                            study,
                            attrs,
                            offset + index,
                            hasMore,
                            hasMore || offset > 0
                        );
                    });
                    if (hasMore) {
                        study.series.pop();
                    }
                    console.log("study",study);
                    console.log("patients",this.patients);
                    // StudiesService.trim(this);
                    study.showSeries = true;
                }
            }else{
                this.appService.setMessage( {
                    'title': 'Info',
                    'text': 'No matching series found!',
                    'status': 'info'
                });
            }
            this.cfpLoadingBar.complete();
        },(err)=>{
                j4care.log("Something went wrong on search", err);
                this.httpErrorHandler.handleError(err);
                this.cfpLoadingBar.complete();
        });
    }

    getInstances(series:SeriesDicom, offset){
        // console.log('in query Instances serie=', series);
        this.cfpLoadingBar.start();
        // if (series.offset < 0) series.offset = 0;
        if (offset < 0) offset = 0;
        // let callingAet = new Aet(this._filter.filterModel.aet);
        let filters = this.getFilterClone();
        if(filters.limit){
            filters.limit++;
        }
        filters["offset"] = offset;
        filters['includefield'] = 'all';
        delete filters.aet;
        filters["orderby"] = 'InstanceNumber';
        this.service.getInstances(series.attrs['0020000D'].Value[0], series.attrs['0020000E'].Value[0], filters, this.studyWebService.selectedWebService)
            .subscribe((res)=>{
                if (res){
                    let hasMore = res.length > this._filter.filterModel.limit;
                    series.instances = res.map((attrs, index) => {
                        let numberOfFrames = j4care.valueOf(attrs['00280008']),
                            gspsQueryParams:GSPSQueryParams[] = this.service.createGSPSQueryParams(attrs),
                            video = this.service.isVideo(attrs),
                            image = this.service.isImage(attrs);
                        return new InstanceDicom(
                            series,
                            offset + index,
                            attrs,
                            new WadoQueryParams(attrs['0020000D'].Value[0],attrs['0020000E'].Value[0], attrs['00080018'].Value[0]),
                            video,
                            image,
                            numberOfFrames,
                            gspsQueryParams,
                            this.service.createArray(video || numberOfFrames || gspsQueryParams.length || 1),
                            1,
                            this._filter.filterModel.limit || 20,
                            hasMore,
                            hasMore || offset > 0
                        )
                    });
                    if(hasMore){
                        series.instances.pop();
                    }
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
    entryFilterChanged(e?){
        console.log("e",e);
        console.log("this.studyWebService",this.studyWebService);
        // this.selectedWebAppService = _.get(this.filter,"filterModel.webApp");
/*        if(this.studyWebService.selectedDevice.dicomDeviceName != this.filter.filterEntryModel["device"] && this.filter.filterEntryModel["device"] && this.filter.filterEntryModel["device"] != ''){
            this.deviceConfigurator.getDevice(this.filter.filterEntryModel["device"]).subscribe(device=>{
                this.studyWebService.selectedDeviceObject = device;
                this._filter.filterSchemaEntry = this.service.getEntrySchema(this.studyWebService.devicesDropdown, this.studyWebService.getDcmWebAppServicesDropdown(["QIDO_RS"]));
            });
            this._filter.filterEntryModel["webService"] = undefined;
            this.studyWebService.dcmWebAppServices = undefined;
        }
        if(!this.selectedWebAppService || this.selectedWebAppService.dcmWebAppName != this.filter.filterEntryModel["webService"]){
            this.studyWebService.setSelectedWebAppByString(this.filter.filterEntryModel["webService"]);
        }*/
    }

    filterChanged(){
        if(this.studyWebService.selectedWebService != _.get(this.filter,"filterModel.webApp")){
            this.studyWebService.selectedWebService = _.get(this.filter,"filterModel.webApp");
            this.internal = !(this.appService.archiveDeviceName && this.studyWebService.selectedWebService.dicomDeviceName && this.studyWebService.selectedWebService.dicomDeviceName != this.appService.archiveDeviceName);
/*            this.moreFunctionConfig.options = this.moreFunctionConfig.options.filter(option=>{
                console.log("option",option);
                if(option.value === "retrieve_multiple"){
                    return !this.internal;
                }else{
                    return true;
                }
            });*/
            // console.log("test",test);
            this.setTrash();
            this.patients = [];
        }
        // this.tableParam.tableSchema  = this.service.PATIENT_STUDIES_TABLE_SCHEMA(this, this.actions, {trashActive:this.trash.active});
    }
    moreFunctionFilterPipe(value, internal){
        console.log("value",value);
        return value.filter(option=>{
            console.log("option",option);
            switch (option.value) {
                case "retrieve_multiple":
                    return !internal;
                case "export_multiple":
                    return internal;
                case "upload_dicom":
                    return internal;
                case "permanent_delete":
                    return internal;
                case "export_multiple":
                    return internal;
            }
            return true;
        });
    }

    setSchema(){
        this._filter.filterSchemaMain.lineLength = undefined;
        this._filter.filterSchemaExpand.lineLength = undefined;
        this._filter.filterSchemaMain  = this.service.getFilterSchema(
            this.studyConfig.tab,
            this.applicationEntities.aes,
            this._filter.quantityText,
            'main',
            this.studyWebService.webServices.filter((webApp:DcmWebApp)=>webApp.dcmWebServiceClass.indexOf("QIDO_RS") > -1)
        );
        this._filter.filterSchemaExpand  = this.service.getFilterSchema(this.studyConfig.tab, this.applicationEntities.aes,this._filter.quantityText,'expand');
        this.filterButtonPath.count = j4care.getPath(this._filter.filterSchemaMain.schema,"id", "count");
        this.filterButtonPath.size = j4care.getPath(this._filter.filterSchemaMain.schema,"id", "size");
        this.filterButtonPath.count.pop();
        this.filterButtonPath.size.pop();
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
/*
    getApplicationEntities(){
        if(!this.applicationEntities.aetsAreSet){
            Observable.forkJoin(
                this.service.getAes().map(aes=> aes.map(aet=> new Aet(aet))),
                this.service.getAets().map(aets=> aets.map(aet => new Aet(aet))),
            )
            .subscribe((res)=>{
/!*                [0,1].forEach(i=>{
                    res[i] = j4care.extendAetObjectWithAlias(res[i]);
                    ["external","internal"].forEach(location=>{
                      this.applicationEntities.aes[location] = this.permissionService.filterAetDependingOnUiConfig(res[i],location);
                      this.applicationEntities.aets[location] = this.permissionService.filterAetDependingOnUiConfig(res[i],location);
                      this.applicationEntities.aetsAreSet = true;
                    })
                });*!/
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
*/

/*    getDevices(){
        this.service.getDevices()
            .subscribe(devices=>{
                if(_.hasIn(this.appService,"global.myDevice") && this.appService.deviceName && this.appService.deviceName === this.appService.global.myDevice.dicomDeviceName){
                    this.studyWebService = new StudystudyWebServiceModel({
                        devices:devices,
                        selectedDeviceObject:this.appService.global.myDevice
                    });
                    // this.studyWebService.setSelectedWebAppByString(this.appService.deviceName);
                    this.filter.filterEntryModel["device"] = this.appService.deviceName;
                    // this.entryFilterChanged();
                    this.initExporters(2);
                    this.initRjNotes(2);
                }else{
                    this.studyWebService = new StudystudyWebServiceModel({devices:devices});
                }
                this.setSchema();
                // this.getApplicationEntities();
        },err=>{
            j4care.log("Something went wrong on getting Devices",err);
            this.httpErrorHandler.handleError(err);
        })
    }*/

    createPatient(){
        let config:{saveLabel:string,titleLabel:string} = {
            saveLabel:'CREATE',
            titleLabel:'Create new patient'
        };
        let newPatient: any = {
            'attrs': {
                '00100010': { 'vr': 'PN', 'Value': [{
                        Alphabetic: ''
                    }]},
                '00100020': { 'vr': 'LO', 'Value': ['']},
                '00100021': { 'vr': 'LO', 'Value': ['']},
                '00100030': { 'vr': 'DA', 'Value': ['']},
                '00100040': { 'vr': 'CS', 'Value': ['']}
            }
        };
        this.modifyPatient(newPatient, 'create', config);
    };

    editPatient(patient){
        let config:{saveLabel:string,titleLabel:string} = {
            saveLabel:'SAVE',
            titleLabel:'Edit patient'
        };
        config.titleLabel += ((_.hasIn(patient, 'attrs.00100010.Value.0.Alphabetic')) ? '<b>' + patient.attrs['00100010'].Value[0]['Alphabetic'] + '</b>' : ' ');
        config.titleLabel += ((_.hasIn(patient, 'attrs.00100020.Value.0')) ? ' with ID: <b>' + patient.attrs['00100020'].Value[0] + '</b>' : '');
        this.modifyPatient(patient, 'edit', config);
    };

    modifyPatient(patient, mode:("edit"|"create") , config?:{saveLabel:string,titleLabel:string}){
        let originalPatientObject;
        if(mode === "edit"){
            originalPatientObject = _.cloneDeep(patient);
        }
        this.lastPressedCode = 0;
        this.config.viewContainerRef = this.viewContainerRef;
        this.service.getPatientIod().subscribe((iod) => {
            this.service.patientIod = iod;

            this.service.initEmptyValue(patient.attrs);
            this.dialogRef = this.dialog.open(EditPatientComponent, {
                height: 'auto',
                width: '90%'
            });

            this.dialogRef.componentInstance.mode = mode;
            this.dialogRef.componentInstance.patient = patient;
            this.dialogRef.componentInstance.dropdown = this.service.getArrayFromIod(iod);
            this.dialogRef.componentInstance.iod = this.service.replaceKeyInJson(iod, 'items', 'Value');
            this.dialogRef.componentInstance.saveLabel = config.saveLabel;
            this.dialogRef.componentInstance.titleLabel = config.titleLabel;
            this.dialogRef.afterClosed().subscribe(result => {
                if (result){
                    if(mode === "create"){
                        this.service.modifyPatient(undefined,patient.attrs,this.studyWebService).subscribe(res=>{
                            this.appService.showMsg("Patient created successfully");
                        },err=>{
                            this.httpErrorHandler.handleError(err);
                        });
                    }else{
                        this.service.modifyPatient(this.service.getPatientId(originalPatientObject.attrs),patient.attrs,this.studyWebService).subscribe(res=>{
                            this.appService.showMsg("Patient updated successfully");
                        },err=>{
                            _.assign(patient, originalPatientObject);
                            this.httpErrorHandler.handleError(err);
                        });
                    }
                }else{
                    _.assign(patient, originalPatientObject);
                }
                this.dialogRef = null;
            });
        }, (err) => {
            this.httpErrorHandler.handleError(err);
            console.log('error', err);
        });
    };

    editStudy(study){
        let config:{saveLabel:string,titleLabel:string} = {
            saveLabel:'SAVE',
            titleLabel:'Edit study of patient '
        };
        config.titleLabel += ((_.hasIn(study, 'attrs.00100010.Value.0.Alphabetic')) ? '<b>' + study.attrs['00100010'].Value[0]['Alphabetic'] + '</b>' : ' ');
        config.titleLabel += ((_.hasIn(study, 'attrs.00100020.Value.0')) ? ' with ID: <b>' + study.attrs['00100020'].Value[0] + '</b>' : '');
        this.modifyStudy(study, 'edit', config);
    };
    modifyStudy(study, mode, config?:{saveLabel:string,titleLabel:string}){
        let $this = this;
        this.config.viewContainerRef = this.viewContainerRef;
        let originalStudyObject = _.cloneDeep(study);
        if (mode === 'edit'){
            _.forEach(study.attrs, function(value, index) {
                let checkValue = '';
                if (value.Value && value.Value.length){
                    checkValue = value.Value.join('');
                }
                if (!(value.Value && checkValue != '')){
                    delete study.attrs[index];
                }
            });
        }
        this.lastPressedCode = 0;
        this.service.getStudyIod()
            .map(iod=>{
                this.service.patientIod = iod;
                return iod;
            })
            .subscribe((res) => {
                let iod = $this.service.replaceKeyInJson(res, 'items', 'Value');
                let studyFiltered = _.cloneDeep(study);
                studyFiltered.attrs = new ComparewithiodPipe().transform(study.attrs, iod);
                $this.service.initEmptyValue(studyFiltered.attrs);
                $this.dialogRef = $this.dialog.open(EditStudyComponent, {
                    height: 'auto',
                    width: '90%'
                });
                $this.dialogRef.componentInstance.study = studyFiltered;
                $this.dialogRef.componentInstance.dropdown = $this.service.getArrayFromIod(res);
                $this.dialogRef.componentInstance.iod = iod;
                $this.dialogRef.componentInstance.saveLabel = config.saveLabel;
                $this.dialogRef.componentInstance.titleLabel = config.titleLabel;
                $this.dialogRef.componentInstance.mode = mode;
                $this.dialogRef.afterClosed().subscribe(result => {
                    if (result){
                        $this.service.clearPatientObject(studyFiltered.attrs);
                        $this.service.convertStringToNumber(studyFiltered.attrs);
                        let local = {};
                        $this.service.appendPatientIdTo(study.attrs, local);
                        _.forEach(studyFiltered.attrs, function(m, i){
                            if (res[i]){
                                local[i] = m;
                            }
                        });
                        this.service.modifyStudy(local,this.studyWebService, new HttpHeaders({ 'Content-Type': 'application/dicom+json' })).subscribe(
                            () => {
                                $this.appService.showMsg('Study saved successfully!');
                            },
                            (err) => {
                                $this.httpErrorHandler.handleError(err);
                            }
                        );
                    }else{
                        _.assign(study, originalStudyObject);
                    }
                    $this.dialogRef = null;
                });
            });
    };

    setExpiredDate(study){
        this.setExpiredDateQuery(study,false);
    }

    setExpiredDateQuery(study, infinit){
        this.confirm(this.service.getPrepareParameterForExpiriationDialog(study,this.exporters, infinit)).subscribe(result => {
            if(result){
                this.cfpLoadingBar.start();
                if(result.schema_model.expiredDate){
                    this.service.setExpiredDate(this.studyWebService, _.get(study,"attrs.0020000D.Value[0]"), result.schema_model.expiredDate, result.schema_model.exporter).subscribe(
                        (res)=>{
                            _.set(study,"attrs.77771023.Value[0]",result.schema_model.expiredDate);
                            _.set(study,"attrs.77771023.vr","DA");
                            this.appService.showMsg( 'Expired date set successfully!');
                            this.cfpLoadingBar.complete();
                        },
                        (err)=>{
                            this.httpErrorHandler.handleError(err);
                            this.cfpLoadingBar.complete();
                        }
                    );
                }else{
                    this.appService.showError("Expired date is required!");
                }
            }
        });
    }
    deleteRejectedInstances(){
        let result = {
            reject: undefined
        };
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(DeleteRejectedInstancesComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.rjnotes = this.trash.rjnotes;
        this.dialogRef.componentInstance.results = result;
        this.dialogRef.afterClosed().subscribe(re => {
            console.log('afterclose re', re);
            this.cfpLoadingBar.start();
            if (re) {

                let params = {};
                if (re.rejectedBefore){
                    params['rejectedBefore'] = re.rejectedBefore;
                }
                if (re.keepRejectionNote === true){
                    params['keepRejectionNote'] = re.keepRejectionNote;
                }
                console.log('params1', this.appService.param(params));
                console.log('params', params);

                this.service.deleteRejectedInstances(re.reject, params)
                .subscribe(
                        (res) => {
                            console.log('in res', res);
                            this.cfpLoadingBar.complete();
                            // this.fireRightQuery();
                            if (_.hasIn(res, 'deleted')){
                                this.appService.setMessage({
                                    'title': 'Info',
                                    'text': res.deleted + ' instances deleted successfully!',
                                    'status': 'info'
                                });
                            }else{
                                this.appService.setMessage({
                                    'title': 'Warning',
                                    'text': 'Process executed successfully',
                                    'status': 'warning'
                                });
                            }
                        },
                        (err) => {
                            console.log('error', err);
                            this.httpErrorHandler.handleError(err);
                        });
            }
            this.cfpLoadingBar.complete();
            this.dialogRef = null;
        });

    }

    deleteStudy(study){
        console.log('study', study);
        // if(study.attrs['00201208'].Value[0] === 0){
        this.confirm({
            content: 'Are you sure you want to delete this study?'
        }).subscribe(result => {
            this.cfpLoadingBar.start();
            if (result){
                this.service.deleteStudy(_.get(study,"attrs['0020000D'].Value[0]"),this.studyWebService.selectedWebService)
/*                this.$http.delete(
                    '../aets/' + this.aet + '/rs/studies/' + study.attrs['0020000D'].Value[0]
                )*/
                .subscribe(
                    (response) => {
                        this.appService.showMsg('Study deleted successfully!');
                        this.cfpLoadingBar.complete();
                    },
                    (response) => {
                        this.httpErrorHandler.handleError(response);
                        this.cfpLoadingBar.complete();
                    }
                );
            }
            this.cfpLoadingBar.complete();
        });
    };

    rejectStudy(study) {
        let $this = this;
        if (this.trash.active) {
            this.service.rejectStudy(study.attrs, this.studyWebService.selectedWebService, this.trash.rjcode.codeValue + '^' + this.trash.rjcode.codingSchemeDesignator )
            .subscribe(
                (res) => {
                    $this.appService.setMessage({
                        'title': 'Info',
                        'text': 'Study restored successfully!',
                        'status': 'info'
                    });
                    // $this.queryStudies($this.patients[0].offset);
                },
                (response) => {
                    $this.httpErrorHandler.handleError(response);
                    console.log('response', response);
                }
            );
        }else{
            let select: any = [];
            _.forEach(this.trash.rjnotes, (m, i) => {
                select.push({
                    title: m.codeMeaning,
                    value: m.codeValue + '^' + m.codingSchemeDesignator,
                    label: m.label
                });
            });
            let parameters: any = {
                content: 'Select rejected type',
                select: select,
                result: {select: this.trash.rjnotes[0].codeValue + '^' + this.trash.rjnotes[0].codingSchemeDesignator},
                saveButton: 'REJECT'
            };
            this.confirm(parameters).subscribe(result => {
                if (result) {
                    $this.cfpLoadingBar.start();
                    this.service.rejectStudy(study.attrs, this.studyWebService.selectedWebService, parameters.result.select )
                        .subscribe(
                        (response) => {
                            $this.appService.setMessage({
                                'title': 'Info',
                                'text': 'Study rejected successfully!',
                                'status': 'info'
                            });

                            // patients.splice(patientkey,1);
                            $this.cfpLoadingBar.complete();
                        },
                        (err) => {
                            $this.httpErrorHandler.handleError(err);
                            // angular.element("#querypatients").trigger('click');
                            $this.cfpLoadingBar.complete();
                        }
                    );
                } else {
                    console.log('else', result);
                    console.log('parameters', parameters);
                }
            });
        }
    };
    rejectSeries(series) {
        let $this = this;
        if (this.trash.active) {
            this.service.rejectSeries(series.attrs, this.studyWebService.selectedWebService, this.trash.rjcode.codeValue + '^' + this.trash.rjcode.codingSchemeDesignator )
            .subscribe(
                (res) => {
                    // $scope.queryStudies($scope.studies[0].offset);
                    $this.appService.setMessage({
                        'title': 'Info',
                        'text': 'Series restored successfully!',
                        'status': 'info'
                    });
                    // $this.queryStudies($this.patients[0].offset);
                },
                (response) => {
                    $this.httpErrorHandler.handleError(response);
                    console.log('response', response);
                }
            );
        }else{
            let select: any = [];
            _.forEach(this.trash.rjnotes, (m, i) => {
                select.push({
                    title: m.codeMeaning,
                    value: m.codeValue + '^' + m.codingSchemeDesignator,
                    label: m.label
                });
            });
            let parameters: any = {
                content: 'Select rejected type',
                select: select,
                result: {select: this.trash.rjnotes[0].codeValue + '^' + this.trash.rjnotes[0].codingSchemeDesignator},
                saveButton: 'REJECT'
            };

            console.log('parameters', parameters);
            this.confirm(parameters).subscribe(result => {
                if (result) {
                    console.log('result', result);
                    console.log('parameters', parameters);
                    $this.cfpLoadingBar.start();
                    this.service.rejectSeries(series.attrs, this.studyWebService.selectedWebService, parameters.result.select )
                        .subscribe(
                        (response) => {
                            $this.appService.setMessage({
                                'title': 'Info',
                                'text': 'Series rejected successfully!',
                                'status': 'info'
                            });

                            // patients.splice(patientkey,1);
                            $this.cfpLoadingBar.complete();
                        },
                        (err) => {
                            $this.httpErrorHandler.handleError(err);
                            // angular.element("#querypatients").trigger('click');
                            $this.cfpLoadingBar.complete();
                        }
                    );
                } else {
                    console.log('else', result);
                    console.log('parameters', parameters);
                }
            });
        }
    };
    rejectInstance(instance) {
        let $this = this;
        if (this.trash.active) {
            this.service.rejectInstance(instance.attrs, this.studyWebService.selectedWebService, this.trash.rjcode.codeValue + '^' + this.trash.rjcode.codingSchemeDesignator )
                .subscribe(
                (res) => {
                    // $scope.queryStudies($scope.studies[0].offset);
                    $this.appService.setMessage({
                        'title': 'Info',
                        'text': 'Instance restored successfully!',
                        'status': 'info'
                    });
                    // $this.queryStudies($this.patients[0].offset);
                },
                (response) => {
                    $this.httpErrorHandler.handleError(response);
                    console.log('response', response);
                }
            );
        }else{

            let select: any = [];
            _.forEach(this.trash.rjnotes, (m, i) => {
                select.push({
                    title: m.codeMeaning,
                    value: m.codeValue + '^' + m.codingSchemeDesignator,
                    label: m.label
                });
            });
            let parameters: any = {
                content: 'Select rejected type',
                select: select,
                result: {select: this.trash.rjnotes[0].codeValue + '^' + this.trash.rjnotes[0].codingSchemeDesignator},
                saveButton: 'REJECT'
            };
            console.log('parameters', parameters);
            this.confirm(parameters).subscribe(result => {
                if (result) {
                    console.log('result', result);
                    console.log('parameters', parameters);
                    $this.cfpLoadingBar.start();
                    this.service.rejectInstance(instance.attrs, this.studyWebService.selectedWebService, parameters.result.select ).subscribe(
                        (response) => {
                            $this.appService.setMessage({
                                'title': 'Info',
                                'text': 'Instance rejected successfully!',
                                'status': 'info'
                            });

                            // patients.splice(patientkey,1);
                            $this.cfpLoadingBar.complete();
                        },
                        (err) => {
                            $this.httpErrorHandler.handleError(err);
                            // angular.element("#querypatients").trigger('click');
                            $this.cfpLoadingBar.complete();
                        }
                    );
                } else {
                    console.log('else', result);
                    console.log('parameters', parameters);
                }
            });
        }
    };

    setTrash(){
        if (this.studyWebService.selectedWebService.dicomAETitleObject && this.studyWebService.selectedWebService.dicomAETitleObject.dcmHideNotRejectedInstances){
            if (!this.trash.rjcode){
                this.service.getRejectNotes({dcmRevokeRejection:true})
                    .subscribe((res)=>{
                        this.trash.rjcode = res[0];
                    });
            }
            this.trash.active = true;
        }else{
            this.trash.active = false;
        }
        this.tableParam.tableSchema  = this.getSchema();
    };
    getSchema(){
        return this.service.checkSchemaPermission(this.service.PATIENT_STUDIES_TABLE_SCHEMA(this, this.actions, {
            trash:this.trash,
            selectedWebService: _.get(this.studyWebService,"selectedWebService"),
            tableParam:this.tableParam
        }));
    }


    retrieveMultipleStudies(){
        this.exporter(
            // `/aets/${this.aet}/dimse/${this.externalAET}/studies/query:${this.queryAET}/export/dicom:${destinationAET}`,
            '',
            'Retrieve matching studies depending on selected filters, from external C-MOVE SCP',
            '',
            'multiple-retrieve',
            {},
            ""
        );
    }
    exportMultipleStudies(){
        this.exporter(
            '',
            'Export all matching studies',
            'Studies will not be sent!',
            'multipleExport',
            {},
            "study"
        );
    }

    exportStudy(study) {
        this.exporter(
            this.service.studyURL(study.attrs, this.studyWebService.selectedWebService),
            'Export study',
            'Study will not be sent!',
            'single',
            study.attrs,
            "study"
        );
    };
    exportSeries(series) {
        this.exporter(
            this.service.seriesURL(series.attrs, this.studyWebService.selectedWebService),
            'Export series',
            'Series will not be sent!',
            'single',
            series.attrs,
            "series"
        );
    };
    exportInstance(instance) {
        this.exporter(
            this.service.instanceURL(instance.attrs, this.studyWebService.selectedWebService),
            'Export instance',
            'Instance will not be sent!',
            'single',
            instance.attrs,
            "instance"
        );
    };
    exporter(url, title, warning, mode, objectAttr, dicomMode){
        let $this = this;
        let id;
        let urlRest;
        let noDicomExporters = [];
        let dicomPrefixes = [];
        _.forEach(this.exporters, (m, i) => {
            if (m.id.indexOf(':') > -1){
                dicomPrefixes.push(m);
            }else{
                noDicomExporters.push(m);
            }
        });
        this.config.viewContainerRef = this.viewContainerRef;
        let config = {
            height: 'auto',
            width: '500px'
        };
        if(mode === "multiple-retrieve"){
            config = {
                height: 'auto',
                width: '600px'
            };
        }
        this.dialogRef = this.dialog.open(ExportDialogComponent, config);
        this.dialogRef.componentInstance.noDicomExporters = noDicomExporters;
        this.dialogRef.componentInstance.dicomPrefixes = dicomPrefixes;
        this.dialogRef.componentInstance.externalInternalAetMode = this.internal ? "internal" : "external";
        this.dialogRef.componentInstance.title = title;
        this.dialogRef.componentInstance.mode = mode;
        this.dialogRef.componentInstance.queues = this.queues;
        this.dialogRef.componentInstance.warning = warning;
        this.dialogRef.componentInstance.newStudyPage = true;
        // this.dialogRef.componentInstance.count = this.count;
 /*       if(!internal) {
            this.dialogRef.componentInstance.preselectedExternalAET = this.externalInternalAetModel.dicomAETitle;
        }*/
        this.dialogRef.afterClosed().subscribe(result => {
            if (result){
                let batchID = "";
                let params = {};
                if(result.batchID)
                    batchID = `batchID=${result.batchID}&`;
                $this.cfpLoadingBar.start();
                if(mode === "multiple-retrieve"){
                     urlRest = `${
                        j4care.getUrlFromDcmWebApplication(
                            this.service.getWebAppFromWebServiceClassAndSelectedWebApp(
                                this.studyWebService, 
                                "MOVE_MATCHING", 
                                "MOVE_MATCHING"
                            )
                        )}/studies/export/dicom:${
                            result.selectedAet
                        }?${
                            batchID
                        }${
                            this.appService.param(this.createStudyFilterParams())
                        }`;
                    console.log("urlrest",urlRest);
                }else{
                    if(mode === 'multipleExport'){
                        let checkbox = `${(result.checkboxes['only-stgcmt'] && result.checkboxes['only-stgcmt'] === true)? 'only-stgcmt=true':''}${(result.checkboxes['only-ian'] && result.checkboxes['only-ian'] === true)? 'only-ian=true':''}`;
                        if(checkbox != '' && this.appService.param(this.createStudyFilterParams()) != '')
                            checkbox = '&' + checkbox;
                        urlRest = `${this.service.getDicomURL("export",this.studyWebService.selectedWebService)}/${result.selectedExporter}?${batchID}${this.appService.param(this.createStudyFilterParams())}${checkbox}`;
                    }else{
                        //SINGLE
                        console.log("deviceName",this.appService.archiveDeviceName);
                        console.log("deviceName",this.studyWebService.selectedWebService.dicomDeviceName);
                        if(this.appService.archiveDeviceName && this.studyWebService.selectedWebService.dicomDeviceName && this.studyWebService.selectedWebService.dicomDeviceName != this.appService.archiveDeviceName){
                        // if(this.studyWebService.selectedWebService){
                            // let param = result.dcmQueueName ? `?${batchID}dcmQueueName=${result.dcmQueueName}` : '';
                            if(result.dcmQueueName){
                                params['dcmQueueName'] = result.dcmQueueName
                            }
                            urlRest = `${url}/export/dicom:${result.selectedAet}${j4care.param(params)}`;
                        }else{
                            if (result.exportType === 'dicom'){
                                //id = result.dicomPrefix + result.selectedAet;
                                id = 'dicom:' + result.selectedAet;
                            }else{
                                id = result.selectedExporter;
                            }
                            urlRest = url  + '/export/' + id + '?'+ batchID + this.appService.param(result.checkboxes);
                        }
                    }
                }
                this.service.export(urlRest)
                    .subscribe(
                    (result) => {
                        $this.appService.setMessage({
                            'title': 'Info',
                            'text': $this.service.getMsgFromResponse(result,'Command executed successfully!'),
                            'status': 'info'
                        });
                        $this.cfpLoadingBar.complete();
                    },
                    (err) => {
                        console.log("err",err);
                        $this.appService.setMessage({
                            'title': 'Error ' + err.status,
                            'text': $this.service.getMsgFromResponse(err),
                            'status': 'error'
                        });
                        $this.cfpLoadingBar.complete();
                    }
                );
            }
        });
    }
    storageVerification(){
        this.confirm({
            content: 'Schedule Storage Verification of matching Studies',
            doNotSave:true,
            form_schema:[
                [
                    [
                        {
                            tag:"label",
                            text:"Failed storage verification"
                        },
                        {
                            tag:"checkbox",
                            filterKey:"storageVerificationFailed"
                        }
                    ],[
                    {
                        tag:"label",
                        text:"Verification Policy"
                    },
                    {
                        tag:"select",
                        options:[
                            {
                                value:"DB_RECORD_EXISTS",
                                text:"DB_RECORD_EXISTS",
                                title:"Check for existence of DB records"
                            },
                            {
                                value:"OBJECT_EXISTS",
                                text:"OBJECT_EXISTS",
                                title:"check if object exists on Storage System"
                            },
                            {
                                value:"OBJECT_SIZE",
                                text:"OBJECT_SIZE",
                                title:"check size of object on Storage System"
                            },
                            {
                                value:"OBJECT_FETCH",
                                text:"OBJECT_FETCH",
                                title:"Fetch object from Storage System"
                            },
                            {
                                value:"OBJECT_CHECKSUM",
                                text:"OBJECT_CHECKSUM",
                                title:"recalculate checksum of object on Storage System"
                            },
                            {
                                value:"S3_MD5SUM",
                                text:"S3_MD5SUM",
                                title:"Check MD5 checksum of object on S3 Storage System"
                            }
                        ],
                        showStar:true,
                        filterKey:"storageVerificationPolicy",
                        description:"Verification Policy",
                        placeholder:"Verification Policy"
                    }
                ],[
                    {
                        tag:"label",
                        text:"Update Location DB"
                    },
                    {
                        tag:"checkbox",
                        filterKey:"storageVerificationUpdateLocationStatus"
                    }
                ],[
                    {
                        tag:"label",
                        text:"Batch ID"
                    },
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"batchID",
                        description:"Batch ID",
                        placeholder:"Batch ID"
                    }
                ]
                ]
            ],
            result: {
                schema_model: {}
            },
            saveButton: 'SAVE'
        }).subscribe((ok)=>{
            if(ok){
                this.cfpLoadingBar.start();
                this.service.scheduleStorageVerification(_.merge(ok.schema_model , this.createStudyFilterParams(true)), this.studyWebService).subscribe(res=>{
                    console.log("res",res);
                    this.cfpLoadingBar.complete();
                    this.appService.showMsg('Storage Verification scheduled successfully!');
                },err=>{
                    this.cfpLoadingBar.complete();
                    this.httpErrorHandler.handleError(err);
                });
            }
        });
    }

    storageCommitmen(mode, object){
        console.log('object', object);
        this.service.getStorageSystems().subscribe(storages=>{
            this.confirm({
                content: 'Schedule Storage Verification',
                doNotSave:true,
                form_schema:[
                    [
                        [
                            {
                                tag:"label",
                                text:"Verification Policy"
                            },
                            {
                                tag:"select",
                                options:[
                                    {
                                        value:"DB_RECORD_EXISTS",
                                        text:"DB_RECORD_EXISTS",
                                        title:"Check for existence of DB records"
                                    },
                                    {
                                        value:"OBJECT_EXISTS",
                                        text:"OBJECT_EXISTS",
                                        title:"check if object exists on Storage System"
                                    },
                                    {
                                        value:"OBJECT_SIZE",
                                        text:"OBJECT_SIZE",
                                        title:"check size of object on Storage System"
                                    },
                                    {
                                        value:"OBJECT_FETCH",
                                        text:"OBJECT_FETCH",
                                        title:"Fetch object from Storage System"
                                    },
                                    {
                                        value:"OBJECT_CHECKSUM",
                                        text:"OBJECT_CHECKSUM",
                                        title:"recalculate checksum of object on Storage System"
                                    },
                                    {
                                        value:"S3_MD5SUM",
                                        text:"S3_MD5SUM",
                                        title:"Check MD5 checksum of object on S3 Storage System"
                                    }
                                ],
                                showStar:true,
                                filterKey:"storageVerificationPolicy",
                                description:"Verification Policy",
                                placeholder:"Verification Policy"
                            }
                        ],[
                        {
                            tag:"label",
                            text:"Update Location DB"
                        },
                        {
                            tag:"checkbox",
                            filterKey:"storageVerificationUpdateLocationStatus"
                        }
                    ],[
                        {
                            tag:"label",
                            text:"Storage ID"
                        },{
                            tag:"select",
                            options:storages.map(storage=> new SelectDropdown(storage.dcmStorageID, storage.dcmStorageID)),
                            showStar:true,
                            filterKey:"storageVerificationStorageID",
                            description:"Storage IDs",
                            placeholder:"Storage IDs"
                        }
                    ]
                    ]
                ],
                result: {
                    schema_model: {}
                },
                saveButton: 'QUERY'
            }).subscribe(ok=> {
                if (ok) {
                    this.cfpLoadingBar.start();
                    this.service.verifyStorage(object.attrs, this.studyWebService, mode, ok.schema_model)
                        .subscribe(
                            (response) => {
                                // console.log("response",response);
                                let failed = (response[0]['00081198'] && response[0]['00081198'].Value) ? response[0]['00081198'].Value.length : 0;
                                let success = (response[0]['00081199'] && response[0]['00081199'].Value) ? response[0]['00081199'].Value.length : 0;
                                let msgStatus = 'Info';
                                if (failed > 0 && success > 0) {
                                    msgStatus = 'Warning';
                                    this.appService.setMessage({
                                        'title': msgStatus,
                                        'text': failed + ' of ' + (success + failed) + ' failed!',
                                        'status': msgStatus.toLowerCase()
                                    });
                                    console.log(failed + ' of ' + (success + failed) + ' failed!');
                                }
                                if (failed > 0 && success === 0) {
                                    msgStatus = 'Error';
                                    this.appService.setMessage({
                                        'title': msgStatus,
                                        'text': 'all (' + failed + ') failed!',
                                        'status': msgStatus.toLowerCase()
                                    });
                                    console.log('all ' + failed + 'failed!');
                                }
                                if (failed === 0) {
                                    console.log(success + ' verified successfully 0 failed!');
                                    this.appService.setMessage({
                                        'title': msgStatus,
                                        'text': success + ' verified successfully\n 0 failed!',
                                        'status': msgStatus.toLowerCase()
                                    });
                                }
                                this.cfpLoadingBar.complete();
                            },
                            (response) => {
                                this.httpErrorHandler.handleError(response);
                                this.cfpLoadingBar.complete();
                            }
                        );
                }
            });
        },(err)=>{
            this.httpErrorHandler.handleError(err);
        });
    };
    initRjNotes(retries) {
        this.service.getRejectNotes()
            .subscribe(res => {
                    this.trash.rjnotes = res.sort(function (a, b) {
                        if (a.codeValue === '113039' && a.codingSchemeDesignator === 'DCM')
                            return -1;
                        if (b.codeValue === '113039' && b.codingSchemeDesignator === 'DCM')
                            return 1;
                        return 0;
                    });
                    this.trash.reject = this.trash.rjnotes[0].codeValue + '^' + this.trash.rjnotes[0].codingSchemeDesignator;
                },
                err => {
                    if (retries)
                        this.initRjNotes(retries - 1);
                });
    }
    initWebApps(){
        let aetsTemp;
        this.service.getAets()
            .map((aets:Aet[])=>{
                aetsTemp = aets;
            })
            .switchMap(()=>{
                return this.service.getWebApps()
            })
            .subscribe(
                (webApps:DcmWebApp[])=> {
                    this.studyWebService = new StudyWebService({
                        webServices:webApps.map((webApp:DcmWebApp)=>{
                            aetsTemp.forEach((aet)=>{
                                if(webApp.dicomAETitle && webApp.dicomAETitle === aet.dicomAETitle){
                                    webApp.dicomAETitleObject = aet;
                                }
                            });
                            return webApp;
                        })
                    });
                    // this.getDevices();
                    this.setSchema();
                    this.initExporters(2);
                    this.initRjNotes(2);
                    this.getQueueNames();
                },
                (res)=> {

                });
    }
    initExporters(retries) {
        this.service.getExporters()
            .subscribe(
                (res)=> {
                    this.exporters = res;
                    /*                    if (res && res[0] && res[0].id){
                                            $this.exporterID = res[0].id;
                                        }*/
                    // $this.appService.setGlobal({exporterID:$this.exporterID});
                },
                (res)=> {
                    if (retries)
                        this.initExporters(retries - 1);
                });
    }
    getQueueNames(){
        this.service.getQueueNames().subscribe(names=>{
            this.queues = names.map(name=> new SelectDropdown(name.name, name.description));
        },err=>{
            this.httpErrorHandler.handleError(err);
        })
    }
    testSecure(){
        this.appService.isSecure().subscribe((res)=>{
            console.log("secured",res);
        })
    }

    testAet(){
        this.service.testAet("http://test-ng:8080/dcm4chee-arc/ui2/rs/aets", this.studyWebService.selectedWebService).subscribe(res=>{
            console.log("res",res);
        },err=>{
            console.log("err",err);
        });
        // this.service.test(this.selectedWebAppService);
    }
    testStudy(){
        this.service.testAet("http://test-ng:8080/dcm4chee-arc/aets/TEST/rs/studies?limit=21&offset=0&includefield=all", this.studyWebService.selectedWebService).subscribe(res=>{
            console.log("res",res);
        },err=>{
            console.log("err",err);
        });
        // this.service.test(this.selectedWebAppService);
    }

/*    get selectedWebAppService(): DcmWebApp {
        return this.studyWebService.selectedWebService;
    }

    set selectedWebAppService(value: DcmWebApp) {
        this.studyWebService.selectedWebService = value;
        this.setTrash();
    }*/
}
