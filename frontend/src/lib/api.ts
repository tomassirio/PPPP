// Thin fetch wrapper for the backend under /api/v1. Same origin, session cookie, CSRF header.

export class ApiError extends Error {
	constructor(
		public status: number,
		public detail: string,
		public retryAfter: number | null = null
	) {
		super(detail);
	}
}

function csrfToken(): string | undefined {
	return document.cookie
		.split('; ')
		.find((c) => c.startsWith('XSRF-TOKEN='))
		?.slice('XSRF-TOKEN='.length);
}

export async function api<T = unknown>(
	method: 'GET' | 'POST' | 'PUT' | 'DELETE',
	path: string,
	body?: unknown
): Promise<T> {
	const headers: Record<string, string> = { Accept: 'application/json' };
	const isForm = body instanceof FormData;
	if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
	if (method !== 'GET') {
		const token = csrfToken();
		if (token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token);
	}
	const res = await fetch(`/api/v1${path}`, {
		method,
		headers,
		credentials: 'same-origin',
		body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body)
	});
	if (!res.ok) {
		let detail = res.statusText;
		try {
			const problem = await res.json(); // RFC 9457 ProblemDetail
			detail = problem.detail ?? problem.title ?? detail;
		} catch {
			/* no body */
		}
		const retry = res.headers.get('Retry-After');
		throw new ApiError(res.status, detail, retry ? Number(retry) : null);
	}
	if (res.status === 204 || res.headers.get('Content-Length') === '0') return undefined as T;
	const text = await res.text();
	return (text ? JSON.parse(text) : undefined) as T;
}
