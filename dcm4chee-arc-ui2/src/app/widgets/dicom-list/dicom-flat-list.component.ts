import {Component, HostListener, Input, OnInit} from '@angular/core';
import * as _ from 'lodash-es';
import {WindowRefService} from "../../helpers/window-ref.service";
import {j4care} from "../../helpers/j4care.service";

@Component({
  selector: 'dicom-list',
  templateUrl: './dicom-flat-list.component.html'
})
export class DicomFlatListComponent implements OnInit {

    @Input() dicomObject;
    _ = _;
    width;
    table = [
        {
            title:$localize `:@@patients_name:Patient's Name`,
            code:"00100010",
            description:$localize `:@@patients_name:Patient's Name`,
            widthWeight:1,
            calculatedWidth:"20%"
        },{
            title:$localize `:@@patient_id:Patient ID`,
            code:"00100020",
            description:$localize `:@@patient_id:Patient ID`,
            widthWeight:1,
            calculatedWidth:"20%",
            cssClass:"hideMobile"
        },{
            title:$localize `:@@birth_date:Birth Date`,
            code:"00100030",
            description:$localize `:@@patients_birth_date:Patient's Birth Date`,
            widthWeight:1,
            calculatedWidth:"20%",
            cssClass:"hideOn1000px"
        },{
            title:$localize `:@@sex:Sex`,
            code:"00100040",
            description:$localize `:@@patients_sex:Patient's Sex`,
            widthWeight:1,
            calculatedWidth:"20%",
            cssClass:"hideOn800px"
        },{
            title:$localize `:@@issuer_of_pid:Issuer of PID`,
            code:"00100021",
            description:$localize `:@@issuer_of_patient_id:Issuer of Patient ID`,
            widthWeight:1,
            calculatedWidth:"20%",
            cssClass:"hideOn1100px"
        },
        {
            title:$localize `:@@study_id:Study ID`,
            code:"00200010",
            description:$localize `:@@study_id:Study ID`,
            widthWeight:1,
            calculatedWidth:"20%",
            cssClass:"hideOn800px"
        },{
            title:$localize `:@@acc._nr.:Acc. Nr.`,
            code:"00080050",
            description:$localize `:@@accession_number:Accession Number`,
            widthWeight:1,
            calculatedWidth:"20%",
            cssClass:"hideOn1400px"
        },
        {
            title:$localize `:@@modality:Modality`,
            code:"00080061",
            description:$localize `:@@modalities_in_study:Modalities in Study`,
            widthWeight:0.6,
            calculatedWidth:"20%",
            cssClass:"hideMobile"
        },
        {
            title:$localize `:@@number_of_related_series:#S`,
            code:"00201206",
            description:$localize `:@@number_of_study_related_series:Number of Study Related Series`,
            widthWeight:0.2,
            calculatedWidth:"20%"
        },
        {
            title:$localize `:@@number_of_instances:#I`,
            code:"00201208",
            description:$localize `:@@number_of_study_related_instances:Number of Study Related Instances`,
            widthWeight:0.2,
            calculatedWidth:"20%"
        }
      ];
    loadMoreAudit;
    constructor() { }
    moreStudies = {
        limit: 30,
        start: 0,
        loaderActive: false
    };
    @HostListener('window:resize', ['$event'])
    onResize(event) {
       if(!this.width){
        this.width = event.target.innerWidth;
       }else{
        if(Math.abs(this.width - event.target.innerWidth) > 10){
            this.calculateWidthOfTable();
        }
       }
    }
    @HostListener('window:scroll', ['$event'])
    loadMoreStudiesOnScroll(event?) {
        this.loadMoreCheck();
        this.resetMoreCheck();
    }
    loadMoreCheck(){
        let hT = WindowRefService.nativeWindow.document.getElementsByClassName("load_more")[0] ? j4care.offset(WindowRefService.nativeWindow.document.getElementsByClassName("load_more")[0]).top : 0,
        hH = WindowRefService.nativeWindow.document.getElementsByClassName("load_more")[0].offsetHeight,
        wH = WindowRefService.nativeWindow.innerHeight,
        wS = WindowRefService.nativeWindow.pageYOffset;
        //ws
        if (wS > (hT + hH - wH)){
            this.loadMoreStudies();
        }
    }
    resetMoreCheck(){

        // let hT = ($('.load_more_start').offset()) ? $('.load_more_start').offset().top : 0,
        let hT = WindowRefService.nativeWindow.document.getElementsByClassName("load_more_start")[0] ? j4care.offset(WindowRefService.nativeWindow.document.getElementsByClassName("load_more_start")[0]).top : 0,
            hH =  WindowRefService.nativeWindow.document.getElementsByClassName("load_more_start")[0].offsetHeight,
            // hH = $('.load_more_start').outerHeight(),
            // wH = $(window).height(),
            wS = window.pageYOffset;
        console.log("reset (hT + hH - wH)",(hT + hH));
        console.log("reset wS",wS);
        if ((hT + hH) > wS ){
            this.resetMore();
        }
    }
    resetMore(){
        this.moreStudies =  {
            limit: 30,
            start: 0,
            loaderActive: false
        };
    }
    loadMoreStudies(){
        this.moreStudies.loaderActive = true;
        this.moreStudies.limit += 20;
        this.moreStudies.loaderActive = false;
    }
    ngOnInit() {
        this.calculateWidthOfTable();
    }
    calculateWidthOfTable(){
        let summ = 0;
        _.forEach(this.table,(m,i)=>{
            summ += m['widthWeight'];
        });
        _.forEach(this.table,(m,i)=>{
            m['calculatedWidth'] =  ((m['widthWeight'] * 100)/summ)+"%";
        });
    };

}
