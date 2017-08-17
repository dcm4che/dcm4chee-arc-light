export class User {
    private _user: string;
    private _roles: Array<string>;
    private _realm;
    private _authServerUrl;

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

    get realm() {
        return this._realm;
    }

    set realm(value) {
        this._realm = value;
    }

    get authServerUrl() {
        return this._authServerUrl;
    }

    set authServerUrl(value) {
        this._authServerUrl = value;
    }
}
