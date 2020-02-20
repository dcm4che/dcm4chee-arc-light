import './polyfills.ts';

import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { enableProdMode } from '@angular/core';
import { environment } from './environments/environment';
import { AppModule } from './app/';

import { loadTranslations } from '@angular/localize';
import { getTranslations, ParsedTranslationBundle } from '@locl/core';

console.log("language set",)

// Get data
let languageCode = localStorage.getItem('language_code');

console.log("languageCode from localstorage",languageCode);
console.log("environment.production",environment.production);

if (environment.production) {
  enableProdMode();
}

if(languageCode && languageCode != "en"){
    getTranslations(`./assets/locale/${languageCode}.json`).then(
        (data: ParsedTranslationBundle) => {
            console.warn(data);
            loadTranslations(data.translations as any);
            console.log("after loadTranslation");
            import('./app/app.module').then(module => {
                console.log("in then",module);
                platformBrowserDynamic()
                    .bootstrapModule(module.AppModule)
                    .catch(err => console.error(err));
            }).catch(err=>console.error("first",err));
        }
    );
}else{
    platformBrowserDynamic().bootstrapModule(AppModule);
}

