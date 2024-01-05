package se.sundsvall.byggrarchiver.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import se.sundsvall.byggrarchiver.api.model.enums.BatchTrigger;
import se.sundsvall.byggrarchiver.service.exceptions.ApplicationException;

@Service
public class ArchiverScheduleService {

	private static final Logger log = LoggerFactory.getLogger(ArchiverScheduleService.class);

	private final ByggrArchiverService byggrArchiverService;

	public ArchiverScheduleService(ByggrArchiverService byggrArchiverService) {
		this.byggrArchiverService = byggrArchiverService;
	}

	@Scheduled(cron = "${cron.expression}")
	public void archive() throws ApplicationException {
		log.info("Running archiving on schedule. Timestamp: {}", LocalDateTime.now(ZoneId.systemDefault()));

		// Run batch from one week back in time to yesterday
		// TODO - change this when we run this job everyday
		final LocalDate oneWeekBack = LocalDate.now(ZoneId.systemDefault()).minusDays(7);
		final LocalDate yesterday = LocalDate.now(ZoneId.systemDefault()).minusDays(1);

		byggrArchiverService.runBatch(oneWeekBack, yesterday, BatchTrigger.SCHEDULED);
	}
}
