<!-- D-pad, A/B, Start/Select. Click/tap/keyboard -> POST /api/v1/inputs. Keys: arrows, Z=A, X=B, Enter=Start, Shift/Backspace=Select. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { keyToButton, type Button } from '#lib/play.ts';
	import { SHELLS, saveShell, savedShell, shellVars } from '#lib/shells.ts';

	let { mode }: { mode: 'ANARCHY' | 'DEMOCRACY' } = $props();

	// The player's controller shell (colours), remembered in this browser.
	let shell = $state(savedShell());
	const vars = $derived(shellVars(shell));
	let picker = $state<HTMLDetailsElement>();
	function pick(id: string) {
		shell = SHELLS.find((s) => s.id === id) ?? SHELLS[0];
		saveShell(shell.id);
	}

	let lastPress = $state('');
	let status = $state('');
	let blockedUntil = $state(0);
	let now = $state(Date.now());
	const blocked = $derived(now < blockedUntil);

	let statusError = $state(false);
	let statusTimer: ReturnType<typeof setTimeout> | undefined;
	function say(text: string, error = true) {
		status = text;
		statusError = error;
		clearTimeout(statusTimer);
		statusTimer = setTimeout(() => (status = ''), 3000);
	}

	function block(seconds: number) {
		blockedUntil = Date.now() + seconds * 1000;
		now = Date.now();
		setTimeout(() => (now = Date.now()), seconds * 1000 + 10);
	}

	let inFlight = false;

	async function send(command: string) {
		if (Date.now() < blockedUntil) return;
		lastPress = command.toUpperCase();
		inFlight = true;
		try {
			await api('POST', '/inputs', { command, repeat: 1 });
			say(mode === 'DEMOCRACY' ? 'Voted' : 'Sent', false);
		} catch (e) {
			if (!(e instanceof ApiError)) return say('Network error, try again');
			if (e.status === 429) {
				block(1);
				say('Slow down a little');
			} else if (e.status === 503) {
				block(e.retryAfter ?? 1);
				say('Queue is full, wait a moment');
			} else if (e.status === 400) {
				say(e.detail);
			} else {
				say(`Not sent: ${e.detail}`);
			}
		} finally {
			inFlight = false;
		}
	}

	/** The button the keyboard is holding, drawn pressed like a click or tap. */
	let held = $state<Button | null>(null);
	let heldTimer: ReturnType<typeof setTimeout> | undefined;

	/*
	 * Holding an arrow key in anarchy keeps walking: one press every REPEAT_MS. The buffer is one press in flight
	 * per player: a repeat waits until the server accepted the previous one, and pauses while it says slow down
	 * or queue full. The browser's own key auto-repeat is ignored. Democracy is one vote per window: no repeats.
	 */
	const REPEAT_MS = 250;
	const ARROWS: Button[] = ['up', 'down', 'left', 'right'];
	let repeating: Button | null = null;
	let repeatTimer: ReturnType<typeof setInterval> | undefined;

	function startRepeat(b: Button) {
		stopRepeat();
		repeating = b;
		repeatTimer = setInterval(() => {
			if (!inFlight && Date.now() >= blockedUntil) send(b);
		}, REPEAT_MS);
	}

	function stopRepeat() {
		clearInterval(repeatTimer);
		repeating = null;
	}

	$effect(() => stopRepeat); // leaving the page

	function onkeydown(e: KeyboardEvent) {
		if (e.key === 'Escape' && picker?.open) {
			picker.open = false;
			return;
		}
		const b = keyToButton(e);
		if (!b) {
			if (e.repeat && repeating) e.preventDefault(); // auto-repeat of the held arrow: no page scroll
			return;
		}
		e.preventDefault();
		held = b;
		clearTimeout(heldTimer);
		send(b);
		if (mode === 'ANARCHY' && ARROWS.includes(b)) startRepeat(b);
		else heldTimer = setTimeout(() => (held = null), 1000); // in case the keyup never arrives (focus lost)
	}

	function onkeyup(e: KeyboardEvent) {
		if (repeating && e.key.startsWith('Arrow')) stopRepeat(); // even if focus moved into a text field meanwhile
		clearTimeout(heldTimer);
		heldTimer = setTimeout(() => (held = null), 120); // a quick tap still shows
	}

	function onclick(e: MouseEvent) {
		if (picker?.open && !picker.contains(e.target as Node)) picker.open = false;
	}

	/** A short buzz for touch presses (Android; ignored where unsupported, e.g. iPhone). */
	function buzz(e: Event) {
		if ((e as PointerEvent).pointerType === 'touch') navigator.vibrate?.(12);
	}

	function tap(e: MouseEvent, b: Button) {
		buzz(e);
		send(b);
	}

	/* D-pad by pointer: the press goes out on touch-down; holding repeats like a held arrow key (anarchy).
	   Keyboard activation of the focused button still arrives as a click with detail 0. */
	function pressDir(e: PointerEvent, b: Button) {
		if (e.button !== 0 || blocked) return;
		buzz(e);
		held = b;
		send(b);
		if (mode === 'ANARCHY') startRepeat(b);
	}

	function releaseDir() {
		if (held && ARROWS.includes(held)) {
			stopRepeat();
			held = null;
		}
	}

	function onblur() {
		stopRepeat();
		held = null;
	}

	const dpad: { b: Button; label: string; path: string; area: string; key: string }[] = [
		{ b: 'up', label: 'Up', path: 'M6 15l6-6 6 6', area: 'up', key: 'ArrowUp' },
		{ b: 'left', label: 'Left', path: 'M15 6l-6 6 6 6', area: 'left', key: 'ArrowLeft' },
		{ b: 'right', label: 'Right', path: 'M9 6l6 6-6 6', area: 'right', key: 'ArrowRight' },
		{ b: 'down', label: 'Down', path: 'M6 9l6 6 6-6', area: 'down', key: 'ArrowDown' }
	];
</script>

<svelte:window {onkeydown} {onkeyup} {onblur} {onclick} />

<div class="shell" style={vars}>
	<details class="shells" bind:this={picker}>
		<summary aria-label="Controller colour: {shell.name}" title="Controller colour: {shell.name}">
			<span class="swatch" style:background={shell.shell}></span>
		</summary>
		<fieldset>
			<legend>Controller colour</legend>
			<div class="grid">
				{#each SHELLS as s (s.id)}
					<label title={s.name}>
						<input
							type="radio"
							name="shell"
							value={s.id}
							checked={s.id === shell.id}
							onchange={() => {
								pick(s.id);
								if (picker) picker.open = false;
							}}
						/>
						<span class="swatch" style:background={s.shell}><span class="dot" style:background={s.ab}></span></span>
						<span class="sr-only">{s.name}</span>
					</label>
				{/each}
			</div>
			<p class="current">{shell.name}</p>
		</fieldset>
	</details>
	<!-- Phones: the last press as a chip in the shell (the keyboard card is hidden there). -->
	{#if lastPress || status}
		<p class="chip-last" aria-hidden="true">
			{#if lastPress}<strong class="pixel">{lastPress}</strong>{/if}
			{#if status}<span class:error={statusError}>{status}</span>{/if}
		</p>
	{/if}
	<div class="row">
		<div class="dpad">
			{#each dpad as d (d.b)}
				<button
					type="button"
					class="dir"
					class:held={held === d.b}
					style:grid-area={d.area}
					aria-label={d.label}
					aria-keyshortcuts={d.key}
					disabled={blocked}
					onpointerdown={(e) => pressDir(e, d.b)}
					onpointerup={releaseDir}
					onpointercancel={releaseDir}
					onpointerleave={releaseDir}
					oncontextmenu={(e) => e.preventDefault()}
					onclick={(e) => e.detail === 0 && send(d.b)}
				>
					<svg
						width="22"
						height="22"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2.5"
						stroke-linecap="round"
						stroke-linejoin="round"
						aria-hidden="true"><path d={d.path}></path></svg
					>
				</button>
			{/each}
			<div class="hub"></div>
		</div>
		<div class="ab">
			<button type="button" class="round pixel b" class:held={held === 'b'} aria-label="B button" aria-keyshortcuts="X" disabled={blocked} onclick={(e) => tap(e, 'b')}
				>B</button
			>
			<button type="button" class="round pixel" class:held={held === 'a'} aria-label="A button" aria-keyshortcuts="Z" disabled={blocked} onclick={(e) => tap(e, 'a')}
				>A</button
			>
		</div>
	</div>
	<div class="meta">
		<button type="button" class="pill" class:held={held === 'select'} aria-label="Select" aria-keyshortcuts="Shift" disabled={blocked} onclick={(e) => tap(e, 'select')}
			>SELECT</button
		>
		<button type="button" class="pill" class:held={held === 'start'} aria-label="Start" aria-keyshortcuts="Enter" disabled={blocked} onclick={(e) => tap(e, 'start')}
			>START</button
		>
	</div>
</div>

<section class="controls panel" style={vars} class:idle={!lastPress && !status} aria-label="Controls">
	<div class="head">
		<h2 class="keys-only">Keyboard</h2>
		<p class="last" role="status" aria-live="polite">
			{#if lastPress}<span class="muted">Last press</span><strong class="chip pixel">{lastPress}</strong>{/if}
			{#if status}<span class:error={statusError} class:muted={!statusError}>{status}</span>{/if}
		</p>
	</div>
	<div class="keys keys-only">
		<div class="cluster" role="img" aria-label="Arrow keys move">
			<kbd style:grid-area="up">↑</kbd><kbd style:grid-area="left">←</kbd><kbd style:grid-area="down">↓</kbd><kbd
				style:grid-area="right">→</kbd
			>
		</div>
		<ul class="map">
			<li><kbd>Z</kbd><span class="mini round pixel">A</span></li>
			<li><kbd>X</kbd><span class="mini round pixel">B</span></li>
			<li><kbd>Enter</kbd><span class="mini pill">START</span></li>
			<li><kbd>Shift</kbd><span class="mini pill">SELECT</span></li>
		</ul>
	</div>
</section>

<style>
	.shell {
		position: relative;
		background: var(--shell);
		box-shadow: inset 0 0 0 1px var(--pad-edge);
		border-radius: 16px 16px 48px 16px;
		padding: 24px;
		display: flex;
		flex-direction: column;
		gap: 24px;
	}
	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16px;
		padding: 0 8px;
	}
	.dpad {
		display: grid;
		grid-template-columns: repeat(3, 52px);
		grid-template-rows: repeat(3, 52px);
		grid-template-areas: '. up .' 'left hub right' '. down .';
	}
	.hub {
		grid-area: hub;
		background: var(--dpad);
	}
	button {
		border: none;
		touch-action: manipulation;
		-webkit-tap-highlight-color: transparent;
		user-select: none;
	}
	button:disabled {
		opacity: 0.55;
		cursor: not-allowed;
	}
	button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.dir {
		-webkit-touch-callout: none; /* no long-press menu while holding a direction */
		background: var(--dpad);
		color: var(--dpad-ink);
		border-radius: 6px;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0;
	}
	.dir:active:not(:disabled),
	.round:active:not(:disabled),
	.pill:active:not(:disabled),
	.held:not(:disabled) {
		filter: brightness(0.8);
	}
	.ab {
		display: flex;
		gap: 16px;
		align-items: center;
		transform: rotate(-20deg);
	}
	.round {
		width: 64px;
		height: 64px;
		border-radius: 50%;
		background: var(--ab);
		color: var(--ab-ink);
		font-size: 14px;
	}
	.round.b {
		margin-top: 28px;
	}
	.meta {
		display: flex;
		gap: 16px;
		justify-content: center;
	}
	.pill {
		min-width: 88px;
		height: 44px;
		border-radius: 22px;
		background: var(--pill);
		color: var(--pill-ink);
		font-size: 12px;
		font-weight: 700;
		letter-spacing: 0.08em;
	}
	/* Colour picker: a swatch in the shell's corner opening a grid of shells. */
	.shells {
		position: absolute;
		top: 10px;
		right: 12px;
		z-index: 5;
	}
	.shells summary {
		list-style: none;
		cursor: pointer;
		border-radius: 50%;
	}
	.shells summary::-webkit-details-marker {
		display: none;
	}
	.shells summary:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.swatch {
		display: block;
		position: relative;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		box-shadow:
			inset 0 0 0 1px rgb(0 0 0 / 0.25),
			0 0 0 2px rgb(255 255 255 / 0.6);
	}
	.dot {
		position: absolute;
		right: 3px;
		bottom: 3px;
		width: 7px;
		height: 7px;
		border-radius: 50%;
	}
	fieldset {
		position: absolute;
		top: 30px;
		right: 0;
		margin: 0;
		padding: 12px;
		border: 1px solid var(--border);
		border-radius: 10px;
		background: var(--panel);
		box-shadow: 0 8px 24px rgb(0 0 0 / 0.35);
	}
	legend {
		float: left;
		width: 100%;
		margin-bottom: 8px;
		font-size: 11px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.grid {
		clear: both;
		display: grid;
		grid-template-columns: repeat(5, 30px);
		gap: 8px;
	}
	.grid label {
		position: relative;
		cursor: pointer;
	}
	.grid input {
		position: absolute;
		opacity: 0;
		inset: 0;
		margin: 0;
	}
	.grid .swatch {
		width: 30px;
		height: 30px;
	}
	.grid input:checked + .swatch {
		box-shadow:
			inset 0 0 0 1px rgb(0 0 0 / 0.25),
			0 0 0 3px var(--accent-text);
	}
	.grid input:focus-visible + .swatch {
		outline: 3px solid var(--accent-text);
		outline-offset: 3px;
	}
	.current {
		margin: 10px 0 0;
		font-size: 12px;
		color: var(--text);
	}
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
	}
	.controls {
		padding: 10px 14px;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.head {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 24px;
	}
	h2 {
		margin: 0;
		font-size: 11px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.last {
		margin: 0 0 0 auto;
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 12px;
	}
	.muted {
		color: var(--muted);
	}
	.error {
		color: var(--danger);
		font-weight: 600;
	}
	.chip {
		padding: 4px 7px;
		border-radius: 6px;
		background: var(--bg);
		border: 1px solid var(--border);
		font-size: 9px;
		line-height: 1;
	}
	.keys {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 40px;
	}
	/* Inverted T, like a real keyboard. */
	.cluster {
		display: grid;
		grid-template-columns: repeat(3, 26px);
		grid-template-rows: repeat(2, 24px);
		grid-template-areas: '. up .' 'left down right';
		gap: 3px;
	}
	.cluster kbd {
		width: 26px;
		min-width: 0;
		height: 24px;
		padding: 0;
	}
	/* Two key -> button pairs per row; keys right-aligned against their button. */
	.map {
		margin: 0;
		padding: 0;
		list-style: none;
		display: grid;
		grid-template-columns: auto auto auto auto;
		align-items: center;
		gap: 8px 10px;
	}
	.map li {
		display: contents;
	}
	.map kbd {
		justify-self: end;
	}
	.map .mini {
		justify-self: start;
	}
	.map li:nth-child(odd) .mini {
		margin-right: 18px;
	}
	kbd {
		min-width: 24px;
		height: 22px;
		padding: 0 6px;
		box-sizing: border-box;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		border-radius: 5px;
		background: var(--bg);
		border: 1px solid var(--border);
		border-bottom-width: 3px;
		color: var(--text);
		font: 600 11px/1 var(--body-font);
	}
	/* Tiny copies of the pad's buttons, so each key points at what it presses. */
	.mini.round {
		width: 20px;
		height: 20px;
		font-size: 8px;
		display: inline-flex;
		align-items: center;
		justify-content: center;
	}
	.mini.pill {
		min-width: 0;
		height: 18px;
		padding: 0 8px;
		border-radius: 9px;
		font-size: 9px;
		display: inline-flex;
		align-items: center;
	}
	/* Keyboard parts only where there is a keyboard and mouse; phones keep just the last press. */
	.keys-only {
		display: none;
	}
	.controls.idle {
		display: none;
	}
	@media (hover: hover) and (pointer: fine) and (min-width: 761px) {
		h2.keys-only {
			display: block;
		}
		.keys.keys-only,
		.controls.idle {
			display: flex;
		}
	}
	@media (max-width: 400px) {
		.shell {
			padding: 16px 12px;
		}
		.row {
			padding: 0;
			gap: 8px;
		}
		.dpad {
			grid-template-columns: repeat(3, 46px);
			grid-template-rows: repeat(3, 46px);
		}
		.round {
			width: 56px;
			height: 56px;
		}
		.ab {
			gap: 10px;
		}
	}
	.chip-last {
		display: none;
	}
	@media (max-width: 760px) {
		/* The status line stays in the card for screen readers; visually it is the chip in the shell. */
		.controls {
			position: absolute;
			width: 1px;
			height: 1px;
			overflow: hidden;
			clip: rect(0 0 0 0);
		}
		.chip-last {
			position: absolute;
			top: 10px;
			left: 14px;
			margin: 0;
			display: flex;
			align-items: center;
			gap: 6px;
			padding: 4px 8px;
			border-radius: 999px;
			background: rgb(0 0 0 / 0.18);
			color: var(--pill-ink);
			font-size: 11px;
		}
		.chip-last strong {
			font-size: 9px;
		}
	}
	/* Short phones: a tighter pad so it fits under the screen without scrolling. */
	@media (max-width: 760px) and (max-height: 760px) {
		.shell {
			padding: 14px 12px;
			gap: 12px;
		}
		.dpad {
			grid-template-columns: repeat(3, 44px);
			grid-template-rows: repeat(3, 44px);
		}
		.round {
			width: 52px;
			height: 52px;
		}
		.pill {
			height: 36px;
		}
	}
</style>
