import { Injectable } from '@angular/core';
import {
    HttpInterceptor,
    HttpRequest,
    HttpHandler,
    HttpEvent
} from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable()
export class WithCredentialsInterceptor implements HttpInterceptor {

    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        // Clone the request and add withCredentials: true
        const modifiedReq = req.clone({
            withCredentials: true
        });

        return next.handle(modifiedReq);
    }
}