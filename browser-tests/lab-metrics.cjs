/**
 * Installs document-local observers before application scripts execute. Entries stay raw so
 * session-window CLS and interaction estimates can be independently checked after the run.
 * This is a bounded lab observation, not a field Core Web Vitals distribution.
 */
function installMetrics() {
    const supported = PerformanceObserver.supportedEntryTypes || [];
    const state = {supported, lcp: [], shifts: [], events: [], tasks: [], errors: [], trustedInputs: [], visibility: []};
    window.browserLab = state;
    for (const type of ['largest-contentful-paint', 'layout-shift', 'event', 'longtask']) {
        if (!supported.includes(type)) continue;
        const observer = new PerformanceObserver((list) => {
            for (const entry of list.getEntries()) {
                if (type === 'largest-contentful-paint') state.lcp.push({startTime: entry.startTime, size: entry.size, tag: entry.element?.tagName || null});
                else if (type === 'layout-shift') state.shifts.push({startTime: entry.startTime, value: entry.value, hadRecentInput: entry.hadRecentInput});
                else if (type === 'event')
                    state.events.push({
                        name: entry.name,
                        startTime: entry.startTime,
                        duration: entry.duration,
                        interactionId: entry.interactionId,
                        inputDelay: entry.processingStart - entry.startTime,
                        processingMs: entry.processingEnd - entry.processingStart
                    });
                else state.tasks.push({startTime: entry.startTime, duration: entry.duration});
            }
        });
        observer.observe(type === 'event' ? {type, buffered: true, durationThreshold: 16} : {type, buffered: true});
    }
    for (const name of ['pointerdown', 'keydown'])
        document.addEventListener(name, (event) => state.trustedInputs.push({name, trusted: event.isTrusted, at: performance.now()}), true);
    document.addEventListener('visibilitychange', () => state.visibility.push({state: document.visibilityState, at: performance.now()}));
    window.addEventListener('error', (event) => state.errors.push(event.message));
    window.addEventListener('unhandledrejection', (event) => state.errors.push(String(event.reason)));
}

/**
 * Reads navigation, resources, rendering, and supported responsiveness metrics in the page.
 * INP uses observed Event Timing interaction maxima; unreported sub-16ms interactions remain
 * unknown, never zero. The finite workloads stay below 50 interactions, so no outlier is dropped.
 * CLS excludes recent-input shifts and takes the largest one-second-gap/five-second window.
 */
function readMetrics() {
    const state = window.browserLab;
    const navigation = performance.getEntriesByType('navigation')[0];
    let first = 0;
    let last = 0;
    let windowScore = 0;
    let cls = 0;
    for (const shift of state.shifts) {
        if (shift.hadRecentInput) continue;
        if (windowScore > 0 && shift.startTime - last < 1000 && shift.startTime - first < 5000) windowScore += shift.value;
        else {
            first = shift.startTime;
            windowScore = shift.value;
        }
        last = shift.startTime;
        cls = Math.max(cls, windowScore);
    }
    const interactions = new Map();
    for (const entry of state.events) if (entry.interactionId) interactions.set(entry.interactionId, Math.max(interactions.get(entry.interactionId) || 0, entry.duration));
    const durations = Array.from(interactions.values()).sort((left, right) => right - left);
    const count = typeof performance.interactionCount === 'number' ? performance.interactionCount : null;
    const inp = durations.length ? durations[Math.min(durations.length - 1, Math.floor((count || interactions.size) / 50))] : null;
    const paints = Object.fromEntries(performance.getEntriesByType('paint').map((entry) => [entry.name, entry.startTime]));
    const fcp = paints['first-contentful-paint'] ?? null;
    const dcl = navigation.domContentLoadedEventEnd;
    return {
        observedUntilMs: performance.now(),
        navigation: {
            ttfbMs: navigation.responseStart,
            requestTtfbMs: navigation.responseStart - navigation.requestStart,
            dnsMs: navigation.domainLookupEnd - navigation.domainLookupStart,
            connectMs: navigation.connectEnd - navigation.connectStart,
            tlsMs: navigation.secureConnectionStart ? navigation.connectEnd - navigation.secureConnectionStart : 0,
            domContentLoadedMs: dcl,
            loadMs: navigation.loadEventEnd,
            transferBytes: navigation.transferSize,
            encodedBytes: navigation.encodedBodySize,
            decodedBytes: navigation.decodedBodySize
        },
        fpMs: paints['first-paint'] ?? null,
        fcpMs: fcp,
        lcpMs: state.lcp.at(-1)?.startTime ?? null,
        cls: state.supported.includes('layout-shift') ? cls : null,
        labInpMs: inp,
        interactionCount: count,
        observedInteractionIds: interactions.size,
        inpStatus: !state.supported.includes('event')
            ? 'unsupported'
            : inp === null
              ? 'no qualifying Event Timing entries; below threshold or unobserved'
              : 'observed lab estimate, not field INP',
        longTaskCount: state.supported.includes('longtask') ? state.tasks.length : null,
        longTaskStatus: state.supported.includes('longtask') ? 'observed' : 'unsupported',
        observedBlockingMs: state.supported.includes('longtask') ? state.tasks.reduce((sum, task) => sum + Math.max(0, task.duration - 50), 0) : null,
        fcpToDclBlockingMs:
            fcp === null || !state.supported.includes('longtask')
                ? null
                : state.tasks.filter((task) => task.startTime >= fcp && task.startTime < dcl).reduce((sum, task) => sum + Math.max(0, task.duration - 50), 0),
        resources: performance.getEntriesByType('resource').map((entry) => ({
            name: entry.name,
            type: entry.initiatorType,
            startMs: entry.startTime,
            durationMs: entry.duration,
            transferBytes: entry.transferSize,
            encodedBytes: entry.encodedBodySize,
            decodedBytes: entry.decodedBodySize,
            protocol: entry.nextHopProtocol
        })),
        raw: state
    };
}

module.exports = {installMetrics, readMetrics};
