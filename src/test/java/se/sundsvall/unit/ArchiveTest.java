package se.sundsvall.unit;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import se.sundsvall.ArchiveDao;
import se.sundsvall.Archiver;
import se.sundsvall.TestDao;
import se.sundsvall.exceptions.ApplicationException;
import se.sundsvall.exceptions.ServiceException;
import se.sundsvall.sokigo.arendeexport.ArendeExportIntegrationService;
import se.sundsvall.sokigo.arendeexport.ByggrMapper;
import se.sundsvall.sundsvall.archive.ArchiveMessage;
import se.sundsvall.sundsvall.archive.ArchiveResponse;
import se.sundsvall.sundsvall.archive.ArchiveService;
import se.sundsvall.sundsvall.messaging.MessagingService;
import se.sundsvall.sundsvall.messaging.vo.MessageStatusResponse;
import se.sundsvall.util.Constants;
import se.sundsvall.vo.*;
import se.tekis.arende.*;
import se.tekis.servicecontract.ArendeBatch;
import se.tekis.servicecontract.ArrayOfArende;
import se.tekis.servicecontract.BatchFilter;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
class ArchiveTest {

    public static final String DOCUMENT_ID_1 = "ABC123";
    public static final String DOCUMENT_ID_2 = "aaaaaaaaaaaaaaaaabbbbbbbbbbbccccccccccc";
    public static final String DOCUMENT_ID_3 = "12345678";
    public static final String POST_ARCHIVE_EXCEPTION_MESSAGE = "{\n" +
            "  \"httpCode\": 500,\n" +
            "  \"message\": \"Service error\",\n" +
            "  \"technicalDetails\": {\n" +
            "    \"rootCode\": 500,\n" +
            "    \"rootCause\": \"Internal Server Error\",\n" +
            "    \"serviceId\": \"api-service-archive\",\n" +
            "    \"requestId\": null,\n" +
            "    \"details\": [\n" +
            "      \"Error invoking subclass method\",\n" +
            "      \"Request: /documents\"\n" +
            "    ]\n" +
            "  }\n" +
            "}";

    @Inject
    TestDao testDao;

    @Inject
    Archiver archiver;

    @Inject
    ArchiveDao archiveDao;

    @Inject
    ByggrMapper byggrMapper;

    @InjectMock
    @RestClient
    ArchiveService archiveServiceMock;

    @InjectMock
    @RestClient
    MessagingService messagingServiceMock;

    @InjectMock
    ArendeExportIntegrationService arendeExportIntegrationService;

    // TODO - tests for cases with mixed statuses
    // TODO - tests for mixed handelsetyper

    /**
     * Util method for creating arende-objects
     * @param status - status for the arende
     * @param handelsetyp - type of handelse that should be included
     * @param attachmentCategories - the documents that should be generated
     * @return Arende
     */
    private Arende createArendeObject(String status, String handelsetyp, List<AttachmentCategory> attachmentCategories) {
        Arende arende = new Arende();
        arende.setDnr("BYGG 2021-" + new Random().nextInt(999999));

        arende.setStatus(status);
        Handelse handelse = new Handelse();
        handelse.setHandelsetyp(handelsetyp);

        ArrayOfHandelseHandling arrayOfHandelseHandling = new ArrayOfHandelseHandling();
        List<Dokument> dokumentList = new ArrayList<>();
        attachmentCategories.forEach(category -> {
            Dokument dokument = new Dokument();
            dokument.setDokId(String.valueOf(new Random().nextInt(999999)));
            dokument.setNamn("Test filnamn");
            DokumentFil docFil = new DokumentFil();
            docFil.setFilAndelse("pdf");
            dokument.setFil(docFil);
            dokument.setSkapadDatum(LocalDateTime.now().minusDays(30));

            dokumentList.add(dokument);

            HandelseHandling handling = new HandelseHandling();
            handling.setTyp(category.name());
            handling.setDokument(dokument);

            arrayOfHandelseHandling.getHandling().add(handling);
        });

        handelse.setHandlingLista(arrayOfHandelseHandling);
        ArrayOfHandelse arrayOfHandelse = new ArrayOfHandelse();
        arrayOfHandelse.getHandelse().add(handelse);
        arende.setHandelseLista(arrayOfHandelse);

        for (Dokument doc : dokumentList) {
            Mockito.doReturn(List.of(doc)).when(arendeExportIntegrationService).getDocument(doc.getDokId());
        }

        return arende;

    }

    @BeforeEach
    void beforeEach() throws ServiceException {
        // Clear db between tests
        testDao.deleteAllFromAllTables();

        /*
        Mocks
         */

        // ArendeExport

        // Default mock for ArendeExportIntegrationService
        ArendeBatch arendeBatch = new ArendeBatch();
        ArrayOfArende arrayOfArende = new ArrayOfArende();
        arendeBatch.setArenden(arrayOfArende);
        Mockito.doReturn(arendeBatch).when(arendeExportIntegrationService).getUpdatedArenden(any());

        // Messaging
        MessageStatusResponse messageStatusResponse = new MessageStatusResponse();
        messageStatusResponse.setMessageId("b9535bce-fed9-4a42-a8b7-6fb6540aa3f3");
        messageStatusResponse.setSent(true);
        Mockito.when(messagingServiceMock.postEmail(any())).thenReturn(messageStatusResponse);

        // Archiver
        ArchiveResponse archiveResponse = new ArchiveResponse();
        archiveResponse.setArchiveId("FORMPIPE ID 123-123-123");
        Mockito.when(archiveServiceMock.postArchive(any())).thenReturn(archiveResponse);
    }

    // Standard scenario - Run batch for yesterday - 0 cases and documents found
    @Test
    void testStandardBatchNoDocsFound() throws ServiceException, ApplicationException {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        BatchHistory returnedBatchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.SCHEDULED);

        verify(arendeExportIntegrationService, times(25)).getUpdatedArenden(any());
        verify(arendeExportIntegrationService, times(0)).getDocument(any());
        verify(archiveServiceMock, times(0)).postArchive(any());
        verify(messagingServiceMock, times(0)).postEmail(any());

        Assertions.assertEquals(Status.COMPLETED, archiveDao.getBatchHistory(returnedBatchHistory.getId()).getStatus());
        Assertions.assertTrue(archiveDao.getBatchHistories().contains(returnedBatchHistory));
        Assertions.assertEquals(0, archiveDao.getArchiveHistories(returnedBatchHistory.getId()).size());
    }

    // Standard scenario - Run batch for yesterday - 1 case and 3 documents found
    @Test
    void testStandardBatchThreeDocsFound() throws ApplicationException, ServiceException {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        ArendeBatch arendeBatch = new ArendeBatch();
        arendeBatch.setBatchStart(LocalDateTime.now().minusDays(1).withHour(12).withMinute(0).withSecond(0));
        arendeBatch.setBatchEnd(LocalDateTime.now().minusDays(1).withHour(23).withMinute(0).withSecond(0));

        ArrayOfArende arrayOfArende = new ArrayOfArende();
        Arende arende1 = createArendeObject(Constants.BYGGR_STATUS_AVSLUTAT, Constants.BYGGR_HANDELSETYP_ARKIV, List.of(AttachmentCategory.PLFASE, AttachmentCategory.FASSIT2, AttachmentCategory.TOMTPLBE));
        arrayOfArende.getArende().addAll(List.of(arende1));
        arendeBatch.setArenden(arrayOfArende);

        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = yesterday.atTime(23, 59, 59);
        BatchFilter batchFilter = new BatchFilter();
        batchFilter.setLowerExclusiveBound(start);
        batchFilter.setUpperInclusiveBound(end);

        Mockito.doReturn(arendeBatch).when(arendeExportIntegrationService).getUpdatedArenden(Mockito.argThat(new BatchFilterMatcher(batchFilter)));

        BatchHistory returnedBatchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.SCHEDULED);

        verify(arendeExportIntegrationService, times(3)).getUpdatedArenden(any());
        verify(arendeExportIntegrationService, times(3)).getDocument(any());
        verify(archiveServiceMock, times(3)).postArchive(any());
        verify(messagingServiceMock, times(0)).postEmail(any());

        Assertions.assertEquals(Status.COMPLETED, archiveDao.getBatchHistory(returnedBatchHistory.getId()).getStatus());
        Assertions.assertTrue(archiveDao.getBatchHistories().contains(returnedBatchHistory));
        Assertions.assertEquals(3, archiveDao.getArchiveHistories(returnedBatchHistory.getId()).size());
    }

    // Try to run scheduled batch for the same date and verify it doesn't run
    @Test
    void testRunScheduledBatchForSameDate() throws ServiceException, ApplicationException {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = yesterday.atTime(23, 59, 59);
        BatchFilter batchFilter = new BatchFilter();
        batchFilter.setLowerExclusiveBound(start);
        batchFilter.setUpperInclusiveBound(end);

        ArendeBatch arendeBatch = new ArendeBatch();
        arendeBatch.setBatchStart(start);
        arendeBatch.setBatchEnd(end);

        ArrayOfArende arrayOfArende = new ArrayOfArende();
        Arende arende1 = createArendeObject(Constants.BYGGR_STATUS_AVSLUTAT, Constants.BYGGR_HANDELSETYP_ARKIV, List.of(AttachmentCategory.PLFASE, AttachmentCategory.FASSIT2, AttachmentCategory.TOMTPLBE));
        arrayOfArende.getArende().addAll(List.of(arende1));
        arendeBatch.setArenden(arrayOfArende);

        Mockito.doReturn(arendeBatch).when(arendeExportIntegrationService).getUpdatedArenden(Mockito.argThat(new BatchFilterMatcher(batchFilter)));

        // Run the first batch
        BatchHistory firstBatchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.SCHEDULED);
        Assertions.assertEquals(Status.COMPLETED, archiveDao.getBatchHistory(firstBatchHistory.getId()).getStatus());
        Assertions.assertTrue(archiveDao.getBatchHistories().contains(firstBatchHistory));
        Assertions.assertEquals(3, archiveDao.getArchiveHistories(firstBatchHistory.getId()).size());

        // Run second batch with the same date
        BatchHistory secondBatchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.SCHEDULED);
        Assertions.assertNull(secondBatchHistory);

        // Only the first batch
        verify(arendeExportIntegrationService, times(2)).getUpdatedArenden(any());
        verify(arendeExportIntegrationService, times(3)).getDocument(any());
        verify(archiveServiceMock, times(3)).postArchive(any());
        verify(messagingServiceMock, times(0)).postEmail(any());
    }

    // Try to run manual batch for the same date and verify it runs
    @Test
    void testRunManualBatchForSameDate() throws ServiceException, ApplicationException {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = yesterday.atTime(23, 59, 59);
        BatchFilter batchFilter = new BatchFilter();
        batchFilter.setLowerExclusiveBound(start);
        batchFilter.setUpperInclusiveBound(end);

        ArendeBatch arendeBatch = new ArendeBatch();
        arendeBatch.setBatchStart(start);
        arendeBatch.setBatchEnd(end);

        ArrayOfArende arrayOfArende = new ArrayOfArende();
        Arende arende1 = createArendeObject(Constants.BYGGR_STATUS_AVSLUTAT, Constants.BYGGR_HANDELSETYP_ARKIV, List.of(AttachmentCategory.PLFASE, AttachmentCategory.FASSIT2, AttachmentCategory.TOMTPLBE));
        arrayOfArende.getArende().addAll(List.of(arende1));
        arendeBatch.setArenden(arrayOfArende);

        Mockito.doReturn(arendeBatch).when(arendeExportIntegrationService).getUpdatedArenden(Mockito.argThat(new BatchFilterMatcher(batchFilter)));

        // Run the first batch
        BatchHistory firstBatchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.SCHEDULED);
        Assertions.assertEquals(Status.COMPLETED, archiveDao.getBatchHistory(firstBatchHistory.getId()).getStatus());
        Assertions.assertTrue(archiveDao.getBatchHistories().contains(firstBatchHistory));
        Assertions.assertEquals(3, archiveDao.getArchiveHistories(firstBatchHistory.getId()).size());

        BatchHistory secondBatchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.MANUAL);

        Assertions.assertEquals(2, archiveDao.getBatchHistories().size());
        Assertions.assertEquals(3, archiveDao.getArchiveHistories().size());
        Assertions.assertEquals(Status.COMPLETED, archiveDao.getBatchHistory(secondBatchHistory.getId()).getStatus());
        Assertions.assertTrue(archiveDao.getBatchHistories().contains(secondBatchHistory));
        Assertions.assertEquals(0, archiveDao.getArchiveHistories(secondBatchHistory.getId()).size());

        // Both first and second batch
        verify(arendeExportIntegrationService, times(4)).getUpdatedArenden(any());

        // Only the first batch
        verify(arendeExportIntegrationService, times(3)).getDocument(any());
        verify(archiveServiceMock, times(3)).postArchive(any());
        verify(messagingServiceMock, times(0)).postEmail(any());
    }

    // Try to run batch for a date back in time and verify the scheduled batch change the startDate back in time to the day after latest scheduled batch.
    @ParameterizedTest
    @EnumSource(BatchTrigger.class)
    void testTimeGapScheduledThenScheduled(BatchTrigger batchTrigger) throws ApplicationException {
        LocalDate aLongTimeAgo = LocalDate.now().minusDays(20);

        // Run the first batch
        BatchHistory firstBatchHistory = archiver.runBatch(aLongTimeAgo, aLongTimeAgo, batchTrigger);
        Assertions.assertEquals(Status.COMPLETED, archiveDao.getBatchHistory(firstBatchHistory.getId()).getStatus());
        Assertions.assertTrue(archiveDao.getBatchHistories().contains(firstBatchHistory));
        Assertions.assertEquals(0, archiveDao.getArchiveHistories(firstBatchHistory.getId()).size());

        LocalDate yesterday = LocalDate.now().minusDays(1);
        BatchHistory secondBatchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.SCHEDULED);

        Assertions.assertTrue(archiveDao.getBatchHistories().contains(secondBatchHistory));
        Assertions.assertEquals(aLongTimeAgo.plusDays(1), secondBatchHistory.getStart());
        Assertions.assertEquals(yesterday, secondBatchHistory.getEnd());
        Assertions.assertEquals(Status.COMPLETED, secondBatchHistory.getStatus());
    }

    // Try to run batch for a date back in time and verify the manual batch does NOT change the startDate back in time.
    @ParameterizedTest
    @EnumSource(BatchTrigger.class)
    void testTimeGapManualThenScheduled(BatchTrigger batchTrigger) throws ApplicationException {
        LocalDate aLongTimeAgo = LocalDate.now().minusDays(20);

        // Run the first batch
        BatchHistory firstBatchHistory = archiver.runBatch(aLongTimeAgo, aLongTimeAgo, batchTrigger);
        Assertions.assertEquals(Status.COMPLETED, archiveDao.getBatchHistory(firstBatchHistory.getId()).getStatus());
        Assertions.assertTrue(archiveDao.getBatchHistories().contains(firstBatchHistory));
        Assertions.assertEquals(0, archiveDao.getArchiveHistories(firstBatchHistory.getId()).size());

        LocalDate yesterday = LocalDate.now().minusDays(1);
        BatchHistory secondBatchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.MANUAL);

        Assertions.assertTrue(archiveDao.getBatchHistories().contains(secondBatchHistory));
        Assertions.assertEquals(yesterday, secondBatchHistory.getStart());
        Assertions.assertEquals(yesterday, secondBatchHistory.getEnd());
        Assertions.assertEquals(Status.COMPLETED, secondBatchHistory.getStatus());
    }

    @Test
    void testGetLatestCompletedBatch() throws ApplicationException {
        List<BatchHistory> batchHistoryList = new ArrayList<>();

        BatchHistory batchHistory1 = new BatchHistory(LocalDate.now().minusDays(5), LocalDate.now().minusDays(1), BatchTrigger.SCHEDULED, Status.NOT_COMPLETED);
        batchHistoryList.add(batchHistory1);

        BatchHistory batchHistory2 = new BatchHistory(LocalDate.now().minusDays(7), LocalDate.now().minusDays(6), BatchTrigger.SCHEDULED, Status.COMPLETED);
        batchHistoryList.add(batchHistory2);

        batchHistoryList.forEach(batchHistory -> archiveDao.postBatchHistory(batchHistory));

        LocalDate yesterday = LocalDate.now().minusDays(1);
        BatchHistory latestBatchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.SCHEDULED);

        Assertions.assertTrue(archiveDao.getBatchHistories().contains(latestBatchHistory));
        // Should be nr 2 because the latest is not completed
        Assertions.assertEquals(batchHistory2.getEnd().plusDays(1), latestBatchHistory.getStart());
        Assertions.assertEquals(yesterday, latestBatchHistory.getEnd());
        Assertions.assertEquals(Status.COMPLETED, latestBatchHistory.getStatus());
    }

    @Test
    void testGetLatestCompletedBatch2() throws ApplicationException {
        List<BatchHistory> batchHistoryList = new ArrayList<>();

        BatchHistory batchHistory1 = new BatchHistory(LocalDate.now().minusDays(5), LocalDate.now().minusDays(3), BatchTrigger.SCHEDULED, Status.COMPLETED);
        batchHistoryList.add(batchHistory1);

        BatchHistory batchHistory2 = new BatchHistory(LocalDate.now().minusDays(7), LocalDate.now().minusDays(6), BatchTrigger.SCHEDULED, Status.COMPLETED);
        batchHistoryList.add(batchHistory2);

        batchHistoryList.forEach(batchHistory -> archiveDao.postBatchHistory(batchHistory));

        LocalDate yesterday = LocalDate.now().minusDays(1);
        BatchHistory latestBatchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.SCHEDULED);

        Assertions.assertTrue(archiveDao.getBatchHistories().contains(latestBatchHistory));
        // Should be nr 1 because it's the latest
        Assertions.assertEquals(batchHistory1.getEnd().plusDays(1), latestBatchHistory.getStart());
        Assertions.assertEquals(yesterday, latestBatchHistory.getEnd());
        Assertions.assertEquals(Status.COMPLETED, latestBatchHistory.getStatus());
    }

//    // Run batch and simulate ByggrMapper failure. Verify we handle exception correctly and abort the batch.
//    @Test
//    void testErrorFromByggrMapperV1() throws ServiceException, ApplicationException {
//
//        String exceptionMessage = "Could not parse date";
//        Mockito.when(byggrMapper.getArchiveableAttachments(any(), any())).thenThrow(new BadRequestException(exceptionMessage));
//
//        // Test
//        LocalDate start = LocalDate.now().minusDays(1);
//        LocalDate end = LocalDate.now();
//
//        BadRequestException thrown = Assertions.assertThrows(BadRequestException.class, () -> archiver.archiveByggrAttachments(start, end, BatchTrigger.SCHEDULED));
//
//        Assertions.assertEquals(exceptionMessage, thrown.getLocalizedMessage());
//        List<BatchHistory> batchHistoryList = archiveDao.getBatchHistories();
//        Assertions.assertEquals(1, batchHistoryList.size());
//        Assertions.assertEquals(Status.NOT_COMPLETED, archiveDao.getBatchHistory(batchHistoryList.get(0).getId()).getStatus());
//        Assertions.assertEquals(0, archiveDao.getArchiveHistories().size());
//
//        verify(byggrMapper, times(1)).getArchiveableAttachments(any(), any());
//        verify(archiveServiceMock, times(0)).postArchive(any());
//        verify(messagingServiceMock, times(0)).postEmail(any());
//    }
//
//    // Run batch and simulate ByggrMapper failure. Verify we handle exception correctly and abort the batch.
//    @Test
//    void testErrorFromByggrMapperV2() throws ServiceException, ApplicationException {
//
//        String exceptionMessage = "The response from arendeExportIntegrationService.getUpdatedArenden(batchFilter) was null. This shouldn't happen.";
//        Mockito.when(byggrMapper.getArchiveableAttachments(any(), any())).thenThrow(new ApplicationException(exceptionMessage));
//
//        // Test
//        LocalDate start = LocalDate.now().minusDays(1);
//        LocalDate end = LocalDate.now();
//
//        ApplicationException thrown = Assertions.assertThrows(ApplicationException.class, () -> archiver.archiveByggrAttachments(start, end, BatchTrigger.SCHEDULED));
//
//        Assertions.assertEquals(exceptionMessage, thrown.getLocalizedMessage());
//        List<BatchHistory> batchHistoryList = archiveDao.getBatchHistories();
//        Assertions.assertEquals(1, batchHistoryList.size());
//        Assertions.assertEquals(Status.NOT_COMPLETED, archiveDao.getBatchHistory(batchHistoryList.get(0).getId()).getStatus());
//        Assertions.assertEquals(0, archiveDao.getArchiveHistories().size());
//
//        verify(byggrMapper, times(1)).getArchiveableAttachments(any(), any());
//        verify(archiveServiceMock, times(0)).postArchive(any());
//        verify(messagingServiceMock, times(0)).postEmail(any());
//    }
//
    // Run batch and simulate request to Archive failure. Verify we handle exception correctly and continue with the rest.
    @Test
    void testErrorFromArchive() throws ServiceException, ApplicationException {

        LocalDate yesterday = LocalDate.now().minusDays(1);

        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = yesterday.atTime(23, 59, 59);
        BatchFilter batchFilter = new BatchFilter();
        batchFilter.setLowerExclusiveBound(start);
        batchFilter.setUpperInclusiveBound(end);

        ArendeBatch arendeBatch = new ArendeBatch();
        arendeBatch.setBatchStart(start);
        arendeBatch.setBatchEnd(end);

        ArrayOfArende arrayOfArende = new ArrayOfArende();
        Arende arende1 = createArendeObject(Constants.BYGGR_STATUS_AVSLUTAT, Constants.BYGGR_HANDELSETYP_ARKIV, List.of(AttachmentCategory.PLFASE, AttachmentCategory.FASSIT2, AttachmentCategory.TOMTPLBE));
        arrayOfArende.getArende().addAll(List.of(arende1));
        arendeBatch.setArenden(arrayOfArende);

        Mockito.doReturn(arendeBatch).when(arendeExportIntegrationService).getUpdatedArenden(Mockito.argThat(new BatchFilterMatcher(batchFilter)));

        Mockito.doThrow(ServiceException.create(POST_ARCHIVE_EXCEPTION_MESSAGE, null, Response.Status.INTERNAL_SERVER_ERROR)).when(archiveServiceMock).postArchive(any());

        // Test
        BatchHistory batchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.SCHEDULED);

        Assertions.assertEquals(Status.NOT_COMPLETED, archiveDao.getBatchHistory(batchHistory.getId()).getStatus());
        List<ArchiveHistory> archiveHistoryList = archiveDao.getArchiveHistories(batchHistory.getId());
        Assertions.assertEquals(3, archiveHistoryList.size());
        archiveHistoryList.forEach(archiveHistory -> Assertions.assertEquals(Status.NOT_COMPLETED, archiveHistory.getStatus()));

        verify(arendeExportIntegrationService, times(2)).getUpdatedArenden(any());
        verify(arendeExportIntegrationService, times(3)).getDocument(any());
        verify(archiveServiceMock, times(3)).postArchive(any());
        verify(messagingServiceMock, times(0)).postEmail(any());
    }

    // Rerun an earlier not_completed batch - GET batchhistory and verify it was completed
    @Test
    void testReRunNotCompletedBatch() throws ServiceException, ApplicationException {

        LocalDate yesterday = LocalDate.now().minusDays(1);

        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = yesterday.atTime(23, 59, 59);
        BatchFilter batchFilter = new BatchFilter();
        batchFilter.setLowerExclusiveBound(start);
        batchFilter.setUpperInclusiveBound(end);

        ArendeBatch arendeBatch = new ArendeBatch();
        arendeBatch.setBatchStart(start);
        arendeBatch.setBatchEnd(end);

        ArrayOfArende arrayOfArende = new ArrayOfArende();
        Arende arende1 = createArendeObject(Constants.BYGGR_STATUS_AVSLUTAT, Constants.BYGGR_HANDELSETYP_ARKIV, List.of(AttachmentCategory.GEO, AttachmentCategory.FASSIT2, AttachmentCategory.TOMTPLBE));
        arrayOfArende.getArende().addAll(List.of(arende1));
        arendeBatch.setArenden(arrayOfArende);

        Mockito.doReturn(arendeBatch).when(arendeExportIntegrationService).getUpdatedArenden(Mockito.argThat(new BatchFilterMatcher(batchFilter)));

        // Mock
        Handling handling = arende1.getHandelseLista().getHandelse().get(0).getHandlingLista().getHandling().get(0);
        Attachment attachment = byggrMapper.getAttachment(handling, handling.getDokument());
        ArchiveMessage archiveMessage = new ArchiveMessage();
        archiveMessage.setAttachment(attachment);
        Mockito.doThrow(ServiceException.create(POST_ARCHIVE_EXCEPTION_MESSAGE, null, Response.Status.INTERNAL_SERVER_ERROR)).when(archiveServiceMock).postArchive(Mockito.argThat(new ArchiveMessageAttachmentMatcher(archiveMessage)));

        // First run, fails
        BatchHistory firstBatchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.SCHEDULED);

        Assertions.assertEquals(1, archiveDao.getBatchHistories().size());
        Assertions.assertEquals(Status.NOT_COMPLETED, archiveDao.getBatchHistory(firstBatchHistory.getId()).getStatus());
        List<ArchiveHistory> firstArchiveHistoryList = archiveDao.getArchiveHistories(firstBatchHistory.getId());
        Assertions.assertEquals(3, firstArchiveHistoryList.size());
        // Only one should be NOT_COMPLETED, the other two should be COMPLETED
        List <ArchiveHistory> firstNotCompletedArchiveHistories = firstArchiveHistoryList.stream().filter(archiveHistory -> Status.NOT_COMPLETED.equals(archiveHistory.getStatus())).collect(Collectors.toList());
        List <ArchiveHistory> firstCompletedArchiveHistories = firstArchiveHistoryList.stream().filter(archiveHistory -> Status.COMPLETED.equals(archiveHistory.getStatus())).collect(Collectors.toList());
        Assertions.assertEquals(1, firstNotCompletedArchiveHistories.size());
        Assertions.assertEquals(2, firstCompletedArchiveHistories.size());

        System.out.println("1: " + firstNotCompletedArchiveHistories.get(0));

        verify(arendeExportIntegrationService, times(2)).getUpdatedArenden(any());
        verify(arendeExportIntegrationService, times(3)).getDocument(any());
        verify(archiveServiceMock, times(3)).postArchive(any());
        verify(messagingServiceMock, times(0)).postEmail(any());

        // ReRun, success
        ArchiveResponse archiveResponse = new ArchiveResponse();
        archiveResponse.setArchiveId("FORMPIPE ID 123-123-123");
        Mockito.doReturn(archiveResponse).when(archiveServiceMock).postArchive(any());

        BatchHistory reRunBatchHistory = archiver.reRunBatch(firstBatchHistory.getId());
        Assertions.assertEquals(firstBatchHistory.getId(), reRunBatchHistory.getId());
        Assertions.assertEquals(Status.COMPLETED, reRunBatchHistory.getStatus());
        Assertions.assertEquals(1, archiveDao.getBatchHistories().size());

        List<ArchiveHistory> reRunArchiveHistoryList = archiveDao.getArchiveHistories(reRunBatchHistory.getId());
        Assertions.assertEquals(3, reRunArchiveHistoryList.size());
        // Now all should be COMPLETED
        List <ArchiveHistory> reRunNotCompletedArchiveHistories = reRunArchiveHistoryList.stream().filter(archiveHistory -> Status.NOT_COMPLETED.equals(archiveHistory.getStatus())).collect(Collectors.toList());
        List <ArchiveHistory> reRunCompletedArchiveHistories = reRunArchiveHistoryList.stream().filter(archiveHistory -> Status.COMPLETED.equals(archiveHistory.getStatus())).collect(Collectors.toList());
        Assertions.assertEquals(0, reRunNotCompletedArchiveHistories.size());
        Assertions.assertEquals(3, reRunCompletedArchiveHistories.size());

        // Both the first batch and the reRun
        verify(arendeExportIntegrationService, times(4)).getUpdatedArenden(any());
        // 3 the first time + 1 in the reRun
        verify(arendeExportIntegrationService, times(4)).getDocument(any());
        verify(archiveServiceMock, times(4)).postArchive(any());
        // Only in the rerun when the archiving of GEO success
        verify(messagingServiceMock, times(1)).postEmail(any());
    }

    // Run batch for attachmentCategory "GEO" and verify email was sent
    @Test
    void runBatchGeotekniskUndersokningMessageSentTrue() throws ServiceException, ApplicationException {

        LocalDate yesterday = LocalDate.now().minusDays(1);

        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = yesterday.atTime(23, 59, 59);
        BatchFilter batchFilter = new BatchFilter();
        batchFilter.setLowerExclusiveBound(start);
        batchFilter.setUpperInclusiveBound(end);

        ArendeBatch arendeBatch = new ArendeBatch();
        arendeBatch.setBatchStart(start);
        arendeBatch.setBatchEnd(end);

        ArrayOfArende arrayOfArende = new ArrayOfArende();
        Arende arende1 = createArendeObject(Constants.BYGGR_STATUS_AVSLUTAT, Constants.BYGGR_HANDELSETYP_ARKIV, List.of(AttachmentCategory.GEO, AttachmentCategory.FASSIT2, AttachmentCategory.GEO));
        arrayOfArende.getArende().addAll(List.of(arende1));
        arendeBatch.setArenden(arrayOfArende);

        Mockito.doReturn(arendeBatch).when(arendeExportIntegrationService).getUpdatedArenden(Mockito.argThat(new BatchFilterMatcher(batchFilter)));

        // mocks messaging
        MessageStatusResponse messageStatusResponse = new MessageStatusResponse();
        messageStatusResponse.setMessageId("12312-3123-123-123-123");
        messageStatusResponse.setSent(true);
        Mockito.when(messagingServiceMock.postEmail(any())).thenReturn(messageStatusResponse);

        // Test

        BatchHistory batchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.SCHEDULED);

        Assertions.assertEquals(Status.COMPLETED, archiveDao.getBatchHistory(batchHistory.getId()).getStatus());
        Assertions.assertTrue(archiveDao.getBatchHistories().contains(batchHistory));
        Assertions.assertEquals(3, archiveDao.getArchiveHistories(batchHistory.getId()).size());
        archiveDao.getArchiveHistories(batchHistory.getId()).forEach(archiveHistory -> Assertions.assertEquals(Status.COMPLETED, archiveHistory.getStatus()));

        verify(arendeExportIntegrationService, times(2)).getUpdatedArenden(any());
        verify(arendeExportIntegrationService, times(3)).getDocument(any());
        verify(archiveServiceMock, times(3)).postArchive(any());
        verify(messagingServiceMock, times(2)).postEmail(any());
    }

    // Run batch for attachmentCategory "GEO" and simulate the email was not sent. Verify we log the error and persist all.
    @Test
    void runBatchGeotekniskUndersokningMessageSentFalse() throws ServiceException, ApplicationException {

        LocalDate yesterday = LocalDate.now().minusDays(1);

        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = yesterday.atTime(23, 59, 59);
        BatchFilter batchFilter = new BatchFilter();
        batchFilter.setLowerExclusiveBound(start);
        batchFilter.setUpperInclusiveBound(end);

        ArendeBatch arendeBatch = new ArendeBatch();
        arendeBatch.setBatchStart(start);
        arendeBatch.setBatchEnd(end);

        ArrayOfArende arrayOfArende = new ArrayOfArende();
        Arende arende1 = createArendeObject(Constants.BYGGR_STATUS_AVSLUTAT, Constants.BYGGR_HANDELSETYP_ARKIV, List.of(AttachmentCategory.GEO, AttachmentCategory.FASSIT2, AttachmentCategory.TOMTPLBE));
        arrayOfArende.getArende().addAll(List.of(arende1));
        arendeBatch.setArenden(arrayOfArende);

        Mockito.doReturn(arendeBatch).when(arendeExportIntegrationService).getUpdatedArenden(Mockito.argThat(new BatchFilterMatcher(batchFilter)));

        // mocks messaging
        MessageStatusResponse messageStatusResponse = new MessageStatusResponse();
        messageStatusResponse.setMessageId("12312-3123-123-123-123");
        messageStatusResponse.setSent(false);
        Mockito.when(messagingServiceMock.postEmail(any())).thenReturn(messageStatusResponse);

        // Test
        BatchHistory batchHistory = archiver.runBatch(yesterday, yesterday, BatchTrigger.SCHEDULED);

        Assertions.assertEquals(Status.COMPLETED, archiveDao.getBatchHistory(batchHistory.getId()).getStatus());
        Assertions.assertTrue(archiveDao.getBatchHistories().contains(batchHistory));
        Assertions.assertEquals(3, archiveDao.getArchiveHistories(batchHistory.getId()).size());
        archiveDao.getArchiveHistories(batchHistory.getId()).forEach(archiveHistory -> Assertions.assertEquals(Status.COMPLETED, archiveHistory.getStatus()));

        verify(arendeExportIntegrationService, times(2)).getUpdatedArenden(any());
        verify(arendeExportIntegrationService, times(3)).getDocument(any());
        verify(archiveServiceMock, times(3)).postArchive(any());
        verify(messagingServiceMock, times(1)).postEmail(any());

        // TODO verify error message in the log
    }
}
