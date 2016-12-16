export class User {
    private _user:string;
    private _roles:Array<string>;

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
}
