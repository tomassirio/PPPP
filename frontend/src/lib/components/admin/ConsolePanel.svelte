<script lang="ts">
	import { api } from '#lib/api.ts';
	import { act, consoleStatus as c, notice, setConsole, type ConsoleStatus } from './admin.svelte';

	const speeds = [0.25, 0.5, 1, 1.5, 2, 3, 4];

	async function consoleAction(label: string, method: 'POST' | 'PUT', path: string, body?: unknown, confirmText?: string) {
		const s = await act(label, () => api<ConsoleStatus>(method, `/admin/console${path}`, body), confirmText);
		if (s) setConsole(s);
	}

	async function flush() {
		const r = await act('Flush queue', () => api<{ dropped: number }>('DELETE', '/admin/queue'), 'Drop every queued input?');
		if (r) notice.text = `Flush queue: ${r.dropped} input(s) dropped`;
	}
</script>

<section class="panel" aria-labelledby="console-h">
	<h2 id="console-h" class="pixel">Console</h2>
	<dl class="status">
		<dt>Power</dt><dd>{c.power}</dd>
		<dt>Cartridge</dt><dd>{c.cartridgeId ?? 'none'}</dd>
		<dt>Paused</dt><dd>{c.paused ? 'yes' : 'no'}</dd>
		<dt>Speed</dt><dd>{c.speed === null ? 'unknown' : `${c.speed}x`}</dd>
	</dl>
	<div class="row actions">
		{#if c.power === 'ON'}
			<button class="danger" onclick={() => consoleAction('Power off', 'PUT', '/power', { state: 'OFF' }, 'Power the console off?')}>Power off</button>
		{:else}
			<button class="primary" onclick={() => consoleAction('Power on', 'PUT', '/power', { state: 'ON' })}>Power on</button>
		{/if}
		<button onclick={() => consoleAction('Reset', 'POST', '/reset', undefined, 'Reset the console? Unsaved progress is lost.')}>Reset</button>
		{#if c.paused}
			<button onclick={() => consoleAction('Resume', 'POST', '/resume')}>Resume</button>
		{:else}
			<button onclick={() => consoleAction('Pause', 'POST', '/pause')}>Pause</button>
		{/if}
		<button class="danger" onclick={flush}>Flush queue</button>
	</div>
	<label class="row">
		Speed
		<select
			value={c.speed ?? ''}
			onchange={(e) => consoleAction('Set speed', 'PUT', '/speed', { multiplier: Number(e.currentTarget.value) })}
		>
			{#if c.speed === null}<option value="" disabled>?</option>{/if}
			{#each speeds as s (s)}<option value={s}>{s}x</option>{/each}
		</select>
	</label>
</section>
