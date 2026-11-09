<!-- The avatar in the top bar opens this menu: who you are, light/dark (sun/moon), Admin (admins only), Sign out. -->
<script lang="ts">
	import { isAdmin, logout, type Me } from '#lib/session.svelte.ts';
	import { setTheme, theme } from '#lib/theme.svelte.ts';

	let { me }: { me: Me } = $props();

	let open = $state(false);
	let root = $state<HTMLDivElement>();
	let trigger = $state<HTMLButtonElement>();
	let pictureFailed = $state(false);

	const initials = $derived(
		me.name
			.split(/[\s@.]+/)
			.filter(Boolean)
			.slice(0, 2)
			.map((w) => w[0].toUpperCase())
			.join('')
	);

	// Auto follows the OS until the player picks one; the toggle shows whichever is in effect.
	let osDark = $state(typeof matchMedia !== 'undefined' && matchMedia('(prefers-color-scheme: dark)').matches);
	$effect(() => {
		const q = matchMedia('(prefers-color-scheme: dark)');
		const onChange = () => (osDark = q.matches);
		q.addEventListener('change', onChange);
		return () => q.removeEventListener('change', onChange);
	});
	const dark = $derived(theme.value === 'dark' || (theme.value === 'auto' && osDark));

	function close(refocus = false) {
		open = false;
		if (refocus) trigger?.focus();
	}

	function onclick(e: MouseEvent) {
		if (open && root && !root.contains(e.target as Node)) close();
	}

	function onkeydown(e: KeyboardEvent) {
		if (open && e.key === 'Escape') close(true);
	}
</script>

<svelte:window {onclick} {onkeydown} />

<div class="menu" bind:this={root}>
	<button
		type="button"
		class="trigger"
		bind:this={trigger}
		aria-haspopup="true"
		aria-expanded={open}
		aria-controls="user-menu"
		aria-label="Account menu for {me.name}"
		title={me.name}
		onclick={() => (open = !open)}
	>
		{#if me.picture && !pictureFailed}
			<!-- Google photo URLs reject requests that carry a Referer. -->
			<img class="avatar" src={me.picture} alt="" referrerpolicy="no-referrer" onerror={() => (pictureFailed = true)} />
		{:else}
			<span class="avatar initials" aria-hidden="true">{initials}</span>
		{/if}
		<svg class="caret" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"
			><path d="M6 9l6 6 6-6" /></svg
		>
	</button>

	{#if open}
		<div class="panel dropdown" id="user-menu">
			<div class="who">
				<strong>{me.name}</strong>
				<span>{me.id}</span>
			</div>

			<div class="row">
				<span id="theme-label">Theme</span>
				<div class="toggle" role="group" aria-labelledby="theme-label">
					<button type="button" aria-pressed={!dark} aria-label="Light" title="Light" onclick={() => setTheme('light')}>
						<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
							<circle cx="12" cy="12" r="4" />
							<path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
						</svg>
					</button>
					<button type="button" aria-pressed={dark} aria-label="Dark" title="Dark" onclick={() => setTheme('dark')}>
						<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
							<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" />
						</svg>
					</button>
				</div>
			</div>

			{#if isAdmin(me)}
				<a class="item" href="/admin">Admin</a>
			{/if}
			<button type="button" class="item" onclick={logout}>Sign out</button>
		</div>
	{/if}
</div>

<style>
	.menu {
		position: relative;
	}
	.trigger {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 2px 6px 2px 2px;
		border: 1px solid transparent;
		border-radius: 999px;
		background: none;
		color: var(--muted);
		cursor: pointer;
	}
	.trigger:hover,
	.trigger[aria-expanded='true'] {
		border-color: var(--border);
	}
	.avatar {
		width: 36px;
		height: 36px;
		border-radius: 50%;
		object-fit: cover;
		display: block;
	}
	.initials {
		background: #2a9d8f;
		color: #fff;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 13px;
		font-weight: 700;
	}
	.dropdown {
		position: absolute;
		top: calc(100% + 8px);
		right: 0;
		z-index: 20;
		min-width: 220px;
		padding: 8px;
		display: flex;
		flex-direction: column;
		gap: 4px;
		box-shadow: 0 8px 24px rgb(0 0 0 / 0.3);
	}
	.who {
		display: flex;
		flex-direction: column;
		gap: 2px;
		padding: 8px 10px 10px;
		border-bottom: 1px solid var(--border);
		margin-bottom: 4px;
	}
	.who strong {
		font-size: 14px;
		color: var(--text);
	}
	.who span {
		font-size: 12px;
		color: var(--muted);
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 6px 10px;
		font-size: 14px;
		color: var(--text);
	}
	/* Sun | moon, one pressed. */
	.toggle {
		display: flex;
		padding: 2px;
		border-radius: 999px;
		background: var(--bg);
		border: 1px solid var(--border);
	}
	.toggle button {
		width: 32px;
		height: 28px;
		display: flex;
		align-items: center;
		justify-content: center;
		border: none;
		border-radius: 999px;
		background: none;
		color: var(--muted);
		cursor: pointer;
	}
	.toggle button[aria-pressed='true'] {
		background: var(--panel);
		color: var(--text);
		box-shadow: 0 1px 3px rgb(0 0 0 / 0.25);
	}
	.item {
		display: block;
		width: 100%;
		padding: 8px 10px;
		border: none;
		border-radius: 6px;
		background: none;
		font: inherit;
		font-size: 14px;
		text-align: left;
		text-decoration: none;
		color: var(--text);
		cursor: pointer;
		box-sizing: border-box;
	}
	.item:hover {
		background: var(--border);
	}
	button:focus-visible,
	a:focus-visible {
		outline: 3px solid var(--accent-text, var(--accent));
		outline-offset: 2px;
	}
	@media (max-width: 760px) {
		.avatar {
			width: 32px;
			height: 32px;
		}
	}
</style>
