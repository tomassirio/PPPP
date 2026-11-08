import { describe, expect, it } from 'vitest';
import { compareIds, merge, remove, type ChatMessage } from './messages';

const msg = (id: string): ChatMessage => ({
	id,
	user: 'u',
	name: 'U',
	text: id,
	kind: 'USER',
	command: false,
	at: 0
});
const ids = (l: ChatMessage[]) => l.map((m) => m.id);

describe('chat messages', () => {
	it('orders stream ids numerically', () => {
		expect(compareIds('10-0', '9-5')).toBeGreaterThan(0);
		expect(compareIds('9-1', '9-0')).toBeGreaterThan(0);
		expect(compareIds('9-0', '9-0')).toBe(0);
	});

	it('merges newest-first history and live messages oldest-first without duplicates', () => {
		let list = merge([], [msg('3-0'), msg('2-0'), msg('1-0')]); // history is newest first
		list = merge(list, [msg('4-0')]); // POST response
		list = merge(list, [msg('4-0')]); // same message via SSE
		list = merge(list, [msg('0-1'), msg('0-0')]); // older page
		expect(ids(list)).toEqual(['0-0', '0-1', '1-0', '2-0', '3-0', '4-0']);
	});

	it('removes by id', () => {
		expect(ids(remove([msg('1-0'), msg('2-0')], '1-0'))).toEqual(['2-0']);
	});
});
