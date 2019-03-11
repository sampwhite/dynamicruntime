/**
 * Convenience methods for talking to the sever and handling URLs for links and parsing the URL that got us here.
 */
class Client {
    static pathname;
    static query;
    static parsedArgs;
    static isLoggedIn;
    static profileRegistry = {};
    static currentProfileData;

    // Used to see how user interface behaves if there are delays in calls to server.
    // noinspection JSUnusedGlobalSymbols
    static sleep (time) {
        return new Promise((resolve) => setTimeout(resolve, time));
    }

    static init(path, query) {
        Client.pathname = path;
        let q = "";
        Client.parsedArgs = {};
        if (query && query.length > 0) {
            if (query.charAt(0) === '?') {
                q = query.substring(1);
            } else {
                q = query;
            }
            const usp = new URLSearchParams(q);
            for (const pair of usp.entries()) {
                const key = pair[0];
                const val = pair[1];
                if (key && key.length > 0 && val && val.length > 0) {
                    Client.parsedArgs[key] = val;
                }
            }

        }
        Client.query = q;
    }

    static createNavUrl(relativePath, args) {
        const usp = new URLSearchParams();
        const siteId = Client.parsedArgs["siteId"];
        if (siteId) {
            // Carry over siteId.
            usp.set("siteId", siteId);
        }
        if (args) {
            for (const arg of Object.entries(args)) {
                usp.set(arg[0], arg[1]);
            }
        }
        const query = usp.toString();
        const sepChar = (relativePath.indexOf('?') >= 0) ? '&' : '?';
        return relativePath + ((query && query.length > 0) ? (sepChar + query): "");
    }

    static doJsonFetch(method, endpoint, data, successFunc, errorFunc) {
        const args = {
            credentials: 'same-origin',
            headers: {
                "content-type": "application/json: charset=utf-8"
            },
            method: method
        };
        if (method !== "GET") {
            args.body = JSON.stringify(data);
        }
        //await Client.sleep(2000);
        fetch(endpoint, args)
            .then(res => res.text())
            .then(
                (result) => {
                    result = result.trim();
                    if (result.length === 0) {
                        errorFunc({message:"No response data to fetch."});
                    } else if (result.charAt(0) !== '{') {
                        errorFunc({message: result});
                    } else {
                        const data = JSON.parse(result);
                        let {httpCode} = data;
                        httpCode = httpCode || 200;
                        successFunc(httpCode, data);
                    }
                },
                (error) => {
                    // No longer processing form submit.
                    errorFunc(error);
                }
            )
    }

    static doJsonGet(endpoint, successFunc, errorFunc) {
        Client.doJsonFetch("GET", endpoint, null, successFunc, errorFunc);
    }

    static getIsLoggedIn() {
        return Client.isLoggedIn;
    }

    static registerForProfileUpdate(key, func) {
        Client.profileRegistry[key] = func;
    }

    static requestProfileDataFromServer() {
        // noinspection JSUnusedLocalSymbols
        Client.doJsonGet("/user/self/info", (httpCode, data) => {
            Client.setCurrentProfileData(data);
        }, error => {})
    }

    // noinspection JSUnusedGlobalSymbols
    static unregisterForProfileUpdate(key) {
        delete Client.profileRegistry[key];
    }

    static getCurrentProfileData() {
        return Client.currentProfileData || {};
    }

    static setCurrentProfileData(data) {
        Client.currentProfileData = data;
        const {userProfileData} = data;
        if (userProfileData) {
            const {publicName} = userProfileData;
            if (publicName) {
                data.username = publicName;
            }
        }
        const {username} = data;
        Client.isLoggedIn = !!username;

        // This is to support background refreshes of profile data (a future problem, not relevant yet).
        Client.notifyAll(Client.profileRegistry, data);
    }

    static notifyAll(registry, data) {
        for (const func of Object.values(registry)) {
            func(data);
        }
    }


}

export default Client;