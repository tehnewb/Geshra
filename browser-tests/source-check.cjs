const {readdirSync, readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const {spawnSync} = require('node:child_process');

/**
 * Checks maintained runtime syntax and mechanically verifiable cross-language documentation.
 * Generated bundles and vendor assets are checked through their generator or upstream notices.
 * Architectural responsibility and the accuracy of explanations still require code review.
 */
function checkSources() {
    const directory = resolve(__dirname, '../src/main/resources/web/js/runtime');
    for (const name of readdirSync(directory)) {
        if (!name.endsWith('.js')) continue;
        const file = resolve(directory, name);
        const syntax = spawnSync(process.execPath, ['--check', file], {encoding: 'utf8'});
        if (syntax.status !== 0) throw new Error(syntax.stderr);
        const source = readFileSync(file, 'utf8');
        if (/\/\*\*?[^\r\n]*\*\//.test(source)) throw new Error(name + ': one-line block documentation is prohibited');
        for (const match of source.matchAll(/^(?:export )?(?:async )?function \w+\(|^export class \w+|^    (?:constructor|[a-zA-Z]+)\([^\n]*\) \{/gm)) {
            if (!source.slice(0, match.index).trimEnd().endsWith('*/')) throw new Error(name + ': named behavior requires documentation: ' + match[0]);
        }
        for (const match of source.matchAll(/^(?:export )?const \w+/gm)) {
            if (!source.slice(0, match.index).trimEnd().endsWith('*/')) throw new Error(name + ': module constants require documentation: ' + match[0]);
        }
        for (const match of source.matchAll(/^let [^\n]+/gm)) {
            if (!match[0].includes('//')) throw new Error(name + ': mutable module state requires a right-side explanation');
        }
    }
}

checkSources();
