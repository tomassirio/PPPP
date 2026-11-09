<!-- Progress read from the game's RAM: GET /api/v1/game/state, kept current by GAME_STATE. Hidden for games without a profile. -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';

	/** Shape of GameSummary (backend model). */
	type Summary = {
		game: string;
		counters: { label: string; value: number; max: number }[];
		party: { name: string; species: string; level: number; hp: number; maxHp: number; status: string | null }[];
	};

	let summary = $state<Summary | null>(null);

	function refresh() {
		api<Summary | undefined>('GET', '/game/state')
			.then((s) => (summary = s ?? null))
			.catch(() => {});
	}

	$effect(() => {
		refresh();
		const offs = [
			on('GAME_STATE', (s: Summary) => (summary = s)),
			on('CONSOLE_CHANGED', refresh) // another cartridge may have no profile
		];
		return () => offs.forEach((off) => off());
	});

	const pct = (hp: number, max: number) => (max > 0 ? Math.round((hp / max) * 100) : 0);
</script>

{#if summary}
	<section class="panel card" aria-labelledby="status-h">
		<div class="head">
			<h2 id="status-h">Our run</h2>
			<span class="muted">{summary.game}</span>
		</div>
		<dl class="counters">
			{#each summary.counters as c (c.label)}
				<div>
					<dt>{c.label}</dt>
					<dd><span class="pixel">{c.value}</span><span class="muted">/{c.max}</span></dd>
				</div>
			{/each}
		</dl>
		<h3>Party</h3>
		{#if summary.party.length}
			<ol class="party">
				{#each summary.party as m, i (i)}
					{@const p = pct(m.hp, m.maxHp)}
					<li>
						<div class="row">
							<span class="name" title={m.species}>{m.name}</span>
							<span class="muted">Lv. {m.level}{#if m.status} · <span class="status">{m.status}</span>{/if}</span>
						</div>
						<div
							class="bar"
							role="meter"
							aria-label="{m.name} HP"
							aria-valuemin="0"
							aria-valuemax={m.maxHp}
							aria-valuenow={m.hp}
						>
							<span class:low={p <= 20} class:mid={p > 20 && p <= 50} style:width="{p}%"></span>
						</div>
						<span class="hp muted">{m.hp}/{m.maxHp} HP</span>
					</li>
				{/each}
			</ol>
		{:else}
			<p class="muted">Nobody in the party yet.</p>
		{/if}
	</section>
{/if}

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
	h3,
	dt {
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
	}
	.counters {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(80px, 1fr));
		gap: 12px;
		margin: 0;
	}
	dd {
		margin: 6px 0 0;
	}
	dd .pixel {
		font-size: 14px;
		color: var(--accent);
	}
	.party {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.row {
		display: flex;
		justify-content: space-between;
		gap: 12px;
		font-size: 14px;
	}
	.name {
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.status {
		color: var(--danger);
		font-weight: 600;
	}
	.bar {
		height: 6px;
		margin-top: 6px;
		border-radius: 3px;
		background: var(--border);
		overflow: hidden;
	}
	.bar span {
		display: block;
		height: 100%;
		background: var(--grass);
		transition: width 0.4s ease;
	}
	.bar span.mid {
		background: var(--accent);
	}
	.bar span.low {
		background: var(--danger);
	}
	.hp {
		font-size: 12px;
	}
	@media (prefers-reduced-motion: reduce) {
		.bar span {
			transition: none;
		}
	}
</style>
