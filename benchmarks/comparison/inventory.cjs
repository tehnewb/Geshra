/**
 * Exact packaged artifact inspection uses the installed JDK tool, not dependency assumptions.
 */
const {execFileSync} = require('node:child_process');
/**
 * Artifact metadata and digest storage.
 */
const {readFileSync, writeFileSync, mkdirSync} = require('node:fs');
/**
 * Platform-independent artifact paths.
 */
const {resolve} = require('node:path');
/**
 * SHA-256 identifies the actual measured production jars and JavaScript bundles.
 */
const {createHash} = require('node:crypto');

/**
 * Fingerprints a binary artifact and records its byte count.
 */
function digest(path) {
    const bytes = readFileSync(path);
    return {bytes: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex')};
}

/**
 * Inspects effective packaged dependency versions and verifies Vaadin production metadata.
 */
function inventory() {
    const jar = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'jar.exe' : 'jar') : 'jar';
    const result = {recordedAt: new Date().toISOString(), java: process.env.JAVA_HOME || 'JDK tool on PATH', artifacts: {}};
    for (const app of ['geshra', 'vaadin']) {
        const path = resolve(__dirname, app, 'build/libs/matched-' + app + '.jar');
        const files = execFileSync(jar, ['tf', path], {encoding: 'utf8'}).split(/\r?\n/);
        const metadata = {jar: digest(path), effectiveLibraries: files.filter((name) => name.startsWith('BOOT-INF/lib/')).map((name) => name.slice('BOOT-INF/lib/'.length))};
        if (app === 'vaadin') {
            const config = files.find((name) => name.endsWith('/VAADIN/config/flow-build-info.json'));
            const directory = resolve(__dirname, '../../build/comparison-inventory');
            mkdirSync(directory, {recursive: true});
            execFileSync(jar, ['xf', path, config], {cwd: directory});
            const build = JSON.parse(readFileSync(resolve(directory, config), 'utf8'));
            metadata.production = build;
            if (build.productionMode !== true) throw new Error('Vaadin artifact is not a production build');
        }
        result.artifacts[app] = metadata;
    }
    for (const app of ['react', 'dom']) result.artifacts[app] = {bundle: digest(resolve(__dirname, 'client/dist', app, 'app.js'))};
    result.clientVersions = JSON.parse(readFileSync(resolve(__dirname, 'client/package.json'), 'utf8'));
    result.commonCss = digest(resolve(__dirname, 'common/bench.css'));
    writeFileSync(
        resolve(process.env.COMPARISON_INVENTORY_OUTPUT || resolve(__dirname, '../../performance-results/comparison-inventory.json')),
        JSON.stringify(result, null, 2) + '\n'
    );
    console.log('Production inventory verified and saved.');
}

inventory();
