<!-- D-pad, A/B, Start/Select. Click/tap/keyboard -> POST /api/v1/inputs. Keys: arrows, Z=A, X=B, Enter=Start, Shift/Backspace=Select. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { keyToButton, type Button } from '#lib/play.ts';

	let { mode }: { mode: 'ANARCHY' | 'DEMOCRACY' } = $props();

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

	async function send(command: string) {
		if (Date.now() < blockedUntil) return;
		lastPress = command.toUpperCase();
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
		}
	}

	/** The button the keyboard is holding, drawn pressed like a click or tap. */
	let held = $state<Button | null>(null);
	let heldTimer: ReturnType<typeof setTimeout> | undefined;

	function onkeydown(e: KeyboardEvent) {
		const b = keyToButton(e);
		if (!b) return;
		e.preventDefault();
		held = b;
		clearTimeout(heldTimer);
		heldTimer = setTimeout(() => (held = null), 1000); // in case the keyup never arrives (focus lost)
		send(b);
	}

	function onkeyup() {
		clearTimeout(heldTimer);
		heldTimer = setTimeout(() => (held = null), 120); // a quick tap still shows
	}

	const dpad: { b: Button; label: string; path: string; area: string; key: string }[] = [
		{ b: 'up', label: 'Up', path: 'M6 15l6-6 6 6', area: 'up', key: 'ArrowUp' },
		{ b: 'left', label: 'Left', path: 'M15 6l-6 6 6 6', area: 'left', key: 'ArrowLeft' },
		{ b: 'right', label: 'Right', path: 'M9 6l6 6-6 6', area: 'right', key: 'ArrowRight' },
		{ b: 'down', label: 'Down', path: 'M6 9l6 6 6-6', area: 'down', key: 'ArrowDown' }
	];
</script>

<svelte:window {onkeydown} {onkeyup} />

<div class="shell">
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
					onclick={() => send(d.b)}
				>
					<svg
						width="22"
						height="22"
						viewBox="0 0 24 24"
						fill="none"
						stroke="#edebe3"
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
			<button type="button" class="round pixel b" class:held={held === 'b'} aria-label="B button" aria-keyshortcuts="X" disabled={blocked} onclick={() => send('b')}
				>B<kbd aria-hidden="true">X</kbd></button
			>
			<button type="button" class="round pixel" class:held={held === 'a'} aria-label="A button" aria-keyshortcuts="Z" disabled={blocked} onclick={() => send('a')}
				>A<kbd aria-hidden="true">Z</kbd></button
			>
		</div>
	</div>
	<div class="meta">
		<button type="button" class="pill" class:held={held === 'select'} aria-label="Select" aria-keyshortcuts="Shift" disabled={blocked} onclick={() => send('select')}
			>SELECT<kbd aria-hidden="true">Shift</kbd></button
		>
		<button type="button" class="pill" class:held={held === 'start'} aria-label="Start" aria-keyshortcuts="Enter" disabled={blocked} onclick={() => send('start')}
			>START<kbd aria-hidden="true">Enter</kbd></button
		>
	</div>
</div>

<p class="last" role="status" aria-live="polite">
	{#if lastPress}
		<span class="muted">Last press</span>
		<strong class="chip pixel">{lastPress}</strong>
	{/if}
	{#if status}<span class:error={statusError} class:muted={!statusError}>{status}</span>{/if}
	<span class="keys">Keyboard: arrows, plus the key on each button</span>
</p>

<style>
	.shell {
		background: #d8d3c4;
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
		background: #2b2d42;
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
		outline: 3px solid var(--accent);
		outline-offset: 2px;
	}
	.dir {
		background: #2b2d42;
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
		background: #bc4b51;
		color: #fff;
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
		background: #8d8a80;
		color: var(--ink);
		font-size: 12px;
		font-weight: 700;
		letter-spacing: 0.08em;
	}
	.last {
		margin: 0;
		min-height: 28px;
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 13px;
	}
	.muted {
		color: var(--muted);
	}
	.error {
		color: var(--danger);
		font-weight: 600;
	}
	.chip {
		padding: 5px 8px;
		border-radius: 6px;
		background: var(--panel);
		border: 1px solid var(--border);
		font-size: 10px;
		line-height: 1;
	}
	.keys {
		margin-left: auto;
		font-size: 12px;
		color: var(--muted);
	}
	/* Keyboard shortcuts as keycaps on the buttons, only where there is a keyboard and mouse. */
	button {
		position: relative;
	}
	kbd {
		display: none;
		position: absolute;
		right: -6px;
		bottom: -6px;
		padding: 2px 4px;
		border-radius: 4px;
		background: var(--bg);
		color: var(--text);
		border: 1px solid var(--border);
		font: 600 9px/1 var(--body-font);
		letter-spacing: 0;
		pointer-events: none;
	}
	.keys {
		display: none;
	}
	@media (hover: hover) and (pointer: fine) and (min-width: 761px) {
		kbd,
		.keys {
			display: inline-block;
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
</style>
