import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';

const backend = 'http://localhost:8080';

/** The bit of the proxy's EventEmitter API used below (its typings do not resolve here). */
type ProxyEvents = {
	on(event: 'proxyReq', listener: (proxyReq: { destroy(): void }, req: unknown, res: { on(event: 'close', cb: () => void): void }) => void): void;
};

export default defineConfig({
	plugins: [
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},
			// Static SPA served by nginx; unknown paths fall back to index.html.
			adapter: adapter({ fallback: 'index.html' })
		})
	],
	server: {
		// Keep the browser's Host header so Spring builds OAuth redirect URIs for the dev server, not :8080.
		proxy: {
			'/api': {
				target: backend,
				// The proxy can keep a streaming request (SSE /api/v1/events) open upstream after the browser left,
				// so presence never drops the viewer. Close it with the browser's side.
				configure: (proxy) =>
					(proxy as unknown as ProxyEvents).on('proxyReq', (proxyReq, _req, res) =>
						res.on('close', () => proxyReq.destroy())
					)
			},
			'/oauth2': { target: backend },
			'/login/oauth2': { target: backend },
			'/ws': { target: backend, ws: true }
		}
	},
	test: {
		include: ['src/**/*.test.ts']
	}
});
