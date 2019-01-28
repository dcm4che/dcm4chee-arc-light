import {Component, HostListener, OnInit} from '@angular/core';
import {ActivatedRoute} from "@angular/router";
import {AccessLocation, FilterSchema, StudyFilterConfig, StudyPageConfig, StudyTab} from "../../interfaces";
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

    isOpen = true;
    testToggle(){
        this.isOpen = !this.isOpen;
    }
    studyConfig:StudyPageConfig = {
        tab:"study",
        accessLocation:"internal"
    };

    filter:StudyFilterConfig = {
        filterSchemaMain:{
            lineLength:undefined,
            schema:[]
        },
        filterSchemaExpand:{
            lineLength:2,
            schema:[]
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

    applicationEntities = {
        aes:{
          external:[],
          internal:[]
        },
        aets:{
          external:[],
          internal:[]
        },
        isSet:false
    };

    constructor(
        private route:ActivatedRoute,
        private service:StudyService,
        private permissionService:PermissionService,
        private appService:AppService
    ) { }

    ngOnInit() {
        console.log("aet",this.applicationEntities);
        this.route.params.subscribe(params => {
          this.studyConfig.tab = params.tab;
          this.getApplicationEntities();
        });
    }
    testShow = true;
    fixedHeader = false;
    @HostListener("window:scroll", [])
    onWindowScroll(e) {
        let html = document.documentElement;
        if(html.scrollTop > 73){
            this.fixedHeader = true;
            this.testShow = false;
        }else{
            this.fixedHeader = false;
            this.testShow = true;
        }

    }

    search(e){
        console.log("e",e);
    }

    filterChanged(){

    }

    setSchema(){
        this.filter.filterSchemaMain.lineLength = undefined;
        this.filter.filterSchemaExpand.lineLength = undefined;
        // setTimeout(()=>{
            this.filter.filterSchemaMain  = this.service.getFilterSchema(this.studyConfig.tab,  this.applicationEntities.aes[this.studyConfig.accessLocation],this.filter.quantityText,false);
            this.filter.filterSchemaExpand  = this.service.getFilterSchema(this.studyConfig.tab, this.applicationEntities.aes[this.studyConfig.accessLocation],this.filter.quantityText,true);
        // },0);
    }

    accessLocationChange(e){
        console.log("e",e.value);
        console.log("accessLocation",this.studyConfig.accessLocation);
        this.setSchema();
    }

    getApplicationEntities(){
        if(!this.applicationEntities.isSet){
            Observable.forkJoin(
                this.service.getAes().map(aes=> aes.map(aet=> new Aet(aet))),
                this.service.getAets().map(aets=> aets.map(aet => new Aet(aet))),
            )
            .subscribe((res)=>{
                [0,1].forEach(i=>{
                    res[i] = j4care.extendAetObjectWithAlias(res[i]);
                    ["external","internal"].forEach(location=>{
                      this.applicationEntities.aes[location] = this.permissionService.filterAetDependingOnUiConfig(res[i],location);
                      this.applicationEntities.aets[location] = this.permissionService.filterAetDependingOnUiConfig(res[i],location);
                      this.applicationEntities.isSet = true;
                    })
                });
                this.setSchema();
            },(err)=>{
                this.appService.showError("Error getting AETs!");
                j4care.log("error getting aets in Study page",err);
            });
        }else{
            this.setSchema();
        }
    }
}
