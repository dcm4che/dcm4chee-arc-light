import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { enableProdMode } from '@angular/core';
import { environment } from './environments/environment';

import localeDe from '@angular/common/locales/de';
import localeDeExtra from '@angular/common/locales/extra/de';

import { AppModule } from './app/app.module';
import {registerLocaleData} from '@angular/common';
registerLocaleData(localeDe, 'de', localeDeExtra);




if (environment.production) {
  enableProdMode();
}



platformBrowserDynamic().bootstrapModule(AppModule)
    .catch((err: unknown) => console.error(err));
