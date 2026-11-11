<!-- People who signed in with Google but are not on the allowlist (GET /admin/access-requests). Accept with a role,
     or reject: a rejected account is not listed again (adding it by hand in People still works). -->
<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { act } from './admin.svelte';

	type Role = 'PLAYER' | 'ADMIN';
	type Request = { email: string; name: string | null; picture: string | null; requestedAt: number };

	let requests = $state<Request[]>([]);
	let roles = $state<Record<string, Role>>({});

	async function load() {
		const list = await act('Load access requests', () => api<Request[]>('GET', '/admin/access-requests'), undefined, true);
		if (list) requests = list;
	}
	onMount(load);
	$effect(() => on('ACCESS_REQUESTS', load));

	const enc = encodeURIComponent;
	const when = (ms: number) => new Date(ms).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' });

	async function accept(r: Request) {
		const role = roles[r.email] ?? 'PLAYER';
		if (await act(`Let in ${r.email}`, () => api('POST', `/admin/access-requests/${enc(r.email)}/accept`, { role }).then(() => true)))
			await load();
	}

	async function reject(r: Request) {
		const ok = await act(
			`Reject ${r.email}`,
			() => api('DELETE', `/admin/access-requests/${enc(r.email)}`).then(() => true),
			`Reject ${r.email}? Their next sign-in attempts won't show up here.`
		);
		if (ok) await load();
	}
</script>

<section class="panel" aria-labelledby="requests-h">
	<h2 id="requests-h" class="pixel">
		Access requests{#if requests.length}<span class="count">{requests.length}</span>{/if}
	</h2>
	{#if requests.length === 0}
		<p class="muted">No one is waiting. People who try to sign in without being on the list show up here.</p>
	{:else}
		<ul class="requests">
			{#each requests as r (r.email)}
				<li>
					{#if r.picture}
						<img src={r.picture} alt="" referrerpolicy="no-referrer" width="36" height="36" />
					{:else}
						<span class="avatar" aria-hidden="true">{(r.name ?? r.email).slice(0, 1).toUpperCase()}</span>
					{/if}
					<span class="who">
						<b>{r.name ?? r.email}</b>
						{#if r.name}<span class="muted">{r.email}</span>{/if}
						<span class="muted small">asked {when(r.requestedAt)}</span>
					</span>
					<select aria-label={`Role for ${r.email}`} bind:value={() => roles[r.email] ?? 'PLAYER', (v) => (roles[r.email] = v)}>
						<option value="PLAYER">Player</option>
						<option value="ADMIN">Admin</option>
					</select>
					<button class="primary" onclick={() => accept(r)}>Accept</button>
					<button class="danger" onclick={() => reject(r)}>Reject</button>
				</li>
			{/each}
		</ul>
	{/if}
</section>

<style>
	.count {
		margin-left: 8px;
		padding: 2px 7px;
		border-radius: 999px;
		background: var(--accent);
		color: var(--ink);
		font-size: 9px;
		vertical-align: middle;
	}
	p {
		margin: 0;
	}
	.requests {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	li {
		display: flex;
		align-items: center;
		gap: 10px;
		flex-wrap: wrap;
	}
	img,
	.avatar {
		width: 36px;
		height: 36px;
		border-radius: 50%;
		flex: none;
	}
	.avatar {
		display: grid;
		place-items: center;
		background: var(--border-strong);
		font-weight: 700;
	}
	.who {
		flex: 1;
		min-width: 160px;
		display: flex;
		flex-direction: column;
		overflow-wrap: anywhere;
	}
	.small {
		font-size: 12px;
	}
</style>
