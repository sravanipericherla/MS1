package com.legacy.cm;

import java.io.InputStream;
import java.sql.Timestamp;

/**
 * Data Transfer Object representing an IBM Content Manager document record
 * retrieved from legacy Db2 catalog and collection tables.
 */
public class LegacyDocumentRecord {

    private String itemId;
    private int versionId;
    private String itemTypeName;
    private Timestamp createdTimestamp;
    private String aclCode;
    private String fileName;
    private String mimeType;
    private long docSize;
    private byte[] documentData;

    public LegacyDocumentRecord() {}

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public int getVersionId() { return versionId; }
    public void setVersionId(int versionId) { this.versionId = versionId; }

    public String getItemTypeName() { return itemTypeName; }
    public void setItemTypeName(String itemTypeName) { this.itemTypeName = itemTypeName; }

    public Timestamp getCreatedTimestamp() { return createdTimestamp; }
    public void setCreatedTimestamp(Timestamp createdTimestamp) { this.createdTimestamp = createdTimestamp; }

    public String getAclCode() { return aclCode; }
    public void setAclCode(String aclCode) { this.aclCode = aclCode; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getDocSize() { return docSize; }
    public void setDocSize(long docSize) { this.docSize = docSize; }

    public byte[] getDocumentData() { return documentData; }
    public void setDocumentData(byte[] documentData) { this.documentData = documentData; }
}
