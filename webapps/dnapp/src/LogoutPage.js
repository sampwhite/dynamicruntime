import React, {Component} from 'react';
import Logout from 'dncore/views/Logout';
import {Redirect} from "react-router-dom";

import {dnl} from 'dncore/api/Functions';

class LogoutPage extends Component {
  constructor(props) {
    super(props);
    this.state = {didLogout: false};
    this.onLogout = this.onLogout.bind(this);
  }

  onLogout() {
    this.setState({didLogout: true});
  }

  render() {
    const {didLogout} = this.state;
    if (didLogout) {
      return (<Redirect to={dnl("/portal/login")}/>);
    }
    return (
        <Logout onLogout={this.onLogout}/>
    )
  }
}

export default LogoutPage;