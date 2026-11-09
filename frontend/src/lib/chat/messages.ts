// Pure chat list logic: Redis stream ids ("<ms>-<seq>") order messages; ids dedupe them.

export type ChatMessage = {
	id: string;
	user: string;
	name: string;
	text: string;
	kind: string; // 'USER' | 'SYSTEM'
	command: boolean;
	at: number;
};

/** Orders stream ids numerically ("10-0" after "9-5"). */
export function compareIds(a: string, b: string): number {
	const [am, as] = a.split('-').map(Number);
	const [bm, bs] = b.split('-').map(Number);
	return am - bm || as - bs;
}

/** Merges incoming messages (any order) into the list: oldest first, no duplicate ids. */
export function merge(list: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
	const byId = new Map(list.map((m) => [m.id, m]));
	for (const m of incoming) byId.set(m.id, m);
	return [...byId.values()].sort((a, b) => compareIds(a.id, b.id));
}

export const remove = (list: ChatMessage[], id: string) => list.filter((m) => m.id !== id);

const NAME_COLORS = ['#f2c14e', '#7fd1c7', '#f4a259', '#c9a7eb', '#a7d676', '#8ab4f8'];

/** Stable per-user name color from the design palette. */
export function nameColor(user: string): string {
	let h = 0;
	for (const c of user) h = (h * 31 + c.charCodeAt(0)) | 0;
	return NAME_COLORS[Math.abs(h) % NAME_COLORS.length];
}

/** Splits text so the mode names can be coloured; rejoined parts equal the input. */
export const modeParts = (text: string) => text.split(/\b(ANARCHY|DEMOCRACY)\b/).filter(Boolean);
