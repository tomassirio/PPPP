// Pure chat list logic: Redis stream ids ("<ms>-<seq>") order messages; ids dedupe them.

export type ChatMessage = {
	id: string;
	user: string;
	name: string;
	text: string;
	kind: string; // 'USER' | 'SYSTEM' (everyone) | 'ADMIN' (admins only)
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

/**
 * Pokémon Red towns in their gym badge's colour (Celadon's Rainbow Badge is drawn as a gradient, 'rainbow').
 * Towns without a gym get a colour of their own. Routes and other places stay plain.
 */
export const TOWN_COLORS: Record<string, string> = {
	'Pallet Town': '#e8eef2',
	'Viridian City': '#4cd17a', // Earth
	'Pewter City': '#b4bcc6', // Boulder
	'Cerulean City': '#5cb8ff', // Cascade
	'Lavender Town': '#b9a3ff',
	'Vermilion City': '#ffa53a', // Thunder
	'Celadon City': 'rainbow', // Rainbow
	'Fuchsia City': '#ff8ab8', // Soul
	'Saffron City': '#f5c542', // Marsh
	'Cinnabar Island': '#ff6b5b', // Volcano
	'Indigo Plateau': '#8f9dff'
};

export type Part = { text: string; mode?: 'anarchy' | 'democracy'; town?: string };

const HIGHLIGHT = new RegExp(`\\b(ANARCHY|DEMOCRACY|${Object.keys(TOWN_COLORS).join('|')})\\b`);

/** Splits a system line so mode names and towns can be coloured; the parts' text rejoins to the input. */
export function systemParts(text: string): Part[] {
	return text
		.split(HIGHLIGHT)
		.filter(Boolean)
		.map((t) =>
			t === 'ANARCHY' || t === 'DEMOCRACY'
				? { text: t, mode: t.toLowerCase() as Part['mode'] }
				: t in TOWN_COLORS
					? { text: t, town: TOWN_COLORS[t] }
					: { text: t }
		);
}
