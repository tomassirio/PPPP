import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '#lib/api.ts';
import { act, notice } from './admin.svelte';

describe('act', () => {
	it('reports success, ApiError detail, and skips when confirm is declined', async () => {
		expect(await act('Reset', async () => 42)).toBe(42);
		expect(notice).toEqual({ text: 'Reset: done', error: false });

		await act('Insert', () => Promise.reject(new ApiError(409, 'Power the console off first')));
		expect(notice).toEqual({ text: 'Insert failed: Power the console off first', error: true });

		vi.stubGlobal('confirm', () => false);
		const fn = vi.fn();
		expect(await act('Flush', fn, 'Sure?')).toBeUndefined();
		expect(fn).not.toHaveBeenCalled();
	});
});
