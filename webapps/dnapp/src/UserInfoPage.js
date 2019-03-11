import React, {Component} from 'react';
import {Redirect} from "react-router-dom";

import UserRegistrationInfo from 'dncore/views/UserRegistrationInfo'
import Client from 'dncore/api/Client'
import {dnl} from 'dncore/api/Functions';

class UserInfoPage extends Component {
  render() {
    if (!Client.getIsLoggedIn()) {
      return <Redirect to={dnl("/portal/login")}/>
    }
    return (
      <div className="presentBox">
        <UserRegistrationInfo/>
      </div>
    )
  }
}

export default UserInfoPage;