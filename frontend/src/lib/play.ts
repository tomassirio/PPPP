// Pure helpers for the /play page (unit-tested in play.test.ts).

export type Button = 'up' | 'down' | 'left' | 'right' | 'a' | 'b' | 'start' | 'select';

/** Shapes from StateController.State / InputScheduler.Tally / EmulatorHost.Status. */
export type Tally = { window: string; tally: Record<string, number>; endsAt: number };
export type GameState = {
	power: 'ON' | 'OFF';
	cartridgeId: string | null;
	paused: boolean;
	mode: 'ANARCHY' | 'DEMOCRACY';
	queueDepth: number;
	tally: Tally | null;
	modeVotes: Record<string, number>;
	launchAt: string | null; // ISO instant the console powers itself on (the countdown), or null
};

/** Time left as "2d 04:12:09", or "04:12:09" under a day; "00:00:00" once it is over. */
export function countdown(ms: number): string {
	const s = Math.max(0, Math.ceil(ms / 1000));
	const pad = (n: number) => String(n).padStart(2, '0');
	const hms = `${pad(Math.floor(s / 3600) % 24)}:${pad(Math.floor(s / 60) % 60)}:${pad(s % 60)}`;
	const days = Math.floor(s / 86400);
	return days ? `${days}d ${hms}` : hms;
}

const KEYS: Record<string, Button> = {
	ArrowUp: 'up',
	ArrowDown: 'down',
	ArrowLeft: 'left',
	ArrowRight: 'right',
	z: 'a',
	Z: 'a',
	x: 'b',
	X: 'b',
	Enter: 'start',
	Shift: 'select',
	Backspace: 'select'
};

export type KeyLike = {
	key: string;
	repeat?: boolean;
	ctrlKey?: boolean;
	metaKey?: boolean;
	altKey?: boolean;
	target?: EventTarget | null;
};

/** True for keydowns a page shortcut must ignore: typing in a field, modifier shortcuts, held keys. */
export function notForShortcuts(e: KeyLike): boolean {
	if (e.repeat || e.ctrlKey || e.metaKey || e.altKey) return true;
	const t = e.target as HTMLElement | null;
	const tag = t?.tagName;
	return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || !!t?.isContentEditable;
}

/** The pad button for a keydown, or null when it should be left alone (typing, shortcuts, held keys). */
export function keyToButton(e: KeyLike): Button | null {
	if (notForShortcuts(e)) return null;
	const tag = (e.target as HTMLElement | null)?.tagName;
	// Enter on a focused button or link already activates it.
	if (e.key === 'Enter' && (tag === 'BUTTON' || tag === 'A')) return null;
	return KEYS[e.key] ?? null;
}

/** Tally rows, most votes first; ties keep first-vote order (the backend's tie-break). */
export function tallyRows(tally: Record<string, number>) {
	const total = Object.values(tally).reduce((s, n) => s + n, 0);
	return Object.entries(tally)
		.map(([button, votes]) => ({ button, votes, pct: total ? Math.round((votes * 100) / total) : 0 }))
		.sort((x, y) => y.votes - x.votes);
}

/** Reconnect delay: 0.5 s doubling up to 30 s. */
export const backoff = (attempt: number) => Math.min(30_000, 500 * 2 ** attempt);

/** "LEFT,LEFT,DOWN" -> "LEFT ×2 DOWN". */
export function formatCommand(cmd: string): string {
	const out: [string, number][] = [];
	for (const b of cmd.split(',')) {
		const last = out[out.length - 1];
		if (last && last[0] === b) last[1]++;
		else out.push([b, 1]);
	}
	return out.map(([b, n]) => (n > 1 ? `${b} ×${n}` : b)).join(' ');
}

/** User ids are e-mails; show the local part. */
export const shortUser = (id: string) => id.split('@')[0];

/**
 * Walking with held arrows: after `released` goes up, what to walk next. `still` = arrows still held, oldest
 * first; `walking` = the direction being walked. Returns 'keep' (releasing an arrow already switched away from),
 * another direction to walk, or null to stop.
 */
export function afterArrowRelease(still: Button[], walking: Button | null, released: Button): Button | 'keep' | null {
	if (walking && walking !== released) return 'keep';
	return still.at(-1) ?? null;
}
