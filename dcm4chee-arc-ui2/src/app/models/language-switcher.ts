import {Globalvar} from "../constants/globalvar";
import {LanguageConfig, LanguageObject, LocalLanguageObject} from "../interfaces";
import {j4care} from "../helpers/j4care.service";
import {AppService} from "../app.service";
import {User} from "./user";

export class LanguageSwitcher {
    private _currentSelectedLanguage:LanguageObject;
    private _languageList:LanguageObject[];
    private _open:boolean = false;
    constructor(languageConfig:LanguageConfig,user:User){
        try {
            this._languageList = languageConfig.dcmLanguages.map(language=>{
                return j4care.extractLanguageDataFromString(language);
            });
            const defaultConfigLanguage = j4care.extractLanguageDataFromString(j4care.getDefaultLanguageFromProfile(languageConfig,user));
            const currentSavedLanguage = <LocalLanguageObject> JSON.parse(localStorage.getItem('current_language'));
            //TODO check if currentSavedLanguage and defaultConfigLanguage the same is
            if(currentSavedLanguage && currentSavedLanguage.username === user.user){
                this._currentSelectedLanguage = currentSavedLanguage.language;
            }else{
                const localLanguage:LocalLanguageObject = {
                    language:defaultConfigLanguage,
                    username:user.user || ""
                };
                localStorage.setItem('current_language', JSON.stringify(localLanguage));
                setTimeout(()=>{
                    location.reload();
                },1);
                //this._currentSelectedLanguage =  defaultConfigLanguage;
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