// Colour theme: 'auto' follows the OS (no data-theme attribute), 'light'/'dark' pin it.
// app.html applies the saved choice before first paint so there is no flash.
export type Theme = 'auto' | 'light' | 'dark';
const KEY = 'pppp.theme';

function saved(): Theme {
	try {
		const t = localStorage.getItem(KEY);
		return t === 'light' || t === 'dark' ? t : 'auto';
	} catch {
		return 'auto';
	}
}

export const theme = $state({ value: saved() });

export function setTheme(t: Theme) {
	theme.value = t;
	if (t === 'auto') delete document.documentElement.dataset.theme;
	else document.documentElement.dataset.theme = t;
	try {
		if (t === 'auto') localStorage.removeItem(KEY);
		else localStorage.setItem(KEY, t);
	} catch {
		/* private mode: not remembered */
	}
}
