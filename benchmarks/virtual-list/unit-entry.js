/**
 * Direct exports of actual production behavior for deterministic acknowledgment/lifecycle checks.
 */
import {configureVirtualList, acknowledgeVirtualWindow, resumeVirtualLists} from '../../src/main/resources/web/js/runtime/virtual-list.js';
import {virtualLists} from '../../src/main/resources/web/js/runtime/state.js';
import {cleanupRuntimeTree} from '../../src/main/resources/web/js/runtime/lifecycle.js';
import {updateDOM} from '../../src/main/resources/web/js/runtime/dom.js';

window.virtualTest = {configureVirtualList, acknowledgeVirtualWindow, resumeVirtualLists, virtualLists, cleanupRuntimeTree, updateDOM};
