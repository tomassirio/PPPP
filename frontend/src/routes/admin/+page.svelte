<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { isAdmin, loadMe, session } from '#lib/session.svelte.ts';
	import { act, notice, setConsole, type ConsoleStatus } from '#lib/components/admin/admin.svelte.ts';
	import ConsolePanel from '#lib/components/admin/ConsolePanel.svelte';
	import CartridgesPanel from '#lib/components/admin/CartridgesPanel.svelte';
	import SaveStatesPanel from '#lib/components/admin/SaveStatesPanel.svelte';
	import SettingsPanel from '#lib/components/admin/SettingsPanel.svelte';
	import PeoplePanel from '#lib/components/admin/PeoplePanel.svelte';
	import '#lib/components/admin/admin.css';

	onMount(() => {
		let off: (() => void) | undefined;
		loadMe().then(async (me) => {
			if (!me) return goto('/');
			if (!isAdmin(me)) return;
			off = on('CONSOLE_CHANGED', (s: ConsoleStatus) => setConsole(s));
			const state = await act('Load console state', () => api<ConsoleStatus>('GET', '/state'), undefined, true);
			if (state) setConsole({ power: state.power, cartridgeId: state.cartridgeId, paused: state.paused, speed: state.speed });
		});
		return () => off?.();
	});
</script>

<svelte:head><title>Admin · Pibs&PibettesPlayPokemon</title></svelte:head>

<div class="admin">
	<header>
		<h1 class="pixel">Admin</h1>
		<a href="/play">Back to play</a>
	</header>

	{#if session.me && isAdmin(session.me)}
		<p class="notice" class:error={notice.error} role="status" aria-live="polite">{notice.text}</p>
		<main>
			<ConsolePanel />
			<CartridgesPanel />
			<SaveStatesPanel />
			<SettingsPanel />
			<PeoplePanel />
		</main>
	{:else if session.me}
		<section class="panel"><p>Admins only. <a href="/play">Go to play</a></p></section>
	{/if}
</div>
