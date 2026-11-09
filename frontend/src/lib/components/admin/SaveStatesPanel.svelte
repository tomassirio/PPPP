<script lang="ts">
	import { api } from '#lib/api.ts';
	import { act, consoleStatus } from './admin.svelte';

	type SaveState = { id: string; cartridgeId: string; kind: 'manual' | 'auto'; createdAt: string };

	let saves = $state<SaveState[]>([]);
	const PER_PAGE = 10;
	let page = $state(0);
	const pages = $derived(Math.max(1, Math.ceil(saves.length / PER_PAGE)));
	const shown = $derived(saves.slice(page * PER_PAGE, (page + 1) * PER_PAGE));

	async function load() {
		const list = await act('Load save states', () => api<SaveState[]>('GET', '/admin/save-states'), undefined, true);
		if (!list) return;
		saves = list;
		page = Math.min(page, Math.max(0, Math.ceil(list.length / PER_PAGE) - 1));
	}

	// Save states belong to the inserted cartridge: reload when it changes.
	$effect(() => {
		void consoleStatus.cartridgeId;
		load();
	});

	async function create(label: string, path: string) {
		if (await act(label, () => api('POST', path))) await load();
	}
</script>

<section class="panel" aria-labelledby="saves-h">
	<h2 id="saves-h" class="pixel">Save states</h2>
	<div class="row">
		<button class="primary" onclick={() => create('Take save state', '/admin/save-states')}>Take now</button>
		<button onclick={() => create('Run autosave', '/admin/autosave/run')}>Run autosave now</button>
	</div>
	<div class="scroll saves">
		<table>
			<thead><tr><th>Time</th><th>Kind</th><th>ID</th><th></th></tr></thead>
			<tbody>
				{#each shown as s (s.id)}
					<tr>
						<td>{new Date(s.createdAt).toLocaleString()}</td>
						<td>{s.kind}</td>
						<td><code>{s.id}</code></td>
						<td>
							<button
								onclick={() =>
									act(`Load ${s.id}`, () => api('POST', `/admin/save-states/${encodeURIComponent(s.id)}/load`), `Load save state from ${new Date(s.createdAt).toLocaleString()}? Current progress is lost.`)}
								>Load</button
							>
						</td>
					</tr>
				{:else}
					<tr><td colspan="4" class="muted">{consoleStatus.cartridgeId ? 'No save states yet.' : 'No cartridge inserted.'}</td></tr>
				{/each}
			</tbody>
		</table>
	</div>
	{#if pages > 1}
		<nav class="row pager" aria-label="Save state pages">
			<button onclick={() => page--} disabled={page === 0}>Newer</button>
			<span class="muted" aria-live="polite">Page {page + 1} of {pages} · {saves.length} saves</span>
			<button onclick={() => page++} disabled={page >= pages - 1}>Older</button>
		</nav>
	{/if}
</section>
