/**
 * Sends a genuine authoritative server operation. Neither client predicts the result locally.
 * The form carries its native value when submitted; typing alone does not require server work.
 */
export async function requestAction(action, value = '') {
    const response = await fetch('/api/action', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({action, value})});
    if (!response.ok) throw new Error('Server operation failed: ' + response.status);
    return response.json();
}
