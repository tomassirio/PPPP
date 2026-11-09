<!-- CONTRACT: self-contained chat panel, no props. Fills its container's height; the play page decides placement. -->
<script lang="ts">
	import { onMount, tick } from 'svelte';
	import { api, ApiError } from '../api';
	import { on } from '../events';
	import { session, isAdmin } from '../session.svelte';
	import { merge, modeParts, remove, nameColor, type ChatMessage } from '../chat/messages';

	const PAGE = 50;
	const MAX = 300;

	let messages = $state<ChatMessage[]>([]);
	let hasMore = $state(true);
	let loadingOlder = $state(false);
	let draft = $state('');
	let sending = $state(false);
	let error = $state('');
	let note = $state('');
	let muteMinutes = $state(60);
	let list: HTMLUListElement;

	const admin = $derived(isAdmin(session.me));

	// Per-viewer preference: hide SYSTEM lines so only people chatting show.
	let showSystem = $state(read() !== '0');
	function read() {
		try {
			return localStorage.getItem('chat.showSystem');
		} catch {
			return null;
		}
	}
	$effect(() => {
		try {
			localStorage.setItem('chat.showSystem', showSystem ? '1' : '0');
		} catch {
			// storage blocked: preference lasts this page only
		}
	});
	const shown = $derived(showSystem ? messages : messages.filter((m) => m.kind !== 'SYSTEM'));

	const nearBottom = () => list.scrollHeight - list.scrollTop - list.clientHeight < 60;

	/** Adds messages; follows the bottom only if the reader was already there. */
	async function add(incoming: ChatMessage[]) {
		const follow = nearBottom();
		messages = merge(messages, incoming);
		await tick();
		if (follow) list.scrollTop = list.scrollHeight;
	}

	async function loadOlder() {
		if (loadingOlder || !hasMore || !messages.length) return;
		loadingOlder = true;
		try {
			const page = await api<ChatMessage[]>('GET', `/chat/messages?limit=${PAGE}&before=${messages[0].id}`);
			hasMore = page.length === PAGE;
			const before = list.scrollHeight;
			messages = merge(messages, page);
			await tick();
			list.scrollTop += list.scrollHeight - before; // keep the reader's place
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'Could not load older messages.';
		} finally {
			loadingOlder = false;
		}
	}

	onMount(() => {
		api<ChatMessage[]>('GET', `/chat/messages?limit=${PAGE}`)
			.then(async (page) => {
				hasMore = page.length === PAGE;
				messages = merge(messages, page);
				await tick();
				list.scrollTop = list.scrollHeight;
			})
			.catch(() => (error = 'Could not load chat history.'));
		const offs = [
			on('CHAT_MESSAGE', (m: ChatMessage) => add([m])),
			on('CHAT_DELETED', ({ id }: { id: string }) => (messages = remove(messages, id)))
		];
		return () => offs.forEach((off) => off());
	});

	async function send(e?: Event) {
		e?.preventDefault();
		const text = draft.trim();
		if (!text || sending) return;
		sending = true;
		error = note = '';
		try {
			const m = await api<ChatMessage>('POST', '/chat/messages', { text });
			draft = '';
			list.scrollTop = list.scrollHeight; // your own message: always jump down
			await add([m]);
		} catch (err) {
			if (!(err instanceof ApiError)) error = 'Could not send. Check your connection.';
			else if (err.status === 429) error = 'Slow down: one message per second.';
			else error = err.detail;
		} finally {
			sending = false;
		}
	}

	function onKey(e: KeyboardEvent) {
		if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) send(e);
	}

	async function del(m: ChatMessage) {
		try {
			await api('DELETE', `/admin/chat/messages/${encodeURIComponent(m.id)}`);
			messages = remove(messages, m.id);
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'Could not delete.';
		}
	}

	async function mute(m: ChatMessage) {
		try {
			await api('POST', `/admin/users/${encodeURIComponent(m.user)}/mute`, { minutes: muteMinutes });
			error = '';
			note = `${m.name} muted for ${muteMinutes} min.`;
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'Could not mute.';
		}
	}

	const time = (at: number) => new Date(at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
</script>

<!-- Keys typed or pressed anywhere in chat must never reach the game's keyboard controls. -->
<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
<section
	class="panel chat"
	aria-label="Chat"
	onkeydown={(e) => e.stopPropagation()}
	onkeyup={(e) => e.stopPropagation()}
>
	<header>
		<h2>Chat</h2>
		<label class="toggle"><input type="checkbox" bind:checked={showSystem} /> System messages</label>
		{#if admin}
			<label class="mute-pick">
				Mute for
				<select bind:value={muteMinutes}>
					<option value={5}>5 min</option>
					<option value={60}>1 hour</option>
					<option value={1440}>1 day</option>
				</select>
			</label>
		{/if}
	</header>

	<ul bind:this={list} onscroll={() => list.scrollTop < 40 && loadOlder()}>
		{#if hasMore && messages.length}
			<li class="older">
				<button type="button" onclick={loadOlder} disabled={loadingOlder}>
					{loadingOlder ? 'Loading…' : 'Load older messages'}
				</button>
			</li>
		{/if}
		{#each shown as m (m.id)}
			{@const system = m.kind === 'SYSTEM'}
			<li class:system class:mine={m.user === session.me?.id} title={time(m.at)}>
				<span class="who" style:color={system ? undefined : nameColor(m.user)}>{m.name}</span>
				{#if m.command}<span class="tag">command</span>{/if}
				<span class="text"
					>{#if system}{#each modeParts(m.text) as part, i (i)}{#if part === 'ANARCHY' || part === 'DEMOCRACY'}<span class="mode {part.toLowerCase()}">{part}</span>{:else}{part}{/if}{/each}{:else}{m.text}{/if}</span
				>
				{#if admin && !system}
					<span class="mod">
						<button type="button" onclick={() => del(m)} aria-label="Delete message from {m.name}">Delete</button>
						{#if m.user !== session.me?.id}
							<button type="button" onclick={() => mute(m)} aria-label="Mute {m.name}">Mute</button>
						{/if}
					</span>
				{/if}
			</li>
		{/each}
	</ul>

	<form onsubmit={send}>
		<label for="chat-input" class="sr-only">Message</label>
		<textarea
			id="chat-input"
			rows="1"
			maxlength={MAX}
			placeholder="Say something to the group"
			bind:value={draft}
			onkeydown={onKey}
			readonly={sending}
			aria-describedby="chat-count chat-error"
		></textarea>
		<button type="submit" disabled={sending || !draft.trim()}>Send</button>
	</form>
	<div class="foot">
		<p id="chat-error" class:error={!!error} role="status">{error || note}</p>
		<span id="chat-count" class="count" class:full={draft.length >= MAX}>{draft.length}/{MAX}</span>
	</div>
</section>

<style>
	.chat {
		height: 100%;
		min-height: 0;
		box-sizing: border-box;
		padding: 20px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	header {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
		gap: 8px;
	}
	h2 {
		margin: 0;
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.toggle {
		margin-left: auto;
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 12px;
		color: var(--muted);
	}
	.toggle input {
		accent-color: var(--accent);
	}
	.mode {
		font-style: normal;
		font-weight: 700;
	}
	.anarchy {
		color: var(--anarchy);
	}
	.democracy {
		color: var(--democracy);
	}
	.mute-pick {
		font-size: 12px;
		color: var(--muted);
	}
	select,
	textarea {
		background: var(--bg);
		border: 1px solid #3a4851;
		border-radius: 8px;
		color: var(--text);
		font: inherit;
	}
	ul {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 12px;
		flex: 1 1 auto;
		min-height: 0;
		overflow-y: auto;
	}
	li {
		font-size: 14px;
		line-height: 20px;
		overflow-wrap: anywhere;
	}
	.text {
		white-space: pre-wrap;
	}
	.who {
		font-weight: 700;
	}
	.system,
	.system .who {
		color: var(--muted);
		font-style: italic;
	}
	.mine {
		border-left: 2px solid var(--accent);
		padding-left: 8px;
	}
	.tag {
		font-size: 11px;
		padding: 0 5px;
		border-radius: 4px;
		border: 1px solid var(--border);
		color: var(--muted);
		vertical-align: 1px;
	}
	.older {
		text-align: center;
	}
	.older button,
	.mod button {
		background: none;
		border: none;
		color: var(--muted);
		font-size: 12px;
		padding: 2px 4px;
		text-decoration: underline;
	}
	.mod button:hover {
		color: var(--danger);
	}
	form {
		display: flex;
		gap: 8px;
		align-items: flex-end;
	}
	textarea {
		flex: 1 1 auto;
		min-width: 0;
		min-height: 44px;
		max-height: 120px;
		box-sizing: border-box;
		padding: 11px 12px;
		font-size: 14px;
		resize: vertical;
	}
	form button {
		height: 44px;
		padding: 0 16px;
		border-radius: 8px;
		border: none;
		background: var(--accent);
		color: var(--ink);
		font-weight: 600;
		font-size: 14px;
	}
	form button:disabled {
		opacity: 0.5;
		cursor: default;
	}
	button:focus-visible,
	textarea:focus-visible,
	select:focus-visible,
	input:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: 2px;
	}
	.foot {
		display: flex;
		justify-content: space-between;
		gap: 8px;
		font-size: 12px;
		line-height: 17px;
		margin-top: -8px;
	}
	.foot p {
		margin: 0;
		color: var(--muted);
	}
	.foot p.error {
		color: var(--danger);
	}
	.count {
		color: var(--muted);
		font-variant-numeric: tabular-nums;
	}
	.count.full {
		color: var(--danger);
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
