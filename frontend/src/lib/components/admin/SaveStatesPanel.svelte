<script lang="ts">
	import { api } from '#lib/api.ts';
	import { act, consoleStatus } from './admin.svelte';

	type SaveState = { id: string; cartridgeId: string; kind: 'manual' | 'auto'; createdAt: string };

	let saves = $state<SaveState[]>([]);

	async function load() {
		const list = await act('Load save states', () => api<SaveState[]>('GET', '/admin/save-states'), undefined, true);
		if (list) saves = list;
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
	<div class="scroll">
		<table>
			<thead><tr><th>Time</th><th>Kind</th><th>ID</th><th></th></tr></thead>
			<tbody>
				{#each saves as s (s.id)}
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
</section>
