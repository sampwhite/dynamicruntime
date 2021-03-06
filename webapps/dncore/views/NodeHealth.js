import React, {Component} from 'react';
import Client from '../api/Client'
import moment from 'moment';
import './dncore.css'

class NodeHealth extends Component {
  constructor(props) {
    super(props);
    this.state = {
      isLoaded: false,
      message: "Page loading...",
      refreshCount: 0,
      refreshing: true,
      nodes: {}
    };
    this.handleHealthData = this.handleHealthData.bind(this);
    this.doUpdate = this.doUpdate.bind(this);
  }

  static mkNodeDisplay(data) {
    const {nodeId, currentTime, nodeStartTime, uptime} = data;

    return (
      <div  key={nodeId} className="nodeInfo infoBox">
        <div key="header" className="infoHeader">
          <span key="l" className="label">Health Info For Node:</span>&nbsp;
          <span key="v" className="titleValue">{nodeId}</span>
        </div>
        <div key="body" className="infoBody">
          <table>
            <tbody>
              {NodeHealth.mkNameValue("Current Node Time:", NodeHealth.dt(currentTime))}
              {NodeHealth.mkNameValue("Node Start Time:", NodeHealth.dt(nodeStartTime))}
              {NodeHealth.mkNameValue("Up Time:", uptime)}
            </tbody>
          </table>
        </div>
      </div>
    )
  }

  static mkNameValue(name, value) {
    return (
      <tr key={name}>
        <td className="label" key="l">{name}</td>
        <td key="v">{value}</td>
      </tr>
    );
  }

  static dt(dateStr) {
    const nd = Date.parse(dateStr);
    return moment(nd).format('MMM Do YYYY, h:mm:ss a');
  }

  componentDidMount() {
    Client.doJsonGet("/health/info",
      this.handleHealthData,
      (error) => {
        this.setState({
          isLoaded: true,
          message: error.message
        });
      }
    );
    this.setInterval = setInterval(this.doUpdate, 2000);
  }

  componentWillUnmount() {
    if (this.setInterval) {
      clearInterval(this.setInterval);
    }
  }

  doUpdate() {
    Client.doJsonGet("/health/info",
      this.handleHealthData,
      (error) => {
        this.setState({
          isLoaded: true,
          message: error.message
        });
      }
    );
  }

  handleHealthData(httpCode, data) {
    const {refreshCount} = this.state;
    if (refreshCount >= 20) {
      clearInterval(this.setInterval);
      this.setState({refreshing: false});
      return;
    }
    if (httpCode === 200) {
      const {nodeId} = data;
      const {nodes} = this.state;
      nodes[nodeId] = data;
      this.setState({nodes: nodes, isLoaded: true, message: "", refreshCount: refreshCount + 1})
    } else {
      this.setState({message: data.message, isLoaded: true})
    }
  }

  render() {
    const {isLoaded, message, refreshCount, refreshing, nodes} = this.state;
    if (!isLoaded) {
      return <div>
        Waiting for health call to return.
      </div>
    }
    if (message) {
      return <div>
        {message}
      </div>
    }
    const refreshStr = (refreshing) ? "" + refreshCount :
      "" + refreshCount + " (Stopped, After Reaching Cap on Refreshes)";
    const nodeArr = Object.values(nodes).sort((a, b) => {
      const {nodeId: aId} = a;
      const {nodeId: bId} = b;
      if (aId > bId) return 1;
      if (aId < bId) return -1;
      return 0;
    });

    return (<div>
      <span className="title">Health Information on Nodes</span>
      <div className="description">A node is queried every two seconds. The load balancer will round
        robin the current nodes. <br/>The number of refreshes is capped at 20.
      </div>
      {nodeArr.map(n => NodeHealth.mkNodeDisplay(n))}
      <p/>
      <div><span className="label">Refresh Count:</span> {refreshStr}</div>
    </div>);
  }
}

export default NodeHealth;