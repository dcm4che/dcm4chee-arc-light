import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from "@angular/forms";
import {MatDialogRef} from "@angular/material";
import {CsvUploadService} from "./csv-upload.service";
import {AppService} from "../../../app.service";
import {j4care} from "../../../helpers/j4care.service";
import * as _ from "lodash";

@Component({
  selector: 'csv-upload',
  templateUrl: './csv-upload.component.html',
  styleUrls: ['./csv-upload.component.scss']
})
export class CsvUploadComponent implements OnInit {

    form: FormGroup;
    csvFile:File;
    aes;
    params = {
        formSchema:[],
        prepareUrl:undefined
    };
    showLoader = false;
    model = {};
    constructor(
        public dialogRef: MatDialogRef<CsvUploadComponent>,
        private _fb: FormBuilder,
        private service:CsvUploadService,
        private appService:AppService
    ){}
    inputChanged(form, e){
        console.log("form",form)
        console.log("this.fomr",this.form)
        console.log("e",e)
        console.log("e",e.target.checked);
        if(form.type === "checkbox"){
            this.form.controls[form.filterKey].setValue(e.target.checked);
        }
    }
    ngOnInit() {
        console.log("formSchema",this.params);
        let formContent = {};
        this.params.formSchema.forEach(form=>{
            if(form.type === "checkbox"){
                formContent[form.filterKey] =  [null];
            }else{
                formContent[form.filterKey] =[j4care.getValue(form.filterKey, this.params, form.defaultValue), form.validation]
            }
        });
        this.form = this._fb.group(formContent);
    }
    submit(){
        this.showLoader = true;
        let semicolon:boolean = false;
        let url = this.params.prepareUrl(this.form.value)
        if(_.hasIn(this.form.value,"semicolon") && this.form.value["semicolon"]){
            semicolon = true;
        }
        this.service.uploadCSV(url, this.csvFile, semicolon, (end)=>{
            this.showLoader = false;
            if(end.status >= 199 && end.status < 300){
                let msg = "Tasks created successfully!";
                try{
                    if(end.response){
                        let countObject = JSON.parse(end.response);
                        msg = `${countObject.count} tasks created successfully!`
                    }
                }catch (e){
                    console.log("Count could not be extracted",e)
                }
                this.appService.setMessage({
                    "text":msg,
                    "status":"info"
                })
                this.dialogRef.close('ok');
            }else{
                let msg = 'Upload failed, please try again later!';
                try{
                    if(end.response){
                        let countObject = JSON.parse(end.response);
                        msg = countObject.errorMessage;
                    }
                }catch (e){
                    console.log("Count could not be extracted",e)
                }
                this.appService.setMessage({
                    "text":msg,
                    "status":"error"
                });
                this.dialogRef.close(null);
            }
        },(err)=>{
            this.showLoader = false;
            this.appService.setMessage({
                "text":'Upload failed, please try again later!',
                "status":"error"
            })
            this.dialogRef.close(null);
        });
    }
    onFileChange(e){
        console.log("e",e.target.files[0]);
        this.csvFile = e.target.files[0];
    }
}
