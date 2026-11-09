<!-- Behind the sign-in device: the game as it is right now (a public screenshot, a few seconds old, blurred), sky
     and clouds by the local hour, tall grass along the bottom, and the party's silhouettes wandering through it. -->
<script lang="ts">
	import type { Teaser } from '#lib/teaser.js';

	let { teaser }: { teaser: Teaser | null } = $props();

	// New screenshot every 5 s (the server caches it as long); hidden until one has loaded.
	let tick = $state(Date.now());
	let shotOk = $state(false);
	$effect(() => {
		const t = setInterval(() => (tick = Date.now()), 5000);
		return () => clearInterval(t);
	});

	const hour = new Date().getHours();
	const time = hour < 6 || hour >= 21 ? 'night' : hour < 9 || hour >= 18 ? 'dusk' : 'day';

	const walkers = $derived((teaser?.party ?? []).filter((m) => m.icon > 0).slice(0, 6));
</script>

<div class="backdrop {time}" aria-hidden="true">
	<img class="shot" class:ok={shotOk} src="/api/v1/public/screen.png?t={tick}" alt="" onload={() => (shotOk = true)} onerror={() => (shotOk = false)} />
	<div class="sky"></div>
	<span class="cloud c1"></span>
	<span class="cloud c2"></span>
	<span class="cloud c3"></span>
	<div class="grass">
		{#each walkers as m, i (i)}
			<span class="walker" style:--src="url(/api/v1/game/icons/{m.icon}.png)" style:--i={i}></span>
		{/each}
	</div>
</div>

<style>
	.backdrop {
		position: fixed;
		inset: 0;
		z-index: 0;
		overflow: hidden;
		pointer-events: none;
		--sky-top: #1d2a52;
		--sky-low: #3d5a8a;
		--grass: #2f7d3a;
		--grass-dark: #1f5a28;
		--cloud: rgb(255 255 255 / 0.18);
	}
	.backdrop.dusk {
		--sky-top: #2b2350;
		--sky-low: #b0607a;
		--grass: #2a6634;
		--grass-dark: #1a4722;
	}
	.backdrop.night {
		--sky-top: #070a18;
		--sky-low: #16203f;
		--grass: #173d20;
		--grass-dark: #0e2914;
		--cloud: rgb(255 255 255 / 0.07);
	}
	/* Night: no clouds, a field of pixel stars that twinkle. */
	.night .cloud {
		display: none;
	}
	.night .sky::after {
		content: '';
		position: absolute;
		inset: 0 0 30% 0;
		background:
			/* Lattices with coprime periods and offsets, so together they read as scattered. */
			radial-gradient(#fff 1px, transparent 1.5px) 13px 7px / 263px 191px,
			radial-gradient(#fff 1px, transparent 1.5px) 140px 95px / 317px 229px,
			radial-gradient(#fff 0.8px, transparent 1.2px) 71px 160px / 179px 251px,
			radial-gradient(#cfe0ff 1.5px, transparent 2px) 200px 40px / 389px 283px,
			radial-gradient(#ffe9b0 1.2px, transparent 1.7px) 33px 210px / 433px 307px;
		animation: twinkle 4s steps(2) infinite alternate;
	}
	@keyframes twinkle {
		to {
			opacity: 0.55;
		}
	}
	.backdrop.day {
		--sky-top: #3f7fc4;
		--sky-low: #9cc8ea;
		--cloud: rgb(255 255 255 / 0.55);
	}
	.sky {
		position: absolute;
		inset: 0;
		background: linear-gradient(var(--sky-top), var(--sky-low));
		opacity: 0.55;
	}
	.night .sky {
		opacity: 0.9;
	}
	/* The game, huge and soft: colour and motion, nothing readable. */
	.shot {
		position: absolute;
		inset: -40px;
		width: calc(100% + 80px);
		height: calc(100% + 80px);
		object-fit: cover;
		image-rendering: pixelated;
		filter: blur(22px) saturate(1.3);
		opacity: 0;
		transition: opacity 1s;
	}
	.shot.ok {
		opacity: 0.45;
	}
	/* Pixel clouds: stacked blocky ellipses, drifting left to right. */
	.cloud {
		position: absolute;
		left: -20vw;
		width: 140px;
		height: 36px;
		background:
			radial-gradient(circle at 30% 70%, var(--cloud) 0 18px, transparent 19px),
			radial-gradient(circle at 55% 45%, var(--cloud) 0 22px, transparent 23px),
			radial-gradient(circle at 75% 70%, var(--cloud) 0 16px, transparent 17px);
		animation: drift linear infinite;
	}
	.c1 {
		top: 12%;
		animation-duration: 90s;
	}
	.c2 {
		top: 24%;
		scale: 0.7;
		animation-duration: 130s;
		animation-delay: -60s;
	}
	.c3 {
		top: 6%;
		scale: 1.3;
		animation-duration: 110s;
		animation-delay: -30s;
	}
	@keyframes drift {
		to {
			translate: 140vw 0;
		}
	}
	/* Tall grass: a row of pointed blades along the bottom. */
	.grass {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 0;
		height: 16vh;
		min-height: 70px;
		background:
			linear-gradient(135deg, var(--grass) 25%, transparent 25%) 0 0 / 24px 24px repeat-x,
			linear-gradient(225deg, var(--grass) 25%, transparent 25%) 0 0 / 24px 24px repeat-x,
			linear-gradient(transparent 12px, var(--grass) 12px, var(--grass-dark));
		opacity: 0.8;
	}
	/* The party, as dark silhouettes walking across in the grass at their own pace. */
	.walker {
		position: absolute;
		bottom: 20%;
		left: -80px;
		width: 56px;
		height: 56px;
		background: var(--grass-dark);
		-webkit-mask: var(--src) center / contain no-repeat;
		mask: var(--src) center / contain no-repeat;
		animation: walk calc(40s + var(--i) * 9s) linear calc(var(--i) * -13s) infinite;
	}
	@keyframes walk {
		0%,
		100% {
			translate: 0 0;
		}
		25% {
			translate: 28vw -4px;
		}
		50% {
			translate: 56vw 0;
		}
		75% {
			translate: 84vw -4px;
		}
		99.9% {
			translate: calc(100vw + 160px) 0;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.cloud,
		.walker,
		.night .sky::after {
			animation: none;
		}
		.walker {
			left: calc(8% + var(--i) * 15%);
		}
		.cloud {
			left: calc(10% + var(--i, 0) * 30%);
		}
		.c2 {
			left: 55%;
		}
		.c3 {
			left: 75%;
		}
	}
</style>
