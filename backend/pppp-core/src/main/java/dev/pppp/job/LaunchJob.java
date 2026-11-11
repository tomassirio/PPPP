package dev.pppp.job;

import dev.pppp.service.LaunchCountdown;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** Quartz entry point for the scheduled start; Spring Boot's job factory autowires it. */
@DisallowConcurrentExecution
public class LaunchJob extends QuartzJobBean {
    @Autowired LaunchCountdown countdown;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        countdown.fire();
    }
}
