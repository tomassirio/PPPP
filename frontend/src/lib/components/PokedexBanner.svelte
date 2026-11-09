<!-- A side banner of the Pokédex (numbers from..to): unseen = silhouette with "?", seen = sprite, caught = sprite
     plus a Poké Ball. Sprites come from the ROM via /api/v1/game/icons/{n}.png. Fills its container. -->
<script lang="ts">
	import { game, watchGame } from '#lib/game.svelte.ts';

	let { from, to }: { from: number; to: number } = $props();

	$effect(() => watchGame());

	const dex = $derived(game.summary?.collection);
	const seen = $derived(new Set(dex?.seen));
	const owned = $derived(new Set(dex?.owned));
	const numbers = $derived(dex ? Array.from({ length: Math.min(to, dex.size) - from + 1 }, (_, i) => from + i) : []);
	const pad = (n: number) => String(n).padStart(3, '0');
</script>

{#if dex}
	<ol class="banner" aria-label="Pokédex {from} to {to}" style:--rows={Math.ceil(numbers.length / 4)}>
		{#each numbers as n (n)}
			{@const name = dex.names[n - 1]}
			<li class:seen={seen.has(n)} class:owned={owned.has(n)} title="#{pad(n)} {name ?? '???'}">
				<img src="/api/v1/game/icons/{n}.png" alt={name ? `${name}${owned.has(n) ? ', caught' : ', seen'}` : `#${n}, not seen yet`} loading="lazy" />
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
{/if}

<style>
	/* Square tiles, 4 across, as big as the banner's height allows. */
	.banner {
		container-type: size;
		height: 100%;
		margin: 0;
		padding: 0;
		list-style: none;
		display: grid;
		--cell: min(calc((100cqw - 3 * 6px) / 4), calc((100cqh - (var(--rows) - 1) * 6px) / var(--rows)));
		grid-template-columns: repeat(4, var(--cell));
		grid-auto-rows: var(--cell);
		gap: 6px;
		justify-content: center;
		align-content: center;
	}
	li {
		position: relative;
		border-radius: 6px;
		background: var(--panel);
		border: 1px solid var(--border);
		display: flex;
		align-items: center;
		justify-content: center;
		overflow: hidden;
	}
	img {
		width: 88%;
		height: 88%;
		object-fit: contain;
		image-rendering: pixelated;
		/* Unseen: only a faint outline of the real sprite. */
		filter: brightness(0) invert(1) opacity(0.14);
	}
	li.seen {
		background: #e2f3d6;
		border-color: transparent;
	}
	li.seen img {
		filter: none;
	}
	.unknown {
		position: absolute;
		font: 700 calc(var(--cell) * 0.38) / 1 var(--pixel-font);
		color: var(--muted);
	}
	.ball {
		position: absolute;
		right: 2px;
		bottom: 2px;
		width: 30%;
		height: 30%;
	}
</style>
