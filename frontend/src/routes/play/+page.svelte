<!-- /play: game screen, pad, mode/votes/queue and chat. Desktop = columns, phone = screen + Play/Vote/Chat tabs. -->
<script lang="ts">
	import { goto } from '$app/navigation';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { isAdmin, loadMe, logout, session } from '#lib/session.svelte.ts';
	import type { GameState } from '#lib/play.ts';
	import Chat from '#lib/components/Chat.svelte';
	import GameScreen from '#lib/components/GameScreen.svelte';
	import GameStatus from '#lib/components/GameStatus.svelte';
	import Gamepad from '#lib/components/Gamepad.svelte';
	import ModeCard from '#lib/components/ModeCard.svelte';
	import SoundControl from '#lib/components/SoundControl.svelte';
	import UpNext from '#lib/components/UpNext.svelte';
	import VoteTally from '#lib/components/VoteTally.svelte';
	import banner from '#lib/assets/banner.webp';

	let game = $state<GameState>({
		power: 'OFF',
		cartridgeId: null,
		paused: false,
		mode: 'ANARCHY',
		queueDepth: 0,
		tally: null,
		modeVotes: { ANARCHY: 0, DEMOCRACY: 0 }
	});
	let tab = $state<'play' | 'vote' | 'chat'>('play');

	const me = $derived(session.me);
	let loadFailed = $state(false);
	let pictureFailed = $state(false);
	const initials = $derived(
		(me?.name ?? '')
			.split(/[\s@.]+/)
			.filter(Boolean)
			.slice(0, 2)
			.map((w) => w[0].toUpperCase())
			.join('')
	);
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

{#if me}
	<div class="page">
		<header>
			<div class="brand">
				<img class="logo" src={banner} alt="Pibs&amp;PibettesPlayPokemon" width="924" height="111" />
				<span class="pixel short">pppp</span>
				<span class="pill {game.mode.toLowerCase()}">Mode: {modeLabel}</span>
			</div>
			<div class="user">
				{#if isAdmin(me)}<a href="/admin">Admin</a>{/if}
				{#if me.picture && !pictureFailed}
					<!-- Google photo URLs reject requests that carry a Referer. -->
					<img class="avatar" src={me.picture} alt="Signed in as {me.name}" title={me.name} referrerpolicy="no-referrer" onerror={() => (pictureFailed = true)} />
				{:else}
					<span class="avatar" title={me.name} aria-label="Signed in as {me.name}">{initials}</span>
				{/if}
				<button type="button" class="link" onclick={logout}>Sign out</button>
			</div>
		</header>

		<main>
			<section class="game" aria-label="Game">
				<div class="screen-slot">
					<GameScreen power={game.power} paused={game.paused}>
						{#snippet controls()}<SoundControl />{/snippet}
					</GameScreen>
				</div>
				<div class="tabs" role="group" aria-label="Panels">
					{#each ['play', 'vote', 'chat'] as const as t (t)}
						<button type="button" aria-pressed={tab === t} onclick={() => (tab = t)}
							>{t[0].toUpperCase() + t.slice(1)}</button
						>
					{/each}
				</div>
				<div class="pad" class:off={tab !== 'play'}>
					<Gamepad mode={game.mode} />
				</div>
			</section>
			<section class="side" class:off={tab !== 'vote'} aria-label="Mode and votes">
				<ModeCard mode={game.mode} bind:votes={game.modeVotes} />
				{#if game.mode === 'DEMOCRACY'}<VoteTally tally={game.tally} />{/if}
				<div class="next"><UpNext /></div>
				<GameStatus />
			</section>
			<section class="chat" class:off={tab !== 'chat'} aria-label="Chat">
				<Chat />
			</section>
		</main>
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
		color: var(--accent);
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
	.short {
		display: none;
		font-size: 11px;
		color: var(--accent);
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
	img.avatar {
		object-fit: cover;
	}
	.avatar {
		width: 36px;
		height: 36px;
		border-radius: 50%;
		background: #2a9d8f;
		color: #fff;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 13px;
		font-weight: 700;
	}
	.user a,
	.link {
		font-size: 14px;
	}
	.link {
		background: none;
		border: none;
		padding: 0;
		color: var(--accent);
		text-decoration: underline;
	}
	.link:hover {
		color: var(--accent-hover);
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
		main {
			flex: 1 1 0;
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
			flex: 1 1 0;
			min-height: 0;
			aspect-ratio: auto;
		}
		.chat {
			min-height: 0;
		}
	}
	:global(.page :focus-visible) {
		outline: 3px solid var(--accent);
		outline-offset: 2px;
	}

	@media (max-width: 760px) {
		header {
			padding: 8px 16px;
			gap: 8px;
		}
		.logo {
			display: none;
		}
		.short {
			display: inline;
		}
		.brand,
		.user {
			gap: 10px;
		}
		.pill {
			font-size: 12px;
			padding: 5px 9px;
		}
		.avatar {
			width: 32px;
			height: 32px;
			font-size: 12px;
		}
		main {
			padding: 12px 16px 24px;
			gap: 16px;
		}
		.game,
		.side,
		.chat {
			flex: 1 1 100%;
			max-width: none;
		}
		.next {
			flex: none;
			height: 260px;
		}
		.chat {
			height: 70vh;
		}
		.off {
			display: none;
		}
		.tabs {
			display: flex;
			padding: 0 8px;
		}
		.tabs button {
			flex: 1 1 0;
			height: 44px;
			border: none;
			background: transparent;
			font-size: 14px;
			font-weight: 600;
			color: var(--muted);
			box-shadow: inset 0 -1px 0 var(--border);
		}
		.tabs button[aria-pressed='true'] {
			color: var(--accent);
			box-shadow: inset 0 -3px 0 var(--accent);
		}
	}
</style>
