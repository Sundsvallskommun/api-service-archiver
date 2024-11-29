package se.sundsvall.byggrarchiver.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;
import static org.junit.platform.commons.util.ReflectionUtils.findMethod;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.byggrarchiver.testutils.TestUtil.randomLong;

import java.time.LocalDate;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import se.sundsvall.byggrarchiver.api.model.BatchHistoryResponse;
import se.sundsvall.byggrarchiver.api.model.enums.BatchTrigger;
import se.sundsvall.byggrarchiver.service.ByggrArchiverService;

@ExtendWith(MockitoExtension.class)
class ArchiverSchedulerTest {

	@Mock
	private SchedulerProperties mockSchedulerProperties;

	@Mock
	private ByggrArchiverService mockByggrArchiverService;

	@InjectMocks
	private ArchiverScheduler archiverScheduler;

	@Test
	void archive() {

		// Arrange
		final var originalStart = LocalDate.now().minusDays(7);
		final var end = LocalDate.now().minusDays(1);
		final var batchTrigger = BatchTrigger.SCHEDULED;
		final var municipalityId = "2281";

		when(mockByggrArchiverService.runBatch(originalStart, end, batchTrigger, municipalityId))
			.thenReturn(BatchHistoryResponse.builder().withId(randomLong()).build());

		when(mockSchedulerProperties.municipalityIds()).thenReturn(List.of(municipalityId));

		// Act
		archiverScheduler.archive();

		// Assert
		verify(mockByggrArchiverService).runBatch(originalStart, end, batchTrigger, municipalityId);
		verifyNoMoreInteractions(mockByggrArchiverService);
	}

	@Test
	void testScheduledAnnotationContainsCorrectCronValue() {
		var scheduledAnnotation = findMethod(ArchiverScheduler.class, "archive")
			.flatMap(archive -> findAnnotation(archive, Scheduled.class))
			.orElseThrow(() -> new IllegalStateException("Unable to find the 'archive' method on the " + ArchiverScheduler.class.getName() + " class"));

		assertThat(scheduledAnnotation.cron()).isEqualTo("${scheduler.cron.expression}");
	}

	@Test
	void testSchedulerLockAnnotationContainsCorrectNameAndLockAtMostForValue() {
		var schedulerLockAnnotation = findMethod(ArchiverScheduler.class, "archive")
			.flatMap(archive -> findAnnotation(archive, SchedulerLock.class))
			.orElseThrow(() -> new IllegalStateException("Unable to find the 'archive' method on the " + ArchiverScheduler.class.getName() + " class"));

		assertThat(schedulerLockAnnotation.name()).isEqualTo("archive");
		assertThat(schedulerLockAnnotation.lockAtMostFor()).isEqualTo("${scheduler.shedlock-lock-at-most-for}");
	}
}
