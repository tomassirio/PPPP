package dev.pppp.job;

import dev.pppp.service.AutoSave;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** Quartz entry point; Spring Boot's job factory autowires it. */
@DisallowConcurrentExecution
public class AutoSaveJob extends QuartzJobBean {
    @Autowired AutoSave autoSave;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        autoSave.run();
    }
}
