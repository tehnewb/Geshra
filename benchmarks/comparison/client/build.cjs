/**
 * Pinned bundler creates production React and plain-DOM apps, never a dev/HMR server.
 */
const {buildSync} = require('esbuild');
/**
 * Copies the identical native-element stylesheet and writes the matched empty HTML shells.
 */
const {mkdirSync, copyFileSync, writeFileSync} = require('node:fs');
/**
 * Resolves every build input relative to this isolated project.
 */
const {resolve} = require('node:path');

/**
 * Bundles both real clients with NODE_ENV=production and exact pinned runtime dependencies.
 */
function buildClients() {
    for (const name of ['react', 'dom']) {
        const directory = resolve(__dirname, 'dist', name);
        mkdirSync(directory, {recursive: true});
        buildSync({
            entryPoints: [resolve(__dirname, name + '.js')],
            outfile: resolve(directory, 'app.js'),
            bundle: true,
            minify: true,
            define: {'process.env.NODE_ENV': '"production"'},
            target: 'es2020',
            legalComments: 'eof'
        });
        copyFileSync(resolve(__dirname, '../common/bench.css'), resolve(directory, 'bench.css'));
        writeFileSync(
            resolve(directory, 'index.html'),
            '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Matched benchmark</title><link rel="icon" href="data:,"><link rel="stylesheet" href="/bench.css"><script src="/app.js" defer></script></head><body><div id="app"></div></body></html>'
        );
    }
}

buildClients();
