import {Globalvar} from "../constants/globalvar";
import {LanguageConfig, LanguageObject, LocalLanguageObject} from "../interfaces";
import {j4care} from "../helpers/j4care.service";
import {AppService} from "../app.service";
import {User} from "./user";

export class LanguageSwitcher {
    private _currentSelectedLanguage:LanguageObject;
    private _languageList:LanguageObject[];
    private _open:boolean = false;
    constructor(languageConfig:LanguageConfig){
        try {
            this._languageList = languageConfig.dcmLanguages.map(language=>{
                return j4care.extractLanguageDataFromString(language);
            });
            const currentSavedLanguage = localStorage.getItem('current_language');
            if(currentSavedLanguage){
                this._currentSelectedLanguage = this.languageList.filter(language=>language.code === currentSavedLanguage)[0];
            }else{
                this._currentSelectedLanguage = this.languageList[0]; //TODO should make this more intelligent ( at least to pick en ), must be tested with not secured version
            }
        }catch (e) {
            j4care.log("Error on language-switcher construct",e);
        }
    }

    get currentSelectedLanguage(): LanguageObject {
        return this._currentSelectedLanguage;
    }

    set currentSelectedLanguage(value: LanguageObject) {
        this._currentSelectedLanguage = value;
    }

    get languageList(): LanguageObject[] {
        return this._languageList;
    }

    set languageList(value: LanguageObject[]) {
        this._languageList = value;
    }

    get open(): boolean {
        return this._open;
    }

    set open(value: boolean) {
        this._open = value;
    }
}