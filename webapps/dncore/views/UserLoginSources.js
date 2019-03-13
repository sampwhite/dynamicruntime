import React, {Component} from 'react';
import Message from './Message';
import Client from '../api/Client'
import {timeAgo} from '../api/Functions'

class UserLoginSources extends Component {
  constructor(props) {
    super(props);
    this.state = {
      loginSources: {}
    };
    this.handleProfileDataUpdate = this.handleProfileDataUpdate.bind(this);
  }

  componentDidMount() {
    // Connect us to the user profile data cache.
    Client.registerForProfileUpdate("UserLoginSources", this.handleProfileDataUpdate);
    this.handleProfileDataUpdate(Client.getCurrentProfileData());
  }

  componentWillUnmount() {
    Client.unregisterForProfileUpdate("UserLoginSources");
  }

  handleProfileDataUpdate(userInfo) {
    const {userProfileData} = userInfo;
    const {loginSources} = userProfileData;

    this.setState({loginSources});
  }

  static mkDisplayRow(val1, val2) {
    return (<tr><td className="leftSourcesCell">{val1}</td><td className="rightSourcesCell">{val2}</td></tr>)
  }

  static extractSourcesInfo(loginSources) {
    const {capturedIps} = loginSources;
    const srcList = [];
    if (!capturedIps) {
      return [];
    }
    for (const cIp of capturedIps) {
      const {ipAddress, geoLocation} = cIp;
      const {userAgents} = cIp;
      const machines = {};
      for (const ua of userAgents) {
        // Use our understanding of how user agent information is formatted in user profile data.
        // There is no explicit schema contract, but the format can be deduced by looking at example data.
        const uas = ua.split("@");
        const dStrs = uas[0].split("#");
        const lastLoginDate = new Date(Date.parse(dStrs[1]));
        const uaStr = uas[1];
        const machineName = UserLoginSources.extractMachineName(uaStr);
        const existing = machines[machineName];
        if (existing) {
          const {lastLoginDate: otherDate} = existing;
          const newLastLoginDate = lastLoginDate > otherDate ? lastLoginDate : otherDate;
          machines[machineName] = {machineName, lastLoginDate: newLastLoginDate};
        } else {
          machines[machineName] = {machineName, lastLoginDate};
        }
      }
      const uaList = Object.values(machines).sort((a,b) => {
          const {lastLoginDate: d1} = a;
          const {lastLoginDate: d2} = b;
          if (d1 > d2) return -1;
          if (d1 < d2) return 1;
          return 0;
        }
      );
      const gl = geoLocation ? UserLoginSources.formatGeoLocation(geoLocation) : "Location Unavailable";
      srcList.push({ip: ipAddress, geoLocation: gl, uaList});
    }
    // Code looks better if we capture value into local variable before returning it, so we suppress
    // IntelliJ's objection. Also we can examine result with console log more easily as well.
    // noinspection UnnecessaryLocalVariableJS
    const sortedList = srcList.sort((a,b) => {
      const {uaList: ua1} = a;
      const {uaList: ua2} = b;
      const isEmpty1 = !ua1 || ua1.length === 0;
      const isEmpty2 = !ua2 || ua2.length === 0;
      if (isEmpty1) {
        return (isEmpty2) ? 0 : 1;
      } else if (isEmpty2) {
        return -1;
      }
      // Sort from most recent to least recent date.
      const {lastLoginDate: d1} = ua1[0];
      const {lastLoginDate: d2} = ua2[0];
      if (d1 > d2) return -1;
      if (d1 < d2) return 1;
      return 0;
    });
    return sortedList;
  }

  // Simplify machine and OS string into something simpler for user.
  static MACHINE_DETECTIONS = {windows: "Windows",  iphone: "iPhone",
    droid: "Android", linux: "Linux", cros: "Chrome OS", mac: "Mac"};

  static extractMachineName(userAgent) {
    const index1 = userAgent.indexOf("(");
    let machineName = "Unknown OS";
    if (index1 > 0) {
      const index2 = userAgent.indexOf(")", index1);
      if (index2 > 0) {
        machineName = userAgent.substring(index1 + 1, index2);
        const md = machineName.toLowerCase();
        for (const mKey of Object.keys(UserLoginSources.MACHINE_DETECTIONS)) {
          if (md.includes(mKey)) {
            machineName = UserLoginSources.MACHINE_DETECTIONS[mKey];
            break;
          }
        }
      }
    }
    return machineName;
  }

  static formatGeoLocation(geoLocation) {
    // 0 = Country
    // 1 = State
    // 2 = City
    // 3 = Postal Code
    return geoLocation[2] + ", " + geoLocation[1] + ", " + geoLocation[0];
  }


  render() {
    const {loginSources} = this.state;
    const sourceInfo = UserLoginSources.extractSourcesInfo(loginSources);
    const rows = sourceInfo.map((source) => {
      const {ip, geoLocation, uaList} = source;
      const cell1 = <div>{ip}<br/><span className="statusMsg">{geoLocation}</span></div>;
      const cell2 = uaList.map((ua) => {
        const {machineName, lastLoginDate} = ua;
        return (<div className="machineName">{machineName} <span className="sourceTime">({timeAgo(lastLoginDate)})</span></div>)
      });
      return UserLoginSources.mkDisplayRow(cell1, cell2);
    });
    return (
      <div className="formBox infoBox">
        <div className="infoHeader">
          Last Login Locations<br/>
          <Message>Locations are only approximate and are based on the IP address.</Message>
        </div>
        <div className="infoBody">
          <table className="loginSourcesTable">
            <thead>
              <th>Location</th><th>Device/OS</th>
            </thead>
            <tbody>
            {rows}
            </tbody>
          </table>
        </div>
      </div>
    )
  }
}

export default UserLoginSources;