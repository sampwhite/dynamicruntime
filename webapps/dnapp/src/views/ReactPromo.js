import React, {Component} from "react";
import "./ReactPromo.css"
import logo from "./logo.svg";

class ReactPromo extends Component {
  render() {
    return (
      <div className="ReactPromo">
        <header className="ReactPromo-header">
          <img src={logo} className="ReactPromo-logo" alt="logo"/>
          {this.props.children}
        </header>
      </div>
    );
  }
}

export default ReactPromo;
