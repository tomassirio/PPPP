<!-- Factory reset: the whole run back to zero (POST /admin/factory-reset). Typed confirmation in a modal dialog. -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import { act, notice, setConsole, type ConsoleStatus } from './admin.svelte';

	const WORD = 'RESET';
	let dialog: HTMLDialogElement;
	let typed = $state('');
	let busy = $state(false);

	function open() {
		typed = '';
		dialog.showModal();
	}

	async function confirmReset(e: SubmitEvent) {
		e.preventDefault();
		if (typed !== WORD || busy) return;
		busy = true;
		const s = await act('Factory reset', () => api<ConsoleStatus>('POST', '/admin/factory-reset'));
		busy = false;
		dialog.close();
		if (s) {
			setConsole(s);
			notice.text = 'Factory reset: done. New game!';
		}
	}
</script>

<section class="panel" aria-labelledby="reset-h">
	<h2 id="reset-h" class="pixel">Danger zone</h2>
	<p class="muted">Start the whole run over from zero. There is no undo.</p>
	<div class="row actions">
		<button class="danger" onclick={open}>Factory reset…</button>
	</div>
</section>

<dialog bind:this={dialog} class="reset" aria-labelledby="reset-title">
	<form onsubmit={confirmReset}>
		<h2 id="reset-title" class="pixel">Reset everything?</h2>
		<p>This wipes:</p>
		<ul>
			<li>the cartridge's battery save and <b>every save state</b> (manual and automatic)</li>
			<li>the party box and the game event history</li>
			<li>all chat messages and the "last pressed" list</li>
			<li>the input queue and mode votes</li>
		</ul>
		<p class="muted">Players, roles, bans, settings and ROMs stay. The console reboots into a brand new game.</p>
		<label>
			Type <b>{WORD}</b> to confirm
			<input bind:value={typed} autocomplete="off" spellcheck="false" />
		</label>
		<div class="row actions">
			<button type="button" onclick={() => dialog.close()}>Cancel</button>
			<button type="submit" class="danger" disabled={typed !== WORD || busy}>
				{busy ? 'Resetting…' : 'Reset everything'}
			</button>
		</div>
	</form>
</dialog>

<style>
	.reset {
		max-width: min(480px, calc(100vw - 32px));
		padding: 24px;
		border: 2px solid var(--danger);
		border-radius: 12px;
		background: var(--panel);
		color: var(--text);
	}
	.reset::backdrop {
		background: rgb(0 0 0 / 0.6);
	}
	form {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	h2 {
		margin: 0;
		font-size: 12px;
		color: var(--danger);
	}
	p,
	ul {
		margin: 0;
	}
	ul {
		padding-left: 20px;
		line-height: 1.6;
	}
	label {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	input {
		font: inherit;
		padding: 8px 10px;
		border-radius: 6px;
		border: 1px solid var(--border-strong);
		background: var(--bg);
		color: var(--text);
	}
	.actions {
		justify-content: flex-end;
	}
	button[disabled] {
		opacity: 0.5;
		cursor: not-allowed;
	}
</style>
