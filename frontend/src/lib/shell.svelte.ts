// The player's chosen controller shell, shared so the page can echo its colour (phones: the selected tab, the
// bezel's chat toggle). Remembered per browser.
import { SHELLS, saveShell, savedShell, type Shell } from './shells';

export const currentShell = $state<{ value: Shell }>({ value: savedShell() });

export function pickShell(id: string) {
	currentShell.value = SHELLS.find((s) => s.id === id) ?? SHELLS[0];
	saveShell(currentShell.value.id);
}
