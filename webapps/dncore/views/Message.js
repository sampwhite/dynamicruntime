import React, {Component} from 'react';

class DnMessage extends Component {
  render() {
    const {error, warn, status} = this.props;

    const className = (error) ? "errorMsg" :
      ((warn) ? "warnMsg" : ((status) ? "statusMsg" : "infoMsg"));
    return <div className={className}>{this.props.children}</div>
  }
}

export default DnMessage;