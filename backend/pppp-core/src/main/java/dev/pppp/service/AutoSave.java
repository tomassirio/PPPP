package dev.pppp.service;

import dev.pppp.job.AutoSaveJob;
import dev.pppp.model.SaveState;
import dev.pppp.model.Settings;
import java.util.Set;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Automatic save states: the work itself, and the Quartz trigger built from Settings.Autosave. */
@Component
public class AutoSave {
    static final JobKey JOB = JobKey.jobKey("autosave");
    static final TriggerKey TRIGGER = TriggerKey.triggerKey("autosave");
    private static final Logger log = LoggerFactory.getLogger(AutoSave.class);

    private final Scheduler scheduler;
    private final SettingsService settings;
    private final SaveStateService saves;
    private final ChatService chat;

    public AutoSave(Scheduler scheduler, SettingsService settings, SaveStateService saves, ChatService chat) {
        this.scheduler = scheduler;
        this.settings = settings;
        this.saves = saves;
        this.chat = chat;
    }

    /** Takes an automatic state and prunes old ones. Returns null (skips) when the console is off or empty. */
    public SaveState run() {
        SaveState state = saves.takeAuto();
        if (state == null) return null;
        chat.postSystem("Autosaved (" + state.id() + ")");
        return state;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleOnBoot() {
        try {
            schedule(settings.autosave());
        } catch (RuntimeException e) {
            log.warn("Autosave not scheduled", e);
        }
    }

    @EventListener
    public void schedule(Settings.Autosave s) {
        try {
            scheduler.unscheduleJob(TRIGGER);
            if (!s.enabled()) return;
            var job = JobBuilder.newJob(AutoSaveJob.class).withIdentity(JOB).build();
            var trigger = TriggerBuilder.newTrigger().withIdentity(TRIGGER).forJob(JOB)
                    .withSchedule(CronScheduleBuilder.cronSchedule(s.cron())).build();
            scheduler.scheduleJob(job, Set.of(trigger), true);
        } catch (SchedulerException e) {
            throw new IllegalStateException(e);
        }
    }
}
