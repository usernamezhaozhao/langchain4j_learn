package com.qi.langchain4j_learn_4_9;


import ai.djl.modality.nlp.preprocess.TextCleaner;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
interface RagTokenStreamAssistant {
    TokenStream chat(@MemoryId Long userId, @UserMessage String msg);
}

@SpringBootTest
public class RagChainTest {

    @Test
    void Test1() throws ExecutionException, InterruptedException {
        String pdfPath = "D:/桌面/赵鸿伟两年工作经验.pdf";
        DocumentParser parser = new ApacheTikaDocumentParser(); // 或其他 PDF 解析器
        List<Document> documents = List.of(
                FileSystemDocumentLoader.loadDocument(Paths.get(pdfPath), parser)
        );
        //注入到向量数据库，这里是内存数据库
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor.ingest(documents, embeddingStore);

        ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .maxResults(3)        // 最多返回3条
                .minScore(0.5)        // 相似度阈值
                .build();
        // 按句子拆分，每块最大 200 字符，重叠 50 字符
        List<TextSegment> segments = DocumentSplitters
                .recursive(200, 50)
                .split(documents.get(0));
        System.out.println("分块完成，共 " + segments.size() + " 段");
        segments.stream().forEach(System.out::println);
        //注册模型
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        //流式输出
        RagTokenStreamAssistant assistant = AiServices.builder(RagTokenStreamAssistant.class)
                .streamingChatModel(chatModel) // 关键：使用 streamingChatModel 方法
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))  // 每个用户保留最近10条消息
//                .contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore))
                .contentRetriever(retriever)
                .build();
        //CompletableFuture接受数据
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        TokenStream tokenStream = assistant.chat(1L,"这个文档在说什么");
        tokenStream.onPartialResponse(System.out::print)
                .onCompleteResponse(response -> future.complete(response))
                .onError(future::completeExceptionally)
                .start();
        future.join();
        System.out.println(future.get().tokenUsage());
//        documents.stream()
//                .forEach(doc -> System.out.println(doc.text()));
    }
    //metadata
    //LangChain4j 为了保持最小化和灵活性，在设计 FileSystemDocumentLoader 时并没有默认填充所有元数据。
    // 它的API文档也只定义了 source、file_name 等作为通用的键名（Common metadata key）。
    // 这个设计意图很明确：它把填充元数据的“最终解释权”留给了开发者，让你可以根据不同的业务场景来定制。
    //=== 文档自动生成的 Metadata ===
    //source: null
    //absolute_path: null
    //file_name: qiqi.pdf
    //file_size: null
    @Test
    void Test2() throws ExecutionException, InterruptedException {
        String pdfPath = "D:/桌面/赵鸿伟两年工作经验.pdf";
        DocumentParser parser = new ApacheTikaDocumentParser(); // 或其他 PDF 解析器
        List<Document> documents = List.of(
                FileSystemDocumentLoader.loadDocument(Paths.get(pdfPath), parser)
        );
        // 加载文档后
        Document doc = documents.get(0);
        Metadata metadata = doc.metadata();
        System.out.println("=== 文档自动生成的 Metadata ===");
        System.out.println("source: " + metadata.getString("source"));
        System.out.println("absolute_path: " + metadata.getString("absolute_path"));
        System.out.println("file_name: " + metadata.getString("file_name"));
        System.out.println("file_size: " + metadata.getLong("file_size"));
    }

}
