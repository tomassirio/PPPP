<!-- Scheduled start (GET/PUT /admin/settings/launch): pick a local date and time; players see a countdown on the
     screen while the console is off, and it powers itself on at that moment. -->
<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { countdown } from '#lib/play.ts';
	import { act, notice } from './admin.svelte';

	type Launch = { enabled: boolean; at: string | null };

	let enabled = $state(false);
	let local = $state(''); // <input type="datetime-local"> value, in the browser's time zone
	let current = $state<Launch | null>(null);
	let now = $state(Date.now());

	// ISO instant <-> "YYYY-MM-DDTHH:mm" in local time.
	const toLocal = (iso: string) => {
		const d = new Date(iso);
		return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
	};
	const fromLocal = (v: string) => new Date(v).toISOString();

	function show(l: Launch) {
		current = l;
		enabled = l.enabled;
		if (l.at) local = toLocal(l.at);
	}

	onMount(() => {
		act('Load scheduled start', () => api<Launch>('GET', '/admin/settings/launch'), undefined, true).then((l) => l && show(l));
		const t = setInterval(() => (now = Date.now()), 1000);
		// Fired (or changed by another admin): follow it.
		const off = on('LAUNCH', (e: { at: string | null }) => {
			current = { enabled: e.at !== null, at: e.at ?? current?.at ?? null };
			enabled = current.enabled;
		});
		return () => {
			clearInterval(t);
			off();
		};
	});

	async function save(e: SubmitEvent) {
		e.preventDefault();
		if (enabled && !local) {
			notice.text = 'Pick a date and time first';
			notice.error = true;
			return;
		}
		const body: Launch = { enabled, at: local ? fromLocal(local) : null };
		const saved = await act('Save scheduled start', () => api<Launch>('PUT', '/admin/settings/launch', body));
		if (saved) show(saved);
	}

	const live = $derived(current?.enabled && current.at ? Date.parse(current.at) : null);
</script>

<section class="panel" aria-labelledby="launch-h">
	<h2 id="launch-h" class="pixel">Scheduled start</h2>
	<p class="hint">
		While the console is off, players see a countdown on the screen. At the set time the Game Boy powers itself on.
	</p>
	<form onsubmit={save}>
		<label class="check"><input type="checkbox" bind:checked={enabled} /> Countdown enabled</label>
		<label>
			Powers on at (your local time)
			<input type="datetime-local" bind:value={local} required={enabled} />
		</label>
		<button class="primary">Save scheduled start</button>
	</form>
	<p class="muted" role="status">
		{#if live !== null}
			Starts in <b>{countdown(live - now)}</b> ({new Date(live).toLocaleString()}).
		{:else}
			No countdown set.
		{/if}
	</p>
</section>

<style>
	form {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	label:not(.check) {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.hint {
		margin: 0;
	}
	p {
		margin: 0;
	}
</style>
