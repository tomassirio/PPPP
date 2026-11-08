<!-- /play: game screen, pad, mode/votes/queue and chat. Desktop = columns, phone = screen + Play/Vote/Chat tabs. -->
<script lang="ts">
	import { goto } from '$app/navigation';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { isAdmin, loadMe, logout, session } from '#lib/session.svelte.ts';
	import type { GameState } from '#lib/play.ts';
	import Chat from '#lib/components/Chat.svelte';
	import GameScreen from '#lib/components/GameScreen.svelte';
	import Gamepad from '#lib/components/Gamepad.svelte';
	import ModeCard from '#lib/components/ModeCard.svelte';
	import UpNext from '#lib/components/UpNext.svelte';
	import VoteTally from '#lib/components/VoteTally.svelte';

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
		loadMe().then((m) => {
			if (!m) goto('/');
		});
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
				<span class="pixel logo"
					><span class="long">Pibs&amp;PibettesPlayPokemon</span><span class="short">pppp</span></span
				>
				<span class="pill">Mode: {modeLabel}</span>
			</div>
			<div class="user">
				{#if isAdmin(me)}<a href="/admin">Admin</a>{/if}
				<span class="avatar" title={me.name} aria-label="Signed in as {me.name}">{initials}</span>
				<button type="button" class="link" onclick={logout}>Sign out</button>
			</div>
		</header>

		<main>
			<section class="game" aria-label="Game">
				<GameScreen power={game.power} paused={game.paused} />
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
				<UpNext />
			</section>
			<section class="chat" class:off={tab !== 'chat'} aria-label="Chat">
				<Chat />
			</section>
		</main>
	</div>
{/if}

<style>
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
		font-size: 16px;
		color: var(--accent);
	}
	.short {
		display: none;
	}
	.pill {
		font-size: 13px;
		font-weight: 600;
		padding: 6px 10px;
		border-radius: 999px;
		background: var(--border);
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
		align-items: flex-start;
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
	.chat {
		flex: 1 1 340px;
		height: 640px;
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
	:global(.page :focus-visible) {
		outline: 3px solid var(--accent);
		outline-offset: 2px;
	}

	@media (max-width: 760px) {
		header {
			padding: 8px 16px;
			gap: 8px;
		}
		.long {
			display: none;
		}
		.short {
			display: inline;
			font-size: 11px;
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
