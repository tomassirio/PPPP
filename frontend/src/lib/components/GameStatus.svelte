<!-- Party and badges read from the game's RAM: GET /api/v1/game/state, kept current by GAME_STATE.
     Hidden for games without a profile. Sprites come from the ROM via /api/v1/game/icons/{id}.png. -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';

	/** Shape of GameSummary (backend model). */
	type Member = { name: string; species: string; level: number; hp: number; maxHp: number; status: string | null; icon: number };
	type Summary = {
		game: string;
		counters: { label: string; value: number; max: number }[];
		party: Member[];
		badges: { name: string; place: string; earned: boolean }[];
	};

	const badgeArt = import.meta.glob('../assets/badges/*.png', { eager: true, import: 'default' }) as Record<string, string>;
	const art = (name: string) => badgeArt[`../assets/badges/${name.toLowerCase()}.png`];

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

	const slots = $derived(summary ? [...summary.party, ...Array(Math.max(0, 6 - summary.party.length)).fill(null)] : []);
	const earned = $derived(summary?.badges.filter((b) => b.earned).length ?? 0);
	const next = $derived(summary?.badges.find((b) => !b.earned));
	const dex = $derived(summary?.counters.find((c) => c.label === 'Pokédex'));

	function hp(m: Member) {
		const r = m.maxHp > 0 ? m.hp / m.maxHp : 0;
		return { pct: Math.round(r * 100), level: r > 0.5 ? 'ok' : r > 0.2 ? 'mid' : 'low' };
	}
</script>

{#if summary}
	<section class="panel card" aria-labelledby="party-h">
		<div class="head">
			<h2 id="party-h">Party</h2>
			<span class="muted">{summary.party.length} / 6</span>
		</div>
		<ol class="party">
			{#each slots as m, i (i)}
				{#if m}
					{@const h = hp(m)}
					<li class="mon" class:fainted={m.hp === 0} title="{m.name} ({m.species}), Lv {m.level}, {m.hp}/{m.maxHp} HP{m.status ? `, ${m.status}` : ''}">
						<span class="sprite">
							{#if m.icon}<img src="/api/v1/game/icons/{m.icon}.png" alt="" width="56" height="56" />{/if}
						</span>
						<div class="info">
							<div class="row">
								<b class="name pixel">{m.name}</b>
								<span class="lv">Lv{m.level}</span>
							</div>
							<span class="bar" role="meter" aria-label="{m.name} HP" aria-valuemin="0" aria-valuemax={m.maxHp} aria-valuenow={m.hp}>
								<span class={h.level} style:width="{h.pct}%"></span>
							</span>
							<div class="row">
								<span class="species">{m.species}</span>
								<span class="hp" class:bad={m.hp === 0 || m.status}>
									{#if m.hp === 0}FNT{:else if m.status}{m.status}{:else}{m.hp}/{m.maxHp}{/if}
								</span>
							</div>
						</div>
					</li>
				{:else}
					<li class="empty">Empty</li>
				{/if}
			{/each}
		</ol>

		<hr />

		<div class="head">
			<h2 id="badges-h">Badges</h2>
			<span class="muted">{earned} / {summary.badges.length}</span>
		</div>
		<ul class="badges" aria-labelledby="badges-h">
			{#each summary.badges as b (b.name)}
				{@const label = `${b.name} Badge (${b.place}): ${b.earned ? 'earned' : 'not yet'}`}
				<li class:locked={!b.earned} title={label}>
					{#if art(b.name)}<img src={art(b.name)} alt={label} />{:else}<span class="pixel" role="img" aria-label={label}>{b.name[0]}</span>{/if}
				</li>
			{/each}
		</ul>
		<p class="muted">
			{next ? `Next: ${next.place}` : 'All badges earned'}{#if dex} · Pokédex {dex.value}/{dex.max}{/if}
		</p>
	</section>
{/if}

<style>
	.card {
		padding: 16px 20px;
		display: flex;
		flex-direction: column;
		gap: 12px;
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
	}
	hr {
		width: 100%;
		margin: 0;
		border: none;
		border-top: 1px solid var(--border);
	}
	ol,
	ul {
		margin: 0;
		padding: 0;
		list-style: none;
	}
	.party {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
		gap: 8px;
	}
	.mon,
	.empty {
		height: 52px;
		box-sizing: border-box;
		border-radius: 10px;
	}
	.mon {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 6px 8px;
		background: var(--bg);
		min-width: 0;
	}
	.mon.fainted {
		opacity: 0.6;
	}
	.mon.fainted img {
		filter: grayscale(1);
	}
	.sprite {
		flex: none;
		width: 38px;
		height: 38px;
		border-radius: 6px;
		background: #e2f3d6;
		display: flex;
		align-items: center;
		justify-content: center;
		overflow: hidden;
	}
	.sprite img {
		width: 36px;
		height: 36px;
		image-rendering: pixelated;
	}
	.info {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.row {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
		gap: 6px;
		min-width: 0;
	}
	.name {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-size: 9px;
		font-weight: normal;
	}
	.species {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-size: 11px;
		color: var(--muted);
	}
	.lv,
	.hp {
		flex: none;
		font-size: 11px;
		color: var(--muted);
		font-variant-numeric: tabular-nums;
	}
	.hp {
		color: var(--text);
	}
	.hp.bad {
		color: var(--danger);
		font-weight: 600;
	}
	.bar {
		display: block;
		height: 5px;
		border-radius: 999px;
		background: var(--border);
		overflow: hidden;
	}
	.bar span {
		display: block;
		height: 100%;
		border-radius: 999px;
		transition: width 0.4s ease;
	}
	.bar .ok {
		background: var(--grass);
	}
	.bar .mid {
		background: var(--accent);
	}
	.bar .low {
		background: var(--danger);
	}
	.empty {
		display: flex;
		align-items: center;
		justify-content: center;
		border: 1px dashed var(--border);
		font-size: 12px;
		color: var(--muted);
	}
	.badges {
		display: grid;
		grid-template-columns: repeat(8, minmax(0, 32px));
		justify-content: space-between;
		gap: 4px;
	}
	.badges li {
		aspect-ratio: 1;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.badges img {
		width: 100%;
		height: 100%;
		object-fit: contain;
	}
	.badges .locked img,
	.badges .locked span {
		filter: grayscale(1) brightness(0.55);
		opacity: 0.45;
	}
	@media (prefers-reduced-motion: reduce) {
		.bar span {
			transition: none;
		}
	}
</style>
