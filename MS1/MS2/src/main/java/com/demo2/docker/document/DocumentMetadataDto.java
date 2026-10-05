package com.demo2.docker.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentMetadataDto {
    private Long id;
    private String itemId;
    private String itemType;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String author;
    private LocalDateTime createdAt;
    private String downloadUrl;

    public static DocumentMetadataDto fromEntity(DocumentEntity entity) {
        return DocumentMetadataDto.builder()
                .id(entity.getId())
                .itemId(entity.getItemId())
                .itemType(entity.getItemType())
                .fileName(entity.getFileName())
                .mimeType(entity.getMimeType())
                .fileSize(entity.getFileSize())
                .author(entity.getAuthor())
                .createdAt(entity.getCreatedAt())
                .downloadUrl("/api/v1/documents/" + entity.getId() + "/content")
                .build();
    }
}
