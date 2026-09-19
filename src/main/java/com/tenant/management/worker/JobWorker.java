package com.tenant.management.worker;

import com.tenant.management.service.JobProcessor;
import com.tenant.management.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
@RequiredArgsConstructor
public class JobWorker {

    private final JobProcessor jobProcessor;
    private final JobService jobService;

    @Scheduled(fixedDelay = 2000)
    public void poll() {
        jobService.enqueueOverdueScan();
        jobProcessor.processDue();
    }
}
