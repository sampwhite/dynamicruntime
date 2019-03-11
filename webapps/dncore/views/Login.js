import React, {Component} from 'react';
import Message from './Message';
import Client from '../api/Client'

/** See the version of this implemented in *dncore.js* for extensive documentation. */
class Login extends Component {
  constructor(props) {
    super(props);
    this.state = {
      submitting: false,
      gotFormToken: false,
      progress: "",
      showHelpText: false,
      isError: false,
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
  };

  //
  // Request handling functions.
  //

  /** Gets a form auth token, adds it to the current request data, and calls the actual desired
   * request using *doRequest*. */
  doTokenRequest(method, endpoint, activity, data) {
    this.setState({progress: "Requesting Form Token", submitting: true});
    // First get token.
    Client.doJsonGet("/auth/form/createToken",
      (httpCode, result) => {
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
          progress: error.message,
          isError: true,
          sentCode: false
        });
      }
    );
  }

  /** A standard request processor. */
  doRequest(method, endpoint, activity, data) {
    this.setState({progress: "Executing request", submitting: true});
    Client.doJsonFetch(method, endpoint, data,
      (httpCode, result) => {
        this.processRequestResult(activity, httpCode, data, result);
      },
      (error) => {
        // No longer processing form submit.
        this.setState({submitting: false,
          progress: error.message, isError: true});
      }
    );
  }

  /** Processes both successful and failed results. It takes into account the current
   * *activity* controlling the form creation. */
  processRequestResult(activity, httpCode, data, result) {
    const {username} = data;
    let newState;
    let isSuccess = false;
    let didLogin = false;
    if (httpCode === 200 || httpCode === 201) {
      isSuccess = true;
      if (activity.startsWith("login") || activity === "forgotPassword") {
        // Tell our global cache about our info we have on the logged in user.
        Client.setCurrentProfileData(result);

        // Make callback function from login page.
        newState = {activity: "afterLogin", progress: "Successfully logged in.", isError: false};
        didLogin = true;
      } else if (activity === "sendCode") {
        newState = {progress: "Sent Verification Code", sentCode: true, verifyCode: "", isError: false}
      } else if (activity === "registerNewEmail") {
        const {userId} = result;

        //alert(JSON.stringify(result));
        newState = {progress: "", activity: "loginSetData",
          userId: userId, username: "", password: ""}
      }
      else {
        newState = {progress: "Invalid activity.", isError: true}
      }
    } else if (httpCode === 404) {
      newState = {progress: (<span>User <i>{username}</i> is not available for a login.</span>),
        isError: true};
    } else if (httpCode === 403) {
      if (activity === "loginByPassword") {
        newState = {activity: "loginByCode", progress: "Login requires email validation."};
      } else if (activity === "registerNewEmail") {
        const {contactAddress} = this.state;
        newState = {progress: (<span>Email <i>{contactAddress}</i> is not available
            for creating a new user.</span>), isError: true};
      } else {
        newState = {progress: "Request is not allowed for security reasons.", isError: true}
      }
    } else {
      newState = {progress: result.message, isError: true};
    }
    // No longer submitting form.
    newState.submitting = false;
    if (!isSuccess) {
      newState.sentCode = false;
    }
    this.setState(newState);
    // Call the callback function after we set state, so we do not get unmount warnings.
    if (didLogin) {
      const {onLogin} = this.props;
      if (onLogin) {
        onLogin();
      }
    }
  }

  //
  // Functions that react to changes in form and actions taken by user.
  //

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

  /** A multi-way rendering of forms with pieces spliced together based on the current *activity*.
   * The form tries to enable buttons unless they can successfully perform their action. The
   * form also tries to indicate the user where they may have gone wrong in their data entry,
   * but all dynamically and before the form is submitted. */
  render() {
    const {username, password, passwordVerify, contactAddress, verifyCode, activity, submitting,
      isError, showHelpText, onChangeHasFired, progress, formAuthToken, sentCode} = this.state;

    // If we have logged in, then render a success message.
    if (activity === "afterLogin") {
      return <div className="actionInProgress">{progress}</div>
    }

    // Whether main submit button should be disabled. This gets modified based on activity
    // and current form state. Note that until *onChangeHasFired* we do not know the
    // actual values in our form fields (for username and password at least which can get browser
    // auto-completed). Fortunately *onChangeHasFired* gets set the instant the user interacts
    // with the page in any way.
    // If we know the true state of the form and the form is currently making a call to the
    // server, then we can disable our main button by default
    let disabled = onChangeHasFired && submitting;

    let submitLabel;
    let title;
    let directionsMsg = <Message/>;
    let bottomLink = "";
    const rows = [];
    const settingPassword = activity === "forgotPassword" || activity === "loginSetData";
    if (settingPassword) {
      if (activity === "loginSetData") {
        submitLabel = "Set Username & Password and Login";
        title = "Enter Your New Username and Password";
      } else {
        directionsMsg = <Message>Enter username, get and enter a verification code, and
          then set a new password.</Message>;
        submitLabel = "Set Password and Login";
        title = "Forgot Password"
      }
    } else if (activity === "loginByPassword") {
      submitLabel = "Login";
      title = "Welcome Back";
      directionsMsg =
        <Message>New User?&nbsp;
          <span key="registerNewEmailLink" className="clickText"
                onClick={() => this.setState({activity:"registerNewEmail", password:"",
                  passwordVerify:"", progress: "", sentCode: false, verifyCode: "", isError: false})}>Sign up here</span>
        </Message>;
      bottomLink =
        <Message>
          Forgot your&nbsp;
          <span key="resetPasswordLink" className="clickText"
                onClick={() => this.setState({activity:"forgotPassword", password:"", passwordVerify:"",
                  progress: "", sentCode: false, verifyCode: "", isError: false})}>Password?</span>
        </Message>
    } else if (activity === "loginByCode") {
      title = "Login By Verification Code";
      directionsMsg = <Message>This browser or device is not recognized.
        Login requires using a verification code sent by email.</Message>
    }
    else if (activity === "registerNewEmail") {
      title = "Enter Email for New User";
      directionsMsg = <Message>Enter an email address and then get and enter a verification code to
        create a new user.</Message>;
      submitLabel = "Register Email";
    }
    let disabledSend = submitting;
    if (activity !== "registerNewEmail") {
      // All forms have a *username* except when registering a new email address.
      rows.push(
        Login.mkLoginField("username", "Username", username, this.handleInputChange)
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
            userMsg = <Message warn>Username must not start with a number and must have
              only alphabetic, numeric, or underscore characters.</Message>
          } else if (username.length < 3) {
            userMsg = <Message warn>Username must be at least three characters in length.</Message>
          } else if (username.length > 20) {
            userMsg = <Message warn>Username cannot be more than twenty characters in length.</Message>
          }
        }
        if (userMsg) {
          disabledSend = true;
          disabled = true;
          rows.push(
            Login.mkLoginRptRow("usernameErrMsg", userMsg)
          )
        }
      }
    } else {
      // Doing the first form element for creating a new user.
      rows.push(
        Login.mkLoginField("contactAddress", "Registration Email",
          contactAddress, this.handleInputChange)
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
      const sendCodeButtonText = (sentCode) ? "Create Code Again" : "Create and Email Code";
      const codeLabel =
        <span>
          Verification Code
        </span>;
      const codeContent =
        <div className="codeContent">
          <button className="verifyCodeButton" onClick={this.onSendToken} disabled={disabledSend}>
            {sendCodeButtonText}</button>&nbsp;&nbsp;&nbsp;&nbsp;
          <span className="inputPrefix">Code DN-</span>
          <input name="verifyCode" className="verifyCode" type="text" size="8" value={verifyCode}
          disabled={!formAuthToken} onChange={this.handleInputChange}/>
        </div>;
      const helpLink = <span key="loginLink" className="clickText infoLink"
            onClick={() => this.setState({showHelpText: !showHelpText})}><br/>?</span>;

      rows.push(
        Login.mkLabeledContent("verifyCodeRow", codeLabel, codeContent, null, helpLink)
      );
      if (showHelpText) {
        const toggledDes =
          <Message>
            The <i>{sendCodeButtonText}</i> button sends a generated eight character code to your
            registration email address that needs to be entered in the <i>Code</i> field above. Because
            of issues with the free mailgun IP address we are using for sending email, emails to Yahoo
            are currently bounced as suspected spam. So if you wish to register or login using email,
            you cannot use Yahoo.&nbsp;
            <span key="loginLink" className="clickText"
                  onClick={() => this.setState({showHelpText: false})}>[Hide]</span>
          </Message>;
        rows.push(
          Login.mkLoginRptRow("codeDes", toggledDes)
        );
      }
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
      ({passwordRows, disabled} = Login.createPasswordRows(activity, settingPassword, null, password,
        passwordVerify, this.handleInputChange));
    }
    if (passwordRows) {
      rows.push(passwordRows);
    }
    if (!bottomLink) {
      bottomLink = <Message>
        Back to <span key="loginLink" className="clickText"
                      onClick={() => this.setState({activity:'loginByPassword', progress:"", isError: false})}>
          Login</span>
      </Message>
    }

    return (
      <div className="formBox infoBox">
        <div className="infoHeader">
          {title}<br/>
          {directionsMsg}
        </div>
        <div className="loginForm infoBody">
          <form key="mainForm" className="loginForm" onSubmit={this.onSubmit}>
            <table className="loginFormTable" align="center">
              <tbody>
              {rows}
              </tbody>
            </table>
            {(addPasswordRows && passwordRows && sentCode) ? "" :
              <Message status={!isError} error={isError}>{progress}</Message>}
            <p key="submitButton"><input type="submit" value={submitLabel} disabled={disabled}/></p>
          </form>
          <div key="activityLinks" className="activityLinks">{bottomLink}</div>
        </div>
      </div>
    );
  }

  static mkLoginField(inputName, label, curValue, handleInputChange, inputType, extraClassName, helpLink) {
    const iType = inputType || "input";
    const inputContent =
      <input name={inputName} type={iType} value={curValue} size="40" onChange={handleInputChange}/>;
    return Login.mkLabeledContent(inputName + "Row", label, inputContent, extraClassName, helpLink);
  }

  static mkLabeledContent(rowName, label, content, extraClassName, helpLink) {
    const cName = extraClassName ? " " + extraClassName : "";
    const hLink = helpLink || "";
    return (
      <tr key={rowName}>
        <td width="20%"/>
        <td colSpan="2">
          <label><span className={"headerLabel" + cName}>{label}</span><br/>
            {content}
          </label>
        </td>
        <td width="20%">{hLink}</td>
      </tr>
    )
  }

  static mkNameValueRow(inputName, label, curValue) {
    return (
      <tr key={inputName + "Row"}>
        <td width="20%"/>
        <td key={inputName + "Label"}><span className="label">{label}:</span></td>
        <td key={inputName + "Value"}>{curValue}</td>
        <td width="20%"/>
      </tr>
    )
  }

  static mkLoginRptRow(key, msg) {
    return (<tr key={key}><td colSpan="4" className="msgRow">{msg}</td></tr>)
  }

  static createPasswordRows(activity, settingPassword, currentPassword, password, passwordVerify,
                            handleInputChange) {
    const passwordRows = [];
    const passwordLabel = (activity === "loginByPassword") ? "Password" : "New Password";
    let disabled = false;
    const isChange = (activity === "changePassword");
    if (isChange) {
      passwordRows.push(
        Login.mkLoginField("currentPassword", "Current Password", currentPassword, handleInputChange,
          "password", "currentPassword")
      );
    }
    passwordRows.push(
      Login.mkLoginField("password", passwordLabel, password, handleInputChange, "password")
    );
    if (settingPassword) {
      passwordRows.push(
        Login.mkLoginField("passwordVerify", "Verify Password", passwordVerify, handleInputChange,
          "password")
      );
      let passwdMsg = "";
      const isNewUser = (activity === "loginSetData");
      const passwordEntity = (isNewUser) ? "password" : "new password";

      if (!Login.checkForOneNumber(password) || !/[^a-zA-Z0-9]/.test(password)) {
        passwdMsg = <Message warn>The {passwordEntity} must have one numeric character and one special
          (non-alphanumeric) character in it.</Message>
      } else if (!password || password.length < 6) {
        passwdMsg = <Message warn>The {passwordEntity} must be at least six characters in length.</Message>
      } else if (password.length > 16) {
        passwdMsg = <Message warn>The {passwordEntity} cannot have more than sixteen characters in it.</Message>
      } else if (/\s/.test(password)) {
        passwdMsg = <Message warn>The {passwordEntity} cannot have whitespace in it.</Message>
      } else if (!passwordVerify || passwordVerify !== password) {
        passwdMsg = <Message warn>The verify password must be equal to the {passwordEntity}.</Message>
      } else if (isChange && !currentPassword) {
        passwdMsg = <Message warn>Your current password is required in order to change your password.</Message>
      }
      if (passwdMsg) {
        disabled = true;
        passwordRows.push(
          Login.mkLoginRptRow("passwordErrMsg", passwdMsg)
        )
      }
    }
    return {disabled, passwordRows};
  }

  /** Sees if the password has at least one number in it. */
  static checkForOneNumber(password) {
    if (!password) {
      return false;
    }
    const m = password.match(/(\d+)/);
    return m && m.length >= 1;
  }

  /** Tests the username for invalid characters in a way that matches the same test in the server code. */
  static checkInvalidUsernameChars(username) {
    if (!username || username.length === 0) {
      return false;
    }
    let c = username.charAt(0);
    if (c >= '0' && c <= '9') {
      return true;
    }
    return /[^a-zA-Z0-9_]/.test(username)
  }
}

export default Login;