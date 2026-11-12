import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';

const backend = process.env.PPPP_BACKEND ?? 'http://localhost:8080';


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
			'/api': { target: backend },
			'/oauth2': { target: backend },
			'/login/oauth2': { target: backend },
			'/ws': { target: backend, ws: true }
		}
	},
	test: {
		include: ['src/**/*.test.ts']
	}
});
