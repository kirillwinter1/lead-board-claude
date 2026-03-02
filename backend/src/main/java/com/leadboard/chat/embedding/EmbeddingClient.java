package com.leadboard.chat.embedding;

public interface EmbeddingClient {

    float[] generateEmbedding(String text);
}
