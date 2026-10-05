package com.demo2.docker.document;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.demo2.docker.config.OpenApiConfig.BEARER_AUTH;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Document Management (Content Manager Target REST APIs)",
        description = "Document API")
@SecurityRequirement(name = BEARER_AUTH)
@CrossOrigin(origins = "*")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private final DocumentRepository documentRepository;

    public DocumentController(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Operation(
            summary = "Upload Document (Multipart/Stream)",
            description = "Receives a multipart document upload, reads the file via InputStream, and persists document metadata and binary payload.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Document uploaded successfully",
                            content = @Content(schema = @Schema(implementation = DocumentMetadataDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid or empty file provided")
            }
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadDocument(
            @Parameter(description = "The file to upload as binary content", required = true)
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Document Item Type (corresponds to IBM CM Item Type, e.g. POLICY, CLAIM, INVOICE)")
            @RequestParam(value = "itemType", required = false, defaultValue = "GENERAL") String itemType,

            @Parameter(description = "Author or uploading legacy system identifier")
            @RequestParam(value = "author", required = false, defaultValue = "LEGACY_J2EE_APP") String author
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Uploaded file is empty");
        }

        try {
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document_" + System.currentTimeMillis();
            String contentType = file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

            log.info("Receiving multipart upload: filename='{}', size={} bytes, mimeType='{}', itemType='{}'",
                    originalFilename, file.getSize(), contentType, itemType);

            // Read the binary data using InputStream
            byte[] fileBytes;
            try (InputStream is = file.getInputStream()) {
                fileBytes = is.readAllBytes();
            }

            String generatedItemId = "ICM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            DocumentEntity entity = DocumentEntity.builder()
                    .itemId(generatedItemId)
                    .itemType(itemType.toUpperCase())
                    .fileName(originalFilename)
                    .mimeType(contentType)
                    .fileSize((long) fileBytes.length)
                    .author(author)
                    .createdAt(LocalDateTime.now())
                    .documentData(fileBytes)
                    .build();

            DocumentEntity saved = documentRepository.save(entity);
            log.info("Document saved successfully with ID={} and ItemId={}", saved.getId(), saved.getItemId());

            return ResponseEntity.status(HttpStatus.CREATED).body(DocumentMetadataDto.fromEntity(saved));

        } catch (IOException e) {
            log.error("Failed to process uploaded file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error reading uploaded file: " + e.getMessage());
        }
    }

    @Operation(
            summary = "Stream Document Binary Content (Download)",
            description = "Streams raw document binary data chunk-by-chunk via HTTP OutputStream (chunked transfer encoding).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Document binary stream returned"),
                    @ApiResponse(responseCode = "404", description = "Document not found")
            }
    )
    @GetMapping(value = "/{id}/content")
    public ResponseEntity<StreamingResponseBody> streamDocumentContent(
            @Parameter(description = "Internal document database ID", required = true)
            @PathVariable Long id
    ) {
        log.info("Request received to stream binary content for document ID={}", id);

        return documentRepository.findById(id)
                .map(doc -> {
                    StreamingResponseBody responseBody = outputStream -> {
                    log.info("Streaming binary payload for '{}' ({} bytes)...", doc.getFileName(), doc.getFileSize());
                    try (InputStream inputStream = new ByteArrayInputStream(doc.getDocumentData())) {
                        byte[] buffer = new byte[8192]; // 8KB buffer for chunked transfer
                        int bytesRead;
                        long totalSent = 0;
                        int chunk = 0;                                                    // NEW
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            totalSent += bytesRead;
                            chunk++;                                                      // NEW
                            log.info("MS2 chunk #{}: {} bytes written, total {} of {}",   // NEW
                                    chunk, bytesRead, totalSent, doc.getFileSize());      // NEW
                        }
                        outputStream.flush();
                        log.info("Finished streaming {} bytes in {} chunks for document ID={}", totalSent, chunk, id); // CHANGED
                    }
                };

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFileName() + "\"")
                            .header(HttpHeaders.CONTENT_TYPE, doc.getMimeType())
                            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(doc.getFileSize()))
                            .body(responseBody);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get Document Metadata by ID")
    @GetMapping("/{id}")
    public ResponseEntity<DocumentMetadataDto> getDocumentById(@PathVariable Long id) {
        return documentRepository.findById(id)
                .map(doc -> ResponseEntity.ok(DocumentMetadataDto.fromEntity(doc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "List / Search Document Metadata")
    @GetMapping
    public ResponseEntity<List<DocumentMetadataDto>> listAllDocuments(
            @Parameter(description = "Filter by Item Type (optional)")
            @RequestParam(value = "itemType", required = false) String itemType
    ) {
        List<DocumentEntity> docs;
        if (itemType != null && !itemType.isBlank()) {
            docs = documentRepository.findByItemType(itemType.toUpperCase());
        } else {
            docs = documentRepository.findAll();
        }

        List<DocumentMetadataDto> dtos = docs.stream()
                .map(DocumentMetadataDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
}
