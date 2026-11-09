// The inserted game's summary (GET /api/v1/game/state, kept current by GAME_STATE), shared by the party box and
// the Pokédex banners. null while the game has no profile.
import { api } from './api';
import { on } from './events';

/** Shape of GameSummary (backend model). */
export type Member = { name: string; species: string; level: number; hp: number; maxHp: number; status: string | null; icon: number };
export type GameSummary = {
	game: string;
	counters: { label: string; value: number; max: number }[];
	party: Member[];
	badges: { name: string; place: string; earned: boolean }[];
	collection: { size: number; seen: number[]; owned: number[]; names: (string | null)[] };
};

export const game = $state<{ summary: GameSummary | null }>({ summary: null });

let watchers = 0;
let offs: (() => void)[] = [];

function refresh() {
	api<GameSummary | undefined>('GET', '/game/state')
		.then((s) => (game.summary = s ?? null))
		.catch(() => {});
}

/** Keeps `game.summary` live while at least one component watches; returns the stop function. */
export function watchGame(): () => void {
	if (watchers++ === 0) {
		refresh();
		offs = [
			on('GAME_STATE', (s: GameSummary) => (game.summary = s)),
			on('CONSOLE_CHANGED', refresh) // another cartridge may have no profile
		];
	}
	return () => {
		if (--watchers === 0) {
			offs.forEach((off) => off());
			offs = [];
		}
	};
}
