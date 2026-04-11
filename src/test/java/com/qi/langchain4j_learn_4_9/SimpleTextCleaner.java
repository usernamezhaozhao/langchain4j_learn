package com.qi.langchain4j_learn_4_9;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.DocumentTransformer;

import java.util.List;

/**
 * 一个简单的文本清洗器，用于移除文本中的换行符和额外空格。
 */
public class SimpleTextCleaner implements DocumentTransformer {

    @Override
    public Document transform(Document document) {
        // 获取文档的原始文本
        String originalText = document.text();

        // 执行清洗操作：例如，将所有换行符替换为空格，并压缩多个空格
        String cleanedText = originalText
                .replaceAll("\\r\\n|\\r|\\n", " ") // 将换行符替换为空格
                .replaceAll("\\s+", " ");           // 将多个空白字符压缩为一个空格

        // 返回一个包含清洗后文本的新Document对象，同时保留原有的Metadata
        return Document.from(cleanedText, document.metadata());
    }

    @Override
    public List<Document> transformAll(List<Document> documents) {
        // 批量处理文档，为简化，这里调用单个文档的转换方法
        return documents.stream()
                .map(doc -> this.transform(doc))
                .toList();
    }
}