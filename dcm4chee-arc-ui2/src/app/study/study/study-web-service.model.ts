import {DcmWebApp} from "../../models/dcm-web-app";

export class StudyWebService {
    private _webServices:DcmWebApp[];
    private _selectedWebService:DcmWebApp


    constructor(
        object:{
            webServices?:DcmWebApp[];
            selectedWebService?:DcmWebApp
        } = {}
    ){
        this.webServices = object.webServices;
        this.selectedWebService = object.selectedWebService;
    }

    get webServices(): DcmWebApp[] {
        return this._webServices;
    }

    set webServices(value: DcmWebApp[]) {
        this._webServices = value;
        this._selectedWebService = undefined;
    }

    get selectedWebService(): DcmWebApp {
        return this._selectedWebService;
    }

    set selectedWebService(value: DcmWebApp) {
        this._selectedWebService = value;
    }
}
