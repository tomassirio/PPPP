// One shared EventSource on /api/v1/events for every component.
// Event types: INPUT_EXECUTED, MODE_CHANGED, MODE_VOTES, VOTE_TALLY, CONSOLE_CHANGED, CHAT_MESSAGE, CHAT_DELETED, CHAT_CLEARED, GAME_EVENT, GAME_STATE, PRESENCE.

type Handler = (data: any) => void;

const handlers = new Map<string, Set<Handler>>();
const openHandlers = new Set<() => void>();
let source: EventSource | null = null;

function ensureSource() {
	if (source) return;
	source = new EventSource('/api/v1/events');
	source.addEventListener('open', () => openHandlers.forEach((h) => h()));
	for (const type of handlers.keys()) attach(type);
}

function attach(type: string) {
	source?.addEventListener(type, (e) => {
		const data = JSON.parse((e as MessageEvent).data);
		handlers.get(type)?.forEach((h) => h(data));
	});
}

/** Subscribe to one event type; returns an unsubscribe function (use it in $effect cleanup / onDestroy). */
export function on(type: string, handler: Handler): () => void {
	if (!handlers.has(type)) {
		handlers.set(type, new Set());
		attach(type);
	}
	handlers.get(type)!.add(handler);
	ensureSource();
	return () => handlers.get(type)?.delete(handler);
}

/**
 * Runs on every (re)connect of the stream. Snapshots fetched before the stream was registered (e.g. GET
 * /presence) can be stale and events sent meanwhile missed, so re-fetch here.
 */
export function onOpen(handler: () => void): () => void {
	openHandlers.add(handler);
	ensureSource();
	return () => openHandlers.delete(handler);
}
