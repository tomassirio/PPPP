<!-- Queued inputs (GET /api/v1/queue, refreshed after INPUT_EXECUTED) and recently executed ones from SSE.
     Fills its container's height: the queue on top, then Recent, oldest to newest, kept scrolled to the newest. -->
<script lang="ts">
	import { tick } from 'svelte';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { formatCommand, shortUser } from '#lib/play.ts';

	type Queued = { id: string; user: string; command: string };
	const SHOWN = 5;

	let queue = $state<Queued[]>([]);
	let recent = $state<Queued[]>([]);
	let list = $state<HTMLOListElement>();
	let stick = true; // false while the reader has scrolled up

	const pin = () => {
		if (list && stick) list.scrollTop = list.scrollHeight;
	};

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
		const off = on('INPUT_EXECUTED', async (e: Queued) => {
			recent = [...recent, e].slice(-30);
			clearTimeout(t);
			t = setTimeout(refresh, 300);
			await tick();
			pin();
		});
		return () => {
			off();
			clearTimeout(t);
		};
	});

	$effect(() => {
		if (!list) return;
		const resized = new ResizeObserver(pin);
		resized.observe(list);
		return () => resized.disconnect();
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
				<li><span class="pixel">{formatCommand(q.command)}</span><span class="muted">{shortUser(q.user)}</span></li>
			{/each}
			{#if queue.length > SHOWN}<li class="muted">+{queue.length - SHOWN} more</li>{/if}
		</ol>
	{:else}
		<p class="muted">Nothing queued.</p>
	{/if}

	<h3 id="recent-h">Recent</h3>
	{#if recent.length}
		<ol class="recent" bind:this={list} aria-labelledby="recent-h" onscroll={() => list && (stick = list.scrollHeight - list.scrollTop - list.clientHeight < 30)}>
			{#each recent as r (r.id)}
				<li><span class="pixel">{formatCommand(r.command)}</span><span class="muted">{shortUser(r.user)}</span></li>
			{/each}
		</ol>
	{:else}
		<p class="muted">No presses yet.</p>
	{/if}
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
	.recent {
		flex: 1 1 auto;
		min-height: 48px;
		overflow-y: auto;
		padding-right: 4px;
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
		gap: 8px;
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
