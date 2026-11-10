<!-- The controller colour picker: a swatch opening a grid of shells. up: the grid opens upward (landscape rail). -->
<script lang="ts">
	import { SHELLS } from '#lib/shells.ts';
	import { currentShell, pickShell } from '#lib/shell.svelte.ts';

	let { up = false }: { up?: boolean } = $props();
	const shell = $derived(currentShell.value);
	let picker = $state<HTMLDetailsElement>();
	const uid = $props.id(); // two pickers on the page (pad corner, landscape rail): separate radio groups

	function onclick(e: MouseEvent) {
		if (picker?.open && !picker.contains(e.target as Node)) picker.open = false;
	}
	function onkeydown(e: KeyboardEvent) {
		if (e.key === 'Escape' && picker?.open) picker.open = false;
	}
</script>

<svelte:window {onclick} {onkeydown} />

<details class="shells" class:up bind:this={picker}>
	<summary aria-label="Controller colour: {shell.name}" title="Controller colour: {shell.name}">
		<span class="swatch" style:background={shell.shell}></span>
	</summary>
	<fieldset>
		<legend>Controller colour</legend>
		<div class="grid">
			{#each SHELLS as s (s.id)}
				<label title={s.name}>
					<input
						type="radio"
						name="shell-{uid}"
						value={s.id}
						checked={s.id === shell.id}
						onchange={() => {
							pickShell(s.id);
							if (picker) picker.open = false;
						}}
					/>
					<span class="swatch" style:background={s.shell}><span class="dot" style:background={s.ab}></span></span>
					<span class="sr-only">{s.name}</span>
				</label>
			{/each}
		</div>
		<p class="current">{shell.name}</p>
	</fieldset>
</details>

<style>
	.shells {
		position: relative;
		z-index: 5;
	}
	.shells summary {
		list-style: none;
		cursor: pointer;
		border-radius: 50%;
	}
	.shells summary::-webkit-details-marker {
		display: none;
	}
	.shells summary:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.swatch {
		display: block;
		position: relative;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		box-shadow:
			inset 0 0 0 1px rgb(0 0 0 / 0.25),
			0 0 0 2px rgb(255 255 255 / 0.6);
	}
	.dot {
		position: absolute;
		right: 3px;
		bottom: 3px;
		width: 7px;
		height: 7px;
		border-radius: 50%;
	}
	fieldset {
		position: absolute;
		top: 30px;
		right: 0;
		margin: 0;
		padding: 12px;
		border: 1px solid var(--border);
		border-radius: 10px;
		background: var(--panel);
		box-shadow: 0 8px 24px rgb(0 0 0 / 0.35);
	}
	legend {
		float: left;
		width: 100%;
		margin-bottom: 8px;
		font-size: 11px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.grid {
		clear: both;
		display: grid;
		grid-template-columns: repeat(5, 30px);
		gap: 8px;
	}
	.grid label {
		position: relative;
		cursor: pointer;
	}
	.grid input {
		position: absolute;
		opacity: 0;
		inset: 0;
		margin: 0;
	}
	.grid .swatch {
		width: 30px;
		height: 30px;
	}
	.grid input:checked + .swatch {
		box-shadow:
			inset 0 0 0 1px rgb(0 0 0 / 0.25),
			0 0 0 3px var(--accent-text);
	}
	.grid input:focus-visible + .swatch {
		outline: 3px solid var(--accent-text);
		outline-offset: 3px;
	}
	.current {
		margin: 10px 0 0;
		font-size: 12px;
		color: var(--text);
	}
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
	}
	.shells.up fieldset {
		top: auto;
		bottom: 34px;
		right: auto;
		left: 0;
	}
</style>
