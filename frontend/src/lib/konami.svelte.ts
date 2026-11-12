// The Konami code on /play: the player's own presses (taps or keys, not held-walk repeats) are matched here.
const CODE = ['up', 'up', 'down', 'down', 'left', 'right', 'left', 'right', 'b', 'a'];
let typed: string[] = [];

/** `at` changes each time the code is entered; PokeballRain watches it. */
export const konami = $state({ at: 0 });

export function konamiPress(button: string) {
	typed = [...typed, button].slice(-CODE.length);
	if (typed.join() !== CODE.join()) return;
	typed = [];
	konami.at = Date.now();
}
