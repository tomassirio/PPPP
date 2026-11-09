// Phones: whether new chat messages float over the game screen. Remembered per browser (on by default).
const KEY = 'pppp.chatOverlay';

function saved(): boolean {
	try {
		return localStorage.getItem(KEY) !== 'off';
	} catch {
		return true;
	}
}

export const chatOverlay = $state({ on: saved() });

export function toggleChatOverlay() {
	chatOverlay.on = !chatOverlay.on;
	try {
		localStorage.setItem(KEY, chatOverlay.on ? 'on' : 'off');
	} catch {
		/* private mode: not remembered */
	}
}
