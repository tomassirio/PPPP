<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '#lib/api.ts';
	import { act } from './admin.svelte';

	type Input = {
		mode: 'ANARCHY' | 'DEMOCRACY';
		democracyWindowSeconds: number;
		enterDemocracyPercent: number;
		leaveDemocracyPercent: number;
		holdFrames: number;
		releaseFrames: number;
		maxQueueLength: number;
		chatCommands: boolean;
	};
	type RateLimit = { inputsPerSecond: number };
	type Autosave = { enabled: boolean; cron: string; keep: number };

	let input = $state<Input | null>(null);
	let rate = $state<RateLimit | null>(null);
	let autosave = $state<Autosave | null>(null);

	// Ranges mirror backend Settings.Input validation.
	const numbers: [Exclude<keyof Input, 'mode' | 'chatCommands'>, string, number, number][] = [
		['democracyWindowSeconds', 'Democracy window (s)', 1, 60],
		['enterDemocracyPercent', 'Enter democracy at (%)', 1, 100],
		['leaveDemocracyPercent', 'Leave democracy below (%)', 1, 100],
		['holdFrames', 'Hold frames', 1, 60],
		['releaseFrames', 'Release frames', 1, 60],
		['maxQueueLength', 'Max queue length', 1, 500]
	];

	const get = <T,>(path: string) => act(`Load ${path} settings`, () => api<T>('GET', `/admin/settings/${path}`), undefined, true);

	onMount(async () => {
		const [i, r, a] = await Promise.all([get<Input>('input'), get<RateLimit>('rate-limit'), get<Autosave>('autosave')]);
		input = i ?? null;
		rate = r ?? null;
		autosave = a ?? null;
	});

	function save(path: string, body: unknown) {
		return (e: SubmitEvent) => {
			e.preventDefault();
			act(`Save ${path} settings`, () => api('PUT', `/admin/settings/${path}`, body));
		};
	}
</script>

<section class="panel" aria-labelledby="settings-h">
	<h2 id="settings-h" class="pixel">Settings</h2>

	{#if input}
		<form onsubmit={save('input', input)}>
			<h3>Input</h3>
			<div class="grid">
				<label>
					Mode
					<select bind:value={input.mode}>
						<option value="ANARCHY">Anarchy</option>
						<option value="DEMOCRACY">Democracy</option>
					</select>
				</label>
				{#each numbers as [key, label, min, max] (key)}
					<label>
						{label}
						<input type="number" {min} {max} required bind:value={input[key]} />
					</label>
				{/each}
				<label class="check"><input type="checkbox" bind:checked={input.chatCommands} /> Chat commands</label>
			</div>
			<button class="primary">Save input</button>
		</form>
	{/if}

	{#if rate}
		<form onsubmit={save('rate-limit', rate)}>
			<h3>Rate limit</h3>
			<label>
				Inputs per second per player
				<input type="number" min="1" max="20" required bind:value={rate.inputsPerSecond} />
			</label>
			<button class="primary">Save rate limit</button>
		</form>
	{/if}

	{#if autosave}
		<form onsubmit={save('autosave', autosave)}>
			<h3>Autosave</h3>
			<div class="grid">
				<label class="check"><input type="checkbox" bind:checked={autosave.enabled} /> Enabled</label>
				<label>
					Quartz cron
					<input type="text" required spellcheck="false" bind:value={autosave.cron} placeholder="0 0/30 * * * ?" />
				</label>
				<label>
					Keep (newest)
					<input type="number" min="1" max="1000" required bind:value={autosave.keep} />
				</label>
			</div>
			<button class="primary">Save autosave</button>
		</form>
	{/if}
</section>
