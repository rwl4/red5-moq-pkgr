package org.red5.io.moq.warp.catalog;

import org.red5.io.moq.catalog.CatalogSerializer;

import java.io.IOException;

/**
 * JSON serializer/deserializer for WARP catalogs.
 */
public class WarpCatalogSerializer extends CatalogSerializer {

    public String toJson(WarpCatalog catalog) {
        return serializeObject(catalog);
    }

    public WarpCatalog fromJson(String json) throws IOException {
        try {
            return deserializeObject(json, WarpCatalog.class);
        } catch (Exception e) {
            throw new IOException("Failed to parse WARP catalog JSON", e);
        }
    }
}
