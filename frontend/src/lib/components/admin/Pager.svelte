<!-- Page buttons for a client-side list. Parent shows items.slice(page * perPage, (page + 1) * perPage). -->
<script lang="ts">
	let {
		page = $bindable(0),
		total,
		perPage = 10,
		label,
		noun,
		prev = 'Previous',
		next = 'Next'
	}: { page: number; total: number; perPage?: number; label: string; noun: string; prev?: string; next?: string } = $props();

	const pages = $derived(Math.max(1, Math.ceil(total / perPage)));
	// The list can shrink under us (delete, autosave pruning): stay on a real page.
	$effect(() => {
		if (page > pages - 1) page = pages - 1;
	});
</script>

{#if pages > 1}
	<nav class="row pager" aria-label={label}>
		<button onclick={() => page--} disabled={page === 0}>{prev}</button>
		<span class="muted" aria-live="polite">Page {page + 1} of {pages} · {total} {noun}</span>
		<button onclick={() => page++} disabled={page >= pages - 1}>{next}</button>
	</nav>
{/if}
