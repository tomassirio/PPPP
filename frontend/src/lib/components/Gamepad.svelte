<!-- D-pad, A/B, Start/Select. Click/tap/keyboard -> POST /api/v1/inputs. Keys: arrows, Z=A, X=B, Enter=Start, Shift/Backspace=Select. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { keyToButton, type Button } from '#lib/play.ts';

	let { mode }: { mode: 'ANARCHY' | 'DEMOCRACY' } = $props();

	let lastPress = $state('');
	let status = $state('');
	let blockedUntil = $state(0);
	let now = $state(Date.now());
	let compound = $state('');
	const blocked = $derived(now < blockedUntil);

	let statusTimer: ReturnType<typeof setTimeout> | undefined;
	function say(text: string) {
		status = text;
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
			say(mode === 'DEMOCRACY' ? `Voted ${lastPress}` : `Sent ${lastPress}`);
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

	function submitCompound(e: SubmitEvent) {
		e.preventDefault();
		const c = compound.trim();
		if (!c) return;
		send(c);
		compound = '';
	}

	const dpad: { b: Button; label: string; path: string; area: string }[] = [
		{ b: 'up', label: 'Up', path: 'M6 15l6-6 6 6', area: 'up' },
		{ b: 'left', label: 'Left', path: 'M15 6l-6 6 6 6', area: 'left' },
		{ b: 'right', label: 'Right', path: 'M9 6l6 6-6 6', area: 'right' },
		{ b: 'down', label: 'Down', path: 'M6 9l6 6 6-6', area: 'down' }
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
			<button type="button" class="round pixel b" class:held={held === 'b'} aria-label="B button" disabled={blocked} onclick={() => send('b')}
				>B</button
			>
			<button type="button" class="round pixel" class:held={held === 'a'} aria-label="A button" disabled={blocked} onclick={() => send('a')}
				>A</button
			>
		</div>
	</div>
	<div class="meta">
		<button type="button" class="pill" class:held={held === 'select'} aria-label="Select" disabled={blocked} onclick={() => send('select')}
			>SELECT</button
		>
		<button type="button" class="pill" class:held={held === 'start'} aria-label="Start" disabled={blocked} onclick={() => send('start')}
			>START</button
		>
	</div>
</div>

<p class="last" role="status" aria-live="polite">
	{#if status}{status}{:else if lastPress}Your last press: <strong class="pixel">{lastPress}</strong>{/if}
</p>

{#if mode === 'ANARCHY'}
	<form class="compound" onsubmit={submitCompound}>
		<label for="compound">Combo</label>
		<input
			id="compound"
			type="text"
			placeholder="left2down2"
			autocomplete="off"
			spellcheck="false"
			maxlength="40"
			bind:value={compound}
		/>
		<button type="submit" disabled={blocked}>Send</button>
	</form>
{/if}

<p class="hint">
	{mode === 'DEMOCRACY' ? 'In democracy, each press is a vote. ' : ''}Keyboard: arrow keys, Z for A, X for B,
	Enter for Start, Shift for Select.
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
	button:focus-visible,
	input:focus-visible {
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
		min-height: 20px;
		font-size: 14px;
	}
	.last strong {
		font-size: 11px;
	}
	.compound {
		display: flex;
		gap: 8px;
		align-items: center;
		font-size: 14px;
	}
	.compound label {
		color: var(--muted);
	}
	.compound input {
		flex: 1 1 auto;
		min-width: 0;
		height: 40px;
		box-sizing: border-box;
		background: var(--bg);
		border: 1px solid #3a4851;
		border-radius: 8px;
		color: var(--text);
		padding: 0 12px;
		font: inherit;
	}
	.compound button {
		height: 40px;
		padding: 0 14px;
		border-radius: 8px;
		background: var(--accent);
		color: var(--ink);
		font-weight: 600;
	}
	.hint {
		margin: 0;
		font-size: 13px;
		color: var(--muted);
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
	/* Phones: no keyboard, so no combo box or key hints. */
	@media (max-width: 760px) {
		.compound,
		.hint {
			display: none;
		}
	}
</style>
