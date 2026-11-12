<!-- Konami code on /play (see konami.svelte.ts): Poké Balls and PIBSIGNO.'s face rain down the page and he gets caught. The sign-in
     page's egg is the wild encounter; this is the catch. Reduced motion: the text box and jingle only. -->
<script lang="ts">
	import { konami } from '#lib/konami.svelte.ts';
	import { gotcha } from '#lib/teaser.ts';
	import face from '#lib/assets/konami-face.webp';

	const SHOW_MS = 6000;
	type Ball = { face: boolean; left: number; size: number; delay: number; fall: number; spin: number };

	let balls = $state<Ball[]>([]);
	let shown = $state(false);
	let seen = konami.at; // coming back to /play doesn't replay the last one

	$effect(() => {
		if (konami.at === seen) return;
		seen = konami.at;
		gotcha();
		shown = true;
		balls = matchMedia('(prefers-reduced-motion: reduce)').matches
			? []
			: Array.from({ length: 36 }, (_, i) => ({
					face: i % 4 === 0, // every fourth is the face, a bit bigger and wobbling instead of spinning
					left: Math.random() * 100,
					size: 22 + Math.random() * 26,
					delay: Math.random() * 2.2,
					fall: 2.4 + Math.random() * 1.8,
					spin: (Math.random() < 0.5 ? -1 : 1) * (180 + Math.random() * 540)
				}));
		const t = setTimeout(() => ((shown = false), (balls = [])), SHOW_MS);
		return () => clearTimeout(t);
	});
</script>

{#if shown}
	<div class="rain" aria-hidden="true">
		{#each balls as b, i (i)}
			{#if b.face}
				<img
					class="ball face"
					src={face}
					alt=""
					style:left="{b.left}%"
					style:--size="{b.size + 16}px"
					style:--delay="{b.delay}s"
					style:--fall="{b.fall}s"
				/>
			{:else}
				<span
					class="ball"
					style:left="{b.left}%"
					style:--size="{b.size}px"
					style:--delay="{b.delay}s"
					style:--fall="{b.fall}s"
					style:--spin="{b.spin}deg"
				></span>
			{/if}
		{/each}
	</div>
	<p class="textbox" role="status"><img src={face} alt="" />Gotcha! PIBSIGNO. was caught!</p>
{/if}

<style>
	.rain {
		position: fixed;
		inset: 0;
		z-index: 60;
		pointer-events: none;
		overflow: hidden;
	}
	/* A ball: red top, white bottom, black band, white button with a black ring. */
	.ball {
		position: absolute;
		top: calc(var(--size) * -1.5);
		width: var(--size);
		height: var(--size);
		border-radius: 50%;
		background:
			radial-gradient(circle, #fff 0 14%, #1d1f1c 15% 24%, transparent 25%),
			linear-gradient(#e3350d 0 45%, #1d1f1c 45% 55%, #f4f4f4 55%);
		box-shadow:
			inset 0 0 0 calc(var(--size) * 0.06) #1d1f1c,
			0 4px 10px rgb(0 0 0 / 0.3);
		animation: fall var(--fall) cubic-bezier(0.45, 0, 0.8, 1) var(--delay) both;
	}
	/* The face stays upright: a little wobble instead of a spin. */
	.ball.face {
		object-fit: cover;
		background: none;
		box-shadow:
			0 0 0 2px #fff,
			0 4px 12px rgb(0 0 0 / 0.35);
		animation-name: fall-face;
	}
	@keyframes fall-face {
		from {
			transform: translateY(0) rotate(-12deg);
		}
		25% {
			transform: translateY(calc((100svh + var(--size) * 3) * 0.25)) rotate(12deg);
		}
		50% {
			transform: translateY(calc((100svh + var(--size) * 3) * 0.5)) rotate(-12deg);
		}
		75% {
			transform: translateY(calc((100svh + var(--size) * 3) * 0.75)) rotate(12deg);
		}
		to {
			transform: translateY(calc(100svh + var(--size) * 3)) rotate(-12deg);
		}
	}
	@keyframes fall {
		from {
			transform: translateY(0) rotate(0);
		}
		to {
			transform: translateY(calc(100svh + var(--size) * 3)) rotate(var(--spin));
		}
	}
	/* The game's dialogue box, pinned to the top of the page so it shows on every tab. */
	.textbox {
		position: fixed;
		top: 72px;
		left: 50%;
		translate: -50% 0;
		z-index: 61;
		width: min(92vw, 420px);
		box-sizing: border-box;
		margin: 0;
		padding: 14px 16px;
		font: 12px/1.8 var(--pixel-font);
		color: var(--ink);
		background: #fff;
		border: 3px solid var(--ink);
		border-radius: 6px;
		box-shadow:
			inset 0 0 0 3px #fff,
			inset 0 0 0 5px var(--ink),
			0 8px 24px rgb(0 0 0 / 0.35);
		pointer-events: none;
		animation: pop 0.25s ease-out;
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.textbox img {
		flex: none;
		width: 44px;
		height: 44px;
		border-radius: 50%;
		object-fit: cover;
		box-shadow: 0 0 0 2px var(--ink);
	}
	@keyframes pop {
		from {
			opacity: 0;
			transform: translateY(-8px);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.textbox {
			animation: none;
		}
	}
</style>
