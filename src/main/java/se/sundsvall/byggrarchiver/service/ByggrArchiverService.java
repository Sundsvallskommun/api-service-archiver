package se.sundsvall.byggrarchiver.service;

import static se.sundsvall.dept44.util.ResourceUtils.asString;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

import arendeexport.AbstractArendeObjekt;
import arendeexport.Arende2;
import arendeexport.ArendeBatch;
import arendeexport.ArendeFastighet;
import arendeexport.BatchFilter;
import arendeexport.Dokument;
import arendeexport.HandelseHandling;
import arendeexport.Handling;
import feign.FeignException;
import generated.se.sundsvall.archive.ArchiveResponse;
import generated.se.sundsvall.archive.Attachment;
import generated.se.sundsvall.archive.ByggRArchiveRequest;
import generated.se.sundsvall.archive.metadata.ArkivbildarStrukturTyp;
import generated.se.sundsvall.archive.metadata.ArkivbildareTyp;
import generated.se.sundsvall.archive.metadata.ArkivobjektArendeTyp;
import generated.se.sundsvall.archive.metadata.ArkivobjektHandlingTyp;
import generated.se.sundsvall.archive.metadata.ArkivobjektListaArendenTyp;
import generated.se.sundsvall.archive.metadata.ArkivobjektListaHandlingarTyp;
import generated.se.sundsvall.archive.metadata.BilagaTyp;
import generated.se.sundsvall.archive.metadata.ExtraID;
import generated.se.sundsvall.archive.metadata.FastighetTyp;
import generated.se.sundsvall.archive.metadata.LeveransobjektTyp;
import generated.se.sundsvall.archive.metadata.ObjectFactory;
import generated.se.sundsvall.archive.metadata.StatusArande;
import generated.se.sundsvall.messaging.EmailRequest;
import generated.se.sundsvall.messaging.EmailSender;
import generated.sokigo.fb.FastighetDto;
import se.sundsvall.byggrarchiver.api.model.enums.ArchiveStatus;
import se.sundsvall.byggrarchiver.api.model.enums.AttachmentCategory;
import se.sundsvall.byggrarchiver.api.model.enums.BatchTrigger;
import se.sundsvall.byggrarchiver.integration.db.ArchiveHistoryRepository;
import se.sundsvall.byggrarchiver.integration.db.BatchHistoryRepository;
import se.sundsvall.byggrarchiver.integration.db.model.ArchiveHistory;
import se.sundsvall.byggrarchiver.integration.db.model.BatchHistory;
import se.sundsvall.byggrarchiver.integration.sundsvall.archive.ArchiveClient;
import se.sundsvall.byggrarchiver.integration.sundsvall.messaging.MessagingClient;
import se.sundsvall.byggrarchiver.service.exceptions.ApplicationException;
import se.sundsvall.byggrarchiver.service.util.Constants;
import se.sundsvall.byggrarchiver.service.util.Util;

@Service
public class ByggrArchiverService {

	private static final Logger LOG = LoggerFactory.getLogger(ByggrArchiverService.class);

	private static final String STANGT = "Stängt";

	private final Resource geoTekniskHandlingHtmlTemplate = new ClassPathResource("html-templates/geoteknisk_handling_template.html");
	private final Resource missingExtensionHtmlTemplate = new ClassPathResource("html-templates/missing_extension_template.html");

	@Value("${geo-email.receiver}")
	String geoEmailReceiver;

	@Value("${geo-email.sender}")
	String geoEmailSender;

	@Value("${extension-error-email.receiver}")
	String extensionErrorEmailReceiver;

	@Value("${extension-error-email.sender}")
	String extensionErrorEmailSender;

	@Value("${long-term.archive.url}")
	String longTermArchiveUrl;

	private final ArchiveClient archiveClient;
	private final MessagingClient messagingClient;
	private final ArchiveHistoryRepository archiveHistoryRepository;
	private final BatchHistoryRepository batchHistoryRepository;
	private final FbService fbService;
	private final Util util;
	private final ArendeExportIntegrationService arendeExportIntegrationService;

	public ByggrArchiverService(ArchiveClient archiveClient, MessagingClient messagingClient, ArchiveHistoryRepository archiveHistoryRepository, BatchHistoryRepository batchHistoryRepository, FbService fbService, Util util,
		ArendeExportIntegrationService arendeExportIntegrationService) {
		this.archiveClient = archiveClient;
		this.messagingClient = messagingClient;
		this.archiveHistoryRepository = archiveHistoryRepository;
		this.batchHistoryRepository = batchHistoryRepository;
		this.fbService = fbService;
		this.util = util;
		this.arendeExportIntegrationService = arendeExportIntegrationService;
	}

	public BatchHistory runBatch(LocalDate start, LocalDate end, BatchTrigger batchTrigger) throws ApplicationException {
		LOG.info("Batch with BatchTrigger: {} was started with start: {} and end: {}", batchTrigger, start, end);

		if (BatchTrigger.SCHEDULED.equals(batchTrigger)) {
			final BatchHistory latestBatch = getLatestCompletedBatch();

			if (latestBatch != null) {
				// If this batch end-date is not after the latest batch end date, we don't need to run it again
				if (!end.isAfter(latestBatch.getEnd())) {
					LOG.info("This batch does not have a later end-date({}) than the latest batch ({}). Cancelling this batch...", end, latestBatch.getEnd());
					return null;
				}

				// If there is a gap between the latest batch end-date and this batch start-date, we would risk to miss something.
				// Therefore - set the start-date to the latest batch end-date, plus one day.
				final LocalDate dayAfterLatestBatch = latestBatch.getEnd().plusDays(1);
				if (start.isAfter(dayAfterLatestBatch)) {
					LOG.info("It was a gap between the latest batch end-date and this batch start-date. Sets the start-date to: {}", latestBatch.getEnd().plusDays(1));
					start = dayAfterLatestBatch;
				}

			}
		}

		// Persist the start of this batch
		final BatchHistory batchHistory = BatchHistory.builder()
			.start(start)
			.end(end)
			.batchTrigger(batchTrigger)
			.archiveStatus(ArchiveStatus.NOT_COMPLETED).build();
		batchHistoryRepository.save(batchHistory);

		// Do the archiving
		return archive(start, end, batchHistory);
	}

	public BatchHistory reRunBatch(Long batchHistoryId) throws ApplicationException {
		LOG.info("Rerun was started with batchHistoryId: {}", batchHistoryId);

		final BatchHistory batchHistory = batchHistoryRepository.findById(batchHistoryId).orElseThrow(() -> Problem.valueOf(Status.NOT_FOUND, Constants.BATCH_HISTORY_NOT_FOUND));

		if (ArchiveStatus.COMPLETED.equals(batchHistory.getArchiveStatus())) {
			throw Problem.valueOf(Status.BAD_REQUEST, Constants.IT_IS_NOT_POSSIBLE_TO_RERUN_A_COMPLETED_BATCH);
		}

		LOG.info("Rerun batch: {}", batchHistory);

		// Do the archiving
		return archive(batchHistory.getStart(), batchHistory.getEnd(), batchHistory);

	}

	private BatchHistory archive(LocalDate searchStart, LocalDate searchEnd, BatchHistory batchHistory) throws ApplicationException {
		LOG.info("Batch: {} was started with start-date: {} and end-date: {}", batchHistory.getId(), searchStart, searchEnd);

		final LocalDateTime start = searchStart.atStartOfDay();
		final LocalDateTime end = getEnd(searchEnd);
		final BatchFilter batchFilter = getBatchFilter(start, end);

		ArendeBatch arendeBatch = null;

		do {
			if (arendeBatch != null) {
				setLowerExclusiveBoundWithReturnedValue(batchFilter, arendeBatch);
			}

			LOG.info("Run batch iteration with start-date: {} and end-date: {}", batchFilter.getLowerExclusiveBound(), batchFilter.getUpperInclusiveBound());

			// Get arenden from Byggr
			arendeBatch = arendeExportIntegrationService.getUpdatedArenden(batchFilter);

			final List<Arende2> closedCaseList = arendeBatch.getArenden().getArende().stream()
				.filter(arende -> Constants.BYGGR_STATUS_AVSLUTAT.equals(arende.getStatus())).toList();

			for (final Arende2 closedCase : closedCaseList) {
				// Delete all not completed archive histories connected to this case
				archiveHistoryRepository.deleteArchiveHistoriesByCaseIdAndArchiveStatus(closedCase.getDnr(), ArchiveStatus.NOT_COMPLETED);

				final List<HandelseHandling> handelseHandlingList = closedCase.getHandelseLista().getHandelse().stream()
					.filter(handelse -> Constants.BYGGR_HANDELSETYP_ARKIV.equals(handelse.getHandelsetyp()))
					.flatMap(handelse -> handelse.getHandlingLista().getHandling().stream())
					.filter(handelseHandling -> handelseHandling.getDokument() != null).toList();

				for (final Handling handling : handelseHandlingList) {
					final ArchiveHistory newArchiveHistory = new ArchiveHistory();
					final String docId = handling.getDokument().getDokId();
					final Optional<ArchiveHistory> oldArchiveHistory = archiveHistoryRepository.getArchiveHistoryByDocumentIdAndCaseId(docId, closedCase.getDnr());

					if (oldArchiveHistory.isPresent()) {
						LOG.info("Document-ID: {} in combination with Case-ID: {} is already archived.", docId, closedCase.getDnr());
						continue;
					}
					LOG.info("Document-ID: {} in combination with Case-ID: {} does not exist in the db. Archive it..", docId, closedCase.getDnr());
					newArchiveHistory.setDocumentId(docId);
					newArchiveHistory.setDocumentName(handling.getDokument().getNamn());
					newArchiveHistory.setDocumentType(getAttachmentCategory(handling.getTyp()).getDescription());
					newArchiveHistory.setCaseId(closedCase.getDnr());
					newArchiveHistory.setBatchHistory(batchHistory);
					newArchiveHistory.setArchiveStatus(ArchiveStatus.NOT_COMPLETED);
					archiveHistoryRepository.save(newArchiveHistory);

					// Get documents from Byggr
					final List<Dokument> dokumentList = arendeExportIntegrationService.getDocument(docId);

					for (final Dokument doc : dokumentList) {
						LOG.info("Case-ID: {} Document name: {} Handlingstyp: {} Handling-ID: {} Document-ID: {}",
							closedCase.getDnr(),
							doc.getNamn(),
							handling.getTyp(),
							handling.getHandlingId(),
							doc.getDokId());

						archiveAttachment(getAttachment(doc), closedCase, handling, doc, newArchiveHistory);

						if (ArchiveStatus.COMPLETED.equals(newArchiveHistory.getArchiveStatus())
							&& (newArchiveHistory.getArchiveId() != null)
							&& AttachmentCategory.GEO.equals(getAttachmentCategory(handling.getTyp()))) {
							// Send email to Lantmateriet with info about the archived attachment
							sendEmailToLantmateriet(getFastighet(closedCase.getObjektLista().getAbstractArendeObjekt()).getFastighetsbeteckning(), newArchiveHistory);
						}
					}
				}
			}
		} while (batchFilter.getLowerExclusiveBound().isBefore(end));

		final List<ArchiveHistory> archiveHistoriesRelatedToBatch = archiveHistoryRepository.getArchiveHistoriesByBatchHistoryId(batchHistory.getId());
		if (archiveHistoriesRelatedToBatch.stream().allMatch(archiveHistory -> ArchiveStatus.COMPLETED.equals(archiveHistory.getArchiveStatus()))) {
			// Persist that this batch is completed
			batchHistory.setArchiveStatus(ArchiveStatus.COMPLETED);
			batchHistoryRepository.save(batchHistory);
		}

		LOG.info("Batch with ID: {} is {}", batchHistory.getId(), batchHistory.getArchiveStatus());
		LOG.info("Batch with ID: {} has {} archive histories", batchHistory.getId(), archiveHistoriesRelatedToBatch.size());

		updateStatusOfOldBatchHistories();

		return batchHistory;
	}

	/**
	 * Update the status of NOT_COMPLETED old batch histories to COMPLETED if all archive histories are COMPLETED
	 */
	private void updateStatusOfOldBatchHistories() {
		final List<BatchHistory> notCompletedBatchHistories = batchHistoryRepository.findBatchHistoriesByArchiveStatus(ArchiveStatus.NOT_COMPLETED);

		notCompletedBatchHistories.forEach(batchHistory -> {
			final boolean allCompleted = archiveHistoryRepository.getArchiveHistoriesByBatchHistoryId(batchHistory.getId())
				.stream()
				.allMatch(archiveHistory -> ArchiveStatus.COMPLETED.equals(archiveHistory.getArchiveStatus()));

			if (allCompleted) {
				batchHistory.setArchiveStatus(ArchiveStatus.COMPLETED);
				batchHistoryRepository.save(batchHistory);
				LOG.info("Old batch with ID: {} was NOT_COMPLETED but is now COMPLETED", batchHistory.getId());
			}
		});
	}

	private void archiveAttachment(Attachment attachment, Arende2 arende, Handling handling, Dokument document, ArchiveHistory newArchiveHistory) throws ApplicationException {
		if (newArchiveHistory.getArchiveId() == null) {
			final ByggRArchiveRequest archiveMessage = new ByggRArchiveRequest();
			archiveMessage.setAttachment(attachment);

			String metadataXml;
			final var leveransObjektTyp = getLeveransobjektTyp(arende, handling, document);
			try {
				final JAXBContext context = JAXBContext.newInstance(LeveransobjektTyp.class);
				final Marshaller marshaller = context.createMarshaller();
				final StringWriter stringWriter = new StringWriter();
				marshaller.marshal(new ObjectFactory().createLeveransobjekt(leveransObjektTyp), stringWriter);
				metadataXml = stringWriter.toString();
			} catch (final Exception e) {
				throw new ApplicationException("Something went wrong when trying to marshal LeveransobjektTyp", e);
			}

			archiveMessage.setMetadata(metadataXml);

			// Request to Archive
			ArchiveResponse archiveResponse = null;
			try {
				archiveResponse = archiveClient.postArchive(archiveMessage);
			} catch (final FeignException e) {
				LOG.error("Request to Archive failed. Continue with the rest.", e);

				if (e.getMessage().contains("extension must be valid") || e.getMessage().contains("File format")) {
					LOG.info("The problem was related to the file extension. Send email with the information.");
					sendEmailAboutExtensionError(newArchiveHistory);
				}
			}

			if ((archiveResponse != null)
				&& (archiveResponse.getArchiveId() != null)) {

				// Success! Set status to completed
				newArchiveHistory.setArchiveStatus(ArchiveStatus.COMPLETED);
				newArchiveHistory.setArchiveId(archiveResponse.getArchiveId());
				newArchiveHistory.setArchiveUrl(createArchiveUrl(newArchiveHistory.getArchiveId()));

				LOG.info("The archive-process of document with ID: {} succeeded!", newArchiveHistory.getDocumentId());
			} else {
				// Not successful... Set status to not completed
				newArchiveHistory.setArchiveStatus(ArchiveStatus.NOT_COMPLETED);
				LOG.info("The archive-process of document with ID: {} did not succeed.", newArchiveHistory.getDocumentId());
			}
		} else {
			LOG.info("ArchiveHistory already got a archive-ID. Set status to {}", ArchiveStatus.COMPLETED);
			newArchiveHistory.setArchiveStatus(ArchiveStatus.COMPLETED);
		}

		archiveHistoryRepository.save(newArchiveHistory);
	}

	private String createArchiveUrl(String archiveId) {
		final Map<String, String> valuesMap = new HashMap<>();
		valuesMap.put("archiveId", util.getStringOrEmpty(archiveId));
		final StringSubstitutor stringSubstitutor = new StringSubstitutor(valuesMap);
		return longTermArchiveUrl + stringSubstitutor.replace(Constants.ARCHIVE_URL_QUERY);
	}

	private LeveransobjektTyp getLeveransobjektTyp(Arende2 arende, Handling handling, Dokument document) throws ApplicationException {
		final LeveransobjektTyp leveransobjekt = new LeveransobjektTyp();
		leveransobjekt.setArkivbildarStruktur(getArkivbildarStruktur(arende.getAnkomstDatum()));
		leveransobjekt.setArkivobjektListaArenden(getArkivobjektListaArenden(arende, handling, document));

		leveransobjekt.setInformationsklass(null);
		leveransobjekt.setVerksamhetsbaseradArkivredovisning(null);
		leveransobjekt.setSystemInfo(null);
		leveransobjekt.setArkivobjektListaHandlingar(null);

		return leveransobjekt;
	}

	private ArkivobjektListaArendenTyp getArkivobjektListaArenden(Arende2 arende, Handling handling, Dokument document) throws ApplicationException {
		final ArkivobjektArendeTyp arkivobjektArende = new ArkivobjektArendeTyp();

		arkivobjektArende.setArkivobjektID(arende.getDnr());
		final ExtraID extraID = new ExtraID();
		extraID.setContent(arende.getDnr());
		arkivobjektArende.getExtraID().add(extraID);
		arkivobjektArende.setArendemening(arende.getBeskrivning());
		arkivobjektArende.setAvslutat(formatToIsoDateOrReturnNull(arende.getSlutDatum()));
		arkivobjektArende.setSkapad(formatToIsoDateOrReturnNull(arende.getRegistreradDatum()));
		final StatusArande statusArande = new StatusArande();
		statusArande.setValue(STANGT);
		arkivobjektArende.setStatusArande(statusArande);
		arkivobjektArende.setArendeTyp(arende.getArendetyp());
		arkivobjektArende.setArkivobjektListaHandlingar(getArkivobjektListaHandlingar(handling, document));

		if (Objects.nonNull(arende.getObjektLista())) {
			arkivobjektArende.getFastighet().add(getFastighet(arende.getObjektLista().getAbstractArendeObjekt()));
		}

		if ((arende.getAnkomstDatum() == null) || arende.getAnkomstDatum().isAfter(LocalDate.of(2016, 12, 31))) {
			arkivobjektArende.getKlass().add(Constants.HANTERA_BYGGLOV);
		} else {
			arkivobjektArende.getKlass().add(Constants.F_2_BYGGLOV);
		}

		if (arende.getAnkomstDatum() != null) {
			arkivobjektArende.setNotering(String.valueOf(arende.getAnkomstDatum().getYear()));
		}

		arkivobjektArende.setInkommen(null);
		arkivobjektArende.setInformationsklass(null);
		arkivobjektArende.setArkiverat(null);
		arkivobjektArende.setBeskrivning(null);
		arkivobjektArende.setAtkomst(null);
		arkivobjektArende.setExpedierad(null);
		arkivobjektArende.setForvaringsenhetsReferens(null);
		arkivobjektArende.setGallring(null);
		arkivobjektArende.setMinaArendeoversikterKlassificering(null);
		arkivobjektArende.setMinaArendeoversikterStatus(null);
		arkivobjektArende.setSistaAnvandandetidpunkt(null);
		arkivobjektArende.setSystemidentifierare(null);
		arkivobjektArende.setUpprattad(null);

		final ArkivobjektListaArendenTyp arkivobjektListaArendenTyp = new ArkivobjektListaArendenTyp();
		arkivobjektListaArendenTyp.getArkivobjektArende().add(arkivobjektArende);
		return arkivobjektListaArendenTyp;
	}

	private ArkivobjektListaHandlingarTyp getArkivobjektListaHandlingar(Handling handling, Dokument document) throws ApplicationException {
		final ArkivobjektHandlingTyp arkivobjektHandling = new ArkivobjektHandlingTyp();
		arkivobjektHandling.setArkivobjektID(document.getDokId());
		arkivobjektHandling.setSkapad(formatToIsoDateOrReturnNull(document.getSkapadDatum()));
		arkivobjektHandling.getBilaga().add(getBilaga(document));
		if (Objects.nonNull(handling.getTyp())) {
			final var attachmentCategory = getAttachmentCategory(handling.getTyp());

			arkivobjektHandling.setHandlingstyp(attachmentCategory.getArchiveClassification());
			arkivobjektHandling.setRubrik(attachmentCategory.getDescription());
		}

		arkivobjektHandling.setInformationsklass(null);
		arkivobjektHandling.setInkommen(null);
		arkivobjektHandling.setAtkomst(null);
		arkivobjektHandling.setAvsandare(null);
		arkivobjektHandling.setBeskrivning(null);
		arkivobjektHandling.setExpedierad(null);
		arkivobjektHandling.setForvaringsenhetsReferens(null);
		arkivobjektHandling.setGallring(null);
		arkivobjektHandling.setLopnummer(null);
		arkivobjektHandling.setNotering(null);
		arkivobjektHandling.setSistaAnvandandetidpunkt(null);
		arkivobjektHandling.setSkannad(null);
		arkivobjektHandling.setStatusHandling(null);
		arkivobjektHandling.setSystemidentifierare(null);
		arkivobjektHandling.setUpprattad(null);

		final ArkivobjektListaHandlingarTyp arkivobjektListaHandlingarTyp = new ArkivobjektListaHandlingarTyp();
		arkivobjektListaHandlingarTyp.getArkivobjektHandling().add(arkivobjektHandling);
		return arkivobjektListaHandlingarTyp;
	}

	private BilagaTyp getBilaga(Dokument document) throws ApplicationException {
		final BilagaTyp bilaga = new BilagaTyp();

		if (document.getFil().getFilAndelse() == null) {
			document.getFil().setFilAndelse(util.getExtensionFromByteArray(document.getFil().getFilBuffer()));
		}
		bilaga.setNamn(getNameWithExtension(document.getNamn(), document.getFil().getFilAndelse()));
		bilaga.setBeskrivning(document.getBeskrivning());

		bilaga.setLank("Bilagor\\" + bilaga.getNamn());
		return bilaga;
	}

	private String getNameWithExtension(String name, String extension) {
		extension = extension.trim().toLowerCase();

		if (Pattern.compile(".*(\\.[a-zA-Z]{3,4})$").matcher(name).find()) {
			return name;
		}
		final String extensionWithDot = extension.contains(".") ? extension : "." + extension;
		return name + extensionWithDot;
	}

	private FastighetTyp getFastighet(List<AbstractArendeObjekt> abstractArendeObjektList) throws ApplicationException {
		final FastighetTyp fastighet = new FastighetTyp();

		for (final AbstractArendeObjekt abstractArendeObjekt : abstractArendeObjektList) {
			ArendeFastighet arendeFastighet;

			try {
				arendeFastighet = (ArendeFastighet) abstractArendeObjekt;
			} catch (final ClassCastException e) {
				LOG.info("Could not cast AbstractArendeObjekt to ArendeFastighet");
				continue;
			}

			if ((arendeFastighet != null)
				&& arendeFastighet.isArHuvudObjekt()) {

				final FastighetDto fastighetDto = fbService.getPropertyInfoByFnr(arendeFastighet.getFastighet().getFnr());

				if (fastighetDto != null) {
					fastighet.setFastighetsbeteckning(fastighetDto.getKommun() + " " + fastighetDto.getBeteckning());
					fastighet.setTrakt(fastighetDto.getTrakt());
					fastighet.setObjektidentitet(fastighetDto.getUuid().toString());
				}
			}
		}

		return fastighet;
	}

	private ArkivbildarStrukturTyp getArkivbildarStruktur(LocalDate ankomstDatum) {
		final ArkivbildarStrukturTyp arkivbildarStruktur = new ArkivbildarStrukturTyp();

		final ArkivbildareTyp arkivbildareSundsvallsKommun = new ArkivbildareTyp();
		arkivbildareSundsvallsKommun.setNamn(Constants.SUNDSVALLS_KOMMUN);
		arkivbildareSundsvallsKommun.setVerksamhetstidFran("1974");

		final ArkivbildareTyp arkivbildareByggOchMiljoNamnden = new ArkivbildareTyp();
		if ((ankomstDatum == null) || ankomstDatum.isAfter(LocalDate.of(2016, 12, 31))) {
			arkivbildareByggOchMiljoNamnden.setNamn(Constants.STADSBYGGNADSNAMNDEN);
			arkivbildareByggOchMiljoNamnden.setVerksamhetstidFran("2017");
			arkivbildareByggOchMiljoNamnden.setVerksamhetstidTill(null);
		} else if (ankomstDatum.isAfter(LocalDate.of(1992, 12, 31))) {
			arkivbildareByggOchMiljoNamnden.setNamn(Constants.STADSBYGGNADSNAMNDEN);
			arkivbildareByggOchMiljoNamnden.setVerksamhetstidFran("1993");
			arkivbildareByggOchMiljoNamnden.setVerksamhetstidTill("2017");
		} else if (ankomstDatum.isBefore(LocalDate.of(1993, 1, 1))) {
			arkivbildareByggOchMiljoNamnden.setNamn(Constants.BYGGNADSNAMNDEN);
			arkivbildareByggOchMiljoNamnden.setVerksamhetstidFran("1974");
			arkivbildareByggOchMiljoNamnden.setVerksamhetstidTill("1992");
		}
		arkivbildareSundsvallsKommun.setArkivbildare(arkivbildareByggOchMiljoNamnden);

		arkivbildarStruktur.setArkivbildare(arkivbildareSundsvallsKommun);
		return arkivbildarStruktur;
	}

	private String formatToIsoDateOrReturnNull(LocalDate date) {
		if (date == null) {
			return null;
		}
		return date.format(DateTimeFormatter.ISO_DATE);
	}

	private String formatToIsoDateOrReturnNull(LocalDateTime date) {
		if (date == null) {
			return null;
		}
		return date.format(DateTimeFormatter.ISO_DATE);
	}

	private BatchHistory getLatestCompletedBatch() {
		List<BatchHistory> batchHistoryList = batchHistoryRepository.findAll();

		// Filter completed batches
		batchHistoryList = batchHistoryList.stream()
			.filter(b -> ArchiveStatus.COMPLETED.equals(b.getArchiveStatus())).toList();

		// Sort by end-date of batch
		batchHistoryList = batchHistoryList.stream()
			.sorted(Comparator.comparing(BatchHistory::getEnd, Comparator.reverseOrder())).toList();

		BatchHistory latestBatch = null;

		if (!batchHistoryList.isEmpty()) {

			// Get the latest batch
			latestBatch = batchHistoryList.get(0);
			LOG.info("The latest batch: {}", latestBatch);
		}

		return latestBatch;
	}

	private void sendEmailAboutExtensionError(ArchiveHistory archiveHistory) {

		final var emailRequest = new EmailRequest()
			.sender(new EmailSender()
				.name("ByggrArchiver")
				.address(extensionErrorEmailSender))
			.emailAddress(extensionErrorEmailReceiver)
			.subject("Manuell hantering krävs");

		final Map<String, String> valuesMap = new HashMap<>();
		valuesMap.put("byggrCaseId", util.getStringOrEmpty(archiveHistory.getCaseId()));
		valuesMap.put("documentName", util.getStringOrEmpty(archiveHistory.getDocumentName()));
		valuesMap.put("documentType", util.getStringOrEmpty(archiveHistory.getDocumentType()));

		final StringSubstitutor stringSubstitutor = new StringSubstitutor(valuesMap);
		final String htmlWithReplacedValues = stringSubstitutor.replace(asString(missingExtensionHtmlTemplate));
		emailRequest.setHtmlMessage(Base64.getEncoder().encodeToString(htmlWithReplacedValues.getBytes(StandardCharsets.UTF_8)));

		sendEmail(archiveHistory, emailRequest);
	}

	private void sendEmailToLantmateriet(String propertyDesignation, ArchiveHistory archiveHistory) {

		final var emailRequest = new EmailRequest()
			.sender(new EmailSender()
				.name("ByggrArchiver")
				.address(geoEmailSender))
			.emailAddress(geoEmailReceiver)
			.subject("Arkiverad geoteknisk handling");

		final Map<String, String> valuesMap = new HashMap<>();
		valuesMap.put("byggrCaseId", util.getStringOrEmpty(archiveHistory.getCaseId()));
		valuesMap.put("propertyDesignation", util.getStringOrEmpty(propertyDesignation));

		final StringSubstitutor stringSubstitutor = new StringSubstitutor(valuesMap);
		final String htmlWithReplacedValues = stringSubstitutor.replace(asString(geoTekniskHandlingHtmlTemplate));

		emailRequest.setHtmlMessage(Base64.getEncoder().encodeToString(htmlWithReplacedValues.getBytes(StandardCharsets.UTF_8)));

		sendEmail(archiveHistory, emailRequest);
	}

	private void sendEmail(ArchiveHistory archiveHistory, EmailRequest emailRequest) {
		try {
			LOG.info("Sending email to: {} from: {}", emailRequest.getEmailAddress(), emailRequest.getSender().getAddress());

			final var response = messagingClient.postEmail(emailRequest);

			if ((response == null) || (response.getMessageId() == null)) {
				throw new ApplicationException("Unexpected response from messagingClient.postEmail(): " + response);
			}

			LOG.info("E-mail sent to {} with MessageId: {}",
				emailRequest.getEmailAddress(),
				response.getMessageId());

		} catch (final Exception e) {
			// Just log the error and continue. We don't want to fail the whole batch because of this.
			LOG.error("Something went wrong when trying to send e-mail to " + emailRequest.getEmailAddress() + ". They need to be informed manually. ArchiveHistory: " + archiveHistory, e);
		}
	}

	private LocalDateTime getEnd(LocalDate searchEnd) {
		final var now = LocalDateTime.now(ZoneId.systemDefault());
		if (searchEnd.isBefore(now.toLocalDate())) {
			return searchEnd.atTime(23, 59, 59);
		}

		return LocalDateTime.now(ZoneId.systemDefault());
	}

	private BatchFilter getBatchFilter(LocalDateTime start, LocalDateTime end) {

		final BatchFilter filter = new BatchFilter();
		filter.setLowerExclusiveBound(start);
		filter.setUpperInclusiveBound(end);

		return filter;
	}

	private Attachment getAttachment(Dokument document) throws ApplicationException {
		final Attachment attachment = new Attachment();
		if (document.getFil().getFilAndelse() == null) {
			document.getFil().setFilAndelse(util.getExtensionFromByteArray(document.getFil().getFilBuffer()));
		}
		attachment.setExtension("." + document.getFil().getFilAndelse().toLowerCase());
		attachment.setName(getNameWithExtension(document.getNamn(), document.getFil().getFilAndelse()));
		attachment.setFile(util.byteArrayToBase64(document.getFil().getFilBuffer()));

		return attachment;
	}

	private AttachmentCategory getAttachmentCategory(String handlingsTyp) {
		try {
			return AttachmentCategory.fromCode(handlingsTyp);
		} catch (final IllegalArgumentException e) {
			// All the "handlingstyper" we don't recognize, we set to AttachmentCategory.BIL,
			// which means they get the archiveClassification D,
			// which means that they are not public in the archive.
			return AttachmentCategory.BIL;
		}
	}

	/**
	 * Sets setLowerExclusiveBound to the returned batchEnd if it is not equal or before the latest batch. If it is, we add
	 * 1 hour.
	 * After this, we run the batch again.
	 */
	private void setLowerExclusiveBoundWithReturnedValue(BatchFilter filter, ArendeBatch arendeBatch) {
		LOG.info("Last ArendeBatch start: {} end: {}", arendeBatch.getBatchStart(), arendeBatch.getBatchEnd());
		if ((arendeBatch.getBatchEnd() == null)
			|| arendeBatch.getBatchEnd().isEqual(filter.getLowerExclusiveBound())
			|| arendeBatch.getBatchEnd().isBefore(filter.getLowerExclusiveBound())) {

			final LocalDateTime plusOneHour = filter.getLowerExclusiveBound().plusHours(1);
			filter.setLowerExclusiveBound(plusOneHour.isAfter(filter.getUpperInclusiveBound()) ? filter.getUpperInclusiveBound() : plusOneHour);

		} else {
			filter.setLowerExclusiveBound(arendeBatch.getBatchEnd().isAfter(filter.getUpperInclusiveBound()) ? filter.getUpperInclusiveBound() : arendeBatch.getBatchEnd());
		}
	}
}
