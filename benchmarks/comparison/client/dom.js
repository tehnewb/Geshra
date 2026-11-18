import {requestAction} from './api.js';

/**
 * Creates the same native element tree without a framework. This is a deliberately simple
 * baseline using real server results and explicit DOM updates, not a competitor library.
 */
function startDOM() {
    const root = document.createElement('main');
    root.dataset.ready = 'true';
    root.dataset.revision = '0';
    root.innerHTML =
        '<h1>Matched benchmark</h1><div></div><p data-counter="true">Counter: 0</p><p data-echo="true">Echo: </p><p data-status="true">Ready</p><table><tbody></tbody></table>';
    const controls = root.querySelector('div');
    const input = document.createElement('input');
    input.type = 'text';
    input.setAttribute('aria-label', 'Name');
    for (const [name, operation] of [
        ['Increment', 'increment'],
        ['Echo', 'echo'],
        ['Create 1000', 'create'],
        ['Update tenth', 'update'],
        ['Select 500', 'select'],
        ['Remove 500', 'remove'],
        ['Clear', 'clear']
    ]) {
        const button = document.createElement('button');
        button.textContent = name;
        button.addEventListener('click', async () => applyResult(root, operation, await requestAction(operation, input.value)));
        controls.appendChild(button);
        if (operation === 'increment') controls.appendChild(input);
    }
    document.getElementById('app').appendChild(root);
}

/**
 * Applies the authoritative server delta with stable native rows and the common revision marker.
 */
function applyResult(root, operation, result) {
    const body = root.querySelector('tbody');
    if (operation === 'create') {
        const fragment = document.createDocumentFragment();
        for (const row of result.rows) {
            const element = document.createElement('tr');
            element.dataset.row = row.id;
            const id = document.createElement('td');
            id.textContent = row.id;
            const label = document.createElement('td');
            label.textContent = row.label;
            element.append(id, label);
            fragment.appendChild(element);
        }
        body.replaceChildren(fragment);
    } else if (operation === 'update') {
        for (const patch of result.patches) body.querySelector('[data-row="' + patch.id + '"]').lastElementChild.textContent = patch.label;
    } else if (operation === 'select') body.querySelector('[data-row="500"]')?.classList.add('selected');
    else if (operation === 'remove') body.querySelector('[data-row="500"]')?.remove();
    else if (operation === 'clear') body.replaceChildren();
    if ('counter' in result) root.querySelector('[data-counter]').textContent = 'Counter: ' + result.counter;
    if ('echo' in result) root.querySelector('[data-echo]').textContent = 'Echo: ' + result.echo;
    root.querySelector('[data-status]').textContent = result.status;
    root.dataset.revision = result.revision;
}

startDOM();
