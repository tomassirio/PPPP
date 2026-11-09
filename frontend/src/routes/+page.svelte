<!-- Sign-in page: an original colour handheld that boots into the banner. START (or A, Enter, Space) signs in. -->
<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { loadMe } from '#lib/session.svelte.js';
	import pibs from '#lib/assets/banner-pibs.webp';
	import play from '#lib/assets/banner-play.webp';
	import face from '#lib/assets/konami-face.webp';
	import SignInAbout from '#lib/components/SignInAbout.svelte';
	import SignInBackdrop from '#lib/components/SignInBackdrop.svelte';
	import { bootChime, counter, fanfare, loadTeaser, type Teaser } from '#lib/teaser.js';


	const BOOT_MS = 2900;

	const denied = $derived(page.url.searchParams.get('error') === 'denied');

	let booting = $state(true);
	let held = $state<string | null>(null);
	let dir = $state<string | null>(null);
	let start: HTMLAnchorElement;

	// Konami code (↑↑↓↓←→←→ B A; keys: arrows, X, Z; or taps): a wild MISSINGNO. appears and the shell goes rainbow.
	const KONAMI = ['up', 'up', 'down', 'down', 'left', 'right', 'left', 'right', 'b', 'a'];
	let typed: string[] = [];
	let glitch = $state(false);

	// Fireworks: bursts at random spots, each a ring of stars and little faces flying out.
	type Spark = { face: boolean; dx: number; dy: number; hue: number };
	type Burst = { id: number; x: number; y: number; delay: number; sparks: Spark[] };
	let bursts = $state<Burst[]>([]);
	let burstId = 0;

	function fireworks() {
		bursts = Array.from({ length: 7 }, (_, i) => {
			const n = 12;
			return {
				id: burstId++,
				x: 10 + Math.random() * 80,
				y: 10 + Math.random() * 60,
				delay: i * 0.45,
				sparks: Array.from({ length: n }, (_, k) => {
					const angle = (k / n) * 2 * Math.PI + Math.random() * 0.3;
					const dist = 90 + Math.random() * 70;
					return { face: k % 3 === 0, dx: Math.cos(angle) * dist, dy: Math.sin(angle) * dist, hue: Math.random() * 360 };
				})
			};
		});
	}
	let eatClick = false;

	function input(key: string) {
		typed = [...typed, key].slice(-KONAMI.length);
		if (typed.join() !== KONAMI.join()) return false;
		typed = [];
		glitch = true;
		fanfare();
		fireworks();
		setTimeout(() => ((glitch = false), (bursts = [])), 6000);
		return true;
	}

	let teaser = $state<Teaser | null>(null);

	// Attract mode: after a while with no input the screen cycles demo slides (party, progress) like an arcade
	// cabinet; any key or tap brings the title back.
	const IDLE_MS = 20000;
	const SLIDE_MS = 4500;
	let idle = $state(false);
	let slide = $state(0);
	let idleTimer: ReturnType<typeof setTimeout>;
	const slides = $derived(
		teaser ? (['party', 'progress', 'title'] as const).filter((k) => k !== 'party' || teaser!.party.length) : []
	);
	const attract = $derived(idle && !glitch && !denied && slides.length > 0 ? slides[slide % slides.length] : 'title');
	const badges = $derived(counter(teaser, 'Badges'));
	const caught = $derived(counter(teaser, 'Pokédex'));

	// Sound is only allowed after a gesture: the first key or tap plays the power-on chime.
	let chimed = false;
	function activity() {
		if (!chimed) {
			chimed = true;
			bootChime();
		}
		idle = false;
		clearTimeout(idleTimer);
		idleTimer = setTimeout(() => ((idle = true), (slide = 0)), IDLE_MS);
	}

	function skip() {
		if (!booting) return false;
		booting = false;
		return true;
	}

	function signIn() {
		held = 'start';
		setTimeout(() => start.click(), 120);
	}

	const ARROWS: Record<string, string> = {
		ArrowUp: 'up',
		ArrowDown: 'down',
		ArrowLeft: 'left',
		ArrowRight: 'right'
	};

	const FACE_KEYS: Record<string, string> = { KeyZ: 'a', KeyX: 'b' };

	function onkeydown(e: KeyboardEvent) {
		const t = e.target as HTMLElement;
		if (e.metaKey || e.ctrlKey || e.altKey) return;
		activity();
		if (t.closest('input, textarea, select, [contenteditable]')) return;
		// Any key skips the boot; only Enter/Space stop there (no sign-in mid-boot). Arrows, Z and X still count for the code.
		if (skip() && !ARROWS[e.key] && !FACE_KEYS[e.code]) return e.preventDefault();
		if (e.key === 'Enter' || e.key === ' ') {
			e.preventDefault();
			if (!e.repeat) signIn();
		} else if (ARROWS[e.key]) {
			e.preventDefault();
			dir = ARROWS[e.key];
			if (!e.repeat) input(dir);
		} else if (FACE_KEYS[e.code]) {
			// Z is A, X is B, as on /play. By physical key, so Caps Lock and other layouts work; the button presses in.
			e.preventDefault();
			held = FACE_KEYS[e.code];
			if (!e.repeat) input(held);
		}
	}

	function onkeyup(e: KeyboardEvent) {
		if (ARROWS[e.key] === dir) dir = null;
		if (FACE_KEYS[e.code] === held) held = null;
	}

	// Which D-pad arm the pointer landed on, from its offset to the pad's centre.
	function aim(e: PointerEvent) {
		const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
		const x = e.clientX - r.left - r.width / 2;
		const y = e.clientY - r.top - r.height / 2;
		dir = Math.abs(x) > Math.abs(y) ? (x < 0 ? 'left' : 'right') : y < 0 ? 'up' : 'down';
		input(dir);
	}

	function release() {
		if (held !== 'start') held = null;
		dir = null;
	}

	onMount(() => {
		// Backend down or other error: stay on the sign-in page, START still works.
		loadMe()
			.then((me) => me && goto('/play'))
			.catch(() => {});
		if (matchMedia('(prefers-reduced-motion: reduce)').matches) booting = false;
		const t = setTimeout(() => (booting = false), BOOT_MS);
		// The teaser is public; refreshed while the page is open. Fails quietly (the page works without it).
		const refresh = () => loadTeaser().then((v) => (teaser = v), () => {});
		refresh();
		const poll = setInterval(refresh, 30000);
		const slides = setInterval(() => idle && slide++, SLIDE_MS);
		idleTimer = setTimeout(() => (idle = true), IDLE_MS);
		return () => {
			clearTimeout(t);
			clearTimeout(idleTimer);
			clearInterval(poll);
			clearInterval(slides);
		};
	});
</script>

<svelte:window {onkeydown} {onkeyup} onpointerdown={() => (skip(), activity())} onpointerup={release} onblur={release} />

<SignInBackdrop {teaser} />

<main>
	<div class="device" class:booting class:glitch>
		<div class="shell" aria-hidden="true"></div>

		<div class="bezel">
			<span class="led" aria-hidden="true"></span>
			<div class="lcd" class:denied>
				<h1>
					<img src={pibs} alt="Pibs&amp;Pibettes" width="720" height="131" /><span class="row"
						><img src={play} alt="PlayPokemon" width="720" height="171" /></span
					>
				</h1>
				{#if attract === 'party' && teaser}
					<div class="attract" aria-hidden="true">
						<p class="slide-title">OUR TEAM</p>
						<ul class="team">
							{#each teaser.party.slice(0, 6) as m, i (i)}
								<li>
									{#if m.icon}<img src="/api/v1/game/icons/{m.icon}.png" alt="" />{/if}
									<span>L{m.level}</span>
								</li>
							{/each}
						</ul>
					</div>
				{:else if attract === 'progress' && teaser}
					<div class="attract" aria-hidden="true">
						<p class="slide-title">OUR JOURNEY</p>
						<dl class="journey">
							{#if badges}<dt>BADGES</dt><dd>{badges.value}/{badges.max}</dd>{/if}
							{#if caught}<dt>CAUGHT</dt><dd>{caught.value}/{caught.max}</dd>{/if}
							{#if teaser.playTime}<dt>TIME</dt><dd>{teaser.playTime}</dd>{/if}
							<dt>ONLINE</dt><dd>{teaser.online}</dd>
						</dl>
					</div>
				{/if}
				<p class="press" aria-hidden="true">PRESS START TO SIGN IN</p>
				{#if glitch}
					<p class="textbox" role="status">A wild MISSINGNO. appeared!</p>
				{:else if denied}
					<p class="textbox" role="alert">You're not on the trainer list. Ask the host to add you.</p>
				{/if}
				<span class="flash" aria-hidden="true"></span>
			</div>
			<p class="label" aria-hidden="true">PIBS <b>&amp;</b> PIBETTES</p>
		</div>

		<p class="logo" aria-hidden="true">Pibtendo</p>

		<div class="dpad" data-dir={dir} aria-hidden="true" onpointerdown={aim}>
			<span></span>
		</div>

		<div class="face" aria-hidden="true">
			<!-- svelte-ignore a11y_no_static_element_interactions (decorative, hidden from AT) -->
			<span class="round b" class:down={held === 'b'} onpointerdown={() => {
					held = 'b';
					input('b');
				}}>B</span>
			<a
				class="round a"
				class:down={held === 'a'}
				href="/oauth2/authorization/google"
				data-sveltekit-reload
				tabindex="-1"
				onpointerdown={() => {
					held = 'a';
					eatClick = input('a');
				}}
				onclick={(e) => {
					// The A that finishes the code shows the egg instead of signing in.
					if (eatClick) e.preventDefault();
					eatClick = false;
				}}>A</a
			>
		</div>

		<div class="pills">
			<!-- svelte-ignore a11y_no_static_element_interactions (decorative, hidden from AT) -->
			<span class="pill" class:down={held === 'select'} aria-hidden="true" onpointerdown={() => (held = 'select')}
				>SELECT</span
			>
			<a
				bind:this={start}
				class="pill"
				class:down={held === 'start'}
				href="/oauth2/authorization/google"
				data-sveltekit-reload
				aria-label="Sign in with Google"
				onpointerdown={() => (held = 'start')}>START</a
			>
		</div>

		<div class="speaker" aria-hidden="true"></div>
	</div>

	<SignInAbout {teaser} />

	{#if bursts.length}
		<div class="fireworks" aria-hidden="true">
			{#each bursts as b (b.id)}
				<div class="burst" style:left="{b.x}%" style:top="{b.y}%" style:--delay="{b.delay}s">
					{#each b.sparks as sp, k (k)}
						{#if sp.face}
							<img class="spark me" src={face} alt="" style:--dx="{sp.dx}px" style:--dy="{sp.dy}px" />
						{:else}
							<span class="spark" style:--dx="{sp.dx}px" style:--dy="{sp.dy}px" style:color="hsl({sp.hue} 95% 60%)">★</span>
						{/if}
					{/each}
				</div>
			{/each}
		</div>
	{/if}
</main>

<style>
	main {
		min-height: 100vh;
		min-height: 100dvh;
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 24px;
		padding: 20px 12px;
		overflow: hidden;
		position: relative;
		z-index: 1; /* above the backdrop */
	}
	/* The backdrop is see-through; the page colour sits under it. */
	:global(body:has(main > .device)) {
		background: var(--bg);
	}
	/* Wide: the about card beside the device. */
	@media (min-width: 900px) {
		main {
			flex-direction: row;
			gap: 56px;
		}
	}
	p {
		margin: 0;
	}

	/* Everything on the device is sized in cqw: 1cqw is 1% of the device width. */
	.device {
		/* Shell plastic: change this one line. Also nice: teal #2fa39b, berry #c23a6e,
		   dandelion #f0c030, kiwi #8fc23a. */
		--shell: #7a5cc2;
		--shell-hi: color-mix(in oklab, var(--shell), white 30%);
		--shell-lo: color-mix(in oklab, var(--shell), black 38%);
		--guts: color-mix(in oklab, var(--shell), #0a0612 78%);
		/* Moulded marks: a shade darker than the plastic, with a lit lower lip. */
		--emboss: color-mix(in oklab, var(--shell), black 22%);
		--emboss-hi: color-mix(in oklab, var(--shell-hi), transparent 35%);
		--rubber: #2e2935;
		--pixel: #1f1a33;
		position: relative;
		/* 1.67 is the device's height/width; 40px is the page padding, plus the about strip under it on phones. */
		width: min(410px, calc(100vw - 24px), calc((100dvh - 40px - var(--below, 0px)) / 1.67));
		min-width: 280px;
		aspect-ratio: 100 / 167;
		container-type: inline-size;
		user-select: none;
		-webkit-user-select: none;
		-webkit-tap-highlight-color: transparent;
		touch-action: manipulation;
	}
	.device > * {
		position: absolute;
	}

	/* Translucent plastic: an opaque tint, the board and screws seen dimly through it, then gloss on top. */
	.shell {
		inset: 0;
		border-radius: 6cqw 6cqw 13cqw 13cqw / 6cqw 6cqw 11cqw 11cqw;
		background:
			linear-gradient(100deg, rgba(255, 255, 255, 0.16) 0 18%, transparent 34% 76%, rgba(255, 255, 255, 0.07) 90%),
			radial-gradient(ellipse 70% 40% at 50% 72%, rgba(20, 0, 40, 0.14), transparent),
			linear-gradient(175deg, var(--shell-hi), var(--shell) 22%, var(--shell) 70%, color-mix(in oklab, var(--shell), black 14%));
		box-shadow:
			inset 0 0 0 0.45cqw color-mix(in oklab, var(--shell-hi), transparent 30%),
			inset 0.8cqw 1cqw 1.6cqw rgba(255, 255, 255, 0.22),
			inset -0.8cqw -1.4cqw 2.4cqw rgba(20, 0, 40, 0.3),
			0 1.3cqw 0 var(--shell-lo),
			0 1.5cqw 0 color-mix(in oklab, var(--shell-lo), black 30%),
			0 4cqw 6cqw rgba(0, 0, 0, 0.55),
			0 10cqw 14cqw rgba(0, 0, 0, 0.35);
		overflow: hidden;
	}
	/* Parts seen through the plastic: the D-pad and button membrane rings, the speaker cone. */
	.shell::before {
		content: '';
		position: absolute;
		inset: 0;
		background:
			radial-gradient(circle at 20.7cqw 110.6cqw, transparent 14.4cqw, var(--guts) 14.8cqw 15.6cqw, transparent 16cqw),
			radial-gradient(circle at 67cqw 113cqw, transparent 7.6cqw, var(--guts) 8cqw 8.6cqw, transparent 9cqw),
			radial-gradient(circle at 85cqw 107cqw, transparent 7.6cqw, var(--guts) 8cqw 8.6cqw, transparent 9cqw),
			radial-gradient(circle at 83cqw 147.5cqw, var(--guts) 0 2.4cqw, transparent 2.8cqw 14.6cqw, var(--guts) 15cqw 15.8cqw, transparent 16.2cqw);
		opacity: 0.26;
		filter: blur(0.35cqw);
	}
	/* Screws and the screen's rear frame, top half. */
	.shell::after {
		content: '';
		position: absolute;
		inset: 0;
		background:
			radial-gradient(circle at 9% 4.5%, rgba(255, 255, 255, 0.5) 0 0.5cqw, var(--guts) 0.7cqw 1.5cqw, transparent 1.7cqw),
			radial-gradient(circle at 91% 4.5%, rgba(255, 255, 255, 0.5) 0 0.5cqw, var(--guts) 0.7cqw 1.5cqw, transparent 1.7cqw),
			radial-gradient(circle at 9% 54%, rgba(255, 255, 255, 0.5) 0 0.5cqw, var(--guts) 0.7cqw 1.5cqw, transparent 1.7cqw),
			radial-gradient(circle at 91% 54%, rgba(255, 255, 255, 0.5) 0 0.5cqw, var(--guts) 0.7cqw 1.5cqw, transparent 1.7cqw);
		opacity: 0.28;
		filter: blur(0.35cqw);
	}

	.bezel {
		top: 6.8cqw;
		left: 5.8cqw;
		width: 88.4cqw;
		height: 76cqw;
		/* The bottom-right corner sweeps round more than the others. */
		border-radius: 2.6cqw 2.6cqw 9cqw 3.4cqw / 2.6cqw 2.6cqw 6cqw 3.4cqw;
		background: linear-gradient(172deg, #3b3744, #2a2731 55%, #24212a);
		box-shadow:
			inset 0 0.3cqw 0.3cqw rgba(255, 255, 255, 0.12),
			inset 0 -0.4cqw 0.6cqw rgba(0, 0, 0, 0.4),
			0 0 0 0.35cqw rgba(20, 0, 40, 0.25),
			0 0 2.5cqw 1.6cqw color-mix(in oklab, var(--guts), transparent 78%);
	}
	.label {
		position: absolute;
		left: 0;
		right: 0;
		top: 65.6cqw;
		text-align: center;
		font: italic 800 4.6cqw/1 var(--body-font);
		letter-spacing: 0.02em;
		color: #d8d3e6;
		white-space: nowrap;
	}
	.label b {
		color: #ffcc1a;
	}
	.led {
		position: absolute;
		top: 22.2cqw;
		left: 2.4cqw;
		width: 2cqw;
		height: 2cqw;
		border-radius: 50%;
		background: radial-gradient(circle at 35% 35%, #ffb3a0, #ff3a24 45%, #b3150c);
		box-shadow:
			0 0 0 0.35cqw #1a181f,
			0 0 1.2cqw 0.5cqw rgba(255, 70, 40, 0.55),
			0 0 4cqw 1cqw rgba(255, 70, 40, 0.16);
	}
	/* Three arcs after the LED, POWER centred under the pair. */
	.led::before,
	.led::after {
		position: absolute;
		color: #8f8a9c;
		white-space: nowrap;
	}
	.led::before {
		content: ')))';
		top: 50%;
		left: 3cqw;
		translate: 0 -50%;
		scale: 1 1.25;
		font: 700 2.8cqw/1 var(--body-font);
		letter-spacing: 0.25cqw;
	}
	.led::after {
		content: 'POWER';
		top: 3.6cqw;
		left: 5cqw;
		translate: -50% 0;
		font: 500 2.1cqw/1 var(--body-font);
		letter-spacing: 0.04em;
	}

	.lcd {
		position: absolute;
		top: 9.4cqw;
		left: 15cqw;
		width: 57cqw;
		height: 50cqw;
		overflow: hidden;
		border-radius: 0.6cqw;
		background: radial-gradient(ellipse at 45% 40%, #fffdf6, #f6f1e4 70%, #ece6d6);
		box-shadow:
			inset 0.6cqw 0.6cqw 1cqw rgba(30, 20, 40, 0.25),
			0 0 0 0.4cqw #17151c;
	}
	/* The unlit panel; fades out as the screen powers on. */
	.lcd::before {
		content: '';
		position: absolute;
		inset: 0;
		z-index: 2;
		background: #121116;
		opacity: 0;
		pointer-events: none;
	}
	/* Pixel grid over everything on the glass. */
	.lcd::after {
		content: '';
		position: absolute;
		inset: 0;
		z-index: 3;
		pointer-events: none;
		background:
			linear-gradient(150deg, rgba(255, 255, 255, 0.14), transparent 40%),
			repeating-linear-gradient(to right, rgba(40, 30, 60, 0.06) 0 1px, transparent 1px 3px),
			repeating-linear-gradient(to bottom, rgba(40, 30, 60, 0.06) 0 1px, transparent 1px 3px);
	}
	h1 {
		position: absolute;
		top: 40%;
		left: 0;
		right: 0;
		margin: 0;
		translate: 0 -50%;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.6cqw;
	}
	/* Clipped at its top edge only, so PlayPokemon drops in from under Pibs&Pibettes, not through it. */
	.row {
		clip-path: inset(0 -10cqw -10cqw);
	}
	h1 img {
		display: block;
		width: 50cqw;
		height: auto;
	}
	.press {
		position: absolute;
		top: 79%;
		width: 100%;
		text-align: center;
		font: 2.3cqw/1 var(--pixel-font); /* 22 square glyphs fit the 57cqw glass */
		color: var(--pixel);
		/* One running blink; dropping the delay from two whole cycles to 0 when boot ends keeps its phase. */
		animation:
			appear 0s var(--on, 0s) both,
			blink 1.1s steps(1) var(--on, 0s) infinite;
	}
	.denied h1 {
		top: 23%;
	}
	.denied h1 img {
		width: 36cqw;
	}
	.denied .press {
		top: 45%;
	}
	.textbox {
		position: absolute;
		left: 2cqw;
		right: 2cqw;
		bottom: 2cqw;
		padding: 2.6cqw 3cqw 3cqw;
		font: 2.15cqw/1.75 var(--pixel-font);
		color: var(--pixel);
		background: #fff;
		border: 0.6cqw solid var(--pixel);
		border-radius: 0.8cqw;
		box-shadow:
			inset 0 0 0 0.5cqw #fff,
			inset 0 0 0 0.8cqw var(--pixel);
	}
	.textbox::after {
		content: '';
		position: absolute;
		right: 2.6cqw;
		bottom: 1.6cqw;
		border: 0.9cqw solid transparent;
		border-top-color: var(--pixel);
		border-bottom-width: 0;
		animation: blink 0.8s steps(1) infinite;
	}
	.flash {
		position: absolute;
		inset: 0;
		z-index: 1;
		background: #fff;
		opacity: 0;
		pointer-events: none;
	}

	/* Embossed oval logo: the shell's own colour, raised edge lit from above. */
	.logo {
		top: 89cqw;
		left: 35.6cqw;
		width: 28.8cqw;
		height: 6.4cqw;
		box-sizing: border-box;
		display: grid;
		place-items: center;
		border-radius: 3.2cqw;
		border: 0.45cqw solid var(--emboss);
		box-shadow:
			0 0.25cqw 0 -0.05cqw var(--emboss-hi),
			inset 0 0.25cqw 0 -0.05cqw var(--emboss-hi);
		font: italic 800 4cqw/1 var(--body-font);
		letter-spacing: 0.01em;
		color: var(--emboss);
		text-shadow: 0 0.2cqw 0 var(--emboss-hi);
	}

	/* Controls */
	.dpad {
		top: 98.4cqw;
		left: 8.5cqw;
		width: 24.4cqw;
		height: 24.4cqw;
		transition: transform 60ms;
		cursor: pointer;
	}
	.dpad span,
	.dpad::before,
	.dpad::after {
		position: absolute;
		left: 50%;
		top: 50%;
		translate: -50% -50%;
	}
	/* Each arm carries its two little embossed arrows. */
	.dpad::before,
	.dpad::after {
		content: '';
		width: 8.4cqw;
		height: 24.4cqw;
		border-radius: 1cqw;
		background:
			url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 84 244'%3E%3Cg fill='%23000' fill-opacity='.32' stroke='%23fff' stroke-opacity='.1' stroke-width='2'%3E%3Cpath d='M42 18 53 34H31z'/%3E%3Cpath d='M42 226 53 210H31z'/%3E%3C/g%3E%3C/svg%3E")
				center / 100% 100%,
			linear-gradient(135deg, #4a4550, var(--rubber) 50%, #26232b);
		box-shadow:
			0 0.8cqw 0 #141118,
			0 1.3cqw 1.6cqw rgba(0, 0, 0, 0.35);
	}
	.dpad::after {
		width: 24.4cqw;
		height: 8.4cqw;
		background:
			url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 244 84'%3E%3Cg fill='%23000' fill-opacity='.32' stroke='%23fff' stroke-opacity='.1' stroke-width='2'%3E%3Cpath d='M18 42 34 31V53z'/%3E%3Cpath d='M226 42 210 31V53z'/%3E%3C/g%3E%3C/svg%3E")
				center / 100% 100%,
			linear-gradient(135deg, #4a4550, var(--rubber) 50%, #26232b);
		box-shadow: inset 0 0.25cqw 0 rgba(255, 255, 255, 0.08);
	}
	/* Concave centre: shadow falls on the upper-left of the dip. */
	.dpad span {
		z-index: 1;
		width: 4.2cqw;
		height: 4.2cqw;
		border-radius: 50%;
		background: radial-gradient(circle at 60% 65%, #46414d, #2c2831 70%);
		box-shadow:
			inset 0.3cqw 0.4cqw 0.5cqw rgba(0, 0, 0, 0.45),
			0 0.1cqw 0 rgba(255, 255, 255, 0.06);
	}
	.dpad[data-dir='up'] {
		transform: perspective(60cqw) rotateX(14deg);
	}
	.dpad[data-dir='down'] {
		transform: perspective(60cqw) rotateX(-14deg);
	}
	.dpad[data-dir='left'] {
		transform: perspective(60cqw) rotateY(-14deg);
	}
	.dpad[data-dir='right'] {
		transform: perspective(60cqw) rotateY(14deg);
	}

	/* A and B sit straight on the shell, B low-left and A high-right, letters moulded into the caps. */
	.face {
		top: 100cqw;
		left: 60cqw;
		width: 32cqw;
		height: 20cqw;
	}
	.round {
		position: absolute;
		width: 12cqw;
		height: 12cqw;
		display: grid;
		place-items: center;
		border-radius: 50%;
		background: radial-gradient(circle at 38% 32%, #56505e, #3a3540 45%, #28242d);
		box-shadow:
			inset 0 0.4cqw 0.5cqw rgba(255, 255, 255, 0.12),
			inset 0 -0.3cqw 0.5cqw rgba(0, 0, 0, 0.3),
			0 0 0 0.5cqw rgba(20, 0, 40, 0.14),
			0 0.8cqw 0 #16121a,
			0 1.3cqw 1.6cqw rgba(0, 0, 0, 0.35);
		font: italic 600 5cqw/1 Georgia, 'Times New Roman', serif;
		color: #2b2730;
		text-shadow:
			0 0.15cqw 0 rgba(255, 255, 255, 0.14),
			0 -0.1cqw 0 rgba(0, 0, 0, 0.5);
		text-decoration: none;
		cursor: pointer;
		transition:
			transform 50ms,
			box-shadow 50ms;
	}
	.b {
		left: 1cqw;
		top: 7cqw;
	}
	.a {
		left: 19cqw;
		top: 1cqw;
	}

	.pills {
		top: 137.4cqw;
		left: 37.9cqw;
		width: 24.2cqw;
		height: 10cqw;
		display: flex;
		justify-content: space-between;
	}
	.pill {
		position: relative;
		width: 9.6cqw;
		height: 3.2cqw;
		border-radius: 1.6cqw;
		background: linear-gradient(#4f4a56, #35313b);
		box-shadow:
			0 0.5cqw 0 #1f1b24,
			0 0.8cqw 1cqw rgba(0, 0, 0, 0.3),
			0 0 0 0.45cqw rgba(20, 0, 40, 0.14);
		color: transparent;
		font-size: 0;
		text-decoration: none;
		cursor: pointer;
		transition:
			transform 50ms,
			box-shadow 50ms;
	}
	/* Labels embossed in the shell under each pill. */
	.pill::after {
		content: 'SELECT';
		position: absolute;
		top: 5cqw;
		left: 50%;
		translate: -50% 0;
		font: 600 2.4cqw/1 var(--body-font);
		letter-spacing: 0.06em;
		color: var(--emboss);
		text-shadow: 0 0.15cqw 0 var(--emboss-hi);
	}
	a.pill::after {
		content: 'START';
	}
	/* Bigger hit area than the visible pill, label included. */
	.pill::before {
		content: '';
		position: absolute;
		inset: -3cqw -2cqw -7cqw;
	}
	a.pill:focus-visible,
	a.round:focus-visible {
		outline: 0.8cqw solid #fff;
		outline-offset: 1cqw;
	}

	.round:active,
	.round.down {
		transform: translateY(0.7cqw);
		box-shadow:
			inset 0 0.4cqw 0.5cqw rgba(255, 255, 255, 0.12),
			inset 0 -0.3cqw 0.5cqw rgba(0, 0, 0, 0.3),
			0 0 0 0.5cqw rgba(20, 0, 40, 0.14),
			0 0.1cqw 0 #16121a,
			0 0.3cqw 0.5cqw rgba(0, 0, 0, 0.35);
	}
	.pill:active,
	.pill.down {
		transform: translateY(0.4cqw);
		box-shadow:
			0 0.1cqw 0 #1f1b24,
			0 0.2cqw 0.3cqw rgba(0, 0, 0, 0.3),
			0 0 0 0.45cqw rgba(20, 0, 40, 0.14);
	}

	/* Speaker: staggered columns of drilled holes, the patch's corners rounded off. */
	.speaker {
		--hole: radial-gradient(
			circle,
			#0e0b12 0 0.55cqw,
			color-mix(in oklab, var(--shell-hi), transparent 45%) 0.6cqw 0.8cqw,
			transparent 0.85cqw
		);
		top: 136cqw;
		left: 72.4cqw;
		width: 21.6cqw;
		height: 23cqw;
		border-radius: 3cqw 5cqw 7cqw 5cqw;
		background:
			var(--hole) 0 0 / 6cqw 2.85cqw,
			var(--hole) 3cqw 1.42cqw / 6cqw 2.85cqw;
	}

	/* Konami egg: the press prompt makes way for the textbox; the shell cycles through every colour. */
	.glitch .press {
		visibility: hidden;
	}
	.glitch .shell {
		animation: rainbow 1.2s linear infinite;
	}
	.fireworks {
		position: fixed;
		inset: 0;
		z-index: 50;
		pointer-events: none;
		overflow: hidden;
	}
	.burst {
		position: absolute;
	}
	/* Each spark starts at the burst's centre, flies out to (--dx, --dy), drifts down a little and fades. */
	.spark {
		position: absolute;
		translate: -50% -50%;
		font-size: 22px;
		line-height: 1;
		text-shadow: 0 0 8px currentColor;
		opacity: 0;
		animation: spark 1.6s cubic-bezier(0.15, 0.8, 0.3, 1) var(--delay) both;
	}
	.spark.me {
		width: 40px;
		height: 40px;
		border-radius: 50%;
		box-shadow: 0 0 0 2px #fff, 0 0 12px rgb(255 255 255 / 0.7);
	}
	@keyframes spark {
		0% {
			opacity: 1;
			transform: translate(0, 0) scale(0.3) rotate(0deg);
		}
		70% {
			opacity: 1;
		}
		100% {
			opacity: 0;
			transform: translate(var(--dx), calc(var(--dy) + 40px)) scale(1) rotate(200deg);
		}
	}
	@keyframes rainbow {
		to {
			filter: hue-rotate(360deg);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.glitch .shell {
			animation: none;
			filter: hue-rotate(180deg);
		}
		/* No flying: the faces and stars just appear around each burst, then go with the egg. */
		.spark {
			animation: none;
			opacity: 1;
			transform: translate(calc(var(--dx) * 0.6), calc(var(--dy) * 0.6));
		}
	}

	@media (max-width: 899px) {
		.device {
			--below: 100px;
		}
		main {
			gap: 14px;
		}
	}

	/* Attract mode slides: drawn on the glass like the title, in the pixel font. */
	.attract {
		position: absolute;
		inset: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 3cqw;
		padding-bottom: 8cqw;
		background: inherit;
		font-family: var(--pixel-font);
		color: var(--pixel);
		animation: slide-in 0.35s steps(4) both;
	}
	.slide-title {
		font-size: 3cqw;
	}
	.team {
		margin: 0;
		padding: 0;
		list-style: none;
		display: grid;
		grid-template-columns: repeat(3, 13cqw);
		gap: 1.5cqw 3cqw;
	}
	.team li {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.6cqw;
		font-size: 1.9cqw;
	}
	.team img {
		width: 11cqw;
		height: 11cqw;
		object-fit: contain;
		image-rendering: pixelated;
	}
	.journey {
		margin: 0;
		display: grid;
		grid-template-columns: auto auto;
		gap: 2cqw 5cqw;
		font-size: 2.4cqw;
	}
	.journey dd {
		margin: 0;
		text-align: right;
	}
	@keyframes slide-in {
		from {
			clip-path: inset(0 100% 0 0);
		}
	}

	@keyframes blink {
		50% {
			opacity: 0;
		}
	}

	/* Boot sequence. The resting styles above are the idle state; each step animates *from* off. */
	.booting .led {
		animation: led-on 0.15s 0.3s both;
	}
	.booting .lcd::before {
		animation: power 0.55s 0.5s both;
	}
	.booting h1 img {
		animation: drop 0.6s 1.1s both;
	}
	.booting .row img {
		--from: -105%;
		animation-delay: 1.5s;
	}
	.booting .flash {
		animation: flash 0.3s 2.2s both;
	}
	.booting .press {
		--on: 2.2s;
	}
	.booting .textbox {
		animation: appear 0s 2.2s both;
	}
	@keyframes led-on {
		from {
			background: #4a1c1a;
			box-shadow: 0 0 0 0.35cqw #1a181f;
		}
	}
	@keyframes power {
		from {
			opacity: 1;
		}
		30% {
			opacity: 1;
		}
		40% {
			opacity: 0.15;
		}
		52% {
			opacity: 0.7;
		}
		64% {
			opacity: 0;
		}
		74% {
			opacity: 0.35;
		}
	}
	@keyframes drop {
		from {
			translate: 0 var(--from, -48cqw);
			animation-timing-function: cubic-bezier(0.5, 0, 1, 1);
		}
		60% {
			translate: 0 1.6cqw;
			animation-timing-function: ease-out;
		}
		80% {
			translate: 0 -0.6cqw;
			animation-timing-function: ease-in-out;
		}
	}
	@keyframes flash {
		30% {
			opacity: 0.9;
		}
	}
	@keyframes appear {
		from {
			visibility: hidden;
		}
	}
</style>
