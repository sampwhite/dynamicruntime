import React, {Component} from 'react';
import {NavLink} from "react-router-dom";
import Client from 'dncore/api/Client';
import {dnl} from 'dncore/api/Functions';
import dnLogo from './dnlogo.ico';

class MainLayout extends Component {
  constructor(props) {
    super(props);
    const profileData = Client.getCurrentProfileData();
    const isLoggedIn = Client.getIsLoggedIn();

    this.state = {callMade: false, profileData: profileData, isLoggedIn: isLoggedIn};
    this.onUpdateProfile = this.onUpdateProfile.bind(this);
    Client.registerForProfileUpdate("MainLayout", this.onUpdateProfile);
    Client.requestProfileDataFromServer();
  }

  onUpdateProfile(data) {
    this.setState({callMade: true, profileData: data, isLoggedIn: Client.getIsLoggedIn()});
  }

  static mkNavItem(path, label) {
    const exact = (path === "/portal");
    return (
      <li key={path} className="navItem">
        <NavLink exact={exact} to={dnl(path)} activeClassName="activeLink" className="navLink">
          {label}</NavLink>
      </li>
    );
  }

  render() {
    const {callMade, isLoggedIn, profileData} = this.state;
    const {username} = profileData;
    const header = (isLoggedIn) ? <div className="usernameHeader">
      <img src={dnLogo} alt="Dynamic Runtime Logo" className="loginIcon"/>{username}
    </div> : "";
    if (!callMade) {
      return <main className="actionInProgress">Loading page...</main>;
    }
    const rows = [];
    rows.push(MainLayout.mkNavItem("/portal", "Home"));
    rows.push(MainLayout.mkNavItem("/portal/healthInfo", "Health Info"));
    if (!isLoggedIn) {
      rows.push(MainLayout.mkNavItem("/portal/login", "Login"));
    } else {
      rows.push(MainLayout.mkNavItem("/portal/userInfo", "User Info"));
      rows.push(MainLayout.mkNavItem("/portal/logout", "Logout"));
    }

    return (
      <div className="layout">
        <header className="primary-header"/>
        <aside className="primary-aside">
          {header}
          <ul className="navList">
            {rows}
          </ul>
        </aside>
        <main>
          {this.props.children}
        </main>
      </div>
    )
  }
}

export default MainLayout;