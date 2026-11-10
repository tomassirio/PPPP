<!-- Bezel button (phones only): show or mute the chat overlay on the game screen. -->
<script lang="ts">
	import { chatOverlay, toggleChatOverlay } from '#lib/chatOverlay.svelte.ts';
</script>

<button
	type="button"
	class="overlay-toggle"
	aria-pressed={chatOverlay.on}
	aria-label="Chat on screen"
	title={chatOverlay.on ? 'Chat on screen: on' : 'Chat on screen: muted'}
	onclick={toggleChatOverlay}
>
	<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
		<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z" />
		{#if !chatOverlay.on}<path d="M4 4l16 16" />{/if}
	</svg>
</button>

<style>
	.overlay-toggle {
		display: none;
		width: 28px;
		height: 28px;
		align-items: center;
		justify-content: center;
		padding: 0;
		border: none;
		border-radius: 6px;
		background: transparent;
		color: #9aa0a6;
	}
	.overlay-toggle:hover {
		color: #d6dade;
	}
	/* On: the player's plate colour (set on the play page), else the accent. */
	.overlay-toggle[aria-pressed='true'] {
		color: var(--plate, var(--accent));
	}
	.overlay-toggle:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	@media (max-width: 760px), (orientation: landscape) and (max-height: 520px) {
		.overlay-toggle {
			display: inline-flex;
		}
	}
</style>
