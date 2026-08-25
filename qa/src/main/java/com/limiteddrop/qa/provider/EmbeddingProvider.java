package com.limiteddrop.qa.provider;

import java.util.List;

public interface EmbeddingProvider {
    List<Float> embed(String text);
}
