// noinspection JSUnusedAssignment
/**
 * Functions used to help build the basic GUI of the Dynamic Runtime application.
 */

// Mimic *import* statements, but we cannot do imports because we are doing standalone React.
const React = React;
const {Component} = React;

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
                    // noinspection JSCheckFunctionSignatures
                    this.setState({
                        isLoaded: true,
                        items: result.items
                    });
                },
                // Note: it's important to handle errors here
                // instead of a catch() block so that we don't swallow
                // exceptions from actual bugs in components.
                (error) => {
                    // noinspection JSCheckFunctionSignatures
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
            return <div><h2>Loading...</h2></div>
        } else if (error) {
            return <div><span className="error">Error: {error.message}</span></div>
        } else {
            const headers = columns.map((col, index) => {
                return (
                    <th key={index}>{col.label}</th>
                );
            });
            const rows = items.map((item, itemIndex) => {
                const row = columns.map((col, colIndex) => {
                    return (
                        <td key={colIndex} className={col.key}>{DnTable.columnValue(item, col)}</td>
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