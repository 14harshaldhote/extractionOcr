package com.ardur.controller;

import com.ardur.model.Document;
import com.ardur.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class); // Logger initialization

    @Autowired
    private DocumentService documentService;

    @GetMapping
    public String index(Model model) {
        List<Document> documents = documentService.getAllDocuments();
        model.addAttribute("documents", documents);
        return "index"; // Main page to display documents
    }

    @PostMapping("/uploadFolder")
    public ResponseEntity<?> uploadFolder(@RequestParam("folder") MultipartFile[] files) {
        try {
            documentService.processUploadedFolder(files);
            return ResponseEntity.ok("Folder processed successfully");
        } catch (Exception e) {
            logger.error("Error processing uploaded folder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing folder: " + e.getMessage());
        }
    }

    @GetMapping("/documents/{id}")
    public String getDocument(@PathVariable Long id, Model model) {
        Document document = documentService.getDocumentById(id);
        model.addAttribute("document", document);
        return "document"; // View to display document details
    }

    @GetMapping("/api/documents")
    @ResponseBody
    public List<Document> getAllDocuments() {
        return documentService.getAllDocuments();
    }

    @GetMapping("/api/documents/{id}")
    @ResponseBody
    public ResponseEntity<Document> getDocumentDetails(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id);
        return ResponseEntity.ok(document);
    }

    // New method to retrieve the image from the blob
    @GetMapping(value = "/api/images/{id}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        try {
            Document document = documentService.getDocumentById(id);
            if (document != null && document.getImage() != null) {
                // Assuming the image is in a blob format (like PNG)
                byte[] originalImageBytes = document.getImage();

                // Convert the byte array to a BufferedImage
                ByteArrayInputStream bis = new ByteArrayInputStream(originalImageBytes);
                BufferedImage originalImage = ImageIO.read(bis);

                // Convert BufferedImage to JPEG
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(originalImage, "jpeg", baos);
                byte[] jpegImageBytes = baos.toByteArray();

                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(jpegImageBytes); // Return the converted JPEG byte array
            } else {
                return ResponseEntity.notFound().build(); // Image or document not found
            }
        } catch (Exception e) {
            logger.error("Error retrieving image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
