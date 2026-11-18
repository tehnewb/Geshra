const {buildSync} = require('esbuild');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');

/**
 * Builds the checked-in classic-script runtime from focused native-module sources.
 * Java consumers need neither Node nor a frontend build. --check verifies reproducibility
 * without changing the artifact and is suitable for CI before Java packaging.
 */
function buildRuntime() {
    const outfile = resolve(__dirname, '../src/main/resources/web/js/runtime.js');
    const result = buildSync({
        entryPoints: [resolve(__dirname, '../src/main/resources/web/js/runtime/entry.js')],
        outfile,
        bundle: true,
        minify: true,
        format: 'iife',
        target: ['es2020'],
        charset: 'utf8',
        legalComments: 'none',
        banner: {js: '/*\n * Generated WebSocket runtime. Edit js/runtime/*.js and run npm run build:runtime.\n */'},
        write: !process.argv.includes('--check')
    });
    if (process.argv.includes('--check') && result.outputFiles[0].text !== readFileSync(outfile, 'utf8')) {
        throw new Error('runtime.js is stale; run npm run build:runtime');
    }
}

buildRuntime();
