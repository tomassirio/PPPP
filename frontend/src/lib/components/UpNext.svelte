<!-- Queued inputs (GET /api/v1/queue, refreshed after INPUT_EXECUTED), then Last pressed. Fills its container. -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { formatCommand } from '#lib/play.ts';
	import LastPressed from './LastPressed.svelte';
	import Who from './Who.svelte';

	type Queued = { id: string; user: string; command: string };
	const SHOWN = 5;

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
		<ol class="queue">
			{#each queue.slice(0, SHOWN) as q (q.id)}
				<li><span class="pixel">{formatCommand(q.command)}</span><Who user={q.user} /></li>
			{/each}
			{#if queue.length > SHOWN}<li class="muted">+{queue.length - SHOWN} more</li>{/if}
		</ol>
	{:else}
		<p class="muted">Nothing queued.</p>
	{/if}
	<LastPressed />
</section>

<style>
	.card {
		padding: 16px 20px;
		display: flex;
		flex-direction: column;
		gap: 12px;
		box-sizing: border-box;
		height: 100%;
		min-height: 0;
	}
	/* Only Last pressed gives up height when the card is short; the queue part never shrinks away. */
	.card > :not(:last-child) {
		flex: none;
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
