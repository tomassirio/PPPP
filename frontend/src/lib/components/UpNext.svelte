<!-- Queued inputs (GET /api/v1/queue, refreshed after INPUT_EXECUTED) and recently executed ones from SSE. -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { formatCommand, shortUser } from '#lib/play.ts';

	type Queued = { id: string; user: string; command: string };

	let queue = $state<Queued[]>([]);
	let recent = $state<Queued[]>([]);

	async function refresh() {
		try {
			queue = await api<Queued[]>('GET', '/queue?limit=10');
		} catch {
			/* keep the last list */
		}
	}

	$effect(() => {
		refresh();
		let t: ReturnType<typeof setTimeout> | undefined;
		const off = on('INPUT_EXECUTED', (e: Queued) => {
			recent = [e, ...recent].slice(0, 5);
			clearTimeout(t);
			t = setTimeout(refresh, 300);
		});
		return () => {
			off();
			clearTimeout(t);
		};
	});
</script>

<section class="panel card" aria-labelledby="next-h">
	<div class="head">
		<h2 id="next-h">Up next</h2>
		<span class="muted">{queue.length}{queue.length === 10 ? '+' : ''} queued</span>
	</div>
	{#if queue.length}
		<ol>
			{#each queue as q (q.id)}
				<li><span class="pixel">{formatCommand(q.command)}</span><span class="muted">{shortUser(q.user)}</span></li>
			{/each}
		</ol>
	{:else}
		<p class="muted">Nothing queued.</p>
	{/if}

	{#if recent.length}
		<h3>Recent</h3>
		<ol>
			{#each recent as r (r.id)}
				<li><span class="pixel">{formatCommand(r.command)}</span><span class="muted">{shortUser(r.user)}</span></li>
			{/each}
		</ol>
	{/if}
</section>

<style>
	.card {
		padding: 20px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.head {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
	}
	h2,
	h3 {
		margin: 0;
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	p {
		margin: 0;
	}
	.muted {
		font-size: 13px;
		color: var(--muted);
		overflow: hidden;
		text-overflow: ellipsis;
	}
	ol {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	li {
		display: flex;
		justify-content: space-between;
		gap: 12px;
		font-size: 14px;
	}
	li .pixel {
		font-size: 10px;
		line-height: 1.6;
	}
</style>
