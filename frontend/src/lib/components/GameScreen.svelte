<!-- Live screen: PNG frames from /ws/screen drawn on a 160x144 canvas, scaled with pixelated rendering. -->
<script lang="ts">
	import type { Snippet } from 'svelte';
	import { backoff, countdown } from '#lib/play.ts';

	// `controls` renders at the right of the bezel's status strip (e.g. the sound control). `launchAt` (ISO) is a
	// scheduled start: while the console is off, the screen counts down to it.
	let {
		power,
		paused,
		launchAt = null,
		controls
	}: { power: 'ON' | 'OFF'; paused: boolean; launchAt?: string | null; controls?: Snippet } = $props();

	const launch = $derived(power === 'OFF' && launchAt ? Date.parse(launchAt) : null);
	let now = $state(Date.now());
	$effect(() => {
		if (launch === null) return;
		now = Date.now();
		const t = setInterval(() => (now = Date.now()), 1000);
		return () => clearInterval(t);
	});
	const launchDay = $derived(
		launch === null
			? ''
			: new Date(launch).toLocaleString(undefined, { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
	);

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

<!-- .fit is a size container the play page can stretch; the bezel then hugs the largest 160:144 screen that fits. -->
<div class="fit">
	<div class="bezel">
		<div class="screen">
			<canvas bind:this={canvas} width="160" height="144" aria-label="Live game screen"></canvas>
			{#if launch !== null}
				<div class="overlay countdown pixel" aria-hidden="true">
					{#if launch > now}
						<span class="label">Game Boy powers on in</span>
						<span class="time">{countdown(launch - now)}</span>
						<span class="when">{launchDay}</span>
					{:else}
						<span class="time">Starting…</span>
					{/if}
				</div>
				<p class="sr-only" role="status">Console is off. It powers on {launchDay}.</p>
			{:else}
				{#if overlay}
					<div class="overlay pixel" aria-hidden="true">{overlay}</div>
				{/if}
				<p class="sr-only" role="status">{overlay ?? ''}</p>
			{/if}
		</div>
		<div class="strip">
			<span class="live" class:off={!!overlay}><span class="dot" aria-hidden="true">●</span> {overlay ? 'OFFLINE' : 'LIVE'}</span>
			{@render controls?.()}
		</div>
	</div>
</div>

<style>
	.fit {
		container-type: inline-size;
		display: flex;
		justify-content: center;
		align-items: flex-start;
		height: 100%;
	}
	.bezel {
		background: #3b3f45;
		border-radius: 10px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 8px;
		box-sizing: border-box;
		/* 100% normally; the play page sets --bezel-width when it gives .fit a fixed height. */
		width: var(--bezel-width, 100%);
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
	.countdown {
		flex-direction: column;
		gap: 12px;
	}
	.countdown .label {
		font-size: 10px;
	}
	.countdown .time {
		font-size: 22px;
		letter-spacing: 0.04em;
	}
	.countdown .when {
		font-size: 9px;
		opacity: 0.75;
	}
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
		margin: 0;
	}
	.strip {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		min-height: 28px;
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
	/* On air: console on, game running, stream connected. */
	.live:not(.off) .dot {
		color: #ff4d4f;
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
