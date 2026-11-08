<!-- Live screen: PNG frames from /ws/screen drawn on a 160x144 canvas, scaled with pixelated rendering. -->
<script lang="ts">
	import { backoff } from '#lib/play.ts';

	let { power, paused }: { power: 'ON' | 'OFF'; paused: boolean } = $props();

	let canvas: HTMLCanvasElement;
	let connected = $state(false);

	const overlay = $derived(
		power === 'OFF'
			? 'Console is off'
			: paused
				? 'Paused'
				: !connected
					? 'Reconnecting…'
					: null
	);

	$effect(() => {
		const ctx = canvas.getContext('2d')!;
		let ws: WebSocket | null = null;
		let attempt = 0;
		let timer: ReturnType<typeof setTimeout> | undefined;
		let stopped = false;
		// Decode one frame at a time; while busy keep only the newest.
		let busy = false;
		let pending: Blob | null = null;

		async function draw(blob: Blob) {
			busy = true;
			try {
				const bmp = await createImageBitmap(blob);
				ctx.drawImage(bmp, 0, 0, canvas.width, canvas.height);
				bmp.close();
			} catch {
				/* bad frame, skip */
			}
			busy = false;
			if (pending) {
				const next = pending;
				pending = null;
				draw(next);
			}
		}

		function connect() {
			const proto = location.protocol === 'https:' ? 'wss' : 'ws';
			ws = new WebSocket(`${proto}://${location.host}/ws/screen`);
			ws.binaryType = 'blob';
			ws.onopen = () => {
				connected = true;
				attempt = 0;
			};
			ws.onmessage = (e) => {
				if (busy) pending = e.data;
				else draw(e.data);
			};
			ws.onclose = () => {
				connected = false;
				if (!stopped) timer = setTimeout(connect, backoff(attempt++));
			};
		}

		connect();
		return () => {
			stopped = true;
			clearTimeout(timer);
			ws?.close();
		};
	});
</script>

<div class="bezel">
	<div class="screen">
		<canvas bind:this={canvas} width="160" height="144" aria-label="Live game screen"></canvas>
		{#if overlay}
			<div class="overlay pixel" aria-hidden="true">{overlay}</div>
		{/if}
		<p class="sr-only" role="status">{overlay ?? ''}</p>
	</div>
	<span class="live" class:off={!!overlay}>● {overlay ? 'OFFLINE' : 'LIVE'}</span>
</div>

<style>
	.bezel {
		background: #3b3f45;
		border-radius: 10px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.screen {
		position: relative;
		aspect-ratio: 160 / 144;
		width: 100%;
		background: #cadc9f;
		border-radius: 2px;
		overflow: hidden;
	}
	canvas {
		display: block;
		width: 100%;
		height: 100%;
		image-rendering: pixelated;
	}
	.overlay {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		text-align: center;
		padding: 16px;
		background: rgb(202 220 159 / 0.85);
		color: #0f380f;
		font-size: 14px;
		line-height: 1.6;
	}
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
		margin: 0;
	}
	.live {
		font-size: 11px;
		font-weight: 600;
		letter-spacing: 0.08em;
		color: #d6dade;
	}
	.live.off {
		color: var(--muted);
	}
	@media (max-width: 640px) {
		.bezel {
			padding: 8px;
			border-radius: 8px;
		}
		.overlay {
			font-size: 11px;
		}
	}
</style>
