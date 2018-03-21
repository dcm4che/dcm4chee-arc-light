import {Component, Input, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from "@angular/forms";
import {MatDialogRef} from "@angular/material";
import {CsvRetrieveService} from "./csv-retrieve.service";

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
   constructor(
      public dialogRef: MatDialogRef<CsvRetrieveComponent>,
      private _fb: FormBuilder,
      private service:CsvRetrieveService
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
      console.log("aes",this.aes);
  }
  getValue(key,defaultVal?){
       if(this.params[key])
           return this.params[key];
       else
           return defaultVal || '';
  }
    submit(){
        this.service.uploadCSV(this.form.value, this.csvFile);
    }
    onFileChange(e){
        console.log("e",e.target.files[0]);
        this.csvFile = e.target.files[0];
    }
}
