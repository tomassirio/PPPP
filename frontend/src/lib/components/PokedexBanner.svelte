<!-- A side banner of the Pokédex (numbers from..to): unseen = silhouette with "?", seen = sprite, caught = sprite
     plus a Poké Ball. Sprites come from the ROM via /api/v1/game/icons/{n}.png. Fills its container (fill), or a
     plain grid of `columns` across with its natural height (phones' Party tab). -->
<script lang="ts">
	import { game, watchGame } from '#lib/game.svelte.ts';

	let { from, to, columns = 4, fill = true }: { from: number; to: number; columns?: number; fill?: boolean } = $props();

	$effect(() => watchGame());

	const dex = $derived(game.summary?.collection);
	const seen = $derived(new Set(dex?.seen));
	const owned = $derived(new Set(dex?.owned));
	const numbers = $derived(dex ? Array.from({ length: Math.min(to, dex.size) - from + 1 }, (_, i) => from + i) : []);
	const pad = (n: number) => String(n).padStart(3, '0');
</script>

{#if dex}
	<div class="fit" class:grid={!fill}>
	<ol class="banner" aria-label="Pokédex {from} to {to}" style:--rows={Math.ceil(numbers.length / columns)} style:--cols={columns}>
		{#each numbers as n (n)}
			{@const name = dex.names[n - 1]}
			<li class:seen={seen.has(n)} class:owned={owned.has(n)} title="#{pad(n)} {name ?? '???'}">
				{#if seen.has(n)}
					<img src="/api/v1/game/icons/{n}.png" alt="{name}{owned.has(n) ? ', caught' : ', seen'}" loading="lazy" />
				{:else}
					<span class="shape" role="img" aria-label="#{n}, not seen yet" style:--src="url(/api/v1/game/icons/{n}.png)"></span>
				{/if}
				{#if !seen.has(n)}<span class="unknown" aria-hidden="true">?</span>{/if}
				{#if owned.has(n)}
					<svg class="ball" viewBox="0 0 16 16" aria-hidden="true">
						<circle cx="8" cy="8" r="7" fill="#fff" stroke="#111" stroke-width="1.5" />
						<path d="M1 8a7 7 0 0 1 14 0z" fill="#e3350d" stroke="#111" stroke-width="1.5" />
						<line x1="1" y1="8" x2="15" y2="8" stroke="#111" stroke-width="1.5" />
						<circle cx="8" cy="8" r="2.2" fill="#fff" stroke="#111" stroke-width="1.5" />
					</svg>
				{/if}
			</li>
		{/each}
	</ol>
	</div>
{/if}

<style>
	/* .fit is the size container: square cells, 4 across, as big as its height allows; no tiles. */
	.fit {
		container-type: size;
		height: 100%;
	}
	/* Grid: cells from the width alone, rows as tall as they need. */
	.fit.grid {
		container-type: inline-size;
		height: auto;
	}
	.fit.grid .banner {
		--cell: calc((100cqw - (var(--cols) - 1) * var(--gap)) / var(--cols));
		height: auto;
	}
	.banner {
		margin: 0;
		padding: 0;
		list-style: none;
		display: grid;
		--gap: 4px;
		--cell: min(
			calc((100cqw - (var(--cols) - 1) * var(--gap)) / var(--cols)),
			calc((100cqh - (var(--rows) - 1) * var(--gap)) / var(--rows))
		);
		grid-template-columns: repeat(var(--cols), var(--cell));
		grid-auto-rows: var(--cell);
		gap: var(--gap);
		justify-content: center;
		align-content: center;
		height: 100%;
	}
	li {
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	img,
	.shape {
		width: 100%;
		height: 100%;
	}
	img {
		object-fit: contain;
		image-rendering: pixelated;
	}
	/* Unseen: the sprite's shape filled with the text colour, faint, so it works on light and dark themes. */
	.shape {
		background: var(--text);
		opacity: 0.16;
		-webkit-mask: var(--src) center / contain no-repeat;
		mask: var(--src) center / contain no-repeat;
	}
	.unknown {
		position: absolute;
		font: 700 calc(var(--cell) * 0.34) / 1 var(--pixel-font);
		color: var(--muted);
	}
	.ball {
		position: absolute;
		right: 0;
		bottom: 0;
		width: 30%;
		height: 30%;
	}
</style>
