// Pokédex banners beside the game on wide desktops: on unless the player turned them off (remembered per browser).
const KEY = 'pppp.dexBanners';

function saved(): boolean {
	try {
		return localStorage.getItem(KEY) !== 'off';
	} catch {
		return true;
	}
}

export const dexBanners = $state({ on: saved() });

export function setDexBanners(on: boolean) {
	dexBanners.on = on;
	try {
		localStorage.setItem(KEY, on ? 'on' : 'off');
	} catch {
		/* private mode: not remembered */
	}
}
