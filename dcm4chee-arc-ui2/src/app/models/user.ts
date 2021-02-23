export class User {
    private _user: string;
    private _roles: Array<string>;
    private _realm:string;
    private _authServerUrl:string;
    private _su:boolean;
    private _tokenParsed:any;

    constructor(model:{
        user?: string,
        roles?: Array<string>,
        realm?:string,
        authServerUrl?:string,
        su?:boolean,
        tokenParsed?:any
    }={}){
        this._user = model.user;
        this._roles = model.roles;
        this._realm = model.realm;
        this._authServerUrl = model.authServerUrl;
        this._su = model.su;
        this._tokenParsed = model.tokenParsed;
    }
    get su(): boolean {
        return this._su;
    }

    set su(value: boolean) {
        this._su = value;
    }

    get user(): string {
        return this._user;
    }

    set user(value: string) {
        this._user = value;
    }

    get roles(): Array<string> {
        return this._roles;
    }

    set roles(value: Array<string>) {
        this._roles = value;
    }

    get realm():string {
        return this._realm;
    }

    set realm(value:string) {
        this._realm = value;
    }

    get authServerUrl():string {
        return this._authServerUrl;
    }

    set authServerUrl(value:string) {
        this._authServerUrl = value;
    }

    get tokenParsed(): any {
        return this._tokenParsed;
    }

    set tokenParsed(value: any) {
        this._tokenParsed = value;
    }
}
