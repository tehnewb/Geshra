import {PacketWriter} from './packet-writer.js';
import {textBytes, integerBytes} from './wire-size.js';

/**
 * Packs built-in browser actions while preserving legacy callback payload semantics.
 * Coordinates retain signed-short behavior, modifiers share one byte, and textual fields
 * remain bounded by their original 65535-byte limit. Heartbeats retain the one-byte encoding.
 */
export function encodeCompact(opcode, args) {
    if (!Number.isInteger(opcode) || opcode < 0 || opcode > 11) return null;
    if (opcode === 7) return heartbeat;
    const firstText = opcode === 0 ? args[0] : opcode === 2 || opcode === 3 || opcode === 4 || opcode === 6 || opcode === 9 || opcode === 10 ? args[1] : '';
    const secondText = opcode === 9 ? args[2] || '' : '';
    const file = opcode === 6 ? new Uint8Array(args[2]) : null;
    if (file && file.length > 65536) throw new RangeError('Upload exceeds the WebSocket frame limit');
    const textLength = textBytes(firstText);
    const secondLength = textBytes(secondText);
    let capacity = 2 + (opcode === 0 ? 0 : integerBytes(args[0]));
    if (opcode === 0 || opcode === 4 || opcode === 10) capacity += integerBytes(textLength) + textLength;
    else if (opcode === 2 || opcode === 3) capacity += 1 + integerBytes(textLength) + textLength;
    else if (opcode === 6) capacity += integerBytes(textLength) + integerBytes(file.length) + textLength + file.length;
    else if (opcode === 9) capacity += integerBytes(textLength) + textLength + integerBytes(secondLength) + secondLength;
    else if (opcode === 8) capacity += integerBytes(args[1]) + integerBytes(args[2]) + integerBytes(args[3]) + (args[4] === undefined ? 0 : integerBytes(args[4]));
    else if (opcode === 1) {
        capacity++;
        for (let index = 2; index < 8; index++) {
            const signed = (args[index] << 16) >> 16;
            capacity += integerBytes((signed << 1) ^ (signed >> 31));
        }
    }
    if (capacity > 65536) throw new RangeError('Action exceeds the WebSocket frame limit');
    const writer = new PacketWriter(opcode, capacity);
    if (opcode === 0) {
        writer.text(args[0], textLength);
        return writer.finish();
    }
    writer.unsigned(args[0]);
    switch (opcode) {
        case 1: {
            writer.byte((args[1] & 7) | (modifiers(args, 8) << 3));
            for (let index = 2; index < 8; index++) writer.coordinate(args[index]);
            break;
        }
        case 2:
        case 3:
            writer.byte(modifiers(args, 3) | (args[2] ? 16 : 0));
            writer.text(args[1], textLength);
            break;
        case 4:
        case 10:
            writer.text(args[1], textLength);
            break;
        case 5:
        case 11:
            break;
        case 6: {
            const filename = encoder.encode(args[1]);
            if (filename.length > 65535) throw new RangeError('Filename exceeds 65535 UTF-8 bytes');
            writer.unsigned(filename.length);
            writer.unsigned(file.length);
            // The filename length precedes the file length in the existing upload grammar.
            writer.raw(filename);
            writer.raw(file);
            break;
        }
        case 8:
            writer.unsigned(args[1]);
            writer.unsigned(args[2]);
            writer.unsigned(args[3]);
            if (args[4] !== undefined) writer.unsigned(args[4]);
            break;
        case 9:
            writer.text(args[1], textLength);
            writer.text(secondText, secondLength);
            break;
        default:
            return null;
    }
    return writer.finish();
}

/**
 * Packs control, shift, alt, and meta flags in their established bit order.
 */
function modifiers(args, offset) {
    return (args[offset] ? 1 : 0) | (args[offset + 1] ? 2 : 0) | (args[offset + 2] ? 4 : 0) | (args[offset + 3] ? 8 : 0);
}

/**
 * Shared immutable heartbeat avoids allocating a fresh array every timer tick.
 */
const heartbeat = new Uint8Array([7]);

/**
 * Upload-only encoder; normal scalar fields use direct encoding into their packet.
 */
const encoder = new TextEncoder();
