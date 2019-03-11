import React, {Component} from 'react';
import Client from '../api/Client';

class Logout extends Component {
  constructor(props) {
    super(props);
    const {onLogout} = props;
    this.onLogout = onLogout;
    this.doLogout = this.doLogout.bind(this);
  }

  componentDidMount() {
    this.doLogout();
  }

  doLogout() {
    Client.doJsonFetch("POST", "/auth/logout", {}, (httpCode, data) => {
        Client.setCurrentProfileData({});
        this.onLogout(data);
      }, (error) => {});
  }

  render() {
    return <div className="actionInProgress">Doing logout...</div>;
  }
}

export default Logout;