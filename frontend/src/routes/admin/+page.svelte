<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { on } from '#lib/events.ts';
	import { isAdmin, loadMe, session } from '#lib/session.svelte.ts';
	import { loadConsole, notice, setConsole, type ConsoleStatus } from '#lib/components/admin/admin.svelte.ts';
	import ConsolePanel from '#lib/components/admin/ConsolePanel.svelte';
	import CartridgesPanel from '#lib/components/admin/CartridgesPanel.svelte';
	import SaveStatesPanel from '#lib/components/admin/SaveStatesPanel.svelte';
	import SettingsPanel from '#lib/components/admin/SettingsPanel.svelte';
	import PeoplePanel from '#lib/components/admin/PeoplePanel.svelte';
	import AccessRequestsPanel from '#lib/components/admin/AccessRequestsPanel.svelte';
	import ResetPanel from '#lib/components/admin/ResetPanel.svelte';
	import UserMenu from '#lib/components/UserMenu.svelte';
	import '#lib/components/admin/admin.css';

	let loadFailed = $state(false);

	onMount(() => {
		let off: (() => void) | undefined;
		loadMe().catch(() => ((loadFailed = true), null)).then(async (me) => {
			if (loadFailed) return;
			if (!me) return goto('/');
			if (!isAdmin(me)) return;
			off = on('CONSOLE_CHANGED', (s: ConsoleStatus) => setConsole(s));
			await loadConsole();
		});
		return () => off?.();
	});
</script>

<svelte:head><title>Admin · Pibs&PibettesPlayPokemon</title></svelte:head>

<div class="admin">
	<header>
		<h1 class="pixel">Admin</h1>
		<span class="header-end"
			><a href="/play">Back to play</a>{#if session.me}<UserMenu me={session.me} />{/if}</span
		>
	</header>

	{#if session.me && isAdmin(session.me)}
		<p class="notice" class:error={notice.error} role="status" aria-live="polite">{notice.text}</p>
		<main>
			<div class="column">
				<h2 class="group">Game</h2>
				<ConsolePanel />
				<CartridgesPanel />
				<SaveStatesPanel />
			</div>
			<div class="column">
				<h2 class="group">Players &amp; settings</h2>
				<AccessRequestsPanel />
				<PeoplePanel />
				<SettingsPanel />
				<ResetPanel />
			</div>
		</main>
	{:else if session.me}
		<section class="panel"><p>Admins only. <a href="/play">Go to play</a></p></section>
	{:else if loadFailed}
		<section class="panel" role="alert"><p>Couldn't load your session. The server may be restarting. <a href="/" data-sveltekit-reload>Back to sign in</a></p></section>
	{/if}
</div>
