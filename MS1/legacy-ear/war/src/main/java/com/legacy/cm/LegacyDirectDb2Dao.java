package com.legacy.cm;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Legacy Data Access Object simulating direct SQL queries to IBM Content Manager (CM) Db2 catalog tables.
 *
 * <p>BACKGROUND (IBM Content Manager Architecture):
 * IBM Content Manager v8 stores document metadata and content across Db2 tables:
 * <ul>
 *   <li><b>ICMSTITEMS001001</b>: Core Item table (Item ID, Version, Item Type ID, Timestamp, ACL)</li>
 *   <li><b>ICMSTITEMTYPEDEFS</b>: Item Type definition catalog (Item Type ID to Name mapping)</li>
 *   <li><b>ICMSTCOLLNAME001001</b>: Resource collection storage table (Filename, MIME, Size, LOB content)</li>
 * </ul>
 *
 * <p>LEGACY SDK COMPARISON:
 * In the official IBM CM SDK, developers used DKDatastoreICM, DKDDO, and DKLobICM:
 * <pre>
 *   DKDatastoreICM ds = new DKDatastoreICM();
 *   ds.connect("ICMNLSDB", "icmadmin", "password", "");
 *   DKDDO ddo = ds.createDDO("ICM_ITEM_001", DKConstant.DK_CM_DOCUMENT);
 *   ddo.retrieve(DKConstant.DK_CM_CONTENT_ATTRONLY);
 *   DKLobICM lob = (DKLobICM) ddo.getData(ddo.dataId(DKConstant.DK_CM_NAMESPACE_ATTR, "DKContent"));
 *   byte[] content = lob.getContent();
 * </pre>
 * Because SDK calls incurred heavy native wrapper overhead, legacy developers frequently bypassed
 * the SDK in favor of direct SQL queries against Db2.
 */
public class LegacyDirectDb2Dao {

    private static final Logger LOGGER = Logger.getLogger(LegacyDirectDb2Dao.class.getName());

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public static final String LEGACY_DB2_SQL =
            "SELECT " +
            "    itm.ITEMID, " +
            "    itm.VERSIONID, " +
            "    typ.ITEMTYPENAME, " +
            "    itm.CREATEDTIMESTAMP, " +
            "    itm.ACLCODE, " +
            "    doc.FILENAME, " +
            "    doc.MIMETYPE, " +
            "    doc.DOC_SIZE, " +
            "    doc.DOCUMENT_DATA " +
            "FROM ICMSTITEMS001001 itm " +
            "JOIN ICMSTITEMTYPEDEFS typ ON itm.ITEMTYPEID = typ.ITEMTYPEID " +
            "JOIN ICMSTCOLLNAME001001 doc ON itm.ITEMID = doc.ITEMID " +
            "WHERE itm.ITEMID = ?";

    public LegacyDirectDb2Dao(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "JDBC Driver class not found: " + e.getMessage(), e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Executes legacy direct SQL query with PreparedStatement and ResultSet.
     */
    public LegacyDocumentRecord findDocumentByItemId(String itemId) throws SQLException {
        LOGGER.info("[Legacy Db2 Query] Executing direct SQL for ItemID: " + itemId);
        LOGGER.fine("[Legacy Db2 Query SQL]: " + LEGACY_DB2_SQL);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(LEGACY_DB2_SQL)) {

            ps.setString(1, itemId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    LegacyDocumentRecord record = new LegacyDocumentRecord();
                    record.setItemId(rs.getString("ITEMID"));
                    record.setVersionId(rs.getInt("VERSIONID"));
                    record.setItemTypeName(rs.getString("ITEMTYPENAME"));
                    record.setCreatedTimestamp(rs.getTimestamp("CREATEDTIMESTAMP"));
                    record.setAclCode(rs.getString("ACLCODE"));
                    record.setFileName(rs.getString("FILENAME"));
                    record.setMimeType(rs.getString("MIMETYPE"));
                    record.setDocSize(rs.getLong("DOC_SIZE"));

                    // Reading binary BLOB / BYTEA via InputStream
                    try (InputStream is = rs.getBinaryStream("DOCUMENT_DATA")) {
                        if (is != null) {
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            byte[] chunk = new byte[4096];
                            int read;
                            while ((read = is.read(chunk)) != -1) {
                                buffer.write(chunk, 0, read);
                            }
                            record.setDocumentData(buffer.toByteArray());
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Error reading DOCUMENT_DATA binary stream: " + ex.getMessage(), ex);
                    }

                    LOGGER.info("[Legacy Db2 Query] Found record for ItemID: " + itemId + ", filename: " + record.getFileName());
                    return record;
                }
            }
        }

        LOGGER.warning("[Legacy Db2 Query] No record found for ItemID: " + itemId);
        return null;
    }
}
