<!-- Current mode plus the active players' mode votes; vote via POST /api/v1/votes/mode. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';

	let {
		mode,
		votes = $bindable()
	}: { mode: 'ANARCHY' | 'DEMOCRACY'; votes: Record<string, number> } = $props();

	const dem = $derived(mode === 'DEMOCRACY');
	const other = $derived(dem ? 'ANARCHY' : 'DEMOCRACY');
	const label = (m: string) => m[0] + m.slice(1).toLowerCase();
	const switchVotes = $derived(votes[other] ?? 0);
	const keepVotes = $derived(votes[mode] ?? 0);
	const pct = $derived(
		switchVotes + keepVotes ? Math.round((switchVotes * 100) / (switchVotes + keepVotes)) : 0
	);
	let error = $state('');

	async function vote(m: string) {
		error = '';
		try {
			votes = await api<Record<string, number>>('POST', '/votes/mode', { mode: m });
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'Vote failed';
		}
	}
</script>

<section class="panel card" aria-labelledby="mode-h">
	<h2 id="mode-h">Mode</h2>
	<div>
		<p class="mode {mode.toLowerCase()}">{label(mode)}</p>
		<p class="muted">
			{dem ? 'Each window runs the most-voted button.' : 'Every press runs in arrival order.'}
		</p>
	</div>
	<div class="bar-wrap">
		<div class="line">
			<span>Switch to {other.toLowerCase()}</span>
			<span class="muted">{switchVotes} vs {keepVotes} to keep</span>
		</div>
		<div
			class="bar"
			role="progressbar"
			aria-label="Votes to switch"
			aria-valuenow={pct}
			aria-valuemin="0"
			aria-valuemax="100"
		>
			<div class={other.toLowerCase()} style:width="{pct}%"></div>
		</div>
	</div>
	<div class="actions">
		<button type="button" class="primary {other.toLowerCase()}" onclick={() => vote(other)}>Vote {other.toLowerCase()}</button>
		<button type="button" class="ghost" onclick={() => vote(mode)}>Keep {label(mode)}</button>
	</div>
	{#if error}<p class="muted" role="alert">{error}</p>{/if}
</section>

<style>
	.card {
		padding: 20px;
		display: flex;
		flex-direction: column;
		gap: 14px;
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
	.mode {
		font-size: 22px;
		font-weight: 700;
		margin-bottom: 4px;
	}
	.muted {
		font-size: 14px;
		line-height: 20px;
		color: var(--muted);
	}
	.bar-wrap {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.line {
		display: flex;
		justify-content: space-between;
		gap: 8px;
		flex-wrap: wrap;
		font-size: 14px;
	}
	.bar {
		height: 8px;
		border-radius: 4px;
		background: var(--border);
		overflow: hidden;
	}
	.bar div {
		height: 8px;
		transition: width 0.3s;
	}
	.actions {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	button {
		height: 44px;
		padding: 0 16px;
		border-radius: 8px;
		font-weight: 600;
		font-size: 14px;
	}
	button:focus-visible {
		outline: 3px solid var(--accent);
		outline-offset: 2px;
	}
	.primary {
		border: none;
		color: var(--ink);
	}
	.primary:hover {
		filter: brightness(1.12);
	}
	p.anarchy {
		color: var(--anarchy);
	}
	p.democracy {
		color: var(--democracy);
	}
	button.anarchy,
	.bar div.anarchy {
		background: var(--anarchy);
	}
	button.democracy,
	.bar div.democracy {
		background: var(--democracy);
	}
	.ghost {
		border: 1px solid #3a4851;
		background: transparent;
		color: var(--text);
	}
	@media (prefers-reduced-motion: reduce) {
		.bar div {
			transition: none;
		}
	}
</style>
