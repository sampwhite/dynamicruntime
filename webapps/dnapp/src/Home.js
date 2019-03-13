import React, {Component} from 'react';

import ReactPromo from './views/ReactPromo'

class Home extends Component {
  render() {
    return (
      <ReactPromo>
        <div className="promoText">
        This demo React application was built using the{' '}
        <a className="stdLink" href="https://reactjs.org/docs/create-a-new-react-app.html" target="_blank">
          Create React App</a> as the foundation. The style, colors, layout and graphic of this page are
          all from the original example page.<br/>
        </div>
      </ReactPromo>
    )
  }
}

export default Home;