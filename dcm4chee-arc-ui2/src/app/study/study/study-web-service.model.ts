import {DcmWebApp} from "../../models/dcm-web-app";
import * as _ from "lodash-es";
import {SelectDropdown} from "../../interfaces";

export class StudyWebService {
    private _webServices:DcmWebApp[];
    private _allWebServices:DcmWebApp[];
    private _selectedWebService:DcmWebApp;
    private _selectDropdownWebServices:SelectDropdown<DcmWebApp>[];


    constructor(
        object:{
            webServices?:DcmWebApp[];
            selectedWebService?:DcmWebApp,
            allWebServices?:DcmWebApp[]
        } = {}
    ){
        this.webServices = object.webServices;
        this.allWebServices = object.allWebServices;
        if(_.hasIn(object,"selectedWebService.dcmWebAppName")){
            object.webServices.forEach((webService:DcmWebApp)=>{
               if(object.selectedWebService.dcmWebAppName === webService.dcmWebAppName){
                   this.selectedWebService = webService;
               }
            });
        }
    }

    seletWebAppFromWebAppName(dcmWebAppName:string){
        if(this._webServices && dcmWebAppName && dcmWebAppName != ""){
            this._webServices.forEach((webService:DcmWebApp)=>{
                if(dcmWebAppName === webService.dcmWebAppName){
                    this._selectedWebService = webService;
                }
            })
        }else{
            this._selectedWebService = undefined;
        }
    }

    get webServices(): DcmWebApp[] {
        return this._webServices;
    }

    set webServices(value: DcmWebApp[]) {
        this._webServices = value;
        this._selectedWebService = undefined;
        this._selectDropdownWebServices = this._webServices.map((webService:DcmWebApp)=>{
            return new SelectDropdown(webService,webService.dcmWebAppName,webService.dicomDescription,undefined,undefined,webService);
        })

    }

    get selectedWebService(): DcmWebApp {
        return this._selectedWebService;
    }

    set selectedWebService(value: DcmWebApp) {
        this._selectedWebService = value;
    }

    get selectDropdownWebServices(): SelectDropdown<DcmWebApp>[] {
        return this._selectDropdownWebServices;
    }

    set selectDropdownWebServices(value: SelectDropdown<DcmWebApp>[]) {
        this._selectDropdownWebServices = value;
    }


    get allWebServices(): DcmWebApp[] {
        return this._allWebServices;
    }

    set allWebServices(value: DcmWebApp[]) {
        this._allWebServices = value;
    }
}
