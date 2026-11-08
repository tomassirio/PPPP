import { ApiError } from '#lib/api.ts';

export type ConsoleStatus = {
	power: 'ON' | 'OFF';
	cartridgeId: string | null;
	paused: boolean;
	speed: number | null; // GET /state has no speed; known after the first CONSOLE_CHANGED or console action
};

// Shared by the admin panels: console status (cartridge/save-state panels depend on it) and the aria-live notice.
export const consoleStatus = $state<ConsoleStatus>({
	power: 'OFF',
	cartridgeId: null,
	paused: false,
	speed: null
});
export const notice = $state<{ text: string; error: boolean }>({ text: '', error: false });

export function setConsole(s: Partial<ConsoleStatus>) {
	Object.assign(consoleStatus, s);
}

/**
 * Runs one admin action and reports it in the live region.
 * `confirmText` adds a confirm() step for destructive actions; returns the result, or undefined if cancelled/failed.
 * `quiet` reports only failures (for loads).
 */
export async function act<T>(
	label: string,
	fn: () => Promise<T>,
	confirmText?: string,
	quiet = false
): Promise<T | undefined> {
	if (confirmText && !confirm(confirmText)) return undefined;
	try {
		const result = await fn();
		if (!quiet) {
			notice.text = `${label}: done`;
			notice.error = false;
		}
		return result;
	} catch (e) {
		notice.text = `${label} failed: ${e instanceof ApiError ? e.detail : String(e)}`;
		notice.error = true;
		return undefined;
	}
}
