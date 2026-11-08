<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '#lib/api.ts';
	import { act, consoleStatus, setConsole, type ConsoleStatus } from './admin.svelte';

	type Cartridge = { id: string; title: string; size: number; cgb: boolean };

	let cartridges = $state<Cartridge[]>([]);
	let file = $state<FileList | null>(null);

	const load = async () => {
		const list = await act('Load cartridges', () => api<Cartridge[]>('GET', '/admin/cartridges'), undefined, true);
		if (list) cartridges = list;
	};
	onMount(load);

	async function upload(e: SubmitEvent) {
		e.preventDefault();
		const f = file?.[0];
		if (!f) return;
		const form = new FormData();
		form.append('file', f);
		if (await act(`Upload ${f.name}`, () => api('POST', '/admin/cartridges', form))) {
			(e.currentTarget as HTMLFormElement).reset();
			await load();
		}
	}

	async function insert(id: string) {
		const s = await act(`Insert ${id}`, () => api<ConsoleStatus>('PUT', '/admin/console/cartridge', { cartridgeId: id }));
		if (s) setConsole(s);
	}

	async function remove(id: string) {
		const ok = await act(`Delete ${id}`, () => api('DELETE', `/admin/cartridges/${encodeURIComponent(id)}`).then(() => true), `Delete cartridge ${id} and its saves?`);
		if (ok) await load();
	}
</script>

<section class="panel" aria-labelledby="carts-h">
	<h2 id="carts-h" class="pixel">Cartridges</h2>
	<div class="scroll">
		<table>
			<thead><tr><th>Title</th><th>ID</th><th>Size</th><th>Color</th><th></th></tr></thead>
			<tbody>
				{#each cartridges as cart (cart.id)}
					<tr>
						<td>{cart.title}</td>
						<td><code>{cart.id}</code></td>
						<td>{Math.round(cart.size / 1024)} KB</td>
						<td>{cart.cgb ? 'GBC' : 'GB'}</td>
						<td class="row">
							{#if consoleStatus.cartridgeId === cart.id}
								<span class="muted">inserted</span>
							{:else}
								<button onclick={() => insert(cart.id)} title={consoleStatus.power === 'ON' ? 'Power off first' : undefined}>Insert</button>
								<button class="danger" onclick={() => remove(cart.id)}>Delete</button>
							{/if}
						</td>
					</tr>
				{:else}
					<tr><td colspan="5" class="muted">No cartridges uploaded.</td></tr>
				{/each}
			</tbody>
		</table>
	</div>
	<form class="row" onsubmit={upload}>
		<label>ROM file <input type="file" accept=".gb,.gbc" required bind:files={file} /></label>
		<button class="primary">Upload</button>
	</form>
</section>
