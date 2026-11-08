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
};

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

type KeyLike = {
	key: string;
	repeat?: boolean;
	ctrlKey?: boolean;
	metaKey?: boolean;
	altKey?: boolean;
	target?: EventTarget | null;
};

/** The pad button for a keydown, or null when it should be left alone (typing, shortcuts, held keys). */
export function keyToButton(e: KeyLike): Button | null {
	if (e.repeat || e.ctrlKey || e.metaKey || e.altKey) return null;
	const t = e.target as HTMLElement | null;
	const tag = t?.tagName;
	if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || t?.isContentEditable) return null;
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
