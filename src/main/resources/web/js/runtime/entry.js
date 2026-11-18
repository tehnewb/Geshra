import {startRuntime} from './transport.js';

/**
 * Starts one runtime after the document is ready. Sources remain separate by responsibility;
 * the development build bundles them into the single classic script used by existing shells.
 */
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', startRuntime, {once: true});
else startRuntime();
