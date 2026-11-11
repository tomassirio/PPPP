<!-- Queued inputs (GET /api/v1/queue, refreshed after INPUT_EXECUTED), then Last pressed. Fills its container. -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { formatCommand } from '#lib/play.ts';
	import LastPressed from './LastPressed.svelte';
	import Who from './Who.svelte';

	type Queued = { id: string; user: string; command: string };

	let queue = $state<Queued[]>([]);

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
		const off = on('INPUT_EXECUTED', () => {
			clearTimeout(t);
			t = setTimeout(refresh, 300);
		});
		const offReset = on('APP_RESET', refresh);
		return () => {
			off();
			offReset();
			clearTimeout(t);
		};
	});
</script>

<!-- Two columns split by a rule: the queue (left) and Last pressed (right), each scrolling on its own. -->
<section class="panel card" aria-labelledby="next-h">
	<div class="col">
		<div class="head">
			<h2 id="next-h">Up next</h2>
			<span class="count" aria-label="{queue.length}{queue.length === 10 ? ' or more' : ''} queued"
				>{queue.length}{queue.length === 10 ? '+' : ''}</span
			>
		</div>
		{#if queue.length}
			<ol class="queue">
				{#each queue as q (q.id)}
					<li><span class="pixel">{formatCommand(q.command)}</span><Who user={q.user} compact /></li>
				{/each}
			</ol>
		{:else}
			<p class="muted">Nothing queued.</p>
		{/if}
	</div>
	<div class="col last"><LastPressed /></div>
</section>

<style>
	.card {
		padding: 16px 0;
		display: grid;
		grid-template-columns: minmax(0, 2fr) minmax(0, 3fr); /* Last pressed gets room for full names */
		grid-template-rows: minmax(0, 1fr);
		box-sizing: border-box;
		height: 100%;
		min-height: 0;
	}
	.col {
		display: flex;
		flex-direction: column;
		gap: 12px;
		min-height: 0;
		min-width: 0;
		padding: 0 16px 0 20px;
	}
	.col.last {
		padding: 0 20px 0 16px;
		border-left: 1px solid var(--border);
	}
	.head {
		flex: none;
		justify-content: flex-start;
		gap: 8px;
	}
	.count {
		min-width: 18px;
		height: 18px;
		padding: 0 5px;
		box-sizing: border-box;
		border-radius: 999px;
		background: var(--border);
		color: var(--text);
		font-size: 11px;
		font-weight: 600;
		line-height: 18px;
		text-align: center;
	}
	.queue {
		flex: 1 1 auto;
		min-height: 0;
		overflow-y: auto;
	}
	.head {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
	}
	h2 {
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
		gap: 8px;
	}
	li {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 12px;
		font-size: 14px;
	}
	li .pixel {
		font-size: 10px;
		line-height: 1.6;
	}
</style>
