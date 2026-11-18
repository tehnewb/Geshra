/**
 * Counts UTF-8 bytes without allocating an encoded copy. Lone surrogates follow TextEncoder's
 * replacement behavior. Length validation happens before packet storage is allocated.
 */
export function textBytes(text) {
    if (typeof text !== 'string' || text.length > 65535) throw new RangeError('Invalid text length');
    let bytes = 0;
    for (let index = 0; index < text.length; index++) {
        const character = text.charCodeAt(index);
        if (character < 128) bytes++;
        else if (character < 2048) bytes += 2;
        else if (character >= 55296 && character <= 56319 && index + 1 < text.length && text.charCodeAt(index + 1) >= 56320 && text.charCodeAt(index + 1) <= 57343) {
            bytes += 4;
            index++;
        } else bytes += 3;
    }
    if (bytes > 65535) throw new RangeError('Text exceeds 65535 UTF-8 bytes');
    return bytes;
}

/**
 * Returns the byte count of one canonical non-negative signed-32-bit integer.
 */
export function integerBytes(value) {
    if (!Number.isInteger(value) || value < 0 || value > 2147483647) throw new RangeError('Invalid compact integer');
    return value < 128 ? 1 : value < 16384 ? 2 : value < 2097152 ? 3 : value < 268435456 ? 4 : 5;
}
