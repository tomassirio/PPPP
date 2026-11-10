<!-- A player: round photo (initials when there is none) and the short name. compact: photo only, the name as a
     tooltip and for screen readers (narrow lists). -->
<script lang="ts">
	import { shortUser } from '#lib/play.ts';
	import { needPicture, presses } from '#lib/presses.svelte.ts';

	let { user, compact = false }: { user: string; compact?: boolean } = $props();
	let broken = $state(false);
	const picture = $derived(presses.pictures[user]);
	$effect(() => needPicture(user));
</script>

<span class="who" title={compact ? shortUser(user) : undefined}>
	{#if picture && !broken}
		<img class="pic" src={picture} alt="" referrerpolicy="no-referrer" onerror={() => (broken = true)} />
	{:else}
		<span class="pic initials" aria-hidden="true">{shortUser(user).slice(0, 2).toUpperCase()}</span>
	{/if}
	<span class="name" class:sr-only={compact}>{shortUser(user)}</span>
</span>

<style>
	.who {
		position: relative; /* anchors the screen-reader-only name inside scrolled lists */
		display: flex;
		align-items: center;
		gap: 6px;
		min-width: 0;
	}
	.pic {
		flex: none;
		width: 18px;
		height: 18px;
		border-radius: 50%;
		object-fit: cover;
	}
	.initials {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		background: #2a9d8f;
		color: #fff;
		font-size: 8px;
		font-weight: 700;
	}
	/* Never cut off: a long name wraps onto a second line instead. */
	.name {
		min-width: 0;
		font-size: 13px;
		line-height: 1.25;
		color: var(--muted);
		overflow-wrap: anywhere;
	}
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
		white-space: nowrap;
	}
</style>
