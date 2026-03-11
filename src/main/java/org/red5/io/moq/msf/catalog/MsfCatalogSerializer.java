package org.red5.io.moq.msf.catalog;

import org.red5.io.moq.catalog.CatalogSerializer;
import org.red5.io.moq.warp.catalog.WarpCatalog;

import java.io.IOException;

/**
 * JSON serializer/deserializer for MSF catalogs.
 * Uses CatalogSerializer as the shared JSON base and applies MSF validation.
 */
public class MsfCatalogSerializer extends CatalogSerializer {

    /**
     * Serialize catalog to JSON string.
     */
    public String toJson(WarpCatalog catalog) {
        return serializeObject(catalog);
    }

    /**
     * Deserialize JSON string to catalog.
     */
    public MsfCatalog fromJson(String json) throws IOException {
        try {
            return deserializeObject(json, MsfCatalog.class);
        } catch (Exception e) {
            throw new IOException("Failed to parse MSF catalog JSON", e);
        }
    }

    /**
     * Deserialize and validate JSON string to catalog.
     * @throws IOException if parsing fails
     * @throws IllegalArgumentException if validation fails
     */
    public MsfCatalog fromJsonValidated(String json) throws IOException {
        MsfCatalog catalog = fromJson(json);
        catalog.validate();
        return catalog;
    }
}
