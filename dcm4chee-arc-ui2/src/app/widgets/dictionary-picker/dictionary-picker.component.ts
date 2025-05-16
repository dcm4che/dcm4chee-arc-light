import {
    Component, OnInit, EventEmitter, Input, Output
} from '@angular/core';
declare var DCM4CHE: any;
import * as _ from 'lodash-es';
import {SearchPipe} from "../../pipes/search.pipe";
import {WindowRefService} from "../../helpers/window-ref.service";
import {Globalvar} from "../../constants/globalvar";
import {DeviceConfiguratorService} from "../../configuration/device-configurator/device-configurator.service";

@Component({
    selector: 'dictionary-picker',
    templateUrl: './dictionary-picker.component.html',
    styleUrls: ['./dictionary-picker.component.css'],
    standalone: false
})
export class DictionaryPickerComponent implements OnInit {
    Object = Object;
    @Input() dictionary;
    @Input() formelement;
    @Input() hideDots;
    @Output() onValueSet = new EventEmitter();
    filter = '';
    dcmTags = [];
    dcmTagsFiltered = [];
    sliceTo = 20;
    scrollTop = 0;
    search = new SearchPipe();
    constructor(
        public deviceConfiguratorService:DeviceConfiguratorService
    ) { }

    ngOnInit() {
        switch(this.dictionary) {
            case 'dcmTag':
                _.forEach(DCM4CHE.elementName.forTag("all"),(m,i)=>{
                    this.dcmTags.push({
                        key:i,
                        text:m
                    });
                    this.dcmTagsFiltered.push({
                        key:i,
                        text:m
                    });
                });
                break;
            case 'dcmTransferSyntax':
                _.forEach(DCM4CHE.TransferSyntax.nameOf("all"),(m,i)=>{
                    this.dcmTags.push({
                        key:i,
                        text:m
                    });
                    this.dcmTagsFiltered.push({
                        key:i,
                        text:m
                    });
                });
                break;
            case 'dcmProperty':
                this.hideDots = true;
                const currentPropertiePosition = document.location.pathname.split(".").pop(); //Get the last string after the .  from the current URL
                const uiConfig = _.get(this.deviceConfiguratorService.device,"dcmDevice.dcmuiConfig[0]");
                if(currentPropertiePosition === "dcmWebApp"){
                    const dropdown = [
                        {
                            key:"IID_PATIENT_URL=[VIEWER_URL]"
                        },{
                            key:"IID_STUDY_URL=[VIEWER_URL]"
                        },{
                            key:"IID_ENCODE=false"
                        },{
                            key:"IID_URL_TARGET=_self"
                        },{
                            key:"IID_URL_TARGET=_blank"
                        },{
                            key:"MWLAccessionNumberGenerator=[name-of-cd-import-acc-no-id-generator]"
                        },{
                            key:"allow-any-hostname=true"
                        },{
                            key:"disable-trust-manager=true"
                        },{
                            key:"allow-any-hostname=true"
                        },{
                            key:"disable-trust-manager=true"
                        },{
                            key:"bearer-token=[bearer-token]"
                        },{
                            key:"basic-auth=[basic-auth]"
                        },{
                            key:"basic-auth=[basic-auth]"
                        },{
                            key:"content-type=true"
                        },{
                            key:"content-type=false"
                        },{
                            key:"chunked=true"
                        },{
                            key:"transfer-syntax=[transfer-syntax]"
                        },{
                            key:"concurrency=[concurrency]"
                        },{
                            key:"WebAppDropdownLabel=[custom_label]"
                        }
                    ];
                    this.dcmTags = dropdown;
                    this.dcmTagsFiltered = _.clone(dropdown);
                }

                if(_.hasIn(uiConfig,"dcmuiMWLWorklistLabel") && uiConfig.dcmuiMWLWorklistLabel.length > 0){
                    uiConfig.dcmuiMWLWorklistLabel.forEach(el=>{
                        this.dcmTags.push({
                            text:"",
                            key:`MWLWorklistLabel=${el}`,
                            description:$localize `:@@configured_mwl_worklist_label:Configured Worklist label that is in UI config configured, will be used in the Navigation page`
                        })
                        this.dcmTagsFiltered.push({
                            text:"",
                            key:`MWLWorklistLabel=${el}`,
                            description:$localize `:@@configured_mwl_worklist_label:Configured Worklist label that is in UI config configured, will be used in the Navigation page`
                        })
                    })
                }
                if(_.hasIn(this.deviceConfiguratorService.device,"dcmDevice.dcmWebApp")){
                    const webApps = _.get(this.deviceConfiguratorService.device,"dcmDevice.dcmWebApp");
                    webApps.forEach(el=>{
                        if(_.hasIn(el,"dcmWebServiceClass") && el.dcmWebServiceClass.indexOf("STOW_RS") > -1){
                            this.dcmTags.push({
                                text:"",
                                key:`STOWWebApp=${el.dcmWebAppName}`,
                                description:el.description
                            })
                            this.dcmTagsFiltered.push({
                                text:"",
                                key:`STOWWebApp=${el.dcmWebAppName}`,
                                description:el.description
                            })
                        }
                    })
                }
                if(_.hasIn(this.deviceConfiguratorService.device,"dcmDevice.dcmWebApp")){
                    const webApps = _.get(this.deviceConfiguratorService.device,"dcmDevice.dcmWebApp");
                    webApps.forEach(el=>{
                        if(_.hasIn(el,"dcmWebServiceClass") && el.dcmWebServiceClass.indexOf("PAM") > -1){
                            this.dcmTags.push({
                                text:"",
                                key:`PAMWebApp=${el.dcmWebAppName}`,
                                description:el.description
                            })
                            this.dcmTagsFiltered.push({
                                text:"",
                                key:`PAMWebApp=${el.dcmWebAppName}`,
                                description:el.description
                            })
                        }
                    })
                }
                break;
            case 'dcmSOPClass':
                _.forEach(DCM4CHE.SOPClass.nameOf("all"),(m,i)=>{
                    this.dcmTags.push({
                        key:i,
                        text:m
                    });
                    this.dcmTagsFiltered.push({
                        key:i,
                        text:m
                    });
                });
                break;
        }

    }
    ngAfterViewInit() {
        WindowRefService.nativeWindow.document.getElementsByClassName("dictionary_widget_search")[0].focus();
        // $('.dictionary_widget_search').focus();
    }
    addSelectedElement(element){
        this.onValueSet.emit(element.key);
    }

    keyDown(e){
        if(e.keyCode === 13){
            let filtered = new SearchPipe().transform(this.dcmTags, this.filter);
            if(filtered.length > 0){
                this.onValueSet.emit(filtered[0].key);
            }
        }
    }

    onScroll(e){
        const offsetScrollHeight = e.target.scrollTop + e.target.offsetHeight;
        if(this.scrollTop < e.target.scrollTop && offsetScrollHeight + 20 > e.target.scrollHeight){
            this.scrollTop = e.target.scrollTop;
            this.loadMore();
        }
    }
    onSearch(){
        this.sliceTo = 20;
        this.scrollTop = 0;
        this.dcmTagsFiltered = this.search.transform(this.dcmTags,this.filter);
    }
    loadMore(){
        this.sliceTo += 20;
    }
    close(){
        this.onValueSet.emit("");
    }
}
