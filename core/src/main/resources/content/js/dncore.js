// noinspection JSUnusedAssignment
/**
 * Functions used to help build the basic GUI of the Dynamic Runtime application.
 */

// Mimic *import* statements. We cannot do imports because we are doing standalone React.
const React = React;
const {Component} = React;
if (!Component.setState) {
    // Suppress warnings on usage of setState.
    Component.setState = {};
}

function extractParams(url) {
    return new URLSearchParams(url);
}

class DnMessage extends Component {
    render() {
        const {error} = this.props;
        const className = (error) ? "error" : "message";
        return <div className={className}>{this.props.children}</div>
    }
}

class DnTable extends Component {
    constructor(props) {
        super(props);
        this.state = {
            error: null,
            isLoaded: false,
            items: []
        };
    }

    componentDidMount() {
        const {dataUrl} = this.props;
        fetch(dataUrl)
            .then(res => res.json())
            .then(
                (result) => {
                   this.setState({
                        isLoaded: true,
                        items: result.items
                    });
                },
                (error) => {
                    this.setState({
                        isLoaded: true,
                        error
                    });
                }
            )
    }

    static columnValue(item, col) {
        const {mkDisplay} = col;
        if (mkDisplay) {
            return mkDisplay(item);
        }
        return item[col.key]
    }

    render() {
        const {columns} = this.props;
        const {error, isLoaded, items} = this.state;
        if (!isLoaded) {
            return <DnMessage>Loading...</DnMessage>
        } else if (error) {
            return <DnMessage error={true}>Error: {error.message}</DnMessage>
        } else {
            const headers = columns.map((col, index) => {
                return (
                    <th key={index}>{col.label}</th>
                );
            });
            const rows = items.map((item, itemIndex) => {
                const row = columns.map((col, colIndex) => {
                    return (
                        <td key={colIndex} className={col.key + " border"}>{DnTable.columnValue(item, col)}</td>
                    );
                });
                return (
                    <tr key={itemIndex}>{row}</tr>
                );
            });
            return (
                <table>
                    <thead>
                    <tr>{headers}</tr>
                    </thead>
                    <tbody>
                    {rows}
                    </tbody>
                </table>
            );
        }

    }
}

class DnEndpointForm extends Component {
    constructor(props) {
        super(props);
        this.state = {
            error: null,
            isLoaded: false,
            items: []
        };
        this.computeRequestUrl = this.computeRequestUrl.bind(this);
        this.handleInputChange = this.handleInputChange.bind(this);
        this.setFormBuildingState = this.setFormBuildingState.bind(this);
        this.onSubmit = this.onSubmit.bind(this);
    }

    componentDidMount() {
        const {endpoint} = this.props;
        const e = endpoint ? endpoint.trim() : "";
        if (e.length === 0) {
            return;
        }
        const {dataUrl} = this.props;
        const baseUrl = (dataUrl.indexOf("?") > 0) ? dataUrl + "&" : dataUrl + "?";
        const fetchUrl = baseUrl + "endpoint=" + e;
        fetch(fetchUrl)
            .then(res => res.json())
            .then(
                (result) => {
                    this.setFormBuildingState(result.items)
                },
                (error) => {
                    this.setState({
                        isLoaded: true,
                        error
                    });
                }
            )
    }

    setFormBuildingState(items) {
        const obj = {isLoaded: true, items: items};
        if (items.length === 1) {
            const {endpointInputType} = items[0];
            if (endpointInputType) {
                const {dnFields} = endpointInputType;
                const fields = dnFields ? dnFields : [];
                fields.map(fld => {
                    const {name} = fld;
                    const fldName = "fld_" + name;
                    // Controlled forms need an initial state value.
                    obj[fldName] = "";
                });
            }
        }
        //alert(JSON.stringify(obj));

        this.setState(obj);
    }

    static toFormType(dnType) {
        switch (dnType) {
            case "String":
                return "text";
            case "Integer":
                return "number";
            case "Date":
                return "date";
            case "Boolean":
                return "text";
            case "Float":
                return "number";
            default:
                return "text";
        }
    }

    computeRequestUrl() {
        const {endpoint} = this.props;
        const {items} = this.state;
        const {endpointInputType} = items[0];
        const {dnFields: fields} = endpointInputType;

        const params = [];
        //const obj = Object.assign({}, this.state);
        //obj.items = [];
        //alert(JSON.stringify(obj));
        if (fields) {
            for (let fld of fields) {
                const fldName = "fld_" + fld.name;
                const val = this.state[fldName];
                if (val) {
                    params.push(fld.name + "=" + encodeURIComponent(val));
                }
            }
        }
        const paramStr = params.join("&");
        return paramStr.length > 0 ? endpoint + "?" + paramStr : endpoint;
    }

    computeRequestParams() {
        const {items} = this.state;
        const {endpointInputType} = items[0];
        const {dnFields: fields} = endpointInputType;

        const params = {};
        //const obj = Object.assign({}, this.state);
        //obj.items = [];
        //alert(JSON.stringify(obj));
        if (fields) {
            for (let fld of fields) {
                const fldName = "fld_" + fld.name;
                const val = this.state[fldName];
                if (val) {
                    params[fld.name] = val;
                }
            }
        }
        return params;
    }


    handleInputChange(event) {
        const target = event.target;
        const value = target.value;
        const name = target.name;

        this.setState({
            [name]: value
        }, null);
    }

    onSubmit(event) {
        event.preventDefault();
        const {endpoint} = this.props;
        const {items} = this.state;
        const {httpMethod} = items[0];
        const execUrl = (httpMethod === "GET") ? this.computeRequestUrl() : endpoint;

        const args = (httpMethod === "GET") ? null :
            {
                headers: {
                    "content-type": "application/json: charset=utf-8"
                },
                body: JSON.stringify(this.computeRequestParams()),
                method: httpMethod
            };

        fetch(execUrl, args)
            .then(res => res.text())
            .then(
                (result) => {
                    this.setState({
                        results: result
                    });
                },
                (error) => {
                     this.setState({
                        results: error.message
                    });
                }
            )
    }

    render() {
        const {endpoint} = this.props;
        if (!endpoint || endpoint.trim().length === 0) {
            return <DnMessage>Endpoint form requires the query parameter <b>endpoint</b>.</DnMessage>
        }
        const {error, isLoaded, items} = this.state;
        if (!isLoaded) {
            return <DnMessage>Loading...</DnMessage>
        } else if (error) {
            return  (<DnMessage error={true}>Error: {error.message}</DnMessage>);
        } else if (items.length !== 1) {
            return <DnMessage>Did not get back an endpoint definition for endpoint {endpoint}.</DnMessage>
        }
        else {
            const {endpointInputType, httpMethod} = items[0];
            if (!endpointInputType) {
                return <DnMessage>Endpoint definition for endpoint {endpoint} did not return a
                    input type.</DnMessage>
            }
            const {dnFields} = endpointInputType;
            const fields = dnFields ? dnFields : [];
            const {baseType: inputBaseType} = endpointInputType;

            const formFields = fields.map(fld => {
                const {coreType, name, label, description} = fld;
                const fldName = "fld_" + name;
                if (coreType === "Map") {
                    return [
                        <tr key={"label_" + name}>
                            <td className="formLabel"><label>{label}</label></td>
                            <td className="formDescription" colSpan="2">{description}</td>
                        </tr>,
                        <tr key={"textarea_"+ name}>
                            <td colSpan="3">
                                <textarea className="jsonInput" name={fldName} cols="100" rows="10"
                                          value={this.state[fldName]} onChange={this.handleInputChange}/>
                            </td>
                        </tr>
                    ]
                } else {
                    const inputType = DnEndpointForm.toFormType(coreType);
                    return (
                        <tr key={"row_" + name}>
                            <td key={"label_" + name} className="formLabel"><label>{label}:</label></td>
                            <td key={"input_" + name} className="formInput">
                                <input name={fldName} type={inputType} value={this.state[fldName]}
                                       onChange={this.handleInputChange}/>
                            </td>
                            <td key={"description_" + name} className="formDescription">{description}</td>
                        </tr>
                    )
                }
            });
            let linkStr = "";
            if (inputBaseType && inputBaseType.indexOf(".") > 0) {
                const url = "/schema/dnType/list?dnTypeName=" + inputBaseType;
                linkStr = <div><p>The core input type definition for endpoint <b>{endpoint} </b>
                    can be found at <a href={url}>{inputBaseType}</a>. This does not include
                    general protocol parameters.</p></div>
            } else {
                if (fields.length > 0) {
                    linkStr = <div><p>Endpoint <b>{endpoint}</b> has only intrinsic defined input parameters.</p></div>
                } else {
                    linkStr = <div>Endpoint <b>{endpoint}</b> has no input parameters.</div>
                }
            }
            const mainTable = (fields.length > 0) ?
                <table>
                    <tbody>
                    {formFields}
                    </tbody>
                </table> : "";
            return (
                <div>
                    {linkStr}
                    <form key="mainForm" onSubmit={this.onSubmit}>
                        {mainTable}
                        <p key="requestInfo"><b>Request: </b>
                            <span className={httpMethod + "Method"}>{httpMethod}</span>:{this.computeRequestUrl()}</p>
                        <p key="submitButton"><input type="submit" value="Execute Request"/></p>
                    </form>
                    <form key="results">
                        <textarea name="results" cols="140" rows="40" value={this.state.results} readOnly={true}/>
                    </form>
                </div>
            );
        }
    }
}