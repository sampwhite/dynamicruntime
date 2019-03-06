import React from 'react';
import ReactDOM from 'react-dom';
import './index.css';
import App from './App';
import Client from 'dncore/api/Client';

import * as serviceWorker from './serviceWorker';

// Initializes Client API calls and preps for handling JSON calls and URL building.
Client.init(window.location.pathname, window.location.search);

ReactDOM.render(<App />, document.getElementById('root'));

// If you want your app to work offline and load faster, you can change
// unregister() to register() below. Note this comes with some pitfalls.
// Learn more about service workers: http://bit.ly/CRA-PWA
serviceWorker.unregister();
