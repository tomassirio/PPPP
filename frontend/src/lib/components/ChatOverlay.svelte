<!-- Phones: new player chat messages float over the bottom of the game screen and fade out. Text only (never
     HTML). Off on the Chat tab (`hidden`) or when muted from the bezel. Place inside a positioned container. -->
<script lang="ts">
	import { chatOverlay } from '#lib/chatOverlay.svelte.ts';
	import { nameColor, type ChatMessage } from '#lib/chat/messages.ts';
	import { on } from '#lib/events.ts';

	let { hidden = false }: { hidden?: boolean } = $props();

	const SHOW_MS = 6000;
	let lines = $state<ChatMessage[]>([]);

	$effect(() =>
		on('CHAT_MESSAGE', (m: ChatMessage) => {
			if (m.kind !== 'USER' || hidden || !chatOverlay.on) return;
			lines = [...lines, m].slice(-3);
			setTimeout(() => (lines = lines.filter((l) => l.id !== m.id)), SHOW_MS);
		})
	);
	$effect(() => {
		if (hidden || !chatOverlay.on) lines = [];
	});
</script>

<ul class="overlay" aria-hidden="true">
	{#each lines as m (m.id)}
		<li><b style:color={nameColor(m.user)}>{m.name}</b> {m.text}</li>
	{/each}
</ul>

<style>
	.overlay {
		display: none;
	}
	@media (max-width: 760px), (orientation: landscape) and (max-height: 520px) {
		.overlay {
			position: absolute;
			left: 24px;
			right: 24px;
			bottom: 56px; /* above the LIVE strip */
			z-index: 3;
			margin: 0;
			padding: 0;
			list-style: none;
			display: flex;
			flex-direction: column;
			align-items: flex-start;
			gap: 4px;
			pointer-events: none;
		}
	}
	li {
		max-width: 100%;
		padding: 4px 8px;
		border-radius: 8px;
		background: rgb(17 22 26 / 0.72);
		color: #fff;
		font-size: 12px;
		line-height: 1.35;
		overflow-wrap: anywhere;
		animation: fade 6s ease-in forwards;
	}
	b {
		font-weight: 700;
		margin-right: 4px;
	}
	/* Fully visible for most of its life, then fades away. */
	@keyframes fade {
		0% {
			opacity: 0;
		}
		5%,
		75% {
			opacity: 1;
		}
		100% {
			opacity: 0;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		li {
			animation: none;
		}
	}
</style>
