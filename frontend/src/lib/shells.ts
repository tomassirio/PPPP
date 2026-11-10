// Controller shells a player can pick for the pad (remembered per browser). Original = the classic Game Boy;
// the rest approximate Game Boy Color shells. Translucent "clear" shells use alpha. Button colours are hex.

export type Shell = { id: string; name: string; shell: string; dpad: string; ab: string; pill: string };

export const SHELLS: Shell[] = [
	{ id: 'original', name: 'Original', shell: '#d8d3c4', dpad: '#2b2d42', ab: '#bc4b51', pill: '#8d8a80' },
	{ id: 'atomic', name: 'Atomic', shell: 'rgb(150 140 178 / 0.6)', dpad: '#4d4766', ab: '#6a6488', pill: '#5b5577' },
	{ id: 'berry', name: 'Berry', shell: '#d9324f', dpad: '#2a2a2e', ab: '#8e1c33', pill: '#7a1a2d' },
	{ id: 'black', name: 'Black', shell: '#2e2e32', dpad: '#141416', ab: '#141416', pill: '#1c1c1f' },
	{ id: 'clear-black', name: 'Clear Black', shell: 'rgb(70 70 76 / 0.7)', dpad: '#222225', ab: '#2c2c30', pill: '#2c2c30' },
	{ id: 'clear-blue', name: 'Clear Blue', shell: 'rgb(40 140 225 / 0.65)', dpad: '#163f6b', ab: '#1d5f9c', pill: '#1d5f9c' },
	{ id: 'clear-green', name: 'Clear Green', shell: 'rgb(70 205 60 / 0.65)', dpad: '#1f5c1a', ab: '#2e8a26', pill: '#2e8a26' },
	{ id: 'clear-orange', name: 'Clear Orange', shell: 'rgb(255 105 30 / 0.72)', dpad: '#7a2a08', ab: '#c2410c', pill: '#a8380b' },
	{ id: 'clear-purple', name: 'Clear Purple', shell: 'rgb(95 75 210 / 0.72)', dpad: '#261b6b', ab: '#3b2a9a', pill: '#3b2a9a' },
	{ id: 'clear', name: 'Clear', shell: 'rgb(235 235 242 / 0.45)', dpad: '#b8b8c2', ab: '#c8c8d2', pill: '#c8c8d2' },
	{ id: 'glow-blue', name: 'Glow Blue', shell: '#b9d6e6', dpad: '#7fa6bd', ab: '#8fb5cc', pill: '#8fb5cc' },
	{ id: 'glow-green', name: 'Glow Green', shell: '#9ed6c8', dpad: '#5e9e8f', ab: '#6fb3a3', pill: '#6fb3a3' },
	{ id: 'pikachu', name: 'Pikachu Clear Orange', shell: 'rgb(255 90 20 / 0.78)', dpad: '#7a2a08', ab: '#f7c531', pill: '#a8380b' },
	{ id: 'light-blue', name: 'Light Blue', shell: '#8fd0d6', dpad: '#3f8a92', ab: '#5aaab2', pill: '#5aaab2' },
	{ id: 'light-pink', name: 'Light Pink', shell: '#f1c3cc', dpad: '#b97a88', ab: '#d99aa8', pill: '#d99aa8' },
	{ id: 'light-purple', name: 'Light Purple', shell: '#cfc8f2', dpad: '#7d70c4', ab: '#a69ae0', pill: '#a69ae0' },
	{ id: 'purple', name: 'Purple', shell: '#5145b8', dpad: '#221a66', ab: '#2e2580', pill: '#2e2580' },
	{ id: 'snes-gray', name: 'SNES Gray', shell: '#d4d2d6', dpad: '#4a4a4f', ab: '#6b3fa0', pill: '#8f8d94' },
	{ id: 'green', name: 'Green', shell: '#8ccf48', dpad: '#3e6e1a', ab: '#5c9a2a', pill: '#5c9a2a' },
	{ id: 'neon-yellow', name: 'Neon Yellow', shell: '#c9f03a', dpad: '#6b8a12', ab: '#8fb31f', pill: '#8fb31f' },
	{ id: 'clear-red', name: 'Clear Red', shell: 'rgb(230 40 52 / 0.72)', dpad: '#6b1016', ab: '#9e1b24', pill: '#9e1b24' },
	{ id: 'pk-limited', name: 'PK Limited', shell: '#f7c531', dpad: '#1f3fa8', ab: '#1f3fa8', pill: '#1f3fa8' },
	{ id: 'teal', name: 'Teal', shell: '#1aa7a7', dpad: '#0a4f4f', ab: '#0e6f6f', pill: '#0e6f6f' },
	{ id: 'white', name: 'White', shell: '#f2f2f2', dpad: '#7a7a7a', ab: '#9a9a9a', pill: '#9a9a9a' },
	{ id: 'yellow', name: 'Yellow', shell: '#f6b93b', dpad: '#7a5208', ab: '#c98a12', pill: '#c98a12' }
];

const KEY = 'pppp.shell';

export function savedShell(): Shell {
	try {
		const id = localStorage.getItem(KEY);
		return SHELLS.find((s) => s.id === id) ?? SHELLS[0];
	} catch {
		return SHELLS[0];
	}
}

export function saveShell(id: string) {
	try {
		localStorage.setItem(KEY, id);
	} catch {
		/* private mode: not remembered */
	}
}

/** Dark or light text for a background: '#rrggbb' or 'rgb(r g b / a)' (alpha ignored). WCAG luminance. */
export function inkFor(color: string): string {
	const rgb = color.startsWith('#')
		? [1, 3, 5].map((i) => parseInt(color.slice(i, i + 2), 16))
		: (color.match(/[\d.]+/g) ?? ['0', '0', '0']).slice(0, 3).map(Number);
	const [r, g, b] = rgb.map((v) => {
		const c = v / 255;
		return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
	});
	return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.3 ? '#1d1f1c' : '#ffffff';
}

/** CSS custom properties for the pad. */
export function shellVars(s: Shell): string {
	return [
		`--shell:${s.shell}`,
		`--dpad:${s.dpad}`,
		`--dpad-ink:${inkFor(s.dpad)}`,
		`--ab:${s.ab}`,
		`--ab-ink:${inkFor(s.ab)}`,
		`--pill:${s.pill}`,
		`--pill-ink:${inkFor(s.pill)}`
	].join(';');
}
