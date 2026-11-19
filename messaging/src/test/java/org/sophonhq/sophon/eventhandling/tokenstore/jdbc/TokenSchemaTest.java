package org.sophonhq.sophon.eventhandling.tokenstore.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenSchemaTest {

    @Test
    void defaultNamesAreCorrect() {
        TokenSchema tokenSchema = TokenSchema.builder()
            .build();

        assertThat(tokenSchema.tokenTable()).isEqualTo("TokenEntry");
        assertThat(tokenSchema.processorNameColumn()).isEqualTo("processorName");
        assertThat(tokenSchema.segmentColumn()).isEqualTo("segment");
        assertThat(tokenSchema.tokenColumn()).isEqualTo("token");
        assertThat(tokenSchema.tokenTypeColumn()).isEqualTo("tokenType");
        assertThat(tokenSchema.timestampColumn()).isEqualTo("timestamp");
        assertThat(tokenSchema.ownerColumn()).isEqualTo("owner");
    }

    @Test
    void modifiedNamesAreCorrect() {
        TokenSchema tokenSchema = TokenSchema.builder()
            .setTokenTable("TokenEntryModified")
            .setProcessorNameColumn("processorNameModified")
            .setSegmentColumn("segmentModified")
            .setTokenColumn("tokenModified")
            .setTokenTypeColumn("tokenTypeModified")
            .setTimestampColumn("timestampModified")
            .setOwnerColumn("ownerModified")
            .build();

        assertThat(tokenSchema.tokenTable()).isEqualTo("TokenEntryModified");
        assertThat(tokenSchema.processorNameColumn()).isEqualTo("processorNameModified");
        assertThat(tokenSchema.segmentColumn()).isEqualTo("segmentModified");
        assertThat(tokenSchema.tokenColumn()).isEqualTo("tokenModified");
        assertThat(tokenSchema.tokenTypeColumn()).isEqualTo("tokenTypeModified");
        assertThat(tokenSchema.timestampColumn()).isEqualTo("timestampModified");
        assertThat(tokenSchema.ownerColumn()).isEqualTo("ownerModified");
    }

}