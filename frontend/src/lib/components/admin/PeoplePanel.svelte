<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { act } from './admin.svelte';
	import Pager from './Pager.svelte';

	type Role = 'PLAYER' | 'ADMIN';
	type User = { id: string; role: Role; banned: boolean; mutedUntil: number | null };

	let users = $state<User[]>([]);
	let email = $state('');
	let role = $state<Role>('PLAYER');
	let muteMinutes = $state(10);
	const PER_PAGE = 10;
	let page = $state(0);
	const shown = $derived(users.slice(page * PER_PAGE, (page + 1) * PER_PAGE));

	async function load() {
		const list = await act('Load users', () => api<User[]>('GET', '/admin/users'), undefined, true);
		if (list) users = list;
	}
	onMount(load);
	// Someone let in from Access requests joins the list.
	$effect(() => on('ACCESS_REQUESTS', load));

	const enc = encodeURIComponent;
	const until = (ms: number) => new Date(ms).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' });

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
	<!-- One column grid (rows use subgrid): email | role | actions line up across add, mute and every person. -->
	<div class="people">
		<form class="person" onsubmit={add}>
			<input type="email" required bind:value={email} autocomplete="off" placeholder="Email to add" aria-label="Email to add" />
			<select bind:value={role} aria-label="Role for new person">
				<option value="PLAYER">Player</option>
				<option value="ADMIN">Admin</option>
			</select>
			<button class="primary">Add</button>
		</form>
		<div class="person">
			<label for="mute-minutes" class="muted">Mute duration (min)</label>
			<input id="mute-minutes" type="number" min="1" max="525600" bind:value={muteMinutes} />
		</div>
		<ul class="list" aria-label="Allowlist">
			{#each shown as u (u.id)}
				<li class="person">
					<span class="email" title={u.id}>{u.id}</span>
					<select
						aria-label={`Role of ${u.id}`}
						value={u.role}
						onchange={(e) => change(`Set role of ${u.id}`, () => api('PUT', `/admin/allowlist/${enc(u.id)}`, { role: e.currentTarget.value }))}
					>
						<option value="PLAYER">Player</option>
						<option value="ADMIN">Admin</option>
					</select>
					<span class="actions">
						{#if u.mutedUntil}
							<button title="Muted until {until(u.mutedUntil)}" onclick={() => change(`Unmute ${u.id}`, () => api('DELETE', `/admin/users/${enc(u.id)}/mute`))}>Unmute</button>
						{:else}
							<button onclick={() => change(`Mute ${u.id}`, () => api('POST', `/admin/users/${enc(u.id)}/mute`, { minutes: muteMinutes }))}>Mute</button>
						{/if}
						{#if u.banned}
							<button onclick={() => change(`Unban ${u.id}`, () => api('DELETE', `/admin/users/${enc(u.id)}/ban`))}>Unban</button>
						{:else}
							<button class="danger" onclick={() => change(`Ban ${u.id}`, () => api('POST', `/admin/users/${enc(u.id)}/ban`), `Ban ${u.id}?`)}>Ban</button>
						{/if}
						{#if u.role === 'ADMIN'}
							<button class="danger" disabled title="Demote to player first" aria-label="Remove {u.id} (demote to player first)">Remove</button>
						{:else}
							<button class="danger" onclick={() => change(`Remove ${u.id}`, () => api('DELETE', `/admin/allowlist/${enc(u.id)}`), `Remove ${u.id} from the allowlist?`)}>Remove</button>
						{/if}
					</span>
				</li>
			{:else}
				<li class="muted">Allowlist is empty.</li>
			{/each}
		</ul>
	</div>
	<Pager bind:page total={users.length} perPage={PER_PAGE} label="People pages" noun="people" />
</section>
