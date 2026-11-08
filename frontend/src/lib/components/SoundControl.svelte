<!-- Sound on/off (M) and volume for the live game audio. Off by default; the choice is remembered. -->
<script lang="ts">
	import { createPlayer, loadPrefs, savePrefs } from '#lib/audio.ts';
	import { notForShortcuts } from '#lib/play.ts';

	const prefs = loadPrefs();
	let on = $state(prefs.on);
	let volume = $state(prefs.volume);
	const player = createPlayer();
	player.setVolume(prefs.volume);

	function toggle() {
		on = !on;
		savePrefs({ on, volume });
		if (on) player.start();
		else player.stop();
	}

	function setVolume() {
		player.setVolume(volume);
		savePrefs({ on, volume });
	}

	function onkeydown(e: KeyboardEvent) {
		if ((e.key === 'm' || e.key === 'M') && !notForShortcuts(e)) toggle();
	}

	$effect(() => {
		// Remembered as on: browsers only let audio start after a gesture, so wait for the first one.
		const resume = () => on && player.start();
		if (on) {
			window.addEventListener('pointerdown', resume, { once: true });
			window.addEventListener('keydown', resume, { once: true });
		}
		return () => {
			window.removeEventListener('pointerdown', resume);
			window.removeEventListener('keydown', resume);
			player.stop();
		};
	});
</script>

<svelte:window {onkeydown} />

<div class="sound">
	<button type="button" aria-pressed={on} aria-label="Sound" title="Sound (M)" onclick={toggle}>
		<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
			<path d="M4 9h4l5-4v14l-5-4H4z" fill="currentColor" />
			{#if on}
				<path d="M16 9a4 4 0 0 1 0 6M18.5 6.5a7.5 7.5 0 0 1 0 11" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
			{:else}
				<path d="M16 9l5 6M21 9l-5 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
			{/if}
		</svg>
	</button>
	<label>
		<span>Volume</span>
		<input type="range" min="0" max="1" step="0.05" bind:value={volume} oninput={setVolume} />
	</label>
</div>

<style>
	.sound {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: 12px;
		font-size: 13px;
		color: var(--muted);
	}
	button {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 40px;
		height: 40px;
		border-radius: 8px;
		border: 1px solid var(--border);
		background: var(--panel);
		color: var(--muted);
	}
	button[aria-pressed='true'] {
		color: var(--accent);
		border-color: var(--accent);
	}
	label {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	input {
		width: 120px;
		accent-color: var(--accent);
	}
</style>
