import React, {Component} from 'react';
import {BrowserRouter as Router, Route, Switch, Redirect} from "react-router-dom";
import './App.css';
import NodeHealthPage from './NodeHealthPage'
import LoginPage from './LoginPage'
import UserInfoPage from './UserInfoPage';
import LogoutPage from './LogoutPage'
import Home from './Home'
import MainLayout from './MainLayout'
import {dnl} from 'dncore/api/Functions';

class App extends Component {
  render() {
    return (
      <div className="App">
        <Router>
          <MainLayout>
            <Switch>
              <Route exact path="/portal" component={Home}/>
              <Route path="/portal/healthInfo" component={NodeHealthPage}/>
              <Route path="/portal/login" component={LoginPage}/>
              <Route path="/portal/userInfo" component={UserInfoPage}/>
              <Route path="/portal/logout" component={LogoutPage}/>
              <Redirect to={dnl("/portal")}/>
            </Switch>
          </MainLayout>
        </Router>
      </div>
    );
  }
}

export default App;
