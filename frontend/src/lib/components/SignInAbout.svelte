<!-- What PPPP is, for first-time visitors, how the shared game is going right now (public teaser), and the way in.
     Drawn as an in-game text box, with the run's progress as a trainer card. -->
<script lang="ts">
	import { counter, type Teaser } from '#lib/teaser.js';

	let { teaser }: { teaser: Teaser | null } = $props();

	const badgeArt = import.meta.glob('../assets/badges/*.png', { eager: true, import: 'default' }) as Record<
		string,
		string
	>;
	// Gym order; the teaser only gives a count, and badges are earned in this order on a normal run.
	const BADGES = ['Boulder', 'Cascade', 'Thunder', 'Rainbow', 'Soul', 'Marsh', 'Volcano', 'Earth'];

	const badges = $derived(counter(teaser, 'Badges'));
	const caught = $derived(counter(teaser, 'Pokédex'));
	const earned = $derived(badges?.value ?? 0);
</script>

<section class="about" aria-labelledby="about-title">
	<h2 id="about-title">One cartridge. <br />Too many thumbs.</h2>
	<p class="pitch">
		One Game Boy, one Pokémon Red save, and everyone holding the controls. Every button you press lands in the same game.<span
			class="more"
			aria-hidden="true">▼</span
		>
	</p>

	<div class="modes">
		<p class="mode anarchy"><b>ANARCHY</b>Every press goes straight in. Chaos.</p>
		<p class="mode democracy"><b>DEMOCRACY</b>We vote on the next move. Slower chaos.</p>
	</div>

	<!-- Placeholders until the teaser loads (or if the server is down), so the card keeps its shape. -->
	<div class="card" aria-label="Our run so far" role="group">
		<p class="card-title">TRAINER CARD</p>
		<dl>
			<dt>BADGES</dt>
			<dd class="badges">
				{#each BADGES as name, i (name)}
					<img
						src={badgeArt[`../assets/badges/${name.toLowerCase()}.png`]}
						alt=""
						class:dim={i >= earned}
						title="{name} Badge"
					/>
				{/each}
				<span class="sr-only">{earned} of 8</span>
			</dd>
			<dt>POKéDEX</dt>
			<dd>{caught?.value ?? '–'} caught</dd>
			<dt>TIME</dt>
			<dd>{teaser?.playTime ?? '–'}</dd>
			<dt>ONLINE</dt>
			<dd><span class="dot" class:live={teaser} aria-hidden="true"></span>{teaser?.online ?? '–'}</dd>
		</dl>
	</div>

	<a class="cta" href="/oauth2/authorization/google" data-sveltekit-reload>
		<!-- Google's G, in Poké Ball colours: red top, black band, white bottom, the button where the bar starts. -->
		<svg class="g" viewBox="0 0 24 24" aria-hidden="true">
			<defs>
				<linearGradient id="g-ball" x1="0" y1="0" x2="0" y2="24" gradientUnits="userSpaceOnUse">
					<stop offset="0.45" stop-color="#e04848" />
					<stop offset="0.45" stop-color="#202020" />
					<stop offset="0.55" stop-color="#202020" />
					<stop offset="0.55" stop-color="#f8f8f8" />
				</linearGradient>
			</defs>
			<path d="M18.13 6.86A8 8 0 1 0 20 12H12" fill="none" stroke="#202020" stroke-width="7" stroke-linejoin="round" />
			<path d="M18.13 6.86A8 8 0 1 0 20 12H12" fill="none" stroke="url(#g-ball)" stroke-width="4" />
			<circle cx="12" cy="12" r="2.3" fill="#f8f8f8" stroke="#202020" stroke-width="1.4" />
		</svg>
		Sign in with Google
	</a>
</section>

<style>
	/* The game's text box: a dark frame, a light gap, a thin inner line. */
	.about {
		position: relative;
		z-index: 1;
		width: 430px;
		box-sizing: border-box;
		padding: 26px 26px 22px;
		border-radius: 12px;
		background: var(--panel);
		border: 4px solid var(--text);
		box-shadow:
			inset 0 0 0 4px var(--panel),
			inset 0 0 0 6px var(--text),
			0 16px 40px rgb(0 0 0 / 0.3);
		color: var(--text);
	}
	h2 {
		margin: 0 0 14px;
		font: 18px/1.6 var(--pixel-font);
	}
	.pitch {
		margin: 0;
		font-size: 15px;
		line-height: 1.55;
		color: var(--muted);
	}
	.more {
		margin-left: 6px;
		font-size: 10px;
		color: var(--text);
	}
	@media (prefers-reduced-motion: no-preference) {
		.more {
			animation: blink 0.9s steps(1) infinite;
		}
	}
	@keyframes blink {
		50% {
			opacity: 0;
		}
	}

	.modes {
		margin-top: 16px;
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 10px;
	}
	.mode {
		margin: 0;
		padding: 10px 12px;
		border-radius: 8px;
		border: 2px solid var(--c);
		background: color-mix(in oklab, var(--c), transparent 90%);
		font-size: 13px;
		line-height: 1.4;
	}
	.mode b {
		display: block;
		margin-bottom: 6px;
		font: 9px/1 var(--pixel-font);
		color: var(--c);
	}
	.anarchy {
		--c: var(--anarchy);
	}
	.democracy {
		--c: var(--democracy);
	}

	.card {
		margin-top: 16px;
		padding: 12px 14px;
		border-radius: 8px;
		background: color-mix(in oklab, var(--text), transparent 94%);
	}
	.card-title {
		margin: 0 0 10px;
		font: 9px/1 var(--pixel-font);
		color: var(--accent-text);
	}
	dl {
		margin: 0;
		display: grid;
		grid-template-columns: auto 1fr;
		gap: 8px 14px;
		align-items: center;
	}
	dt {
		font: 8px/1 var(--pixel-font);
		color: var(--muted);
	}
	dd {
		margin: 0;
		font-size: 14px;
		font-weight: 600;
	}
	.badges {
		display: flex;
		gap: 4px;
	}
	.badges img {
		width: 22px;
		height: 22px;
		image-rendering: pixelated;
	}
	.badges img.dim {
		filter: grayscale(1);
		opacity: 0.25;
	}
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip-path: inset(50%);
	}
	.dot {
		display: inline-block;
		width: 8px;
		height: 8px;
		margin-right: 6px;
		border-radius: 50%;
		background: var(--muted);
	}
	.dot.live {
		background: #3fb950;
		box-shadow: 0 0 6px #3fb950;
	}

	.cta {
		margin-top: 18px;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 12px;
		min-height: 50px;
		border-radius: 10px;
		background: var(--accent);
		color: #1d1f1c;
		font: 11px/1 var(--pixel-font);
		text-decoration: none;
		box-shadow: 0 4px 0 color-mix(in oklab, var(--accent), black 40%);
		transition:
			transform 60ms,
			box-shadow 60ms,
			background 120ms;
	}
	.cta:hover {
		background: var(--accent-hover);
	}
	.cta:active {
		transform: translateY(3px);
		box-shadow: 0 1px 0 color-mix(in oklab, var(--accent), black 40%);
	}
	.cta:focus-visible {
		outline: 3px solid var(--text);
		outline-offset: 3px;
	}
	.g {
		width: 24px;
		height: 24px;
		flex: none;
	}
	@media (prefers-reduced-motion: no-preference) {
		.cta:hover .g {
			animation: wobble 0.5s steps(4);
		}
	}
	/* The caught-ball wobble. */
	@keyframes wobble {
		25% {
			rotate: -15deg;
		}
		75% {
			rotate: 15deg;
		}
	}

	/* Phones: a compact strip under the device: title, the card's numbers on one line, the button. */
	@media (max-width: 899px) {
		.about {
			width: min(410px, calc(100vw - 24px));
			padding: 12px 14px;
			border-width: 3px;
			box-shadow:
				inset 0 0 0 3px var(--panel),
				inset 0 0 0 5px var(--text),
				0 10px 24px rgb(0 0 0 / 0.3);
		}
		h2 {
			font-size: 9px;
			margin: 0;
			white-space: nowrap;
		}
		h2 br,
		.pitch,
		.modes,
		.card-title,
		dt:nth-of-type(1),
		.badges {
			display: none;
		}
		.card {
			margin-top: 8px;
			padding: 0;
			background: none;
		}
		dl {
			display: flex;
			gap: 6px 10px;
			font-size: 12px;
		}
		dd {
			font-size: 12px;
		}
		.cta {
			margin-top: 10px;
			min-height: 44px;
		}
	}
</style>
