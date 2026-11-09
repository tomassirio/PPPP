<!-- Cycles Auto -> Light -> Dark. Auto follows the OS setting. -->
<script lang="ts">
	import { setTheme, theme, type Theme } from '#lib/theme.svelte.ts';

	const order: Theme[] = ['auto', 'light', 'dark'];
	const label = { auto: 'Auto', light: 'Light', dark: 'Dark' };
	const next = $derived(order[(order.indexOf(theme.value) + 1) % order.length]);
</script>

<button
	type="button"
	class="theme"
	onclick={() => setTheme(next)}
	aria-label="Theme: {label[theme.value]}. Switch to {label[next]}"
	title="Theme: {label[theme.value]}"
>
	<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
		{#if theme.value === 'light'}
			<circle cx="12" cy="12" r="4" />
			<path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
		{:else if theme.value === 'dark'}
			<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" />
		{:else}
			<circle cx="12" cy="12" r="8" />
			<path d="M12 4a8 8 0 0 1 0 16z" fill="currentColor" />
		{/if}
	</svg>
	<span class="text">{label[theme.value]}</span>
</button>

<style>
	.theme {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		height: 32px;
		padding: 0 10px;
		border: 1px solid var(--border);
		border-radius: 999px;
		background: transparent;
		color: var(--text);
		font-size: 13px;
	}
	.theme:hover {
		border-color: var(--border-strong);
	}
	.theme:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	@media (max-width: 760px) {
		.text {
			display: none;
		}
		.theme {
			width: 32px;
			padding: 0;
			justify-content: center;
		}
	}
</style>
