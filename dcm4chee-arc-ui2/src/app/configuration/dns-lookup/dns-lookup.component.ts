import {Component, signal} from '@angular/core';
import {ConfigTabComponent} from "../config-tab.component";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {DnsLookupService} from "./dns-lookup.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {JsonPipe, SlicePipe} from "@angular/common";

@Component({
  selector: 'app-dns-lookup',
  imports: [
    ConfigTabComponent,
    ReactiveFormsModule,
    FormsModule,
    JsonPipe
  ],
  templateUrl: './dns-lookup.component.html',
  styleUrl: './dns-lookup.component.scss',
  standalone: true
})
export class DnsLookupComponent {
  searchField = '';
  lookupResults:any = signal(0);
  constructor(
      private service:DnsLookupService,
      private httpErrorHandler:HttpErrorHandler
  ) { }
  dnsLookup(){
    console.log(this.searchField);
    if(this.searchField.trim() != ''){
      this.service.dnsLookup(this.searchField).subscribe((response)=>{
        console.log(response);
        this.lookupResults.set(response);
      }, err=>{
        this.httpErrorHandler.handleError(err);
      });
    }
  }
}

