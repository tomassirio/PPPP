import { describe, expect, it } from 'vitest';
import { backoff, formatCommand, keyToButton, tallyRows } from './play';

describe('play helpers', () => {
	it('maps keys, but not while typing or for held keys', () => {
		expect(keyToButton({ key: 'ArrowLeft' })).toBe('left');
		expect(keyToButton({ key: 'z' })).toBe('a');
		expect(keyToButton({ key: 'Shift' })).toBe('select');
		expect(keyToButton({ key: 'q' })).toBeNull();
		expect(keyToButton({ key: 'z', repeat: true })).toBeNull();
		expect(keyToButton({ key: 'z', target: { tagName: 'INPUT' } as never })).toBeNull();
		expect(keyToButton({ key: 'Enter', target: { tagName: 'BUTTON' } as never })).toBeNull();
		expect(keyToButton({ key: 'x', target: { tagName: 'BUTTON' } as never })).toBe('b');
	});

	it('orders tally rows by votes, keeping first-vote order on ties', () => {
		expect(tallyRows({ UP: 1, A: 2, LEFT: 1 })).toEqual([
			{ button: 'A', votes: 2, pct: 50 },
			{ button: 'UP', votes: 1, pct: 25 },
			{ button: 'LEFT', votes: 1, pct: 25 }
		]);
	});

	it('backs off exponentially with a cap', () => {
		expect([0, 1, 2].map(backoff)).toEqual([500, 1000, 2000]);
		expect(backoff(20)).toBe(30_000);
	});

	it('groups repeated steps', () => {
		expect(formatCommand('LEFT,LEFT,DOWN')).toBe('LEFT ×2 DOWN');
	});
});
