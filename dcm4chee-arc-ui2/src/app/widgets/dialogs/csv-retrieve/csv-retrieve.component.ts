import {Component, Input, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from "@angular/forms";
import {MatDialogRef} from "@angular/material";
import {CsvRetrieveService} from "./csv-retrieve.service";
import {AppService} from "../../../app.service";

@Component({
  selector: 'csv-retrieve',
  templateUrl: './csv-retrieve.component.html',
  styleUrls: ['./csv-retrieve.component.scss']
})
export class CsvRetrieveComponent implements OnInit {

    form: FormGroup;
    csvFile:File;
    aes;
    params;
    showLoader = false;
    constructor(
        public dialogRef: MatDialogRef<CsvRetrieveComponent>,
        private _fb: FormBuilder,
        private service:CsvRetrieveService,
        private appService:AppService
    ){}

    ngOnInit() {
        this.form = this._fb.group({
            aet:[this.getValue('aet'), Validators.required],
            externalAET:[this.getValue('externalAET'), Validators.required],
            field:[this.getValue('field',1),  Validators.minLength(1)],
            destinationAET:[this.getValue('destinationAET'), Validators.required],
            priority:this.getValue('priority',NaN),
            batchID:this.getValue('batchID')
        });
    }
    getValue(key,defaultVal?){
       if(this.params[key])
           return this.params[key];
       else
           return defaultVal || '';
    }
    submit(){
        this.showLoader = true;
        this.service.uploadCSV(this.form.value, this.csvFile, (end)=>{
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
                this.appService.setMessage({
                    "text":'Upload failed, please try again later!',
                    "status":"error"
                })
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
