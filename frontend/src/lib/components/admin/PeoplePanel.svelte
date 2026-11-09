<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '#lib/api.ts';
	import { act } from './admin.svelte';

	type Role = 'PLAYER' | 'ADMIN';
	type User = { id: string; role: Role; banned: boolean };

	let users = $state<User[]>([]);
	let email = $state('');
	let role = $state<Role>('PLAYER');
	let muteMinutes = $state(10);

	async function load() {
		const list = await act('Load users', () => api<User[]>('GET', '/admin/users'), undefined, true);
		if (list) users = list;
	}
	onMount(load);

	const enc = encodeURIComponent;

	// Runs an action that returns no body, then reloads the table.
	async function change(label: string, fn: () => Promise<unknown>, confirmText?: string) {
		if (await act(label, () => fn().then(() => true), confirmText)) await load();
	}

	async function add(e: SubmitEvent) {
		e.preventDefault();
		await change(`Add ${email}`, () => api('POST', '/admin/allowlist', { email, role }));
		email = '';
	}
</script>

<section class="panel" aria-labelledby="people-h">
	<h2 id="people-h" class="pixel">People</h2>
	<form class="row" onsubmit={add}>
		<label>Email <input type="email" required bind:value={email} autocomplete="off" /></label>
		<label>
			Role
			<select bind:value={role}>
				<option value="PLAYER">Player</option>
				<option value="ADMIN">Admin</option>
			</select>
		</label>
		<button class="primary">Add</button>
	</form>
	<label class="row">
		Mute duration (minutes)
		<input type="number" min="1" max="525600" bind:value={muteMinutes} />
	</label>
	<div class="scroll">
		<table>
			<thead><tr><th>Email</th><th>Role</th><th>Banned</th><th></th></tr></thead>
			<tbody>
				{#each users as u (u.id)}
					<tr>
						<td class="wrap">{u.id}</td>
						<td>
							<select
								aria-label={`Role of ${u.id}`}
								value={u.role}
								onchange={(e) => change(`Set role of ${u.id}`, () => api('PUT', `/admin/allowlist/${enc(u.id)}`, { role: e.currentTarget.value }))}
							>
								<option value="PLAYER">Player</option>
								<option value="ADMIN">Admin</option>
							</select>
						</td>
						<td>{u.banned ? 'yes' : 'no'}</td>
						<td class="row">
							<button onclick={() => change(`Mute ${u.id}`, () => api('POST', `/admin/users/${enc(u.id)}/mute`, { minutes: muteMinutes }))}>Mute</button>
							{#if !u.banned}
								<button class="danger" onclick={() => change(`Ban ${u.id}`, () => api('POST', `/admin/users/${enc(u.id)}/ban`), `Ban ${u.id}?`)}>Ban</button>
							{/if}
							<button class="danger" onclick={() => change(`Remove ${u.id}`, () => api('DELETE', `/admin/allowlist/${enc(u.id)}`), `Remove ${u.id} from the allowlist?`)}>Remove</button>
						</td>
					</tr>
				{:else}
					<tr><td colspan="4" class="muted">Allowlist is empty.</td></tr>
				{/each}
			</tbody>
		</table>
	</div>
</section>
