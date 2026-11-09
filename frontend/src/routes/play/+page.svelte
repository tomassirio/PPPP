<!-- /play: game screen, pad, mode/votes/queue and chat. Desktop = columns, phone = screen + Play/Vote/Chat tabs. -->
<script lang="ts">
	import { goto } from '$app/navigation';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { loadMe, session } from '#lib/session.svelte.ts';
	import { currentShell } from '#lib/shell.svelte.ts';
	import type { GameState } from '#lib/play.ts';
	import Chat from '#lib/components/Chat.svelte';
	import GameScreen from '#lib/components/GameScreen.svelte';
	import GameStatus from '#lib/components/GameStatus.svelte';
	import GameToasts from '#lib/components/GameToasts.svelte';
	import ChatOverlay from '#lib/components/ChatOverlay.svelte';
	import ChatOverlayToggle from '#lib/components/ChatOverlayToggle.svelte';
	import PokedexBanner from '#lib/components/PokedexBanner.svelte';
	import Gamepad from '#lib/components/Gamepad.svelte';
	import ModeCard from '#lib/components/ModeCard.svelte';
	import Presence from '#lib/components/Presence.svelte';
	import SoundControl from '#lib/components/SoundControl.svelte';
	import UserMenu from '#lib/components/UserMenu.svelte';
	import ShellPicker from '#lib/components/ShellPicker.svelte';
	import UpNext from '#lib/components/UpNext.svelte';
	import LastPressed from '#lib/components/LastPressed.svelte';
	import VoteTally from '#lib/components/VoteTally.svelte';
	import banner from '#lib/assets/banner.webp';
	import ppppLogo from '#lib/assets/pppp-logo.png';

	let game = $state<GameState>({
		power: 'OFF',
		cartridgeId: null,
		paused: false,
		mode: 'ANARCHY',
		queueDepth: 0,
		tally: null,
		modeVotes: { ANARCHY: 0, DEMOCRACY: 0 }
	});
	type Tab = 'play' | 'vote' | 'party' | 'chat';
	let tab = $state<Tab>('play');

	// Phone tab bar: icons (static SVG paths, not user content) and badges for what changed on other tabs.
	const TABS: { id: Tab; label: string; icon: string }[] = [
		{ id: 'play', label: 'Play', icon: '<rect x="3" y="6" width="18" height="12" rx="4"/><path d="M7 12h4M9 10v4"/><circle cx="15.5" cy="11" r="1"/><circle cx="17.5" cy="13.5" r="1"/>' },
		{ id: 'vote', label: 'Vote', icon: '<path d="M4 13l4 4L20 5"/><path d="M4 20h16"/>' },
		{ id: 'party', label: 'Party', icon: '<circle cx="12" cy="12" r="9"/><path d="M3 12h6m6 0h6"/><circle cx="12" cy="12" r="3"/>' },
		{ id: 'chat', label: 'Chat', icon: '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/>' }
	];
	// Keep the phone's screen awake while /play is open and visible (Screen Wake Lock; ignored where unsupported).
	$effect(() => {
		if (!me || !('wakeLock' in navigator)) return;
		let lock: WakeLockSentinel | null = null;
		const request = () => {
			if (document.visibilityState === 'visible') navigator.wakeLock.request('screen').then((l) => (lock = l)).catch(() => {});
		};
		request();
		document.addEventListener('visibilitychange', request);
		return () => {
			document.removeEventListener('visibilitychange', request);
			lock?.release();
		};
	});

	// Landscape (phone held sideways): Vote/Party/Chat are sheets over the game. The sheet keeps showing the
	// last of them while it slides out, and a tap outside the sheet and the rail closes it.
	const LANDSCAPE = '(orientation: landscape) and (max-height: 520px)';
	let landscape = $state(typeof matchMedia !== 'undefined' && matchMedia(LANDSCAPE).matches);
	$effect(() => {
		const q = matchMedia(LANDSCAPE);
		const onChange = () => (landscape = q.matches);
		q.addEventListener('change', onChange);
		return () => q.removeEventListener('change', onChange);
	});
	// Landscape big screen: the screen fills the height and the controls float over it, translucent.
	let bigScreen = $state(read('pppp.bigScreen') === 'on');
	function read(key: string) {
		try {
			return localStorage.getItem(key);
		} catch {
			return null;
		}
	}
	function toggleBigScreen() {
		bigScreen = !bigScreen;
		try {
			localStorage.setItem('pppp.bigScreen', bigScreen ? 'on' : 'off');
		} catch {
			/* private mode: not remembered */
		}
	}
	let sheetTab = $state<Tab>('vote');
	$effect(() => {
		if (tab !== 'play') sheetTab = tab;
	});
	const shown = $derived(landscape && tab === 'play' ? sheetTab : tab); // what the side panels display
	function closeSheetOnOutsideTap(e: PointerEvent) {
		if (!landscape || tab === 'play') return;
		const t = e.target as Element;
		if (t.closest('.side, .chat, .tabs, header, .shells')) return; // sheet, rail, corners, colour picker
		e.preventDefault();
		e.stopPropagation(); // the tap only closes the sheet; it must not also press a game button
		swallowClick = true; // A/B/Start/Select act on the click that follows
		setTimeout(() => (swallowClick = false), 500); // no click came (a drag): don't eat the next real one
		tab = 'play';
	}
	let swallowClick = false;
	function swallowClosingClick(e: MouseEvent) {
		if (!swallowClick) return;
		swallowClick = false;
		e.preventDefault();
		e.stopPropagation();
	}

	let unreadChat = $state(0);
	let newInParty = $state(false);
	let now = $state(Date.now());
	$effect(() => {
		const t = setInterval(() => (now = Date.now()), 1000);
		return () => clearInterval(t);
	});
	const badges = $derived<Record<Tab, number | boolean>>({
		play: false,
		vote: tab !== 'vote' && !!game.tally && game.tally.endsAt > now,
		party: newInParty,
		chat: unreadChat
	});
	$effect(() => {
		if (tab === 'chat') unreadChat = 0;
		if (tab === 'party') newInParty = false;
	});
	$effect(() => {
		const offs = [
			on('CHAT_MESSAGE', () => {
				if (tab !== 'chat') unreadChat++;
			}),
			on('GAME_EVENT', () => {
				if (tab !== 'party') newInParty = true;
			})
		];
		return () => offs.forEach((off) => off());
	});

	// The screen frame sizes itself to the height left over; on desktop the controller matches its width.
	let screenSlot = $state<HTMLDivElement>();
	let frameWidth = $state(0);
	$effect(() => {
		const frame = screenSlot?.querySelector('.bezel');
		if (!frame) return;
		const watch = new ResizeObserver(([e]) => (frameWidth = Math.round(e.borderBoxSize[0].inlineSize)));
		watch.observe(frame);
		return () => watch.disconnect();
	});

	const me = $derived(session.me);
	let loadFailed = $state(false);
	const modeLabel = $derived(game.mode === 'DEMOCRACY' ? 'Democracy' : 'Anarchy');

	$effect(() => {
		loadMe()
			.then((m) => {
				if (!m) goto('/');
			})
			.catch(() => (loadFailed = true));
	});

	// Only once signed in: /state plus the live events that keep it current.
	$effect(() => {
		if (!me) return;
		api<GameState>('GET', '/state').then((s) => (game = s));
		const offs = [
			on('CONSOLE_CHANGED', (s) => {
				game.power = s.power;
				game.paused = s.paused;
				game.cartridgeId = s.cartridgeId;
			}),
			on('MODE_CHANGED', (e) => {
				game.mode = e.mode;
				game.modeVotes = { ANARCHY: 0, DEMOCRACY: 0 }; // the backend clears mode votes on a switch
				game.tally = null;
			}),
			on('MODE_VOTES', (v) => (game.modeVotes = v)),
			on('VOTE_TALLY', (t) => (game.tally = t)),
			on('INPUT_EXECUTED', (e) => {
				if (e.window && e.window === game.tally?.window) game.tally = null;
			})
		];
		return () => offs.forEach((off) => off());
	});
</script>

<svelte:window onpointerdowncapture={closeSheetOnOutsideTap} onclickcapture={swallowClosingClick} />

{#if me}
	<div class="page" class:big={bigScreen} style:--plate={currentShell.value.shell}>
		<header>
			<div class="brand">
				<img class="logo" src={banner} alt="Pibs&amp;PibettesPlayPokemon" width="924" height="111" />
				<img class="short" src={ppppLogo} alt="PPPP" width="240" height="103" />
				<span class="pill {game.mode.toLowerCase()}"><span class="pill-label">Mode: </span>{modeLabel}</span>
			</div>
			<div class="user">
				<Presence />
				<UserMenu {me} />
			</div>
		</header>

		<div class="stage">
			<aside class="dex" aria-label="Pokédex, first half"><PokedexBanner from={1} to={76} /></aside>
		<main>
			<section class="game" aria-label="Game" style:--frame-w={frameWidth ? `${frameWidth}px` : null}>
				<div class="screen-slot" bind:this={screenSlot}>
					<GameScreen power={game.power} paused={game.paused}>
						{#snippet controls()}<span class="bezel-controls"><ChatOverlayToggle /><SoundControl /></span>{/snippet}
					</GameScreen>
					<GameToasts />
					<ChatOverlay hidden={tab === 'chat'} />
				</div>
				<nav class="tabs" aria-label="Panels">
					{#each TABS.filter((t) => !(landscape && t.id === 'play')) as t (t.id)}
						{@const badge = badges[t.id]}
						<button
							type="button"
							aria-pressed={tab === t.id}
							aria-label="{t.label}{badge ? `, ${typeof badge === 'number' ? `${badge} new` : 'new'}` : ''}"
							onclick={() => (tab = t.id)}
						>
							<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
								>{@html t.icon}</svg
							>
							<span>{t.label}</span>
							{#if badge}<span class="badge" aria-hidden="true">{typeof badge === 'number' ? (badge > 99 ? '99+' : badge) : ''}</span>{/if}
						</button>
					{/each}
					<button type="button" class="big-toggle" aria-pressed={bigScreen} aria-label="Big screen" title="Big screen" onclick={toggleBigScreen}>
						<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
							><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" /></svg
						>
					</button>
					<span class="rail-picker"><ShellPicker up /></span>
				</nav>
				<div class="pad" class:off={tab !== 'play'}>
					<Gamepad mode={game.mode} />
				</div>
			</section>
			<section class="side" class:off={tab !== 'vote' && tab !== 'party'} aria-label="Mode, votes and party">
				<!-- Phones: Vote and Party are separate tabs; desktop: one column (display: contents). -->
				<div class="voting" class:off={shown !== 'vote'}>
					<ModeCard mode={game.mode} bind:votes={game.modeVotes} />
					<!-- The flexible slot: the open vote in democracy (the queue is only votes then), the queue in anarchy. -->
					<div class="next">
						{#if game.mode === 'DEMOCRACY'}
							<VoteTally tally={game.tally}><LastPressed /></VoteTally>
						{:else}
							<UpNext />
						{/if}
					</div>
				</div>
				<div class="party" class:off={shown !== 'party'}>
					<GameStatus />
				</div>
			</section>
			<section class="chat" class:off={tab !== 'chat'} aria-label="Chat">
				<Chat />
			</section>
		</main>
			<aside class="dex" aria-label="Pokédex, second half"><PokedexBanner from={77} to={151} /></aside>
		</div>
	</div>
{:else if loadFailed}
	<section class="load-error" role="alert">
		<p>Couldn't load your session. The server may be restarting.</p>
		<p><a href="/" data-sveltekit-reload>Back to sign in</a> · <button type="button" onclick={() => location.reload()}>Try again</button></p>
	</section>
{/if}

<style>
	.load-error {
		max-width: 420px;
		margin: 96px auto;
		padding: 0 16px;
		text-align: center;
		color: var(--muted);
	}
	.load-error button {
		background: none;
		border: none;
		padding: 0;
		color: var(--accent-text);
		text-decoration: underline;
	}
	.page {
		min-height: 100vh;
	}
	header {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: 16px;
		padding: 16px 32px;
		border-bottom: 1px solid var(--border);
	}
	.brand,
	.user {
		display: flex;
		align-items: center;
		gap: 16px;
		flex-wrap: wrap;
	}
	.logo {
		display: block;
		height: 36px;
		width: auto;
	}
	/* Phones: the PPPP logo instead of the long banner. */
	.short {
		display: none;
		height: 32px;
		width: auto;
	}
	.pill {
		font-size: 13px;
		font-weight: 600;
		padding: 6px 10px;
		border-radius: 999px;
		background: var(--border);
	}
	.pill.anarchy {
		background: var(--anarchy);
		color: var(--ink);
	}
	.pill.democracy {
		background: var(--democracy);
		color: var(--ink);
	}
	main {
		display: flex;
		flex-wrap: wrap;
		gap: 24px;
		padding: 24px 32px;
		max-width: 1440px;
		margin: 0 auto;
		box-sizing: border-box;
		/* Columns stretch to the tallest one (the game), so chat fills the rest of the screen. */
		align-items: stretch;
	}
	.game {
		flex: 1 1 520px;
		max-width: 560px;
		display: flex;
		flex-direction: column;
		gap: 16px;
		min-width: 0;
	}
	.screen-slot {
		position: relative;
	}
	.bezel-controls {
		display: flex;
		align-items: center;
		gap: 4px;
	}
	.pad {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.side {
		flex: 1 1 320px;
		display: flex;
		flex-direction: column;
		gap: 16px;
		min-width: 0;
	}
	/* Up next takes the middle column's spare height, so the party box lines up with the controller. */
	.next {
		flex: 1 1 0;
		contain: size;
		min-height: 140px;
	}
	/* One card fills the slot: Up next (anarchy) or This vote with Last pressed inside (democracy). */
	.next > :global(*) {
		height: 100%;
		box-sizing: border-box;
	}
	.chat {
		flex: 1 1 340px;
		/* Its size never drives the row height: it is as tall as the game column. */
		contain: size;
		min-height: 480px;
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	.chat > :global(*) {
		flex: 1 1 auto;
		min-height: 0;
	}
	.tabs {
		display: none;
	}
	/* Landscape only (beats the phone tab-bar's ".tabs button" rule). */
	.tabs .big-toggle,
	.tabs .rail-picker {
		display: none;
	}
	/* Pokédex side banners only where the side margins are wide enough (see the wide media query). */
	.stage {
		display: contents;
	}
	.dex {
		display: none;
	}
	.voting,
	.party {
		display: contents;
	}

	/* Desktop: the page is exactly one screen tall and never scrolls. The game screen takes the height the
	   controller leaves; a column that still doesn't fit (very short windows) scrolls inside itself. */
	@media (min-width: 1100px) and (min-height: 600px) {
		.page {
			height: 100dvh;
			display: flex;
			flex-direction: column;
			overflow: hidden;
		}
		header {
			flex: none;
		}
		.stage {
			display: flex;
			flex: 1 1 0;
			min-height: 0;
		}
		main {
			flex: 1 1 auto;
			min-height: 0;
			width: 100%;
			flex-wrap: nowrap;
		}
		.game,
		.side {
			min-height: 0;
			overflow-y: auto;
		}
		.screen-slot {
			flex: 1 1 0;
			min-height: 200px;
		}
		.screen-slot :global(.fit) {
			container-type: size;
			/* Largest screen that fits: bezel padding 32px across, 76px of padding + LIVE strip down. */
			--bezel-width: min(100cqw, (100cqh - 76px) * 160 / 144 + 32px);
		}
		/* Every pixel of height goes to the screen: tighter page padding and controller. */
		main {
			padding-top: 16px;
			padding-bottom: 16px;
		}
		.pad :global(.shell) {
			padding: 16px 24px;
			gap: 16px;
		}
		.pad {
			width: var(--frame-w, 100%);
			min-width: min(380px, 100%); /* d-pad + A/B need this much on very short windows */
			max-width: 100%;
			align-self: center;
		}
		.chat {
			min-height: 0;
		}
	}
	/* Side margins of 200px+: the Pokédex as two banners, 1-76 left and 77-151 right. */
	@media (min-width: 1840px) and (min-height: 600px) {
		.dex {
			display: block;
			flex: 1 1 0;
			min-width: 0;
			padding: 16px 16px;
			box-sizing: border-box;
		}
		main {
			flex: 0 1 1440px;
		}
	}
	:global(.page :focus-visible) {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}

	@media (max-width: 760px) {
		/* One row: badge + mode on the left, online + account menu on the right. */
		header {
			padding: 8px 16px;
			gap: 8px;
			flex-wrap: nowrap;
		}
		.logo {
			display: none;
		}
		.short {
			display: block;
		}
		.brand,
		.user {
			gap: 8px;
			flex-wrap: nowrap;
		}
		.user {
			margin-left: auto;
		}
		.pill-label {
			display: none;
		}
		.pill {
			font-size: 12px;
			padding: 5px 9px;
		}
		/* App-like: the page is one screen tall. Screen on top, the active tab fills the middle and scrolls
		   inside itself, the tab bar sits at the bottom. */
		.page {
			height: 100dvh;
			display: flex;
			flex-direction: column;
			overflow: hidden;
		}
		header {
			flex: none;
		}
		main {
			flex: 1 1 0;
			min-height: 0;
			display: flex;
			flex-direction: column;
			flex-wrap: nowrap;
			/* Full width on every tab: the desktop centring (margin: auto) made the column, and the screen in
			   it, shrink to whatever the active tab contained. */
			width: 100%;
			max-width: none;
			margin: 0;
			padding: 8px 12px 0;
			gap: 8px;
		}
		.game {
			display: contents;
		}
		.screen-slot {
			order: 0;
			flex: none;
			height: min(calc((100vw - 24px - 32px) * 144 / 160 + 76px), 46dvh);
		}
		.screen-slot :global(.fit) {
			container-type: size;
			--bezel-width: min(100cqw, (100cqh - 76px) * 160 / 144 + 32px);
		}
		.pad,
		.side,
		.chat {
			order: 1;
			flex: 1 1 0;
			min-height: 0;
			max-width: none;
			overflow-y: auto;
		}
		/* Vote and Party are fixed, not scrolled: their content is sized to fit; only lists inside scroll. */
		.side {
			gap: 12px;
			display: flex;
			flex-direction: column;
			overflow: hidden;
		}
		.chat {
			overflow: hidden; /* the chat scrolls its own list; the message box stays at the bottom */
		}
		.voting {
			flex: 1 1 0;
			min-height: 0;
			display: flex;
			flex-direction: column;
			gap: 12px;
		}
		.voting .next {
			flex: 1 1 0;
			min-height: 0;
			height: auto;
		}
		.party {
			flex: 1 1 0;
			min-height: 0;
			display: flex;
			flex-direction: column;
			gap: 12px;
		}
		.off {
			display: none;
		}
		.tabs {
			order: 2;
			flex: none;
			display: flex;
			margin: 0 -12px;
			padding: 4px 4px calc(4px + env(safe-area-inset-bottom));
			border-top: 1px solid var(--border);
			background: var(--panel);
		}
		.tabs button {
			position: relative;
			flex: 1 1 0;
			display: flex;
			flex-direction: column;
			align-items: center;
			gap: 2px;
			padding: 6px 0;
			border: none;
			background: transparent;
			font-size: 11px;
			font-weight: 600;
			color: var(--muted);
		}
		/* The selected tab in the player's plate colour (pulled toward the text colour on light, like names). */
		.tabs button[aria-pressed='true'] {
			color: color-mix(in oklab, var(--plate, var(--accent-text)), var(--text) var(--name-dim));
		}
		.badge {
			position: absolute;
			top: 2px;
			left: calc(50% + 6px);
			min-width: 8px;
			height: 16px;
			padding: 0 4px;
			box-sizing: border-box;
			border-radius: 999px;
			background: var(--danger);
			color: #fff;
			font-size: 10px;
			line-height: 16px;
			text-align: center;
		}
		.badge:empty {
			width: 10px;
			min-width: 0;
			height: 10px;
			padding: 0;
			top: 4px;
		}
	}
	/* Phone held sideways: a Game Boy Advance. D-pad left, screen in the middle, A/B right, Select/Start under
	   the screen; no header or tabs. The pad's own boxes become grid items via display: contents. */
	@media (orientation: landscape) and (max-height: 520px) {
		/* One rule for alignment: the screen is centred, everything else is centred in the gutter beside it
		   (d-pad over the rail on the left, A/B over Select/Start on the right; the bottom pair share a centre line). */
		.page {
			--screen-w: calc((100dvh - 16px - 76px) * 160 / 144 + 32px);
			--gutter: max(0px, calc((100vw - var(--screen-w)) / 2));
			--bottom-row: 37px; /* centre line of the rail and Select/Start, from the bottom */
		}
		.page.big {
			--gutter: min(260px, 34vw); /* the screen fills everything; controls use the outer band */
		}
		.page {
			height: 100dvh;
			display: flex;
			flex-direction: column;
			overflow: hidden;
		}
		.dex,
		.off,
		.logo,
		.short {
			display: none;
		}
		/* Mode pill top-left, online + account menu top-right, floating over the corners. */
		header {
			position: fixed;
			inset: 8px max(12px, env(safe-area-inset-right)) auto max(12px, env(safe-area-inset-left));
			z-index: 32;
			padding: 0;
			border: none;
			background: none;
			pointer-events: none;
		}
		header > * {
			pointer-events: auto;
		}
		.pill-label {
			display: none;
		}
		.stage,
		.game,
		.pad,
		.pad.off {
			display: contents;
		}
		main {
			flex: 1 1 0;
			min-height: 0;
			max-width: none;
			display: grid;
			grid-template-columns: 1fr auto 1fr;
			grid-template-rows: 1fr;
			grid-template-areas: 'dpad screen ab';
			align-items: center;
			justify-items: center;
			gap: 6px 12px;
			padding: 8px max(20px, env(safe-area-inset-right)) 8px max(20px, env(safe-area-inset-left));
		}
		.screen-slot {
			grid-area: screen;
			/* Full height: Select/Start sit under A/B, not under the screen. */
			height: calc(100dvh - 16px);
			width: calc((100dvh - 16px - 76px) * 160 / 144 + 32px);
		}
		.screen-slot :global(.fit) {
			container-type: size;
			--bezel-width: min(100cqw, (100cqh - 76px) * 160 / 144 + 32px);
		}
		.pad :global(.shell),
		.pad :global(.row) {
			display: contents;
		}
		/* Controls pinned near the edges, a little below the middle, where thumbs rest. */
		.pad :global(.dpad),
		.pad :global(.ab) {
			position: fixed;
			z-index: 20;
			top: 50%;
			translate: -50% -50%;
		}
		.pad :global(.dpad) {
			left: calc(var(--gutter) / 2);
		}
		.pad :global(.ab) {
			left: calc(100vw - var(--gutter) / 2);
		}
		.pad :global(.meta) {
			position: fixed;
			z-index: 20;
			left: calc(100vw - var(--gutter) / 2);
			bottom: var(--bottom-row);
			translate: -50% 50%;
			gap: 8px;
		}
		.pad :global(.controls) {
			display: none;
		}
		/* The colour picker lives inside the rail pill here, not in the pad's corner. */
		.pad :global(.picker-corner) {
			display: none;
		}
		.tabs .rail-picker {
			display: flex;
			align-items: center;
			padding: 0 9px;
		}
		.tabs .big-toggle {
			display: flex;
		}
		.big-toggle[aria-pressed='true'] {
			color: color-mix(in oklab, var(--plate, var(--accent-text)), var(--text) var(--name-dim));
			background: var(--bg);
		}
		/* Big screen: the game stretched edge to edge (wider pixels, on purpose); the controls float over it. */
		.big .screen-slot {
			position: fixed;
			inset: 0;
			z-index: 1;
			width: auto;
			height: auto;
		}
		.big .screen-slot :global(.fit) {
			container-type: normal;
			height: 100%;
		}
		.big .screen-slot :global(.bezel) {
			width: 100%;
			height: 100%;
			padding: 0;
			gap: 0;
			border-radius: 0;
		}
		.big .screen-slot :global(.screen) {
			aspect-ratio: auto;
			flex: 1 1 0;
			min-height: 0;
			border-radius: 0;
		}
		.big .screen-slot :global(.strip) {
			position: fixed;
			top: 10px;
			left: 50%;
			translate: -50% 0;
			z-index: 33;
			min-height: 0;
			padding: 2px 12px;
			border-radius: 999px;
			background: rgb(0 0 0 / 0.5);
		}
		.big .pad :global(.dpad),
		.big .pad :global(.ab),
		.big .pad :global(.meta) {
			position: fixed;
			z-index: 20;
			opacity: 0.55;
		}

		/* Other tabs: an icon rail in the bottom-left corner; Vote, Party and Chat open as a sheet on the right,
		   with the game still visible. Play closes it. */
		.tabs {
			position: fixed;
			left: max(8px, calc(var(--gutter) / 2));
			bottom: var(--bottom-row);
			translate: max(-50%, calc(8px - var(--gutter) / 2)) 50%;
			z-index: 31;
			display: flex;
			gap: 2px;
			padding: 4px;
			border: 1px solid var(--border);
			border-radius: 999px;
			background: var(--panel);
			box-shadow: 0 4px 16px rgb(0 0 0 / 0.3);
		}
		.tabs button {
			position: relative;
			width: 40px;
			height: 40px;
			padding: 0;
			display: flex;
			align-items: center;
			justify-content: center;
			border: none;
			border-radius: 50%;
			background: transparent;
			color: var(--muted);
		}
		.tabs button[aria-pressed='true'] {
			color: color-mix(in oklab, var(--plate, var(--accent-text)), var(--text) var(--name-dim));
			background: var(--bg);
		}
		.tabs button span:not(.badge) {
			position: absolute;
			width: 1px;
			height: 1px;
			overflow: hidden;
			clip: rect(0 0 0 0);
		}
		.badge {
			position: absolute;
			top: 2px;
			right: 2px;
			min-width: 8px;
			height: 14px;
			padding: 0 3px;
			box-sizing: border-box;
			border-radius: 999px;
			background: var(--danger);
			color: #fff;
			font-size: 9px;
			line-height: 14px;
		}
		.badge:empty {
			width: 8px;
			height: 8px;
			padding: 0;
		}
		.side,
		.chat {
			position: fixed;
			top: 8px; /* over the online/account corner, which is fine while a sheet is open */
			right: max(8px, env(safe-area-inset-right));
			bottom: 8px;
			z-index: 33;
			width: min(420px, 52vw);
			box-sizing: border-box;
			padding: 8px;
			border: 1px solid var(--border);
			border-radius: 14px;
			background: var(--bg);
			box-shadow: 0 8px 32px rgb(0 0 0 / 0.4);
			overflow-y: auto;
		}
		.chat {
			display: flex;
			flex-direction: column;
			overflow: hidden;
			min-height: 0; /* the desktop minimum (480px) is taller than the sheet: it hid the message box */
		}
		/* Sheets are fixed, not scrolled, like the portrait tabs: only lists inside scroll. */
		.side {
			overflow: hidden;
			gap: 10px;
		}
		.voting,
		.party {
			flex: 1 1 0;
			min-height: 0;
			display: flex;
			flex-direction: column;
			gap: 10px;
		}
		.voting.off,
		.party.off {
			display: none;
		}
		/* Sheets slide in from the right and back out (kept rendered, moved off-screen when closed). */
		.side,
		.side.off,
		.chat,
		.chat.off {
			display: flex;
			flex-direction: column;
			transition:
				translate 0.25s ease,
				visibility 0.25s;
		}
		.side.off,
		.chat.off {
			translate: calc(100% + 24px) 0;
			visibility: hidden;
		}
		.voting .next {
			flex: 1 1 0;
			min-height: 0;
			height: auto;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.side,
		.chat {
			transition: none;
		}
	}
</style>
