<!-- "N online" in the top bar; hover, focus or tap shows who. Live via PRESENCE events. -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import { on, onOpen } from '#lib/events.ts';

	type Viewer = { id: string; name: string; picture: string | null };
	let viewers = $state<Viewer[]>([]);
	let open = $state(false);
	let broken = $state(new Set<string>());

	function load() {
		api<Viewer[]>('GET', '/presence')
			.then((v) => {
				viewers = v;
			})
			.catch(() => {});
	}

	$effect(() => {
		load();
		const offs = [
			on('PRESENCE', (v: Viewer[]) => {
				viewers = v;
			}),
			onOpen(load) // our own stream is registered by now, so the list includes us
		];
		return () => offs.forEach((off) => off());
	});

	const initials = (name: string) =>
		name
			.split(/[\s@.]+/)
			.filter(Boolean)
			.slice(0, 2)
			.map((w) => w[0].toUpperCase())
			.join('');
</script>

<div class="presence" class:open role="presentation" onmouseleave={() => (open = false)}>
	<button
		type="button"
		aria-expanded={open}
		aria-controls="presence-list"
		onclick={() => (open = !open)}
		onkeydown={(e) => e.key === 'Escape' && (open = false)}
	>
		<span class="dot" aria-hidden="true"></span>
		{viewers.length} online
	</button>
	<ul id="presence-list" aria-label="Connected players">
		{#each viewers as v (v.id)}
			<li>
				{#if v.picture && !broken.has(v.id)}
					<img src={v.picture} alt="" referrerpolicy="no-referrer" onerror={() => (broken = new Set(broken).add(v.id))} />
				{:else}
					<span class="initials" aria-hidden="true">{initials(v.name)}</span>
				{/if}
				<span class="name">{v.name}</span>
			</li>
		{:else}
			<li class="empty">Nobody yet</li>
		{/each}
	</ul>
</div>

<style>
	.presence {
		position: relative;
	}
	button {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		height: 32px;
		padding: 0 12px;
		border: 1px solid var(--border);
		border-radius: 999px;
		background: transparent;
		color: var(--text);
		font-size: 13px;
		font-weight: 600;
		white-space: nowrap;
	}
	button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: #3ecf6e;
		box-shadow: 0 0 0 3px color-mix(in oklab, #3ecf6e, transparent 75%);
	}
	ul {
		display: none;
		position: absolute;
		top: 100%;
		right: 0; /* open leftward from the button's right edge, so it never runs off the window */
		z-index: 10;
		margin: 0;
		padding: 8px;
		min-width: 220px;
		max-width: calc(100vw - 32px);
		box-sizing: border-box;
		max-height: 320px;
		overflow-y: auto;
		list-style: none;
		background: var(--panel);
		border: 1px solid var(--border);
		border-radius: 12px;
		box-shadow: 0 12px 32px rgb(0 0 0 / 0.25);
		translate: 0 6px;
	}
	.presence:hover ul,
	.presence:focus-within ul,
	.open ul {
		display: block;
	}
	.presence::after {
		/* invisible hover bridge over the 6px gap */
		content: '';
		position: absolute;
		left: 0;
		right: 0;
		top: 100%;
		height: 8px;
	}
	li {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 6px 8px;
		font-size: 14px;
	}
	img,
	.initials {
		width: 28px;
		height: 28px;
		border-radius: 50%;
		flex: none;
	}
	.initials {
		display: flex;
		align-items: center;
		justify-content: center;
		background: #2a9d8f;
		color: #fff;
		font-size: 11px;
		font-weight: 700;
	}
	.name {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.empty {
		color: var(--muted);
	}
</style>
