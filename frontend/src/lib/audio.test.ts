import { describe, expect, it } from 'vitest';
import { LEAD, MAX_LEAD, nextStart } from './audio';

describe('nextStart', () => {
	it('plays back to back while the buffer is healthy', () => {
		expect(nextStart(10.08, 10)).toBe(10.08);
	});

	it('resyncs LEAD ahead after an underrun instead of playing late audio in the past', () => {
		expect(nextStart(0, 10)).toBe(10 + LEAD);
		expect(nextStart(9.9, 10)).toBe(10 + LEAD);
	});

	it('drops chunks while queued audio exceeds MAX_LEAD, so latency cannot drift up', () => {
		expect(nextStart(10 + MAX_LEAD + 0.01, 10)).toBeNull();
		expect(nextStart(10 + MAX_LEAD, 10)).toBe(10 + MAX_LEAD);
	});
});
