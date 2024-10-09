package com.ardur.service;


import com.ardur.model.Document;
import com.ardur.repository.DocumentRepository;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    @Autowired
    private DocumentRepository documentRepository;

    @Value("${tesseract.datapath}")
    private String TESSERACT_DATAPATH;

    @Value("${tesseract.library.path}")
    private String TESSERACT_LIBRARY_PATH;

    public void processUploadedFolder(MultipartFile[] files) throws IOException, TesseractException {
        if (files.length == 0) {
            logger.warn("No files uploaded.");
            throw new IllegalArgumentException("No files uploaded.");
        }

        String projectName = null;

        for (MultipartFile file : files) {
            String filePath = file.getOriginalFilename();
            if (filePath == null) {
                logger.warn("Skipping file with null name");
                continue;
            }

            Path path = Paths.get(filePath);
            if (path.getNameCount() < 3) {
                logger.warn("Skipping file with insufficient path depth: {}", filePath);
                continue;
            }

            if (projectName == null) {
                projectName = path.getName(0).toString();
                logger.info("Processing project: {}", projectName);
            }

            String batchName = path.getName(1).toString();
            String fileName = path.getFileName().toString();

            // Convert MultipartFile to File
            File tempFile = File.createTempFile("temp", fileName);
            file.transferTo(tempFile);

            try {
                processDocument(projectName, batchName, fileName, tempFile);
            } catch (Exception e) {
                logger.error("Error processing document: {}", fileName, e);
            } finally {
                if (!tempFile.delete()) {
                    logger.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    @Transactional
    public Document processDocument(String projectName, String batchName, String fileName, File imageFile) throws IOException, TesseractException {
        logger.info("Processing document: {}, Project: {}, Batch: {}", fileName, projectName, batchName);

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(TESSERACT_DATAPATH);
        System.setProperty("jna.library.path", TESSERACT_LIBRARY_PATH);

        String ocrText;
        try {
            ocrText = tesseract.doOCR(imageFile);
            logger.debug("OCR completed for file: {}", fileName);
        } catch (TesseractException e) {
            logger.error("OCR failed for file: {}", fileName, e);
            throw e;
        }

        byte[] imageBytes;
        try {
            imageBytes = Files.readAllBytes(imageFile.toPath());
            logger.debug("Image bytes read for file: {}", fileName);
        } catch (IOException e) {
            logger.error("Failed to read image file: {}", fileName, e);
            throw e;
        }

        Document document = new Document();
        document.setProjectName(projectName);
        document.setBatchName(batchName);
        document.setFileName(fileName);
        document.setFileSize(imageFile.length());
        document.setImage(imageBytes);
        document.setOcrText(ocrText);
        document.setCreatedAt(LocalDateTime.now());
        document.setDate(parseDateFromBatchName(batchName));

        try {
            return documentRepository.save(document);
        } catch (Exception e) {
            logger.error("Failed to save document to database", e);
            throw e;
        }
    }

    private LocalDate parseDateFromBatchName(String batchName) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            return LocalDate.parse(batchName, formatter);
        } catch (Exception e) {
            logger.warn("Unable to parse date from batch name: {}. Using current date.", batchName);
            return LocalDate.now();
        }
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    public Document getDocumentById(Long id) {
        return documentRepository.findById(id).orElse(null);
    }
}
