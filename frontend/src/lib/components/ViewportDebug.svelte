<script lang="ts">
	// Temporary: /play?debug shows what the phone's browser really gives the page, to fix the hidden tab bar.
	// Remove once that's fixed.
	let lines = $state<string[]>([]);
	let probe = $state<HTMLDivElement>();

	$effect(() => {
		const px = (n: number | undefined) => (n === undefined ? '-' : `${Math.round(n)}`);
		const unit = (u: string) => {
			probe!.style.height = u;
			return probe!.getBoundingClientRect().height;
		};
		const rect = (sel: string) => document.querySelector(sel)?.getBoundingClientRect();
		const measure = () => {
			if (!probe) return;
			const vv = visualViewport;
			probe.style.paddingBottom = 'env(safe-area-inset-bottom)';
			const inset = parseFloat(getComputedStyle(probe).paddingBottom);
			probe.style.paddingBottom = '0';
			lines = [
				`innerHeight ${px(innerHeight)}  outer ${px(outerHeight)}`,
				`visualViewport ${px(vv?.height)} top ${px(vv?.offsetTop)} x${vv?.scale ?? '-'}`,
				`clientHeight ${px(document.documentElement.clientHeight)}  screen ${px(screen.height)}`,
				`dvh ${px(unit('100dvh'))} svh ${px(unit('100svh'))} lvh ${px(unit('100lvh'))} vh ${px(unit('100vh'))}`,
				`safe-area-bottom ${px(inset)}`,
				`.page ${px(rect('.page')?.top)}..${px(rect('.page')?.bottom)}`,
				`.tabs ${px(rect('.tabs')?.top)}..${px(rect('.tabs')?.bottom)}`,
				`scrollY ${px(scrollY)}  standalone ${matchMedia('(display-mode: standalone)').matches}`,
				navigator.userAgent.replace(/^Mozilla\/5\.0 /, '')
			];
		};
		measure();
		const timer = setInterval(measure, 1000);
		addEventListener('resize', measure);
		visualViewport?.addEventListener('resize', measure);
		return () => {
			clearInterval(timer);
			removeEventListener('resize', measure);
			visualViewport?.removeEventListener('resize', measure);
		};
	});
</script>

<div class="probe" bind:this={probe} aria-hidden="true"></div>
<pre class="debug">{lines.join('\n')}</pre>

<style>
	.probe {
		position: fixed;
		top: 0;
		left: 0;
		width: 0;
		visibility: hidden;
		pointer-events: none;
	}
	.debug {
		position: fixed;
		top: 60px;
		left: 8px;
		right: 8px;
		z-index: 1000;
		margin: 0;
		padding: 8px 10px;
		border: 1px solid var(--accent-text);
		border-radius: 8px;
		background: rgb(10 13 16 / 0.9);
		color: #edebe3;
		font: 11px/1.5 ui-monospace, Menlo, monospace;
		white-space: pre-wrap;
		word-break: break-all;
		pointer-events: none;
	}
</style>
