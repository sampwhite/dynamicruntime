import React, {Component} from 'react';
import Login from 'dncore/views/Login';
import {Redirect} from "react-router-dom";

import {dnl} from 'dncore/api/Functions';

class LoginPage extends Component {
  constructor(props) {
    super(props);
    this.state = {didLogin: false};
    this.onLogin = this.onLogin.bind(this);
  }

  onLogin() {
    this.setState({didLogin: true});
  }

  render() {
    const {didLogin} = this.state;
    if (didLogin) {
      return (<Redirect to={dnl("/portal")}/>);
    }
    return (
      <div className="presentBox">
        <Login onLogin={this.onLogin}/>
      </div>
    )
  }
}

export default LoginPage;