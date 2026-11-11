package dev.pppp.service;

import dev.pppp.emulator.EmulatorHost;
import dev.pppp.job.LaunchJob;
import dev.pppp.model.LiveEvent;
import dev.pppp.model.Settings;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Scheduled start (Settings.Launch): a one-shot Quartz trigger powers the console on at the set time, then the
 * setting turns itself off. Every change is sent to browsers as LAUNCH {at} (null when there is no countdown).
 * If the server was down at that moment, the trigger fires as soon as it is back.
 */
@Component
public class LaunchCountdown {
    static final JobKey JOB = JobKey.jobKey("launch");
    static final TriggerKey TRIGGER = TriggerKey.triggerKey("launch");
    private static final Logger log = LoggerFactory.getLogger(LaunchCountdown.class);

    /** LAUNCH payload; at is null without a countdown. */
    public record Countdown(Instant at) {}

    private final Scheduler scheduler;
    private final SettingsService settings;
    private final ConsoleService console;
    private final EmulatorHost host;
    private final ChatService chat;
    private final ApplicationEventPublisher events;

    public LaunchCountdown(Scheduler scheduler, SettingsService settings, ConsoleService console, EmulatorHost host,
                           ChatService chat, ApplicationEventPublisher events) {
        this.scheduler = scheduler;
        this.settings = settings;
        this.console = console;
        this.host = host;
        this.chat = chat;
        this.events = events;
    }

    /** Saves the setting; an enabled one needs a time in the future (IllegalArgumentException otherwise). */
    public Settings.Launch update(Settings.Launch s) {
        if (s.enabled() && (s.at() == null || !s.at().isAfter(Instant.now()))) {
            throw new IllegalArgumentException("Pick a start time in the future");
        }
        settings.save(s);
        if (s.enabled()) chat.postAdmin("The Game Boy will power on at " + s.at());
        return s;
    }

    /** When the console powers itself on, or null without a countdown. */
    public Instant startsAt() {
        Settings.Launch s = settings.launch();
        return s.enabled() ? s.at() : null;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleOnBoot() {
        try {
            schedule(settings.launch());
        } catch (RuntimeException e) {
            log.warn("Scheduled start not restored", e);
        }
    }

    @EventListener
    public void schedule(Settings.Launch s) {
        try {
            scheduler.unscheduleJob(TRIGGER);
            if (s.enabled() && s.at() != null) {
                var job = JobBuilder.newJob(LaunchJob.class).withIdentity(JOB).build();
                var trigger = TriggerBuilder.newTrigger().withIdentity(TRIGGER).forJob(JOB).startAt(Date.from(s.at()))
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                        .build();
                scheduler.scheduleJob(job, Set.of(trigger), true);
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException(e);
        }
        events.publishEvent(new LiveEvent("LAUNCH", new Countdown(s.enabled() ? s.at() : null)));
    }

    /** The countdown is over: the setting turns off, and an inserted cartridge that is off powers on. */
    public void fire() {
        Settings.Launch s = settings.launch();
        settings.save(new Settings.Launch(false, s.at()));
        EmulatorHost.Status status = host.status();
        if (status.cartridgeId() == null) {
            chat.postAdmin("Countdown over, but no cartridge is inserted");
            return;
        }
        if (status.power() == EmulatorHost.Power.ON) return;
        console.power(EmulatorHost.Power.ON);
        chat.postSystem("The countdown is over. Let the adventure begin!");
    }
}
