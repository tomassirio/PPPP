<!-- "Last pressed": executed presses, oldest to newest, kept scrolled to the newest unless the reader scrolled up.
     Fills its container's height. -->
<script lang="ts">
	import { tick } from 'svelte';
	import { formatCommand } from '#lib/play.ts';
	import { presses, watchPresses } from '#lib/presses.svelte.ts';
	import Who from './Who.svelte';

	let list = $state<HTMLOListElement>();
	let stick = true;
	const pin = () => {
		if (list && stick) list.scrollTop = list.scrollHeight;
	};

	$effect(() => watchPresses());
	$effect(() => {
		void presses.recent.length;
		tick().then(pin);
	});
	$effect(() => {
		if (!list) return;
		const resized = new ResizeObserver(pin);
		resized.observe(list);
		return () => resized.disconnect();
	});
</script>

<div class="last">
	<h3 id="last-h">Last pressed</h3>
	{#if presses.recent.length}
		<ol
			bind:this={list}
			aria-labelledby="last-h"
			onscroll={() => list && (stick = list.scrollHeight - list.scrollTop - list.clientHeight < 30)}
		>
			{#each presses.recent as r (r.id)}
				<li><span class="pixel">{formatCommand(r.command)}</span><Who user={r.user} /></li>
			{/each}
		</ol>
	{:else}
		<p class="muted">No presses yet.</p>
	{/if}
</div>

<style>
	.last {
		display: flex;
		flex-direction: column;
		gap: 12px;
		flex: 1 1 auto;
		min-height: 0;
	}
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
		font-size: 13px;
		color: var(--muted);
	}
	ol {
		flex: 1 1 auto;
		min-height: 48px;
		overflow-y: auto;
		margin: 0;
		padding: 0 4px 0 0;
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
	.pixel {
		font-size: 10px;
		line-height: 1.6;
	}
</style>
