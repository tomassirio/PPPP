<!-- What PPPP is, for first-time visitors, and how the shared game is going right now (public teaser). -->
<script lang="ts">
	import { counter, type Teaser } from '#lib/teaser.js';

	let { teaser }: { teaser: Teaser | null } = $props();

	const badges = $derived(counter(teaser, 'Badges'));
	const caught = $derived(counter(teaser, 'Pokédex'));
</script>

<section class="about" aria-labelledby="about-title">
	<h2 id="about-title">One Game Boy. Everyone plays.</h2>
	<p class="pitch">
		Pokémon Red, shared live. Every press goes to the same game: <b>Anarchy</b>, every press counts; <b>Democracy</b>, the
		vote decides.
	</p>
	{#if teaser}
		<ul class="stats" aria-label="The game right now">
			<li><span class="dot" aria-hidden="true"></span><b>{teaser.online}</b> {teaser.online === 1 ? 'trainer' : 'trainers'} online</li>
			{#if badges}<li><b>{badges.value}</b>/{badges.max} badges</li>{/if}
			{#if caught}<li><b>{caught.value}</b> caught</li>{/if}
			{#if teaser.playTime}<li><b>{teaser.playTime}</b> played</li>{/if}
		</ul>
	{/if}
</section>

<style>
	.about {
		position: relative;
		z-index: 1;
		max-width: 300px;
		padding: 18px 20px;
		border-radius: 14px;
		background: color-mix(in oklab, var(--panel), transparent 15%);
		border: 1px solid var(--border);
		backdrop-filter: blur(8px);
		box-shadow: 0 10px 30px rgb(0 0 0 / 0.3);
		color: var(--text);
	}
	h2 {
		margin: 0 0 8px;
		font: 12px/1.5 var(--pixel-font);
	}
	.pitch {
		margin: 0;
		font-size: 14px;
		line-height: 1.5;
		color: var(--muted);
	}
	.stats {
		margin: 12px 0 0;
		padding: 12px 0 0;
		border-top: 1px solid var(--border);
		list-style: none;
		display: flex;
		flex-wrap: wrap;
		gap: 6px 14px;
		font-size: 13px;
		color: var(--muted);
	}
	.stats b {
		color: var(--text);
	}
	.dot {
		display: inline-block;
		width: 8px;
		height: 8px;
		margin-right: 6px;
		border-radius: 50%;
		background: #3fb950;
		box-shadow: 0 0 6px #3fb950;
	}
	/* Phones: one compact strip under the device. */
	@media (max-width: 899px) {
		.about {
			max-width: min(410px, calc(100vw - 24px));
			box-sizing: border-box;
			padding: 10px 14px;
		}
		h2 {
			font-size: 10px;
			margin: 0;
		}
		.pitch {
			display: none;
		}
		.stats {
			margin-top: 6px;
			padding-top: 6px;
			font-size: 12px;
		}
	}
</style>
