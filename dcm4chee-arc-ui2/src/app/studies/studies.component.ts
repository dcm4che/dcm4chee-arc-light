import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-studies',
  templateUrl: './studies.component.html',
  styleUrls: ['./studies.component.css']
})
export class StudiesComponent implements OnInit {
    orderby = [
        {
            value:"PatientName",
            label:"<label title=\"Patient\">Patient</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span>",
            mode:"patient"
        },
        {
            value:"-PatientName",
            label:"<label title=\"Patient\">Patient</label><span class=\"orderbynamedesc\"></span>",
            mode:"patient"
        },
        {

            value:"StudyDate,StudyTime",
            label:"<label title=\"Study\">Study</label><span class=\"orderbydateasc\"></span>",
            mode:"study"
        },
        {
            value:"-StudyDate,-StudyTime",
            label:"<label title=\"Study\">Study</label><span class=\"orderbydatedesc\"></span>",
            mode:"study"
        },
        {
            value:"PatientName,StudyDate,StudyTime",
            label:"<label title=\"Study\">Study</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>",
            mode:"study"
        },
        {
            value:"-PatientName,StudyDate,StudyTime",
            label:"<label title=\"Study\">Study</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>",
            mode:"study"
        },
        {
            value:"PatientName,-StudyDate,-StudyTime",
            label:"<label title=\"Study\">Study</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>",
            mode:"study"
        },
        {
            value:"-PatientName,-StudyDate,-StudyTime",
            label:"<label title=\"Study\">Study</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>",
            mode:"study"
        },
        {
            value:"ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
            label:"<label title=\"Modality worklist\">MWL</label></span><span class=\"orderbydateasc\"></span>",
            mode:"mwl"
        },
        {
            value:"-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
            label:"<label title=\"Modality worklist\">MWL</label><span class=\"orderbydatedesc\"></span>",
            mode:"mwl"
        },
        {
            value:"PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
            label:"<label title=\"Modality worklist\">MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>",
            mode:"mwl"
        },
        {
            value:"-PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
            label:"<label title=\"Modality worklist\">MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>",
            mode:"mwl"
        },
        {
            value:"PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
            label:"<label title=\"Modality worklist\">MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>",
            mode:"mwl"
        },
        {
            value:"-PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime",
            label:"<label title=\"Modality worklist\">MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>",
            mode:"mwl"
        }
    ];
    showClipboardHeaders = {
        "study":false,
        "series":false,
        "instance":false
    };
    disabled = {};
    filter = { orderby: "StudyDate,StudyTime" };
    clipBoardNotEmpty(){
        return false; //TODO
    }
    user = {
        isRole:function(role){
            return true; //TODO
        }
    }
    filterMode = "study";
    showClipboardContent = false;
    showoptionlist = false;
    orderbytext = this.orderby[2].label;
    patient ={
        hide:false,
        modus:'patient',
        showmwls:true
    };
    study =  {
        modus:'study',
        selected:false
    };
    series = {
        modus:'series',
        selected:false
    };
    instance = {
        modus:'instance',
        selected:false
    };
    select_show=false;
  constructor() { }

  ngOnInit() {

  }

}
