<!-- Sign-in page (design/Main.dc.html, design/LoginMobile.dc.html under 600px). -->
<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { loadMe } from '#lib/session.svelte.js';
	import TitleScene from '#lib/components/TitleScene.svelte';
	import banner from '#lib/assets/banner.webp';

	const denied = $derived(page.url.searchParams.get('error') === 'denied');

	// Backend down or other error: stay on the sign-in page, the button still works.
	onMount(() => {
		loadMe()
			.then((me) => me && goto('/play'))
			.catch(() => {});
	});
</script>

<main>
	<h1><img src={banner} alt="Pibs&amp;PibettesPlayPokemon" width="924" height="111" /></h1>
	<p class="tagline">One Game Boy. Eighteen trainers.<span class="wide">&nbsp;Every button counts.</span></p>

	<TitleScene />

	<p class="blink" aria-hidden="true">SIGN IN TO START</p>

	<section class="card" aria-labelledby="signin-heading">
		<h2 id="signin-heading">Sign in to play</h2>
		<p class="muted">Invite only. Use the Google account your host added to the trainer list.</p>
		{#if denied}
			<p class="error" role="alert">
				That Google account isn't on the trainer list. Ask your host to add you.
			</p>
		{/if}
		<a class="google" href="/oauth2/authorization/google" data-sveltekit-reload>
			<svg
				width="20"
				height="20"
				viewBox="0 0 24 24"
				fill="none"
				stroke="#1f1f1f"
				stroke-width="2"
				stroke-linecap="round"
				stroke-linejoin="round"
				aria-hidden="true"
				><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"></path><path d="M10 17l5-5-5-5"
				></path><path d="M15 12H3"></path></svg
			>
			<span>Sign in with Google</span>
		</a>
		<p class="muted small">Not on the list? Ask your host to add your email.</p>
	</section>

	<p class="small footer">Self-hosted · private to your group</p>
</main>

<style>
	main {
		min-height: 100vh;
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 56px 24px;
		gap: 28px;
	}
	h1 {
		margin: 0;
		width: min(100%, 720px);
	}
	h1 img {
		display: block;
		width: 100%;
		height: auto;
	}
	p {
		margin: 0;
	}
	.tagline {
		font-size: 18px;
		color: var(--muted);
		text-align: center;
	}
	.blink {
		font-family: var(--pixel-font);
		font-size: 12px;
		animation: blink 1.2s steps(2, end) infinite;
	}
	@keyframes blink {
		50% {
			opacity: 0;
		}
	}
	.card {
		width: 100%;
		max-width: 420px;
		box-sizing: border-box;
		background: var(--panel);
		border: 1px solid var(--border);
		border-radius: 12px;
		padding: 28px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	h2 {
		margin: 0;
		font-size: 20px;
		font-weight: 600;
	}
	.muted {
		font-size: 15px;
		line-height: 22px;
		color: var(--muted);
	}
	.small {
		font-size: 13px;
		line-height: 20px;
		color: var(--muted);
	}
	.error {
		font-size: 15px;
		line-height: 22px;
		padding: 12px 14px;
		border: 1px solid var(--danger);
		border-left-width: 4px;
		border-radius: 8px;
		background: rgba(231, 111, 81, 0.12);
	}
	.google {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 12px;
		height: 52px;
		border-radius: 8px;
		background: #ffffff;
		color: #1f1f1f;
		border: 1px solid #dadce0;
		font-size: 16px;
		font-weight: 600;
		text-decoration: none;
	}
	.google:hover {
		color: #1f1f1f;
		background: #f1f3f4;
	}
	.google:focus-visible {
		outline: 3px solid var(--accent);
		outline-offset: 3px;
	}

	@media (max-width: 600px) {
		main {
			padding: 40px 16px 24px;
			gap: 20px;
		}
		.tagline {
			font-size: 15px;
		}
		.wide {
			display: none;
		}
		.blink {
			font-size: 10px;
		}
		.footer {
			display: none;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.blink {
			animation: none;
		}
	}
</style>
