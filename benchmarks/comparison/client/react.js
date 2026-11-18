import {createElement, useRef, useState} from 'react';
import {createRoot} from 'react-dom/client';
import {requestAction} from './api.js';
import {Table} from './react-table.js';

/**
 * The production React fixture keeps a keyed client representation of authoritative server
 * results. Uses native elements and ordinary React state; no custom renderer or forced sync.
 */
function MatchedApp() {
    const [state, setState] = useState({counter: 0, echo: '', status: 'Ready', revision: 0, rows: []});
    const input = useRef(null);
    /**
     * Applies one server delta with stable row keys and a final completion revision.
     */
    async function action(name) {
        const result = await requestAction(name, input.current.value);
        setState((previous) => {
            let rows = previous.rows;
            if (name === 'create') rows = result.rows;
            else if (name === 'update') {
                const patches = new Map(result.patches.map((patch) => [patch.id, patch.label]));
                rows = rows.map((row) => (patches.has(row.id) ? {...row, label: patches.get(row.id)} : row));
            } else if (name === 'select') rows = rows.map((row) => (row.id === 500 ? {...row, selected: true} : row));
            else if (name === 'remove') rows = rows.filter((row) => row.id !== 500);
            else if (name === 'clear') rows = [];
            return {...previous, rows, counter: result.counter ?? previous.counter, echo: result.echo ?? previous.echo, status: result.status, revision: result.revision};
        });
    }
    return createElement(
        'main',
        {'data-ready': 'true', 'data-revision': state.revision},
        createElement('h1', null, 'Matched benchmark'),
        createElement(
            'div',
            null,
            createElement('button', {onClick: () => action('increment')}, 'Increment'),
            createElement('input', {type: 'text', 'aria-label': 'Name', ref: input}),
            ...[
                ['Echo', 'echo'],
                ['Create 1000', 'create'],
                ['Update tenth', 'update'],
                ['Select 500', 'select'],
                ['Remove 500', 'remove'],
                ['Clear', 'clear']
            ].map(([name, operation]) => createElement('button', {key: name, onClick: () => action(operation)}, name))
        ),
        createElement('p', {'data-counter': 'true'}, 'Counter: ' + state.counter),
        createElement('p', {'data-echo': 'true'}, 'Echo: ' + state.echo),
        createElement('p', {'data-status': 'true'}, state.status),
        createElement(Table, {rows: state.rows})
    );
}

createRoot(document.getElementById('app')).render(createElement(MatchedApp));
