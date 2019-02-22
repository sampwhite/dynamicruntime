// noinspection JSUnusedAssignment
/**
 * Functions used to help build the basic GUI of the Dynamic Runtime application.
 */

// Mimic *import* statements. We cannot do imports because we are doing standalone React.
const React = React;
const {Component} = React;
if (!Component.setState) {
    // Suppress warnings on usage of setState.
    Component.setState = {};
}

function extractParams(url) {
    return new URLSearchParams(url);
}

class DnMessage extends Component {
    render() {
        const {error} = this.props;
        const className = (error) ? "error" : "message";
        return <div className={className}>{this.props.children}</div>
    }
}

class DnTable extends Component {
    constructor(props) {
        super(props);
        this.state = {
            error: null,
            isLoaded: false,
            items: []
        };
    }

    componentDidMount() {
        const {dataUrl} = this.props;
        fetch(dataUrl, {credentials: 'same-origin'})
            .then(res => res.json())
            .then(
                (result) => {
                   this.setState({
                        isLoaded: true,
                        items: result.items
                    });
                },
                (error) => {
                    this.setState({
                        isLoaded: true,
                        error
                    });
                }
            )
    }

    static columnValue(item, col) {
        const {mkDisplay} = col;
        if (mkDisplay) {
            return mkDisplay(item);
        }
        return item[col.key]
    }

    render() {
        const {columns, desFunction} = this.props;
        const {error, isLoaded, items} = this.state;
        if (!isLoaded) {
            return <DnMessage>Loading...</DnMessage>
        } else if (error) {
            return <DnMessage error={true}>Error: {error.message}</DnMessage>
        } else {
            const headers = columns.map((col, index) => {
                return (
                    <th key={index}>{col.label}</th>
                );
            });
            const description = desFunction ? <div>{desFunction(items)}</div> : "";
            const rows = items.map((item, itemIndex) => {
                const row = columns.map((col, colIndex) => {
                    return (
                        <td key={colIndex} className={col.key + " border"}>{DnTable.columnValue(item, col)}</td>
                    );
                });
                return (
                    <tr key={itemIndex}>{row}</tr>
                );
            });
            return (
                <div>
                    {description}
                    <table>
                        <thead>
                        <tr>{headers}</tr>
                        </thead>
                        <tbody>
                        {rows}
                        </tbody>
                    </table>
                </div>
            );
        }

    }
}

class DnEndpointForm extends Component {
    constructor(props) {
        super(props);
        this.state = {
            error: null,
            isLoaded: false,
            items: []
        };
        this.computeRequestUrl = this.computeRequestUrl.bind(this);
        this.handleInputChange = this.handleInputChange.bind(this);
        this.setFormBuildingState = this.setFormBuildingState.bind(this);
        this.onSubmit = this.onSubmit.bind(this);
    }

    componentDidMount() {
        const {endpoint} = this.props;
        const e = endpoint ? endpoint.trim() : "";
        if (e.length === 0) {
            return;
        }
        const {dataUrl} = this.props;
        const baseUrl = (dataUrl.indexOf("?") > 0) ? dataUrl + "&" : dataUrl + "?";
        const fetchUrl = baseUrl + "endpoint=" + e;
        fetch(fetchUrl, {credentials: 'same-origin'})
            .then(res => res.json())
            .then(
                (result) => {
                    this.setFormBuildingState(result.items)
                },
                (error) => {
                    this.setState({
                        isLoaded: true,
                        error
                    });
                }
            )
    }

    setFormBuildingState(items) {
        const obj = {isLoaded: true, items: items};
        if (items.length === 1) {
            const {endpointInputType} = items[0];
            if (endpointInputType) {
                const {dnFields} = endpointInputType;
                const fields = dnFields ? dnFields : [];
                fields.map(fld => {
                    const {name} = fld;
                    const fldName = "fld_" + name;
                    // Controlled forms need an initial state value.
                    obj[fldName] = "";
                });
            }
        }
        //alert(JSON.stringify(obj));

        this.setState(obj);
    }

    static toFormType(dnType) {
        switch (dnType) {
            case "String":
                return "text";
            case "Integer":
                return "number";
            case "Date":
                return "date";
            case "Boolean":
                return "text";
            case "Float":
                return "number";
            default:
                return "text";
        }
    }

    computeRequestUrl() {
        const {endpoint} = this.props;
        const {items} = this.state;
        const {endpointInputType} = items[0];
        const {dnFields: fields} = endpointInputType;

        const params = [];
        //const obj = Object.assign({}, this.state);
        //obj.items = [];
        //alert(JSON.stringify(obj));
        if (fields) {
            for (const fld of fields) {
                const fldName = "fld_" + fld.name;
                let val = this.state[fldName];
                if (val) {
                    const {isPassword} = fld;
                    if (isPassword) {
                        val = val.replace(/./g, '*');
                    }

                    params.push(fld.name + "=" + encodeURIComponent(val));
                }
            }
        }
        const paramStr = params.join("&");
        return paramStr.length > 0 ? endpoint + "?" + paramStr : endpoint;
    }

    computeRequestParams() {
        const {items} = this.state;
        const {endpointInputType} = items[0];
        const {dnFields: fields} = endpointInputType;

        const params = {};
        //const obj = Object.assign({}, this.state);
        //obj.items = [];
        //alert(JSON.stringify(obj));
        if (fields) {
            for (let fld of fields) {
                const fldName = "fld_" + fld.name;
                const val = this.state[fldName];
                if (val) {
                    params[fld.name] = val;
                }
            }
        }
        return params;
    }


    handleInputChange(event) {
        const target = event.target;
        const value = target.value;
        const name = target.name;

        this.setState({
            [name]: value
        }, null);
    }

    onSubmit(event) {
        event.preventDefault();
        const {endpoint} = this.props;
        const {items} = this.state;
        const {httpMethod} = items[0];
        const execUrl = (httpMethod === "GET") ? this.computeRequestUrl() : endpoint;

        const args = (httpMethod === "GET") ? {credentials: 'same-origin'} :
            {
                credentials: 'same-origin',
                headers: {
                    "content-type": "application/json: charset=utf-8"
                },
                body: JSON.stringify(this.computeRequestParams()),
                method: httpMethod
            };

        fetch(execUrl, args)
            .then(res => res.text())
            .then(
                (result) => {
                    this.setState({
                        results: result
                    });
                },
                (error) => {
                     this.setState({
                        results: error.message
                    });
                }
            )
    }

    render() {
        const {endpoint} = this.props;
        if (!endpoint || endpoint.trim().length === 0) {
            return <DnMessage>Endpoint form requires the query parameter <b>endpoint</b>.</DnMessage>
        }
        const {error, isLoaded, items} = this.state;
        if (!isLoaded) {
            return <DnMessage>Loading...</DnMessage>
        } else if (error) {
            return  (<DnMessage error={true}>Error: {error.message}</DnMessage>);
        } else if (items.length !== 1) {
            return <DnMessage>Did not get back an endpoint definition for endpoint {endpoint}.</DnMessage>
        }
        else {
            const endpointDef = items[0];
            const {endpointInputType, endpointOutputType, httpMethod, description} = endpointDef;
            if (!endpointInputType) {
                return <DnMessage>Endpoint definition for endpoint {endpoint} did not return a
                    input type.</DnMessage>
            }
            const {dnFields} = endpointInputType;
            const fields = dnFields ? dnFields : [];
            const {baseType: inputBaseType} = endpointInputType;

            const formFields = fields.map(fld => {
                const {coreType, isPassword, isLargeString, required, name, label, description} = fld;
                const labelClassName = required ? "highlight" : "standard";
                const fldName = "fld_" + name;
                if (coreType === "Map" || isLargeString) {
                    return [
                        <tr key={"label_" + name}>
                            <td className="formLabel"><label className={labelClassName}>{label}</label></td>
                            <td className="formDescription" colSpan="2">{description}</td>
                        </tr>,
                        <tr key={"textarea_"+ name}>
                            <td colSpan="3">
                                <textarea className="largeString" name={fldName} cols="100" rows="10"
                                          value={this.state[fldName]} onChange={this.handleInputChange}/>
                            </td>
                        </tr>
                    ]
                } else {
                    const inputType = (isPassword) ? "password" : DnEndpointForm.toFormType(coreType);
                    return (
                        <tr key={"row_" + name}>
                            <td key={"label_" + name} className="formLabel">
                                <label className={labelClassName}>{label}:</label></td>
                            <td key={"input_" + name} className="formInput">
                                <input name={fldName} type={inputType} value={this.state[fldName]}
                                       onChange={this.handleInputChange}/>
                            </td>
                            <td key={"description_" + name} className="formDescription">{description}</td>
                        </tr>
                    )
                }
            });
            let inputLinkStr = "";
            if (inputBaseType && inputBaseType.indexOf(".") > 0) {
                const url = "/schema/dnType/list?dnTypeName=" + inputBaseType;
                inputLinkStr = <div>The core input type definition for endpoint <b>{endpoint} </b>
                    can be found at <a href={url}>{inputBaseType}</a>. This does not include
                    general protocol parameters</div>
            } else {
                if (fields.length > 0) {
                    inputLinkStr = <div>Endpoint <b>{endpoint}</b> has only intrinsic defined input parameters.</div>
                } else {
                    inputLinkStr = <div>Endpoint <b>{endpoint}</b> has no input parameters.</div>
                }
            }
            const {dnFields: outputFields} = endpointOutputType || {};
            //alert(JSON.stringify(endpointOutputType));
            let outputLinkStr = "";
            if (outputFields) {
                const itemsField = outputFields.find(oField => oField.name === "items");
                const {dnTypeRef: outputTypeRef} = itemsField || {};
                if (outputTypeRef && outputTypeRef.indexOf(".") > 0) {
                    const url = "/schema/dnType/list?dnTypeName=" + outputTypeRef;
                    outputLinkStr = <div>The definition of the type for <i>items</i> in the response
                        can be found at <a href={url}>{outputTypeRef}</a>.</div>;
                }
            }
            const {name: endpointSchemaName} = endpointDef;
            const endpointUrl = "/schema/dnType/list?dnTypeName=" + endpointSchemaName;
            const endpointLinkStr = <div>The full endpoint definition can be found at <a href={endpointUrl}>
                {endpointSchemaName}</a></div>;
            const mainTable = (fields.length > 0) ?
                <table>
                    <tbody>
                    {formFields}
                    </tbody>
                </table> : "";
            return (
                <div>
                    <i>{description}</i>
                    <p/>
                    {inputLinkStr}
                    {outputLinkStr}
                    {endpointLinkStr}
                    <form key="mainForm" onSubmit={this.onSubmit}>
                        {mainTable}
                        <p key="requestInfo"><b>Request: </b>
                            <span className={httpMethod + "Method"}>{httpMethod}</span>:{this.computeRequestUrl()}</p>
                        <p key="submitButton"><input type="submit" value="Execute Request"/></p>
                    </form>
                    <form key="results">
                        <textarea name="results" cols="140" rows="40" value={this.state.results} readOnly={true}/>
                    </form>
                </div>
            );
        }
    }
}

/**
 * This class provides a dynamic rendering of a Login, Forgot Password, and Registration forms.
 * It uses the overlaps in the definitions of the forms to create a single code solution
 * for all the different forms. It does this by keeping track of a current *activity*.
 *
 * The following *activity* values are captured into the React state. They determine
 * the form fields to present, the request to execute when the form is submitted, and the
 * mechanism for handling the response. In some cases, an activity can transition into another
 * activity allowing one form to transition into another.
 *
 * * loginByPassword - Attempts to login using a simple username and password. If a 403 is returned
 *   indicating that the browser is not a familiar browser, then the activity will transition to
 *   the *loginByCode* activity.
 *
 * * loginByCode - Provides a button to send a generated verification code to a user's registration email.
 *   It then uses that code to perform a login.
 *
 * * forgotPassword - This is identical to the *loginByCode* except that the user must provide a new password
 *   as a part of performing the login.
 *
 * * registerNewEmail - Provides a form to enter an email, a button to sent a generate verification code
 *   to that email, and will create a new user using the new email as the registration email if the verification
 *   code is entered into the form. After this activity is successfully submitted, it will transition to the
 *   *loginSetData* activity.
 *
 * * loginSetData - This can only be performed using the verification code used by the *registerNewEmail*. It
 *   sets the username and password of a new user that has not yet been assigned a username and password.
 *
 * In addition to the activities that are captured in state and control the display and actions of the form,
 * there is an additional action called *sendCode* which is used to make a request to the server to create
 * and send a verification code to the registration email. Testing on this activity shows up in the
 * processRequestResult method.
 *
 * This implementation also tries to dynamically indicate the user an awareness of the current inputs and
 * the actions that have been performed up to that point. All the forms have a progress area where the form reports
 * either progress or errors. There is also instruction text which is chosen by activity. Once the login
 * is performed, the class calls a callback function to allow the main HTML page to choose how it wants to handle
 * a successful login.
 *
 * Some of the activity calls require first a request for a *formAuthToken*. At some point the intention is to display
 * a captcha of some type before the forms can be submitted, but this is a future effort. Part of this work
 * is to determine when user interaction should be demanded and when it is considered OK to not bother the user. That
 * choice will be determined by the type of captcha data returned when asking for the *formAuthToken*.
 */
class Login extends Component {
    constructor(props) {
        super(props);
        this.state = {
            submitting: false,
            gotFormToken: false,
            progress: "",
            activity: "loginByPassword",
            // Browser's pre-fill (autocomplete) username and password fields. This is used to determine
            // if react may not be aware of the current true values of the username and password field.
            onChangeHasFired: false,
            // Form fields. We have to set them with initial values to make them *controlled* fields.
            username: "",
            contactAddress: "",
            contactType: "email", // Not actually populated into a form field at this time.
            password: "",
            passwordVerify: "",
            verifyCode: "",
        };
        this.onSubmit = this.onSubmit.bind(this);
        this.doRequest = this.doRequest.bind(this);
        this.doTokenRequest = this.doTokenRequest.bind(this);
        this.handleInputChange = this.handleInputChange.bind(this);
        this.onSendToken = this.onSendToken.bind(this);
        this.processRequestResult = this.processRequestResult.bind(this);
    }

    componentDidMount() {}

    handleInputChange(event) {
        const target = event.target;
        const value = target.value;
        const name = target.name;
        //console.log("Changed name " + name + " to value " + value);

        this.setState({
            onChangeHasFired: true,
            [name]: value
        }, null);
    }

    /** Sends the generated token to the registration email. Note that this request will get the
     * *formAuthToken* so that calls that follow this one, do not need to call *doTokenRequest* */
    onSendToken() {
        const {activity} = this.state;
        if (activity === "registerNewEmail") {
            const {contactAddress, contactType} = this.state;
            this.doTokenRequest("POST", "/auth/newContact/sendVerify", "sendCode",
                {contactAddress, contactType});
        } else {
            const {username} = this.state;
            this.doTokenRequest("POST", "/auth/user/sendVerify",
                "sendCode", {username});
        }

    }

    onSubmit(event) {
        event.preventDefault();
        // Indicate that we are currently handling form so that we do not get double-click problems.
        this.setState({progress: "Requesting Form Token", submitting: true});
        const {activity} = this.state;

        if (activity === "loginByPassword") {
            const {username, password} = this.state;
            if (username && password) {
                this.doTokenRequest("POST", "/auth/login/byPassword", activity,
                    {username, password});
            } else {
                this.setState({progress: "Username and password must be filled out in form.",
                    submitting: false});
            }
        } else if (activity === "loginByCode") {
            const {username, formAuthToken, verifyCode} = this.state;
            this.doRequest("POST","/auth/login/byCode", activity,
                {username, formAuthToken, verifyCode});
        } else if (activity === "forgotPassword") {
            const {username, formAuthToken, password, verifyCode} = this.state;
            this.doRequest("POST", "/auth/login/byCode", activity,
                {username, formAuthToken, password, verifyCode});
        } else if (activity === "registerNewEmail") {
            const {formAuthToken, contactAddress, contactType, verifyCode} = this.state;
            this.doRequest("PUT", "/auth/user/createInitial", activity,
                {formAuthToken, contactAddress, contactType, verifyCode})
        } else if (activity === "loginSetData") {
            const {formAuthToken, userId, username, password, verifyCode} = this.state;
            this.doRequest("PUT", "/auth/user/setLoginData", activity,
                {formAuthToken, userId, username, password, verifyCode});
        }
    }

    /** Gets a form auth token, adds it to the current request data, and calls the actual desired
     * request using *doRequest*. */
    doTokenRequest(method, endpoint, activity, data) {
        this.setState({progress: "Requesting Form Token", submitting: true});
        // First get token.
        fetch("/auth/form/createToken", {credentials: 'same-origin'})
            .then(res => res.json())
            .then(
                (result) => {
                    const {captchaData} = result;
                    data.formAuthToken = result.formAuthToken;
                    data.formAuthCode = captchaData.formAuthCode;
                    this.setState({
                        formAuthToken: data.formAuthToken,
                        formAuthCode: data.formAuthCode,
                    });
                    this.doRequest(method, endpoint, activity, data);
                },
                (error) => {
                    this.setState({
                        progress: (<DnMessage error={true}>{error.message}</DnMessage>),
                        sentCode: false
                    });
                }
            )

    }

    /** Classic request handling. This code takes advantage of the fact that
     * the HTTP code is returned as an *httpCode* field in the error json, making handling the
     * response a bit simpler. */
    doRequest(method, endpoint, activity, data) {
        this.setState({progress: "Executing request", submitting: true});
        const args = {
            credentials: 'same-origin',
            headers: {
                "content-type": "application/json: charset=utf-8"
            },
            body: JSON.stringify(data),
            method: method
        };
        fetch(endpoint, args)
            .then(res => res.json())
            .then(
                (result) => {
                    let {httpCode} = result;
                    httpCode = httpCode || 200;
                    this.processRequestResult(activity, httpCode, data, result);
                },
                (error) => {
                    // No longer processing form submit.
                    this.setState({submitting: false,
                        progress: (<DnMessage error={true}>{error.message}</DnMessage>)});
                }
            )
    }

    /** Processes both successful and failed results. It takes into account the current
     * *activity* controlling the form creation. */
    processRequestResult(activity, httpCode, data, result) {
        const {username} = data;
        let newState;
        let isSuccess = false;
        if (httpCode === 200 || httpCode === 201) {
            isSuccess = true;
            if (activity.startsWith("login") || activity === "forgotPassword") {
                // Make callback function from login page.
                newState = {activity: "afterLogin", progress: "Successfully logged in."};
                const {onLogin} = this.props;
                if (onLogin) {
                    onLogin();
                }
            } else if (activity === "sendCode") {
                newState = {progress: "Sent Verification Code", sentCode: true, verifyCode: ""}
            } else if (activity === "registerNewEmail") {
                const {userId} = result;

                //alert(JSON.stringify(result));
                newState = {progress: "", activity: "loginSetData",
                    userId: userId, username: "", password: ""}
            }
        } else if (httpCode === 404) {
            newState = {progress:
                    (<DnMessage error={true}>User <i>{username}</i> is not available for doing a login.</DnMessage>)};
        } else if (httpCode === 403) {
            if (activity === "loginByPassword") {
                newState = {activity: "loginByCode", progress: "Login requires email validation."};
            } else if (activity === "registerNewEmail") {
                const {contactAddress} = this.state;
                newState = {progress: (<DnMessage error={true}>Email <i>{contactAddress}</i> is not available
                        for creating a new user.</DnMessage>)};
            } else {
                newState = {progress: <DnMessage error={true}>"Request is not allowed for
                        security reasons."</DnMessage>}
            }
        } else {
            newState = {progress: (<DnMessage error={true}>{result.message}</DnMessage>)};
        }
        // No longer submitting form.
        newState.submitting = false;
        if (!isSuccess) {
            newState.sentCode = false;
        }
        this.setState(newState)
    }

    /** Sees if the password has at least one number in it. */
    static checkForOneNumber(password) {
        if (!password) {
            return false;
        }
        const m = password.match(/(\d+)/);
        return m && m.length >= 1;
    }

    /** Tests the username in a way that matches the same test in the server code. */
    static checkInvalidUsernameChars(username) {
        if (!username || username.length === 0) {
            return false;
        }
        let c = username.charAt(0);
        if (c >= '0' || c <= '9') {
            return false;
        }
        /[^a-zA-Z0-9_]/.test(username)
    }

    /** A multi-way rendering of forms with pieces spliced together based on the current *activity*.
     * The form tries to enable buttons unless they can successfully perform their action. The
     * form also tries to indicate the user where they may have gone wrong in their data entry,
     * but all dynamically and before the form is submitted. */
    render() {
        const {username, password, passwordVerify, contactAddress, verifyCode, activity, submitting,
            onChangeHasFired, progress, formAuthToken, sentCode} = this.state;

        // If we have logged in, then render a success message.
        if (activity === "afterLogin") {
            return <DnMessage>{progress}</DnMessage>
        }

        // Whether main submit button should be disabled. This gets modified based on activity
        // and current form state. Note that until *onChangeHasFired* we do not know the
        // actual values in our form fields (for username and password at least which can get browser
        // auto-completed). Fortunately *onChangeHasFired* gets set the instant the user interacts
        // with the page in any way.
        // If we know the true state of the form and the form is currently making a call to the
        // server, then we can disable our main button by default
        let disabled = onChangeHasFired && submitting;

        let submitLabel = "Login";
        const rows = [];
        const settingPassword = activity === "forgotPassword" || activity === "loginSetData";
        let directionsMsg = <DnMessage/>;
        if (settingPassword) {
            directionsMsg = (activity === "loginSetData") ?
                <DnMessage>Choose a username and password for your new user.</DnMessage> :
                <DnMessage>Enter username, get and enter a verification code, and
                    then set a new password.</DnMessage>;
            submitLabel = (activity === "loginSetData") ? "Set Username & Password and Login" :
                "Set Password and Login";
        } else if (activity === "loginByPassword") {
            directionsMsg = <DnMessage>Enter username and password.</DnMessage>;
            submitLabel = "Login";
        } else if (activity === "loginByCode") {
            directionsMsg = <DnMessage>This browser or device is not recognized.
                Login requires using a verification code sent by email.</DnMessage>
        }
        else if (activity === "registerNewEmail") {
            directionsMsg = <DnMessage>Enter an email address and then get and enter a verification code to
                create a new user.</DnMessage>;
            submitLabel = "Register Email";
        }
        let disabledSend = submitting;
        if (activity !== "registerNewEmail") {
            // All forms have a *username* except when registering a new email address.
            rows.push(
                <tr key="usernameRow">
                    <td key="loginLabel" className="formLabel"><label className="standard">Username:</label></td>
                    <td key="loginInput" className="formInput">
                        <input name="username" type="text" value={username}
                               onChange={this.handleInputChange}/></td>
                </tr>
            );
            if (!username) {
                // Only disable if we truly know that the username is not set.
                if (onChangeHasFired) {
                    disabledSend = true;
                    disabled = true;
                }
            } else {
                let userMsg ="";
                if (activity !== "loginByPassword") {
                    if (Login.checkInvalidUsernameChars(username)) {
                        userMsg = <DnMessage>Username must not start with a number and must have
                        only alphabetic, numeric, or underscore characters.</DnMessage>
                    } else if (username.length < 3) {
                        userMsg = <DnMessage>Username must be at least three characters in length.</DnMessage>
                    } else if (username.length > 20) {
                        userMsg = <DnMessage>Username cannot be more than twenty characters in length.</DnMessage>
                    }
                }
                if (userMsg) {
                    disabledSend = true;
                    disabled = true;
                    rows.push(
                        <tr key="usernameErrMsg">
                            <td key="usernameErrMsgRow" colSpan="2">{userMsg}</td>
                        </tr>
                    )
                }
            }
        } else {
            // Doing the first form element for creating a new user.
            rows.push(
                <tr key="contactRow">
                    <td key="contactLabel" className="formLabel"><label className="standard">Email:</label></td>
                    <td key="contactInput" className="formInput">
                        <input name="contactAddress" type="text" size="40" value={contactAddress}
                               onChange={this.handleInputChange}/></td>
                </tr>
            );
            if (!contactAddress || contactAddress.length < 3) {
                disabled = true;
            } else {
                const index = contactAddress.indexOf("@");
                if (index < 0 || index >= contactAddress.length - 1) {
                    disabled = true;
                }
            }
            if (disabled) {
                disabledSend = true;
            }
            if (!formAuthToken) {
                disabled = true;
            }
        }

        let addPasswordRows = true;
        if (activity === "loginByCode" || activity === "registerNewEmail" || activity === "forgotPassword") {
            // We are doing verification by email sent verification code. Correctly entering the code allows us
            // to successfully submit the full form.
            const sendCodeButtonText = (sentCode) ? "Create and Email Code Again" : "Create and Email Code";
            rows.push(
                <tr key="verifyCodeRow">
                    <td key="verifyCodeInput" className="formLabel"><label className="standard">Code:</label></td>
                    <td key="verifyCodeLabel" className="formInput">
                        DN-<input name="verifyCode" type="text" size="8" value={verifyCode}
                               disabled={!formAuthToken} onChange={this.handleInputChange}/>
                     </td>
                </tr>,
                <tr key="sendCodeRow">
                    <td key="sendCodeCell" colSpan="2">
                        &nbsp;&nbsp;<button onClick={this.onSendToken} disabled={disabledSend}>
                            {sendCodeButtonText}
                        </button>
                    </td>
                </tr>,
                <tr key="verifySendExplain"><td key="info" colSpan="2">
                    The <i>{sendCodeButtonText}</i> button sends a generated eight character code to your
                    registration email address that needs to be entered in the <i>Code</i> field above. Because
                    of issues with the free mailgun IP address we are using for sending email, emails to Yahoo
                    are currently bounced as suspected spam. So if you wish to register or login using email,
                    you cannot use Yahoo.
                </td></tr>
            );
            if (!verifyCode || verifyCode.length !== 8) {
                addPasswordRows = false;
                disabled = true;
            }
        } else if (activity === "loginByPassword") {
            if (!password && onChangeHasFired) {
                disabled = true;
            }
        }

        // Password handling is complicated because on some forms there is also a password verify field
        // and there are rules for what constitutes a valid password. We do not want to enable
        // the main form button until the password passes all tests. Note: if doing verification by code,
        // the password fields are not presented until a code is entered.
        let passwordRows = null;
        if (addPasswordRows && (settingPassword || activity === "loginByPassword")) {
            passwordRows = [];
            const passwordLabel = (activity === "loginByPassword") ? "Password" : "New Password";
            passwordRows.push(
                <tr key="passwordRow">
                    <td key="passwordInput" className="formLabel"><label className="standard">{passwordLabel}:</label></td>
                    <td key="passwordLabel" className="formInput">
                        <input name="password" type="password" value={password}
                               onChange={this.handleInputChange}/></td>
                </tr>
            );
            if (settingPassword) {
                passwordRows.push(
                    <tr key="passwordVerifyRow">
                        <td key="passwordVerifyInput" className="formLabel">
                            <label className="standard">Verify Password:</label></td>
                        <td key="passwordVerifyLabel" className="formInput">
                            <input name="passwordVerify" type="password" value={passwordVerify}
                                   onChange={this.handleInputChange}/></td>

                    </tr>
                );
                let passwdMsg = "";
                if (!Login.checkForOneNumber(password) || !/[^a-zA-Z0-9]/.test(password)) {
                    passwdMsg = <DnMessage>The password must have one numeric character and one special
                        (non-alphanumeric) character in it.</DnMessage>
                } else if (!password || password.length < 6) {
                    passwdMsg = <DnMessage>The password must be at least six characters in length.</DnMessage>
                } else if (password.length > 16) {
                    passwdMsg = <DnMessage>The password cannot have more than sixteen characters in it.</DnMessage>
                } else if (/\s/.test(password)) {
                    passwdMsg = <DnMessage>The password cannot have whitespace in it.</DnMessage>
                } else if (!passwordVerify || passwordVerify !== password) {
                    passwdMsg = <DnMessage>The verify password must be equal to the password.</DnMessage>
                }
                if (passwdMsg) {
                    disabled = true;
                    passwordRows.push(
                        <tr key="passwordErrMsg">
                            <td key="passwordErrMsgRow" colSpan="2">{passwdMsg}</td>
                        </tr>
                    )
                }
            }
        }
        if (passwordRows) {
            rows.push(passwordRows);
        }

        // Links to take us to the different entry activities supported by this object.
        const links = (
            <div>
                <span key="loginLink" className="clickText"
                      onClick={() => this.setState({activity:'loginByPassword', progress:""})}>[Login]</span>
                <span key="resetPasswordLink" className="clickText"
                      onClick={() => this.setState({activity:"forgotPassword", password:"", passwordVerify:"",
                      progress: "", sentCode: false, verifyCode: ""})}>[Forgot Password]</span>
                <span key="registerNewEmailLink" className="clickText"
                      onClick={() => this.setState({activity:"registerNewEmail", password:"",
                          passwordVerify:"", progress: "", sentCode: false, verifyCode: ""})}>[Register]</span>
            </div>
        );

        return (
            <div>
                {directionsMsg}<p/>
                <form key="mainForm" onSubmit={this.onSubmit}>
                    <table className="loginFormTable">
                        <tbody>
                        {rows}
                        </tbody>
                    </table>
                    {(addPasswordRows && passwordRows && sentCode) ? "" : progress}
                     <p key="submitButton"><input type="submit" value={submitLabel} disabled={disabled}/></p>
                </form>
                <div key="activityLinks" className="activityLinks">{links}</div>
            </div>
        );
    }
}