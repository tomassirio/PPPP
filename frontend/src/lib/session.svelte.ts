import { api, ApiError } from './api';

export type Me = { id: string; name: string; picture: string | null; roles: string[]; rateLimitRemaining: number };

// Shared session state. `me` is null until loaded or when signed out.
export const session = $state<{ me: Me | null; loaded: boolean }>({ me: null, loaded: false });

export async function loadMe(): Promise<Me | null> {
	try {
		session.me = await api<Me>('GET', '/me');
	} catch (e) {
		if (!(e instanceof ApiError) || e.status !== 401) throw e;
		session.me = null;
	}
	session.loaded = true;
	return session.me;
}

export const isAdmin = (me: Me | null) => !!me?.roles.includes('ADMIN');

export async function logout() {
	await api('POST', '/logout');
	session.me = null;
	location.href = '/';
}
