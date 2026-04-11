package com.qi.langchain4j_learn_4_9;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.DocumentTransformer;

import java.util.List;

/**
 * 简单的文本清洗器
 *
 * 功能: 实现 DocumentTransformer 接口，用于清洗文档文本
 * 使用场景: RAG 系统中，在文档分割之前进行预处理
 *
 * 清洗操作:
 * 1. 移除换行符（\r\n, \r, \n）
 * 2. 压缩多个连续空格为一个空格
 *
 * 为什么需要文本清洗?
 * - PDF、Word 等文档可能包含大量换行符和空格
 * - 这些字符会影响文本分割和向量化的效果
 * - 清洗后的文本更适合 LLM 处理
 *
 * 使用示例:
 * ```java
 * DocumentTransformer cleaner = new SimpleTextCleaner();
 * Document cleanedDoc = cleaner.transform(originalDoc);
 * ```
 */
public class SimpleTextCleaner implements DocumentTransformer {

    /**
     * 转换单个文档
     *
     * @param document 原始文档
     * @return 清洗后的文档
     *
     * 工作流程:
     * 1. 获取文档的原始文本
     * 2. 执行清洗操作（移除换行符、压缩空格）
     * 3. 创建新的 Document 对象，保留原有的 Metadata
     *
     * 注意事项:
     * - 不修改原始文档，而是创建新的文档对象
     * - 保留原有的元数据（文件名、路径等）
     * - 清洗操作是不可逆的，确保在分割之前调用
     */
    @Override
    public Document transform(Document document) {
        // 获取文档的原始文本
        String originalText = document.text();

        // 执行清洗操作
        String cleanedText = originalText
                // 步骤1: 将所有换行符（\r\n, \r, \n）替换为空格
                // 正则表达式 \r\n|\r|\n 匹配所有类型的换行符
                // Windows: \r\n, Unix/Linux: \n, Mac: \r
                .replaceAll("\\r\\n|\\r|\\n", " ")

                // 步骤2: 将多个连续的空白字符（空格、制表符等）压缩为一个空格
                // \s+ 匹配一个或多个空白字符
                // 例如: "hello    world" -> "hello world"
                .replaceAll("\\s+", " ");

        // 返回一个包含清洗后文本的新 Document 对象
        // 同时保留原有的 Metadata（文件名、路径等）
        return Document.from(cleanedText, document.metadata());
    }

    /**
     * 批量转换文档
     *
     * @param documents 原始文档列表
     * @return 清洗后的文档列表
     *
     * 实现方式:
     * - 使用 Stream API 对每个文档调用 transform() 方法
     * - 返回清洗后的文档列表
     *
     * 使用场景:
     * - 批量处理多个文档
     * - 在 RAG 系统中预处理整个文档集合
     */
    @Override
    public List<Document> transformAll(List<Document> documents) {
        // 使用 Stream API 批量处理
        // 1. documents.stream(): 将列表转换为流
        // 2. .map(doc -> this.transform(doc)): 对每个文档调用 transform() 方法
        // 3. .toList(): 将流转换回列表
        return documents.stream()
                .map(doc -> this.transform(doc))
                .toList();
    }
}