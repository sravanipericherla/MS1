package com.demo2.docker.document;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "DOCUMENTS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", length = 64)
    private String itemId;

    @Column(name = "item_type", length = 50)
    private String itemType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "author", length = 100)
    private String author;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "document_data", columnDefinition = "bytea")
    private byte[] documentData;
}
