import React, {Component} from 'react';
import Message from './Message';
import Login from './Login';
import Client from '../api/Client'

/** See *UserProfile* in the dynamicruntime  user interface. */
class UserRegistrationInfo extends Component {
  constructor(props) {
    super(props);
    this.state = {
      progress: "",
      isError: false,
      currentPassword: "",
      password: "",
      passwordVerify: "",
      activity: "showInfo"
    };
    this.handleInputChange = this.handleInputChange.bind(this);
    this.handleProfileDataUpdate = this.handleProfileDataUpdate.bind(this);
    this.onSubmit = this.onSubmit.bind(this);
    this.doRequest = this.doRequest.bind(this);
    this.processRequestResult = this.processRequestResult.bind(this);
  }

  componentDidMount() {
    // Connect us to the user profile data cache.
    Client.registerForProfileUpdate("UserRegistration", this.handleProfileDataUpdate);
    this.handleProfileDataUpdate(Client.getCurrentProfileData());
  }

  componentWillUnmount() {
    Client.unregisterForProfileUpdate("UserRegistration");
  }

  handleInputChange(event) {
    const target = event.target;
    const value = target.value;
    const name = target.name;
    //console.log("Changed name " + name + " to value " + value);

    this.setState({
      [name]: value
    }, null);
  }

  handleProfileDataUpdate(userInfo) {
    const {username, userProfileData} = userInfo;
    const {contacts} = userProfileData;
    const regContact = contacts.find((c) => c["contactUsage"] === "registration");
    const {contactAddress: email} = regContact;
    this.setState({email, username, userInfo});
  }

  onSubmit(event) {
    event.preventDefault();

    const {activity} = this.state;

    if (activity === "changePassword") {
      this.setState({progress: "Sending password change request", submitting: true});
      const {userId, currentPassword, password} = this.state;
      this.doRequest("PUT", "/user/self/setData", activity,
        {userId, currentPassword, password});
    }
  }

  doRequest(method, endpoint, activity, data) {
    Client.doJsonFetch(method, endpoint, data,
      (httpCode, result) => {
        this.processRequestResult(activity, httpCode, data, result);
      },
      (error) => {
        // No longer processing form submit.
        this.setState({submitting: false, progress: error.message, isError: true});
      }
    );
  }

  processRequestResult(activity, httpCode, data, result) {
    let newState;
    if (httpCode === 200 || httpCode === 201) {
      newState = {progress: "Password has been updated.", activity: "showInfo",
        currentPassword: "", password: "", passwordVerify: "", isError: false}

    } else if (httpCode === 403) {
      newState = {progress: "Request is not allowed for security reasons.", isError: true}
    } else {
      newState = {progress: result.message, isError: true};
    }
    // No longer submitting form.
    newState.submitting = false;
    this.setState(newState)
  }

  render() {
    const {username, currentPassword, password, passwordVerify, email,
      activity, submitting, progress, isError} = this.state;
    const profileRows = [];
    profileRows.push(
      Login.mkNameValueRow("email", "Email", email),
      Login.mkNameValueRow("username", "Username", username)
    );

    const doPasswordEdit = (activity === "changePassword");
    const linkLabel = doPasswordEdit ? "[Hide Change Password]" : "[Change Password]";
    const newActivity = doPasswordEdit ? "showInfo" : "changePassword";
    const links = (
      <div className="activityLinks">
                <span key="passwordChange" className="clickText"
                      onClick={() => this.setState({activity:newActivity, progress:""})}>{linkLabel}</span>
      </div>
    );
    let disabled = false;
    let passwordRows = [];
    let submitButton = "";
    if (doPasswordEdit) {
      ({passwordRows, disabled} =  Login.createPasswordRows(activity, true, currentPassword,
        password, passwordVerify, this.handleInputChange));
      submitButton = <p key="submitButton" className="profileSubmitButton">
        <input type="submit" value="Change Password" disabled={disabled || submitting}/></p>
    }

    return (
      <div className="formBox infoBox">
        <div className="infoHeader">User Registration Information</div>
        <div className="passwordForm infoBody">
          <form key="mainForm" className="passwordForm" onSubmit={this.onSubmit}>
            <table className="profileFormTable">
              <tbody>
                {profileRows}
                {passwordRows}
              </tbody>
            </table>
            {submitButton}
          </form>
          <Message>{links}</Message>
          <Message error={isError} status>{progress}</Message>
        </div>
      </div>
    )

  }
}

export default UserRegistrationInfo;