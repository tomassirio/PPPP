// Live game sound from /ws/audio: a JSON header, then mono s16le chunks (~1 frame each), each played
// as an AudioBufferSourceNode scheduled right after the previous one, ~LEAD seconds ahead of now.
import { backoff } from './play.ts';

/** Jitter buffer we aim for, and the most we tolerate before dropping chunks (seconds). */
export const LEAD = 0.1;
export const MAX_LEAD = 0.25;

/**
 * Start time for the next chunk, or null to drop it. playAt is where the previous chunk ends.
 * Underrun (playAt already due) restarts LEAD ahead; overrun (queued past MAX_LEAD) drops until it drains.
 */
export function nextStart(playAt: number, now: number): number | null {
	if (playAt < now + 0.005) return now + LEAD;
	if (playAt > now + MAX_LEAD) return null;
	return playAt;
}

export type Prefs = { on: boolean; volume: number };

export function loadPrefs(): Prefs {
	try {
		const p = JSON.parse(localStorage.getItem('pppp.sound') ?? '{}');
		const volume = typeof p.volume === 'number' ? Math.min(1, Math.max(0, p.volume)) : 0.7;
		return { on: p.on === true, volume };
	} catch {
		return { on: false, volume: 0.7 };
	}
}

export function savePrefs(p: Prefs) {
	try {
		localStorage.setItem('pppp.sound', JSON.stringify(p));
	} catch {
		/* private mode: not remembered */
	}
}

/** Muted players hold no socket, so they cost the server nothing. */
export function createPlayer() {
	let ctx: AudioContext | null = null;
	let gain: GainNode | null = null;
	let volume = 1;
	let ws: WebSocket | null = null;
	let timer: ReturnType<typeof setTimeout> | undefined;
	let attempt = 0;
	let rate = 0;
	let playAt = 0;

	function play(pcm: Int16Array) {
		if (!ctx || !gain || !rate || !pcm.length) return;
		const start = nextStart(playAt, ctx.currentTime);
		if (start === null) return;
		const buf = ctx.createBuffer(1, pcm.length, rate);
		const ch = buf.getChannelData(0);
		for (let i = 0; i < pcm.length; i++) ch[i] = pcm[i] / 32768; // Int16Array is little-endian on every browser platform
		const src = ctx.createBufferSource();
		src.buffer = buf;
		src.connect(gain);
		src.start(start);
		playAt = start + buf.duration;
	}

	function connect() {
		const proto = location.protocol === 'https:' ? 'wss' : 'ws';
		const sock = new WebSocket(`${proto}://${location.host}/ws/audio`);
		ws = sock;
		sock.binaryType = 'arraybuffer';
		sock.onmessage = (e) => {
			if (typeof e.data === 'string') {
				rate = JSON.parse(e.data).sampleRate;
				attempt = 0;
			} else {
				play(new Int16Array(e.data));
			}
		};
		sock.onclose = () => {
			if (ws === sock) timer = setTimeout(connect, backoff(attempt++));
		};
	}

	return {
		/** Call from a user gesture (click/key) so the browser lets the AudioContext run. */
		start() {
			if (!ctx) {
				// Run the context at the stream's rate (the server always sends 22050 Hz) so chunks play
				// back to back without each being resampled on its own, which clicks at the seams.
				try {
					ctx = new AudioContext({ sampleRate: 22050 });
				} catch {
					ctx = new AudioContext(); // browser refuses that rate: per-chunk resampling, may click
				}
				gain = ctx.createGain();
				gain.gain.value = volume;
				gain.connect(ctx.destination);
			}
			ctx.resume();
			if (!ws) connect();
		},
		stop() {
			clearTimeout(timer);
			const sock = ws;
			ws = null;
			sock?.close();
			ctx?.suspend();
		},
		setVolume(v: number) {
			volume = v;
			if (gain) gain.gain.value = v;
		}
	};
}
