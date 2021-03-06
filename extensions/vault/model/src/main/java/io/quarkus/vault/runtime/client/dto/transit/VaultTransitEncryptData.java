package io.quarkus.vault.runtime.client.dto.transit;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.vault.runtime.client.dto.VaultModel;

public class VaultTransitEncryptData implements VaultModel {

    public String ciphertext;
    public String error;
    @JsonProperty("batch_results")
    public List<VaultTransitEncryptDataBatchResult> batchResults;

}
