// One shared EventSource on /api/v1/events for every component.
// Event types: INPUT_EXECUTED, MODE_CHANGED, MODE_VOTES, VOTE_TALLY, CONSOLE_CHANGED, CHAT_MESSAGE, CHAT_DELETED, GAME_EVENT.

type Handler = (data: any) => void;

const handlers = new Map<string, Set<Handler>>();
let source: EventSource | null = null;

function ensureSource() {
	if (source) return;
	source = new EventSource('/api/v1/events');
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
