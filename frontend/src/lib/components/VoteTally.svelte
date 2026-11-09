<!-- Open democracy window: live tally bars and countdown. Fed by /state.tally and VOTE_TALLY. -->
<script lang="ts">
	import type { Snippet } from 'svelte';
	import { tallyRows, type Tally } from '#lib/play.ts';

	/** children: shown at the bottom of the card, taking its remaining height (the play page puts Last pressed there). */
	let { tally, children }: { tally: Tally | null; children?: Snippet } = $props();

	let now = $state(Date.now());
	$effect(() => {
		const t = setInterval(() => (now = Date.now()), 250);
		return () => clearInterval(t);
	});

	const open = $derived(tally && tally.endsAt > now ? tally : null);
	const rows = $derived(open ? tallyRows(open.tally) : []);
	const total = $derived(rows.reduce((s, r) => s + r.votes, 0));
	const secs = $derived(open ? Math.ceil((open.endsAt - now) / 1000) : 0);
</script>

<!-- With children (the play page passes Last pressed): two columns split by a rule, like Up next. -->
<section class="panel card" class:split={!!children} aria-labelledby="tally-h">
	<div class="col">
		<div class="head">
			<h2 id="tally-h">This vote</h2>
			{#if open}<span class="muted">Closes in {secs} s · {total} voted</span>{/if}
		</div>
		{#if open}
			<ul>
				{#each rows as r, i (r.button)}
					<li>
						<span class="pixel">{r.button}</span>
						<div class="bar"><div style:width="{r.pct}%" class:lead={i === 0}></div></div>
						<span class="muted">{r.votes} {r.votes === 1 ? 'vote' : 'votes'}</span>
					</li>
				{/each}
			</ul>
		{:else}
			<p class="muted">No vote open. Press a button to start one.</p>
		{/if}
		{#if !children}
			<p class="muted">Press a button on the pad to vote. Repeat counts merge by median.</p>
		{/if}
	</div>
	{#if children}<div class="col side">{@render children()}</div>{/if}
</section>

<style>
	.card {
		padding: 20px;
		display: flex;
		flex-direction: column;
		gap: 14px;
		box-sizing: border-box;
		height: 100%;
		min-height: 0;
		overflow-y: auto; /* many buttons voted on a short screen: scroll inside, never over the party box */
	}
	.col {
		display: flex;
		flex-direction: column;
		gap: 14px;
		min-width: 0;
		min-height: 0;
	}
	.col > * {
		flex: none;
	}
	/* Split: the vote (2 parts) | Last pressed (3 parts, room for full names), each scrolling on its own. */
	.card.split {
		display: grid;
		grid-template-columns: minmax(0, 2fr) minmax(0, 3fr);
		grid-template-rows: minmax(0, 1fr);
		padding: 16px 0;
		overflow: hidden;
	}
	.split .col {
		padding: 0 16px 0 20px;
		overflow-y: auto;
	}
	.split .col.side {
		padding: 0 20px 0 16px;
		border-left: 1px solid var(--border);
		overflow: hidden;
	}
	.col.side > :global(*) {
		flex: 1 1 auto;
	}
	.head {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
		gap: 8px;
		flex-wrap: wrap;
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
		line-height: 19px;
		color: var(--muted);
	}
	ul {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	li {
		display: grid;
		grid-template-columns: 64px 1fr 64px;
		align-items: center;
		gap: 10px;
	}
	li .pixel {
		font-size: 10px;
	}
	li .muted {
		text-align: right;
	}
	.bar {
		height: 10px;
		border-radius: 5px;
		background: var(--border);
		overflow: hidden;
	}
	.bar div {
		height: 10px;
		background: #7fd1c7;
		transition: width 0.3s;
	}
	.bar div.lead {
		background: var(--accent);
	}
	@media (prefers-reduced-motion: reduce) {
		.bar div {
			transition: none;
		}
	}
</style>
