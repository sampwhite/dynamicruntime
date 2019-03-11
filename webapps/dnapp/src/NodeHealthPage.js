import React, {Component} from 'react';
import NodeHealth from 'dncore/views/NodeHealth'

class NodeHealthPage extends Component {
  render() {
    return (
      <div className="stdContent">
        <NodeHealth/>
      </div>
    )
  }
}

export default NodeHealthPage;