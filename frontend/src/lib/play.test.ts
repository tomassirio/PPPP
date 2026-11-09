import { describe, expect, it } from 'vitest';
import { afterArrowRelease, backoff, formatCommand, keyToButton, tallyRows } from './play';

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

import { inkFor, SHELLS, shellVars } from './shells';

describe('shells', () => {
	it('picks readable ink and keeps the original first', () => {
		expect(inkFor('#ffffff')).toBe('#1d1f1c');
		expect(inkFor('#141416')).toBe('#ffffff');
		expect(SHELLS[0].id).toBe('original');
		expect(new Set(SHELLS.map((s) => s.id)).size).toBe(SHELLS.length);
		expect(shellVars(SHELLS[0])).toContain('--ab:#bc4b51');
	});
});

describe('walking with held arrows', () => {
	it('keeps walking the new direction when the old arrow is let go', () => {
		// holding UP, pressed LEFT (now walking LEFT), then UP goes up
		expect(afterArrowRelease(['left'], 'left', 'up')).toBe('keep');
	});
	it('goes back to the arrow still held when the walked one is let go', () => {
		expect(afterArrowRelease(['up'], 'left', 'left')).toBe('up');
	});
	it('stops when nothing is held', () => {
		expect(afterArrowRelease([], 'left', 'left')).toBeNull();
	});
});
