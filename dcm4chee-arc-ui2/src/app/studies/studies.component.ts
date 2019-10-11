import {Component, ViewContainerRef, OnDestroy, trigger, transition, style, animate, OnInit} from '@angular/core';
import {Http, Headers, RequestOptionsArgs} from '@angular/http';
import {StudiesService} from './studies.service';
import {AppService} from '../app.service';
import {User} from '../models/user';
import {Globalvar} from '../constants/globalvar';
import * as _ from 'lodash';
import {MessagingComponent} from '../widgets/messaging/messaging.component';
import {SelectItem} from 'primeng/components/common/api';
import {MatDialogConfig, MatDialog, MatDialogRef} from '@angular/material';
import {EditPatientComponent} from '../widgets/dialogs/edit-patient/edit-patient.component';
import {EditMwlComponent} from '../widgets/dialogs/edit-mwl/edit-mwl.component';
import {CopyMoveObjectsComponent} from '../widgets/dialogs/copy-move-objects/copy-move-objects.component';
import {ConfirmComponent} from '../widgets/dialogs/confirm/confirm.component';
import {Subscription} from 'rxjs';
import {EditStudyComponent} from '../widgets/dialogs/edit-study/edit-study.component';
import {ComparewithiodPipe} from '../pipes/comparewithiod.pipe';
import {DeleteRejectedInstancesComponent} from '../widgets/dialogs/delete-rejected-instances/delete-rejected-instances.component';
import {DatePipe} from '@angular/common';
import {ExportDialogComponent} from '../widgets/dialogs/export/export.component';
import {UploadDicomComponent} from '../widgets/dialogs/upload-dicom/upload-dicom.component';
import {UploadFilesComponent} from "../widgets/dialogs/upload-files/upload-files.component";
import {WindowRefService} from "../helpers/window-ref.service";
import {FormatAttributeValuePipe} from "../pipes/format-attribute-value.pipe";
import {FormatDAPipe} from "../pipes/format-da.pipe";
import {FormatTMPipe} from "../pipes/format-tm.pipe";
import {HttpErrorHandler} from "../helpers/http-error-handler";
import {J4careHttpService} from "../helpers/j4care-http.service";
import {j4care} from "../helpers/j4care.service";
import {ViewerComponent} from "../widgets/dialogs/viewer/viewer.component";
import {PermissionService} from "../helpers/permissions/permission.service";
import {LoadingBarModule, LoadingBarService} from "@ngx-loading-bar/core";
import {ActivatedRoute} from "@angular/router";
import {SelectDropdown} from "../interfaces";
declare var Keycloak: any;
declare var $: any;
import "rxjs/add/operator/retry";
import {DropdownList} from "../helpers/form/dropdown-list";
import {RetrieveMonitoringService} from "../monitoring/external-retrieve/retrieve-monitoring.service";
import {HttpHeaders} from "@angular/common/http";
import {KeycloakService} from "../helpers/keycloak-service/keycloak.service";

@Component({
    selector: 'app-studies',
    templateUrl: './studies.component.html'
})
export class StudiesComponent implements OnDestroy,OnInit{

    // @ViewChildren(MessagingComponent) msg;
    queues;
    orderby = Globalvar.ORDERBY;
    orderbyExternal = Globalvar.ORDERBY_EXTERNAL;
    limit = 20;
    showClipboardHeaders = {
        'study': false,
        'series': false,
        'instance': false
    };
    showFilterWarning: boolean;
    debugpre = false;
    saveLabel = 'SAVE';
    titleLabel = 'Edit patient';
    rjcode = null;
    trashaktive = false;
    clipboard: any = {};
    showCheckboxes = false;
    disabled = {
        IssuerOfPatientID:false,
        LocalNamespaceEntityID:false
    };
    patientmode = false;
    withoutstudies = false;
    ExternalRetrieveAETchecked = false;
    StudyReceiveDateTime = {
        from: undefined,
        to: undefined
    };
    diffFilter = false;
    externalInternalAetMode = "internal";
    filter = {
        orderby: '-StudyDate,-StudyTime',
        ModalitiesInStudy: '',
        'ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate': '',
        'ScheduledProcedureStepSequence.ScheduledProcedureStepStatus': '',
        //returnempty: false,
        PatientSex: '',
        PatientName:'',
        PatientBirthDate: '',
        PatientID:'',
        StudyDate:'',
        IssuerOfPatientID:'',
        fuzzymatching:'',
        StudyTime:'',
        SplitStudyDateRange:'',
        compressionfailed:false,
        storageVerificationFailed:false,
        StudyInstanceUID:"",
        onlyDefault:false
    };
    diffQueue = false;
    missing = true;
    different = true;
    diffAttributeSet;
    batchID;
    comparefield;
    queryMode = 'queryStudies';
    moreDiffs;
    // ScheduledProcedureStepSequence: any = {
    //     ScheduledProcedureStepStartTime: {
    //         from: '',
    //         to: ''
    //     },
    //     ScheduledProcedureStepStartDate: {
    //         from: this.service.getTodayDate(),
    //         to: this.service.getTodayDate(),
    //         toObject: new Date(),
    //         fromObject: new Date()
    //     }
    // };
    moreMWL;
    morePatients;
    moreStudies;
    moreFunctionsButtons = false;
    opendropdown = false;
    addPatientAttribut = '';
    lastPressedCode;
    clipboardHasScrollbar = false;
    target;
    allAes;
    diffModeShow = false;
    // birthDate;
    clipBoardNotEmpty(){
        if (this.clipboard && ((_.size(this.clipboard.otherObjects) > 0) || (_.size(this.clipboard.patients) > 0))){
            return true;
        }else{
            return false;
        }
    }
    _ = _;
    jsonHeader = new HttpHeaders({ 'Content-Type': 'application/json' });
    aet1;
    aet2;
    count;
    size;
    studyDateChanged(){
        console.log('on studydate changed', this.studyDate);
        if (this.studyDate.from === '' && this.studyDate.to === ''){
            localStorage.setItem('dateset', 'no');
        }else if (this.studyDate.from != '' && this.studyDate.to != ''){
                localStorage.setItem('dateset', 'yes');
            }
    }
    clearStudyDate(){
        this.studyDate.fromObject = null;
        this.studyDate.toObject = null;
        this.studyDate.from = '';
        this.studyDate.to = '';
        this.filter.StudyDate = "";
    }

    clearForm(){
        _.forEach(this.filter, (m, i) => {
            if (i != 'orderby'){
                this.filter[i] = '';
            }
        });
        $('.single_clear').hide();
        this.clearStudyDate();
        // localStorage.setItem("dateset",false);
        this.studyDateChanged();
        this.studyTime.fromObject = null;
        this.studyTime.toObject = null;
        this.ExternalRetrieveAETchecked = null;
        this.studyTime.from = '';
        this.studyTime.to = '';
        this.StudyReceiveDateTime.from = undefined;
        this.StudyReceiveDateTime.to = undefined;
        // this.birthDate = {};
        // this.birthDate.object = null;
        // this.birthDate.opened = false;
/*        this.ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate.fromObject = null;
        this.ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate.toObject = null;
        this.ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate.from = '';
        this.ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate.to = '';
        this.ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime.fromObject = null;
        this.ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime.toObject = null;
        this.ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime.from = '';
        this.ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime.to = '';*/
    };
    options = {genders:
                        [
                            {
                                'vr': 'CS',
                                'Value': ['F'],
                                'title': 'Female'
                            },
                            {
                                'vr': 'CS',
                                'Value': ['M'],
                                'title': 'Male'
                            },
                            {
                                'vr': 'CS',
                                'Value': ['O'],
                                'title': 'Other'
                            }
                        ]
    };
    user: User;
    filterMode = 'study';
    showClipboardContent = false;
    showoptionlist = false;
    orderbytext = this.orderby[2].label;
    patients = [];
    patient = {
        hide: false,
        modus: 'patient',
        showmwls: true
    };
    isRole(rule){
        return true;
    };
    study =  {
        modus: 'study',
        selected: false
    };
    series = {
        modus: 'series',
        selected: false
    };
    instance = {
        modus: 'instance',
        selected: false
    };
    select_show= false;
    aes: any;
    aet: any;
    aetmodel: any;
    externalInternalAetModel:any;
    advancedConfig = false;
    showModalitySelector = false;
    modalities: any;
    showMore = false;
    attributeFilters: any = {};
    exporters;
    exporterID = null;
    rjnotes;
    reject;
    studyDate: any = { from: this.service.getTodayDate(), to: this.service.getTodayDate(), toObject: new Date(), fromObject: new Date()};
    studyTime: any = { from: '', to: ''};
    hoverdic = [
        '.repeat0 .thead .tr_row',
        '.repeat1_hover',
        '.repeat2_hover',
        '.repeat3_hover',
        '.repeat4_hover'
    ];
    visibleHeaderIndex = 0;
    /*
     * Add the class fixed to the main_content when the user starts to scroll (reached the main_content position)
     * so we can user that as layer so the user can't see the table above the filters when he scrolls.
     */
    headers = [
        '.main_content'
    ];
    items = {};
    anySelected;
    lastSelectedObject = {modus: ''};
    keysdown: any = {};
    lastSelect: any;
    selected: any = {
        hasPatient: false
    };
    pressedKey;
    selectModality(key){
        this.filter.ModalitiesInStudy = '';
        this.filter['ScheduledProcedureStepSequence.Modality'] = '';
        if(this.filterMode === 'mwl')
            this.filter['ScheduledProcedureStepSequence.Modality'] = key;
        else
            this.filter.ModalitiesInStudy = key;
        $('.Modality').show();
        this.showModalitySelector = false;
    };

    showGetSizeLoader;
    showGetCountLoader;
    dialogRef: MatDialogRef<any>;
    subscription: Subscription;
    taskPK;
    constructor(
        public $http: J4careHttpService,
        public service: StudiesService,
        public mainservice: AppService,
        public cfpLoadingBar: LoadingBarService,
        private loadingBar:LoadingBarService,
        private viewContainerRef: ViewContainerRef ,
        private dialog: MatDialog,
        private config: MatDialogConfig,
        public httpErrorHandler:HttpErrorHandler,
        public j4care:j4care,
        public permissionService:PermissionService,
        private route: ActivatedRoute,
        private retrieveMonitoringService:RetrieveMonitoringService,
        private _keycloakService:KeycloakService
    ) {}
    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;
        if((KeycloakService.keycloakAuth && KeycloakService.keycloakAuth.authenticated) || (_.hasIn(this.mainservice,"global.notSecure") && this.mainservice.global.notSecure)){
            this.init();
        }else{
            if (retries){
                setTimeout(()=>{
                    $this.initCheck(retries-1);
                },20);
            }else{
                this.init();
            }
        }
    }

    private init(){
        console.log("study global PDQs",this.mainservice.global.PDQs);
        console.log("mainservice",this.mainservice);
        this.route.queryParams.subscribe(params => {
            console.log("params",params);
            this.checkDiffView(params);
            this.showFilterWarning = true;
            console.log('getglobal', this.mainservice.global);
            let $this = this;
            let dateset = localStorage.getItem('dateset');
            console.log('dateset', dateset);
            if (dateset === 'no'){
                this.clearStudyDate();
                this.filter['ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate'] = null;
            }

            if (_.hasIn(this.mainservice.global, 'state')){
                // $this = $this.mainservice.global.studyThis;
                // _.merge(this,$this.mainservice.global.studyThis);
                let selectedAet;
                _.forEach(this.mainservice.global.state, (m, i) => {
                    if (m && i != 'aetmodel'){
                        $this[i] = m;
                    }
                    if (i === 'aetmodel'){
                        selectedAet = m;
                    }
                });
                let selectedAetIndex = _.indexOf($this.aes, selectedAet);
                if (selectedAetIndex > -1){
                    $this.aetmodel = $this.aes[selectedAetIndex];
                }
            }
            this.modalities = Globalvar.MODALITIES;

            this.initAETs(2,);
            this.getAllAes(2);
            this.initAttributeFilter('Patient', 1);
            this.initExporters(2);
            this.initRjNotes(2);
            this.getDiffAttributeSet();
            this.hoverdic.forEach((m, i) => {
                $(document.body).on('mouseover mouseleave', m, function(e){
                    if (e.type === 'mouseover' && $this.visibleHeaderIndex != i){
                        $($this).addClass('hover');
                        $(m).addClass('hover');
                        $('.headerblock .header_block .thead').addClass('animated fadeOut');
                        setTimeout(function(){
                            $this.visibleHeaderIndex = i;
                            $('.div-table .header_block .thead').removeClass('fadeOut').addClass('fadeIn');
                        }, 200);
                        setTimeout(function(){
                            $('.headerblock .header_block .thead').removeClass('animated');
                        }, 200);
                    }
                });
            });
            $(document).keydown(function(e){
                $this.pressedKey = e.keyCode;
                if ($this.keysdown && $this.keysdown[e.keyCode]) {
                    // Ignore it
                    return;
                }
                // Remember it's down
                let validKeys = [16, 17, 67, 77, 86, 88, 91, 93, 224];
                if (validKeys.indexOf(e.keyCode) > -1){
                    console.log('in if ');
                    $this.keysdown[e.keyCode] = true;
                }
                //ctrl + c clicked
                if ($this.keysdown && ($this.keysdown[17] === true || $this.keysdown[91] === true || $this.keysdown[93] === true || $this.keysdown[224] === true) && $this.keysdown[67] === true && $this.isRole('admin')){
                    console.log('ctrl + c');
                    $this.ctrlC();
                    $this.keysdown = {};
                }
                //ctrl + v clicked
                if ($this.keysdown && ($this.keysdown[17] === true || $this.keysdown[91] === true || $this.keysdown[93] === true || $this.keysdown[224] === true) && $this.keysdown[86] === true && $this.isRole('admin')){
                    $this.ctrlV();
                    $this.keysdown = {};
                }
                //ctrl + m clicked
                if ($this.keysdown && ($this.keysdown[17] === true || $this.keysdown[91] === true || $this.keysdown[93] === true || $this.keysdown[224] === true) && $this.keysdown[77] === true && $this.isRole('admin')){
                    $this.merge();
                    $this.keysdown = {};
                }
                //ctrl + x clicked
                if ($this.keysdown && ($this.keysdown[17] === true || $this.keysdown[91] === true || $this.keysdown[93] === true || $this.keysdown[224] === true) && $this.keysdown[88] === true && $this.isRole('admin')){
                    console.log('ctrl + x');
                    $this.ctrlX();
                    $this.keysdown = {};
                }

            });
            $(document).keyup(function(e){
                console.log('keyUP', e.keyCode);
                $this.pressedKey = null;
                console.log('$this.keysdown', this.keysdown);
                delete $this.keysdown[e.keyCode];
            });

            //Detect in witch column is the mouse position and select the header.
            $(document.body).on('mouseover mouseleave', '.hover_cell', function(e){
                let $this = this;
                if (e.type === 'mouseover'){
                    $('.headerblock > .header_block > .thead').each((i, m) => {
                        $(m).find('.cellhover').removeClass('cellhover');
                        $(m).find('.th:eq(' + $($this).index() + ')').addClass('cellhover');
                    });
                }else{
                    $('.headerblock > .header_block > .thead > .tr_row > .cellhover').removeClass('cellhover');
                }
            });

            this.headers.forEach((m, i) => {
                this.items[i] = this.items[i] || {};
                $(window).scroll(() => {
                    if ($(m).length){
                        $this.items[i].itemOffset = $(m).offset().top;
                        $this.items[i].scrollTop = $(window).scrollTop();
                        if ($this.items[i].scrollTop >= $this.items[i].itemOffset){
                            $this.items[i].itemOffsetOld = $this.items[i].itemOffsetOld || $(m).offset().top;
                            $('.headerblock').addClass('fixed');
                        }
                        if ($this.items[i].itemOffsetOld  && ($this.items[i].scrollTop < $this.items[i].itemOffsetOld)){
                            $('.headerblock').removeClass('fixed');
                        }
                    }
                });
            });

            this.subscription = this.mainservice.createPatient$.subscribe(patient => {
                console.log('patient in subscribe messagecomponent ', patient);
                this.createPatient();
            });
            this.getQueueNames();
        });
    }
    orderByChanged(order){
        this.setOrderByParam(order);
        this.extendedFilter(false);
        this.fireQueryOnChange(order)
    }
    setOrderByParam(order){
        this.showoptionlist = false;
        this.filter.orderby = order.value;
        this.orderbytext = order.label;
        this.filterMode = order.mode;
    }
    checkDiffView(params){
        if(_.hasIn(params, "mode") && params.mode === "diff"){
            setTimeout(()=>{
                this.diffModeShow = true;
                this.taskPK = params["pk"];
                this.setOrderByParam(this.orderby.filter(orderby =>orderby.mode === "diff")[0]);
                let filters = Object.assign({},params);
                delete filters['mode'];
                this.getDiffTaskResults(filters, 0);
            },1);
        }
    }
    getDiffTaskResults(params,offset?){
        let filter = Object.assign({},params);
        filter['offset'] = offset ? offset:0;
        filter['limit'] = this.limit + 1;
        let mode = 'pk';
        this.cfpLoadingBar.start();
        if(this.taskPK != ''){
            filter['pk'] = this.taskPK;
        }else{
            mode = 'batch';
            filter['batchID'] = this.batchID;
        }
        this.service.gitDiffTaskResults(filter,mode).subscribe(res=>{
            console.log("res",res);
            this.patients = [];
            this.morePatients = undefined;
            this.moreDiffs = undefined;
            this.moreStudies = undefined;

            if (_.size(res) > 0) {
                // this.moreDiffs = res.length > this.limit;
                this.prepareDiffData(res, offset);
            }else{
                this.mainservice.setMessage({
                    'title': 'Info',
                    'text': 'No Diff Results found!',
                    'status': 'info'
                });
            }
            this.cfpLoadingBar.complete();
        },err=>{
            this.patients = [];
            this.httpErrorHandler.handleError(err);
            this.cfpLoadingBar.complete();
        });
    }
    // initAETs(retries) {
    //
    //     this.$http.get("/dcm4chee-arc/aets")
    //         .map(res => {let resjson;try{resjson = res.json();}catch (e){resjson = {};} return resjson;})
    //         .subscribe(
    //             (res) => {
    //                 this.aes = this.service.getAes(this.user, res);
    //                 this.aet = this.aes[0].title.toString();
    //                 this.aetmodel = this.aes[0];
    //             },
    //             (res)=>{
    //                 if (retries)
    //                     this.initAETs(retries-1);
    //             }
    //         );
    // }

    aetModeChange(e){
        if(e === "internal"){
            this.aetmodel = this.aes[0];
            this.showoptionlist = false;
            this.filter.orderby = "-StudyDate,-StudyTime";
            this.orderbytext = '<label>Study</label><span class=\"orderbydateasc\"></span>';
            this.filterMode = "study";
            this.patients = [];
        }
        if(e === "external"){
            this.patients = [];
            this.externalInternalAetModel = this.allAes[0];
            this.showoptionlist = false;
            this.filter.orderby = "";
            this.orderbytext = "Study";
            this.filterMode = "study";
        }
        this.filter.SplitStudyDateRange = '';
    }

    /*
    * @confirmparameters is an object that can contain title, content
    * */
    confirm(confirmparameters){
        this.scrollToDialog();
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
/*        this.dialogRef.afterClosed().subscribe(result => {
            if(result){
                console.log("result", result);
            }else{
                console.log("false");
            }
        });*/
        return this.dialogRef.afterClosed();
    };
    timeClose(){
        console.log('closetest', this.studyTime);
    }
    selectTime(state){
        console.log('on selectitme', this.studyTime);
        let obj = state + 'Object';
        try{
            let n = this.studyTime[obj].getHours();
            let m = (this.studyTime[obj].getMinutes() < 10 ? '0' : '') + this.studyTime[obj].getMinutes();
            this.studyTime[state] = n + ':' + m;
        }catch (e){
            console.log('in catch ', this.studyTime);
        }
        console.log('after set ', this.studyTime);
    }
    extendedFilter(...args: any[]){
        if (args.length > 1){
            args[1].preventDefault();
            args[1].stopPropagation();
        }
            this.advancedConfig = args[0];
            if ($('.div-table *').length > 0){
                $('.div-table').removeAttr( 'style' );
                    setTimeout(function() {
                        let marginTop: any = $('.div-table').css('margin-top');
                        if (marginTop){
                            marginTop = marginTop.replace(/[^0-9]/g, '');
                            let outerHeight: any = $('.headerblock').outerHeight();
                            if (marginTop && marginTop < outerHeight && args[0] === true){
                                $('.div-table.extended').css('margin-top', outerHeight);
                            }
                        }
                    }, 50);
            }
    }
    clearClipboard = function(){
        this.clipboard = {};
        this.selected['otherObjects'] = {};
    };
    mapCode(m,i,newObject,mapCodes){
        if(_.hasIn(mapCodes,i)){
            if(_.isArray(mapCodes[i])){
                _.forEach(mapCodes[i],(seq,j)=>{
                    newObject[seq.code] = _.get(m,seq.map);
                    newObject[seq.code].vr = seq.vr;
                });
            }else{
                newObject[mapCodes[i].code] = m;
                newObject[mapCodes[i].code].vr = mapCodes[i].vr;
            }
        }
    }
    uploadInPatient(object){
        console.log("in uuploadInPatient",object);
        this.$http.post(
            '../aets/' + this.aet + '/rs/studies',
            object.attrs,
            new HttpHeaders({ 'Content-Type': 'application/dicom+json' })
        ).subscribe(res=>{
            console.log("study created",res);
            this.upload({attrs:res},"study");
        },err=>{
            console.log("err",err);
            this.httpErrorHandler.handleError(err);
        });
    }
    upload(object,mode){
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
                    this.mapCode(m,i,newObject,mapCodes);
                }else{
                    if(_.indexOf(removeCode,i) === -1){
                        newObject[i] = m;
                    }else{
                        this.mapCode(m,i,newObject,mapCodes);
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
        this.dialogRef.componentInstance.aes = this.aes;
        this.dialogRef.componentInstance.selectedAe = this.aetmodel.dicomAETitle;
        this.dialogRef.componentInstance.dicomObject = object;
        this.dialogRef.afterClosed().subscribe((result) => {
            console.log('result', result);
            if (result){
            }
        });
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
    queryStudie(queryParameters, offset){
        let $this = this;
        if (offset < 0 || offset === undefined) offset = 0;
        this.cfpLoadingBar.start();
        this.service.queryStudies(
            this.rsURL(),
            queryParameters
        ).subscribe((res) => {

                $this.patients = [];
                //           $this.studies = [];
                $this.morePatients = undefined;
                $this.moreStudies = undefined;
                $this.count = "";
                $this.size = "";
                if (_.size(res) > 0) {
                    let index = 0;
                    let pat, study, patAttrs, tags = $this.attributeFilters.Patient.dcmTag;
                    while (tags && (tags[index] < '00201200')) {
                        index++;
                    }
                    tags.splice(index, 0, '00201200');
                    tags.push('77770010','77771010','77771011','77771012','77771013','77771014');
                    console.log("tags",tags);
                    console.log('res', res);
                    res.forEach(function (studyAttrs, index) {
                        patAttrs = {};
                        $this.extractAttrs(studyAttrs, tags, patAttrs);
                        if (!(pat && $this.equalsIgnoreSpecificCharacterSet(pat.attrs, patAttrs))) { //angular.equals replaced with Rx.helpers.defaultComparer
                            pat = {
                                attrs: patAttrs,
                                studies: [],
                                showAttributes: false
                            };
                            // $this.$apply(function () {
                            $this.patients.push(pat);
                            // });
                        }
                        study = {
                            patient: pat,
                            offset: offset + index,
                            moreSeries: false,
                            attrs: studyAttrs,
                            series: null,
                            showAttributes: false,
                            fromAllStudies: false,
                            selected: false
                        };
                        pat.studies.push(study);
                        $this.extendedFilter(false);
                        //                   $this.studies.push(study); //sollte weg kommen
                    });
                    if ($this.moreStudies = (res.length > $this.limit)) {
                        pat.studies.pop();
                        if (pat.studies.length === 0) {
                            $this.patients.pop();
                        }
                        // this.studies.pop();
                    }
                    console.log('patients=', $this.patients[0]);
                    // $this.mainservice.setMessage({
                    //     "title": "Info",
                    //     "text": "Test",
                    //     "status": "info"
                    // });
                    // sessionStorage.setItem("patients", $this.patients);
                    // $this.mainservice.setGlobal({patients:this.patients,moreStudies:$this.moreStudies});
                    // $this.mainservice.setGlobal({studyThis:$this});
                    console.log('global set', $this.mainservice.global);
                    $this.cfpLoadingBar.complete();

                } else {
                    console.log('in else setmsg');
                    $this.patients = [];

                    $this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'No matching Studies found!',
                        'status': 'info'
                    });
                    $this.cfpLoadingBar.complete();
                }
                // setTimeout(function(){
                //     togglePatientsHelper("hide");
                // }, 1000);
                $this.cfpLoadingBar.complete();
            },
            (err) => {
                console.log('in error', err);
                $this.patients = [];
                $this.httpErrorHandler.handleError(err);
                $this.cfpLoadingBar.complete();
            }
        );
    }
    queryDiff(queryParameters, offset){
        let $this = this;
        if (offset < 0 || offset === undefined) offset = 0;

        if(!this.aet2) {
            this.mainservice.setMessage({
                'title': 'Warning',
                'text': "Secondary AET is empty!",
                'status': 'warning'
            });
            return;
        }
        this.cfpLoadingBar.start();
        this.service.queryDiffs(
            $this.diffUrl(),
            queryParameters
        ).subscribe(
            (res) => {
                $this.patients = [];
                //           $this.studies = [];
                $this.morePatients = undefined;
                $this.moreStudies = undefined;
                if (_.size(res) > 0) {
                    //Add number of patient related studies manuelly hex(00201200) => dec(2101760)
                    this.prepareDiffData(res, offset);
                    console.log('global set', $this.mainservice.global);
                    $this.cfpLoadingBar.complete();

                } else {
                    if(queryParameters['queue']){
                        $this.mainservice.setMessage({
                            'title': 'Info',
                            'text': 'Command executed successfully!',
                            'status': 'info'
                        });
                    }else{
                        if(res === null)
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'No matching study found at primary C-FIND SCP',
                                'status': 'info'
                            });
                        else
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'No diffs found!',
                                'status': 'info'
                            });
                    }

                    $this.patients = [];
                    $this.cfpLoadingBar.complete();
                }
                $this.cfpLoadingBar.complete();
            },(err)=>{
                $this.cfpLoadingBar.complete();
                $this.httpErrorHandler.handleError(err);
            }
        );
    };
    prepareDiffData(res, offset){
        let haederCodes = [
            "00200010",
            "0020000D",
            "00080020",
            "00080030",
            "00080090",
            "00080050",
            "00080061",
            "00081030",
            "00201206",
            "00201208"
        ];
        let index = 0;
        while (this.attributeFilters.Patient.dcmTag[index] && (this.attributeFilters.Patient.dcmTag[index] < '00201200')) {
            index++;
        }
        this.attributeFilters.Patient.dcmTag.splice(index, 0, '00201200');

        let pat, study, patAttrs, tags = this.attributeFilters.Patient.dcmTag;
        console.log('res', res);
        res.forEach((studyAttrs, index)=> {
            patAttrs = {};
            this.extractAttrs(studyAttrs, tags, patAttrs);
            if (!(pat && this.equalsIgnoreSpecificCharacterSet(pat.attrs, patAttrs))) { //angular.equals replaced with Rx.helpers.defaultComparer
                pat = {
                    attrs: patAttrs,
                    studies: [],
                    showAttributes: false,
                    showStudies:true
                };
                // this.$apply(function () {
                this.patients.push(pat);
                // });
            }
            let showBorder = false;
            let diffHeaders = {};
            _.forEach(haederCodes,(m)=>{
                diffHeaders[m] = this.getDiffHeader(studyAttrs,m);
            });
            study = {
                patient: pat,
                offset: offset + index,
                moreSeries: false,
                attrs: studyAttrs,
                series: null,
                showAttributes: false,
                fromAllStudies: false,
                selected: false,
                showBorder:false,
                diffHeaders:diffHeaders
            };
            pat.studies.push(study);
            this.extendedFilter(false);
            //                   this.studies.push(study); //sollte weg kommen
        });
        if (this.moreStudies = (res.length > this.limit)) {
            pat.studies.pop();
            if (pat.studies.length === 0) {
                this.patients.pop();
            }
            // this.studies.pop();
        }
    }
    getDiffHeader(study,code){
        let value;
        let sqValue;
        if(_.hasIn(study,[code,"Value",0])){
            if(study[code].vr === "PN"){
                if(_.hasIn(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0,"Alphabetic"])){
                    value =  _.get(study,[code,"Value",0,"Alphabetic"]);
                    sqValue = _.get(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0,"Alphabetic"]);
                    if(value === sqValue){
                        return {
                            value: value,
                            showBorder:false
                        }
                    }else{
                        return {
                            value: value + "/" + sqValue,
                            showBorder:true
                        }
                    }
                }else{
                    return {
                        value: study[code].Value[0].Alphabetic,
                        showBorder:false
                    }
                }
            }else{
                //00200010
                switch(code) {
                    case "00080061":
                        value = new FormatAttributeValuePipe().transform(study[code]);
                        // value = _.get(study,[code,"Value", 0]);
                        if(_.hasIn(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0])){
                            sqValue = new FormatAttributeValuePipe().transform(_.get(study,["04000561","Value",0,"04000550","Value",0,code]));
                            // sqValue = _.get(study,["04000561","Value",0,"04000550","Value",0,code, "Value",0]);
                            if(value === sqValue){
                                return {
                                    value: value,
                                    showBorder:false
                                }
                            }else{
                                return {
                                    value: value + "/" + sqValue,
                                    showBorder:true
                                }
                            }
                        }
                        break;
                    case "00080020":
                        value = new FormatDAPipe().transform(_.get(study,[code,"Value",0]));
                        // value = _.get(study,[code,"Value",0]);
                        if(_.hasIn(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0])){
                            sqValue = new FormatDAPipe().transform(_.get(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0]));
                            // sqValue = _.get(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0]);
                            if(value === sqValue){
                                return {
                                    value: value,
                                    showBorder:false
                                }
                            }else{
                                return {
                                    value: value + "/" + sqValue,
                                    showBorder:true
                                }
                            }
                        }
                        break;
                    case "00080030":
                        value = new FormatTMPipe().transform(_.get(study,[code,"Value",0]));
                        // value = _.get(study,[code,"Value",0]);
                        if(_.hasIn(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0])){
                            sqValue = new FormatTMPipe().transform(_.get(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0]));
                            // sqValue = _.get(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0]);
                            if(value === sqValue){
                                return {
                                    value: value,
                                    showBorder:false
                                }
                            }else{
                                return {
                                    value: value + "/" + sqValue,
                                    showBorder:true
                                }
                            }
                        }
                        break;
                    default:
                        if(_.hasIn(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0])){
                            value = _.get(study,[code,"Value",0]);
                            sqValue = _.get(study,["04000561","Value",0,"04000550","Value",0,code,"Value",0]);
                            if(value === sqValue){
                                return {
                                    value: value,
                                    showBorder:false
                                }
                            }else{
                                return {
                                    value: value + "/" + sqValue,
                                    showBorder:true
                                }
                            }
                        }
                }
            }
            return {
                value: study[code].Value[0],
                showBorder:false
            }
        }else{
            return {
                value: "",
                showBorder:false
            }
        }
    }
    swapDiff(){
        let tempAet = this.aet1;
        this.aet1 = this.aet2;
        this.aet2 = tempAet;
        this.queryDiffs(0);
    }
    queryDiffs(offset){
        if(this.diffModeShow){
            let params = {
/*                offset:offset,
                limit: this.limit + 1,
                pk:this.taskPK,
                batchID:this.batchID*/
            };
            this.getDiffTaskResults(params, offset);
        }else{
            this.queryMode = 'queryDiff';
            this.moreMWL = undefined;
            this.morePatients = undefined;
            let queryParameters = this.createQueryParams(offset, this.limit + 1, this.createStudyFilterParams());
            queryParameters['queue'] = this.diffQueue;
            queryParameters['missing'] = this.missing;
            queryParameters['different'] = this.different;
            if(this.batchID) queryParameters['batchID'] = this.batchID;
            if(this.comparefield && this.different) queryParameters['comparefield'] = this.comparefield;
            this.queryDiff(queryParameters, offset);
        }
    };
    setExpiredDate(study){
        this.setExpiredDateQuery(study,false);
    }

    dateToString(date){
        return (
            date.getFullYear() + '' +
            ((date.getMonth() < 9) ? '0' + (date.getMonth() + 1) : (date.getMonth() + 1)) + '' +
            ((date.getDate() < 10) ? '0' + date.getDate() : date.getDate())
        );
    }
    setExpiredDateQuery(study, infinit){
        this.confirm(this.service.getPrepareParameterForExpiriationDialog(study,this.exporters, infinit)).subscribe(result => {
            if(result){
                this.cfpLoadingBar.start();
                if(result.schema_model.expiredDate){
                    this.service.setExpiredDate(this.aet, _.get(study,"attrs.0020000D.Value[0]"), result.schema_model.expiredDate, result.schema_model.exporter).subscribe(
                        (res)=>{
                            _.set(study,"attrs.77771023.Value[0]",result.schema_model.expiredDate);
                            _.set(study,"attrs.77771023.vr","DA");
                            this.mainservice.setMessage( {
                                'title': 'Info',
                                'text': 'Expired date set successfully!',
                                'status': 'info'
                            });
                            this.cfpLoadingBar.complete();
                        },
                        (err)=>{
                            this.httpErrorHandler.handleError(err);
                            this.cfpLoadingBar.complete();
                        }
                    );
                }else{
                    this.mainservice.showError("Expired date is requred!");
                }
            }
        });
    }
    queryStudies(offset) {
        this.queryMode = 'queryStudies';
        this.moreMWL = undefined;
        this.morePatients = undefined;
        let $this = this;
        let queryParameters = this.createQueryParams(offset, this.limit + 1, this.createStudyFilterParams());
        if (this.showNoFilterWarning(queryParameters) && this.showFilterWarning) {
            $this.confirm({
                content: 'No filter are set, are you sure you want to continue?'
            }).subscribe(result => {
                if (result){
                    $this.queryStudie(queryParameters, offset);
                }
            });
        }else{
                $this.queryStudie(queryParameters, offset);
        }
        this.showFilterWarning = true;

    };
    editMWL(patient, patientkey, mwlkey, mwl){
        this.saveLabel = 'SAVE';
        this.titleLabel = 'Edit MWL of patient ';
        this.titleLabel += ((_.hasIn(patient, 'attrs.00100010.Value.0.Alphabetic')) ? '<b>' + patient.attrs['00100010'].Value[0]['Alphabetic'] + '</b>' : ' ');
        this.titleLabel += ((_.hasIn(patient, 'attrs.00100020.Value.0')) ? ' with ID: <b>' + patient.attrs['00100020'].Value[0] + '</b>' : '');
        this.modifyMWL(patient, 'edit', patientkey, mwlkey, mwl);
    };
    studyReceiveDateTimeChanged(e, mode){
        this.filter['StudyReceiveDateTime'] = this.filter['StudyReceiveDateTime'] || {};
        this['StudyReceiveDateTime'][mode] = e;
        let datePipeEn = new DatePipe('us-US');
        if (this.StudyReceiveDateTime.from && this.StudyReceiveDateTime.to){
            this.filter['StudyReceiveDateTime'] = datePipeEn.transform(this.StudyReceiveDateTime.from, 'yyyyMMddHHmmss') + '-' + datePipeEn.transform(this.StudyReceiveDateTime.to, 'yyyyMMddHHmmss');
        }
        if ((this.StudyReceiveDateTime.from && !this.StudyReceiveDateTime.to) || (!this.StudyReceiveDateTime.from && this.StudyReceiveDateTime.to)){
            this.filter['StudyReceiveDateTime'] = (this.StudyReceiveDateTime.from) ? datePipeEn.transform(this.StudyReceiveDateTime.from, 'yyyyMMddHHmmss') + '-' : '-' + datePipeEn.transform(this.StudyReceiveDateTime.to, 'yyyyMMddHHmmss');
        }
    }
    deleteRejectedInstances(){
        let result = {
            reject: undefined
        };
        let $this = this;
        this.scrollToDialog();
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(DeleteRejectedInstancesComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.rjnotes = this.rjnotes;
        this.dialogRef.componentInstance.results = result;
        this.dialogRef.afterClosed().subscribe(re => {
            console.log('afterclose re', re);
            $this.cfpLoadingBar.start();
            if (re) {
                console.log('in re', re);
                console.log('in re', _.size(re));
                // console.log("in result",result);
                let params: RequestOptionsArgs = {};
                if (re.rejectedBefore){
                    params['rejectedBefore'] = re.rejectedBefore;
                }
                if (re.keepRejectionNote === true){
                    params['keepRejectionNote'] = re.keepRejectionNote;
                }
                console.log('params1', $this.mainservice.param(params));
                console.log('params', params);
                $this.$http.delete(
                    '../reject/' + re.reject + '?' + $this.mainservice.param(params)
                )
                    // .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
                    .subscribe(
                    (res) => {
                        console.log('in res', res);
                        $this.cfpLoadingBar.complete();
                        // $this.fireRightQuery();
                        if (_.hasIn(res, 'deleted')){
                            this.mainservice.setMessage({
                                'title': 'Info',
                                'text': res.deleted + ' instances deleted successfully!',
                                'status': 'info'
                            });
                        }else{
                            this.mainservice.setMessage({
                                'title': 'Warning',
                                'text': 'Process executed successfully',
                                'status': 'warning'
                            });
                        }
                    },
                    (err) => {
                        console.log('error', err);
                        $this.httpErrorHandler.handleError(err);
                    });
            }
            $this.cfpLoadingBar.complete();
            this.dialogRef = null;
        });

    }
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
        this.titleLabel = 'Create new MWL';
        // modifyStudy(patient, "create");
        this.modifyMWL(patient, 'create', '', '', mwl);
    };
    modifyMWL(patient, mode, patientkey, mwlkey, mwl){
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
                if (value.vr === 'DA' && value.Value && value.Value[0]){
/*                    var string = value.Value[0];
                    string = string.replace(/\./g,"");
                    var yyyy = string.substring(0,4);
                    var MM = string.substring(4,6);
                    var dd = string.substring(6,8);
                    var timestampDate   = Date.parse(yyyy+"-"+MM+"-"+dd);
                    var date          = new Date(timestampDate);
                    $scope.dateplaceholder[index] = date;*/
                    console.log('in date', value.Value);
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
            console.log('berfore set mwl', mwl);
            console.log('after initemptyvalue');
            let iod = $this.service.replaceKeyInJson(res, 'items', 'Value');
            let mwlFiltered = _.cloneDeep(mwl);
            mwlFiltered.attrs = new ComparewithiodPipe().transform(mwl.attrs, iod);
            $this.service.initEmptyValue(mwlFiltered.attrs);
            $this.scrollToDialog();
            $this.dialogRef = $this.dialog.open(EditMwlComponent, {
                height: 'auto',
                width: '90%'
            });
            $this.dialogRef.componentInstance.iod = iod;
            $this.dialogRef.componentInstance.mode = mode;
            $this.dialogRef.componentInstance.dropdown = $this.service.getArrayFromIod(res);
            $this.dialogRef.componentInstance.mwl = mwlFiltered;
            $this.dialogRef.componentInstance.mwlkey = mwlkey;
            console.log('$this.savelabel', $this.saveLabel);
            $this.dialogRef.componentInstance.saveLabel = $this.saveLabel;
            $this.dialogRef.componentInstance.titleLabel = $this.titleLabel;
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
                    $this.$http.post(
                        '../aets/' + $this.aet + '/rs/mwlitems',
                        local,
                         new HttpHeaders({ 'Content-Type': 'application/dicom+json' })
                    ).subscribe((response) => {
                        if (mode === 'edit'){
                            // _.assign(mwl, mwlFiltered);
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'MWL saved successfully!',
                                'status': 'info'
                            });
                        }else{
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'MWL created successfully!',
                                'status': 'info'
                            });
                            $this.fireRightQuery();
                        }
                    }, (response) => {
                        $this.httpErrorHandler.handleError(response);
                        // $scope.callBackFree = true;
                    });
                }else{
                    console.log('no', originalMwlObject);
                    // patient = originalPatient;
                    _.assign(mwl, originalMwlObject);
                }
                $this.dialogRef = null;
            });
        }, (err) => {
            console.log('error', err);
        });
    }
    editPatient(patient, patientkey){
        this.saveLabel = 'SAVE';
        this.titleLabel = 'Edit patient ';
        this.titleLabel += ((_.hasIn(patient, 'attrs.00100010.Value.0.Alphabetic')) ? '<b>' + patient.attrs['00100010'].Value[0]['Alphabetic'] + '</b>' : ' ');
        this.titleLabel += ((_.hasIn(patient, 'attrs.00100020.Value.0')) ? ' with ID: <b>' + patient.attrs['00100020'].Value[0] + '</b>' : '');
        console.log('titleLabel', this.titleLabel);
        this.modifyPatient(patient, 'edit', patientkey);
    };
    modifyStudy(patient, mode, patientkey, studykey, study){
        this.config.viewContainerRef = this.viewContainerRef;
        let originalStudyObject = _.cloneDeep(study);
        // console.log("patient",patient);
        // console.log("studykey",studykey);
        // console.log("study",study);
        if (mode === 'edit'){
            _.forEach(study.attrs, function(value, index) {
                let checkValue = '';
                if (value.Value && value.Value.length){
                    checkValue = value.Value.join('');
                }
                if (!(value.Value && checkValue != '')){
                    delete study.attrs[index];
                }
/*                if(value.vr === "DA" && value.Value && value.Value[0]){
                    var string = value.Value[0];
                    string = string.replace(/\./g,"");
                    var yyyy = string.substring(0,4);
                    var MM = string.substring(4,6);
                    var dd = string.substring(6,8);
                    var timestampDate   = Date.parse(yyyy+"-"+MM+"-"+dd);
                    var date          = new Date(timestampDate);
                    $scope.dateplaceholder[index] = date;
                }*/
            });
        }

        this.lastPressedCode = 0;
        let $this = this;
        this.service.getStudyIod().subscribe((res) => {
            $this.service.patientIod = res;
            let header = 'Create new Study';
            if (mode === 'edit'){
                if (patient.attrs['00100020'] && patient.attrs['00100020'].Value[0]){
                    header = 'Edit study of patient <span>' + patient.attrs['00100010'].Value[0]['Alphabetic'] + '</span> with ID <span>' + patient.attrs['00100020'].Value[0] + '</span>';
                }else{
                    header = 'Edit study of patient <span>' + patient.attrs['00100010'].Value[0]['Alphabetic'] + '</span>';
                }
            }
            let iod = $this.service.replaceKeyInJson(res, 'items', 'Value');
            let studyFiltered = _.cloneDeep(study);
            studyFiltered.attrs = new ComparewithiodPipe().transform(study.attrs, iod);
            $this.service.initEmptyValue(studyFiltered.attrs);
            console.log('afterinintemptyvalue');
            $this.scrollToDialog();
            $this.dialogRef = $this.dialog.open(EditStudyComponent, {
                height: 'auto',
                width: '90%'
            });
            console.log('afterinintemptyvalue2');
            $this.dialogRef.componentInstance.study = studyFiltered;
            $this.dialogRef.componentInstance.studykey = studykey;
            $this.dialogRef.componentInstance.dropdown = $this.service.getArrayFromIod(res);
            $this.dialogRef.componentInstance.iod = iod;
            console.log('$this.savelabel', $this.saveLabel);
            $this.dialogRef.componentInstance.saveLabel = $this.saveLabel;
            $this.dialogRef.componentInstance.titleLabel = $this.titleLabel;
            $this.dialogRef.componentInstance.mode = mode;
            $this.dialogRef.afterClosed().subscribe(result => {
                if (result){
                    $this.service.clearPatientObject(studyFiltered.attrs);
                    $this.service.convertStringToNumber(studyFiltered.attrs);
                    // StudiesService.convertDateToString($scope, "editstudyFiltered");

                    //Add patient attributs again
                    // angular.extend($scope.editstudyFiltered.attrs, patient.attrs);
                    // $scope.editstudyFiltered.attrs.concat(patient.attrs);
                    let local = {};
                    $this.service.appendPatientIdTo(patient.attrs, local);
                    // local["00100020"] = patient.attrs["00100020"];
                    _.forEach(studyFiltered.attrs, function(m, i){
                        if (res[i]){
                            local[i] = m;
                        }
                    });
                    $this.$http.post(
                        '../aets/' + $this.aet + '/rs/studies',
                        local,
                         new HttpHeaders({ 'Content-Type': 'application/dicom+json' })
                    ).subscribe(
                        (response) => {
                            if (mode === 'edit'){
                                // _.assign(study, studyFiltered);
                                $this.mainservice.setMessage( {
                                    'title': 'Info',
                                    'text': 'Study saved successfully!',
                                    'status': 'info'
                                });
                            }else{
                                $this.mainservice.setMessage( {
                                    'title': 'Info',
                                    'text': 'Study created successfully!',
                                    'status': 'info'
                                });
                                $this.fireRightQuery();
                            }
                        },
                        (response) => {
                            $this.httpErrorHandler.handleError(response);
                        }
                    );
                }else{
                    console.log('no', originalStudyObject);
                    // patient = originalPatient;
                    _.assign(study, originalStudyObject);
                }
                $this.dialogRef = null;
            });
        });
        // console.log("$scope.editstudy",$scope.editstudy);
 /*       $http.get('iod/study.iod.json',{ cache: true}).then(function (res) {
                // angular.forEach($scope.editstudy.attrs,function(m, i){
                //     if(!res.data[i] || res.data[i] === undefined){
                //         delete $scope.editstudy.attrs[i];
                //     }
                // });
                var dropdown                = StudiesService.getArrayFromIod(res);
                res.data = StudiesService.replaceKeyInJson(res.data, "items", "Value");
                $templateRequest('templates/edit_study.html').then(function(tpl) {
                    $scope.dropdown             = dropdown;
                    $scope.DCM4CHE              = DCM4CHE;
                    $scope.addPatientAttribut   = "";
                    $scope.opendropdown         = false;
                    // console.log("tpl",tpl);
                    var html                    = $compile(tpl)($scope);

                    // console.log("$scope.editstudy",$scope.editstudy);
                    // console.log("html",html);
                    var $vex = vex.dialog.open({
                        message: header,
                        input: html,
                        className:"vex-theme-os edit-patient",
                        overlayClosesOnClick: false,
                        escapeButtonCloses: false,
                        afterOpen: function($vexContent) {
                            cfpLoadingBar.complete();

                        },
                        onSubmit: function(e) {
                            //Prevent submit/close if ENTER was clicked
                            if($scope.lastPressedCode === 13){
                                e.preventDefault();
                            }else{
                                $vex.data().vex.callback();
                            }
                        },
                        buttons: [
                            $.extend({}, vex.dialog.buttons.YES, {
                                text: 'Save'
                            }), $.extend({}, vex.dialog.buttons.NO, {
                                text: 'Cancel'
                            })
                        ],
                        callback: function(data) {
                            cfpLoadingBar.start();
                            if (data === false) {
                                cfpLoadingBar.complete();

                                StudiesService.clearPatientObject($scope.editstudy.attrs);
                                return console.log('Cancelled');
                            }else{

                            }
                            vex.close($vex.data().vex.id);
                        }
                    });
                });
            },
            function errorCallback(response) {
                DeviceService.msg($scope, {
                    "title": "Error "+response.status,
                    "text": response.data.errorMessage,
                    "status": "error"
                });
                console.log("response",response);
            });*/
    };
    editStudy(patient, patientkey, studykey, study){
        this.saveLabel = 'SAVE';
        this.titleLabel = 'Edit study of patient ';
        this.titleLabel += ((_.hasIn(patient, 'attrs.00100010.Value.0.Alphabetic')) ? '<b>' + patient.attrs['00100010'].Value[0]['Alphabetic'] + '</b>' : ' ');
        this.titleLabel += ((_.hasIn(patient, 'attrs.00100020.Value.0')) ? ' with ID: <b>' + patient.attrs['00100020'].Value[0] + '</b>' : '');
        console.log('titleLabel', this.titleLabel);
        this.modifyStudy(patient, 'edit', patientkey, studykey, study);
    };
    createStudy(patient){
        this.saveLabel = 'CREATE';
        this.titleLabel = 'Create new Study';
        let study = {
            'attrs': {
                '00200010': { 'vr': 'SH', 'Value': ['']},
                '0020000D': { 'vr': 'UI', 'Value': ['']},
                '00080050': { 'vr': 'SH', 'Value': ['']}
            }
        };
        this.modifyStudy(patient, 'create', '', '', study);
    };

    createPatient(){
        this.saveLabel = 'CREATE';
        this.titleLabel = 'Create new patient';
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
        this.modifyPatient(newPatient, 'create', null);
    };
    scrollToDialog(){
        let counter = 0;
        let i = setInterval(function(){
            if (($('.md-overlay-pane').length > 0)) {
                clearInterval(i);
                $('html, body').animate({
                    scrollTop: ($('.md-overlay-pane').offset().top)
                }, 200);
            }
            if (counter > 200){
                clearInterval(i);
            }else{
                counter++;
            }
        }, 50);
    }
    getHl7ApplicationNameFormAETtitle(aet){
        for(let i = 0; i < this.allAes.length; i++){
            if(aet === this.allAes[i].dicomAETitle){
                return this.allAes[i].hl7ApplicationName;
            }
        };
    }
    modifyPatient(patient, mode , patientkey){
        let originalPatientObject = _.cloneDeep(patient);
        this.config.viewContainerRef = this.viewContainerRef;
        let oldPatientID;
        this.lastPressedCode = 0;
        let modifyPatientService;
        if (mode === 'edit'){
            _.forEach(patient.attrs, function(value, index) {
                let checkValue = '';
                if (value.Value && value.Value.length){
                    checkValue = value.Value.join('');
                }
                if (!(value.Value && checkValue != '')){
                    delete patient.attrs[index];
                }
                if (index === '00100040' && patient.attrs[index] && patient.attrs[index].Value && patient.attrs[index].Value[0]){
                    patient.attrs[index].Value[0] = patient.attrs[index].Value[0].toUpperCase();
                }
            });
            oldPatientID = this.service.getPatientId(patient.attrs);
        }
        let $this = this;
        this.service.getPatientIod().subscribe((iod) => {
            $this.service.patientIod = iod;

            $this.service.initEmptyValue(patient.attrs);
            $this.scrollToDialog();
            $this.dialogRef = $this.dialog.open(EditPatientComponent, {
                height: 'auto',
                width: '90%'
            });

            $this.dialogRef.componentInstance.mode = mode;
            $this.dialogRef.componentInstance.patient = patient;
            $this.dialogRef.componentInstance.patientkey = patientkey;
            $this.dialogRef.componentInstance.dropdown = $this.service.getArrayFromIod(iod);
            $this.dialogRef.componentInstance.iod = $this.service.replaceKeyInJson(iod, 'items', 'Value');
            $this.dialogRef.componentInstance.saveLabel = $this.saveLabel;
            $this.dialogRef.componentInstance.titleLabel = $this.titleLabel;
            $this.dialogRef.componentInstance.externalInternalAetMode = $this.externalInternalAetMode;
            $this.dialogRef.afterClosed().subscribe(result => {
                //If user clicked save
                if (result){
                    if(mode === "create"){
                        if(this.service.getPatientId(patient.attrs)){
                            modifyPatientService = $this.service.modifyPatient(patient, iod, oldPatientID, $this.aet, $this.service.getHl7ApplicationNameFormAETtitle($this.aet, $this.allAes), $this.externalInternalAetModel.hl7ApplicationName,  mode, $this.externalInternalAetMode,this.externalInternalAetMode === "external");
                            if(modifyPatientService){
                                modifyPatientService.save.subscribe((response)=>{
                                    this.fireRightQuery();
                                    this.mainservice.setMessage({
                                        'title': 'Info',
                                        'text': modifyPatientService.successMsg,
                                        'status': 'info'
                                    });
                                },(err)=>{
                                    _.assign(patient, originalPatientObject);
                                    $this.httpErrorHandler.handleError(err);
                                });
                            }
                        }else{
                            this.service.createPatient(
                                patient.attrs,
                                this.aet,
                                this.service.getHl7ApplicationNameFormAETtitle($this.aet, $this.allAes),
                                this.externalInternalAetModel.hl7ApplicationName,
                                this.externalInternalAetMode
                            ).subscribe((res)=>{
                                this.mainservice.setMessage({
                                    'title': 'Info',
                                    'text': 'Patient created successfully!',
                                    'status': 'info'
                                });
                            },(err)=>{
                                this.httpErrorHandler.handleError(err);
                            });
                        }
                    }else{
                        this.service.changePatientID(
                            oldPatientID,
                            this.service.getPatientId(patient.attrs),
                            patient.attrs,
                            this.aet,
                            this.service.getHl7ApplicationNameFormAETtitle($this.aet, $this.allAes),
                            this.externalInternalAetModel.hl7ApplicationName,
                            this.externalInternalAetMode
                        ).subscribe((idChanged)=>{
                            let id = oldPatientID;
                            if(idChanged)
                                id = this.service.getPatientId(patient.attrs);
                            modifyPatientService = $this.service.modifyPatient(patient, iod, id, $this.aet, $this.service.getHl7ApplicationNameFormAETtitle($this.aet, $this.allAes), $this.externalInternalAetModel.hl7ApplicationName,  mode, $this.externalInternalAetMode, this.externalInternalAetMode === "external");
                            if(modifyPatientService){
                                modifyPatientService.save.subscribe((response)=>{
                                    this.fireRightQuery();
                                    this.mainservice.setMessage({
                                        'title': 'Info',
                                        'text': modifyPatientService.successMsg,
                                        'status': 'info'
                                    });
                                },(err)=>{
                                    _.assign(patient, originalPatientObject);
                                    $this.httpErrorHandler.handleError(err);
                                });
                            }else{
                                _.assign(patient, originalPatientObject);
                            }
                        },(err)=>{
                            _.assign(patient, originalPatientObject);
                            this.httpErrorHandler.handleError(err);
                        });
                    }
/*
                    if(oldPatientID === $this.service.getPatientId(patient.attrs) && ($this.externalInternalAetMode === "internal" || mode === "create")){
                        let modifyPatientService = $this.service.modifyPatient(patient, iod, oldPatientID, $this.aet, $this.service.getHl7ApplicationNameFormAETtitle($this.aet, $this.allAes), $this.externalInternalAetModel.hl7ApplicationName,  mode, $this.externalInternalAetMode);
                        if(modifyPatientService){
                            modifyPatientService.save.subscribe((response)=>{
                                this.fireRightQuery();
                                this.mainservice.setMessage({
                                    'title': 'Info',
                                    'text': modifyPatientService.successMsg,
                                    'status': 'info'
                                });
                            },(err)=>{
                                _.assign(patient, originalPatientObject);
                                $this.httpErrorHandler.handleError(err);
                            });
                        }
                    }else{
                        //If patient id was changed and the aetmode is external than change the patient id first than update the patient
                        let changeExternalPatientIdService = $this.service.changeExternalPatientID($this.service.preparePatientObjectForExternalPatiendIdChange(originalPatientObject.attrs, patient.attrs), $this.service.getHl7ApplicationNameFormAETtitle($this.aet, $this.allAes) ,  $this.externalInternalAetModel.hl7ApplicationName, oldPatientID);
                        if(changeExternalPatientIdService){
                            changeExternalPatientIdService.save.subscribe((response)=>{
                                this.mainservice.setMessage({
                                    'title': 'Info',
                                    'text': changeExternalPatientIdService.successMsg,
                                    'status': 'info'
                                });
                                if(this.service.otherAttributesButIDWasChanged(originalPatientObject.attrs,patient.attrs)){
                                    let modifyPatientService = $this.service.modifyPatient(patient, iod, oldPatientID,$this.aet, $this.service.getHl7ApplicationNameFormAETtitle($this.aet, $this.allAes), $this.externalInternalAetModel.hl7ApplicationName,  mode, $this.externalInternalAetMode);
                                    if(modifyPatientService){
                                        modifyPatientService.save.subscribe((response)=>{
                                            this.fireRightQuery();
                                            this.mainservice.setMessage({
                                                'title': 'Info',
                                                'text': modifyPatientService.successMsg,
                                                'status': 'info'
                                            });
                                        },(err)=>{
                                            _.assign(patient, $this.service.preparePatientObjectForExternalPatiendIdChange(originalPatientObject.attrs, patient.attrs));
                                            $this.httpErrorHandler.handleError(err);
                                        });
                                    }
                                }
                            },(err)=>{
                                _.assign(patient, originalPatientObject);
                                $this.httpErrorHandler.handleError(err);
                            });
                        }
                    }*/
                }else{
                    _.assign(patient, originalPatientObject);
                }
                $this.dialogRef = null;
            });
        }, (err) => {
            $this.httpErrorHandler.handleError(err);
            console.log('error', err);
        });
    };
    deleteStudy = function(study){
        console.log('study', study);
        // if(study.attrs['00201208'].Value[0] === 0){
        let $this = this;
        this.confirm({
            content: 'Are you sure you want to delete this study?'
        }).subscribe(result => {
            $this.cfpLoadingBar.start();
            if (result){
                $this.$http.delete(
                    '../aets/' + $this.aet + '/rs/studies/' + study.attrs['0020000D'].Value[0]
                ).subscribe(
                    (response) => {
                        $this.mainservice.setMessage({
                            'title': 'Info',
                            'text': 'Study deleted successfully!',
                            'status': 'info'
                        });
                        console.log('response', response);
                        $this.fireRightQuery();
                        $this.cfpLoadingBar.complete();
                    },
                    (response) => {
                        $this.httpErrorHandler.handleError(response);
                        $this.cfpLoadingBar.complete();
                    }
                );
            }
            $this.cfpLoadingBar.complete();
        });
/*        vex.dialog.confirm({
            message: 'Are you sure you want to delete this study?',
            callback: function(value) {
                if(value){
                    $http({
                        method: 'DELETE',
                        url:"./rs/aets/"+$scope.aet+"/rs/studies/"+study.attrs["0020000D"].Value[0],
                    }).then(
                        function successCallback(response) {
                            DeviceService.msg($scope, {
                                "title": "Info",
                                "text": "Study deleted successfully!",
                                "status": "info"
                            });
                            console.log("response",response);
                            fireRightQuery();
                            cfpLoadingBar.complete();
                        },
                        function errorCallback(response) {
                            DeviceService.msg($scope, {
                                // "title": "Error",
                                // "text": "Error deleting study!",
                                // "status": "error"
                                "title": "Error "+response.status,
                                "text": response.data.errorMessage,
                                "status": "error"
                            });
                            cfpLoadingBar.complete();
                        }
                    );
                }else{
                    $log.log("deleting canceled");
                    cfpLoadingBar.complete();
                }
            }
        });*/
    };
    rejectStudy(study) {
        let $this = this;
        if (this.trashaktive) {
            this.$http.post(
                this.studyURL(study.attrs) + '/reject/' + this.rjcode.codeValue + '^' + this.rjcode.codingSchemeDesignator,
                {},
                $this.jsonHeader
            ).subscribe(
                (res) => {
                // $scope.queryStudies($scope.studies[0].offset);
                    $this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'Study restored successfully!',
                        'status': 'info'
                    });
                    $this.queryStudies($this.patients[0].offset);
                },
                (response) => {
                    $this.httpErrorHandler.handleError(response);
                    console.log('response', response);
                }
            );
        }else{
            let select: any = [];
            _.forEach(this.rjnotes, (m, i) => {
                select.push({
                    title: m.codeMeaning,
                    value: m.codeValue + '^' + m.codingSchemeDesignator,
                    label: m.label
                });
            });
            let parameters: any = {
                content: 'Select rejected type',
                select: select,
                result: {select: this.rjnotes[0].codeValue + '^' + this.rjnotes[0].codingSchemeDesignator},
                saveButton: 'REJECT'
            };
            this.confirm(parameters).subscribe(result => {
                if (result) {
                    $this.cfpLoadingBar.start();
                    $this.$http.post(
                        $this.studyURL(study.attrs) + '/reject/' + parameters.result.select,
                        {},
                        $this.jsonHeader
                    ).subscribe(
                        (response) => {
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'Study rejected successfully!',
                                'status': 'info'
                            });

                            // patients.splice(patientkey,1);
                            $this.fireRightQuery();
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
        if (this.trashaktive) {
            this.$http.post(
                this.seriesURL(series.attrs) + '/reject/' + this.rjcode.codeValue + '^' + this.rjcode.codingSchemeDesignator,
                {},
                $this.jsonHeader
            ).subscribe(
                (res) => {
                // $scope.queryStudies($scope.studies[0].offset);
                    $this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'Series restored successfully!',
                        'status': 'info'
                    });
                    $this.queryStudies($this.patients[0].offset);
                },
                (response) => {
                    $this.httpErrorHandler.handleError(response);
                    console.log('response', response);
                }
            );
        }else{
            let select: any = [];
            _.forEach(this.rjnotes, (m, i) => {
                select.push({
                    title: m.codeMeaning,
                    value: m.codeValue + '^' + m.codingSchemeDesignator,
                    label: m.label
                });
            });
            let parameters: any = {
                content: 'Select rejected type',
                select: select,
                result: {select: this.rjnotes[0].codeValue + '^' + this.rjnotes[0].codingSchemeDesignator},
                saveButton: 'REJECT'
            };

            console.log('parameters', parameters);
            this.confirm(parameters).subscribe(result => {
                if (result) {
                    console.log('result', result);
                    console.log('parameters', parameters);
                    $this.cfpLoadingBar.start();
                    $this.$http.post(
                        $this.seriesURL(series.attrs) + '/reject/' + parameters.result.select,
                        {},
                        $this.jsonHeader
                    ).subscribe(
                        (response) => {
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'Series rejected successfully!',
                                'status': 'info'
                            });

                            // patients.splice(patientkey,1);
                            $this.fireRightQuery();
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
        if (this.trashaktive) {
            this.$http.post(
                this.instanceURL(instance.attrs) + '/reject/' + this.rjcode.codeValue + '^' + this.rjcode.codingSchemeDesignator,
                {},
                $this.jsonHeader
            ).subscribe(
                (res) => {
                    // $scope.queryStudies($scope.studies[0].offset);
                    $this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'Instance restored successfully!',
                        'status': 'info'
                    });
                    $this.queryStudies($this.patients[0].offset);
                },
                (response) => {
                    $this.httpErrorHandler.handleError(response);
                    console.log('response', response);
                }
            );
        }else{

            let select: any = [];
            _.forEach(this.rjnotes, (m, i) => {
                select.push({
                    title: m.codeMeaning,
                    value: m.codeValue + '^' + m.codingSchemeDesignator,
                    label: m.label
                });
            });
            let parameters: any = {
                content: 'Select rejected type',
                select: select,
                result: {select: this.rjnotes[0].codeValue + '^' + this.rjnotes[0].codingSchemeDesignator},
                saveButton: 'REJECT'
            };
            console.log('parameters', parameters);
            this.confirm(parameters).subscribe(result => {
                if (result) {
                    console.log('result', result);
                    console.log('parameters', parameters);
                    $this.cfpLoadingBar.start();
                    $this.$http.post(
                        $this.instanceURL(instance.attrs) + '/reject/' + parameters.result.select,
                        {},
                        $this.jsonHeader
                    ).subscribe(
                        (response) => {
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'Instance rejected successfully!',
                                'status': 'info'
                            });

                            // patients.splice(patientkey,1);
                            $this.fireRightQuery();
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
    deletePatient(patient, patients, patientkey){
        // console.log("study",study);
        if (!_.hasIn(patient, 'attrs["00201200"].Value[0]') || patient.attrs['00201200'].Value[0] === ''){
            this.mainservice.setMessage({
                'title': 'Error',
                'text': 'Cannot delete patient with empty Patient ID!',
                'status': 'error'
            });
            this.cfpLoadingBar.complete();
        }else{
            let $this = this;
            this.confirm({
                content: 'Are you sure you want to delete this patient?'
            }).subscribe(result => {
                if (result){
                    $this.cfpLoadingBar.start();
                    $this.$http.delete('../aets/' + $this.aet + '/rs/patients/' + encodeURIComponent(this.service.getPatientId(patient.attrs)),undefined, true).subscribe(
                        (response) => {
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'Patient deleted successfully!',
                                'status': 'info'
                            });
                            console.log('patients', patients);
                            console.log('patientkey', patientkey);
                            // patients.splice(patientkey,1);
                            $this.fireRightQuery();
                            $this.cfpLoadingBar.complete();
                        },
                        (err) => {
                            $this.httpErrorHandler.handleError(err);
                            // angular.element("#querypatients").trigger('click');
                            $this.cfpLoadingBar.complete();
                        }
                    );
                }
            });
        }
    };
    deleteMWL(mwl){

        let $this = this;
        this.confirm({
            content: 'Are you sure you want to delete this MWL?'
        }).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                $this.$http.delete(
                    `../aets/${this.aet}/rs/mwlitems/${mwl.attrs['0020000D'].Value[0]}/${mwl.attrs['00400100'].Value[0]['00400009'].Value[0]}`
                ).subscribe(
                    (response) => {
                        $this.mainservice.setMessage({
                            'title': 'Info',
                            'text': 'MWL deleted successfully!',
                            'status': 'info'
                        });
                        $this.fireRightQuery();
                        $this.cfpLoadingBar.complete();
                    },
                    (response) => {
                        $this.httpErrorHandler.handleError(response);
                        console.log('response', response);

                        $this.cfpLoadingBar.complete();
                    }
                );
            }
        });
    };
    keyDownOnHeader(event){
        if (event.keyCode == 13) {
            this.fireRightQuery();
        }
    }
    retrieveMultipleStudies(){
/*        this.cfpLoadingBar.start();
        this.service.getCount(
            this.rsURL(),
            'studies',
            this.createStudyFilterParams()
        ).subscribe((res)=>{
            this.cfpLoadingBar.complete();
            this.count = res.count;
        });*/
            this.exporter(
                // `/aets/${this.aet}/dimse/${this.externalAET}/studies/query:${this.queryAET}/export/dicom:${destinationAET}`,
                '',
                'Retrieve matching studies depending on selected filters, from external C-MOVE SCP',
                '',
                'multiple',
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
            let url = `${this.rsURL()}/studies`;
            this._keycloakService.getToken().subscribe((response)=>{
                if(!this.mainservice.global.notSecure){
                    token = response.token;
                }
                let filterClone = _.cloneDeep(queryParameters);
                delete filterClone['offset'];
                delete filterClone['limit'];
                filterClone["accept"] = `text/csv${(semicolon?';delimiter=semicolon':'')}`;
                let fileName = "dcm4chee.csv";
                if(attr && mode){
                    filterClone["PatientID"] =  this.valueOf(attr['00100020']);
                    filterClone["IssuerOfPatientID"] = this.valueOf(attr['00100021']);
                    if(mode === "series" && _.hasIn(attr,'0020000D')){
                        url =`${url}/${this.valueOf(attr['0020000D'])}/series`;
                        fileName = `${this.valueOf(attr['0020000D'])}.csv`;
                    }
                    if(mode === "instance"){
                        url =`${url}/${this.valueOf(attr['0020000D'])}/series/${this.valueOf(attr['0020000E'])}/instances`;
                        fileName = `${this.valueOf(attr['0020000D'])}_${this.valueOf(attr['0020000E'])}.csv`;
                    }
                }
                if(!this.mainservice.global.notSecure){
                    filterClone["access_token"] = token;
                }
                j4care.downloadFile(`${url}?${this.mainservice.param(filterClone)}`,fileName);
                // WindowRefService.nativeWindow.open(`${url}?${this.mainservice.param(filterClone)}`);
            });
        })
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
                this.service.scheduleStorageVerification(_.merge(ok.schema_model , this.createStudyFilterParams()), this.aetmodel.dicomAETitle).subscribe(res=>{
                    console.log("res");
                    this.cfpLoadingBar.complete();
                    this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'Storage Verification scheduled successfully!',
                        'status': 'info'
                    });
                },err=>{
                    this.cfpLoadingBar.complete();
                    this.httpErrorHandler.handleError(err);
                });
            }
        });
    }
    exportStudy(study) {
        this.exporter(
            this.studyURL(study.attrs),
            'Export study',
            'Study will not be sent!',
            'single',
            study.attrs,
            "study"
        );
    };
    exportSeries(series) {
        this.exporter(
            this.seriesURL(series.attrs),
            'Export series',
            'Series will not be sent!',
            'single',
            series.attrs,
            "series"
        );
    };
    exportInstance(instance) {
        this.exporter(
            this.instanceURL(instance.attrs),
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
        if(mode === "multiple"){
            config = {
                height: 'auto',
                width: '600px'
            };
        }
        this.dialogRef = this.dialog.open(ExportDialogComponent, config);
        this.dialogRef.componentInstance.noDicomExporters = noDicomExporters;
        this.dialogRef.componentInstance.dicomPrefixes = dicomPrefixes;
        this.dialogRef.componentInstance.externalInternalAetMode = this.externalInternalAetMode;
        this.dialogRef.componentInstance.title = title;
        this.dialogRef.componentInstance.mode = mode;
        this.dialogRef.componentInstance.queues = this.queues;
        this.dialogRef.componentInstance.warning = warning;
        this.dialogRef.componentInstance.count = this.count;
        if($this.externalInternalAetMode === 'external') {
            this.dialogRef.componentInstance.preselectedExternalAET = this.externalInternalAetModel.dicomAETitle;
        }
        this.dialogRef.afterClosed().subscribe(result => {
            if (result){
                let batchID = "";
                let params = {};
                if(result.batchID)
                    batchID = `batchID=${result.batchID}&`;
                $this.cfpLoadingBar.start();
                if(mode === "multiple"){
                    urlRest = `../aets/${result.selectedAet}/dimse/${result.externalAET}/studies/query:${result.queryAET}/export/dicom:${result.destinationAET}?${batchID}${ this.mainservice.param(this.createStudyFilterParams())}` ;
                }else{
                    if(mode === 'multipleExport'){
                        let checkbox = `${(result.checkboxes['only-stgcmt'] && result.checkboxes['only-stgcmt'] === true)? 'only-stgcmt=true':''}${(result.checkboxes['only-ian'] && result.checkboxes['only-ian'] === true)? 'only-ian=true':''}`;
                        if(checkbox != '' && this.mainservice.param(this.createStudyFilterParams()) != '')
                            checkbox = '&' + checkbox;
                        urlRest = `../aets/${this.aet}/export/${result.selectedExporter}/studies?${batchID}${this.mainservice.param(this.createStudyFilterParams())}${checkbox}`;
                    }else{
                        if($this.externalInternalAetMode === 'external'){
                            // let param = result.dcmQueueName ? `?${batchID}dcmQueueName=${result.dcmQueueName}` : '';
                            if(result.dcmQueueName){
                                params['dcmQueueName'] = result.dcmQueueName
                            }
                            urlRest = `${url}/export/dicom:${result.selectedAet}${j4care.param(params)}`;
                            // urlRest = `../aets/${this.aet}/dimse/${result.externalAET}/studies/${objectAttr['0020000D'].Value[0]}/export/dicom:${result.selectedAet}${param}`;
    /*                        switch (dicomMode){
                                case 'study':
                                    console.log("newUrl",this.studyURL(objectAttr));
                                    break;
                                case 'series':
                                    console.log("newUrl",this.seriesURL(objectAttr));
                                    break;
                                case 'instance':
                                    console.log("newUrl",this.instanceURL(objectAttr));
                                    break;
                            }
                            id = 'dicom:' + result.selectedAet;
                            urlRest = url  + '/export/' + id + '?' + this.mainservice.param({queue:result.queue});*/
                        }else{
                            if (result.exportType === 'dicom'){
                                //id = result.dicomPrefix + result.selectedAet;
                                id = 'dicom:' + result.selectedAet;
                            }else{
                                id = result.selectedExporter;
                            }
                            urlRest = url  + '/export/' + id + '?'+ batchID + this.mainservice.param(result.checkboxes);
                        }
                    }
                }
                $this.$http.post(
                    urlRest,
                    undefined,
                    $this.jsonHeader
                ).subscribe(
                    (result) => {
                        $this.mainservice.setMessage({
                            'title': 'Info',
                            'text': $this.service.getMsgFromResponse(result,'Command executed successfully!'),
                            'status': 'info'
                        });
                        $this.cfpLoadingBar.complete();
                    },
                    (err) => {
                        console.log("err",err);
                        $this.mainservice.setMessage({
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
    queryMWL(offset){
        this.queryMode = 'queryMWL';
        this.moreStudies = undefined;
        this.morePatients = undefined;
        if (offset < 0 || offset === undefined) offset = 0;
        this.cfpLoadingBar.start();
        let $this = this;
        if(this.externalInternalAetMode === 'internal'){
            this.service.getCount(
                this.rsURL(),
                'mwlitems',
                this.createPatientFilterParams()
            ).subscribe((res)=>{
                this.count = res.count;
            },(err)=>{
                this.cfpLoadingBar.complete();
                this.httpErrorHandler.handleError(err);
            });
        }else{
            this.count = "";
        }
        this.service.queryMwl(
            this.rsURL(),
            this.createQueryParams(offset, this.limit + 1, this.createMwlFilterParams())
        ).subscribe((res) => {
                $this.patients = [];
                //           $this.studies = [];
                $this.morePatients = undefined;
                $this.moreMWL = undefined;
                if (_.size(res) > 0){
                    let pat, mwl, patAttrs, tags = $this.attributeFilters.Patient.dcmTag;
                    res.forEach(function (studyAttrs, index) {
                        patAttrs = {};
                        $this.extractAttrs(studyAttrs, tags, patAttrs);
                        if (!(pat && _.isEqual(pat.attrs, patAttrs))) {
                            pat = {
                                attrs: patAttrs,
                                mwls: [],
                                showmwls: true,
                                showAttributes: false
                            };
                            // $this.$apply(function () {
                            $this.patients.push(pat);
                            // $this.mwl.push(pat);
                            // });
                        }
                        mwl = {
                            patient: pat,
                            offset: offset + index,
                            moreSeries: false,
                            attrs: studyAttrs,
                            series: null,
                            showAttributes: false,
                            fromAllStudies: false,
                            selected: false
                        };
                        pat.mwls.push(mwl);
                    });
                    console.log('in mwl patient', $this.patients);
                    $this.extendedFilter(false);
                    if ($this.moreMWL = (res.length > $this.limit)) {
                        pat.mwls.pop();
                        if (pat.mwls.length === 0)
                            $this.patients.pop();
                        // $this.studies.pop();
                    }
                    // $this.mainservice.setGlobal({
                    //     patients:this.patients,
                    //     moreMWL:$this.moreMWL,
                    //     morePatients:$this.morePatients,
                    //     moreStudies:$this.moreStudies
                    // });
                } else {
                    this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'No matching Modality Worklist Entries found!',
                        'status': 'info'
                    });
                }
                // console.log("$this.patients",$this.patients);
                $this.cfpLoadingBar.complete();
            },
            (err) => {
                $this.cfpLoadingBar.complete();
                $this.httpErrorHandler.handleError(err);
            }
        );
    };
    setTrash(){
        this.aet = this.aetmodel.dicomAETitle;
        let $this = this;
        if (this.aetmodel.dcmHideNotRejectedInstances === true){
            if (this.rjcode === null){
                this.$http.get('../reject?dcmRevokeRejection=true')
                    // .map((res) => res.json())
                    .subscribe((res)=>{
                        $this.rjcode = res[0];
                    });
            }
            //this.filter.returnempty = false;
            this.trashaktive = true;
        }else{
            this.trashaktive = false;
        }
        this.fireRightQuery();
    };
    fireQueryOnChange(order){
        console.log('order2', order);
        switch (order.mode) {
            case 'patient':
                console.log('in patientcase', this);
                this.patientmode = true;
                this.queryPatients(0);
                break;
            case 'study':
                this.patientmode = false;
                this.queryStudies(0);
                break;
            case 'mwl':
                this.patientmode = false;
                this.queryMWL(0);
                break;
            case 'diff':
                this.patientmode = false;
                this.patients = [];
                this.moreMWL = false;
                this.moreStudies = false;
                this.morePatients = false;
                this.getAllAes(0);
                break;
        }
    }
    fireRightQuery(){
        console.log('querymode=', this.queryMode);
        this.showFilterWarning = false;
        this[this.queryMode](0);
    }
    querySeries = function(study, offset) {
        console.log('in query sersies study=', study);
        this.cfpLoadingBar.start();
        if (offset < 0) offset = 0;
        let $this = this;
        this.service.querySeries(
            this.rsURL(),
            study.attrs['0020000D'].Value[0],
            this.createQueryParams(offset, this.limit + 1, { orderby: 'SeriesNumber'})
        ).subscribe((res) => {
            if (res){
                if (res.length === 0){
                    this.mainservice.setMessage( {
                        'title': 'Info',
                        'text': 'No matching series found!',
                        'status': 'info'
                    });
                    console.log('in reslength 0');
                }else{

                    study.series = res.map(function (attrs, index) {
                        return {
                            study: study,
                            offset: offset + index,
                            moreInstances: false,
                            attrs: attrs,
                            instances: null,
                            showAttributes: false,
                            selected: false
                        };
                    });
                    if (study.moreSeries = (study.series.length > $this.limit)) {
                        study.series.pop();
                    }
                    // StudiesService.trim(this);
                }
                $this.cfpLoadingBar.complete();
            }else{
                this.mainservice.setMessage( {
                    'title': 'Info',
                    'text': 'No matching series found!',
                    'status': 'info'
                });
            }
        },(err)=>{
            $this.cfpLoadingBar.complete();
            $this.httpErrorHandler.handleError(err);
        });
    };

    queryInstances = function (series, offset) {
        this.cfpLoadingBar.start();
        if (offset < 0) offset = 0;
        let $this = this;
        this.service.queryInstances(
            this.rsURL(),
            series.attrs['0020000D'].Value[0],
            series.attrs['0020000E'].Value[0],
            this.createQueryParams(offset, this.limit + 1, { orderby: 'InstanceNumber'})
        ).subscribe((res)=>{
                if (res){
                    series.instances = res.map(function(attrs, index) {
                        let numberOfFrames = $this.valueOf(attrs['00280008']),
                            gspsQueryParams = $this.createGSPSQueryParams(attrs),
                            video = $this.isVideo(attrs),
                            image = $this.isImage(attrs);
                        $this.cfpLoadingBar.complete();
                        return {
                            series: series,
                            offset: offset + index,
                            attrs: attrs,
                            showAttributes: false,
                            showFileAttributes: false,
                            wadoQueryParams: {
                                studyUID: attrs['0020000D'].Value[0],
                                seriesUID: attrs['0020000E'].Value[0],
                                objectUID: attrs['00080018'].Value[0]
                            },
                            video: video,
                            image: image,
                            numberOfFrames: numberOfFrames,
                            gspsQueryParams: gspsQueryParams,
                            views: $this.createArray(video || numberOfFrames || gspsQueryParams.length || 1),
                            view: 1,
                            selected: false
                        };
                    });
                    console.log("series",series);
                    console.log("series.instances",series.instances);
                }else{
                    series.instances = {};
                }
                if (series.moreInstances = (series.instances.length > $this.limit)) {
                    series.instances.pop();
                }
                // StudiesService.trim(this);
                $this.cfpLoadingBar.complete();
        },(err)=>{
            $this.cfpLoadingBar.complete();
            $this.httpErrorHandler.handleError(err);
        });
    };
    queryAllStudiesOfPatient = function(patient, offset, event) {
        console.log('in queryallstudies');
        event.preventDefault();
        this.cfpLoadingBar.start();
        if (offset < 0) offset = 0;
        let $this = this;
        this.service.queryStudies(
            this.rsURL(),
            this.createQueryParams(offset, this.limit + 1, {
                PatientID: this.valueOf(patient.attrs['00100020']),
                IssuerOfPatientID: this.valueOf(patient.attrs['00100021']),
                orderby: this.filter.orderby !== 'StudyDate,StudyTime' ? '-StudyDate,-StudyTime' : this.filter.orderby
            })
        )
        .subscribe((res) => {
            console.log('res in queryallstudy', res);
            if (res && res.length > 0){
                patient.studies = res.map(function (attrs, index) {
                    return {
                        patient: patient,
                        offset: offset + index,
                        moreSeries: false,
                        attrs: attrs,
                        series: null,
                        showAttributes: false,
                        fromAllStudies: true,
                        selected: false
                    };
                });
                if (patient.moreStudies = (patient.studies.length > $this.limit)) {
                    patient.studies.pop();
                }
                // StudiesService.trim($this);
                // console.log("patient",patient);
            }else{
                this.mainservice.setMessage( {
                    'title': 'Info',
                    'text': 'No matching Studies found!',
                    'status': 'info'
                });
            }
            this.cfpLoadingBar.complete();
        },(err)=>{
                $this.cfpLoadingBar.complete();
                $this.httpErrorHandler.handleError(err);
        });
    };
    getCount(){
        let mode;
        let filters;
        if(this.queryMode === "queryStudies"){
            mode = "studies"
            filters = this.createStudyFilterParams();
        }
        if(this.queryMode === "queryPatients"){
            mode = "patients"
            filters = this.createPatientFilterParams();
        }
        if(this.queryMode === "queryMWL"){
            mode = "mwlitems"
            filters = this.createMwlFilterParams();
        }
        if (this.showNoFilterWarning(filters)) {
            this.confirm({
                content: 'No filter are set, are you sure you want to continue?'
            }).subscribe(result => {
                if (result){
                    this.getCountService(mode,filters);
                }
            });
        }else{
            this.getCountService(mode,filters);
        }
    }
    getCountService(mode, filters){
        let clonedFilters = _.cloneDeep(filters);
        delete clonedFilters.orderby;
        delete clonedFilters.limit;
        this.showGetCountLoader = true;
        this.service.getCount(
            this.rsURL(),
            mode,
            clonedFilters
        ).subscribe((res)=>{
            this.showGetCountLoader = false;
            try{
                this.count = res.count;
            }catch (e){
                console.error("count error",e);
            }
        },(err)=>{
            this.showGetCountLoader = false;
            this.httpErrorHandler.handleError(err);
        });
    }
    getSize(){
        let filters = this.createStudyFilterParams();
        if (this.showNoFilterWarning(filters)) {
            this.confirm({
                content: 'No filter are set, are you sure you want to continue?'
            }).subscribe(result => {
                if (result){
                    this.getSizeService(filters);
                }
            });
        }else{
            this.getSizeService(filters);
        }
    }
    getSizeService(filters){
        let clonedFilters = _.cloneDeep(filters);
        delete clonedFilters.orderby;
        delete clonedFilters.limit;
        this.showGetSizeLoader = true;
        this.service.getSize(
            this.rsURL(),
            clonedFilters
        ).subscribe((res)=>{
            this.showGetSizeLoader = false;
            try {
                this.size = j4care.convertBtoHumanReadable(res.size,1);
            }catch (e){
                console.log("convert byte error:",e);
            }
        },(err)=>{
            this.showGetSizeLoader = false;
            this.httpErrorHandler.handleError(err);
        });
    }
    queryPatients = function(offset){
        this.queryMode = 'queryPatients';
        this.moreStudies = undefined;
        this.moreMWL = undefined;
        this.cfpLoadingBar.start();
        let $this = this;
        if (offset < 0) offset = 0;
        if(this.externalInternalAetMode === 'internal'){
            this.service.getCount(
                this.rsURL(),
                'patients',
                this.createPatientFilterParams()
            ).subscribe((res)=>{
                this.count = res.count;
            });
        }else{
            this.count = "";
        }
        this.service.queryPatients(
            this.rsURL(),
            this.createQueryParams(offset, this.limit + 1, this.createPatientFilterParams())
        ).subscribe((res) => {
            $this.morePatients = undefined;
            $this.moreStudies = undefined;
            if (_.size(res) > 0){
                $this.patients = res.map(function (attrs, index) {
                    return {
                        moreStudies: false,
                        offset: offset + index,
                        attrs: attrs,
                        studies: null,
                        showAttributes: false,
                        selected: false
                    };
                });
                if ($this.morePatients = ($this.patients.length > $this.limit)) {
                    $this.patients.pop();
                }
            } else {
                $this.patients = [];
                $this.mainservice.setMessage( {
                    "title": "Info",
                    "text": "No matching Patients found!",
                    "status": "info"
                });
            }
            $this.extendedFilter(false);
            // var state = ($this.allhidden) ? "hide" : "show";
            // setTimeout(function(){
            //     togglePatientsHelper(state);
            // }, 1000);
            $this.cfpLoadingBar.complete();
        },(err)=>{
            $this.cfpLoadingBar.complete();
            $this.httpErrorHandler.handleError(err);
        });
    };

    toggleAttributs(instance, art){
        if (art === 'fileattributs'){
            instance.showAttributes = false;
            instance.showFileAttributes = !instance.showFileAttributes;
        }else{
            instance.showAttributes = !instance.showAttributes;
            instance.showFileAttributes = false;
        }
    };
    // addFileAttribute(instance){
    //     // var id      = '#file-attribute-list-'+(instance.attrs['00080018'].Value[0]).replace(/\./g, '');
    //     // if($(id).find("*").length < 1){
    //     //     this.cfpLoadingBar.start();
    //     //     var html    = '<file-attribute-list [studyuid]="'+ instance.wadoQueryParams.studyUID +'" [seriesuid]="'+ instance.wadoQueryParams.seriesUID +'"  [objectuid]="'+ instance.wadoQueryParams.objectUID+ '" [aet]="'+this.aet+'" ></file-attribute-list>';
    //     //     // console.log("html=",html);
    //     //     // cfpLoadingBar.set(cfpLoadingBar.status()+(0.2));
    //     //     $(id).html(html);
    //     //     this.cfpLoadingBar.complete();
    //     // }
    //     if(!instance.showFileAttributes || instance.showFileAttributes === false){
    //         instance.showFileAttributes = true;
    //     }else{
    //         instance.showFileAttributes = false;
    //     }
    // };
    downloadURL(inst, transferSyntax) {
        let token;
        let url = "";
        let fileName = "dcm4che.dcm";
        this._keycloakService.getToken().subscribe((response)=>{
            if(!this.mainservice.global.notSecure){
                token = response.token;
            }
            let exQueryParams = { contentType: 'application/dicom'};
            if (transferSyntax){
                exQueryParams["transferSyntax"] = {};
                exQueryParams["transferSyntax"] = transferSyntax;
            }
            if(!this.mainservice.global.notSecure){
                // WindowRefService.nativeWindow.open(this.wadoURL(inst.wadoQueryParams, exQueryParams) + `&access_token=${token}`);
                url = this.wadoURL(inst.wadoQueryParams, exQueryParams) + `&access_token=${token}`;
            }else{
                // WindowRefService.nativeWindow.open(this.wadoURL(inst.wadoQueryParams, exQueryParams));
                url = this.wadoURL(inst.wadoQueryParams, exQueryParams);
            }
            if(j4care.hasSet(inst, "attrs[00080018].Value[0]")){
                fileName = `${_.get(inst, "attrs[00080018].Value[0]")}.dcm`
            }
            j4care.downloadFile(url,fileName);
        });
    };
    downloadZip(object, level, mode){
        let token;
        let param = 'accept=application/zip';
        let url = this.studyURL(object.attrs);
        let fileName = this.studyFileName(object.attrs);
        if(mode === 'compressed'){
            param += ';transfer-syntax=*';
        }
        if(level === 'serie'){
            url = this.seriesURL(object.attrs);
            fileName = this.seriesFileName(object.attrs);
        }
        this._keycloakService.getToken().subscribe((response)=>{
            if(!this.mainservice.global.notSecure){
                token = response.token;
            }
            if(!this.mainservice.global.notSecure){
                j4care.downloadFile(`${url}?${param}&access_token=${token}`,`${fileName}.zip`)
            }else{
                j4care.downloadFile(`${url}?${param}`,`${fileName}.zip`)
            }
        });
    };
    viewInstance(inst) {
        let $this = this;
        let token;
        let url;
        let contentType;
        this._keycloakService.getToken().subscribe((response)=>{
            if(!this.mainservice.global.notSecure){
                token = response.token;
            }
            this.select_show = false;
            if(inst.video || inst.image || inst.numberOfFrames || inst.gspsQueryParams.length){
                if (inst.gspsQueryParams.length){
                    url =  this.wadoURL(inst.gspsQueryParams[inst.view - 1]);
                }
                if (inst.numberOfFrames || (inst.image && !inst.video)){
                    contentType = 'image/jpeg';
                    url =  this.wadoURL(inst.wadoQueryParams, { contentType: 'image/jpeg'});
                }
                if (inst.video){
                    contentType = 'video/*';
                    url =  this.wadoURL(inst.wadoQueryParams, { contentType: 'video/*' });
                }
            }else{
                url = this.wadoURL(inst.wadoQueryParams);
            }
            if(!contentType){
                if(_.hasIn(inst,"attrs.00420012.Value.0") && inst.attrs['00420012'].Value[0] != ''){
                    contentType = inst.attrs['00420012'].Value[0];
                }
            }
            if(!contentType || contentType.toLowerCase() === 'application/pdf' || contentType.toLowerCase().indexOf("video") > -1 || contentType.toLowerCase() === 'text/xml'){
                // this.j4care.download(url);
                if(!this.mainservice.global.notSecure){
                    WindowRefService.nativeWindow.open(this.renderURL(inst) + `&access_token=${token}`);
                }else{
                    WindowRefService.nativeWindow.open(this.renderURL(inst));
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
                this.dialogRef.afterClosed().subscribe((result) => {
                    console.log('result', result);
                    if (result){
                    }
                });
                console.log("this.renderURL(inst)",this.renderURL(inst));
            }
        // window.open(this.renderURL(inst));
        });
    };
    select(object, modus, keys, fromcheckbox){
        // let test = true;
        let clickFromCheckbox = (fromcheckbox && fromcheckbox === 'fromcheckbox');
        if (this.isRole('admin')){
            this.anySelected = true;
            this.lastSelectedObject = object;
            this.lastSelectedObject.modus = modus;
            /*
            * If the function was called from checkbox go in there
            * */
            if (clickFromCheckbox){
                console.log('in if fromcheckbox', fromcheckbox);
                this.selectObject(object, modus, true);
            }

            console.log('in ctrlclick keysdown', this.keysdown);
            console.log('Object.keys(this.keysdown).length', Object.keys(this.keysdown).length);
            //ctrl + click
            if (this.keysdown && Object.keys(this.keysdown).length === 1 && (this.keysdown[17] === true || this.keysdown[91] === true || this.keysdown[93] === true || this.keysdown[224] === true)){
                this.selectObject(object, modus, false);
            }
            // //close contextmenu (That's a bug on contextmenu module. The bug has been reported)
            // $(".dropdown.contextmenu").addClass('ng-hide');

            //Shift + click
            console.log('before shiftclick');
            if (this.keysdown && Object.keys(this.keysdown).length === 1 && this.keysdown[16] === true){
                console.log('in shift click');
                this.service.clearSelection(this.patients);
                if (!this.lastSelect){
                    this.selectObject(object, modus, false);
                    this.lastSelect = {'keys': keys, 'modus': modus};
                }else{
                    if (modus != this.lastSelect.modus){
                        this.service.clearSelection(this.patients);
                        this.selectObject(object, modus, false);
                        this.lastSelect = {'keys': keys, 'modus': modus};
                    }else{
                        switch (modus) {
                            case 'patient':
                                this.selectObject(object, modus, false);
                                break;
                            case 'study':
                                // {"patientkey":patientkey,"studykey":studykey}
                                if (keys.patientkey != this.lastSelect.keys.patientkey){
                                    this.service.clearSelection(this.patients);
                                    this.selectObject(object, modus, false);
                                    this.lastSelect = {'keys': keys, 'modus': modus};
                                }else{
                                    console.log('keys.studykey', keys.studykey);
                                    console.log('this.lastSelect.keys.studykey', this.lastSelect.keys.studykey);
                                    if (keys.studykey > this.lastSelect.keys.studykey){
                                        for (let i = keys.studykey; i >= this.lastSelect.keys.studykey; i--) {
                                            console.log('i', i);
                                            console.log('this.patients[keys.patientkey].studies[i]=', this.patients[keys.patientkey].studies[i]);
                                            // this.patients[keys.patientkey].studies[i].selected = true;
                                            this.selectObject(this.patients[keys.patientkey].studies[i], modus, false);
                                        }
                                    }else{
                                        for (let i = this.lastSelect.keys.studykey; i >= keys.studykey; i--) {
                                            console.log('this.patients[keys.patientkey].studies[i]=', this.patients[keys.patientkey].studies[i]);
                                            // this.patients[keys.patientkey].studies[i].selected = true;
                                            this.selectObject(this.patients[keys.patientkey].studies[i], modus, false);
                                        }
                                    }
                                    this.lastSelect = {};
                                }
                                break;
                            case 'series':
                                console.log('series');
                                console.log('keys', keys);
                                if (keys.patientkey != this.lastSelect.keys.patientkey || keys.studykey != this.lastSelect.keys.studykey){
                                    this.service.clearSelection(this.patients);
                                    this.selectObject(object, modus, false);
                                    this.lastSelect = {'keys': keys, 'modus': modus};
                                }else{
                                    console.log('keys.studykey', keys.serieskey);
                                    console.log('this.lastSelect.keys.studykey', this.lastSelect.keys.serieskey);
                                    if (keys.serieskey > this.lastSelect.keys.serieskey){
                                        for (let i = keys.serieskey; i >= this.lastSelect.keys.serieskey; i--) {
                                            console.log('i', i);
                                            console.log('this.patients[keys.patientkey].studies[i]=', this.patients[keys.patientkey].studies[keys.studykey].series[i]);
                                            // this.patients[keys.patientkey].studies[i].selected = true;
                                            this.selectObject(this.patients[keys.patientkey].studies[keys.studykey].series[i], modus, false);
                                        }
                                    }else{
                                        for (let i = this.lastSelect.keys.serieskey; i >= keys.serieskey; i--) {
                                            console.log('this.patients[keys.patientkey].studies[i]=', this.patients[keys.patientkey].studies[keys.studykey].series[i]);
                                            // this.patients[keys.patientkey].studies[i].selected = true;
                                            this.selectObject(this.patients[keys.patientkey].studies[keys.studykey].series[i], modus, false);
                                        }
                                    }
                                    this.lastSelect = {};
                                }
                                break;
                            case 'instance':
                                console.log('series');
                                console.log('keys', keys);
                                console.log('this.patients', this.patients[keys.patientkey]);
                                if (keys.patientkey != this.lastSelect.keys.patientkey || keys.studykey != this.lastSelect.keys.studykey || keys.serieskey != this.lastSelect.keys.serieskey){
                                    this.service.clearSelection(this.patients);
                                    this.selectObject(object, modus, false);
                                    this.lastSelect = {'keys': keys, 'modus': modus};
                                }else{
                                    console.log('keys.studykey', keys.instancekey);
                                    console.log('this.lastSelect.keys.studykey', this.lastSelect.keys.instancekey);
                                    if (keys.instancekey > this.lastSelect.keys.instancekey){
                                        for (let i = keys.instancekey; i >= this.lastSelect.keys.instancekey; i--) {
                                            console.log('i', i);
                                            // console.log("this.patients[keys.patientkey].studies[i]=",this.patients[keys.patientkey].studies[keys.studykey].series[keys.studykey].instances[i]);
                                            // this.patients[keys.patientkey].studies[i].selected = true;
                                            this.selectObject(this.patients[keys.patientkey].studies[keys.studykey].series[keys.serieskey].instances[i], modus, false);
                                        }
                                    }else{
                                        for (let i = this.lastSelect.keys.instancekey; i >= keys.instancekey; i--) {
                                            // console.log("this.patients[keys.patientkey].studies[keys.studykey].series[keys.studykey].instances[i]=",this.patients[keys.patientkey].studies[keys.studykey].series[keys.studykey].instances[i]);
                                            // this.patients[keys.patientkey].studies[i].selected = true;
                                            this.selectObject(this.patients[keys.patientkey].studies[keys.studykey].series[keys.serieskey].instances[i], modus, false);
                                        }
                                    }
                                    this.lastSelect = {};
                                }
                                break;
                            default:
                            //
                        }
                    }
                }
            }
            console.log('before keyend');
            if (!this.showCheckboxes && !clickFromCheckbox && this.keysdown && Object.keys(this.keysdown).length === 0 && this.anySelected){
                this.service.clearSelection(this.patients);
                this.anySelected = false;
                this.selected = {};
                // this.selected['otherObjects'] = {};
            }
            if (modus === 'patient'){
                console.log('this.selected', this.selected);
                //console.log("this.selectedkeys",Object.keys(this.selected.patients).length);
                //console.log("patient.length",_.size(this.selected.patients));
                if (_.size(this.selected.patients) > 0){
                    this.selected.hasPatient = true;
                }else{
                    this.selected.hasPatient = false;
                }
            }
            try {
                this.clipboardHasScrollbar = $('#clipboard_content').get(0).scrollHeight > $('#clipboard_content').height();
            }catch (e){

            }
        }
    };
    // clipboardHasScrollbar(){
    //     // $.fn.hasScrollBar = function() {
    //     //     return this.get(0).scrollHeight > this.height();
    //     // }
    //     return
    // }
    selectObject(object, modus, fromcheckbox){
        // console.log('in select object modus', modus);
        // console.log('object', object);
        // console.log('object selected', object.selected);
        // console.log('this.clipboard.action', this.clipboard.action);
        this.showClipboardHeaders[modus] = true;
        // if(!fromcheckbox){
            object.selected = !object.selected;
        // }
        this.target = object;
        this.target.modus = modus;
        console.log('2object selected', object.selected);
        // this.selected['otherObjects'][object.attrs["0020000D"].Value[0]]["modus"] = this.selected['otherObjects'][object.attrs["0020000D"].Value[0]]["modus"] || modus;
        // console.log("",);
        if (_.hasIn(object, 'selected') && object.selected === true){
            this.selected['otherObjects'] = this.selected['otherObjects'] || {};
            if (modus === 'patient'){
/*                if(!_.isset(object.attrs["00100020"].Value[0])){

                }*/
                console.log('issettestid =', _.hasIn(object, 'attrs["00100020"].Value[0]'));
                console.log('issuerhasin =', _.hasIn(object, 'attrs["00100021"].Value[0]'));
                console.log('modus in selectObject patient');
                this.selected['patients'] = this.selected['patients'] || [];
                let patientobject = {};
                patientobject['PatientID'] = object.attrs['00100020'].Value[0];
                // if(object.attrs["00100021"] && object.attrs["00100021"].Value && object.attrs["00100021"].Value[0]){
                if (_.hasIn(object, 'attrs["00100021"].Value[0]') && object.attrs['00100021'].Value[0] != ''){
                    patientobject['IssuerOfPatientID'] = object.attrs['00100021'].Value[0];
                }
                // if((object.attrs["00100024"] && object.attrs["00100024"].Value[0]['00400032'] && object.attrs["00100024"].Value[0]['00400032'].Value[0]) && (object.attrs["00100024"] && object.attrs["00100024"].Value[0]['00400033'] && object.attrs["00100024"].Value[0]['00400033'].Value[0])){
                if (_.hasIn(object, 'attrs.00100024.Value[0].00400032.Value[0]') && _.hasIn(object, 'attrs.00100024.Value[0].00400033.Value[0]') && (object.attrs['00100024'].Value[0]['00400032'].Value[0] != '') && (object.attrs['00100024'].Value[0]['00400033'].Value[0] != '')){
                    patientobject['IssuerOfPatientIDQualifiers'] = {
                        'UniversalEntityID': object.attrs['00100024'].Value[0]['00400032'].Value[0],
                        'UniversalEntityIDType': object.attrs['00100024'].Value[0]['00400033'].Value[0]
                    };
                }
                // console.log("check if patient in select selected =",this.service.getPatientId(this.selected.patients));
                let patientInSelectedObject = false;
                _.forEach(this.selected.patients, (m, i) => {
                    // console.log('i=', i);
                    // console.log('m=', m);
                    // console.log('patientid', this.service.getPatientId(m));
                    if (this.service.getPatientId(m) === this.service.getPatientId(patientobject)){
                        patientInSelectedObject = true;
                    }
                });
                patientobject["attrs"] = object.attrs || {};
                console.log('patientobject =', this.service.getPatientId(patientobject));
                if (!patientInSelectedObject){
                    this.selected['patients'].push(patientobject);
                }
/*                this.selected['patients'].push({
                    "PatientID": object.attrs["00100020"].Value[0],
                    "IssuerOfPatientID": ((object.attrs["00100021"] && object.attrs["00100021"].Value && object.attrs["00100021"].Value[0]) ? object.attrs["00100021"].Value[0]:''),
                    "IssuerOfPatientIDQualifiers": {
                        "UniversalEntityID": ((object.attrs["00100024"] && object.attrs["00100024"].Value[0]['00400032'] && object.attrs["00100024"].Value[0]['00400032'].Value[0]) ? object.attrs["00100024"].Value[0]['00400032'].Value[0] : ''),
                        "UniversalEntityIDType": ((object.attrs["00100024"] && object.attrs["00100024"].Value[0]['00400033'] && object.attrs["00100024"].Value[0]['00400033'].Value[0]) ? object.attrs["00100024"].Value[0]['00400033'].Value[0] : '')
                    }
                });*/

                this.selected['otherObjects'][object.attrs['00100020'].Value[0]] = this.selected['otherObjects'][object.attrs['00100020'].Value[0]] || {};
                this.selected['otherObjects'][object.attrs['00100020'].Value[0]] = patientobject;
            }else{
                this.selected['otherObjects'][object.attrs['0020000D'].Value[0]] = this.selected['otherObjects'][object.attrs['0020000D'].Value[0]] || {};
                this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['StudyInstanceUID'] = this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['StudyInstanceUID'] || object.attrs['0020000D'].Value[0];
            }
            if (modus === 'study'){
                _.forEach(object.series, (m, k) => {
                    m.selected = object.selected;
                    _.forEach(m.instances, (j, i) => {
                        j.selected = object.selected;
                    });
                });
            }
            if (modus === 'series'){
                _.forEach(object.instances, function(j, i){
                    j.selected = object.selected;
                });
                this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'] = this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'] || [];
                let SeriesInstanceUIDInArray = false;
                if (this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence']){
                    _.forEach(this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'], function(s, l){
                        console.log('s', s);
                        console.log('l', l);
                        if (s.SeriesInstanceUID === object.attrs['0020000E'].Value[0]){
                            SeriesInstanceUIDInArray = true;
                        }
                    });
                }else{
                    SeriesInstanceUIDInArray = false;
                }
                if (!SeriesInstanceUIDInArray){
                    this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'].push({
                        'SeriesInstanceUID': object.attrs['0020000E'].Value[0]
                    });
                }
            }
            if (modus === 'instance'){

                this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'] = this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'] || [];
                let SeriesInstanceUIDInArray = false;
                if (this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence']){

                    _.forEach(this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'], function(s, l){
                        if (s.SeriesInstanceUID === object.attrs['0020000E'].Value[0]){
                            SeriesInstanceUIDInArray = true;
                        }
                    });
                }else{
                    SeriesInstanceUIDInArray = false;
                }
                if (!SeriesInstanceUIDInArray){
                    this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'].push({
                        'SeriesInstanceUID': object.attrs['0020000E'].Value[0]
                    });
                }
                let $this = this;
                _.forEach($this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'], function(m, i){
                    if (m.SeriesInstanceUID === object.attrs['0020000E'].Value[0]){

                        $this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'][i]['ReferencedSOPSequence'] = $this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'][i]['ReferencedSOPSequence'] || [];

                        let sopClassInstanceUIDInArray = false;
                        _.forEach($this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'][i]['ReferencedSOPSequence'], function(m2, i2){
                            if (m2.ReferencedSOPClassUID && m2.ReferencedSOPClassUID === object.attrs['00080016'].Value[0] && m2.ReferencedSOPInstanceUID && m2.ReferencedSOPInstanceUID === object.attrs['00080018'].Value[0]){
                                sopClassInstanceUIDInArray = true;
                            }
                        });
                        if (!sopClassInstanceUIDInArray){
                            $this.selected['otherObjects'][object.attrs['0020000D'].Value[0]]['ReferencedSeriesSequence'][i]['ReferencedSOPSequence'].push(                                                                                                                    {
                                'ReferencedSOPClassUID': object.attrs['00080016'].Value[0],
                                'ReferencedSOPInstanceUID': object.attrs['00080018'].Value[0]
                            });
                        }
                    }
                });
            }
        }else{
            if (modus === 'patient'){
                console.log('modus in selectObject patient', this.selected['otherObjects']);
                // if(this.clipboard.action === 'merge'){
/*                this.selected['otherObjects']["patients"] = this.selected['patients'] || [];
                this.selected['patients'].push({
                    "PatientID": object.attrs["00100020"].Value[0],
                    "IssuerOfPatientID": ((object.attrs["00100020"]) ? object.attrs["00100020"].Value[0]:''),
                    "IssuerOfPatientIDQualifiers": {
                        "UniversalEntityID": ((object.attrs["00100024"] && object.attrs["00100024"].Value[0]['00400032'] && object.attrs["00100024"].Value[0]['00400032'].Value[0]) ? object.attrs["00100024"].Value[0]['00400032'].Value[0] : ''),
                        "UniversalEntityIDType": ((object.attrs["00100024"] && object.attrs["00100024"].Value[0]['00400033'] && object.attrs["00100024"].Value[0]['00400033'].Value[0]) ? object.attrs["00100024"].Value[0]['00400033'].Value[0] : '')
                    }
                });*/
                let $this = this;
                _.forEach(this.selected['patients'], (m, i) => {
                    console.log('m', m);
                    // console.log("ifcheck,mpatientid",m.PatientID);
                    // console.log("ifcheck,objectpid",object.attrs["00100020"].Value[0]);
                    if (m && m.PatientID === object.attrs['00100020'].Value[0]){
                        console.log('in if', $this.selected['patients'][i]);
                       this.selected['patients'].splice(i, 1);
                        console.log('in if', $this.selected['otherObjects']);
                    }
                    console.log('i', i);
                });
                delete this.selected['otherObjects'][object.attrs['00100020'].Value[0]];
                // this.selected['otherObjects'][object.attrs["00100020"].Value[0]] = this.selected['otherObjects'][object.attrs["00100020"].Value[0]] || {};
                // }else{
                // }
            }else{
                console.log('in else', modus);
                if (_.hasIn(this.selected, ['otherObjects', object.attrs['0020000D'].Value[0]])){
                    delete this.selected['otherObjects'][object.attrs['0020000D'].Value[0]];
                }
                if (modus === 'study'){
                    _.forEach(object.series, (m, k) => {
                        m.selected = object.selected;
                        _.forEach(m.instances, (j, i) => {
                            j.selected = object.selected;
                        });
                    });
                }
                if (modus === 'series'){
                    _.forEach(object.instances, function(j, i){
                        j.selected = object.selected;
                    });
                }
/*                this.selected['otherObjects'][object.attrs["0020000D"].Value[0]] = this.selected['otherObjects'][object.attrs["0020000D"].Value[0]] || {};
                this.selected['otherObjects'][object.attrs["0020000D"].Value[0]]["StudyInstanceUID"] = this.selected['otherObjects'][object.attrs["0020000D"].Value[0]]["StudyInstanceUID"] || object.attrs["0020000D"].Value[0];*/
            }
        }
        // this.selected['otherObjects'][modus] = this.selected['otherObjects'][modus] || [];
        // this.selected['otherObjects'][modus].push(object);
        console.log('this.selected[\'otherObjects\']', this.selected['otherObjects']);
    }
    rsURL() {
/*        let url;
        if(this.externalInternalAetMode === "external"){
            url = `../aets/${this.aetmodel.dicomAETitle}/dimse/${this.externalInternalAetModel.dicomAETitle}`;
        }
        if(this.externalInternalAetMode === "internal"){
            url = `../aets/${this.aet}/rs`;
        }*/
        let externalInternalAetModel = this.externalInternalAetModel && this.externalInternalAetModel.dicomAETitle ? this.externalInternalAetModel.dicomAETitle : undefined;
        let aetmodel = this.aetmodel && this.aetmodel.dicomAETitle ? this.aetmodel.dicomAETitle : undefined;
        return this.service.rsURL(this.externalInternalAetMode,this.aet,aetmodel,externalInternalAetModel);
    }
    diffUrl(){
        if(!this.aet1){
            this.aet1 = this.aet;
        }
        if(!this.aet2){
            this.mainservice.showMsg("Secondary AET is empty!");
        }else{
            return `../aets/${this.aet}/dimse/${this.aet1}/diff/${this.aet2}/studies`;
        }
    }
    studyURL(attrs) {
        return this.rsURL() + '/studies/' + attrs['0020000D'].Value[0];
    }
    seriesURL(attrs) {
        return this.studyURL(attrs) + '/series/' + attrs['0020000E'].Value[0];
    }
    instanceURL(attrs) {
        return this.seriesURL(attrs) + '/instances/' + attrs['00080018'].Value[0];
    }
    studyFileName(attrs) {
        return attrs['0020000D'].Value[0];
    }
    seriesFileName(attrs) {
        return this.studyFileName(attrs) + '_' + attrs['0020000E'].Value[0];
    }
    instanceFileName(attrs) {
        return this.seriesFileName(attrs) + '_' + attrs['00080018'].Value[0];
    }
    createPatientFilterParams() {
        let filter = Object.assign({}, this.filter);
        delete filter["onlyDefault"];
        if(!this.filter["onlyDefault"]){
            filter["includefield"] = 'all';
        }
        console.log('filter', filter);
        return filter;
    }
    createStudyFilterParams() {
        let filter = Object.assign({}, this.filter);
        delete filter["onlyDefault"];
        delete filter['ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate'];
        delete filter['ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime'];
        if(!this.filter["onlyDefault"]){
            filter["includefield"] = 'all';
        }
        // this.appendFilter(filter, 'StudyDate', this.studyDate, /-/g);
        // this.appendFilter(filter, 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate', this.ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate, /-/g);
        // this.appendFilter(filter, 'StudyTime', this.studyTime, /:/g);
        // this.appendFilter(filter, 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime', this.ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime, /-/g);
        return filter;
    }
    createMwlFilterParams() {
        let filter = Object.assign({}, this.filter);
        delete filter["onlyDefault"];
        delete filter.StudyDate;
        delete filter.StudyTime;
        if(!this.filter["onlyDefault"]){
            filter["includefield"] = 'all';
        }
/*        this.appendFilter(filter, 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate', this.studyDate, /-/g);
        this.appendFilter(filter, 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate', this.ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate, /-/g);
        this.appendFilter(filter, 'StudyTime', this.studyTime, /:/g);
        this.appendFilter(filter, 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime', this.ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime, /-/g);*/
        return filter;
    }

    appendFilter(filter, key, range, regex) {
        let value = range.from.replace(regex, '');
        if (range.to !== range.from)
            value += '-' + range.to.replace(regex, '');
        if (value.length)
            filter[key] = value;
    }

    createQueryParams(offset, limit, filter) {
        let params = {
            offset: offset,
            limit: limit
        };
        if(!this.filter["onlyDefault"]){
            params["includefield"] = 'all';
        }

        for (let key in filter){
            if ((filter[key] || filter[key] === false) && key != "onlyDefault"){
                params[key] = filter[key];
            }
        }
        // for(let key = 0;key < filter.length ; key++){
        //     console.log("params=",params,"key=",key, "paramskey",params[key]);
        //     if (filter[key] || filter===false){
        //         params[key] = filter[key];
        //     }
        // }
        return params;
    }
    showClipboardForAMoment(){
        this.showClipboardContent = true;
        let $this = this;
        setTimeout(function() {
            $this.showClipboardContent = false;
        }, 1500);
    };
    uploadDicom(){
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(UploadDicomComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.aes = this.aes;
        this.dialogRef.componentInstance.selectedAe = this.aetmodel.dicomAETitle;
        this.dialogRef.afterClosed().subscribe((result) => {
            console.log('result', result);
            if (result){
            }
        });
    };
    ctrlX(){
        if (this.isRole('admin')){
            console.log('ctrl x');
            if (_.size(this.selected['otherObjects']) > 0){
                if (this.selected.hasPatient === true){
                    this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'Move process on patient level is not supported',
                        'status': 'info'
                    });
                }else{
                    // this.service.MergeRecursive(this.clipboard, this.selected);
                    _.merge(this.clipboard, this.selected);
                    if (this.clipboard.action && this.clipboard.action === 'copy'){
                        let $this = this;
                        this.confirm({
                            content: 'Are you sure you want to change the action from copy to move?'
                        }).subscribe(result => {
                            if (result){
                                $this.clipboard['action'] = 'move';
                            }
                            $this.showClipboardForAMoment();
                        });
                    }else{
                        this.clipboard['action'] = 'move';
                        this.showClipboardForAMoment();
                    }
                    this.service.clearSelection(this.patients);
                    this.selected = {};
                    // this.showClipboardContent = true;

                }
            }
        }
    };
    merge(){
        if (this.isRole('admin')){
            if (_.size(this.selected['patients']) > 0){
                console.log('merge', this.clipboard);
                this.clipboard = this.clipboard || {};
                console.log('this.selected[\'otherObjects\']', this.selected['otherObjects']);
                // console.log("test",angular.merge({},this.clipboard.selected, this.selected['otherObjects']));
                // this.clipboard.selected = angular.merge({},this.clipboard.selected, this.selected['otherObjects']);
                this.service.MergeRecursive(this.clipboard, this.selected);
                if (this.clipboard.action && (this.clipboard.action === 'move' || this.clipboard.action === 'copy')){
                    let $this = this;
                    this.confirm({
                        content: 'Are you sure you want to change the action from ' + this.clipboard.action + ' to merge?'
                    }).subscribe(result => {
                        if (result){
                            $this.clipboard['action'] = 'merge';
                        }
                        $this.showClipboardForAMoment();
                    });
                }else{
                    this.clipboard['action'] = 'merge';
                    this.showClipboardForAMoment();
                }
                console.log('this.clipboard', this.clipboard);
                this.service.clearSelection(this.patients);

                this.selected = {};
                // this.showClipboardContent = true;
            }
/*            else{
                this.mainservice.setMessage({
                    "title": "Info",
                    "text": "No element selected!",
                    "status":'info'
                });
            }*/
        }
    };
    ctrlC(){
        if (this.isRole('admin')){
            if (_.size(this.selected['otherObjects']) > 0){
                if (this.selected.hasPatient === true){
                    this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'Copy process on patient level is not supported',
                        'status': 'info'
                    });
                }else{
                    console.log('ctrl c', this.clipboard);

                    this.clipboard = this.clipboard || {};
                    console.log('this.selected[\'otherObjects\']', this.selected['otherObjects']);
                    // console.log("test",angular.merge({},this.clipboard.selected, this.selected['otherObjects']));
                    // this.clipboard.selected = angular.merge({},this.clipboard.selected, this.selected['otherObjects']);
                    // delete this.selected['otherObjects'].patients;
                    console.log('this.selected[\'otherObjects\']', this.selected['otherObjects']);
                    // this.service.MergeRecursive(this.clipboard,this.selected);
                    _.merge(this.clipboard, this.selected);
                    console.log('this.clipboard', this.clipboard);
                    if (this.clipboard.action && this.clipboard.action === 'move'){
                        let $this = this;
                        this.confirm({
                            content: 'Are you sure you want to change the action from move to copy?'
                        }).subscribe(result => {
                            if (result){
                                $this.clipboard['action'] = 'copy';
                            }
                            $this.showClipboardForAMoment();
                        });
                    }else{
                        this.clipboard['action'] = 'copy';
                        this.showClipboardForAMoment();
                    }
                    console.log('this.clipboard', this.clipboard);
                    this.service.clearSelection(this.patients);
                    this.selected = {};
                    // this.showClipboardContent = true;
                }
            }
/*            else{
                this.mainservice.setMessage({
                    "title": "Info",
                    "text": "No element selected!",
                    "status":'info'
                });
            }*/
        }
    };
    ctrlV() {
        console.log('ctrlv clipboard', this.clipboard);
        console.log('ctrlv size', _.size(this.clipboard));
        if (_.size(this.clipboard) > 0 && (_.size(this.selected) > 0 || (_.hasIn(this.selected, 'hasPatient') && _.size(this.selected) > 1))) {
            this.cfpLoadingBar.start();
            let headers: HttpHeaders = new HttpHeaders({'Content-Type': 'application/json'});
            if (!this.service.isTargetInClipboard(this.selected, this.clipboard) || this.target.modus === "mwl"){

                let $this = this;
                // if (((_.keysIn(this.selected.otherObjects)[0]) in this.clipboard.otherObjects)) {
                //     this.mainservice.setMessage({
                //         "title": "Warning",
                //         "text": "Target object cannot be in the clipboard!",
                //         "status":'warning'
                //     });
                // } else {

                    this.config.viewContainerRef = this.viewContainerRef;
                    this.dialogRef = this.dialog.open(CopyMoveObjectsComponent, {
                        height: 'auto',
                        width: '90%'
                    });
                    let action = this.clipboard['action'].toUpperCase();
                    let title = action + ' PROCESS';
                    if(this.target.modus === "mwl"){
                        title = "LINK TO MWL";
                        _.forEach(this.rjnotes,(m,i)=>{
                            console.log("m",m);
                            if(m.type === "INCORRECT_MODALITY_WORKLIST_ENTRY"){
                                this.reject = m.codeValue+"^"+m.codingSchemeDesignator;
                            }
                        });
                        $this.clipboard.action = 'move';
                    }
                    if(this.externalInternalAetMode === 'external' && ($this.selected.patients.length > 1 || $this.clipboard.patients.length > 1)){
                        $this.mainservice.setMessage({
                            'title': 'Warning',
                            'text': 'External merge of multiple patients is not allowed, just the first selected patient will be taken for merge!',
                            'status': 'warning'
                        });
                    }
                    this.dialogRef.componentInstance.clipboard = this.clipboard;
                    this.dialogRef.componentInstance.rjnotes = this.rjnotes;
                    this.dialogRef.componentInstance.selected = this.selected['otherObjects'];
                    this.dialogRef.componentInstance.showClipboardHeaders = this.showClipboardHeaders;
                    this.dialogRef.componentInstance.target = this.target;
                    this.dialogRef.componentInstance.reject = this.reject;
                    this.dialogRef.componentInstance.saveLabel = action;
                    this.dialogRef.componentInstance.title = title;
                    this.cfpLoadingBar.complete();
                    this.dialogRef.afterClosed().subscribe(result => {
                        $this.cfpLoadingBar.start();
                        if (result) {
                            $this.reject = result;
                            console.log("reject",$this.reject);
                            if ($this.clipboard.action === 'merge') {
/*                                console.log('object', object);
                                console.log('in merge clipboard', $this.clipboard);
                                console.log('in merge selected', $this.selected['otherObjects']);
                                console.log('in merge selected', $this.selected.patients[0].PatientID);
                                console.log('getpatientid', $this.service.getPatientId($this.selected.patients));*/
                                let object;
                                let url;
                                if(this.externalInternalAetMode === 'external'){
                                    url = `../hl7apps/${$this.service.getHl7ApplicationNameFormAETtitle($this.aet, $this.allAes)}/hl7/${$this.externalInternalAetModel.hl7ApplicationName}/patients/${$this.service.getPatientId($this.clipboard.patients)}/merge?queue=true`;
                                    object = $this.selected.patients[0].attrs;

                                }else{
                                    delete $this.clipboard.patients[0].attrs;
                                    _.forEach($this.clipboard.patients,(pat,ind)=>{
                                        if(_.hasIn(pat,"attrs")){
                                            delete $this.clipboard.patients[ind].attrs;
                                        }
                                    });
                                    object =  $this.clipboard.patients;
                                    url = '../aets/' + $this.aet + '/rs/patients/' + $this.service.getPatientId($this.selected.patients) + '/merge';
                                }
                                console.log("url",url);
                                $this.$http.post(
                                    url,
                                    object,
                                    headers
                                )
                                    .subscribe((response) => {
                                        console.log('response in first', response.status);
                                        if (response.status === 204){
                                            $this.mainservice.setMessage({
                                                'title': 'Info',
                                                'text': 'Patients merged successfully!',
                                                'status': 'info'
                                            });
                                        }else{
                                            $this.mainservice.setMessage({
                                                'title': 'Info',
                                                'text': response.statusText,
                                                'status': 'info'
                                            });
                                        }
                                        $this.selected = {};
                                        $this.clipboard = {};
                                        $this.fireRightQuery();
                                        $this.cfpLoadingBar.complete();
                                    }, (response) => {
                                        $this.cfpLoadingBar.complete();
                                        $this.httpErrorHandler.handleError(response);
                                    });
                            }
                            if ($this.clipboard.action === 'copy') {
                                console.log('in ctrlv copy patient', $this.target);
                                if ($this.target.modus === 'patient') {
                                    let study = {
                                        '00100020': $this.target.attrs['00100020'],
                                        '00200010': {'vr': 'SH', 'Value': ['']},
                                        '0020000D': {'vr': 'UI', 'Value': ['']},
                                        '00080050': {'vr': 'SH', 'Value': ['']}
                                    };
                                    $this.$http.post(
                                        '../aets/' + $this.aet + '/rs/studies',
                                        study,
                                        headers
                                    ).map(res => {
                                        console.log('in map1', res);
                                        let resjson;
                                        try {
                                          /*  let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
                                            if(pattern.exec(res.url)){
                                                WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                                            }*/
                                            // resjson = res.json();
                                            resjson = res;
                                        } catch (e) {
                                            resjson = {};
                                        }
                                        return resjson;
                                    })
                                        .subscribe((response) => {
                                                console.log('in subscribe2', response);
                                                _.forEach($this.clipboard.otherObjects, function (m, i) {
                                                    console.log('m', m);
                                                    console.log('i', i);
                                                    $this.$http.post(
                                                        '../aets/' + $this.aet + '/rs/studies/' + response['0020000D'].Value[0] + '/copy',
                                                        m,
                                                        headers
                                                    )
                                                        .map(res => {
                                                            console.log('in map1', res);
                                                            let resjson;
                                                            try {
                                                                /*let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
                                                                if(pattern.exec(res.url)){
                                                                    WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                                                                }*/
                                                                // resjson = res.json();
                                                                resjson = res;
                                                            } catch (e) {
                                                                resjson = {};
                                                            }
                                                            return resjson;
                                                        })
                                                        .subscribe((response) => {
                                                            console.log('in then function', response);
                                                            $this.clipboard = {};
                                                            $this.selected = {};
                                                             $this.mainservice.setMessage({
                                                             'title': 'Info',
                                                             'text': 'Object with the Study Instance UID ' + m.StudyInstanceUID + ' copied successfully!',
                                                             'status': 'info'
                                                             });
                                                            $this.cfpLoadingBar.complete();
                                                            // $this.callBackFree = true;
                                                        }, (response) => {
                                                            console.log('resin err', response);
                                                            $this.clipboard = {};
                                                            $this.cfpLoadingBar.complete();
                                                            $this.httpErrorHandler.handleError(response);
                                                            // $this.callBackFree = true;
                                                        });
                                                });
                                                $this.fireRightQuery();
                                            },
                                            (response) => {
                                                $this.cfpLoadingBar.complete();
                                                $this.httpErrorHandler.handleError(response);
                                                console.log('response', response);
                                            }
                                        );
                                } else {
                                    _.forEach($this.clipboard.otherObjects, function (m, i) {
                                        console.log('m', m);
                                        $this.$http.post(
                                            '../aets/' + $this.aet + '/rs/studies/' + $this.target.attrs['0020000D'].Value[0] + '/copy',
                                            m,
                                            headers
                                        )
                                            .map(res => {
                                                console.log('in map1', res);
                                                let resjson;
                                                try {
                                                    /*let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
                                                    if(pattern.exec(res.url)){
                                                        WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                                                    }*/
                                                    // resjson = res.json();
                                                    resjson = res;
                                                } catch (e) {
                                                    resjson = {};
                                                }
                                                return resjson;
                                            })
                                            .subscribe((response) => {
                                                console.log('in then function');
                                                $this.cfpLoadingBar.complete();
                                                $this.mainservice.setMessage({
                                                    'title': 'Info',
                                                    'text': 'Object with the Study Instance UID ' + $this.target.attrs['0020000D'].Value[0] + ' copied successfully!',
                                                    'status': 'info'
                                                });
                                                $this.clipboard = {};
                                                $this.selected = {};
                                                $this.fireRightQuery();
                                                // $this.callBackFree = true;
                                            }, (response) => {
                                                $this.cfpLoadingBar.complete();
                                                $this.httpErrorHandler.handleError(response);
                                                // $this.callBackFree = true;
                                            });
                                    });
                                }
                            }
                            if ($this.clipboard.action === 'move') {
                                if ($this.target.modus === 'patient') {
                                    let study = {
                                        '00100020': $this.target.attrs['00100020'],
                                        '00200010': {'vr': 'SH', 'Value': ['']},
                                        '0020000D': {'vr': 'UI', 'Value': ['']},
                                        '00080050': {'vr': 'SH', 'Value': ['']}
                                    };
                                    $this.$http.post(
                                        '../aets/' + $this.aet + '/rs/studies',
                                        study,
                                        headers
                                    )
                                        .map(res => {
                                            console.log('in map1', res);
                                            let resjson;
                                            try {
                                               /* let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
                                                if(pattern.exec(res.url)){
                                                    WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                                                }*/
                                                // resjson = res.json();
                                                resjson = res;
                                            } catch (e) {
                                                resjson = {};
                                            }
                                            return resjson;
                                        })
                                        .subscribe((response) => {
                                                _.forEach($this.clipboard.otherObjects, function (m, i) {
                                                    console.log('m', m);
                                                    $this.$http.post(
                                                        '../aets/' + $this.aet + '/rs/studies/' + response['0020000D'].Value[0] + '/move/' + $this.reject,
                                                        m,
                                                        headers
                                                    )
                                                        .map(res => {
                                                            let resjson;
                                                            try {
                                                                /*let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
                                                                if(pattern.exec(res.url)){
                                                                    WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                                                                }*/
                                                                // resjson = res.json();
                                                                resjson = res;
                                                            } catch (e) {
                                                                resjson = {};
                                                            }
                                                            return resjson;
                                                        })
                                                        .subscribe((response) => {
                                                            console.log('in then function');
                                                            $this.clipboard = {};
                                                            $this.selected = {};
                                                            $this.cfpLoadingBar.complete();
                                                            $this.mainservice.setMessage({
                                                                'title': 'Info',
                                                                'text': 'Object with the Study Instance UID ' + m.StudyInstanceUID + ' moved successfully!',
                                                                'status': 'info'
                                                            });
                                                            $this.fireRightQuery();
                                                        }, (response) => {
                                                            $this.cfpLoadingBar.complete();
                                                            $this.httpErrorHandler.handleError(response);
                                                        });
                                                });
                                            },
                                            (response) => {
                                                $this.cfpLoadingBar.complete();
                                                $this.httpErrorHandler.handleError(response);
                                                console.log('response', response);
                                            }
                                        );
                                } else {
                                    let url;
                                    let index = 1;
                                    if ($this.target.modus === 'mwl') {
                                        url = `../aets/${$this.aet}/rs/mwlitems/${$this.target.attrs['0020000D'].Value[0]}/${_.get($this.target.attrs,'[00400100].Value[0][00400009].Value[0]')}/move/${$this.reject}`;
                                    }else{
                                        url = `../aets/${$this.aet}/rs/studies/${$this.target.attrs['0020000D'].Value[0]}/move/${$this.reject}`;
                                    }
                                    _.forEach($this.clipboard.otherObjects, function (m, i) {
                                        console.log('m', m);
                                        console.log("$this.clipboard.otherObjects.length",Object.keys($this.clipboard.otherObjects).length);
                                        console.log("i",index);
                                        $this.$http.post(
                                            url,
                                            m,
                                            headers
                                        )
                                            .map(res => {
                                                console.log('in map1', res);
                                                let resjson;
                                                try {
                                                    /*let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
                                                    if (pattern.exec(res.url)) {
                                                        WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                                                    }*/
                                                    // resjson = res.json();
                                                    resjson = res;
                                                } catch (e) {
                                                    resjson = {};
                                                }
                                                return resjson;
                                            })
                                            .subscribe((response) => {
                                                console.log('in then function');
                                                $this.cfpLoadingBar.complete();
                                                $this.mainservice.setMessage({
                                                    'title': 'Info',
                                                    'text': 'Object with the Study Instance UID ' + $this.target.attrs['0020000D'].Value[0] + ' moved successfully!',
                                                    'status': 'info'
                                                });
                                                if(index == Object.keys($this.clipboard.otherObjects).length){
                                                    $this.clipboard = {};
                                                    $this.selected = {};
                                                }
                                                $this.fireRightQuery();
                                            }, (response) => {
                                                $this.cfpLoadingBar.complete();
                                                $this.httpErrorHandler.handleError(response);
                                                if(index == Object.keys($this.clipboard.otherObjects).length){
                                                    $this.clipboard = {};
                                                    $this.selected = {};
                                                }
                                            });
                                        index++;
                                    });
                                }
                            }

                        }else{
                            $this.service.clearSelection(this.patients);
                            $this.selected = {};
                            $this.clipboard = {};
                        }
                        $this.cfpLoadingBar.complete();
                        this.dialogRef = null;
                    });
            }else {
                this.mainservice.setMessage({
                    'title': 'Warning',
                    'text': 'Target object can not be in the clipboard',
                    'status': 'warning'
                });
            }
        }else {
            if ( (_.size(this.clipboard) < 1 &&
                    (
                        _.size(this.selected) < 1 ||
                        (_.hasIn(this.selected, 'hasPatient') && _.size(this.selected) < 2) ||
                        (
                            (_.hasIn(this.selected, 'otherObjects') && _.size(this.selected['otherObjects']) == 0) &&
                            (_.hasIn(this.selected, 'patients') && _.size(this.selected['patients']) == 0)
                        )
                    )
                )
              ) {
                console.warn('ctrl v with empty clipboard and empty selected');
            }else{
                console.log('clipboard=', this.clipboard);
                console.log('selected=', this.selected);
                if (_.size(this.clipboard) < 1){
                    this.mainservice.setMessage({
                        'title': 'Warning',
                        'text': 'Clipboard is empty, first add something in the clipboard with the copy,cut or merge button',
                        'status': 'warning'
                    });
                }
                if ((_.size(this.selected) < 1 || (_.hasIn(this.selected, 'hasPatient') && _.size(this.selected) < 2))){
                    this.mainservice.setMessage({
                        'title': 'Warning',
                        'text': 'No target object was selected!',
                        'status': 'warning'
                    });
                }
            }
        }
    };
    conditionWarning($event, condition, msg){
        let id = $event.currentTarget.id;
        let $this = this;
        if (condition){
            this.disabled[id] = true;
            this.mainservice.setMessage({
                'title': 'Warning',
                'text': msg,
                'status': 'warning'
            });
            setTimeout(function() {
                $this.disabled[id] = false;
            }, 100);
        }
    };
    renderURL(inst) {
        if (inst.video)
            return this.wadoURL(inst.wadoQueryParams, { contentType: 'video/*' });
        if (inst.numberOfFrames)
            return this.wadoURL(inst.wadoQueryParams, { contentType: 'image/jpeg', frameNumber: inst.view });
        if (inst.gspsQueryParams.length)
            return this.wadoURL(inst.gspsQueryParams[inst.view - 1]);
        return this.wadoURL(inst.wadoQueryParams);
    }
    getKeys(obj){
        if (_.isArray(obj)){
            return obj;
        }else{
            // console.log("objectkeys=",Object.keys(obj));
            return Object.keys(obj);
        }
    }

    addEffect(direction){
        let element = $('.div-table');
        element.removeClass('fadeInRight').removeClass('fadeInLeft');
        setTimeout(function(){
            if (direction === 'left'){
                element.addClass('animated').addClass('fadeOutRight');
            }
            if (direction === 'right'){
                element.addClass('animated').addClass('fadeOutLeft');
            }
        }, 1);
        setTimeout(function(){
            element.removeClass('fadeOutRight').removeClass('fadeOutLeft');
            if (direction === 'left'){
                element.addClass('fadeInLeft').removeClass('animated');
            }
            if (direction === 'right'){
                element.addClass('fadeInRight').removeClass('animated');
            }
        }, 301);
    };
    wadoURL(...args: any[]): any {
        let i, url = '../aets/' + this.aet + '/wado?requestType=WADO';
        for (i = 0; i < arguments.length; i++) {
            _.forEach(arguments[i], (value, key) => {
                url += '&' + key + '=' + value;
            });
        }
        return url;
    }
    extractAttrs(attrs, tags, extracted) {
        for (let tag in attrs){
            // if (this.binarySearch(tags, parseInt(tag, 16)) >= 0) {
                if (_.indexOf(tags, tag) > -1){
                    extracted[tag] = attrs[tag];
                }
            // }
        }
        // attrs.forEach((value, tag) => {
        //     if (this.binarySearch(tags, parseInt(tag, 16)) >= 0) {
        //         extracted[tag] = value;
        //     }
        // });
    }
    equalsIgnoreSpecificCharacterSet(attrs, other) {
        return Object.keys(attrs).filter(tag => tag != '00080005')
                .every(tag => _.isEqual(attrs[tag],other[tag]))
            && Object.keys(other).filter(tag => tag != '00080005')
                .every(tag => attrs[tag]);
    }
    binarySearch(ar, el) {
        let m = 0;
        let n = ar.length - 1;
        while (m <= n) {
            let k = (n + m) >> 1;
            let cmp = el - ar[k];
            if (cmp > 0) {
                m = k + 1;
            } else if (cmp < 0) {
                n = k - 1;
            } else {
                return k;
            }
        }
        return -m - 1;
    }
    createGSPSQueryParams(attrs) {
        let sopClass = this.valueOf(attrs['00080016']),
            refSeries = this.valuesOf(attrs['00081115']),
            queryParams = [];
        if (sopClass === '1.2.840.10008.5.1.4.1.1.11.1' && refSeries) {
            refSeries.forEach((seriesRef) => {
                this.valuesOf(seriesRef['00081140']).forEach((objRef) => {
                    queryParams.push({
                        studyUID: attrs['0020000D'].Value[0],
                        seriesUID: seriesRef['0020000E'].Value[0],
                        objectUID: objRef['00081155'].Value[0],
                        contentType: 'image/jpeg',
                        frameNumber: this.valueOf(objRef['00081160']) || 1,
                        presentationSeriesUID: attrs['0020000E'].Value[0],
                        presentationUID: attrs['00080018'].Value[0]
                    });
                });
            });
        }
        return queryParams;
    }
    isVideo(attrs) {
        let sopClass = this.valueOf(attrs['00080016']);
        return [
            '1.2.840.10008.5.1.4.1.1.77.1.1.1',
            '1.2.840.10008.5.1.4.1.1.77.1.2.1',
            '1.2.840.10008.5.1.4.1.1.77.1.4.1']
            .indexOf(sopClass) != -1 ? 1 : 0;
    }
    isImage(attrs){
        let sopClass = this.valueOf(attrs['00080016']);
        let bitsAllocated = this.valueOf(attrs['00280100']);
        return ((bitsAllocated && bitsAllocated != "") && (sopClass != '1.2.840.10008.5.1.4.1.1.481.2'));
    }
    valuesOf(attr) {
        return attr && attr.Value;
    }
    valueOf(attr) {
        return attr && attr.Value && attr.Value[0];
    }
    createArray(n) {
        let a = [];
        for (let i = 1; i <= n; i++)
            a.push(i);
        return a;
    }
    aesdropdown: SelectItem[] = [];
    initAETs(retries, mode?) {
        if (!this.aes){
            let $this = this;
            if(!mode){
                mode = "internal";
            }
           this.$http.get('../aets')
                .map(res => j4care.redirectOnAuthResponse(res))
                .map(aet=> this.permissionService.filterAetDependingOnUiConfig(aet,mode))
                .subscribe((res)=> {
                        $this.aes = j4care.extendAetObjectWithAlias($this.service.getAes($this.mainservice.user, res));

                        try{
                            $this.aet = $this.aes[0].dicomAETitle.toString();
                            if (!$this.aetmodel){
                                $this.aetmodel = $this.aes[0];
                            }
                        }catch(e){
                            console.warn(e);
                        }
                        // $this.mainservice.setGlobal({aet:$this.aet,aetmodel:$this.aetmodel,aes:$this.aes, aesdropdown:$this.aesdropdown});
                    },
                    (res)=> {
                        if (retries)
                            $this.initAETs(retries - 1, mode);
                });
        }
    }
    getAllAes(retries) {
        let $this = this;
        this.$http.get('../aes')
            .map(res => j4care.redirectOnAuthResponse(res))
            .map(aet=> this.permissionService.filterAetDependingOnUiConfig(aet,"external"))
            .subscribe(
                (res)=> {
                    $this.allAes = j4care.extendAetObjectWithAlias(res.map((res)=>{
                        res['title'] = res['dicomAETitle'];
                        res['description'] = res['dicomDescription'];
                        return res;
                    }));
                    $this.externalInternalAetModel = $this.allAes[0];
                },
                (res)=> {
                    if (retries)
                        $this.getAllAes(retries - 1);
            });
    }
    testSetObject() {
        this.aetmodel = {
            'title': 'DCM4CHEE_TRASH',
            'description': 'Show rejected instances only',
            'dcmHideNotRejectedInstances': true,
            'dcmAcceptedUserRole': [
                'admin'
            ],
            'label': 'DCM4CHEE_TRASH'
        };
    }
    onChange(newValue, model) {
        // if(model.includes(".")){
            let arr = model.split('.');
            // let obj = this;
            // for(let o of arr){
            // }
            // console.log("filter.PatientSex=",this["filter"]["PatientSex"]);
        console.log('onchange, newValue', newValue);
        console.log('arr', arr);
        console.log('model', model);
            _.set(this, arr, newValue);
            // console.log("filter.PatientSex2=",this["filter"]["PatientSex"]);
        // }else{

            // this[model] = newValue;
        // }
        if(model === 'aetmodel' || model === 'externalInternalAetModel'){
         //TODO Show ForceQueryByStudyUID only if bouth selected aets are parte of an device the primary device type ARCHIVE
        }
        if (model === 'aetmodel'){
            this.aet = newValue.dicomAETitle;
            // this.aetmodel = newValue;
            this.setTrash();
        }
        console.log('this.aetmodel', this.aetmodel);
    }
    initAttributeFilter(entity, retries) {
        let $this = this;
       this.$http.get('../attribute-filter/' + entity)
            .map(res => {let resjson; try{
                /*let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
                if(pattern.exec(res.url)){
                    WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                }*/
                resjson = res; }catch (e){resjson = {}; } return resjson; })
            .subscribe(
                function (res) {
                    if (entity === 'Patient' && res.dcmTag){
                        let privateAttr = [parseInt('77770010', 16), parseInt('77771010', 16), parseInt('77771011', 16)];
                        res.dcmTag.push(...privateAttr);
                    }
                    $this.attributeFilters[entity] = res;
                    console.log('this.attributeFilters', $this.attributeFilters);
                    // $this.mainservice.setGlobal({attributeFilters:$this.attributeFilters});
                },
                function (res) {
                    if (retries)
                        $this.initAttributeFilter(entity, retries - 1);
            });
    };
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
                    let url = '../aets/' + this.aet + '/rs/studies/';
                    switch (mode) {
                        case 'study':
                            url += object.attrs['0020000D'].Value[0] + '/stgver';
                            break;
                        case 'series':
                            url += object.attrs['0020000D'].Value[0] + '/series/' + object.attrs['0020000E'].Value[0] + '/stgver';
                            break;
                        default:
                        case 'instance':
                            url += object.attrs['0020000D'].Value[0] + '/series/' + object.attrs['0020000E'].Value[0] + '/instances/' + object.attrs['00080018'].Value[0] + '/stgver';
                            break;
                    }
                    if (ok && ok.schema_model) {
                        url += j4care.getUrlParams(ok.schema_model);
                    }
                    let $this = this;

                    let headers = new HttpHeaders({'Content-Type': 'application/dicom+json'});
                    this.$http.post(
                        url,
                        {},
                        headers
                    )
                        .map(res => {
                            let resjson;
                            try {
                               /* let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
                                if (pattern.exec(res.url)) {
                                    WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                                }*/
                                resjson = res;
                            } catch (e) {
                                resjson = {};
                            }
                            return resjson;
                        })
                        .subscribe(
                            (response) => {
                                // console.log("response",response);
                                let failed = (response[0]['00081198'] && response[0]['00081198'].Value) ? response[0]['00081198'].Value.length : 0;
                                let success = (response[0]['00081199'] && response[0]['00081199'].Value) ? response[0]['00081199'].Value.length : 0;
                                let msgStatus = 'Info';
                                if (failed > 0 && success > 0) {
                                    msgStatus = 'Warning';
                                    this.mainservice.setMessage({
                                        'title': msgStatus,
                                        'text': failed + ' of ' + (success + failed) + ' failed!',
                                        'status': msgStatus.toLowerCase()
                                    });
                                    console.log(failed + ' of ' + (success + failed) + ' failed!');
                                }
                                if (failed > 0 && success === 0) {
                                    msgStatus = 'Error';
                                    this.mainservice.setMessage({
                                        'title': msgStatus,
                                        'text': 'all (' + failed + ') failed!',
                                        'status': msgStatus.toLowerCase()
                                    });
                                    console.log('all ' + failed + 'failed!');
                                }
                                if (failed === 0) {
                                    console.log(success + ' verified successfully 0 failed!');
                                    this.mainservice.setMessage({
                                        'title': msgStatus,
                                        'text': success + ' verified successfully\n 0 failed!',
                                        'status': msgStatus.toLowerCase()
                                    });
                                }
                                this.cfpLoadingBar.complete();
                            },
                            (response) => {
                                $this.httpErrorHandler.handleError(response);
                                this.cfpLoadingBar.complete();
                            }
                        );
                }
            });
        },(err)=>{
            this.httpErrorHandler.handleError(err);
        });
    };
    openViewer(model, mode){
        try {
            let token;
            let target;
            let url;
            let configuredUrlString = mode === "study" ? this.aetmodel.dcmInvokeImageDisplayStudyURL : this.aetmodel.dcmInvokeImageDisplayPatientURL;
            this._keycloakService.getToken().subscribe((response) => {
                if (!this.mainservice.global.notSecure) {
                    token = response.token;
                }
                console.groupCollapsed("OpenViewer");
                console.log("Configure URL:",configuredUrlString);
                console.log("aetmodel     :",this.aetmodel);
                console.log("model:        ",model);
                console.log("mode:         ",mode);
                url = configuredUrlString.replace(/(&)(\w*)=(\{\}|_self|_blank)/g, (match, p1, p2, p3, offset, string) => {
                    switch (p2) {
                        case "studyUID":
                            return `${p1}${p2}=${model['0020000D'].Value[0]}`;
                        case "patientID":
                            return `${p1}${p2}=${this.service.getPatientId(model)}`;
                        case "access_token":
                            return `${p1}${p2}=${token}`;
                        case "target":
                            target = `${p3}`;
                            return "";
                    }
                }).trim();
                console.log("Prepared URL: ", url);
                console.groupEnd();
                if (target) {
                    this.service.getWindow().open(url, target);
                } else {
                    this.service.getWindow().open(url);
                }
            });
        }catch(e){
            j4care.log("Something went wrong while opening the Viewer",e);
            this.mainservice.showError("Something went wrong while opening the Viewer open the inspect to see more details");
        }
    };
    showMoreFunction(e, elementLimit){
        let duration = 300;
        let visibleElements = $(e.target).siblings('.hiddenbuttons').length - $(e.target).siblings('.hiddenbuttons.ng-hide').length;
/*        $(".more_menu_content").removeClass("activemenu");
        console.log("moremenucontent",$(e.target).closest(".more_menu_content"));*/
        // $(e.target).closest(".more_menu_content").addClass("activemenu");
        console.log('visibleElements', visibleElements);
        let index = 1;
        let cssClass: string = 'block' + elementLimit;
        while (index * elementLimit < visibleElements){
            index++;
        }
        let height = 26 * index;

        let variationvalue = visibleElements * 26;
        if (visibleElements > elementLimit){
            variationvalue = elementLimit * 26;
        }
        let element = $(e.target).closest('.more_menu_study');

        $.each($(".more_menu_content.instance_level"),(i,m)=>{
            $(m).removeClass("activemenu");
        });
        if (element.hasClass('open')){
            $(e.target).closest('.more_menu_content').css('height', 26);
            if (visibleElements > elementLimit){
                $(e.target).closest('.more_menu_content').removeClass('block').removeClass(cssClass);
            }
            element.animate({
                right: '-=' + variationvalue
            }, duration, function() {
                element.removeClass('open');
                element.removeAttr('style');
            });
        }else{
            setTimeout(()=>{
                $(e.target).closest(".more_menu_content.instance_level").addClass("activemenu");
            },300);
            $(e.target).closest('.more_menu_content').css('height', height);
                console.log("vor settitmout");
            if (visibleElements > elementLimit){
                console.log('$(e.target).parent(.more_menu_content)', $(e.target).closest('.more_menu_content'));
                $(e.target).closest('.more_menu_content').addClass(cssClass).addClass('block');
                // $(e.target).closest(".more_menu_content").css("width",((elementLimit*26)+18));
                // $(e.target).closest(".more_menu_content").css("left",-((elementLimit-1)*26));
            }
            $('.more_menu_study.open').each(function(i, m){
                $(m).removeClass('open');

                if ($(m).hasClass('repeat3block')){
                    $(m).css('right', '-249px');
                }else{
                    $(m).css('right', '-195px');
                }
                $(m).closest('.more_menu_content')
                    .removeClass('block')
                    .removeClass('block3')
                    .removeClass('block5')
                    .removeClass('block7')
                    .css('height', 26);
                $(m).removeAttr('style');
            });
            element.animate({
                right: '+=' + variationvalue
            }, duration, function() {
                element.addClass('open');

            });
        }
    };

    removeClipboardElement(modus, keys){
        this.service.removeClipboardElement(modus, keys, this.clipboard);
    }
    initExporters(retries) {
        let $this = this;
       this.$http.get('../export')
            .subscribe(
                function (res) {
                    $this.exporters = res;
                    if (res && res[0] && res[0].id){
                        $this.exporterID = res[0].id;
                    }
                    // $this.mainservice.setGlobal({exporterID:$this.exporterID});
                },
                function (res) {
                    if (retries)
                        $this.initExporters(retries - 1);
                });
    }
/*    showExporter(){
        if (_.size(this.exporters) > 0){
            return true;
        }else{
            return false;
        }
    }*/
    initRjNotes(retries) {
        let $this = this;
       this.$http.get('../reject')
            .subscribe(
                function (res) {
                    let rjnotes = res;
                    rjnotes.sort(function (a, b) {
                        if (a.codeValue === '113039' && a.codingSchemeDesignator === 'DCM')
                            return -1;
                        if (b.codeValue === '113039' && b.codingSchemeDesignator === 'DCM')
                            return 1;
                        return 0;
                    });
                    $this.rjnotes = rjnotes;
                    $this.reject = rjnotes[0].codeValue + '^' + rjnotes[0].codingSchemeDesignator;

                    // $this.mainservice.setGlobal({rjnotes:rjnotes,reject:$this.reject});
                },
                function (res) {
                    if (retries)
                        $this.initRjNotes(retries - 1);
            });
    }
    showCheckBoxes(){
        this.showCheckboxes = !this.showCheckboxes;
    }

    debugTemplate(obj){
        console.log('obj', obj);
    }

    testToken(){
        this.$http.get('../aes')
            .subscribe((res)=>{
                console.log("testres",res);
            },(err)=>{

            });
        // var keycloak = new Keycloak('./assets/keycloak.json');
        //
        // keycloak.init().success(function(authenticated) {
        //     console.log(authenticated ? 'authenticated' : 'not authenticated');
        // }).error(function() {
        //     console.log('failed to initialize');
        // });
        // keycloak.updateToken(30).success(function() {
        //     console.log("success")
        // }).error(function() {
        //     console.log('Failed to refresh token');
        // });
        // var x = document.cookie;
        // console.log("cookie",x);
        // this.mainservice.getRealmOfLogedinUser()
        //     .subscribe((res)=>{
        //         let token = res.token;
        //         // this.$http.get('../reject')
        //         this.kc.init().then(init => {
        //             console.log("init",init);
        //         this.kc.getToken(token)
        //             .then(token => {
        //                 console.log("token",token);
        //                 let headers = new Headers({
        //                     'Accept': 'application/json',
        //                     'Authorization': 'Bearer ' + token
        //                 });
        //
        // /*                let options = new RequestOptions({ headers });
        //
        //                 this.http.get('/database/products', options)
        //                     .map(res => res.json())
        //                     .subscribe(prods => this.products = prods,
        //                         error => console.log(error));*/
        //             })
        //             .catch(error => console.log(error));
        //         }).catch(error => console.log(error));
        //     });
    }
    queryNationalPationtRegister(patientId){
        if(patientId.xroad){
            delete patientId.xroad;
        }else{
            if(_.hasIn(this.mainservice,"global['PDQs']") && this.mainservice.global['PDQs'].length > 0){
                //PDQ is configured
                console.log("global",this.mainservice.global);
                if(this.mainservice.global['PDQs'].length > 1){
                    this.confirm({
                        content: 'Schedule Storage Verification of matching Studies',
                        doNotSave:true,
                        form_schema:[
                                [
                                    [

                                    {
                                        tag:"label",
                                        text:"Select PDQ Service"
                                    },
                                    {
                                        tag:"select",
                                        options:this.mainservice.global['PDQs'].map(pdq=>{
                                            return new SelectDropdown(pdq.id, (pdq.description || pdq.id))
                                        }),
                                        filterKey:"PDQServiceID",
                                        description:"PDQ ServiceID",
                                        placeholder:"PDQ ServiceID"
                                    }
                                ]
                            ]
                        ],
                        result: {
                            schema_model: {}
                        },
                        saveButton: 'QUERY'
                    }).subscribe(ok=>{
                        if(ok && ok.schema_model.PDQServiceID){
                            this.queryPDQ(patientId,ok.schema_model.PDQServiceID);
                        }
                    })
                }else{
                    this.queryPDQ(patientId, this.mainservice.global.PDQs[0].id);
                }
            }else{
                console.log("global",this.mainservice.global);
                this.cfpLoadingBar.start();
                this.service.queryNationalPationtRegister(this.service.getPatientId(patientId.attrs)).subscribe((xroadAttr)=>{
                    patientId.xroad = xroadAttr;
                    this.cfpLoadingBar.complete();
                },(err)=>{
                    console.error("Error Quering National Pation Register",err);
                    this.httpErrorHandler.handleError(err);
                    this.cfpLoadingBar.complete();
                });
            }
        }
    }
    queryPDQ(patientId, PDQServiceID){
        this.cfpLoadingBar.start();
        this.service.queryPatientDemographics(this.service.getPatientId(patientId.attrs),PDQServiceID).subscribe((xroadAttr)=>{
            patientId.xroad = xroadAttr;
            this.cfpLoadingBar.complete();
        },(err)=>{
            console.error("Error Quering National Patient Register",err);
            this.httpErrorHandler.handleError(err);
            this.cfpLoadingBar.complete();
        });
    }
    getDiffAttributeSet(){
        this.service.getDiffAttributeSet()
            .retry(2)
            .subscribe((res)=>{
                this.diffAttributeSet = res.filter(attr => attr.type ==='DIFF_RS' && attr.id != 'all');
            },(err)=>{
                console.error("Error getting Diff Attribute Set",err);
            });
    }
    getQueueNames(){
        this.retrieveMonitoringService.getQueueNames().subscribe(names=>{
            this.queues = names.map(name=> new SelectDropdown(name.name, name.description));
        },err=>{
            this.httpErrorHandler.handleError(err);
        })
    }
    ngOnDestroy() {
        // Save state of the study page in a global variable after leaving it
        let state = {
            aet: this.aet,
            aes: this.aes,
            aetmodel: this.aetmodel,
            attributeFilters: this.attributeFilters,
            exporterID: this.exporterID,
            aesdropdown: this.aesdropdown,
            rjnotes: this.rjnotes,
            reject: this.reject,
            filter: this.filter,
            moreMWL: this.moreMWL,
            morePatients: this.morePatients,
            moreStudies: this.moreStudies,
            limit: this.limit,
            trashaktive: this.trashaktive,
            patientmode: this.patientmode,
            // ScheduledProcedureStepSequence: this.ScheduledProcedureStepSequence,
            filterMode: this.filterMode,
            user: this.user,
            patients: this.patients,
            patient: this.patient,
            study: this.study,
            series: this.series,
            instance: this.instance,
            exporters: this.exporters,
            studyDate: this.studyDate,
            studyTime: this.studyTime,
            orderbytext: this.orderbytext,
            rjcode: this.rjcode,
            count: this.count
        };
        let global = this.mainservice.global;
        global.state = state || {};
        // if(_.hasIn(this.mainservice.global,"state")){
        //     this.mainservice.setGlobal({state:{}});
        //     this.mainservice.setGlobal({state:state});
        // }else{
            this.mainservice.setGlobal(global);
        // }
    }
}
