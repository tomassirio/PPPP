// Executed presses (INPUT_EXECUTED) and players' photos, shared by Up next and the democracy view so the
// "Last pressed" list survives mode switches.
import { api } from './api';
import { on } from './events';

export type Press = { id: string; user: string; command: string };

export const presses = $state<{ recent: Press[]; pictures: Record<string, string> }>({ recent: [], pictures: {} });

let watchers = 0;
let off: (() => void) | undefined;
let picturesAt = 0;

/** Player id -> Google photo (GET /players/pictures); again when someone unknown shows up, at most every 10 s. */
export async function loadPictures(force = false) {
	if (!force && Date.now() - picturesAt < 10_000) return;
	picturesAt = Date.now();
	try {
		presses.pictures = await api<Record<string, string>>('GET', '/players/pictures');
	} catch {
		/* initials instead */
	}
}

export function needPicture(user: string) {
	if (!(user in presses.pictures)) loadPictures();
}

/** Keeps `presses.recent` (last 30, oldest first) live while watched; returns the stop function. */
export function watchPresses(): () => void {
	if (watchers++ === 0) {
		loadPictures(true);
		off = on('INPUT_EXECUTED', (e: Press) => {
			presses.recent = [...presses.recent, e].slice(-30);
			needPicture(e.user);
		});
	}
	return () => {
		if (--watchers === 0) off?.();
	};
}
