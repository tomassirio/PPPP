<!-- Sign-in page: an original handheld that boots into the banner. START (or A, Enter, Space) signs in. -->
<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { loadMe } from '#lib/session.svelte.js';
	import banner from '#lib/assets/banner.webp';

	const BOOT_MS = 3300;

	const denied = $derived(page.url.searchParams.get('error') === 'denied');

	let booting = $state(true);
	let held = $state<string | null>(null);
	let dir = $state<string | null>(null);
	let start: HTMLAnchorElement;

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

	function onkeydown(e: KeyboardEvent) {
		const t = e.target as HTMLElement;
		if (e.metaKey || e.ctrlKey || e.altKey) return;
		if (t.closest('input, textarea, select, [contenteditable]')) return;
		if (skip()) return e.preventDefault();
		if (e.key === 'Enter' || e.key === ' ') {
			e.preventDefault();
			if (!e.repeat) signIn();
		} else if (ARROWS[e.key]) {
			e.preventDefault();
			dir = ARROWS[e.key];
		}
	}

	function onkeyup(e: KeyboardEvent) {
		if (ARROWS[e.key] === dir) dir = null;
	}

	// Which D-pad arm the pointer landed on, from its offset to the pad's centre.
	function aim(e: PointerEvent) {
		const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
		const x = e.clientX - r.left - r.width / 2;
		const y = e.clientY - r.top - r.height / 2;
		dir = Math.abs(x) > Math.abs(y) ? (x < 0 ? 'left' : 'right') : y < 0 ? 'up' : 'down';
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
		return () => clearTimeout(t);
	});
</script>

<svelte:window {onkeydown} {onkeyup} onpointerdown={skip} onpointerup={release} onblur={release} />

<!-- Maps the banner onto the four LCD greens, and drops soft alpha so edges stay hard. -->
<svg class="defs" aria-hidden="true" width="0" height="0">
	<filter id="lcd-ink" color-interpolation-filters="sRGB" x="0" y="0" width="100%" height="100%">
		<feColorMatrix
			type="matrix"
			values="0.3 0.59 0.11 0 0  0.3 0.59 0.11 0 0  0.3 0.59 0.11 0 0  0 0 0 1 0"
		/>
		<feComponentTransfer>
			<feFuncR type="discrete" tableValues="0.059 0.059 0.059 0.059 0.059 0.059 0.059 0.059 0.545 0.545 0.545 0.608 0.608 0.608 0.608 0.608" />
			<feFuncG type="discrete" tableValues="0.22 0.22 0.22 0.22 0.22 0.22 0.22 0.22 0.675 0.675 0.675 0.737 0.737 0.737 0.737 0.737" />
			<feFuncB type="discrete" tableValues="0.059 0.059 0.059 0.059 0.059 0.059 0.059 0.059 0.059 0.059 0.059 0.059 0.059 0.059 0.059 0.059" />
			<feFuncA type="discrete" tableValues="0 1" />
		</feComponentTransfer>
	</filter>
</svg>

<main>
	<div class="device" class:booting>
		<div class="shell" aria-hidden="true"></div>

		<div class="bezel">
			<p class="label" aria-hidden="true">PIBS &amp; PIBETTES</p>
			<span class="led" aria-hidden="true"></span>
			<div class="lcd" class:denied>
				<!-- The banner is one wide image; it is shown as two clipped rows so it is big enough to read. -->
				<h1>
					<span class="row"
						><img class="banner" src={banner} alt="Pibs&amp;PibettesPlayPokemon" width="924" height="111" /></span
					>
					<span class="row two" aria-hidden="true"><img class="banner" src={banner} alt="" /></span>
				</h1>
				<p class="press" aria-hidden="true">PRESS START</p>
				{#if denied}
					<p class="textbox" role="alert">You're not on the trainer list. Ask the host to add you.</p>
				{/if}
				<span class="flash" aria-hidden="true"></span>
			</div>
		</div>

		<p class="model" aria-hidden="true">PPPP</p>

		<div class="dpad" data-dir={dir} aria-hidden="true" onpointerdown={aim}>
			<span></span>
		</div>

		<div class="face" aria-hidden="true">
			<!-- svelte-ignore a11y_no_static_element_interactions (decorative, hidden from AT) -->
			<span class="round" class:down={held === 'b'} onpointerdown={() => (held = 'b')}>B</span>
			<a
				class="round"
				class:down={held === 'a'}
				href="/oauth2/authorization/google"
				data-sveltekit-reload
				tabindex="-1"
				onpointerdown={() => (held = 'a')}>A</a
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

		<div class="speaker" aria-hidden="true">
			<span></span><span></span><span></span><span></span><span></span><span></span>
		</div>
	</div>

	<p class="help">Press START to sign in with Google.</p>
</main>

<style>
	.defs {
		position: absolute;
	}
	main {
		min-height: 100vh;
		min-height: 100dvh;
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 28px;
		padding: 24px 12px;
		overflow: hidden;
		background: radial-gradient(ellipse 60% 55% at 50% 42%, #1a2228, var(--bg) 75%);
	}
	p {
		margin: 0;
	}
	.help {
		font-size: 14px;
		color: var(--muted);
	}

	/* Everything on the device is sized in cqw: 1cqw is 1% of the device width. */
	.device {
		--navy: #2c3672;
		--lcd-0: #0f380f;
		--lcd-1: #306230;
		--lcd-2: #8bac0f;
		--lcd-3: #9bbc0f;
		position: relative;
		width: min(400px, calc(100vw - 24px), calc((100dvh - 96px) / 1.64));
		min-width: 280px;
		aspect-ratio: 100 / 164;
		container-type: inline-size;
		user-select: none;
		-webkit-user-select: none;
		-webkit-tap-highlight-color: transparent;
		touch-action: manipulation;
	}
	.device > * {
		position: absolute;
	}

	.shell {
		inset: 0;
		border-radius: 3.5cqw 3.5cqw 19cqw 3.5cqw;
		background:
			linear-gradient(to bottom, transparent 4.6cqw, rgba(0, 0, 0, 0.16) 4.6cqw 4.9cqw, rgba(255, 255, 255, 0.5) 4.9cqw 5.2cqw, transparent 5.2cqw),
			linear-gradient(162deg, #dcd8cf 0%, #cbc6bb 55%, #bcb7ab 100%);
		box-shadow:
			inset 0.5cqw 0.6cqw 0.6cqw rgba(255, 255, 255, 0.55),
			inset -0.6cqw -0.9cqw 1.2cqw rgba(60, 50, 40, 0.18),
			0 1.4cqw 0 #9a958a,
			0 1.6cqw 0 #6f6b62,
			0 4cqw 6cqw rgba(0, 0, 0, 0.55),
			0 10cqw 14cqw rgba(0, 0, 0, 0.35);
	}
	/* The two short seams at the top corners. */
	.shell::before {
		content: '';
		position: absolute;
		top: 0;
		left: 7cqw;
		right: 7cqw;
		height: 4.9cqw;
		border-inline: 0.3cqw solid rgba(0, 0, 0, 0.15);
		box-shadow:
			inset 0.3cqw 0 rgba(255, 255, 255, 0.45),
			0.3cqw 0 rgba(255, 255, 255, 0.45);
	}

	.bezel {
		top: 9cqw;
		left: 7cqw;
		width: 86cqw;
		height: 72cqw;
		border-radius: 2.5cqw 2.5cqw 12cqw 2.5cqw;
		background: linear-gradient(170deg, #5f6272, #4c4f5d 70%, #454856);
		box-shadow:
			inset 0 0.3cqw 0.3cqw rgba(255, 255, 255, 0.12),
			inset 0 -0.4cqw 0.6cqw rgba(0, 0, 0, 0.35),
			0 0 0 0.3cqw rgba(0, 0, 0, 0.08);
	}
	.label {
		position: absolute;
		top: 2.4cqw;
		left: 4cqw;
		right: 4cqw;
		display: flex;
		align-items: center;
		gap: 2cqw;
		font: 600 2.3cqw/1 var(--body-font);
		letter-spacing: 0.22em;
		color: #b8bccb;
		white-space: nowrap;
	}
	.label::before,
	.label::after {
		content: '';
		flex: 1;
		height: 1.3cqw;
		background: linear-gradient(#c9a43c 0 0.45cqw, transparent 0.45cqw 0.85cqw, #5a76b8 0.85cqw);
	}
	.label::before {
		flex: 0 0 6cqw;
	}
	.led {
		position: absolute;
		top: 29cqw;
		left: 5.2cqw;
		width: 2.4cqw;
		height: 2.4cqw;
		border-radius: 50%;
		background: radial-gradient(circle at 35% 35%, #ffb3a0, #ff3a24 45%, #b3150c);
		box-shadow:
			0 0 0 0.35cqw #3a3c47,
			0 0 1.2cqw 0.5cqw rgba(255, 70, 40, 0.6),
			0 0 4cqw 1cqw rgba(255, 70, 40, 0.18);
	}

	.lcd {
		position: absolute;
		top: 7.5cqw;
		left: 13cqw;
		width: 60cqw;
		height: 54cqw;
		overflow: hidden;
		border-radius: 0.6cqw;
		background: radial-gradient(ellipse at 45% 40%, #a6c61a, var(--lcd-3) 55%, #8eae0e);
		box-shadow:
			inset 0.8cqw 0.8cqw 1.2cqw rgba(15, 40, 10, 0.45),
			inset -0.2cqw -0.2cqw 0.4cqw rgba(15, 40, 10, 0.25),
			0 0 0 0.4cqw #3d404c;
	}
	/* The unlit panel; fades out as the screen warms up. */
	.lcd::before {
		content: '';
		position: absolute;
		inset: 0;
		background: #3a4719;
		opacity: 0;
	}
	/* Dot-matrix grid laid over everything on the glass. */
	.lcd::after {
		content: '';
		position: absolute;
		inset: 0;
		pointer-events: none;
		background:
			repeating-linear-gradient(to right, rgba(15, 56, 15, 0.07) 0 1px, transparent 1px 3px),
			repeating-linear-gradient(to bottom, rgba(15, 56, 15, 0.07) 0 1px, transparent 1px 3px);
	}
	h1 {
		position: absolute;
		top: 41%;
		left: 4cqw;
		right: 4cqw;
		margin: 0;
		transform: translateY(-50%);
		/* LCD pixels throw a faint shadow onto the back layer. */
		filter: drop-shadow(0.35cqw 0.45cqw 0 rgba(15, 56, 15, 0.2));
	}
	/* --k maps banner pixels to cqw; 487px is where 'Pibs&Pibettes' ends and 'PlayPokemon' starts. */
	h1 {
		--k: calc(52cqw / 487);
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 1cqw;
	}
	.row {
		display: block;
		overflow: hidden;
		width: calc(487 * var(--k));
		height: calc(111 * var(--k));
	}
	.two {
		width: calc(437 * var(--k));
	}
	.banner {
		display: block;
		width: calc(924 * var(--k));
		max-width: none;
		height: auto;
		filter: url(#lcd-ink);
	}
	.two .banner {
		margin-left: calc(-487 * var(--k));
	}
	.press {
		position: absolute;
		top: 77%;
		width: 100%;
		text-align: center;
		font: 2.9cqw/1 var(--pixel-font);
		color: var(--lcd-0);
		text-shadow: 0.35cqw 0.45cqw 0 rgba(15, 56, 15, 0.2);
		animation: blink 1.1s steps(1) infinite;
	}
	.denied h1 {
		--k: calc(38cqw / 487);
		top: 21%;
	}
	.denied .press {
		top: 46%;
	}
	.textbox {
		position: absolute;
		left: 2cqw;
		right: 2cqw;
		bottom: 2cqw;
		padding: 2.6cqw 3cqw 3cqw;
		font: 2.15cqw/1.75 var(--pixel-font);
		color: var(--lcd-0);
		background: #f2f6e2;
		border: 0.6cqw solid var(--lcd-0);
		border-radius: 0.8cqw;
		box-shadow:
			inset 0 0 0 0.5cqw #f2f6e2,
			inset 0 0 0 0.8cqw var(--lcd-1);
	}
	.textbox::after {
		content: '';
		position: absolute;
		right: 2.6cqw;
		bottom: 1.6cqw;
		border: 0.9cqw solid transparent;
		border-top-color: var(--lcd-0);
		border-bottom-width: 0;
		animation: blink 0.8s steps(1) infinite;
	}
	.flash {
		position: absolute;
		inset: 0;
		background: #e4f2a0;
		opacity: 0;
		pointer-events: none;
	}

	.model {
		top: 83cqw;
		left: 8cqw;
		font: italic 800 5.4cqw/1 var(--body-font);
		letter-spacing: 0.04em;
		color: var(--navy);
		text-shadow: 0 0.2cqw 0 rgba(255, 255, 255, 0.45);
	}

	/* Controls */
	.dpad {
		top: 95cqw;
		left: 8cqw;
		width: 28cqw;
		height: 28cqw;
		border-radius: 50%;
		background: radial-gradient(circle, rgba(0, 0, 0, 0.12) 0 58%, transparent 70%);
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
	.dpad::before,
	.dpad::after {
		content: '';
		width: 8.4cqw;
		height: 24cqw;
		border-radius: 1.2cqw;
		background: linear-gradient(135deg, #3a3c43, #212227);
		box-shadow:
			0 0.9cqw 0 #121316,
			0 1.4cqw 1.6cqw rgba(0, 0, 0, 0.35);
	}
	.dpad::after {
		width: 24cqw;
		height: 8.4cqw;
		box-shadow: inset 0 0.25cqw 0 rgba(255, 255, 255, 0.07);
	}
	/* Centre dimple, drawn above both arms. */
	.dpad span {
		z-index: 1;
		width: 4.4cqw;
		height: 4.4cqw;
		border-radius: 50%;
		background: radial-gradient(circle at 40% 35%, #1b1c20, #2c2e34);
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

	.face {
		top: 98cqw;
		left: 53cqw;
		width: 38cqw;
		height: 18cqw;
		border-radius: 9cqw;
		rotate: -25deg;
		background: linear-gradient(rgba(0, 0, 0, 0.08), rgba(0, 0, 0, 0.13));
		box-shadow:
			inset 0 0.5cqw 0.6cqw rgba(0, 0, 0, 0.14),
			inset 0 -0.3cqw 0.3cqw rgba(255, 255, 255, 0.35);
	}
	.round {
		position: absolute;
		top: 2.2cqw;
		width: 13.6cqw;
		height: 13.6cqw;
		border-radius: 50%;
		background: radial-gradient(circle at 38% 32%, #c2477a, #9b2856 45%, #7a1b42);
		box-shadow:
			inset 0 0.4cqw 0.5cqw rgba(255, 255, 255, 0.18),
			0 0.9cqw 0 #561130,
			0 1.4cqw 1.6cqw rgba(0, 0, 0, 0.35);
		/* The letter sits under the button, on the shell. */
		color: transparent;
		font-size: 0;
		text-decoration: none;
		cursor: pointer;
		transition:
			transform 50ms,
			box-shadow 50ms;
	}
	.round:first-child {
		left: 2.2cqw;
	}
	.round:last-child {
		right: 2.2cqw;
	}
	.round::after {
		position: absolute;
		top: 15.6cqw;
		left: 0;
		right: 0;
		text-align: center;
		font: italic 800 3.2cqw/1 var(--body-font);
		letter-spacing: 0.05em;
		color: var(--navy);
	}
	.round:first-child::after {
		content: 'B';
	}
	.round:last-child::after {
		content: 'A';
	}

	.pills {
		top: 133cqw;
		left: 30cqw;
		width: 36cqw;
		height: 10cqw;
		display: flex;
		justify-content: space-between;
		rotate: -25deg;
	}
	.pill {
		position: relative;
		width: 13cqw;
		height: 3.6cqw;
		border-radius: 2cqw;
		background: linear-gradient(#9a9daa, #7c7f8c);
		box-shadow:
			0 0.6cqw 0 #555864,
			0 0.9cqw 1cqw rgba(0, 0, 0, 0.3),
			0 0 0 0.6cqw rgba(0, 0, 0, 0.07);
		color: transparent;
		font-size: 0;
		text-decoration: none;
		cursor: pointer;
		transition:
			transform 50ms,
			box-shadow 50ms;
	}
	.pill::after {
		content: 'SELECT';
		position: absolute;
		top: 6.2cqw;
		left: 50%;
		translate: -50% 0;
		font: italic 800 2.4cqw/1 var(--body-font);
		letter-spacing: 0.1em;
		color: var(--navy);
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
		outline: 0.8cqw solid var(--navy);
		outline-offset: 1cqw;
	}

	.round:active,
	.round.down {
		transform: translateY(0.8cqw);
		box-shadow:
			0 0.1cqw 0 #561130,
			0 0.3cqw 0.5cqw rgba(0, 0, 0, 0.35);
	}
	.pill:active,
	.pill.down {
		transform: translateY(0.5cqw);
		box-shadow:
			0 0.1cqw 0 #555864,
			0 0.2cqw 0.3cqw rgba(0, 0, 0, 0.3),
			0 0 0 0.6cqw rgba(0, 0, 0, 0.07);
	}

	.speaker {
		top: 136cqw;
		left: 68cqw;
		width: 24cqw;
		height: 18cqw;
		display: flex;
		justify-content: space-between;
		rotate: -28deg;
	}
	.speaker span {
		width: 1.5cqw;
		border-radius: 1cqw;
		background: linear-gradient(#8f8a80, #a29d92);
		box-shadow:
			inset 0 0.4cqw 0.4cqw rgba(0, 0, 0, 0.35),
			0 0.2cqw 0 rgba(255, 255, 255, 0.5);
	}

	@keyframes blink {
		50% {
			opacity: 0;
		}
	}

	/* Boot sequence. The resting styles above are the idle state; each step animates *from* off. */
	.booting .led {
		animation: led-on 0.15s 0.35s both;
	}
	.booting .lcd::before {
		animation: lcd-warm 0.7s 0.6s both;
	}
	.booting h1 {
		animation: drop 1.2s steps(18, end) 1.25s both;
	}
	.booting .flash {
		animation: flash 0.35s 2.55s both;
	}
	.booting .press {
		animation:
			appear 0s 2.9s both,
			blink 1.1s steps(1) 2.9s infinite;
	}
	.booting .textbox {
		animation: appear 0s 2.9s both;
	}
	@keyframes led-on {
		from {
			background: #4a1c1a;
			box-shadow: 0 0 0 0.35cqw #3a3c47;
		}
	}
	@keyframes lcd-warm {
		from {
			opacity: 1;
		}
		30% {
			opacity: 0.55;
		}
		45% {
			opacity: 0.7;
		}
	}
	@keyframes drop {
		from {
			transform: translateY(-50%) translateY(-34cqw);
		}
	}
	@keyframes flash {
		30% {
			opacity: 0.85;
		}
	}
	@keyframes appear {
		from {
			visibility: hidden;
		}
	}
</style>
