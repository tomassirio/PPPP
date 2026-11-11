<!-- Behind the sign-in device: an original handheld-era overworld in 16px tiles (trees, a crossroads with a sign, tall
     grass, a pond, flowers, a couple of item balls), tinted by the local hour. A trainer walks the path with the party
     in tow, wild Pokémon jump out of the grass now and then, and flying-type shadows pass over. -->
<script lang="ts">
	import type { Teaser } from '#lib/teaser.js';

	let { teaser }: { teaser: Teaser | null } = $props();

	const TILE = 48; // one 16px tile drawn at 3x
	const DEX = 151;
	const PLAYER = '/api/v1/game/icons/player.png';

	let playerOk = $state(false);

	let w = $state(1280);
	let h = $state(800);

	const hour = new Date().getHours();
	const time = hour < 6 || hour >= 21 ? 'night' : hour < 9 || hour >= 18 ? 'dusk' : 'day';
	const still = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;

	const party = $derived((teaser?.party ?? []).filter((m) => m.icon > 0).slice(0, 6));

	type Kind = 'grass' | 'tall' | 'tree' | 'path' | 'flower' | 'water' | 'sign' | 'ball';
	type Cell = { x: number; y: number; kind: Kind; n: number };

	// Same screen size, same map: a cheap hash instead of Math.random.
	const hash = (x: number, y: number) => (((x * 73856093) ^ (y * 19349663)) >>> 0) % 997;

	const cols = $derived(Math.ceil(w / TILE) + 1);
	const rows = $derived(Math.ceil(h / TILE) + 1);
	const pathX = $derived(Math.max(2, Math.floor(cols * 0.12)));
	const pathY = $derived(Math.max(4, rows - 5));
	const pond = $derived({ x: Math.floor(cols * 0.8), y: Math.floor(rows * 0.5) });

	const cells = $derived.by(() => {
		const out: Cell[] = [];
		const C = cols,
			R = rows;
		for (let y = 0; y < R; y++)
			for (let x = 0; x < C; x++) {
				const n = hash(x, y);
				let kind: Kind = 'grass';
				if (x === pathX || y === pathY) kind = 'path';
				else if (y < 2 || y === R - 1 || x === 0 || x === C - 2) kind = 'tree';
				else if (x === pathX + 1 && y === pathY - 1) kind = 'sign';
				else if ((x === pathX + 5 && y === 3) || (x === C - 5 && y === pathY - 1)) kind = 'ball';
				else if (x >= Math.floor(C * 0.7) && x < C - 3 && y >= 3 && y <= Math.floor(R * 0.35)) kind = 'tall';
				else if (x >= Math.floor(C * 0.45) && x < Math.floor(C * 0.9) && y > pathY && y < R - 1) kind = 'tall';
				else if (x > pathX && x < pathX + 4 && y >= 3 && y < pathY - 1) kind = 'tall';
				else if (x >= pond.x && x < pond.x + 3 && y >= pond.y && y < pond.y + 3) kind = 'water';
				else if (n < 50) kind = 'tree';
				else if (n < 120) kind = 'flower';
				out.push({ x, y, kind, n });
			}
		return out;
	});

	// Wild encounters: every few seconds a tuft of tall grass (preferably one not hidden behind the device or the
	// card) shakes, a "!" pops up, and a random Pokémon hops out and back in. Sprites come from the ROM; no game, no
	// sprite, the "!" still shows.
	let encounter = $state<{ x: number; y: number; id: number; key: number } | null>(null);
	$effect(() => {
		if (still) return;
		let key = 0;
		const t = setInterval(() => {
			const tall = cells.filter((c) => c.kind === 'tall');
			const seen = tall.filter((c) => c.x * TILE < w * 0.17 || c.x * TILE > w * 0.82 || c.y * TILE > h * 0.84);
			const pool = seen.length ? seen : tall;
			if (!pool.length) return;
			const c = pool[Math.floor(Math.random() * pool.length)];
			encounter = { x: c.x, y: c.y, id: 1 + Math.floor(Math.random() * DEX), key: ++key };
		}, 4200);
		return () => clearInterval(t);
	});

	// The trainer and party cross the screen at walking pace (about 3 tiles a second), then the path stays empty a bit.
	// Each pass comes in from a random side: along the crossroads' row from the left or right, or down/up its column.
	type Dir = 'right' | 'left' | 'down' | 'up';
	const DIRS: Dir[] = ['right', 'left', 'down', 'up'];
	const pick = (): Dir => (still ? 'right' : DIRS[Math.floor(Math.random() * DIRS.length)]);
	let dir = $state<Dir>(pick());
	let pass = $state(0);
	const vertical = $derived(dir === 'down' || dir === 'up');
	const lineLength = $derived((party.length + 1) * TILE);
	const crossSeconds = $derived(((vertical ? h : w) + lineLength + 2 * TILE) / (TILE * 3));
	// Where the line starts and ends (translate), and where each follower sits relative to the one ahead.
	const route = $derived(
		{
			right: { from: '-48px 0', to: `calc(100vw + ${lineLength}px) 0`, ox: '-52px', oy: '0px' },
			left: { from: '100vw 0', to: `${-lineLength - TILE}px 0`, ox: '52px', oy: '0px' },
			down: { from: '0 -48px', to: `0 calc(100vh + ${lineLength}px)`, ox: '0px', oy: '-52px' },
			up: { from: '0 100vh', to: `0 ${-lineLength - TILE}px`, ox: '0px', oy: '52px' }
		}[dir]
	);
</script>

<svelte:window bind:innerWidth={w} bind:innerHeight={h} />

<div class="backdrop {time}" aria-hidden="true">
	<!-- Probe: the player's own sprite sheet from the ROM; the stand-in trainer walks until it loads. -->
	<img class="probe" src={PLAYER} alt="" onload={() => (playerOk = true)} />
	<svg
		width={cols * TILE}
		height={rows * TILE}
		viewBox="0 0 {cols * 16} {rows * 16}"
		shape-rendering="crispEdges"
	>
		<defs>
			<symbol id="t-grass" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--g)" />
				<path d="M3 4h1v1H3zM5 4h1v1H5zM4 5h1v1H4zM11 10h1v1h-1zM13 10h1v1h-1zM12 11h1v1h-1z" fill="var(--m)" />
			</symbol>
			<symbol id="t-tall" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--m)" />
				<path d="M0 8 2 2 4 8 6 2 8 8 10 2 12 8 14 2 16 8ZM0 16 2 10 4 16 6 10 8 16 10 10 12 16 14 10 16 16Z" fill="var(--d)" />
				<path d="M2 2h1v2H2zM6 2h1v2H6zM10 2h1v2h-1zM14 2h1v2h-1zM2 10h1v2H2zM6 10h1v2H6zM10 10h1v2h-1zM14 10h1v2h-1z" fill="var(--l)" />
			</symbol>
			<!-- Swaying frames: the blade tips lean right, then left. Only the tips move; the clump stays put. -->
			<symbol id="t-tall-r" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--m)" />
				<path d="M0 8 3 2 4 8 7 2 8 8 11 2 12 8 15 2 16 8ZM0 16 3 11 4 16 7 11 8 16 11 11 12 16 15 11 16 16Z" fill="var(--d)" />
				<path d="M3 2h1v2H3zM7 2h1v2H7zM11 2h1v2h-1zM15 2h1v2h-1zM3 11h1v2H3zM7 11h1v2H7zM11 11h1v2h-1zM15 11h1v2h-1z" fill="var(--l)" />
			</symbol>
			<symbol id="t-tall-l" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--m)" />
				<path d="M0 8 1 2 4 8 5 2 8 8 9 2 12 8 13 2 16 8ZM0 16 1 11 4 16 5 11 8 16 9 11 12 16 13 11 16 16Z" fill="var(--d)" />
				<path d="M1 2h1v2H1zM5 2h1v2H5zM9 2h1v2H9zM13 2h1v2h-1zM1 11h1v2H1zM5 11h1v2H5zM9 11h1v2H9zM13 11h1v2h-1z" fill="var(--l)" />
			</symbol>
			<symbol id="t-tree" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--g)" />
				<path d="M4 15h8v1H4z" fill="var(--m)" />
				<path d="M6 11h4v4H6z" fill="var(--bark)" />
				<path d="M4 1h8v1H4zM2 2h12v1H2zM1 3h14v7H1zM2 10h12v1H2zM4 11h8v1H4z" fill="var(--d)" />
				<path d="M4 3h4v2H4zM3 5h2v2H3z" fill="var(--m)" />
				<path d="M10 8h4v2h-4zM8 10h4v1H8z" fill="var(--k)" />
			</symbol>
			<symbol id="t-path" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--p)" />
				<path d="M3 4h1v1H3zM11 2h1v1h-1zM7 9h1v1H7zM13 12h1v1h-1zM2 13h1v1H2z" fill="var(--p2)" />
			</symbol>
			<symbol id="t-water" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--w)" />
			</symbol>
			<symbol id="t-sign" viewBox="0 0 16 16">
				<use href="#t-grass" width="16" height="16" />
				<path d="M7 9h2v6H7z" fill="var(--bark)" />
				<path d="M2 2h12v8H2z" fill="var(--k)" />
				<path d="M3 3h10v6H3z" fill="#c89858" />
				<path d="M4 4h6v1H4zM4 6h8v1H4z" fill="var(--bark)" />
			</symbol>
			<!-- An item ball on the grass: red top, white bottom, a band and a button. -->
			<symbol id="t-ball" viewBox="0 0 16 16">
				<use href="#t-grass" width="16" height="16" />
				<path d="M4 13h8v1H4z" fill="var(--m)" />
				<path d="M6 3h4v1H6zM4 4h8v2H4zM3 6h10v1H3z" fill="#e04848" />
				<path d="M3 8h10v1H3zM4 9h8v2H4zM6 11h4v1H6z" fill="#f8f8f8" />
				<path d="M3 7h10v1H3z" fill="#202020" />
				<path d="M7 6h2v3H7z" fill="#202020" />
				<path d="M7.5 7h1v1h-1z" fill="#f8f8f8" />
				<path d="M5 4h2v1H5z" fill="#ff9a9a" />
			</symbol>
			<!-- Two frames each, swapped by CSS. -->
			<symbol id="t-flower-1" viewBox="0 0 16 16">
				<path d="M7 5h2v2H7zM5 7h2v2H5zM9 7h2v2H9zM7 9h2v2H7z" fill="var(--petal)" />
				<path d="M7 7h2v2H7z" fill="#f0c030" />
			</symbol>
			<symbol id="t-flower-2" viewBox="0 0 16 16">
				<path d="M5 5h2v2H5zM9 5h2v2H9zM5 9h2v2H5zM9 9h2v2H9z" fill="var(--petal)" />
				<path d="M7 7h2v2H7z" fill="#f0c030" />
			</symbol>
			<symbol id="t-wave-1" viewBox="0 0 16 16">
				<path d="M2 4h4v1H2zM10 10h4v1h-4z" fill="var(--w2)" />
			</symbol>
			<symbol id="t-wave-2" viewBox="0 0 16 16">
				<path d="M4 4h4v1H4zM8 10h4v1H8z" fill="var(--w2)" />
			</symbol>
			<!-- Stand-in trainer while there's no ROM to take the real one from: two walking frames, facing right. -->
			<symbol id="t-kid-1" viewBox="0 0 16 16">
				<path d="M5 1h5v1H5zM4 2h7v2H4z" fill="#7a5cc2" />
				<path d="M9 4h4v1H9z" fill="#4a3488" />
				<path d="M4 4h3v2H4z" fill="#3a2a20" />
				<path d="M7 5h4v3H7zM5 6h2v2H5z" fill="#f8c890" />
				<path d="M9 6h1v1H9z" fill="#202020" />
				<path d="M5 8h6v4H5z" fill="#f2c14e" />
				<path d="M8 9h2v2H8z" fill="#f8c890" />
				<path d="M5 12h2v2H5zM9 12h2v2H9z" fill="#3858a8" />
				<path d="M4 14h3v1H4zM9 14h3v1H9z" fill="#202020" />
			</symbol>
			<symbol id="t-kid-2" viewBox="0 0 16 16">
				<path d="M5 1h5v1H5zM4 2h7v2H4z" fill="#7a5cc2" />
				<path d="M9 4h4v1H9z" fill="#4a3488" />
				<path d="M4 4h3v2H4z" fill="#3a2a20" />
				<path d="M7 5h4v3H7zM5 6h2v2H5z" fill="#f8c890" />
				<path d="M9 6h1v1H9z" fill="#202020" />
				<path d="M5 8h6v4H5z" fill="#f2c14e" />
				<path d="M7 9h2v2H7z" fill="#f8c890" />
				<path d="M6 12h4v2H6z" fill="#3858a8" />
				<path d="M6 14h4v1H6z" fill="#202020" />
			</symbol>
		</defs>
		{#each cells as c (c.x + ',' + c.y)}
			{@const x = c.x * 16}
			{@const y = c.y * 16}
			{#if c.kind === 'flower'}
				<use href="#t-grass" {x} {y} width="16" height="16" />
				<g class:red={c.n % 2 === 1}>
					<use class="f1" href="#t-flower-1" {x} {y} width="16" height="16" />
					<use class="f2" href="#t-flower-2" {x} {y} width="16" height="16" />
				</g>
			{:else if c.kind === 'water'}
				<use href="#t-water" {x} {y} width="16" height="16" />
				<use class="f1" href="#t-wave-1" {x} {y} width="16" height="16" />
				<use class="f2" href="#t-wave-2" {x} {y} width="16" height="16" />
			{:else if c.kind === 'tall' && c.n % 23 === 0}
				<g class="rustle" style:--delay={c.n % 7}>
					<use href="#t-tall" {x} {y} width="16" height="16" />
					<use class="sway r" href="#t-tall-r" {x} {y} width="16" height="16" />
					<use class="sway l" href="#t-tall-l" {x} {y} width="16" height="16" />
				</g>
			{:else}
				<use href="#t-{c.kind}" {x} {y} width="16" height="16" />
			{/if}
		{/each}
		<!-- Shoreline round the pond. -->
		<rect x={pond.x * 16 + 0.5} y={pond.y * 16 + 0.5} width="47" height="47" fill="none" stroke="var(--k)" />
	</svg>

	<!-- Item balls glint now and then. -->
	{#each cells.filter((c) => c.kind === 'ball') as c, i (c.x + ',' + c.y)}
		<span class="glint" style:left="{c.x * TILE + 30}px" style:top="{c.y * TILE + 10}px" style:--i={i}></span>
	{/each}

	<!-- The trainer leads, the party follows one tile behind each other. -->
	{#key pass}
	<div
		class="line {dir}"
		style:top={vertical ? '0px' : `${pathY * TILE - 6}px`}
		style:left={vertical ? `${pathX * TILE}px` : '0px'}
		style:--cross="{crossSeconds}s"
		style:--from={route.from}
		style:--to={route.to}
		style:--ox={route.ox}
		style:--oy={route.oy}
		onanimationend={(e) => {
			if (e.target !== e.currentTarget) return;
			dir = pick();
			pass++;
		}}
	>
		{#if playerOk}
			<span class="member player"></span>
		{:else}
			<svg class="member" viewBox="0 0 16 16" shape-rendering="crispEdges">
				<use class="s1" href="#t-kid-1" width="16" height="16" />
				<use class="s2" href="#t-kid-2" width="16" height="16" />
			</svg>
		{/if}
		{#each party as m, i (i)}
			<img class="member mon" src="/api/v1/game/icons/{m.icon}.png" alt="" style:--i={i + 1} />
		{/each}
	</div>
	{/key}

	{#if encounter}
		{#key encounter.key}
			<div class="encounter" style:left="{encounter.x * TILE}px" style:top="{encounter.y * TILE}px">
				<div class="pop">
					<img
						class="wild"
					src="/api/v1/game/icons/{encounter.id}.png"
					alt=""
						onerror={(e) => ((e.currentTarget as HTMLElement).style.display = 'none')}
					/>
				</div>
				<svg class="tuft" viewBox="0 0 16 16" shape-rendering="crispEdges">
					<use href="#t-tall" width="16" height="16" />
					<use class="sway r" href="#t-tall-r" width="16" height="16" />
					<use class="sway l" href="#t-tall-l" width="16" height="16" />
				</svg>
				{#each [-1, 1, -0.5, 0.6] as dx, k (k)}
					<span class="leaf" style:--dx={dx} style:--k={k}></span>
				{/each}
				<span class="bang">!</span>
			</div>
		{/key}
	{/if}

	{#if !still}
		<span class="shadow s-a"></span>
		<span class="shadow s-b"></span>
	{/if}
</div>

<style>
	/* Palette: four greens, sand, water. Night and dusk tint the whole map; the dark theme dims it. */
	.backdrop {
		--l: #d8f0a8;
		--g: #a8d878;
		--m: #70b058;
		--d: #387848;
		--k: #1c4030;
		--bark: #7a5230;
		--p: #ecdca4;
		--p2: #c8b078;
		--w: #5898d8;
		--w2: #b0d8f8;
		--petal: #fff;
		position: fixed;
		inset: 0;
		z-index: 0;
		overflow: hidden;
		pointer-events: none;
		filter: var(--scenery-filter);
	}
	.dusk {
		filter: var(--scenery-filter) sepia(0.35) hue-rotate(-20deg) saturate(1.2);
	}
	.night {
		filter: var(--scenery-filter) brightness(0.5) saturate(0.6) hue-rotate(25deg);
	}
	svg {
		display: block;
	}
	.red {
		--petal: #f06878;
	}
	.f2,
	.s2 {
		visibility: hidden;
	}
	@media (prefers-reduced-motion: no-preference) {
		.f1,
		.f2 {
			animation: frame 1.6s steps(1) infinite;
		}
		.f2 {
			animation-delay: -0.8s;
		}
		/* Walk cycle: one frame per half tile. */
		.s1,
		.s2 {
			animation: frame 0.34s steps(1) infinite;
		}
		.s2 {
			animation-delay: -0.17s;
		}
	}
	@keyframes frame {
		0% {
			visibility: visible;
		}
		50% {
			visibility: hidden;
		}
	}

	/* Something in the grass: now and then the tips sway right, left, right, left, each tuft on its own clock. */
	.sway {
		visibility: hidden;
	}
	@media (prefers-reduced-motion: no-preference) {
		.rustle .r {
			animation: sway-r 7s steps(1) calc(var(--delay) * -1s) infinite;
		}
		.rustle .l {
			animation: sway-l 7s steps(1) calc(var(--delay) * -1s) infinite;
		}
		.tuft .r {
			animation: tuft-r 0.9s steps(1);
		}
		.tuft .l {
			animation: tuft-l 0.9s steps(1);
		}
	}
	@keyframes sway-r {
		0% {
			visibility: visible;
		}
		3% {
			visibility: hidden;
		}
		6% {
			visibility: visible;
		}
		9% {
			visibility: hidden;
		}
	}
	@keyframes sway-l {
		3% {
			visibility: visible;
		}
		6% {
			visibility: hidden;
		}
		9% {
			visibility: visible;
		}
		12% {
			visibility: hidden;
		}
	}
	/* Encounter: the same sway, three times as fast. */
	@keyframes tuft-r {
		0%,
		30%,
		60% {
			visibility: visible;
		}
		15%,
		45%,
		75% {
			visibility: hidden;
		}
	}
	@keyframes tuft-l {
		15%,
		45%,
		75% {
			visibility: visible;
		}
		30%,
		60%,
		90% {
			visibility: hidden;
		}
	}
	.probe {
		display: none;
	}
	/* The player's sheet (16x96: down, up, left, then each one's walking frame). Right mirrors left. A tile is
	   stand-walk; walking up or down, every other step uses the walking frame mirrored, as the game does. */
	.player {
		--stand: -96px;
		--walk: -240px;
		--face: 1;
		--alt: 1;
		background: url('/api/v1/game/icons/player.png') 0 var(--stand) / 48px 288px;
		image-rendering: pixelated;
		transform: scaleX(var(--face));
	}
	.right .player {
		--face: -1;
	}
	.down .player {
		--stand: 0px;
		--walk: -144px;
		--alt: -1;
	}
	.up .player {
		--stand: -48px;
		--walk: -192px;
		--alt: -1;
	}
	/* The stand-in only has side frames. */
	.left svg.member {
		transform: scaleX(-1);
	}
	@media (prefers-reduced-motion: no-preference) {
		.player {
			animation: step 0.68s steps(1) infinite;
		}
	}
	@keyframes step {
		25% {
			background-position: 0 var(--walk);
		}
		50% {
			background-position: 0 var(--stand);
		}
		75% {
			background-position: 0 var(--walk);
			transform: scaleX(calc(var(--face) * var(--alt)));
		}
	}

	/* A four-point sparkle on the item ball. */
	.glint {
		position: absolute;
		width: 12px;
		height: 12px;
		background: #fff;
		clip-path: polygon(50% 0, 62% 38%, 100% 50%, 62% 62%, 50% 100%, 38% 62%, 0 50%, 38% 38%);
		scale: 0;
		animation: glint 3.2s ease-out calc(var(--i) * 1.3s) infinite;
	}
	@keyframes glint {
		0%,
		70%,
		100% {
			scale: 0;
			rotate: 0deg;
		}
		80% {
			scale: 1;
			rotate: 45deg;
		}
		90% {
			scale: 0.4;
			rotate: 90deg;
		}
	}

	/* The walking line. The whole line glides along its path, then waits off screen a moment before the next pass;
	   each member sits one tile behind the one before. */
	.line {
		position: absolute;
		width: 48px;
		height: 48px;
		translate: var(--from);
		animation: march var(--cross) linear both;
	}
	@keyframes march {
		from {
			translate: var(--from);
		}
		85%,
		to {
			translate: var(--to);
		}
	}
	.member {
		position: absolute;
		width: 48px;
		height: 48px;
		left: calc(var(--i, 0) * var(--ox));
		top: calc(var(--i, 0) * var(--oy));
		image-rendering: pixelated;
		filter: drop-shadow(0 3px 0 rgb(0 0 0 / 0.18));
	}
	/* Party sprites hop a pixel on alternate steps from the trainer. */
	.mon {
		animation: hop 0.34s steps(1) calc(var(--i) * -0.17s) infinite;
	}
	@keyframes hop {
		50% {
			translate: 0 -3px;
		}
	}

	/* Wild encounter, 2.6 s: the tuft shakes, leaves fly, "!" pops, the Pokémon hops up out of the grass and back in. */
	.encounter {
		position: absolute;
		width: 48px;
		height: 48px;
	}
	.tuft {
		position: absolute;
		inset: 0;
		width: 48px;
		height: 48px;
	}
	.leaf {
		position: absolute;
		left: 21px;
		top: 18px;
		width: 6px;
		height: 6px;
		background: var(--l);
		box-shadow: 0 0 0 1px var(--d);
		opacity: 0;
		animation: leaf 0.8s ease-out calc(var(--k) * 0.05s) both;
	}
	@keyframes leaf {
		0% {
			opacity: 1;
			translate: 0 0;
		}
		50% {
			translate: calc(var(--dx) * 22px) -26px;
		}
		100% {
			opacity: 0;
			translate: calc(var(--dx) * 34px) 4px;
		}
	}
	.bang {
		position: absolute;
		left: 10px;
		top: -46px;
		width: 28px;
		height: 34px;
		display: grid;
		place-items: center;
		box-sizing: border-box;
		background: #fff;
		border: 3px solid #202020;
		border-radius: 4px;
		font: 16px/1 var(--pixel-font);
		color: #202020;
		scale: 0;
		transform-origin: 50% 100%;
		animation: bang 1.2s cubic-bezier(0.3, 1.6, 0.5, 1) 0.45s both;
	}
	.bang::after {
		content: '';
		position: absolute;
		bottom: -8px;
		left: 6px;
		border: 4px solid transparent;
		border-top-color: #202020;
	}
	@keyframes bang {
		0% {
			scale: 0;
		}
		25%,
		85% {
			scale: 1;
		}
		100% {
			scale: 0;
		}
	}
	/* Clipped at the tile's bottom edge, so the Pokémon comes out of the grass, not from under it. */
	.pop {
		position: absolute;
		inset: 0;
		clip-path: inset(-80px -24px 0 -24px);
	}
	.wild {
		position: absolute;
		left: 0;
		top: 0;
		width: 48px;
		height: 48px;
		image-rendering: pixelated;
		opacity: 0;
		animation: wild 1.8s 0.8s both;
	}
	@keyframes wild {
		0% {
			opacity: 1;
			translate: 0 24px;
			animation-timing-function: cubic-bezier(0.2, 0.8, 0.4, 1);
		}
		22% {
			translate: 0 -30px;
			animation-timing-function: cubic-bezier(0.6, 0, 0.8, 0.4);
		}
		38% {
			translate: 0 -14px;
		}
		48% {
			translate: 0 -20px;
		}
		58%,
		80% {
			opacity: 1;
			translate: 0 -14px;
		}
		100% {
			opacity: 0;
			translate: 0 24px;
		}
	}

	/* A flying type's shadow crossing the map, wings beating. */
	.shadow {
		position: absolute;
		left: 0;
		top: 0;
		width: 56px;
		height: 24px;
		background: rgb(10 30 20 / 0.22);
		clip-path: polygon(0 20%, 35% 55%, 50% 40%, 65% 55%, 100% 20%, 70% 85%, 50% 70%, 30% 85%);
		opacity: 0;
		animation:
			fly 26s linear infinite,
			flap 0.4s steps(2) infinite;
	}
	.s-b {
		scale: 0.75;
		animation-delay: -13s, 0s;
		animation-duration: 34s, 0.35s;
	}
	@keyframes fly {
		0% {
			opacity: 1;
			translate: -80px 90vh;
		}
		40% {
			opacity: 1;
			translate: calc(100vw + 80px) 10vh;
		}
		41%,
		100% {
			opacity: 0;
			translate: calc(100vw + 80px) 10vh;
		}
	}
	@keyframes flap {
		50% {
			transform: scaleY(0.45);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.line,
		.mon,
		.glint {
			animation: none;
		}
		.line {
			translate: 40vw 0;
		}
	}
</style>
