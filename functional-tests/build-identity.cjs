/**
 * Reads the owned server's persisted launch identity and current packaged artifact.
 */
const fs = require('node:fs');
/**
 * Resolves build outputs relative to this independent test directory.
 */
const path = require('node:path');
/**
 * Independently hashes the actual JAR instead of copying a prior report value.
 */
const crypto = require('node:crypto');
/**
 * Checks agreement between recorded launch metadata, PID file and the actual artifact hash.
 */
const assert = require('node:assert/strict');

/**
 * Returns the current build identity, rejecting stale or mismatched launch metadata.
 * This verifies disk evidence; it does not independently attest the running process command line.
 */
function readBuildIdentity() {
    /*
     * Hash before browser work so the report never silently inherits a hardcoded older build.
     */
    const root = path.join(__dirname, '..');
    const metadata = JSON.parse(fs.readFileSync(path.join(root, 'build/showcase-build.json'), 'utf8').replace(/^\uFEFF/, ''));
    const pid = Number(
        fs
            .readFileSync(path.join(root, 'build/showcase-server.pid'), 'utf8')
            .replace(/^\uFEFF/, '')
            .trim()
    );
    const jar = path.join(root, 'examples/spring-app/build/libs/geshra-spring-example.jar');
    const sha256 = crypto.createHash('sha256').update(fs.readFileSync(jar)).digest('hex').toUpperCase();
    assert.equal(pid, metadata.PID, 'PID file differs from launch metadata');
    assert.equal(sha256, metadata.SHA256, 'Current JAR differs from launched build metadata');
    return {pid, sha256, url: metadata.URL, jar};
}

module.exports = {readBuildIdentity};
